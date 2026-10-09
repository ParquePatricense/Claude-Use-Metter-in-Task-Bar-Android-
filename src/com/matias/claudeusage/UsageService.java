package com.matias.claudeusage;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.service.quicksettings.TileService;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RemoteViews;

import org.json.JSONObject;

public class UsageService extends Service {
    static final String ACTION_REFRESH = "refresh";
    static final String EXTRA_HAPTIC = "haptic";
    private static final String CHANNEL = "usage";
    private static final String CH_ALERT = "alerts";
    private static final int NOTIF_ID = 1;
    private static final int ID_LEVEL = 2, ID_RESET = 3, ID_EXPIRED = 4, ID_PRE = 5, ID_SUMMARY = 6;
    private static final long SEC = 1000, MIN = 60 * SEC;
    private static final String HOME = "https://claude.ai/api/organizations";

    // Corre dentro de claude.ai, con las cookies y el bypass de Cloudflare del navegador
    private static final String JS =
            "(async()=>{try{" +
            "const r=await fetch('/api/organizations',{credentials:'include'});" +
            "if(!r.ok){Bridge.result('ERR:organizations HTTP '+r.status);return;}" +
            "const o=await r.json();let last='sin organizaciones';" +
            "for(const g of o){const u=await fetch('/api/organizations/'+g.uuid+'/usage',{credentials:'include'});" +
            "if(!u.ok){last='usage HTTP '+u.status;continue;}" +
            "const j=await u.json();if(j&&j.five_hour!==undefined){Bridge.result(JSON.stringify({u:j,n:g.name||''}));return;}" +
            "last='respuesta: '+JSON.stringify(j).slice(0,120);}" +
            "Bridge.result('ERR:'+last);" +
            "}catch(e){Bridge.result('ERR:'+e)}})()";

    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean pageReady;
    private int failures;
    private ConnectivityManager.NetworkCallback netCb;

    private final Runnable tick = new Runnable() {
        public void run() {
            fetch();
            main.postDelayed(this, nextDelay());
        }
    };

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (System.currentTimeMillis() - Usage.load(c).updated > 30 * SEC) refreshNow();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.migrate(this);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Uso de Claude", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(ch);
        channels(this);

        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        // Vuelve internet: reintenta si el último intento falló
        netCb = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                main.post(new Runnable() {
                    public void run() { if (Usage.load(UsageService.this).error != null) refreshNow(); }
                });
            }
        };
        try { getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(netCb); } catch (Exception ignored) {}
    }

    static final int K_COIN = 0, K_WARN = 1, K_OVER = 2, K_ACH = 3, K_PLAIN = 4;
    private static final String[] SFX_CH = {"s_coin", "s_warn", "s_over", "s_ach"};
    private static final String[] SFX_NAME = {"Avisos 8-bit: reinicio", "Avisos 8-bit: 75% y 90%", "Avisos 8-bit: límite", "Avisos 8-bit: logros"};
    private static final int[] SFX_RES = {R.raw.sfx_coin, R.raw.sfx_warn, R.raw.sfx_over, R.raw.sfx_ach};

    /** Canales de aviso: normal, 8-bit (uno por sonido) y silencioso (horario de sueño). */
    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel al = new NotificationChannel(CH_ALERT, "Avisos de Claude", NotificationManager.IMPORTANCE_HIGH);
        al.enableVibration(true);
        nm.createNotificationChannel(al);
        for (int i = 0; i < SFX_CH.length; i++) {
            NotificationChannel s = new NotificationChannel(SFX_CH[i], SFX_NAME[i], NotificationManager.IMPORTANCE_HIGH);
            s.setSound(android.net.Uri.parse("android.resource://" + c.getPackageName() + "/" + SFX_RES[i]),
                    new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build());
            s.enableVibration(true);
            nm.createNotificationChannel(s);
        }
        NotificationChannel q = new NotificationChannel("alerts_quiet", "Avisos en horario de sueño", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(q);
    }

    private static String channelFor(Context c, int kind) {
        if (kind != K_ACH && History.inQuiet(c)) return "alerts_quiet";
        if (Prefs.get(c).getBoolean("sounds", true) && kind < SFX_CH.length) return SFX_CH[kind];
        return CH_ALERT;
    }

    static void notifyAchievement(Context c, String title, String text) {
        channels(c);
        Notification n = new Notification.Builder(c, channelFor(c, K_ACH))
                .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(Theme.widget(c).accent).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 6, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .build();
        c.getSystemService(NotificationManager.class).notify(100 + (int) (System.currentTimeMillis() % 1000), n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n;
        try { n = build(Usage.load(this)); } catch (RuntimeException e) {
            Prefs.get(this).edit().putInt("nFps", 0).putInt("nFpsIdx", 2).apply();
            n = build(Usage.load(this));
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        pageReady = false;
        if (intent != null && intent.getBooleanExtra(EXTRA_HAPTIC, false)) haptic();
        refreshNow();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenOn); } catch (Exception ignored) {}
        try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCb); } catch (Exception ignored) {}
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    private void haptic() {
        if (!Prefs.get(this).getBoolean("haptic", true)) return;
        try {
            android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK));
            else v.vibrate(android.os.VibrationEffect.createOneShot(20, 120));
        } catch (Exception ignored) {}
    }

    private void refreshNow() {
        main.removeCallbacks(tick);
        main.post(tick);
    }

    /** Intervalo inteligente: rápido mientras usás Claude, lento si está quieto o la pantalla está apagada. */
    private long nextDelay() {
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean("smart", true)) return 30 * SEC;
        long now = System.currentTimeMillis();
        Usage u = Usage.load(this);
        if (u.error != null) return MIN;
        if (u.reset > 0 && u.reset - now < 7 * MIN && u.reset - now > -2 * MIN) return 30 * SEC;
        boolean screen = ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
        if (!screen) return 10 * MIN;
        if (now - p.getLong("lastChange", 0) < 10 * MIN) return 30 * SEC;
        return 2 * MIN;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void fetch() {
        String cookie = Prefs.cookie(this);
        if (cookie == null) {
            Prefs.get(this).edit().putString("error", "sin sesión").apply();
            getSystemService(NotificationManager.class).notify(NOTIF_ID,
                    base(-1).setContentTitle("Claude").setContentText("Abrí la app para iniciar sesión").build());
            return;
        }
        // Sesión guardada (cuenta cargada a mano o recién cambiada): la paso al almacén de cookies
        CookieManager cm = CookieManager.getInstance();
        String have = cm.getCookie("https://claude.ai");
        if (have == null || !have.contains("sessionKey=")) {
            for (String part : cookie.split(";")) cm.setCookie("https://claude.ai", part.trim());
            cm.flush();
        }
        if (web == null) {
            web = new WebView(getApplicationContext());
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.addJavascriptInterface(new Bridge(), "Bridge");
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    pageReady = true;
                    v.evaluateJavascript(JS, null);
                }
            });
            pageReady = false;
        }
        String ua = Prefs.ua(this);
        if (ua != null && !ua.equals(web.getSettings().getUserAgentString())) web.getSettings().setUserAgentString(ua);
        if (!pageReady || failures >= 3) {
            failures = 0;
            pageReady = false;
            web.loadUrl(HOME);
        } else {
            web.evaluateJavascript(JS, null);
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void result(final String s) {
            main.post(new Runnable() {
                public void run() { handle(s); }
            });
        }
    }

    private void handle(String s) {
        Usage u = Usage.load(this);
        if (s == null || s.startsWith("ERR:")) {
            failures++;
            String err = s == null ? "sin respuesta" : s.substring(4);
            boolean auth = err.contains("HTTP 401") || err.contains("HTTP 403");
            u.error = err;
            u.save(this);
            if (auth && failures >= 2) expired(); else show(u);
            return;
        }
        try {
            JSONObject o = new JSONObject(s);
            Usage n = Usage.parse(o.getJSONObject("u"));
            failures = 0;
            SharedPreferences p = Prefs.get(this);
            if (p.getBoolean("expired", false)) {
                p.edit().putBoolean("expired", false).apply();
                getSystemService(NotificationManager.class).cancel(ID_EXPIRED);
            }
            String org = o.optString("n", "");
            JSONObject acc = Prefs.active(this);
            if (!org.isEmpty() && acc != null && acc.optString("name", "").startsWith("Cuenta ")) {
                Prefs.setActiveField(this, "name", org.replace("'s Organization", ""));
            }
            if (n.pct != u.pct || n.week != u.week) p.edit().putLong("lastChange", n.updated).apply();
            alerts(u, n);
            n.save(this);
            History.add(this, n);
            weeklySummary();
            progress(u, n);
            show(n);
        } catch (Exception e) {
            u.error = "datos inválidos: " + e.getMessage();
            u.save(this);
            show(u);
        }
    }

    private void alerts(Usage old, Usage n) {
        SharedPreferences p = Prefs.get(this);
        long now = System.currentTimeMillis();
        long win = n.reset > 0 ? Math.round(n.reset / 600000.0) : 0;
        long oldWin = p.getLong("alertWin", -1);
        int lvl = p.getInt("alertLvl", 0);
        if (win != oldWin) {
            // La ventana anterior terminó: aviso de reinicio
            if (oldWin > 0 && now >= oldWin * 600000 - 15 * MIN && old.pct > 0) {
                alert(ID_RESET, "Sesión de Claude reiniciada", "Volviste a 0%. Semana " + n.week + "%", false, K_COIN);
                p.edit().putLong("lastReset", now).apply();
            }
            lvl = 0;
            if (win > 0) p.edit().putInt("winCount", p.getInt("winCount", 0) + 1).apply();
        }
        int hit = n.pct >= 100 ? 100 : n.pct >= 90 ? 90 : n.pct >= 75 ? 75 : 0;
        if (hit > lvl) {
            String txt = hit == 100
                    ? "Llegaste al límite. " + n.resetLine()
                    : "Te queda " + (100 - n.pct) + "% · " + n.resetLine();
            alert(ID_LEVEL, "Claude al " + n.pct + "%", txt, false, hit == 100 ? K_OVER : K_WARN);
            lvl = hit;
        }
        // Aviso 5 min antes del reinicio
        if (p.getBoolean("preReset", true) && n.reset > 0 && n.pct > 0
                && n.reset - now <= 5 * MIN && n.reset > now && p.getLong("preWin", 0) != win) {
            long m = Math.max(1, (n.reset - now + 30 * SEC) / MIN);
            alert(ID_PRE, "En " + m + " min se reinicia tu sesión", "A las " + Usage.clock(n.reset) + " volvés a 0%", false, K_COIN);
            p.edit().putLong("preWin", win).apply();
        }
        p.edit().putLong("alertWin", win).putInt("alertLvl", lvl).apply();
    }

    /** Experiencia de la mascota (días de uso) y logros. */
    private void progress(Usage old, Usage n) {
        SharedPreferences p = Prefs.get(this);
        long today = java.time.LocalDate.now().toEpochDay();
        boolean newDay = p.getLong("lastXpDay", -1) != today;
        if (newDay) {
            p.edit().putLong("lastXpDay", today).putInt("xpDays", p.getInt("xpDays", 0) + 1).apply();
        }
        // Respaldo automático semanal
        if (newDay && p.getBoolean("autoBackup", false)
                && System.currentTimeMillis() - p.getLong("lastAutoBackup", 0) >= 7 * History.DAY) {
            Backup.auto(this);
        }
        Achievements.unlock(this, "first");
        if (n.pct >= 100) Achievements.unlock(this, "limit");
        int hour = java.time.LocalTime.now().getHour();
        if (n.pct > old.pct && old.pct >= 0) {
            if (hour < 4) Achievements.unlock(this, "night");
            if (hour >= 5 && hour < 7) Achievements.unlock(this, "early");
        }
        if (p.getInt("winCount", 0) >= 10) Achievements.unlock(this, "sessions10");
        if (newDay || n.pct != old.pct) {
            History.Stats st = History.stats(History.load(this, System.currentTimeMillis() - 8 * History.DAY), 8);
            if (st.streak >= 3) Achievements.unlock(this, "streak3");
            if (st.streak >= 7) Achievements.unlock(this, "streak7");
            if (!st.days.isEmpty() && st.days.get(0).sessions >= 5) Achievements.unlock(this, "marathon");
        }
    }

    /** Los lunes desde las 9: resumen de la semana anterior. */
    private void weeklySummary() {
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean("weekly", true)) return;
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        if (now.getDayOfWeek() != java.time.DayOfWeek.MONDAY || now.getHour() < 9) return;
        int week = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (p.getInt("sumWeek", -1) == week) return;
        p.edit().putInt("sumWeek", week).apply();
        History.Stats st = History.stats(History.load(this, System.currentTimeMillis() - 8 * History.DAY), 8);
        if (st.avgPeak < 0) return;
        alert(ID_SUMMARY, "Tu semana con Claude", "Pico promedio " + st.avgPeak + "% · " + st.sessions + " sesiones · "
                + st.limits + " límites · racha " + st.streak + " días", false, K_ACH);
    }

    private void alert(int id, String title, String text, boolean relogin) { alert(id, title, text, relogin, K_PLAIN); }

    private void alert(int id, String title, String text, boolean relogin, int kind) {
        PendingIntent pi = openApp(relogin);
        Notification.Builder b = new Notification.Builder(this, channelFor(this, kind))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(relogin ? Theme.widget(this).high : Theme.widget(this).accent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (relogin) b.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat), "Iniciar sesión", pi).build());
        getSystemService(NotificationManager.class).notify(id, b.build());
    }

    private void expired() {
        PendingIntent login = openApp(true);
        Notification n = base(-1)
                .setContentTitle("Claude: sesión vencida")
                .setContentText("Tocá para volver a iniciar sesión")
                .setColor(Theme.widget(this).high)
                .setContentIntent(login)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean("expired", false)) {
            p.edit().putBoolean("expired", true).apply();
            alert(ID_EXPIRED, "Claude: sesión vencida", "Tocá para volver a iniciar sesión y seguir viendo tu uso", true);
        }
        updateOthers();
    }

    private PendingIntent openApp(boolean relogin) {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (relogin) i.putExtra(MainActivity.EXTRA_RELOGIN, true);
        return PendingIntent.getActivity(this, relogin ? 2 : 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void show(Usage u) {
        try {
            getSystemService(NotificationManager.class).notify(NOTIF_ID, build(u));
        } catch (RuntimeException e) {
            // Si el sistema rechaza el tamaño, apaga la animación de la notificación y reintenta
            Prefs.get(this).edit().putInt("nFps", 0).putInt("nFpsIdx", 2).apply();
            getSystemService(NotificationManager.class).notify(NOTIF_ID, build(u));
        }
        updateOthers();
    }

    private void updateOthers() {
        WidgetProvider.update(this);
        try { TileService.requestListeningState(this, new ComponentName(this, QsTile.class)); } catch (Exception ignored) {}
    }

    private Notification.Builder base(int pct) {
        Theme t = Theme.widget(this);
        SharedPreferences p = Prefs.get(this);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(numberIcon(this, pct))
                .setColor(pct < 0 ? t.accent : t.level(pct))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(false));
        // Imagen grande de la notificación: la mascota o el estilo del widget (Samsung la muestra a la izquierda)
        int icon = p.getInt("nIcon", 0);
        if (icon < 2) {
            try { b.setLargeIcon(Icon.createWithBitmap(notifImage(t, icon, pct))); } catch (Exception ignored) {}
        }
        // Accesos directos (Android muestra hasta 3 botones)
        int n = 0;
        if (p.getBoolean("actRefresh", true) && n++ < 3) b.addAction(action("Actualizar",
                PendingIntent.getService(this, 1, new Intent(this, UsageService.class).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE)));
        if (p.getBoolean("goClaude", true) && n++ < 3) b.addAction(action("Ir a Claude", web(4, "https://claude.ai/new")));
        if (p.getBoolean("actCode", true) && n++ < 3) b.addAction(action("Claude Code", web(5, "https://claude.ai/code")));
        if (p.getBoolean("actUsage", false) && n++ < 3) b.addAction(action("Ver uso en Claude", web(7, "https://claude.ai/settings/usage")));
        return b;
    }

    private Notification.Action action(String label, PendingIntent pi) {
        return new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat), label, pi).build();
    }

    private PendingIntent web(int code, String url) {
        return PendingIntent.getActivity(this, code, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
    }

    private Bitmap notifImage(Theme t, int kind, int pct) {
        Usage u = Usage.load(this);
        if (kind == 1) return Art.badge(this, t, Prefs.get(this).getInt("wStyle", 0), pct, u.week, 192, 0, Mascots.current(this));
        Bitmap spr = Mascots.sprite(t, Mascots.current(this), pct, false, 0);
        Bitmap out = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(t.bg);
        c.drawCircle(80, 80, 80, bg);
        Paint pix = new Paint();
        pix.setFilterBitmap(false);
        c.drawBitmap(spr, null, new android.graphics.RectF(16, 14, 144, 142), pix);
        return out;
    }

    private Notification build(Usage u) {
        if (!u.hasData()) {
            return base(-1).setContentTitle("Claude")
                    .setContentText(u.error != null ? "Error: " + u.error + " (reintentando)" : "Conectando…").build();
        }
        RemoteViews small = new RemoteViews(getPackageName(), R.layout.notif_small);
        small.setTextViewText(R.id.n_session, u.sessionLine());
        small.setImageViewBitmap(R.id.n_sbar, Art.bar(Theme.widget(this), u.pct, 600, 12));
        small.setTextViewText(R.id.n_week, u.error != null ? "Sin conexión, reintentando…" : u.weekLine());
        RemoteViews big = new RemoteViews(getPackageName(), R.layout.notif_big);
        big.setTextViewText(R.id.n_session, u.sessionLine());
        big.setImageViewBitmap(R.id.n_sbar, Art.bar(Theme.widget(this), u.pct, 600, 14));
        big.setTextViewText(R.id.n_week, u.weekLine());
        big.setImageViewBitmap(R.id.n_wbar, Art.bar(Theme.widget(this), u.week, 600, 14));
        // Color de la paleta, ajustado al fondo del panel (claro u oscuro)
        Theme t = Theme.widget(this);
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int strong = Art.readable(t.accent, night);
        small.setTextColor(R.id.n_session, strong);
        big.setTextColor(R.id.n_session, strong);
        // Mascota animada (cuadros que el panel pasa a los FPS elegidos)
        if (u.extra != null) big.setTextViewText(R.id.n_extra, u.extra);
        else big.setViewVisibility(R.id.n_extra, View.GONE);
        Notification.Builder b = base(u.pct)
                .setContentTitle("Sesión Claude: " + u.pct + "%")
                .setContentText(u.resetLine())
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(big);
        if (u.error != null) b.setSubText("sin conexión");
        return b.build();
    }

    static Icon numberIcon(Context c, int pct) { return Icon.createWithBitmap(Art.statusIcon(c, pct)); }
}

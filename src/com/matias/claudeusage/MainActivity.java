package com.matias.claudeusage;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String EXTRA_RELOGIN = "relogin";
    private static final int REQ_EXPORT = 7, REQ_BACKUP = 8, REQ_RESTORE = 9, REQ_TREE = 10;
    static final String ACTION_REFRESH = "com.matias.claudeusage.REFRESH";
    static final String ACTION_CHART = "com.matias.claudeusage.CHART";
    private static final int SCREEN_LOGIN = 0, SCREEN_MAIN = 1, SCREEN_SETTINGS = 2;

    private final Handler handler = new Handler();
    private WebView web;
    private int screen = -1;
    private boolean addingAccount;
    private long chartSpan = History.DAY;
    private Theme th;
    private float d;

    private final Runnable poll = new Runnable() {
        public void run() { if (!checkCookie()) handler.postDelayed(this, 1500); }
    };

    @Override
    protected void onCreate(Bundle b) {
        Prefs.migrate(this);
        th = Theme.of(this);
        setTheme(th.dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        if (!th.dark) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (Prefs.cookie(this) == null) showLogin(false);
        else if (getIntent().getBooleanExtra(EXTRA_RELOGIN, false)) relogin();
        else if (b != null && b.getInt("screen", SCREEN_MAIN) == SCREEN_SETTINGS) showSettings();
        else { shortcut(getIntent()); showMain(); }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("screen", screen);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.getBooleanExtra(EXTRA_RELOGIN, false)) relogin();
        else if (shortcut(i)) showMain();
    }

    /** Atajos del ícono (mantener apretado): actualizar / ver gráfico. */
    private boolean shortcut(Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) {
            startForegroundService(new Intent(this, UsageService.class).setAction(UsageService.ACTION_REFRESH)
                    .putExtra(UsageService.EXTRA_HAPTIC, true));
            Toast.makeText(this, "Actualizando…", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (ACTION_CHART.equals(i.getAction())) { chartSpan = 7 * History.DAY; return true; }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screen == SCREEN_MAIN) showMain();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (screen == SCREEN_MAIN) handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        if (screen == SCREEN_LOGIN && web != null && web.canGoBack()) web.goBack();
        else if (screen == SCREEN_LOGIN && Prefs.cookie(this) != null) { destroyWeb(); showMain(); }
        else if (screen == SCREEN_SETTINGS) showMain();
        else super.onBackPressed();
    }

    // ---------- Login ----------

    private void showLogin(boolean add) {
        screen = SCREEN_LOGIN;
        addingAccount = add;
        handler.removeCallbacksAndMessages(null);
        LinearLayout root = column();
        TextView t = text(add ? "Iniciá sesión con la otra cuenta de Claude." :
                "Iniciá sesión en Claude. Cuando entres, la app se configura sola.", 15, th.fg);
        t.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(t);
        Button manual = button("Pegar sessionKey a mano");
        manual.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { askKey(); }
        });
        root.addView(manual);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Sin la marca "wv" para que Google permita el login
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replaceAll("Version/\\S+ ", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) { checkCookie(); }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        web.loadUrl("https://claude.ai/login");
        handler.postDelayed(poll, 1500);
    }

    private boolean checkCookie() {
        if (screen != SCREEN_LOGIN || web == null) return true;
        String c = CookieManager.getInstance().getCookie("https://claude.ai");
        if (c == null || !c.contains("sessionKey=")) return false;
        CookieManager.getInstance().flush();
        loggedIn(c, web.getSettings().getUserAgentString());
        return true;
    }

    private void askKey() {
        final EditText in = new EditText(this);
        in.setHint("sk-ant-sid01-...");
        new AlertDialog.Builder(this)
                .setTitle("sessionKey")
                .setView(in)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int w) {
                        String k = in.getText().toString().trim();
                        if (k.startsWith("sessionKey=")) k = k.substring(11);
                        if (k.isEmpty()) return;
                        loggedIn("sessionKey=" + k, null);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void loggedIn(String cookie, String ua) {
        handler.removeCallbacks(poll);
        if (addingAccount) Prefs.addAccount(this, cookie, ua); else Prefs.saveActive(this, cookie, ua);
        addingAccount = false;
        Prefs.resetLive(this);
        destroyWeb();
        startForegroundService(new Intent(this, UsageService.class).setAction(UsageService.ACTION_REFRESH));
        showMain();
    }

    private void destroyWeb() {
        if (web != null) { web.destroy(); web = null; }
    }

    private void relogin() {
        stopService(new Intent(this, UsageService.class));
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        showLogin(false);
    }

    private void addAccount() {
        saveCurrentCookies();
        stopService(new Intent(this, UsageService.class));
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        showLogin(true);
    }

    private void saveCurrentCookies() {
        String c = CookieManager.getInstance().getCookie("https://claude.ai");
        if (c != null && c.contains("sessionKey=")) Prefs.setActiveField(this, "cookie", c);
    }

    private void switchTo(int idx) {
        if (idx == Prefs.activeIndex(this)) return;
        saveCurrentCookies();
        Prefs.setActive(this, idx);
        applyAccount();
    }

    /** Carga las cookies de la cuenta activa y reinicia el servicio. */
    private void applyAccount() {
        Prefs.resetLive(this);
        final String cookie = Prefs.cookie(this);
        CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
            public void onReceiveValue(Boolean ok) {
                if (cookie != null) {
                    for (String part : cookie.split(";")) CookieManager.getInstance().setCookie("https://claude.ai", part.trim());
                }
                CookieManager.getInstance().flush();
                if (cookie != null) {
                    startForegroundService(new Intent(MainActivity.this, UsageService.class).setAction(UsageService.ACTION_REFRESH));
                }
                WidgetProvider.update(MainActivity.this);
                if (cookie == null) {
                    stopService(new Intent(MainActivity.this, UsageService.class));
                    showLogin(false);
                } else showSettings();
            }
        });
    }

    // ---------- Pantalla principal ----------

    private void showMain() {
        screen = SCREEN_MAIN;
        startForegroundService(new Intent(this, UsageService.class));
        handler.removeCallbacksAndMessages(null);
        final SharedPreferences p = Prefs.get(this);
        final boolean zen = p.getBoolean("zen", false);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new BgView(this, p.getInt("bg", 0), th), matchParent());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        frame.addView(scroll);
        if (p.getBoolean("crt", false)) frame.addView(new CrtView(this), matchParent());
        final ConfettiView confetti = new ConfettiView(this);
        frame.addView(confetti, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = column();
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        if (zen) root.setGravity(Gravity.CENTER);
        scroll.addView(root, contentParams());

        final MascotView mascot = new MascotView(this);
        boolean showMascot = p.getBoolean("mascot", true) && p.getInt("meter", 0) != 3;
        if (!zen) {
            TextView acc = text(accountName(), 13, th.dim);
            acc.setGravity(Gravity.CENTER);
            root.addView(acc);
            if (showMascot) root.addView(mascot, margins(0, 8, 0, 4));
        }
        final GaugeView gauge = new GaugeView(this);
        gauge.setZen(zen);
        root.addView(gauge);
        final TextView extra = text("", 14, th.dim);
        extra.setGravity(Gravity.CENTER);
        final ChartView chart = new ChartView(this);
        final TextView stats = card(), daily = card(), st = text("", 13, th.dim);
        final TextView predict = text("", 14, th.accent);
        predict.setGravity(Gravity.CENTER);
        final HeatmapView heat = new HeatmapView(this);
        final Button t24 = button("24 h"), t7 = button("7 días");

        if (zen) {
            Button exit = button("Salir del modo zen");
            exit.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { p.edit().putBoolean("zen", false).apply(); showMain(); }
            });
            root.addView(exit, margins(40, 24, 40, 0));
        } else {
            root.addView(extra);
            root.addView(predict, margins(0, 6, 0, 0));
            Button enough = button("¿Me alcanza?");
            enough.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askEnough(); } });
            root.addView(enough, margins(0, 12, 0, 0));

            LinearLayout tabs = new LinearLayout(this);
            tabs.setGravity(Gravity.CENTER);
            tabs.addView(t24);
            tabs.addView(t7);
            root.addView(tabs, margins(0, 16, 0, 0));
            root.addView(chart, margins(0, 4, 0, 0));
            root.addView(stats, margins(0, 16, 0, 0));
            root.addView(daily, margins(0, 12, 0, 0));
            root.addView(heat, margins(0, 16, 0, 0));
            st.setGravity(Gravity.CENTER);
            st.setPadding(0, dp(16), 0, dp(12));
            root.addView(st);
            Button refresh = button("Actualizar ahora");
            refresh.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startForegroundService(new Intent(MainActivity.this, UsageService.class).setAction(UsageService.ACTION_REFRESH));
                }
            });
            root.addView(refresh);
            Button settings = button("Ajustes");
            settings.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSettings(); } });
            root.addView(settings);
        }
        setScreen(frame);

        final Runnable[] reloadChart = new Runnable[1];
        reloadChart[0] = new Runnable() {
            public void run() {
                List<History.Point> pts = History.load(MainActivity.this, System.currentTimeMillis() - 28 * History.DAY);
                chart.set(pts, chartSpan, th);
                t24.setAlpha(chartSpan == History.DAY ? 1f : 0.5f);
                t7.setAlpha(chartSpan == History.DAY ? 0.5f : 1f);
                fillStats(stats, daily, History.stats(pts, 7), History.stats(pts, 7, 7));
                heat.set(History.heatmap(pts), th);
                predict.setText(History.prediction(MainActivity.this, Usage.load(MainActivity.this)));
            }
        };
        t24.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { chartSpan = History.DAY; reloadChart[0].run(); }
        });
        t7.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { chartSpan = 7 * History.DAY; reloadChart[0].run(); }
        });

        handler.post(new Runnable() {
            long shown = -1;
            public void run() {
                Usage u = Usage.load(MainActivity.this);
                gauge.set(u, th);
                mascot.set(u.hasData() ? u.pct : -1, th);
                extra.setText(u.extra != null ? u.extra : "");
                extra.setVisibility(u.extra != null ? View.VISIBLE : View.GONE);
                if (u.updated != shown && !zen) { shown = u.updated; reloadChart[0].run(); }
                String upd = u.updated > 0
                        ? "Actualizado hace " + ago(System.currentTimeMillis() - u.updated)
                        + (p.getBoolean("smart", true) ? " · intervalo inteligente" : " · cada 30 s")
                        : "Conectando…";
                st.setText(u.error != null ? upd + "\nÚltimo error: " + u.error : upd);
                // Confeti si la sesión se reinició hace poco
                long lr = p.getLong("lastReset", 0);
                if (p.getBoolean("confetti", true) && p.getBoolean("anim", true) && lr > p.getLong("confettiShown", 0)
                        && System.currentTimeMillis() - lr < 30 * 60_000 && confetti.getWidth() > 0) {
                    p.edit().putLong("confettiShown", lr).apply();
                    confetti.burst(th);
                    Sfx.play(MainActivity.this, Sfx.COIN);
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    /** ¿Me alcanza la sesión para X tiempo más, a mi ritmo actual? */
    private void askEnough() {
        final String[] opts = {"30 minutos", "1 hora", "2 horas"};
        final double[] hours = {0.5, 1, 2};
        new AlertDialog.Builder(this).setTitle("¿Me alcanza para…?")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int which) {
                        new AlertDialog.Builder(MainActivity.this).setTitle(opts[which])
                                .setMessage(enoughText(hours[which])).setPositiveButton("Ok", null).show();
                    }
                }).show();
    }

    private String enoughText(double h) {
        Usage u = Usage.load(this);
        if (!u.hasData()) return "Todavía no hay datos.";
        long now = System.currentTimeMillis();
        List<History.Point> pts = History.load(this, now - 90 * 60_000L);
        History.Point first = null, last = null;
        for (History.Point q : pts) {
            if (q.reset != (u.reset / 60000) * 60000) continue;
            if (first == null) first = q;
            last = q;
        }
        double rate = 0;
        if (first != null && last != null && last.ts - first.ts >= 10 * 60_000L) {
            rate = (last.pct - first.pct) / ((last.ts - first.ts) / 3_600_000.0);
        }
        int left = 100 - u.pct;
        double untilReset = u.reset > 0 ? (u.reset - now) / 3_600_000.0 : 5;
        if (left <= 0) return "Ya llegaste al límite ✖\n" + u.resetLine() + ".";
        if (rate <= 0.5) return "Sí ✔\nTu ritmo de la última hora es casi nulo. Te queda " + left + "% de la sesión.";
        String r = "Tu ritmo: " + Math.round(rate) + "% por hora. Te queda " + left + "%.\n\n";
        double usable = Math.min(h, untilReset);
        double need = rate * usable;
        if (need <= left) {
            r += "Sí ✔ En ese tiempo usarías ~" + Math.round(need) + "%.";
        } else {
            long limitAt = now + (long) (left / rate * 3_600_000);
            r += "Justo no ✖ A tu ritmo llegás al límite a las " + Usage.clock(limitAt)
                    + " (en " + Usage.left(limitAt) + ").";
        }
        if (u.reset > 0 && h > untilReset) r += "\n\nA las " + Usage.clock(u.reset) + " se reinicia la sesión y volvés a 0%.";
        return r;
    }

    private static String delta(int now, int before, boolean lowerIsBetter) {
        int dlt = now - before;
        if (dlt == 0) return " (=)";
        boolean good = lowerIsBetter ? dlt < 0 : dlt > 0;
        return " (" + (dlt > 0 ? "▲" : "▼") + Math.abs(dlt) + (good ? " ✔" : "") + ")";
    }

    private void fillStats(TextView stats, TextView daily, History.Stats s, History.Stats prev) {
        StringBuilder a = new StringBuilder("Estadísticas · 7 días\n\n");
        a.append("Pico diario promedio: ").append(s.avgPeak < 0 ? "–" : s.avgPeak + "%").append('\n');
        a.append("Sesiones usadas: ").append(s.sessions).append('\n');
        a.append("Veces que llegaste al límite: ").append(s.limits).append('\n');
        a.append("Racha sin llegar al límite: ").append(s.streak).append(s.streak == 1 ? " día" : " días").append(s.streak >= 3 ? " 🔥" : "").append('\n');
        a.append("Horario de más uso: ").append(s.topHour < 0 ? "–" : String.format(Locale.US, "%02d–%02d h", s.topHour, (s.topHour + 1) % 24));
        if (prev.avgPeak >= 0 && s.avgPeak >= 0) {
            a.append("\n\nVs. semana anterior\n");
            a.append("Pico promedio: ").append(s.avgPeak).append('%').append(delta(s.avgPeak, prev.avgPeak, true)).append('\n');
            a.append("Sesiones: ").append(s.sessions).append(delta(s.sessions, prev.sessions, false)).append('\n');
            a.append("Límites: ").append(s.limits).append(delta(s.limits, prev.limits, true));
        }
        stats.setText(a.toString());

        StringBuilder b = new StringBuilder("Resumen diario\n");
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE d/M", new Locale("es"));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        for (History.Day day : s.days) {
            String name = day.date.equals(today) ? "Hoy" : day.date.equals(today.minusDays(1)) ? "Ayer" : day.date.format(f);
            b.append('\n').append(name).append(": ");
            if (day.peak < 0) { b.append("sin datos"); continue; }
            b.append("pico ").append(day.peak).append("% · ").append(day.sessions)
                    .append(day.sessions == 1 ? " sesión" : " sesiones");
            if (day.limits > 0) b.append(" · ").append(day.limits).append(day.limits == 1 ? " límite" : " límites");
        }
        daily.setText(b.toString());
    }

    private static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        return s < 60 ? s + " s" : s < 3600 ? (s / 60) + " min" : (s / 3600) + " h";
    }

    private String accountName() {
        org.json.JSONObject a = Prefs.active(this);
        return a == null ? "" : a.optString("name", "");
    }

    // ---------- Ajustes ----------

    private void showSettings() {
        screen = SCREEN_SETTINGS;
        handler.removeCallbacksAndMessages(null);
        final SharedPreferences p = Prefs.get(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new BgView(this, p.getInt("bg", 0), th), matchParent());
        ScrollView scroll = new ScrollView(this);
        frame.addView(scroll);
        if (p.getBoolean("crt", false)) frame.addView(new CrtView(this), matchParent());
        LinearLayout root = column();
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root, contentParams());
        root.addView(text("Ajustes", 26, th.fg));

        // Cuentas
        section(root, "Cuenta");
        JSONArray accs = Prefs.accounts(this);
        RadioGroup ag = new RadioGroup(this);
        for (int i = 0; i < accs.length(); i++) {
            RadioButton rb = radio(accs.optJSONObject(i).optString("name", "Cuenta " + (i + 1)));
            rb.setId(1000 + i);
            ag.addView(rb);
        }
        ag.check(1000 + Prefs.activeIndex(this));
        ag.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) { switchTo(id - 1000); }
        });
        root.addView(ag);
        Button add = button("Agregar otra cuenta");
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { addAccount(); } });
        root.addView(add);
        Button re = button("Volver a iniciar sesión en esta cuenta");
        re.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { relogin(); } });
        root.addView(re);
        Button del = button("Quitar esta cuenta");
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("¿Quitar \"" + accountName() + "\" de la app? Se borra su historial.")
                        .setPositiveButton("Quitar", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int w) {
                                History.file(MainActivity.this).delete();
                                Prefs.removeActive(MainActivity.this);
                                applyAccount();
                            }
                        })
                        .setNegativeButton("Cancelar", null).show();
            }
        });
        root.addView(del);

        // Tema
        section(root, "Tema");
        RadioGroup tg = new RadioGroup(this);
        String[] themes = {"Como el sistema", "Claro", "Oscuro"};
        for (int i = 0; i < 3; i++) { RadioButton rb = radio(themes[i]); rb.setId(2000 + i); tg.addView(rb); }
        tg.check(2000 + p.getInt("theme", 0));
        tg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                p.edit().putInt("theme", id - 2000).apply();
                recreate();
            }
        });
        root.addView(tg);

        // Apariencia
        section(root, "Apariencia");
        root.addView(text("Paleta", 15, th.fg));
        root.addView(choiceList(Theme.PALETTES, "palette", 5000, true));
        root.addView(text("Tipografía", 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choiceList(new String[]{"Normal", "Pixel"}, "font", 5100, true));
        root.addView(text("Medidor de la app", 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choiceList(GaugeView.METERS, "meter", 5200, true));
        root.addView(toggle("Modo zen (solo el número y el tiempo)", "zen", false));
        root.addView(text("Fondo animado", 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choiceList(BgView.KINDS, "bg", 5300, true));
        root.addView(toggle("Filtro retro CRT", "crt", false));
        root.addView(toggle("Transiciones animadas entre pantallas", "transitions", true));

        // Mascota
        section(root, "Mascota");
        root.addView(text("Etapa: " + Mascots.STAGES[Mascots.stage(this)] + " · " + p.getInt("xpDays", 0)
                + " días de uso (bebé a los 3, adulta a los 10, legendaria a los 30)", 14, th.dim));
        root.addView(toggle("Evolución (huevo → bebé → adulta → legendaria)", "evolve", true));
        root.addView(toggle("Una mascota distinta cada día", "randomDaily", false));
        root.addView(choiceList(Mascots.names(), "mascotId", 6000, false));
        root.addView(text("Accesorio", 15, th.fg), margins(0, 8, 0, 0));
        String[] accNames = new String[Achievements.ACCESSORIES.length];
        for (int i = 0; i < accNames.length; i++)
            accNames[i] = Achievements.ACCESSORIES[i] + (Achievements.accessoryUnlocked(this, i) ? "" : " 🔒");
        root.addView(choiceList(accNames, "accessory", 6100, false));
        root.addView(toggle("Mostrar la mascota arriba", "mascot", true));
        root.addView(toggle("Ícono de la app = tu mascota", "mascotIcon", false));
        root.addView(toggle("Sonidos 8-bit (avisos, tocar la mascota, confeti)", "sounds", true));

        // Logros
        section(root, "Logros · " + Achievements.count(this) + "/" + Achievements.ALL.length);
        StringBuilder ach = new StringBuilder();
        for (String[] a : Achievements.ALL) {
            boolean got = Achievements.has(this, a[0]);
            ach.append(got ? "★ " : "☆ ").append(a[1]).append(" — ").append(a[2]);
            if (!a[3].isEmpty()) ach.append(" · desbloquea ").append(a[3]);
            ach.append('\n');
        }
        TextView achT = text(ach.toString().trim(), 14, th.fg);
        achT.setLineSpacing(0, 1.25f);
        root.addView(achT);
        // Barra de estado y notificación
        section(root, "Barra de estado y notificación");
        root.addView(text("Android pinta los íconos de la barra de estado del mismo color que la hora. Para que el número no se confunda, elegí una forma distinta:", 13, th.dim));
        root.addView(choiceList(new String[]{"Número solo", "Número en recuadro relleno", "Número dentro de un anillo", "Silueta de la mascota"}, "statusIcon", 7100, false));
        root.addView(toggle("Mascota animada en la notificación", "nMascot", true));
        root.addView(text("Fluidez de la mascota en la notificación", 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choiceList(new String[]{"60 FPS", "30 FPS", "Quieta"}, "nFpsIdx", 7200, false));

        // Animaciones
        section(root, "Animaciones");
        root.addView(toggle("Animaciones (medidor, gráfico, mascota)", "anim", true));
        root.addView(toggle("Pulso cuando pasás el 90%", "pulse", true));
        root.addView(toggle("Confeti cuando se reinicia la sesión", "confetti", true));
        root.addView(toggle("Segundos en la cuenta regresiva", "seconds", true));

        // Actualización y avisos
        section(root, "Actualización y avisos");
        root.addView(toggle("Intervalo inteligente (ahorra batería)\n30 s mientras usás Claude, más lento si está quieto o con la pantalla apagada",
                "smart", true));
        root.addView(toggle("Avisar 5 min antes del reinicio de la sesión", "preReset", true));
        root.addView(toggle("Resumen semanal los lunes", "weekly", true));
        int[] qw = History.quietWindow(this);
        root.addView(toggle("No molestar inteligente: avisos en silencio mientras dormís\n"
                + (qw != null ? String.format(java.util.Locale.US, "Detectado: %02d–%02d h", qw[0], qw[1])
                : "Todavía aprendiendo tu horario (por ahora 01–08 h)"), "smartDnd", true));
        root.addView(toggle("Vibrar al actualizar desde el widget o Ajustes rápidos", "haptic", true));
        root.addView(toggle("Botón \"Ir a Claude\" en la notificación", "goClaude", true));

        // Widget
        section(root, "Widget de inicio");
        final TextView alphaLbl = text("", 15, th.fg);
        root.addView(alphaLbl);
        SeekBar sb = new SeekBar(this);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(th.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(th.accent));
        sb.setMax(100);
        sb.setProgress(p.getInt("wAlpha", 90));
        alphaLbl.setText("Opacidad del fondo: " + sb.getProgress() + "%");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { alphaLbl.setText("Opacidad del fondo: " + v + "%"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {
                p.edit().putInt("wAlpha", s.getProgress()).apply();
                WidgetProvider.update(MainActivity.this);
            }
        });
        root.addView(sb, margins(0, 4, 0, 12));
        root.addView(text("Estilo", 15, th.fg));
        root.addView(choiceList(Art.STYLES, "wStyle", 3000, false));
        root.addView(toggle("Minimalista (solo la imagen, en cualquier tamaño)", "wMinimal", false));
        root.addView(toggle("Animar el widget", "wAnim", true));
        root.addView(text("Fluidez de la animación del widget", 15, th.fg), margins(0, 8, 0, 0));
        final String[] fpsOpts = {"60 FPS", "30 FPS"};
        RadioGroup fg = new RadioGroup(this);
        fg.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < 2; i++) { RadioButton rb = radio(fpsOpts[i]); rb.setId(7000 + i); fg.addView(rb); }
        fg.check(p.getInt("wFps", 60) >= 60 ? 7000 : 7001);
        fg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                p.edit().putInt("wFps", id == 7000 ? 60 : 30).apply();
                WidgetProvider.update(MainActivity.this);
            }
        });
        root.addView(fg);
        int actual = p.getInt("wFpsActual", -1);
        if (actual >= 0) root.addView(text("Ahora el widget anima a " + (actual > 2 ? actual + " FPS" : actual == 0 ? "0 FPS (quieto)" : "2 cuadros (tu launcher no aceptó más)"), 13, th.dim));
        root.addView(text("Al tocar el widget", 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choice(new String[]{"Actualizar", "Abrir la app"}, "wTap", 4000));

        // Datos
        section(root, "Datos");
        Button exp = button("Exportar historial (CSV para Excel)");
        exp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/csv").putExtra(Intent.EXTRA_TITLE, "claude-uso.csv");
                startActivityForResult(i, REQ_EXPORT);
            }
        });
        root.addView(exp);
        Button bak = button("Copia de seguridad (ajustes + historial)");
        bak.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json").putExtra(Intent.EXTRA_TITLE, "claude-uso-backup.json"), REQ_BACKUP);
            }
        });
        root.addView(bak);
        Button res = button("Restaurar copia de seguridad");
        res.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"), REQ_RESTORE);
            }
        });
        root.addView(res);
        String tree = p.getString("backupTree", null);
        long lastAuto = p.getLong("lastAutoBackup", 0);
        root.addView(toggle("Respaldo automático semanal", "autoBackup", false));
        root.addView(text(tree == null ? "Carpeta: sin elegir" : "Carpeta: " + Uri.decode(Uri.parse(tree).getLastPathSegment())
                + (lastAuto > 0 ? " · último: " + Usage.dayClock(lastAuto) : ""), 13, th.dim));
        Button folder = button("Elegir carpeta para los respaldos");
        folder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE); }
        });
        root.addView(folder);
        Button now = button("Respaldar ahora en esa carpeta");
        now.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean ok = Backup.auto(MainActivity.this);
                Toast.makeText(MainActivity.this, ok ? "Respaldo guardado" : "Elegí una carpeta primero", Toast.LENGTH_SHORT).show();
                if (ok) showSettings();
            }
        });
        root.addView(now);

        // Otros
        section(root, "Otros");
        root.addView(text("• Pantalla de bloqueo: si tu versión de One UI permite widgets ahí, mantené apretado el reloj del bloqueo → Widgets → Claude Uso.\n"
                + "• Atajos: mantené apretado el ícono de la app para Actualizar, Ver 7 días o Abrir Claude.\n"
                + "• Botón en Ajustes rápidos: bajá la cortina, tocá el lápiz (editar) y arrastrá \"Claude\".\n"
                + "• Pantalla de bloqueo: la notificación se ve completa. En el Always On Display aparece el número.\n"
                + "• Galaxy Watch: los avisos (75/90/100%, reinicio) llegan al reloj si tiene activadas las notificaciones de esta app.",
                14, th.dim));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Button bat = button("Permitir que funcione en segundo plano");
            bat.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                }
            });
            root.addView(bat, margins(0, 12, 0, 0));
        }
        Button stop = button("Apagar widget y notificación");
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, UsageService.class));
                finish();
            }
        });
        root.addView(stop, margins(0, 12, 0, 0));
        Button back = button("Volver");
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showMain(); } });
        root.addView(back);
        setScreen(frame);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_BACKUP) { Backup.write(this, data.getData()); return; }
        if (req == REQ_TREE) {
            getContentResolver().takePersistableUriPermission(data.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Prefs.get(this).edit().putString("backupTree", data.getData().toString()).putBoolean("autoBackup", true).apply();
            Backup.auto(this);
            showSettings();
            return;
        }
        if (req == REQ_RESTORE) {
            if (Backup.read(this, data.getData())) recreate();
            return;
        }
        if (req != REQ_EXPORT) return;
        try {
            List<History.Point> pts = History.load(this, 0);
            DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd;HH:mm");
            StringBuilder sb = new StringBuilder("fecha;hora;sesion_%;semana_%;reinicio_sesion\n");
            for (History.Point q : pts) {
                sb.append(Instant.ofEpochMilli(q.ts).atZone(ZoneId.systemDefault()).format(df)).append(';')
                        .append(q.pct).append(';').append(q.week).append(';')
                        .append(q.reset > 0 ? Usage.clock(q.reset) : "").append('\n');
            }
            OutputStream out = getContentResolver().openOutputStream(data.getData());
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
            Toast.makeText(this, "Exportado: " + pts.size() + " registros", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Helpers de UI ----------

    private int dp(int v) { return (int) (v * d); }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(th.bg);
        return l;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Theme.font(false));
        return t;
    }

    private TextView card() {
        TextView t = text("", 14, th.fg);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(th.card);
        g.setCornerRadius(16 * d);
        t.setBackground(g);
        return t;
    }

    private void section(LinearLayout root, String title) {
        TextView t = text(title, 13, th.accent);
        t.setTypeface(Theme.font(true));
        t.setAllCaps(true);
        root.addView(t, margins(0, 24, 0, 6));
    }

    private Button button(String s) {
        Button b = new Button(this) {
            @Override
            public boolean performClick() {
                if (Prefs.get(getContext()).getBoolean("haptic", true)) performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                return super.performClick();
            }
        };
        b.setText(s);
        b.setAllCaps(false);
        b.setTypeface(Theme.font(false));
        return b;
    }

    private RadioButton radio(String s) {
        RadioButton r = new RadioButton(this);
        r.setText(s);
        r.setTextColor(th.fg);
        r.setTextSize(15);
        r.setTypeface(Theme.font(false));
        r.setButtonTintList(tints());
        return r;
    }

    private RadioGroup choice(String[] opts, final String key, final int base) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < opts.length; i++) { RadioButton rb = radio(opts[i]); rb.setId(base + i); g.addView(rb); }
        g.check(base + Prefs.get(this).getInt(key, 0));
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup gr, int id) {
                Prefs.get(MainActivity.this).edit().putInt(key, id - base).apply();
                WidgetProvider.update(MainActivity.this);
            }
        });
        return g;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Cambia de pantalla con una transición suave (fade + desliz). */
    private void setScreen(View v) {
        setContentView(v);
        SharedPreferences p = Prefs.get(this);
        if (p.getBoolean("transitions", true) && p.getBoolean("anim", true)) {
            v.setAlpha(0f);
            v.setTranslationY(dp(18));
            v.animate().alpha(1f).translationY(0).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    /** Ícono del launcher: el clásico o el de la mascota elegida (activity-alias). */
    private void applyIcon() {
        SharedPreferences p = Prefs.get(this);
        int want = p.getBoolean("mascotIcon", false) ? Mascots.current(this) : -1;
        if (p.getInt("iconApplied", -1) == want) return;
        PackageManager pm = getPackageManager();
        for (int i = -1; i < Mascots.count(); i++) {
            android.content.ComponentName cn = new android.content.ComponentName(this,
                    getPackageName() + (i < 0 ? ".IconDefault" : ".Icon" + i));
            pm.setComponentEnabledSetting(cn, i == want ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        p.edit().putInt("iconApplied", want).apply();
    }

    /** Color de los indicadores: marcado = acento de la paleta, sin marcar = tenue (visible en oscuro). */
    private android.content.res.ColorStateList tints() {
        return new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{th.accent, th.dim});
    }

    /** Opciones en lista vertical. recreate = la app se redibuja al cambiar (paleta, fuente). */
    private RadioGroup choiceList(String[] opts, final String key, final int base, final boolean recreate) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < opts.length; i++) { RadioButton rb = radio(opts[i]); rb.setId(base + i); g.addView(rb); }
        g.check(base + Prefs.get(this).getInt(key, 0));
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup gr, int id) {
                if ("accessory".equals(key) && !Achievements.accessoryUnlocked(MainActivity.this, id - base)) {
                    Toast.makeText(MainActivity.this, "Todavía no lo desbloqueaste. Mirá los logros.", Toast.LENGTH_SHORT).show();
                    gr.check(base + Prefs.get(MainActivity.this).getInt(key, 0));
                    return;
                }
                Prefs.get(MainActivity.this).edit().putInt(key, id - base).apply();
                if ("nFpsIdx".equals(key)) Prefs.get(MainActivity.this).edit().putInt("nFps", new int[]{60, 30, 0}[id - base]).apply();
                if ("mascotId".equals(key)) { Achievements.sawMascot(MainActivity.this, id - base); applyIcon(); }
                WidgetProvider.update(MainActivity.this);
                startForegroundService(new Intent(MainActivity.this, UsageService.class));
                if (recreate) recreate();
            }
        });
        return g;
    }

    /** Ancho máximo del contenido (Samsung DeX, tablets, pantalla horizontal). */
    private FrameLayout.LayoutParams contentParams() {
        int wdp = getResources().getConfiguration().screenWidthDp;
        int w = wdp > 640 ? dp(600) : ViewGroup.LayoutParams.MATCH_PARENT;
        return new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
    }

    private Switch toggle(String label, final String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(th.fg);
        s.setTextSize(15);
        s.setTypeface(Theme.font(false));
        s.setThumbTintList(tints());
        s.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Art.fade(th.accent, 140), th.track}));
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(Prefs.get(this).getBoolean(key, def));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.get(MainActivity.this).edit().putBoolean(key, on).apply();
                if ("zen".equals(key)) { if (on) Achievements.unlock(MainActivity.this, "zen"); return; }
                if ("mascot".equals(key) || "sounds".equals(key) || "smartDnd".equals(key) || "autoBackup".equals(key)) return;
                if ("crt".equals(key) || "evolve".equals(key)) { showSettings(); return; }
                if ("mascotIcon".equals(key)) {
                    // Sincroniza el ícono de la barra de estado con el de la app
                    Prefs.get(MainActivity.this).edit().putInt("statusIcon", on ? 3 : 1).apply();
                    applyIcon();
                    WidgetProvider.update(MainActivity.this);
                    startForegroundService(new Intent(MainActivity.this, UsageService.class));
                    showSettings();
                    return;
                }
                if ("nMascot".equals(key)) { startForegroundService(new Intent(MainActivity.this, UsageService.class)); return; }
                if ("randomDaily".equals(key)) { applyIcon(); WidgetProvider.update(MainActivity.this); return; }
                if ("wMinimal".equals(key) || "wAnim".equals(key)) { WidgetProvider.update(MainActivity.this); return; }
                if ("smart".equals(key)) {
                    startForegroundService(new Intent(MainActivity.this, UsageService.class).setAction(UsageService.ACTION_REFRESH));
                }
            }
        });
        return s;
    }
}

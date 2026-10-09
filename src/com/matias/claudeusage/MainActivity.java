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
        applyRefreshRate();
        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        if (!th.dark) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (needsUnlock()) screen = SCREEN_LOCKED;   // onResume muestra el bloqueo antes que cualquier dato
        else if (Prefs.cookie(this) == null) showLogin(false);
        else if (getIntent().getBooleanExtra(EXTRA_RELOGIN, false)) confirmRelogin();
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
        if (i.getBooleanExtra(EXTRA_RELOGIN, false)) confirmRelogin();
        else if (shortcut(i)) showMain();
    }

    /** Atajos del ícono (mantener apretado): actualizar / ver gráfico. */
    private boolean shortcut(Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) {
            startForegroundService(new Intent(this, UsageService.class).setAction(UsageService.ACTION_REFRESH)
                    .putExtra(UsageService.EXTRA_HAPTIC, true));
            Toast.makeText(this, L.t("Actualizando…"), Toast.LENGTH_SHORT).show();
            return true;
        }
        if (ACTION_CHART.equals(i.getAction())) { chartSpan = 7 * History.DAY; return true; }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsUnlock()) { showLocked(); return; }
        if (screen == SCREEN_MAIN || screen == SCREEN_LOCKED) render();
    }

    @Override
    protected void onStop() {
        super.onStop();
        leftAt = System.currentTimeMillis();
    }

    // ---------- Bloqueo con huella o PIN ----------

    private static final int SCREEN_LOCKED = 3;
    private static boolean unlocked;
    private static long leftAt;

    /** Se vuelve a pedir si la app estuvo más de 30 s en segundo plano. */
    private boolean needsUnlock() {
        if (!Prefs.get(this).getBoolean("appLock", false) || Build.VERSION.SDK_INT < 29) return false;
        if (unlocked && leftAt > 0 && System.currentTimeMillis() - leftAt > 30_000) unlocked = false;
        return !unlocked;
    }

    private void showLocked() {
        screen = SCREEN_LOCKED;
        handler.removeCallbacksAndMessages(null);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView t = text(L.t("Claude Uso bloqueada"), 22, th.fg);
        t.setGravity(Gravity.CENTER);
        root.addView(t);
        Button b = button(L.t("Desbloquear"));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { askUnlock(); } });
        root.addView(b, margins(0, 24, 0, 0));
        setContentView(root);
        askUnlock();
    }

    private void askUnlock() {
        if (Build.VERSION.SDK_INT < 29) return;
        android.hardware.biometrics.BiometricPrompt.Builder b = new android.hardware.biometrics.BiometricPrompt.Builder(this)
                .setTitle(L.t("Claude Uso bloqueada"))
                .setSubtitle(L.t("Desbloqueá para ver tu uso"));
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            b.setDeviceCredentialAllowed(true);
        }
        b.build().authenticate(new android.os.CancellationSignal(), getMainExecutor(),
                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                        unlocked = true;
                        leftAt = 0;
                        render();
                    }
                });
    }

    /** Muestra la pantalla que corresponde (después de desbloquear). */
    private void render() {
        if (Prefs.cookie(this) == null) showLogin(false);
        else showMain();
    }

    /** Cerrar sesión pedido desde afuera (notificación): se confirma antes. */
    private void confirmRelogin() {
        new AlertDialog.Builder(this)
                .setMessage(L.t("¿Volver a iniciar sesión? Se cierra la sesión actual en esta cuenta."))
                .setPositiveButton(L.t("Iniciar sesión"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { relogin(); }
                })
                .setNegativeButton(L.t("Cancelar"), null)
                .show();
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
        TextView t = text(add ? L.t("Iniciá sesión con la otra cuenta de Claude.") :
                L.t("Iniciá sesión en Claude. Cuando entres, la app se configura sola."), 15, th.fg);
        t.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(t);
        Button manual = button(L.t("Pegar sessionKey a mano"));
        manual.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { askKey(); }
        });
        root.addView(manual);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
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
                .setPositiveButton(L.t("Guardar"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int w) {
                        String k = in.getText().toString().trim();
                        if (k.startsWith("sessionKey=")) k = k.substring(11);
                        if (k.isEmpty()) return;
                        // Solo letras, números, guion y guion bajo: evita pegar texto o código extraño
                        if (!k.matches("[A-Za-z0-9_\\-]{20,400}")) {
                            Toast.makeText(MainActivity.this, L.t("La sessionKey no tiene un formato válido."), Toast.LENGTH_LONG).show();
                            return;
                        }
                        loggedIn("sessionKey=" + k, null);
                    }
                })
                .setNegativeButton(L.t("Cancelar"), null)
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
        final Button t24 = button("24 h"), t7 = button(L.t("7 días"));

        if (zen) {
            Button exit = button(L.t("Salir del modo zen"));
            exit.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { p.edit().putBoolean("zen", false).apply(); showMain(); }
            });
            root.addView(exit, margins(40, 24, 40, 0));
        } else {
            root.addView(extra);
            root.addView(predict, margins(0, 6, 0, 0));
            Button enough = button(L.t("¿Me alcanza?"));
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
            Button refresh = button(L.t("Actualizar ahora"));
            refresh.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startForegroundService(new Intent(MainActivity.this, UsageService.class).setAction(UsageService.ACTION_REFRESH));
                }
            });
            root.addView(refresh);
            Button settings = button(L.t("Ajustes"));
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
                        ? L.t("Actualizado hace ") + ago(System.currentTimeMillis() - u.updated)
                        + (p.getBoolean("smart", true) ? L.t(" · intervalo inteligente") : L.t(" · cada 30 s"))
                        : L.t("Conectando…");
                st.setText(u.error != null ? upd + L.t("\nÚltimo error: ") + u.error : upd);
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
        final String[] opts = {L.t("30 minutos"), L.t("1 hora"), L.t("2 horas")};
        final double[] hours = {0.5, 1, 2};
        new AlertDialog.Builder(this).setTitle(L.t("¿Me alcanza para…?"))
                .setItems(opts, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int which) {
                        new AlertDialog.Builder(MainActivity.this).setTitle(opts[which])
                                .setMessage(enoughText(hours[which])).setPositiveButton(L.t("Ok"), null).show();
                    }
                }).show();
    }

    private String enoughText(double h) {
        Usage u = Usage.load(this);
        if (!u.hasData()) return L.t("Todavía no hay datos.");
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
        if (left <= 0) return L.t("Ya llegaste al límite ✖\n") + u.resetLine() + ".";
        if (rate <= 0.5) return L.t("Sí ✔\nTu ritmo de la última hora es casi nulo. Te queda ") + left + L.t("% de la sesión.");
        String r = L.t("Tu ritmo: ") + Math.round(rate) + L.t("% por hora. Te queda ") + left + L.t("%.\n\n");
        double usable = Math.min(h, untilReset);
        double need = rate * usable;
        if (need <= left) {
            r += L.t("Sí ✔ En ese tiempo usarías ~") + Math.round(need) + "%.";
        } else {
            long limitAt = now + (long) (left / rate * 3_600_000);
            r += L.t("Justo no ✖ A tu ritmo llegás al límite a las ") + Usage.clock(limitAt)
                    + L.t(" (en ") + Usage.left(limitAt) + ").";
        }
        if (u.reset > 0 && h > untilReset) r += L.t("\n\nA las ") + Usage.clock(u.reset) + L.t(" se reinicia la sesión y volvés a 0%.");
        return r;
    }

    private static String delta(int now, int before, boolean lowerIsBetter) {
        int dlt = now - before;
        if (dlt == 0) return " (=)";
        boolean good = lowerIsBetter ? dlt < 0 : dlt > 0;
        return " (" + (dlt > 0 ? "▲" : "▼") + Math.abs(dlt) + (good ? " ✔" : "") + ")";
    }

    private void fillStats(TextView stats, TextView daily, History.Stats s, History.Stats prev) {
        StringBuilder a = new StringBuilder(L.t("Estadísticas · 7 días\n\n"));
        a.append(L.t("Pico diario promedio: ")).append(s.avgPeak < 0 ? "–" : s.avgPeak + "%").append('\n');
        a.append(L.t("Sesiones usadas: ")).append(s.sessions).append('\n');
        a.append(L.t("Veces que llegaste al límite: ")).append(s.limits).append('\n');
        a.append(L.t("Racha sin llegar al límite: ")).append(s.streak).append(s.streak == 1 ? L.t(" día") : L.t(" días")).append(s.streak >= 3 ? " 🔥" : "").append('\n');
        a.append(L.t("Horario de más uso: ")).append(s.topHour < 0 ? "–" : String.format(Locale.US, "%02d–%02d h", s.topHour, (s.topHour + 1) % 24));
        if (prev.avgPeak >= 0 && s.avgPeak >= 0) {
            a.append(L.t("\n\nVs. semana anterior\n"));
            a.append(L.t("Pico promedio: ")).append(s.avgPeak).append('%').append(delta(s.avgPeak, prev.avgPeak, true)).append('\n');
            a.append(L.t("Sesiones: ")).append(s.sessions).append(delta(s.sessions, prev.sessions, false)).append('\n');
            a.append(L.t("Límites: ")).append(s.limits).append(delta(s.limits, prev.limits, true));
        }
        stats.setText(a.toString());

        StringBuilder b = new StringBuilder(L.t("Resumen diario\n"));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE d/M", Locale.getDefault());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        for (History.Day day : s.days) {
            String name = day.date.equals(today) ? L.t("Hoy") : day.date.equals(today.minusDays(1)) ? L.t("Ayer") : day.date.format(f);
            b.append('\n').append(name).append(": ");
            if (day.peak < 0) { b.append(L.t("sin datos")); continue; }
            b.append(L.t("pico ")).append(day.peak).append("% · ").append(day.sessions)
                    .append(day.sessions == 1 ? L.t(" sesión") : L.t(" sesiones"));
            if (day.limits > 0) b.append(" · ").append(day.limits).append(day.limits == 1 ? L.t(" límite") : L.t(" límites"));
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

    /** Posición de Ajustes a restaurar después de un cambio (sobrevive a recreate()). */
    private static int keepScroll = -1;
    /** Qué desplegables estaban abiertos (por clave). */
    private static final java.util.Map<String, Boolean> openState = new java.util.HashMap<String, Boolean>();
    private ScrollView settingsScroll;

    @Override
    public void recreate() {
        if (screen == SCREEN_SETTINGS && settingsScroll != null) keepScroll = settingsScroll.getScrollY();
        super.recreate();
    }

    private void showSettings() {
        if (screen == SCREEN_SETTINGS && settingsScroll != null) keepScroll = settingsScroll.getScrollY();
        screen = SCREEN_SETTINGS;
        handler.removeCallbacksAndMessages(null);
        final SharedPreferences p = Prefs.get(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(th.bg);
        frame.addView(new BgView(this, p.getInt("bg", 0), th), matchParent());
        final ScrollView scroll = new ScrollView(this);
        settingsScroll = scroll;
        frame.addView(scroll);
        if (p.getBoolean("crt", false)) frame.addView(new CrtView(this), matchParent());
        LinearLayout root = column();
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root, contentParams());
        root.addView(text(L.t("Ajustes"), 26, th.fg));

        // Cuentas
        section(root, L.t("Cuenta"));
        JSONArray accs = Prefs.accounts(this);
        RadioGroup ag = new RadioGroup(this);
        for (int i = 0; i < accs.length(); i++) {
            RadioButton rb = radio(accs.optJSONObject(i).optString("name", L.t("Cuenta ") + (i + 1)));
            rb.setId(1000 + i);
            ag.addView(rb);
        }
        ag.check(1000 + Prefs.activeIndex(this));
        ag.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) { switchTo(id - 1000); }
        });
        root.addView(ag);
        Button add = button(L.t("Agregar otra cuenta"));
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { addAccount(); } });
        root.addView(add);
        Button re = button(L.t("Volver a iniciar sesión en esta cuenta"));
        re.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { relogin(); } });
        root.addView(re);
        Button del = button(L.t("Quitar esta cuenta"));
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(L.t("¿Quitar \"") + accountName() + L.t("\" de la app? Se borra su historial."))
                        .setPositiveButton(L.t("Quitar"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dlg, int w) {
                                History.file(MainActivity.this).delete();
                                Prefs.removeActive(MainActivity.this);
                                applyAccount();
                            }
                        })
                        .setNegativeButton(L.t("Cancelar"), null).show();
            }
        });
        root.addView(del);

        // Tema
        section(root, L.t("Tema"));
        RadioGroup tg = new RadioGroup(this);
        String[] themes = {L.t("Como el sistema"), L.t("Claro"), L.t("Oscuro")};
        for (int i = 0; i < 3; i++) { RadioButton rb = radio(themes[i]); rb.setId(2000 + i); tg.addView(rb); }
        tg.check(2000 + p.getInt("theme", 0));
        tg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                p.edit().putInt("theme", id - 2000).apply();
                recreate();
            }
        });
        root.addView(tg);
        root.addView(toggle(L.t("Negro puro AMOLED en modo oscuro (ahorra batería)"), "amoled", false));

        // Apariencia
        section(root, L.t("Apariencia"));
        groupedChoice(root, L.t("Paleta"), Theme.PAL_CATS, Theme.PAL_GROUPS, Theme.PALETTES, p.getInt("palette", 0), new Pick() {
            public void picked(int i) { p.edit().putInt("palette", i).apply(); refreshAll(); recreate(); }
        });
        groupedChoice(root, L.t("Tipografía"), null, null, Theme.FONTS, p.getInt("font", 0), new Pick() {
            public void picked(int i) { p.edit().putInt("font", i).apply(); refreshAll(); recreate(); }
        });
        groupedChoice(root, L.t("Medidor de la app"), null, null, GaugeView.METERS, p.getInt("meter", 0), new Pick() {
            public void picked(int i) { p.edit().putInt("meter", i).apply(); }
        });
        root.addView(toggle(L.t("Modo zen (solo el número y el tiempo)"), "zen", false));
        groupedChoice(root, L.t("Fondo animado"), null, null, BgView.KINDS, p.getInt("bg", 0), new Pick() {
            public void picked(int i) { p.edit().putInt("bg", i).apply(); showSettings(); }
        });
        root.addView(toggle(L.t("Filtro retro CRT"), "crt", false));
        root.addView(toggle(L.t("Transiciones animadas entre pantallas"), "transitions", true));

        // Mascota
        section(root, L.t("Mascota"));
        root.addView(toggle(L.t("Estilo chibi (más chiquita, tipo bebé)"), "chibi", false));
        root.addView(toggle(L.t("Una mascota distinta cada día"), "randomDaily", false));
        groupedChoice(root, L.t("Elegir mascota"), MascotData.CAT_NAMES, MascotData.CATS, Mascots.names(), Mascots.current(this), new Pick() {
            public void picked(int i) {
                p.edit().putString("mascotKey", MascotData.KEYS[i]).apply();
                Achievements.sawMascot(MainActivity.this, i);
                applyIcon();
                refreshAll();
            }
        });
        root.addView(toggle(L.t("Mostrar la mascota arriba"), "mascot", true));
        root.addView(toggle(L.t("Ícono de la app = tu mascota"), "mascotIcon", false));
        root.addView(toggle(L.t("Sonidos 8-bit (avisos, tocar la mascota, confeti)"), "sounds", true));

        // Logros
        section(root, L.t("Logros · ") + Achievements.count(this) + L.t(" de ") + Achievements.ALL.length);
        StringBuilder got = new StringBuilder(), todo = new StringBuilder();
        int pending = 0;
        for (String[] a : Achievements.ALL) {
            if (Achievements.has(this, a[0])) got.append("★ ").append(a[1]).append(" — ").append(a[2]).append('\n');
            else { todo.append("☆ ").append(a[1]).append(" — ").append(a[2]).append('\n'); pending++; }
        }
        TextView achT = text(got.length() > 0 ? got.toString().trim() : L.t("Todavía no conseguiste ninguno."), 14, th.fg);
        achT.setLineSpacing(0, 1.25f);
        root.addView(achT);
        if (pending > 0) {
            LinearLayout hidden = collapsible(root, "logros", L.t("Ver los que podés conseguir (") + pending + ")", false);
            TextView t2 = text(todo.toString().trim(), 14, th.dim);
            t2.setLineSpacing(0, 1.25f);
            hidden.addView(t2);
        }
        // Barra de estado y notificación
        section(root, L.t("Barra de estado y notificación"));
        root.addView(text(L.t("Android pinta los íconos de la barra de estado del mismo color que la hora. Para que el número no se confunda, elegí una forma distinta:"), 13, th.dim));
        if (p.getInt("statusIcon", 1) > 2) p.edit().putInt("statusIcon", 1).apply();
        root.addView(choiceList(new String[]{L.t("Número grande"), L.t("Número con marco"), L.t("Número dentro de un anillo")}, "statusIcon", 7100, false));
        root.addView(text(L.t("Imagen de la notificación"), 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choiceList(new String[]{L.t("Tu mascota"), L.t("El estilo del widget"), L.t("Solo el ícono de la app")}, "nIcon", 7300, false));
        root.addView(text(L.t("Botones de la notificación (hasta 3)"), 15, th.fg), margins(0, 8, 0, 0));
        root.addView(toggle(L.t("Actualizar"), "actRefresh", true));
        root.addView(toggle(L.t("Ir a Claude"), "goClaude", true));
        root.addView(toggle("Claude Code", "actCode", true));
        root.addView(toggle(L.t("Ver uso en Claude"), "actUsage", false));

        // Animaciones
        section(root, L.t("Animaciones"));
        root.addView(toggle(L.t("Animaciones (medidor, gráfico, mascota)"), "anim", true));
        groupedChoice(root, L.t("Fluidez de la app"), null, null, Fps.labels(this), indexOfFps(p.getInt("appFps", 0)), new Pick() {
            public void picked(int i) { p.edit().putInt("appFps", Fps.OPTIONS[i]).apply(); applyRefreshRate(); }
        });
        root.addView(toggle(L.t("Pulso cuando pasás el 90%"), "pulse", true));
        root.addView(toggle(L.t("Confeti cuando se reinicia la sesión"), "confetti", true));
        root.addView(toggle(L.t("Segundos en la cuenta regresiva"), "seconds", true));

        // Actualización y avisos
        section(root, L.t("Actualización y avisos"));
        root.addView(toggle(L.t("Intervalo inteligente (ahorra batería)\n30 s mientras usás Claude, más lento si está quieto o con la pantalla apagada"),
                "smart", true));
        root.addView(toggle(L.t("Avisar 5 min antes del reinicio de la sesión"), "preReset", true));
        root.addView(toggle(L.t("Resumen semanal los lunes"), "weekly", true));
        root.addView(toggle(L.t("Vibraciones con patrón\n75%: 2 cortas · 90%: 3 cortas · 100%: 1 larga · reinicio: corta + larga"), "vibePatterns", true));
        root.addView(toggle(L.t("Modo ahorro automático\nCon menos de 20% de batería (sin cargar) o con el ahorro de energía activo: 30 FPS, sin fondo animado y widget quieto")
                + (Power.saving(this) ? L.t("\nAhora: ACTIVO") : ""), "autoSave", true));

        // Modos del teléfono y automatización
        section(root, L.t("Modos y automatización"));
        root.addView(toggle(L.t("Modo \"Concentración Claude\": activa No molestar al llegar al límite y lo apaga solo al reiniciarse la sesión"), "focusMode", false));
        root.addView(text(L.t("Activar al llegar al"), 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choiceList(new String[]{"90%", "100%"}, "focusAtIdx", 7400, false));
        if (!Focus.hasAccess(this)) {
            Button perm = button(L.t("Dar permiso de No molestar"));
            perm.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }
            });
            root.addView(perm);
        }
        root.addView(text(L.t("El modo aparece en Ajustes → Notificaciones → No molestar, junto a tus otros modos. ")
                + L.t("Si querés que Samsung haga algo más (por ejemplo, bajar el brillo), creá una rutina en Modos y rutinas con la condición \"No molestar activado\"."), 13, th.dim));
        root.addView(toggle(L.t("Avisos para Tasker, MacroDroid y similares\nAcción: ") + Focus.EVENT + L.t(" · extra \"event\": level75, level90, limit o reset"), "broadcast", false));

        // Seguridad
        section(root, L.t("Seguridad"));
        root.addView(toggle(L.t("Bloquear la app con huella o PIN"), "appLock", false));
        int[] qw = History.quietWindow(this);
        root.addView(toggle(L.t("No molestar inteligente: avisos en silencio mientras dormís\n")
                + (qw != null ? String.format(java.util.Locale.US, L.t("Detectado: %02d–%02d h"), qw[0], qw[1])
                : L.t("Todavía aprendiendo tu horario (por ahora 01–08 h)")), "smartDnd", true));
        root.addView(toggle(L.t("Vibrar al actualizar desde el widget o Ajustes rápidos"), "haptic", true));

        // Widget
        section(root, L.t("Widget de inicio"));
        final TextView alphaLbl = text("", 15, th.fg);
        root.addView(alphaLbl);
        SeekBar sb = new SeekBar(this);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(th.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(th.accent));
        sb.setMax(100);
        sb.setProgress(p.getInt("wAlpha", 90));
        alphaLbl.setText(L.t("Opacidad del fondo: ") + sb.getProgress() + "%");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { alphaLbl.setText(L.t("Opacidad del fondo: ") + v + "%"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {
                p.edit().putInt("wAlpha", s.getProgress()).apply();
                WidgetProvider.update(MainActivity.this);
            }
        });
        root.addView(sb, margins(0, 4, 0, 12));
        root.addView(text(L.t("Estilo"), 15, th.fg));
        groupedChoice(root, L.t("Estilo del widget"), null, null, Art.STYLES, p.getInt("wStyle", 0), new Pick() {
            public void picked(int i) { p.edit().putInt("wStyle", i).apply(); WidgetProvider.update(MainActivity.this); }
        });
        root.addView(toggle(L.t("Minimalista (solo la imagen, en cualquier tamaño)"), "wMinimal", false));
        root.addView(toggle(L.t("Animar el widget"), "wAnim", true));
        groupedChoice(root, L.t("Fluidez del widget"), null, null, Fps.labels(this), indexOfFps(p.getInt("wFps2", 60)), new Pick() {
            public void picked(int i) { p.edit().putInt("wFps2", Fps.OPTIONS[i]).apply(); WidgetProvider.update(MainActivity.this); }
        });
        int actual = p.getInt("wFpsActual", -1);
        if (actual >= 0) root.addView(text(L.t("Ahora el widget anima a ") + (actual > 2 ? actual + " FPS" : actual == 0 ? L.t("0 FPS (quieto)") : L.t("2 cuadros (tu launcher no aceptó más)")), 13, th.dim));
        root.addView(text(L.t("Al tocar el widget"), 15, th.fg), margins(0, 8, 0, 0));
        root.addView(choice(new String[]{L.t("Actualizar"), L.t("Abrir la app")}, "wTap", 4000));

        // Datos
        section(root, L.t("Datos"));
        Button exp = button(L.t("Exportar historial (CSV para Excel)"));
        exp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/csv").putExtra(Intent.EXTRA_TITLE, "claude-uso.csv");
                startActivityForResult(i, REQ_EXPORT);
            }
        });
        root.addView(exp);
        Button bak = button(L.t("Copia de seguridad (ajustes + historial)"));
        bak.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json").putExtra(Intent.EXTRA_TITLE, "claude-uso-backup.json"), REQ_BACKUP);
            }
        });
        root.addView(bak);
        Button res = button(L.t("Restaurar copia de seguridad"));
        res.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"), REQ_RESTORE);
            }
        });
        root.addView(res);
        String tree = p.getString("backupTree", null);
        long lastAuto = p.getLong("lastAutoBackup", 0);
        root.addView(toggle(L.t("Respaldo automático semanal"), "autoBackup", false));
        root.addView(text(tree == null ? L.t("Carpeta: sin elegir") : L.t("Carpeta: ") + Uri.decode(Uri.parse(tree).getLastPathSegment())
                + (lastAuto > 0 ? L.t(" · último: ") + Usage.dayClock(lastAuto) : ""), 13, th.dim));
        Button folder = button(L.t("Elegir carpeta para los respaldos"));
        folder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE); }
        });
        root.addView(folder);
        Button now = button(L.t("Respaldar ahora en esa carpeta"));
        now.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean ok = Backup.auto(MainActivity.this);
                Toast.makeText(MainActivity.this, ok ? L.t("Respaldo guardado") : L.t("Elegí una carpeta primero"), Toast.LENGTH_SHORT).show();
                if (ok) showSettings();
            }
        });
        root.addView(now);

        // Otros
        section(root, L.t("Otros"));
        root.addView(text(L.t("• Pantalla de bloqueo: si tu versión de One UI permite widgets ahí, mantené apretado el reloj del bloqueo → Widgets → Claude Uso.\n")
                + L.t("• Atajos: mantené apretado el ícono de la app para Actualizar, Ver 7 días, Abrir Claude o Claude Code.\n")
                + L.t("• Asistente de Google: decí \"Ok Google, abrí Claude Uso\".\n")
                + L.t("• Botón en Ajustes rápidos: bajá la cortina, tocá el lápiz (editar) y arrastrá \"Claude\".\n")
                + L.t("• Pantalla de bloqueo: la notificación se ve completa. En el Always On Display aparece el número.\n")
                + L.t("• Galaxy Watch: los avisos (75/90/100%, reinicio) llegan al reloj si tiene activadas las notificaciones de esta app."),
                14, th.dim));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Button bat = button(L.t("Permitir que funcione en segundo plano"));
            bat.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                }
            });
            root.addView(bat, margins(0, 12, 0, 0));
        }
        Button stop = button(L.t("Apagar widget y notificación"));
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, UsageService.class));
                finish();
            }
        });
        root.addView(stop, margins(0, 12, 0, 0));
        Button back = button(L.t("Volver"));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showMain(); } });
        root.addView(back);
        final int restore = keepScroll;
        keepScroll = -1;
        if (restore >= 0) {
            // Mismo lugar que antes del cambio, sin animación de entrada
            setContentView(frame);
            scroll.post(new Runnable() { public void run() { scroll.scrollTo(0, restore); } });
        } else {
            setScreen(frame);
        }
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
            Toast.makeText(this, L.t("Exportado: ") + pts.size() + L.t(" registros"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, L.t("No se pudo exportar: ") + e.getMessage(), Toast.LENGTH_LONG).show();
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
                if ("focusAtIdx".equals(key)) Prefs.get(MainActivity.this).edit().putInt("focusAt", id - base == 0 ? 90 : 100).apply();
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

    interface Pick { void picked(int index); }

    /** Sección que se abre y se cierra tocando el título. Devuelve el contenedor de adentro. */
    private LinearLayout collapsible(LinearLayout parent, final String title, boolean open) {
        return collapsible(parent, title, title, open);
    }

    /**
     * Sección desplegable: fila con el título y un botón redondo grande con chevrón que gira.
     * key identifica la sección para recordar si estaba abierta al redibujar.
     */
    private LinearLayout collapsible(LinearLayout parent, final String key, String title, boolean open) {
        Boolean saved = openState.get(key);
        boolean isOpen = saved != null ? saved : open;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(6));
        final TextView head = text(title, 15, th.fg);
        head.setTypeface(Theme.font(true));
        row.addView(head, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final ChevronView chev = new ChevronView(this, th, isOpen);
        row.addView(chev, new LinearLayout.LayoutParams(dp(36), dp(36)));
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), 0, 0, dp(4));
        body.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        body.setTag(head);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean show = body.getVisibility() != View.VISIBLE;
                body.setVisibility(show ? View.VISIBLE : View.GONE);
                chev.setOpen(show);
                openState.put(key, show);
            }
        });
        parent.addView(row);
        parent.addView(body);
        return body;
    }

    /**
     * Elección única en un desplegable, con subcategorías desplegables opcionales.
     * El título muestra lo elegido, así se ve sin abrir la lista.
     */
    private void groupedChoice(LinearLayout parent, final String title, String[] catNames, int[][] groups,
                               final String[] names, int selected, final Pick pick) {
        final LinearLayout outer = collapsible(parent, title, title + ": " + names[Math.max(0, Math.min(names.length - 1, selected))], false);
        final TextView outerHead = (TextView) outer.getTag();
        final java.util.List<RadioButton> all = new java.util.ArrayList<RadioButton>();
        if (groups == null) { groups = new int[1][names.length]; for (int i = 0; i < names.length; i++) groups[0][i] = i; }
        for (int g = 0; g < groups.length; g++) {
            boolean has = false;
            for (int i : groups[g]) if (i == selected) has = true;
            LinearLayout box = catNames == null ? outer : collapsible(outer, title + "/" + catNames[g], catNames[g] + " (" + groups[g].length + ")", has);
            for (final int i : groups[g]) {
                final RadioButton rb = radio(names[i]);
                rb.setChecked(i == selected);
                rb.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        for (RadioButton o : all) o.setChecked(o == rb);
                        outerHead.setText(title + ": " + names[i]);
                        pick.picked(i);
                    }
                });
                all.add(rb);
                box.addView(rb);
            }
        }
    }

    private int indexOfFps(int fps) {
        for (int i = 0; i < Fps.OPTIONS.length; i++) if (Fps.OPTIONS[i] == fps) return i;
        return 0;
    }

    private void refreshAll() {
        WidgetProvider.update(this);
        startForegroundService(new Intent(this, UsageService.class));
    }

    /** Pide a la pantalla la tasa de refresco elegida (o la máxima en automático). */
    private void applyRefreshRate() {
        try {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredRefreshRate = Fps.app(this);
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}
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
                Prefs.get(MainActivity.this).edit().putInt(key, id - base).apply();
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
                if ("crt".equals(key)) { showSettings(); return; }
                if ("chibi".equals(key)) { refreshAll(); return; }
                if ("mascotIcon".equals(key)) {
                    applyIcon();
                    WidgetProvider.update(MainActivity.this);
                    startForegroundService(new Intent(MainActivity.this, UsageService.class));
                    showSettings();
                    return;
                }
                if ("randomDaily".equals(key)) { applyIcon(); WidgetProvider.update(MainActivity.this); return; }
                if ("wMinimal".equals(key) || "wAnim".equals(key)) { WidgetProvider.update(MainActivity.this); return; }
                if (key.startsWith("act") || "goClaude".equals(key)) {
                    startForegroundService(new Intent(MainActivity.this, UsageService.class));
                    return;
                }
                if ("amoled".equals(key)) { refreshAll(); recreate(); return; }
                if ("focusMode".equals(key)) {
                    if (on && !Focus.hasAccess(MainActivity.this)) startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    if (!on) Focus.off(MainActivity.this);
                    return;
                }
                if ("autoSave".equals(key)) { refreshAll(); showSettings(); return; }
                if ("vibePatterns".equals(key) || "broadcast".equals(key)) return;
                if ("appLock".equals(key)) { if (on) { unlocked = true; leftAt = 0; } return; }
                if ("smart".equals(key)) {
                    startForegroundService(new Intent(MainActivity.this, UsageService.class).setAction(UsageService.ACTION_REFRESH));
                }
            }
        });
        return s;
    }
}

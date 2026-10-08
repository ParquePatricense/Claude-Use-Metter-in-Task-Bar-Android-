package com.matias.claudeusage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

/** Configuración + cuentas (cada cuenta guarda sus cookies de claude.ai). */
class Prefs {
    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    /** Pasa la cuenta única de versiones anteriores a la lista de cuentas. */
    static void migrate(Context c) {
        SharedPreferences p = get(c);
        if (p.contains("accounts")) return;
        JSONArray arr = new JSONArray();
        String cookie = p.getString("cookie", null);
        if (cookie != null) {
            JSONObject a = newAccount(cookie, p.getString("ua", null), "Cuenta 1");
            arr.put(a);
            File old = new File(c.getFilesDir(), "history.csv");
            if (old.exists()) old.renameTo(new File(c.getFilesDir(), "history_" + a.optString("id") + ".csv"));
        }
        p.edit().putString("accounts", arr.toString()).putInt("active", 0).remove("cookie").remove("ua").apply();
    }

    private static JSONObject newAccount(String cookie, String ua, String name) {
        JSONObject a = new JSONObject();
        try {
            a.put("id", UUID.randomUUID().toString().substring(0, 8));
            a.put("name", name);
            a.put("cookie", cookie);
            if (ua != null) a.put("ua", ua);
        } catch (Exception ignored) {}
        return a;
    }

    static JSONArray accounts(Context c) {
        try { return new JSONArray(get(c).getString("accounts", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static int activeIndex(Context c) { return get(c).getInt("active", 0); }

    static JSONObject active(Context c) {
        JSONArray arr = accounts(c);
        int i = activeIndex(c);
        return i < arr.length() ? arr.optJSONObject(i) : null;
    }

    static String cookie(Context c) {
        JSONObject a = active(c);
        return a == null ? null : a.optString("cookie", null);
    }

    static String ua(Context c) {
        JSONObject a = active(c);
        return a == null || !a.has("ua") ? null : a.optString("ua", null);
    }

    static String accountId(Context c) {
        JSONObject a = active(c);
        return a == null ? "none" : a.optString("id", "none");
    }

    static void setActiveField(Context c, String key, String value) {
        JSONArray arr = accounts(c);
        int i = activeIndex(c);
        if (i >= arr.length()) return;
        try { arr.getJSONObject(i).put(key, value); } catch (Exception ignored) {}
        get(c).edit().putString("accounts", arr.toString()).apply();
    }

    /** Guarda la sesión en la cuenta activa (o crea la primera). */
    static void saveActive(Context c, String cookie, String ua) {
        if (active(c) == null) { addAccount(c, cookie, ua); return; }
        setActiveField(c, "cookie", cookie);
        if (ua != null) setActiveField(c, "ua", ua);
    }

    static void addAccount(Context c, String cookie, String ua) {
        JSONArray arr = accounts(c);
        arr.put(newAccount(cookie, ua, "Cuenta " + (arr.length() + 1)));
        get(c).edit().putString("accounts", arr.toString()).putInt("active", arr.length() - 1).apply();
    }

    static void removeActive(Context c) {
        JSONArray arr = accounts(c), out = new JSONArray();
        int i = activeIndex(c);
        for (int k = 0; k < arr.length(); k++) if (k != i) out.put(arr.opt(k));
        get(c).edit().putString("accounts", out.toString()).putInt("active", 0).apply();
    }

    static void setActive(Context c, int i) { get(c).edit().putInt("active", i).apply(); }

    /** Borra el estado en vivo (al cambiar de cuenta). */
    static void resetLive(Context c) {
        SharedPreferences.Editor e = get(c).edit();
        for (String k : new String[]{"pct", "week", "reset", "weekReset", "updated", "error", "extra",
                "alertWin", "alertLvl", "preWin", "expired", "lastChange"}) e.remove(k);
        e.apply();
    }
}

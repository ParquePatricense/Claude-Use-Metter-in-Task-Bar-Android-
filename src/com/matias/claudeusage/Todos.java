package com.matias.claudeusage;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pendientes para cuando vuelva el límite: texto + etiqueta libre, guardados en el teléfono. */
final class Todos {
    static final int MAX = 100, MAX_TEXT = 200, MAX_TAG = 30;

    static JSONArray load(Context c) {
        JSONArray out = new JSONArray();
        try {
            JSONArray in = new JSONArray(Prefs.get(c).getString("todos", "[]"));
            for (int i = 0; i < in.length() && out.length() < MAX; i++) {
                JSONObject o = in.optJSONObject(i);
                if (o == null) continue;
                String t = clean(o.optString("t", ""), MAX_TEXT);
                if (t.isEmpty()) continue;
                out.put(new JSONObject().put("t", t).put("g", clean(o.optString("g", ""), MAX_TAG)).put("d", o.optBoolean("d", false)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void save(Context c, JSONArray a) { Prefs.get(c).edit().putString("todos", a.toString()).apply(); }

    static boolean add(Context c, String text, String tag) {
        text = clean(text, MAX_TEXT);
        if (text.isEmpty()) return false;
        JSONArray a = load(c);
        if (a.length() >= MAX) return false;
        try { a.put(new JSONObject().put("t", text).put("g", clean(tag, MAX_TAG)).put("d", false)); } catch (Exception e) { return false; }
        save(c, a);
        return true;
    }

    static void toggle(Context c, int i) {
        JSONArray a = load(c);
        JSONObject o = a.optJSONObject(i);
        if (o == null) return;
        try { o.put("d", !o.optBoolean("d")); } catch (Exception ignored) {}
        save(c, a);
    }

    static void remove(Context c, int i) {
        JSONArray a = load(c), out = new JSONArray();
        for (int k = 0; k < a.length(); k++) if (k != i) out.put(a.opt(k));
        save(c, out);
    }

    static int open(Context c) {
        JSONArray a = load(c);
        int n = 0;
        for (int i = 0; i < a.length(); i++) if (!a.optJSONObject(i).optBoolean("d")) n++;
        return n;
    }

    /** "mod 2 · Belka 1 · sin etiqueta 1" (solo los abiertos). */
    static String tags(Context c) {
        JSONArray a = load(c);
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o.optBoolean("d")) continue;
            String g = o.optString("g", "");
            if (g.isEmpty()) g = L.t("sin etiqueta");
            Integer v = m.get(g);
            m.put(g, v == null ? 1 : v + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getKey()).append(' ').append(e.getValue());
        }
        return sb.toString();
    }

    /** Los primeros n abiertos, uno por línea. */
    static String top(Context c, int n) {
        JSONArray a = load(c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length() && n > 0; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o.optBoolean("d")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(label(o));
            n--;
        }
        return sb.toString();
    }

    static String label(JSONObject o) {
        String g = o.optString("g", "");
        return (g.isEmpty() ? "" : "[" + g + "] ") + o.optString("t", "");
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}

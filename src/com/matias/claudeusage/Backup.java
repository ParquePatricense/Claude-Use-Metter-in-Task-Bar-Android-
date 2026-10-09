package com.matias.claudeusage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/** Copia de seguridad: ajustes + historial + nombres de cuentas. No incluye sesiones (cookies). */
final class Backup {
    private static final String[] SKIP = {"accounts", "active", "pct", "week", "reset", "weekReset", "updated",
            "error", "extra", "alertWin", "alertLvl", "preWin", "expired", "lastChange", "status", "backupTree", "lastAutoBackup"};

    static void write(Context c, Uri uri) {
        try {
            OutputStream out = c.getContentResolver().openOutputStream(uri);
            out.write(json(c).getBytes("UTF-8"));
            out.close();
            Toast.makeText(c, L.t("Copia guardada"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(c, L.t("No se pudo guardar: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Respaldo automático en la carpeta elegida (permiso persistente de Android).
     * Guarda claude-uso-AAAA-MM-DD.json y deja solo los 4 más nuevos.
     */
    static boolean auto(Context c) {
        String tree = Prefs.get(c).getString("backupTree", null);
        if (tree == null) return false;
        try {
            Uri treeUri = Uri.parse(tree);
            android.content.ContentResolver cr = c.getContentResolver();
            Uri dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    android.provider.DocumentsContract.getTreeDocumentId(treeUri));
            String name = "claude-uso-" + java.time.LocalDate.now() + ".json";
            Uri file = android.provider.DocumentsContract.createDocument(cr, dir, "application/json", name);
            if (file == null) return false;
            OutputStream out = cr.openOutputStream(file);
            out.write(json(c).getBytes("UTF-8"));
            out.close();
            // Limpieza: deja los 4 respaldos más nuevos
            Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                    android.provider.DocumentsContract.getTreeDocumentId(treeUri));
            java.util.TreeMap<String, String> found = new java.util.TreeMap<String, String>();
            android.database.Cursor cur = cr.query(children, new String[]{
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    String n = cur.getString(1);
                    if (n != null && n.startsWith("claude-uso-") && n.endsWith(".json")) found.put(n, cur.getString(0));
                }
                cur.close();
            }
            while (found.size() > 4) {
                String oldest = found.firstKey();
                android.provider.DocumentsContract.deleteDocument(cr,
                        android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, found.get(oldest)));
                found.remove(oldest);
            }
            Prefs.get(c).edit().putLong("lastAutoBackup", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String json(Context c) throws Exception {
        {
            JSONObject root = new JSONObject();
            root.put("app", "claude-uso");
            root.put("version", 1);
            JSONObject prefs = new JSONObject();
            for (Map.Entry<String, ?> e : Prefs.get(c).getAll().entrySet()) {
                if (skip(e.getKey())) continue;
                Object v = e.getValue();
                JSONObject o = new JSONObject();
                o.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : "s");
                o.put("v", v);
                prefs.put(e.getKey(), o);
            }
            root.put("prefs", prefs);
            JSONArray accs = Prefs.accounts(c), outAccs = new JSONArray();
            JSONObject hist = new JSONObject();
            for (int i = 0; i < accs.length(); i++) {
                JSONObject a = accs.getJSONObject(i);
                String id = a.optString("id");
                outAccs.put(new JSONObject().put("id", id).put("name", a.optString("name")));
                File f = new File(c.getFilesDir(), "history_" + id + ".csv");
                if (f.exists()) hist.put(id, readAll(new FileInputStream(f)));
            }
            root.put("accounts", outAccs);
            root.put("history", hist);
            return root.toString();
        }
    }

    static boolean read(Context c, Uri uri) {
        try {
            JSONObject root = new JSONObject(readAll(c.getContentResolver().openInputStream(uri)));
            if (!"claude-uso".equals(root.optString("app"))) throw new IllegalArgumentException(L.t("no es una copia de esta app"));
            SharedPreferences.Editor ed = Prefs.get(c).edit();
            JSONObject prefs = root.getJSONObject("prefs");
            for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String k = it.next();
                if (skip(k)) continue;
                JSONObject o = prefs.getJSONObject(k);
                String t = o.getString("t");
                if (t.equals("b")) ed.putBoolean(k, o.getBoolean("v"));
                else if (t.equals("i")) ed.putInt(k, o.getInt("v"));
                else if (t.equals("l")) ed.putLong(k, o.getLong("v"));
                else ed.putString(k, o.getString("v"));
            }
            ed.apply();
            // Cuentas que no están en este teléfono: se agregan sin sesión (pide iniciar sesión al elegirlas)
            JSONArray accs = Prefs.accounts(c), in = root.optJSONArray("accounts");
            for (int i = 0; in != null && i < in.length(); i++) {
                JSONObject a = in.getJSONObject(i);
                boolean found = false;
                for (int k = 0; k < accs.length(); k++) if (accs.getJSONObject(k).optString("id").equals(a.optString("id"))) found = true;
                if (!found) accs.put(new JSONObject().put("id", a.optString("id")).put("name", a.optString("name")));
            }
            Prefs.get(c).edit().putString("accounts", accs.toString()).apply();
            // Historial: une lo existente con lo de la copia
            JSONObject hist = root.optJSONObject("history");
            int n = 0;
            for (Iterator<String> it = hist == null ? null : hist.keys(); it != null && it.hasNext(); ) {
                String id = it.next();
                File f = new File(c.getFilesDir(), "history_" + id + ".csv");
                TreeSet<String> lines = new TreeSet<String>();
                if (f.exists()) for (String l : readAll(new FileInputStream(f)).split("\n")) if (!l.isEmpty()) lines.add(l);
                for (String l : hist.getString(id).split("\n")) if (!l.isEmpty()) lines.add(l);
                StringBuilder sb = new StringBuilder();
                for (String l : lines) sb.append(l).append('\n');
                FileOutputStream out = new FileOutputStream(f);
                out.write(sb.toString().getBytes("UTF-8"));
                out.close();
                n += lines.size();
            }
            Toast.makeText(c, L.t("Copia restaurada (") + n + L.t(" registros)"), Toast.LENGTH_SHORT).show();
            WidgetProvider.update(c);
            return true;
        } catch (Exception e) {
            Toast.makeText(c, L.t("No se pudo restaurar: ") + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static boolean skip(String k) {
        for (String s : SKIP) if (s.equals(k)) return true;
        return false;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}

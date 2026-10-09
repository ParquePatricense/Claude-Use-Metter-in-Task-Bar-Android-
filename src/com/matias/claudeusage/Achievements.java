package com.matias.claudeusage;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Logros pixel art. Algunos desbloquean accesorios para la mascota. */
final class Achievements {
    static final String[][] ALL = {
            // id, nombre, descripción
            {"first", L.t("Primer dato"), L.t("La app leyó tu uso por primera vez"), "Moño"},
            {"streak3", L.t("Racha de 3"), L.t("3 días seguidos sin llegar al límite"), "Gorro"},
            {"streak7", L.t("Semana perfecta"), L.t("7 días seguidos sin llegar al límite"), "Corona"},
            {"sessions10", L.t("10 sesiones"), L.t("Usaste 10 sesiones de Claude"), "Anteojos"},
            {"night", L.t("Búho nocturno"), L.t("Usaste Claude entre las 0 y las 4"), "Auriculares"},
            {"early", L.t("Madrugador"), L.t("Usaste Claude entre las 5 y las 7"), ""},
            {"limit", L.t("Al límite"), L.t("Llegaste al 100% de una sesión"), ""},
            {"marathon", L.t("Maratón"), L.t("5 sesiones en un mismo día"), ""},
            {"collector", L.t("Coleccionista"), L.t("Probaste 5 mascotas distintas"), ""},
            {"zen", L.t("Zen"), L.t("Activaste el modo zen"), ""},
    };

    static boolean has(Context c, String id) { return Prefs.get(c).getLong("ach_" + id, 0) > 0; }

    static int count(Context c) {
        int n = 0;
        for (String[] a : ALL) if (has(c, a[0])) n++;
        return n;
    }

    /** Desbloquea y avisa. Devuelve true si es nuevo. */
    static boolean unlock(Context c, String id) {
        if (has(c, id)) return false;
        Prefs.get(c).edit().putLong("ach_" + id, System.currentTimeMillis()).apply();
        for (String[] a : ALL) {
            if (!a[0].equals(id)) continue;
            UsageService.notifyAchievement(c, L.t("🏆 Logro: ") + a[1], a[2]);
        }
        return true;
    }

    static void sawMascot(Context c, int id) {
        SharedPreferences p = Prefs.get(c);
        Set<String> seen = new HashSet<String>(p.getStringSet("seenMascots", new HashSet<String>()));
        seen.add(String.valueOf(id));
        p.edit().putStringSet("seenMascots", seen).apply();
        if (seen.size() >= 5) unlock(c, "collector");
    }
}

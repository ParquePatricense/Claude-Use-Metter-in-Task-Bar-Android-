package com.matias.claudeusage;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Logros pixel art. Algunos desbloquean accesorios para la mascota. */
final class Achievements {
    static final String[][] ALL = {
            // id, nombre, descripción, accesorio que desbloquea
            {"first", "Primer dato", "La app leyó tu uso por primera vez", "Moño"},
            {"streak3", "Racha de 3", "3 días seguidos sin llegar al límite", "Gorro"},
            {"streak7", "Semana perfecta", "7 días seguidos sin llegar al límite", "Corona"},
            {"sessions10", "10 sesiones", "Usaste 10 sesiones de Claude", "Anteojos"},
            {"night", "Búho nocturno", "Usaste Claude entre las 0 y las 4", "Auriculares"},
            {"early", "Madrugador", "Usaste Claude entre las 5 y las 7", ""},
            {"limit", "Al límite", "Llegaste al 100% de una sesión", ""},
            {"marathon", "Maratón", "5 sesiones en un mismo día", ""},
            {"collector", "Coleccionista", "Probaste 5 mascotas distintas", ""},
            {"zen", "Zen", "Activaste el modo zen", ""},
            {"legend", "Leyenda", "Tu mascota llegó a la etapa legendaria", ""},
    };
    static final String[] ACCESSORIES = {"Ninguno", "Moño", "Gorro", "Anteojos", "Auriculares", "Corona"};

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
            String extra = a[3].isEmpty() ? "" : " · Nuevo accesorio: " + a[3];
            UsageService.notifyAchievement(c, "🏆 Logro: " + a[1], a[2] + extra);
        }
        return true;
    }

    static boolean accessoryUnlocked(Context c, int acc) {
        if (acc == 0) return true;
        String name = ACCESSORIES[acc];
        for (String[] a : ALL) if (a[3].equals(name)) return has(c, a[0]);
        return false;
    }

    static void sawMascot(Context c, int id) {
        SharedPreferences p = Prefs.get(c);
        Set<String> seen = new HashSet<String>(p.getStringSet("seenMascots", new HashSet<String>()));
        seen.add(String.valueOf(id));
        p.edit().putStringSet("seenMascots", seen).apply();
        if (seen.size() >= 5) unlock(c, "collector");
    }
}

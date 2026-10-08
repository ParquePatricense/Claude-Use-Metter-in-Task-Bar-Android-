package com.matias.claudeusage;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Sonidos 8-bit dentro de la app (tocar la mascota, confeti, logros). */
final class Sfx {
    static final int TAP = R.raw.sfx_tap, COIN = R.raw.sfx_coin, ACH = R.raw.sfx_ach, POWER = R.raw.sfx_power;
    private static SoundPool pool;
    private static final java.util.Map<Integer, Integer> ids = new java.util.HashMap<Integer, Integer>();

    static synchronized void play(Context c, int res) {
        if (!Prefs.get(c).getBoolean("sounds", true)) return;
        if (pool == null) {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                public void onLoadComplete(SoundPool p, int id, int status) { if (status == 0) p.play(id, 0.6f, 0.6f, 1, 0, 1f); }
            });
        }
        Integer id = ids.get(res);
        if (id == null) { ids.put(res, pool.load(c.getApplicationContext(), res, 1)); return; }
        pool.play(id, 0.6f, 0.6f, 1, 0, 1f);
    }
}

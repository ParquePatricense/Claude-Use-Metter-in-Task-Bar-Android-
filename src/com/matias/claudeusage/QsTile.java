package com.matias.claudeusage;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botón de Ajustes rápidos: muestra el % y al tocarlo actualiza. */
public class QsTile extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        Theme.widget(this);
        Usage u = Usage.load(this);
        t.setLabel(u.hasData() ? "Claude " + u.pct + "%" : "Claude");
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(!u.hasData() ? L.t("Sin datos") : u.reset > 0 ? "⟳ " + Usage.left(u.reset) : L.t("Libre"));
        }
        t.setIcon(UsageService.numberIcon(this, u.pct));
        t.setState(u.hasData() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        Intent i = new Intent(this, UsageService.class).setAction(UsageService.ACTION_REFRESH)
                .putExtra(UsageService.EXTRA_HAPTIC, true);
        try { startService(i); } catch (Exception e) {
            try { startForegroundService(i); } catch (Exception ignored) {}
        }
    }
}

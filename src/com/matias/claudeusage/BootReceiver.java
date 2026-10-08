package com.matias.claudeusage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs.migrate(c);
        if (Prefs.cookie(c) != null) {
            c.startForegroundService(new Intent(c, UsageService.class));
        }
    }
}

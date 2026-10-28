package com.crewwifisaver.app;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class LogoutAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("auto", true)) return;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean screenOff = pm != null && !pm.isInteractive();
        boolean locked = km != null && km.isKeyguardLocked();
        if (!screenOff && !locked) return;
        Intent service = new Intent(context, MonitorService.class);
        service.setAction(MonitorService.ACTION_AUTO_LOGOUT);
        try {
            context.startForegroundService(service);
        } catch (Exception e) {
            Log.e("login.com", "Alarm could not start service", e);
            context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("state", "unknown")
                    .putString("detail", "Automatic logout blocked: " + e.getMessage())
                    .apply();
        }
    }
}

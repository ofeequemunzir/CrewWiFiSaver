package com.crewwifisaver.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

/** A fixed-duration timer, counted from a confirmed portal sign-in, not screen activity. */
public final class SessionLogoutAlarm {
    private SessionLogoutAlarm() {}

    private static PendingIntent intent(Context c) {
        return PendingIntent.getBroadcast(c, 2086,
                new Intent(c, SessionLogoutReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void update(Context c) {
        AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = intent(c);
        manager.cancel(pending);
        SharedPreferences prefs = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("timed_disconnect", false)
                || !prefs.getBoolean("session_active", false)) return;
        long start = prefs.getLong("session_started_elapsed", -1L);
        long now = SystemClock.elapsedRealtime();
        if (start < 0 || start > now) return; // Clock reset or old session after device reboot.
        int seconds = Math.min(900, Math.max(30, prefs.getInt("timed_seconds", 300)));
        long when = Math.max(now + 1000L, start + seconds * 1000L);
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
        } else {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
            } catch (SecurityException e) {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pending);
            }
        }
    }

    public static void cancel(Context c) {
        AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(intent(c));
    }
}

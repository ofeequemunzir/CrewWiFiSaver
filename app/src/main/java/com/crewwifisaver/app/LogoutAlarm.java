package com.crewwifisaver.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class LogoutAlarm {
    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, LogoutAlarmReceiver.class);
        return PendingIntent.getBroadcast(
            c, 1001, i,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static void schedule(Context c, int seconds) {
        cancel(c);
        if (seconds <= 0) return;

        AlarmManager am = (AlarmManager)
            c.getSystemService(Context.ALARM_SERVICE);

        if (am == null) return;

        long when = SystemClock.elapsedRealtime()
            + seconds * 1000L;

        PendingIntent pi = pending(c);

        if (Build.VERSION.SDK_INT >= 31 &&
            !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                when, pi
            );
        } else {
            try {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    when, pi
                );
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    when, pi
                );
            }
        }
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager)
            c.getSystemService(Context.ALARM_SERVICE);

        if (am != null) am.cancel(pending(c));
    }
}


package com.crewwifisaver.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.time.LocalDate;

public final class DailyUsageTracker {

    private static final String PREFS = "daily_usage";
    private static final String DAY = "day";
    private static final String USED = "used_ms";
    private static final String START = "start_elapsed";
    private static final String BOOT = "boot_wall";
    private static final String LIMIT = "limit_ms";

    private DailyUsageTracker() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void resetDay(Context context) {
        SharedPreferences p = prefs(context);
        String today = LocalDate.now().toString();
        String previous = p.getString(DAY, "");

        if (!today.equals(previous)) {
            p.edit()
                .putString(DAY, today)
                .putLong(USED, 0L)
                .putLong(START, -1L)
                .apply();
        }
    }

    public static synchronized void setLimitMinutes(
            Context context, int minutes) {
        resetDay(context);
        prefs(context).edit()
            .putLong(LIMIT, Math.max(1, minutes) * 60000L)
            .apply();
    }

    public static synchronized void update(
            Context context, boolean paidConnected) {
        resetDay(context);

        SharedPreferences p = prefs(context);
        long now = SystemClock.elapsedRealtime();
        long bootWall = System.currentTimeMillis() - now;
        long start = p.getLong(START, -1L);
        long used = p.getLong(USED, 0L);
        long savedBoot = p.getLong(BOOT, -1L);

        if (start >= 0) {
            if (savedBoot >= 0 &&
                    Math.abs(bootWall - savedBoot) < 60000L &&
                    now >= start) {
                used += now - start;
            }
        }

        p.edit()
            .putLong(USED, used)
            .putLong(START, paidConnected ? now : -1L)
            .putLong(BOOT, bootWall)
            .apply();
    }

    public static synchronized long remainingMillis(
            Context context) {
        resetDay(context);
        SharedPreferences p = prefs(context);
        long limit = p.getLong(LIMIT, 3600000L);
        long used = p.getLong(USED, 0L);
        long start = p.getLong(START, -1L);
        long now = SystemClock.elapsedRealtime();

        if (start >= 0 && now >= start) {
            long bootWall = System.currentTimeMillis() - now;
            if (Math.abs(bootWall -
                    p.getLong(BOOT, -1L)) < 60000L) {
                used += now - start;
            }
        }

        return Math.max(0L, limit - used);
    }

    public static synchronized boolean limitReached(
            Context context) {
        return remainingMillis(context) == 0L;
    }
}


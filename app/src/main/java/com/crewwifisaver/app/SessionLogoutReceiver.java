package com.crewwifisaver.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

/** Runs even if the user is actively using the phone. */
public class SessionLogoutReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent unused) {
        SharedPreferences prefs = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("timed_disconnect", false)
                || !prefs.getBoolean("session_active", false)) return;
        long session = prefs.getLong("session_started_elapsed", -1L);
        long start = prefs.getLong("timer_started_elapsed", -1L);
        long now = SystemClock.elapsedRealtime();
        if (start < 0 || now < start || session < 0 || session != prefs.getLong("timer_session", -1L)) return;
        if (prefs.getLong("logout_timer_fired_session", -1L) == session) return;
        int seconds = Math.max(1, Math.min(86399, prefs.getInt("timed_seconds", 300)));
        if (now - start + 1500L < seconds * 1000L) {
            SessionLogoutAlarm.update(c); // An early/inexact alarm; try again.
            return;
        }
        // One timed logout attempt per session; rearmed when a new session starts.
        prefs.edit().putLong("logout_timer_fired_session", session).apply();
        Intent service = new Intent(c, MonitorService.class).setAction(MonitorService.ACTION_AUTO_LOGOUT);
        try { c.startForegroundService(service); }
        catch (Exception e) {
            prefs.edit().putString("detail", "Timed logout blocked by Android. Open Crew Connect.").apply();
            Log.e("CrewConnect", "Unable to trigger timed logout", e);
        }
    }
}

package com.crewwifisaver.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorService extends Service {
    public static final String ACTION_LOGOUT = "com.crewwifisaver.app.LOGOUT";
    public static final String ACTION_AUTO_LOGOUT = "com.crewwifisaver.app.AUTO_LOGOUT";
    public static final String ACTION_STOP = "com.crewwifisaver.app.STOP";
    public static final String MONITOR_CHANNEL = "crew_monitor";
    private static final String ALERT_CHANNEL = "logout_alerts_v23";
    private static final int MONITOR_NOTIFICATION_ID = 12;
    private static final int ALERT_NOTIFICATION_ID = 13;

    private ScreenLockReceiver screenReceiver;
    private final AtomicBoolean logoutAttempted = new AtomicBoolean(false);

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        NotificationChannel monitor = new NotificationChannel(
                MONITOR_CHANNEL, "Crew Connect monitoring", NotificationManager.IMPORTANCE_LOW);
        notifications.createNotificationChannel(monitor);
        NotificationChannel alerts = new NotificationChannel(
                ALERT_CHANNEL, "Crew Connect alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Notifications when automatic logout requests finish");
        alerts.enableVibration(true);
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notifications.createNotificationChannel(alerts);

        startForeground(MONITOR_NOTIFICATION_ID, monitorNotification("Disconnect protection ready"));
        setupScreenMonitoring();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("prefs", MODE_PRIVATE);
    }

    private void setupScreenMonitoring() {
        screenReceiver = new ScreenLockReceiver(new ScreenLockReceiver.Listener() {
            @Override public void onScreenOff() {
                if (prefs().getBoolean("screen_lock", true)) {
                    // This is immediate screen-lock logout, NOT the independent session timer.
                    logout(true);
                }
            }
            @Override public void onScreenOn() { /* No screen-based timer to cancel. */ }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("com.crewwifisaver.app.DAILY_LIMIT_LOGOUT".equals(action)) {
            logout(true);
        } else if (ACTION_LOGOUT.equals(action)) {
            logout(false);
        } else if (ACTION_AUTO_LOGOUT.equals(action) &&
                (prefs().getBoolean("screen_lock", true) || prefs().getBoolean("timed_disconnect", false))) {
            logout(true);
        }
        return START_STICKY;
    }

    private Notification monitorNotification(String detail) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent out = new Intent(this, MonitorService.class).setAction(ACTION_LOGOUT);
        PendingIntent logoutIntent = PendingIntent.getService(this, 2, out,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, MONITOR_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wifi_logout)
                .setContentTitle("Crew Connect")
                .setContentText(detail)
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "Instant disconnect", logoutIntent)
                .setOngoing(true)
                .build();
    }

    private void report(String state, String detail) {
        prefs().edit().putString("state", state).putString("detail", detail).apply();
        getSystemService(NotificationManager.class).notify(
                MONITOR_NOTIFICATION_ID, monitorNotification(detail));
    }

    private void alertAutomaticLogout(String message) {
        if (!prefs().getBoolean("notify_logout", true)) return;
        // Alerts describe observed network status; none claim backend entitlement proof.
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification alert = new Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wifi_logout)
                .setContentTitle(
                        message.startsWith("Internet appears disconnected")
                        ? "Internet appears disconnected"
                        : "Logout status needs checking")
                .setContentIntent(openIntent)
                .setContentText(message)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        try {
            getSystemService(NotificationManager.class).notify(ALERT_NOTIFICATION_ID, alert);
        } catch (SecurityException e) {
            Log.w("Crew Connect", "Notification permission is unavailable", e);
        }
    }

    /**
     * Screen lock, elapsed timer and manual disconnect all use the same gate.
     * A failed or blocked Google request is NOT evidence of paid logout; in
     * that case do nothing and do not send a misleading success notification.
     */
    public void logout(boolean automatic) {
        if (!logoutAttempted.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                PaidInternetProbe.Result before = PaidInternetProbe.checkWithRetry(this);
                if (!before.hasFullInternet()) {
                    // Silent when paid access is not confirmed. This prevents the
                    // repeated screen-lock alerts experienced on free messaging.
                    if (before.state == PaidInternetProbe.State.NO_WIFI)
                        prefs().edit().putBoolean("session_active", false)
                                .putString("connection_status", "offline").apply();
                    else if (before.state == PaidInternetProbe.State.RESTRICTED)
                        prefs().edit().putBoolean("session_active", false)
                                .putString("connection_status", "restricted").apply();
                    else
                        prefs().edit().putString("connection_status", "unknown").apply();
                    return;
                }
                prefs().edit().putBoolean("session_active", true)
                        .putString("connection_status", "connected")
                        .putBoolean("logout_in_progress", true)
                        .putString("free_portal_state", "pending")
                        .putLong("last_logout_attempt", System.currentTimeMillis()).apply();
                report("logging_out", "Disconnecting paid internet…");

                HttpURLConnection conn = null;
                int code;
                try {
                    URL url = new URL("http://logout.com/");
                    conn = PaidInternetProbe.openOnWifi(this, url);
                    conn.setRequestMethod("GET");
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setUseCaches(false);
                    code = conn.getResponseCode();
                    URL currentUrl = url;
                    int redirects = 0;
                    while (code >= 300 && code < 400 && redirects < 10) {
                        String location = conn.getHeaderField("Location");
                        if (location == null) break;
                        currentUrl = new URL(currentUrl, location);
                        if (!"http".equals(currentUrl.getProtocol()) &&
                                !"https".equals(currentUrl.getProtocol()))
                            throw new IOException("Unexpected redirect protocol");
                        conn.disconnect();
                        conn = PaidInternetProbe.openOnWifi(this, currentUrl);
                        conn.setInstanceFollowRedirects(false);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setUseCaches(false);
                        code = conn.getResponseCode();
                        redirects++;
                    }
                    try (InputStream input = conn.getInputStream()) {
                        byte[] buffer = new byte[2048];
                        while (input.read(buffer) != -1) {}
                    } catch (IOException ignored) {
                        // Logout may cut off the network while reading.
                    }
                    if (code < 200 || code >= 400)
                        throw new IOException("Unexpected logout HTTP " + code);
                } finally {
                    if (conn != null) conn.disconnect();
                }

                // The HTTP response alone says NOTHING about entitlement.
                // Recheck general internet access over ship Wi-Fi after logout.
                try { Thread.sleep(1400); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                PaidInternetProbe.Result firstAfter = PaidInternetProbe.checkWithRetry(this);
                if (firstAfter.hasFullInternet()) {
                    try { Thread.sleep(1800); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    firstAfter = PaidInternetProbe.checkWithRetry(this);
                }
                if (firstAfter.hasFullInternet()) {
                    // Paid internet may still be running. Do not claim success or
                    // start the complimentary plan while it is reachable.
                    prefs().edit().putBoolean("session_active", true)
                            .putString("connection_status", "connected")
                            .putBoolean("logout_pending_whatsapp", false).apply();
                    DailyUsageTracker.update(this, true);
                    report("connected", "Internet remains reachable. Logout not verified.");
                    return;
                }
                if (firstAfter.state == PaidInternetProbe.State.NO_WIFI) {
                    prefs().edit().putBoolean("session_active", false)
                            .putString("connection_status", "offline")
                            .putBoolean("logout_pending_whatsapp", false).apply();
                    SessionLogoutAlarm.cancel(this);
                    report("offline", "Wi-Fi disconnected. Free messaging requires ship Wi-Fi.");
                    return;
                }
                // The observed change from 204 to non-204 strongly suggests logout.
                // It still does NOT certify the backend's minute balance.
                prefs().edit().putBoolean("session_active", false)
                        .putString("connection_status", "restricted")
                        .putBoolean("logout_pending_whatsapp", prefs().getBoolean("auto_whatsapp", true))
                        .putString("free_portal_state", "pending")
                        .putInt("reported_remaining_seconds", -1)
                        .putString("session_start_source", "")
                        .apply();
                DailyUsageTracker.update(this, false);
                SessionLogoutAlarm.cancel(this);
                report("restricted", "Internet appears disconnected after logout request.");
                if (automatic) alertAutomaticLogout("Internet appears disconnected. Open Crew Connect for Free WhatsApp Text.");
            } catch (Exception e) {
                report("unknown", "Logout could not be verified. Please check your connection.");
                Log.w("CrewConnect", "Logout request was not verified", e);
            } finally {
                prefs().edit().putBoolean("logout_in_progress", false).apply();
                logoutAttempted.set(false);
                if (!prefs().getBoolean("screen_lock", true) &&
                        !prefs().getBoolean("timed_disconnect", false)) stopSelf();
            }
        }, "CrewPaidLogout").start();
    }

    @Override public void onDestroy() {
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

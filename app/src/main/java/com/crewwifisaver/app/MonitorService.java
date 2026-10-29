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
import java.util.Locale;

public class MonitorService extends Service {
    public static final String ACTION_LOGOUT = "com.crewwifisaver.app.LOGOUT";
    public static final String ACTION_AUTO_LOGOUT = "com.crewwifisaver.app.AUTO_LOGOUT";
    public static final String ACTION_STOP = "com.crewwifisaver.app.STOP";
    public static final String MONITOR_CHANNEL = "crew_monitor";
    private static final String ALERT_CHANNEL = "logout_alerts_v23";
    private static final int MONITOR_NOTIFICATION_ID = 12;
    private static final int ALERT_NOTIFICATION_ID = 13;

    private ScreenLockReceiver screenReceiver;
    private volatile boolean logoutAttempted = false;

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
        if (ACTION_LOGOUT.equals(action)) {
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
        // This confirms that the REQUEST finished, not that paid minutes stopped.
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification alert = new Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wifi_logout)
                .setContentTitle(
                        message.startsWith("Logout request finished")
                        ? "Logout Request Finished"
                        : "Logout Status Unknown")
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
     * Never interpret a plain HTTP 200 from the portal as free WhatsApp activation.
     * The complimentary entitlement requires portal actions that must be tested first.
     * The existing, working paid logout request is intentionally unchanged.
     */
    public void logout(boolean automatic) {
        if (logoutAttempted) return;
        logoutAttempted = true;
        prefs().edit().putLong("last_logout_attempt", System.currentTimeMillis())
                .putString("connection_status", "unknown").apply();
        report("unknown", "Sending background logout request...");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://logout.com/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);

                int code = conn.getResponseCode();
                URL currentUrl = url;
                int redirects = 0;

                while (code >= 300 && code < 400 && redirects < 10) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) break;

                    currentUrl = new URL(currentUrl, location);
                    String protocol = currentUrl.getProtocol();
                    if (!protocol.equals("http") && !protocol.equals("https")) {
                        throw new IOException("Unsupported redirect");
                    }

                    conn.disconnect();
                    conn = (HttpURLConnection) currentUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setUseCaches(false);

                    code = conn.getResponseCode();
                    redirects++;
                }

                try (InputStream input = conn.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    while (input.read(buffer) != -1) {}
                } catch (IOException ignored) {
                    // The portal may disconnect the network during logout.
                }

                if (code < 200 || code >= 400) {
                    throw new IOException("Unexpected logout response HTTP " + code);
                }
                // The request completed; do NOT claim paid internet is verified offline.
                prefs().edit().putBoolean("session_active", false)
                        .putString("connection_status", "logout_requested")
                        .putBoolean("logout_pending_whatsapp", prefs().getBoolean("auto_whatsapp", true))
                        .putString("free_portal_state", "pending")
                        .apply();
                SessionLogoutAlarm.cancel(this);
                report("logout_requested", "Logout request finished (HTTP " + code + "). Verify paid session ended.");
                if (prefs().getBoolean("auto_whatsapp", true)) {
                    alertAutomaticLogout("Logout request finished. Open Crew Connect to activate Free WhatsApp Text.");
                } else if (automatic) {
                    alertAutomaticLogout("Logout request finished. Verify paid internet is disconnected.");
                }
            } catch (Exception e) {
                report("unknown", "Logout request failed: " + e.getMessage());
                if (automatic) {
                    alertAutomaticLogout("Logout could not be confirmed. Check your Crew Wi-Fi connection.");
                }
            } finally {
                if (conn != null) conn.disconnect();
                logoutAttempted = false;
                // If automatic logout is disabled, the service was started
                // for a one-time manual request; don't leave it monitoring.
                if (!prefs().getBoolean("screen_lock", true) &&
                        !prefs().getBoolean("timed_disconnect", false)) stopSelf();
            }
        }, "WifiLogoutRequest").start();
    }

    @Override public void onDestroy() {
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

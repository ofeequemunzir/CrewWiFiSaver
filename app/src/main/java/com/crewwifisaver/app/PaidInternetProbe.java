package com.crewwifisaver.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Ship Wi-Fi-only connectivity signal. Never uses the default mobile data network.
 * HTTP 204 demonstrates open access to the probe host, NOT an authoritative
 * account entitlement. A failed request is UNKNOWN, not proof of logout.
 */
public final class PaidInternetProbe {
    private static final String PROBE_URL = "https://connectivitycheck.gstatic.com/generate_204";
    private PaidInternetProbe() {}

    public enum State { FULL, RESTRICTED, NO_WIFI, UNKNOWN }

    public static final class Result {
        public final State state;
        public final int httpCode;
        Result(State state, int code) { this.state = state; this.httpCode = code; }
        public boolean hasFullInternet() { return state == State.FULL; }
    }

    private static Network wifiNetwork(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return null;
        Network active = manager.getActiveNetwork();
        if (active != null) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(active);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return active;
        }
        // Android may prefer cellular while the ship's captive Wi-Fi is connected.
        // Query Wi-Fi explicitly, but never fall back to cellular/VPN.
        for (Network candidate : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(candidate);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return candidate;
        }
        return null;
    }

    public static HttpURLConnection openOnWifi(Context context, URL url) throws java.io.IOException {
        Network wifi = wifiNetwork(context);
        if (wifi == null) throw new java.io.IOException("Ship Wi-Fi unavailable");
        return (HttpURLConnection) wifi.openConnection(url);
    }

    public static Result check(Context context) {
        Network wifi = wifiNetwork(context);
        if (wifi == null) return new Result(State.NO_WIFI, 0);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) wifi.openConnection(new URL(PROBE_URL));
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(3500);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-cache");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_NO_CONTENT)
                return new Result(State.FULL, code);
            if (code >= 200 && code < 400)
                return new Result(State.RESTRICTED, code);
            return new Result(State.UNKNOWN, code);
        } catch (Exception e) {
            return new Result(State.UNKNOWN, 0);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** A single 204 may be temporary; a second attempt helps avoid false negatives. */
    public static Result checkWithRetry(Context context) {
        Result first = check(context);
        if (first.state != State.UNKNOWN) return first;
        return check(context);
    }

    /**
     * Called when app opens. Keep all UI/session preference writes centralized.
     * Do not replace a portal-confirmed package/time with invented values.
     */
    public static void updateOnAppOpen(Context context, Result result) {
        SharedPreferences prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("logout_in_progress", false)) return;
        boolean previouslyVerified = prefs.getBoolean("session_active", false)
                && "connected".equals(prefs.getString("connection_status", ""));
        SharedPreferences.Editor edit = prefs.edit();
        if (result.hasFullInternet()) {
            edit.putBoolean("session_active", true)
                .putString("connection_status", "connected");
            if (!previouslyVerified) {
                // If the portal itself confirmed this login, its timer is authoritative.
                // Otherwise this is only the time of first detection.
                if (!"portal".equals(prefs.getString("session_start_source", ""))) {
                    edit.putLong("session_started_elapsed", SystemClock.elapsedRealtime())
                        .putLong("session_started_wall", System.currentTimeMillis())
                        .putString("session_start_source", "detected")
                        .putInt("reported_remaining_seconds", -1)
                        .putString("active_package", "");
                }
            }
        } else {
            edit.putBoolean("session_active", false);
            switch (result.state) {
                case NO_WIFI: edit.putString("connection_status", "offline"); break;
                case RESTRICTED: edit.putString("connection_status", "restricted"); break;
                default: edit.putString("connection_status", "unknown"); break;
            }
            // A previous portal session may no longer exist. Its remaining balance
            // must not appear as a live reading.
            edit.putInt("reported_remaining_seconds", -1)
                .putString("active_package", "")
                .putString("session_start_source", "");
        }
        edit.apply();
        if (!result.hasFullInternet()) SessionLogoutAlarm.cancel(context);
        else SessionLogoutAlarm.update(context);
    }
}

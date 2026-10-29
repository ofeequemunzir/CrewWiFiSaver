package com.crewwifisaver.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Crew Connect v2.7: browser-owned sign-in; one Wi-Fi button; two automatic logout controls. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 17, 19);
    private static final int BUTTON = Color.rgb(31, 34, 37);
    private static final int WHITE = Color.rgb(241, 242, 243);
    private static final int MUTED = Color.rgb(155, 158, 163);
    private static final int DIVIDER = Color.rgb(44, 47, 50);
    private static final int ACCENT = Color.rgb(210, 214, 218);
    private static final String PORTAL = "https://onboardicafe.com/";
    private static final int MAX_HOURS = 23;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private SharedPreferences prefs;
    private LinearLayout rootContent;
    private WifiButton wifiButton;
    private TextView statusLabel;
    private TextView secondaryLabel;
    private TextView remainingLabel;
    private LinearLayout wheelContainer;
    private NumberPicker hourPicker;
    private NumberPicker minutePicker;
    private NumberPicker secondPicker;
    private TextView timerCountdown;
    private View timerTitles;
    private View timerWheels;
    private View timerHint;
    private Button timerAction;
    private boolean savingWheels = false;
    private boolean uiBusy = false;
    private boolean manualLogoutRequested = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        LogoutAlarm.cancel(this); // Clear old screen-delay alarms from pre-v2.6 versions.
        if (!prefs.contains("screen_lock")) {
            boolean old = prefs.getBoolean("auto", true);
            prefs.edit().putBoolean("screen_lock", old).apply();
        }
        // Older installations used 30-second intervals. Existing settings remain intact.
        if (!prefs.contains("timed_seconds") || prefs.getInt("timed_seconds", 0) <= 0)
            prefs.edit().putInt("timed_seconds", 300).apply();
        if (!prefs.getBoolean("v27_privacy_migrated", false)) {
            // The earlier embedded portal could have left cached balance and package details.
            // They must never be presented as live readings after a browser-only sign-in.
            prefs.edit().putInt("reported_remaining_seconds", -1)
                    .putString("active_package", "")
                    .putBoolean("v27_privacy_migrated", true).apply();
        }
        buildUi();
        updateMonitor();
        requestNotificationPermissionIfNeeded();
    }

    private int dp(float px) { return Math.round(px * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private TextView label(String value, int size, int color, boolean strong) {
        TextView tv = new TextView(this);
        tv.setText(value); tv.setTextColor(color); tv.setTextSize(size);
        tv.setIncludeFontPadding(false);
        tv.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return tv;
    }
    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }
    private LinearLayout row() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        return box;
    }
    private void space(LinearLayout container, int height) {
        container.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void line(LinearLayout container) {
        View v = new View(this); v.setBackgroundColor(DIVIDER);
        container.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private Switch toggle(boolean on) {
        Switch s = new Switch(this);
        s.setShowText(false);
        s.setChecked(on);
        s.setThumbTintList(new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_checked }, new int[] {} },
                new int[] { WHITE, WHITE }));
        s.setTrackTintList(new ColorStateList(new int[][] {
                new int[] { android.R.attr.state_checked }, new int[] {} },
                new int[] { Color.rgb(105, 110, 116), Color.rgb(65, 68, 72) }));
        return s;
    }
    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        rootContent = column();
        rootContent.setPadding(dp(23), dp(21), dp(23), dp(26));
        scroll.addView(rootContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);

        LinearLayout top = row();
        TextView brand = label("Crew Connect", 22, WHITE, false);
        brand.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = label("⚙", 24, MUTED, false);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("App settings");
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(44)));
        rootContent.addView(top);

        space(rootContent, 66);
        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        wifiButton = new WifiButton();
        wifiButton.setContentDescription("Check internet connection");
        wifiButton.setOnClickListener(v -> onWifiClicked());
        hero.addView(wifiButton, new LinearLayout.LayoutParams(dp(132), dp(132)));
        space(hero, 24);
        statusLabel = label("Checking connection…", 17, WHITE, true);
        statusLabel.setGravity(Gravity.CENTER);
        hero.addView(statusLabel);
        space(hero, 7);
        secondaryLabel = label("Please wait", 12, MUTED, false);
        secondaryLabel.setGravity(Gravity.CENTER);
        hero.addView(secondaryLabel);
        space(hero, 13);
        remainingLabel = label("", 12, MUTED, false);
        remainingLabel.setGravity(Gravity.CENTER);
        hero.addView(remainingLabel);
        rootContent.addView(hero);

        space(rootContent, 75);
        line(rootContent);
        space(rootContent, 22);
        Switch lock = toggle(prefs.getBoolean("screen_lock", true));
        settingsRow(rootContent, "▢", "Auto logout when phone is locked",
                "Sign out when you lock your phone", lock);
        lock.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean("screen_lock", checked)
                    .putBoolean("auto", checked)
                    .putBoolean("instant", true).apply();
            updateMonitor();
            if (checked) requestNotificationPermissionIfNeeded();
        });
        space(rootContent, 22);
        line(rootContent);
        space(rootContent, 22);

        Switch timer = toggle(prefs.getBoolean("timed_disconnect", false));
        settingsRow(rootContent, "◷", "Auto logout timer", "Sign out after a selected duration", timer);
        space(rootContent, 12);
        wheelContainer = column();
        wheelContainer.setBackground(background(Color.rgb(23, 26, 29), 14));
        wheelContainer.setPadding(dp(10), dp(13), dp(10), dp(10));
        initWheels(wheelContainer);
        wheelContainer.setVisibility(timer.isChecked() ? View.VISIBLE : View.GONE);
        renderTimerUi();
        rootContent.addView(wheelContainer);
        timer.setOnCheckedChangeListener((v, checked) -> {
            wheelContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && prefs.getInt("timed_seconds", 0) <= 0) {
                prefs.edit().putInt("timed_seconds", 300).apply();
                setWheelValues(300);
            }
            SharedPreferences.Editor timerEdit = prefs.edit().putBoolean("timed_disconnect", checked);
            if (!checked) timerEdit.putLong("timer_started_elapsed", -1L).putLong("timer_session", -1L);
            timerEdit.apply();
            renderTimerUi();
            SessionLogoutAlarm.update(this);
            updateMonitor();
            if (checked) { requestExactAlarmPermission(); requestNotificationPermissionIfNeeded(); }
        });

        space(rootContent, 25);
        TextView privacy = label("Login and package selection happen in your browser.", 11, MUTED, false);
        privacy.setGravity(Gravity.CENTER);
        rootContent.addView(privacy);
        space(rootContent, 15);
        setStatusUi();
    }

    private void settingsRow(LinearLayout host, String glyph, String heading, String sub, Switch control) {
        LinearLayout line = row();
        TextView symbol = label(glyph, 24, MUTED, false);
        symbol.setGravity(Gravity.CENTER);
        line.addView(symbol, new LinearLayout.LayoutParams(dp(34), dp(38)));
        LinearLayout names = column(); names.setPadding(dp(9), 0, dp(6), 0);
        names.addView(label(heading, 13, WHITE, true));
        space(names, 6);
        names.addView(label(sub, 11, MUTED, false));
        line.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        line.addView(control);
        host.addView(line);
    }

    private NumberPicker wheel(int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(0);
        picker.setMaxValue(max);
        picker.setFormatter(n -> String.format(Locale.US, "%02d", n));
        picker.setWrapSelectorWheel(max >= 5);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setValue(value);
        picker.setContentDescription("Select logout time");
        for (int j=0; j<picker.getChildCount(); j++) {
            View child=picker.getChildAt(j);
            if (child instanceof EditText) {
                ((EditText)child).setTextColor(WHITE);
                ((EditText)child).setTextSize(20);
                ((EditText)child).setGravity(Gravity.CENTER);
            }
        }
        return picker;
    }
    private void initWheels(LinearLayout host) {
        int seconds = Math.max(1, prefs.getInt("timed_seconds", 300));
        hourPicker = wheel(MAX_HOURS, Math.min(MAX_HOURS, seconds/3600));
        minutePicker = wheel(59, (seconds/60)%60);
        secondPicker = wheel(59, seconds%60);
        LinearLayout titles = row();
        for (String t : new String[]{"Hours", "Minutes", "Seconds"}) {
            TextView title = label(t, 11, MUTED, false);
            title.setGravity(Gravity.CENTER);
            titles.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        timerTitles = titles;
        host.addView(titles);
        space(host, 6);
        LinearLayout wheels = row();
        for (NumberPicker picker : new NumberPicker[]{hourPicker, minutePicker, secondPicker}) {
            wheels.addView(picker, new LinearLayout.LayoutParams(0, dp(134), 1f));
        }
        timerWheels = wheels;
        host.addView(wheels);
        NumberPicker.OnValueChangeListener onChange = (p, prev, value) -> persistTimerPicker();
        hourPicker.setOnValueChangedListener(onChange);
        minutePicker.setOnValueChangedListener(onChange);
        secondPicker.setOnValueChangedListener(onChange);
        TextView note = label("Scroll each column to set the logout time", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        timerHint = note;
        host.addView(note);
        space(host, 10);
        timerCountdown = label("", 22, WHITE, true);
        timerCountdown.setGravity(Gravity.CENTER);
        host.addView(timerCountdown);
        space(host, 9);
        timerAction = new Button(this);
        timerAction.setAllCaps(false);
        timerAction.setTextColor(WHITE);
        timerAction.setTextSize(13);
        timerAction.setBackground(background(Color.rgb(57, 61, 65), 12));
        timerAction.setOnClickListener(v -> {
            if (isTimerRunning()) {
                prefs.edit().putLong("timer_started_elapsed", -1L)
                        .putLong("timer_session", -1L).apply();
                SessionLogoutAlarm.cancel(this);
            } else {
                if (!prefs.getBoolean("session_active", false)
                        || !"connected".equals(prefs.getString("connection_status", ""))) {
                    Toast.makeText(this, "Connect paid internet before starting the timer", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putLong("timer_started_elapsed", SystemClock.elapsedRealtime())
                        .putLong("timer_session", prefs.getLong("session_started_elapsed", -1L)).apply();
                requestExactAlarmPermission();
                requestNotificationPermissionIfNeeded();
                SessionLogoutAlarm.update(this);
            }
            renderTimerUi();
        });
        host.addView(timerAction, new LinearLayout.LayoutParams(-1, dp(44)));
    }
    private boolean isTimerRunning() {
        long start = prefs.getLong("timer_started_elapsed", -1L);
        long session = prefs.getLong("session_started_elapsed", -1L);
        return prefs.getBoolean("timed_disconnect", false) && prefs.getBoolean("session_active", false)
                && start >= 0 && start <= SystemClock.elapsedRealtime()
                && session >= 0 && session == prefs.getLong("timer_session", -1L)
                && prefs.getLong("logout_timer_fired_session", -1L) != session;
    }
    private void renderTimerUi() {
        if (timerAction == null || timerCountdown == null || hourPicker == null) return;
        boolean running = isTimerRunning();
        for (View part : new View[]{timerTitles, timerWheels, timerHint})
            if (part != null) part.setVisibility(running ? View.GONE : View.VISIBLE);
        timerAction.setText(running ? "Cancel Timer" : "Start Timer");
        if (running) {
            long start = prefs.getLong("timer_started_elapsed", -1L);
            long elapsed = Math.max(0, (SystemClock.elapsedRealtime() - start) / 1000L);
            long remaining = Math.max(0, prefs.getInt("timed_seconds", 300) - elapsed);
            timerCountdown.setText(String.format(Locale.US, "%02d:%02d:%02d",
                    remaining/3600, (remaining/60)%60, remaining%60));
        } else timerCountdown.setText("");
    }
    private void setWheelValues(int seconds) {
        savingWheels = true;
        hourPicker.setValue(Math.min(MAX_HOURS, seconds/3600));
        minutePicker.setValue((seconds/60)%60);
        secondPicker.setValue(seconds%60);
        savingWheels = false;
    }
    private void persistTimerPicker() {
        if (savingWheels) return;
        int seconds = hourPicker.getValue()*3600 + minutePicker.getValue()*60 + secondPicker.getValue();
        if (seconds == 0) {
            // Never create an instantaneous repeated auto-logout alarm.
            seconds = 1;
            savingWheels = true;
            secondPicker.setValue(1);
            savingWheels = false;
        }
        prefs.edit().putInt("timed_seconds", seconds).apply();
        // Changing the wheels updates the selected duration, but never starts the timer.
        renderTimerUi();
    }

    private void setStatusUi() {
        if (wifiButton == null || statusLabel == null) return;
        String current = prefs.getString("connection_status", "unknown");
        boolean loggingOut = manualLogoutRequested
                || prefs.getBoolean("logout_in_progress", false);
        boolean active = "connected".equals(current)
                && prefs.getBoolean("session_active", false);
        // "Checking" is only a temporary on-screen network request, never a
        // persistent status. In particular UNKNOWN must not spin forever.
        boolean checking = uiBusy && !loggingOut;
        wifiButton.setState((loggingOut || checking) ? 1 : (active ? 2 : 0));
        if (loggingOut) {
            statusLabel.setText("Disconnecting…");
            secondaryLabel.setText("Finishing paid internet logout");
        } else if (checking) {
            statusLabel.setText("Checking connection…");
            secondaryLabel.setText("Please wait");
        } else if (active) {
            statusLabel.setText("Connected");
            secondaryLabel.setText("Tap to disconnect");
        } else if ("restricted".equals(current)) {
            statusLabel.setText("Disconnected");
            String freeState = prefs.getString("free_portal_state", "");
            if ("completed".equals(freeState)) {
                // The completion page is observable; actual WhatsApp message
                // delivery cannot be verified from inside this application.
                secondaryLabel.setText("Free WhatsApp setup complete");
            } else if (prefs.getBoolean("logout_pending_whatsapp", false)) {
                secondaryLabel.setText("Free WhatsApp setup pending");
            } else {
                secondaryLabel.setText("Tap to connect");
            }
        } else if ("offline".equals(current)) {
            statusLabel.setText("Disconnected");
            secondaryLabel.setText("Connect to the ship's Wi-Fi");
        } else {
            statusLabel.setText("Connection unavailable");
            secondaryLabel.setText("Tap Wi-Fi to retry");
        }
        wifiButton.setContentDescription((loggingOut || checking) ? "Connection update in progress" :
                (active ? "Disconnect paid internet" : "Open onboard iCafe sign-in in browser"));
        int remaining = prefs.getInt("reported_remaining_seconds", -1);
        String source = prefs.getString("session_start_source", "");
        if (active && remaining >= 0 && "portal".equals(source)) {
            remainingLabel.setText((remaining / 60) + " min remaining (last reported)");
        } else {
            remainingLabel.setText("");
        }
    }
    private void onWifiClicked() {
        if (!busy.compareAndSet(false, true)) return;
        // A known active session goes straight to the logout service. The
        // service checks Wi-Fi itself; don't waste another check showing a
        // prolonged "Checking" label before starting the requested action.
        if ("connected".equals(prefs.getString("connection_status", ""))
                && prefs.getBoolean("session_active", false)) {
            busy.set(false);
            manualLogoutRequested = true;
            setStatusUi();
            wifiButton.animate().scaleX(.96f).scaleY(.96f).setDuration(100)
                    .withEndAction(() -> wifiButton.animate()
                            .scaleX(1).scaleY(1).setDuration(130).start()).start();
            startMonitor(MonitorService.ACTION_LOGOUT);
            // The service's own logout_in_progress flag takes over shortly.
            // The local feedback flag must never survive indefinitely.
            ui.postDelayed(() -> {
                manualLogoutRequested = false;
                if (!isFinishing() && !isDestroyed()) setStatusUi();
            }, 2500);
            return;
        }
        uiBusy = true;
        setStatusUi();
        wifiButton.animate().scaleX(.95f).scaleY(.95f).setDuration(105)
                .withEndAction(() -> wifiButton.animate().scaleX(1).scaleY(1).setDuration(140).start())
                .start();
        new Thread(() -> {
            PaidInternetProbe.Result status = PaidInternetProbe.checkWithRetry(this);
            boolean wasLoggingOut = prefs.getBoolean("logout_in_progress", false);
            if (!wasLoggingOut) PaidInternetProbe.updateOnAppOpen(this, status);
            ui.post(() -> {
                busy.set(false);
                uiBusy = false;
                if (isFinishing() || isDestroyed()) return;
                setStatusUi();
                if (wasLoggingOut) return;
                if (status.hasFullInternet()) {
                    // No confirmation dialog or web page: tap-to-disconnect is direct.
                    startMonitor(MonitorService.ACTION_LOGOUT);
                    setStatusUi();
                } else if (status.state == PaidInternetProbe.State.NO_WIFI) {
                    new AlertDialog.Builder(this)
                            .setMessage("Connect your phone to the ship's Wi-Fi before signing in.")
                            .setPositiveButton("OK", null).show();
                } else {
                    // The user signs in ONLY in their normal browser: never inside our WebView.
                    openOfficialLogin();
                }
            });
        }, "CrewWifiButtonCheck").start();
    }
    private void openOfficialLogin() {
        Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL));
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        try { startActivity(browser); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser available to open onboard iCafe", Toast.LENGTH_LONG).show();
        }
    }
    private void updateMonitor() {
        boolean keepRunning = prefs.getBoolean("screen_lock", true) ||
                prefs.getBoolean("timed_disconnect", false);
        Intent intent = new Intent(this, MonitorService.class);
        if (keepRunning) {
            try { startForegroundService(intent); }
            catch (Exception e) {
                Toast.makeText(this, "Android blocked automatic logout monitoring", Toast.LENGTH_LONG).show();
            }
        } else {
            intent.setAction(MonitorService.ACTION_STOP);
            try { startService(intent); } catch (Exception ignored) { stopService(intent); }
        }
        SessionLogoutAlarm.update(this);
    }
    private void startMonitor(String action) {
        Intent intent = new Intent(this, MonitorService.class);
        intent.setAction(action);
        try { startForegroundService(intent); }
        catch (Exception e) {
            uiBusy = false; setStatusUi();
            Toast.makeText(this, "Unable to start logout. Try again.", Toast.LENGTH_LONG).show();
        }
    }
    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager manager = (AlarmManager)getSystemService(ALARM_SERVICE);
        if (manager == null || manager.canScheduleExactAlarms()) return;
        new AlertDialog.Builder(this)
                .setTitle("Allow precise logout timers")
                .setMessage("Android can delay timers without Alarms & reminders permission. You can enable it in Settings.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Settings", (dialog, id) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    try { startActivity(intent); } catch (Exception ignored) {}
                }).show();
    }
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 5);
        }
    }
    private void showSettings() {
        String[] options = {"Activate Free WhatsApp Text manually", "Toggle auto WhatsApp activation",
                "About & privacy"};
        new AlertDialog.Builder(this).setTitle("Crew Connect settings")
                .setItems(options, (d, option) -> {
                    if (option == 0) {
                        Intent intent = new Intent(this, FreeWhatsAppTestActivity.class);
                        intent.putExtra(FreeWhatsAppTestActivity.EXTRA_AUTOSTART, true);
                        startActivity(intent);
                    } else if (option == 1) {
                        boolean enabled = !prefs.getBoolean("auto_whatsapp", true);
                        prefs.edit().putBoolean("auto_whatsapp", enabled).apply();
                        Toast.makeText(this, "Free WhatsApp after logout: " +
                                (enabled ? "On" : "Off"), Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(this).setTitle("Crew Connect · v2.7")
                                .setMessage("Sign-in happens exclusively in your phone's browser at onboardicafe.com. " +
                                        "Crew Connect never asks for or stores your internet username or PIN. " +
                                        "Connectivity checks cannot guarantee your account balance, " +
                                        "and Android may delay background actions. Not an official cruise-line app.")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }
    private void refreshConnection() {
        if (!busy.compareAndSet(false, true)) return;
        uiBusy = true;
        setStatusUi();
        new Thread(() -> {
            PaidInternetProbe.Result result = PaidInternetProbe.checkWithRetry(this);
            if (!prefs.getBoolean("logout_in_progress", false))
                PaidInternetProbe.updateOnAppOpen(this, result);
            ui.post(() -> {
                busy.set(false);
                uiBusy = false;
                if (!isFinishing() && !isDestroyed()) setStatusUi();
            });
        }, "CrewWifiResumeProbe").start();
    }
    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            setStatusUi();
            renderTimerUi();
            if (!prefs.getBoolean("logout_in_progress", false)) maybeStartFreeWhatsApp();
            ui.postDelayed(this, 1300);
        }
    };
    private boolean launchedActivation = false;
    private void maybeStartFreeWhatsApp() {
        if (launchedActivation || !prefs.getBoolean("auto_whatsapp", true)
                || !prefs.getBoolean("logout_pending_whatsapp", false)) return;
        launchedActivation = true;
        prefs.edit().putBoolean("logout_pending_whatsapp", false).apply();
        Intent portal = new Intent(this, FreeWhatsAppTestActivity.class);
        portal.putExtra(FreeWhatsAppTestActivity.EXTRA_AUTOSTART, true);
        portal.putExtra(FreeWhatsAppTestActivity.EXTRA_RETURN_ON_COMPLETE, true);
        startActivity(portal);
    }
    @Override protected void onResume() {
        super.onResume();
        launchedActivation = false;
        SessionLogoutAlarm.update(this);
        ui.removeCallbacks(statusTicker);
        ui.post(statusTicker);
        // Finish a pending free-app activation when Android lets the app be visible.
        if (!prefs.getBoolean("logout_in_progress", false)) maybeStartFreeWhatsApp();
        refreshConnection(); // Recheck after returning from official browser login.
    }
    @Override protected void onPause() {
        ui.removeCallbacks(statusTicker);
        super.onPause();
    }
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Subtly raised control with a crisp, geometrically balanced Wi-Fi glyph. */
    private final class WifiButton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path glyph = new Path();
        private int state; // 0=offline, 1=working, 2=connected
        WifiButton() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setState(int next) {
            if (state == next) return;
            state = next;
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * .5f, cy = getHeight() * .5f;
            float r = Math.min(getWidth(), getHeight()) * .415f;

            // Quiet, tactile depth: a shallow shadow and one hairline rim.
            p.reset(); p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            p.setColor(BUTTON);
            p.setShadowLayer(dp(5), 0, dp(3), Color.argb(66, 0, 0, 0));
            canvas.drawCircle(cx, cy, r, p);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(.9f));
            p.setColor(state == 2 ? Color.rgb(211, 214, 217) : Color.rgb(77, 80, 84));
            canvas.drawCircle(cx, cy, r, p);

            // Single delicate highlight makes the surface read as a real button.
            p.setStrokeWidth(dp(.65f));
            p.setColor(Color.rgb(83, 87, 90));
            canvas.drawArc(new RectF(cx-r+dp(3), cy-r+dp(3),
                    cx+r-dp(3), cy+r-dp(3)), 208, 95, false, p);
            if (state == 1) {
                p.setStrokeWidth(dp(1.9f));
                p.setColor(WHITE);
                canvas.drawArc(new RectF(cx-r, cy-r, cx+r, cy+r), 265, 78, false, p);
            }

            // Draw the Wi-Fi mark from three symmetric smooth curves, not
            // elliptical arc segments. Consistent line width at every density.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(dp(1.8f));
            p.setColor(state == 2 ? WHITE : Color.rgb(222, 225, 228));
            float scale = r / dp(55f);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            glyph.reset();
            glyph.moveTo(-dp(25), -dp(8));
            glyph.quadTo(0, -dp(29), dp(25), -dp(8));
            glyph.moveTo(-dp(16), dp(1));
            glyph.quadTo(0, -dp(13), dp(16), dp(1));
            glyph.moveTo(-dp(7.5f), dp(10));
            glyph.quadTo(0, dp(3.5f), dp(7.5f), dp(10));
            canvas.drawPath(glyph, p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(0, dp(18), dp(2.2f), p);
            canvas.restore();
        }
    }
}

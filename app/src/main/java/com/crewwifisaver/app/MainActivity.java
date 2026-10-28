package com.crewwifisaver.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 15);
    private static final int CARD = Color.rgb(26, 30, 35);
    private static final int INNER = Color.rgb(36, 41, 48);
    private static final int WHITE = Color.rgb(246, 248, 249);
    private static final int MUTED = Color.rgb(153, 162, 173);
    private static final int GREEN = Color.rgb(154, 220, 188);
    private static final int DIVIDER = Color.rgb(51, 56, 63);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateLastLogout();
            handler.postDelayed(this, 2000);
        }
    };

    private SharedPreferences prefs;
    private Switch autoSwitch;
    private Switch instantSwitch;
    private Switch notificationSwitch;
    private SeekBar minutesBar;
    private SeekBar secondsBar;
    private LinearLayout delayControls;
    private TextView statusTitle;
    private TextView statusDescription;
    private TextView delayValue;
    private TextView minuteValue;
    private TextView secondValue;
    private TextView lastLogoutLabel;
    private WifiEmblem emblem;
    private boolean initializing = true;
    private boolean alarmPromptShown = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildInterface();
        initializing = false;
        updateControls();

        // The old app required a separate Start button. In v2.3 enabling
        // automatic logout starts the same foreground monitoring service.
        if (prefs.getBoolean("auto", true)) startMonitor(null);
        if (prefs.getBoolean("notify_logout", true)) requestNotificationPermissionIfNeeded();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    private void addSpace(LinearLayout parent, int dpHeight) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(dpHeight)));
    }

    private void addDivider(LinearLayout parent) {
        addSpace(parent, 16);
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        addSpace(parent, 16);
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private Switch createSwitch() {
        Switch sw = new Switch(this);
        sw.setShowText(false);
        int[][] states = {
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        sw.setThumbTintList(new ColorStateList(states,
                new int[]{GREEN, Color.rgb(188, 193, 199)}));
        sw.setTrackTintList(new ColorStateList(states,
                new int[]{Color.rgb(65, 121, 94), Color.rgb(76, 81, 87)}));
        return sw;
    }

    private void addToggle(LinearLayout card, String title, String subtitle, Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = vertical();
        text.addView(label(title, 15, WHITE, true));
        TextView desc = label(subtitle, 12, MUTED, false);
        desc.setPadding(0, dp(4), dp(8), 0);
        text.addView(desc);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(sw);
        card.addView(row);
    }

    private LinearLayout sliderCard(String title, SeekBar bar, boolean minute) {
        LinearLayout card = vertical();
        card.setBackground(background(INNER, 15));
        card.setPadding(dp(12), dp(13), dp(12), dp(10));
        card.addView(label(title, 11, Color.rgb(181, 189, 199), true));
        addSpace(card, 9);
        TextView number = label("0", 27, WHITE, true);
        card.addView(number);
        if (minute) minuteValue = number;
        else secondValue = number;
        addSpace(card, 8);
        bar.setPadding(0, 0, 0, 0);
        bar.setProgressTintList(ColorStateList.valueOf(GREEN));
        bar.setThumbTintList(ColorStateList.valueOf(GREEN));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(92, 100, 108)));
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(32)));
        LinearLayout limits = new LinearLayout(this);
        TextView left = label("0", 11, MUTED, false);
        TextView right = label(minute ? "5" : "59", 11, MUTED, false);
        limits.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        limits.addView(right);
        card.addView(limits);
        return card;
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout name = vertical();
        name.addView(label("login.com", 25, WHITE, true));
        TextView subtitle = label("SMART WI-FI CONTROL", 10, MUTED, true);
        subtitle.setLetterSpacing(0.15f);
        subtitle.setPadding(0, dp(4), 0, 0);
        name.addView(subtitle);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        WifiEmblem smallIcon = new WifiEmblem(false);
        header.addView(smallIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header);
        addSpace(root, 28);

        LinearLayout hero = vertical();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        emblem = new WifiEmblem(true);
        hero.addView(emblem, new LinearLayout.LayoutParams(dp(146), dp(146)));
        addSpace(hero, 17);
        statusTitle = label("Auto Logout Activated", 23, WHITE, true);
        statusTitle.setGravity(Gravity.CENTER);
        hero.addView(statusTitle);
        addSpace(hero, 8);
        statusDescription = label("", 13, MUTED, false);
        statusDescription.setGravity(Gravity.CENTER);
        hero.addView(statusDescription);
        root.addView(hero);
        addSpace(root, 27);

        LinearLayout settings = vertical();
        settings.setPadding(dp(18), dp(20), dp(18), dp(20));
        settings.setBackground(background(CARD, 21));
        autoSwitch = createSwitch();
        autoSwitch.setChecked(prefs.getBoolean("auto", true));
        addToggle(settings, "Automatic logout", "When the screen turns off", autoSwitch);
        addDivider(settings);
        instantSwitch = createSwitch();
        instantSwitch.setChecked(prefs.getBoolean("instant", false));
        addToggle(settings, "Instant logout", "As soon as your screen turns off", instantSwitch);
        addDivider(settings);

        delayControls = vertical();
        LinearLayout delayHeading = new LinearLayout(this);
        delayHeading.setGravity(Gravity.CENTER_VERTICAL);
        delayHeading.addView(label("Logout Delay", 15, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        delayValue = label("00:30", 16, GREEN, true);
        delayHeading.addView(delayValue);
        delayControls.addView(delayHeading);
        TextView explanation = label(
                "Logs out after your screen is locked for the selected time.",
                12, MUTED, false);
        explanation.setPadding(0, dp(7), 0, dp(17));
        delayControls.addView(explanation);

        minutesBar = new SeekBar(this);
        secondsBar = new SeekBar(this);
        minutesBar.setMax(5);
        secondsBar.setMax(59);
        int saved = Math.max(10, Math.min(359, prefs.getInt("lock_seconds", 30)));
        minutesBar.setProgress(saved / 60);
        secondsBar.setProgress(saved % 60);
        LinearLayout sliders = new LinearLayout(this);
        sliders.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout minutesCard = sliderCard("MINUTES", minutesBar, true);
        LinearLayout secondsCard = sliderCard("SECONDS", secondsBar, false);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp1.setMargins(0, 0, dp(6), 0);
        sliders.addView(minutesCard, lp1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp2.setMargins(dp(6), 0, 0, 0);
        sliders.addView(secondsCard, lp2);
        delayControls.addView(sliders);
        settings.addView(delayControls);
        root.addView(settings);
        addSpace(root, 19);

        Button logout = new Button(this);
        logout.setAllCaps(false);
        logout.setText("Log Out Now");
        logout.setTextSize(16);
        logout.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        logout.setTextColor(Color.rgb(19, 22, 25));
        logout.setBackground(background(Color.rgb(243, 245, 246), 15));
        logout.setElevation(dp(0));
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Log out now?")
                .setMessage("Send the Wi-Fi logout request? This may end your paid internet session.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) ->
                        startMonitor(MonitorService.ACTION_LOGOUT))
                .show());
        root.addView(logout, new LinearLayout.LayoutParams(-1, dp(56)));
        addSpace(root, 16);

        notificationSwitch = createSwitch();
        notificationSwitch.setChecked(prefs.getBoolean("notify_logout", true));
        LinearLayout notificationRow = new LinearLayout(this);
        notificationRow.setPadding(dp(5), 0, dp(5), 0);
        addToggle(notificationRow, "Lock-screen notifications",
                "Alert when an automatic logout request finishes", notificationSwitch);
        root.addView(notificationRow);
        addSpace(root, 17);
        lastLogoutLabel = label("Last logout attempt · None yet", 11, MUTED, false);
        lastLogoutLabel.setGravity(Gravity.CENTER);
        root.addView(lastLogoutLabel);
        addSpace(root, 10);
        TextView version = label("login.com  ·  v2.3 beta", 10, Color.rgb(105, 113, 124), false);
        version.setGravity(Gravity.CENTER);
        root.addView(version);
        setContentView(scroll);

        autoSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (initializing) return;
            prefs.edit().putBoolean("auto", checked).apply();
            if (checked) startMonitor(null);
            else stopService(new Intent(this, MonitorService.class));
            updateControls();
            if (checked) requestExactAlarmIfNeeded();
        });
        instantSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (initializing) return;
            prefs.edit().putBoolean("instant", checked).apply();
            updateControls();
        });
        notificationSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("notify_logout", checked).apply();
            if (checked) requestNotificationPermissionIfNeeded();
        });
        minutesBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || initializing) return;
                if (progress == 0 && secondsBar.getProgress() < 10) secondsBar.setProgress(10);
                saveDelay();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        secondsBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || initializing) return;
                if (minutesBar.getProgress() == 0 && progress < 10) {
                    secondsBar.setProgress(10);
                }
                saveDelay();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void saveDelay() {
        int total = minutesBar.getProgress() * 60 + secondsBar.getProgress();
        total = Math.max(10, total);
        prefs.edit().putInt("lock_seconds", total).apply();
        updateControls();
    }

    private void updateControls() {
        if (autoSwitch == null) return;
        boolean enabled = autoSwitch.isChecked();
        boolean instant = instantSwitch.isChecked();
        instantSwitch.setEnabled(enabled);
        minutesBar.setEnabled(enabled && !instant);
        secondsBar.setEnabled(enabled && !instant);
        delayControls.setAlpha(enabled && !instant ? 1f : 0.45f);
        statusTitle.setText(enabled ? "Auto Logout Activated" : "Auto Logout Deactivated");
        emblem.setActive(enabled);
        int minutes = minutesBar.getProgress();
        int seconds = secondsBar.getProgress();
        minuteValue.setText(String.valueOf(minutes));
        secondValue.setText(String.valueOf(seconds));
        delayValue.setText(instant ? "Instant" :
                String.format(Locale.US, "%02d:%02d", minutes, seconds));
        if (!enabled) {
            statusDescription.setText("Automatic screen-off logout is disabled");
        } else if (instant) {
            statusDescription.setText("Logs out when your screen turns off");
        } else {
            String duration = (minutes > 0 ? minutes + " min " : "") + seconds + " sec";
            statusDescription.setText("Logs out " + duration + " after screen-off");
        }
    }

    private void updateLastLogout() {
        long timestamp = prefs.getLong("last_logout_attempt", 0L);
        if (lastLogoutLabel == null) return;
        String value = timestamp == 0L ? "None yet" :
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(timestamp));
        lastLogoutLabel.setText("Last logout attempt · " + value);
    }

    private void startMonitor(String action) {
        Intent intent = new Intent(this, MonitorService.class);
        if (action != null) intent.setAction(action);
        try {
            startForegroundService(intent);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Unable to start monitoring")
                    .setMessage(e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 5);
        }
    }

    private void requestExactAlarmIfNeeded() {
        if (alarmPromptShown || !prefs.getBoolean("auto", true) || Build.VERSION.SDK_INT < 31) return;
        AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (manager == null || manager.canScheduleExactAlarms()) return;
        alarmPromptShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Allow precise logout timer")
                .setMessage("Allow Alarms & reminders so Android can run the screen-off timer more reliably.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Open settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    try { startActivity(intent); }
                    catch (Exception e) {
                        new AlertDialog.Builder(this)
                                .setMessage("Find Alarms & reminders in Android app settings.")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    @Override protected void onResume() {
        super.onResume();
        updateLastLogout();
        requestExactAlarmIfNeeded();
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    /** Simple vector Wi-Fi emblem, no image assets or extra libraries required. */
    private class WifiEmblem extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active = true;
        private final boolean large;
        WifiEmblem(boolean large) { super(MainActivity.this); this.large = large; }
        void setActive(boolean value) { active = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float radius = Math.min(w, h) * 0.47f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(22, 28, 32));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(large ? 2 : 1));
            paint.setColor(active ? Color.rgb(104, 174, 141) : Color.rgb(69, 76, 84));
            canvas.drawCircle(cx, cy, radius - dp(1), paint);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(large ? 5 : 2));
            paint.setColor(active ? Color.rgb(213, 237, 223) : Color.rgb(132, 143, 155));
            float arc = radius * 0.64f;
            RectF outer = new RectF(cx - arc, cy - arc * 0.65f,
                    cx + arc, cy + arc * 1.35f);
            canvas.drawArc(outer, 225, 90, false, paint);
            float innerArc = arc * 0.56f;
            RectF inner = new RectF(cx - innerArc, cy - innerArc * 0.3f,
                    cx + innerArc, cy + innerArc * 1.7f);
            canvas.drawArc(inner, 225, 90, false, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy + radius * 0.47f, dp(large ? 4 : 2), paint);
        }
    }
}

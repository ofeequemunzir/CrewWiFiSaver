package com.crewwifisaver.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
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
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;

/** Compact three-tab Android UI. Uses the existing app ID so it installs as an update. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 17, 27);
    private static final int CARD = Color.rgb(17, 30, 43);
    private static final int INNER = Color.rgb(23, 39, 56);
    private static final int WHITE = Color.rgb(247, 248, 248);
    private static final int MUTED = Color.rgb(151, 169, 187);
    private static final int SOFT = Color.rgb(53, 77, 102);
    private static final int GREEN = Color.rgb(76, 220, 141);
    private static final int BLUE = Color.rgb(75, 136, 232);
    private static final int BORDER = Color.rgb(34, 53, 71);

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateSessionText();
            maybeLaunchFreePortal();
            handler.postDelayed(this, 1000);
        }
    };
    private LinearLayout content;
    private LinearLayout nav;
    private TextView elapsedView;
    private TextView remainingView;
    private TextView statusView;
    private TextView statusDetailView;
    private TextView packageView;
    private WifiCircle wifiCircle;
    private String selectedTab = "home";
    private String accountUsername = "";
    private String accountPin = ""; // Never saved in preferences or written to logs.
    private boolean portalOpening = false;
    private boolean promptShown = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        LogoutAlarm.cancel(this); // Cancel any pending v2.5 screen-off delay alarm.
        // One-time migration from the original screen-off delay design.
        if (!prefs.contains("screen_lock")) {
            boolean enabled = prefs.getBoolean("auto", true);
            prefs.edit().putBoolean("screen_lock", enabled)
                    .putBoolean("auto", enabled)
                    .putBoolean("instant", true)
                    .apply();
        }
        if (prefs.getBoolean("screen_lock", true)) startMonitor(null);
        buildShell();
        showTab("home");
        if (prefs.getBoolean("screen_lock", true) || prefs.getBoolean("timed_disconnect", false))
            requestNotificationPermission();
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color); gd.setCornerRadius(dp(radius)); return gd;
    }
    private GradientDrawable outlined(int color, int radius) {
        GradientDrawable gd = rounded(color, radius);
        gd.setStroke(dp(1), BORDER); return gd;
    }
    private TextView text(String s, int sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private void space(LinearLayout l, int h) {
        l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
    }
    private void divider(LinearLayout l) {
        View v = new View(this); v.setBackgroundColor(BORDER);
        l.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private Button button(String label, int color, int foreground, int height) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(label); b.setTextSize(13);
        b.setLetterSpacing(0.025f); b.setTextColor(foreground);
        b.setTypeface(Typeface.create("sans-serif-medium", 0));
        b.setBackground(rounded(color, 30));
        b.setMinimumHeight(0); b.setMinHeight(0); b.setMinimumWidth(0); b.setMinWidth(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(height)));
        return b;
    }
    private Switch toggle(boolean checked) {
        Switch s = new Switch(this);
        s.setShowText(false); s.setChecked(checked);
        s.setThumbTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{WHITE, MUTED}));
        s.setTrackTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BLUE, SOFT}));
        return s;
    }
    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(17), dp(16), dp(17));
        c.setBackground(outlined(CARD, 17));
        return c;
    }
    private void buildShell() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        LinearLayout header = row();
        header.setPadding(dp(22), dp(22), dp(22), dp(14));
        TextView brand = text("Crew Connect", 26, WHITE, false);
        brand.setTypeface(Typeface.create("sans-serif-light", 0));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView settings = text("⚙", 23, MUTED, false);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Automation settings");
        settings.setOnClickListener(v -> showTab("automation"));
        header.addView(settings, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        content = column();
        content.setPadding(dp(22), dp(10), dp(22), dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        nav = row();
        nav.setPadding(dp(18), dp(10), dp(18), dp(10));
        nav.setBackgroundColor(BG);
        divider(root);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(78)));
        setContentView(root);
    }
    private void buildNav() {
        nav.removeAllViews();
        navItem("home", "Home");
        navItem("automation", "Automation");
        navItem("account", "Account");
    }
    private void navItem(String key, String label) {
        boolean selected = key.equals(selectedTab);
        LinearLayout item = column(); item.setGravity(Gravity.CENTER);
        NavIcon icon = new NavIcon(key, selected);
        item.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(26)));
        space(item, 3);
        TextView title = text(label, 11, selected ? WHITE : MUTED, selected);
        title.setGravity(Gravity.CENTER);
        item.addView(title);
        space(item, 7);
        View line = new View(this); line.setBackground(rounded(selected ? WHITE : BG, 2));
        item.addView(line, new LinearLayout.LayoutParams(dp(35), dp(2)));
        item.setOnClickListener(v -> showTab(key));
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
    }
    private void showTab(String tab) {
        selectedTab = tab;
        content.removeAllViews();
        elapsedView = null; remainingView = null; statusView = null;
        statusDetailView = null; packageView = null; wifiCircle = null;
        buildNav();
        if ("home".equals(tab)) buildHome();
        else if ("automation".equals(tab)) buildAutomation();
        else buildAccount();
        updateSessionText();
    }

    private boolean connected() {
        return prefs.getBoolean("session_active", false) &&
                "connected".equals(prefs.getString("connection_status", "unknown"));
    }
    private void buildHome() {
        // Hero: preserve the approved placement, keeping whitespace around the Wi-Fi ring.
        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        space(hero, 44);
        wifiCircle = new WifiCircle(); wifiCircle.setConnected(connected());
        hero.addView(wifiCircle, new LinearLayout.LayoutParams(dp(160), dp(160)));
        space(hero, 22);
        statusView = text("INTERNET OFFLINE", 16, WHITE, true);
        statusView.setLetterSpacing(0.16f);
        statusView.setGravity(Gravity.CENTER);
        hero.addView(statusView);
        space(hero, 9);
        statusDetailView = text("Your paid minutes are not in use", 13, MUTED, false);
        statusDetailView.setGravity(Gravity.CENTER);
        hero.addView(statusDetailView);
        content.addView(hero);
        space(content, 32);

        Button primary = button(connected() ? "INSTANT DISCONNECT" : "CONNECT TO INTERNET",
                connected() ? SOFT : WHITE, connected() ? WHITE : BG, 56);
        primary.setOnClickListener(v -> {
            if (connected()) confirmLogout();
            else showTab("account");
        });
        content.addView(primary);
        space(content, 25);

        LinearLayout metrics = row();
        LinearLayout left = column();
        left.addView(text("Session elapsed", 11, MUTED, false)); space(left, 7);
        elapsedView = text("00:00:00", 20, WHITE, true);
        left.addView(elapsedView);
        metrics.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        View line = new View(this); line.setBackgroundColor(BORDER);
        metrics.addView(line, new LinearLayout.LayoutParams(dp(1), dp(49)));
        LinearLayout right = column(); right.setPadding(dp(20), 0, 0, 0);
        right.addView(text("Minutes remaining", 11, MUTED, false)); space(right, 7);
        remainingView = text("—", 20, WHITE, true);
        right.addView(remainingView);
        metrics.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(metrics);
        space(content, 30);

        LinearLayout free = card();
        LinearLayout freeRow = row();
        freeRow.addView(new MessageIcon(), new LinearLayout.LayoutParams(dp(35), dp(35)));
        LinearLayout names = column(); names.setPadding(dp(9), 0, 0, 0);
        names.addView(text("Free WhatsApp Text", 14, WHITE, true));
        space(names, 5);
        String freeStatus = "completed".equals(prefs.getString("free_portal_state", ""))
                ? "Portal completed · verify with a text" : "Automatic activation after logout";
        names.addView(text(freeStatus, 11, MUTED, false));
        freeRow.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = toggle(prefs.getBoolean("auto_whatsapp", true));
        s.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("auto_whatsapp", on).apply());
        freeRow.addView(s); free.addView(freeRow);
        content.addView(free);
        space(content, 13);

        LinearLayout second = card();
        second.setOnClickListener(v -> showTab("automation"));
        LinearLayout summary = row();
        TextView shield = text("◇", 26, MUTED, false);
        shield.setGravity(Gravity.CENTER);
        summary.addView(shield, new LinearLayout.LayoutParams(dp(35), -2));
        LinearLayout names2 = column(); names2.setPadding(dp(9), 0, 0, 0);
        names2.addView(text("Automatic Disconnect", 14, WHITE, true));
        space(names2, 5);
        boolean lock = prefs.getBoolean("screen_lock", true);
        boolean timed = prefs.getBoolean("timed_disconnect", false);
        String description = (lock ? "Screen lock" : "Screen lock off") + "  ·  " +
                (timed ? formatDuration(prefs.getInt("timed_seconds", 300)) + " timer" : "Timer off");
        names2.addView(text(description, 11, MUTED, false));
        summary.addView(names2, new LinearLayout.LayoutParams(0, -2, 1f));
        summary.addView(text("›", 23, MUTED, false));
        second.addView(summary);
        content.addView(second);
        space(content, 24);
        TextView quick = text("Activate Free WhatsApp Text manually", 12, MUTED, false);
        quick.setGravity(Gravity.CENTER);
        quick.setPadding(0, dp(12), 0, dp(12));
        quick.setOnClickListener(v -> launchFreePortal(false));
        content.addView(quick);
    }

    private void buildAutomation() {
        space(content, 13);
        TextView title = text("Automation", 21, WHITE, true);
        content.addView(title);
        space(content, 9);
        content.addView(text("Set it once. Crew Connect handles the rest.", 12, MUTED, false));
        space(content, 25);

        LinearLayout lockCard = card();
        Switch lock = toggle(prefs.getBoolean("screen_lock", true));
        addSettingRow(lockCard, "▢", "Screen Lock Disconnect", "Log out when phone is locked", lock);
        lock.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean("screen_lock", checked)
                    .putBoolean("auto", checked).putBoolean("instant", true).apply();
            if (checked) startMonitor(null);
            else if (!prefs.getBoolean("timed_disconnect", false))
                stopService(new Intent(this, MonitorService.class));
        });
        content.addView(lockCard);
        space(content, 15);

        LinearLayout timerCard = card();
        Switch timed = toggle(prefs.getBoolean("timed_disconnect", false));
        addSettingRow(timerCard, "◷", "Timed Disconnect", "Disconnect after selected duration", timed);
        space(timerCard, 17);
        LinearLayout sliderPanel = column();
        sliderPanel.setPadding(dp(15), dp(17), dp(15), dp(16));
        sliderPanel.setBackground(rounded(INNER, 14));
        TextView value = text(formatDuration(prefs.getInt("timed_seconds", 300)), 29, WHITE, true);
        value.setGravity(Gravity.CENTER);
        sliderPanel.addView(value);
        space(sliderPanel, 7);
        TextView hint = text("Minutes : Seconds", 10, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        sliderPanel.addView(hint);
        space(sliderPanel, 12);
        SeekBar bar = new SeekBar(this); bar.setMax(29);
        int initial = Math.max(30, Math.min(900, prefs.getInt("timed_seconds", 300)));
        bar.setProgress((initial - 30) / 30);
        bar.setProgressTintList(ColorStateList.valueOf(BLUE));
        bar.setThumbTintList(ColorStateList.valueOf(WHITE));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(SOFT));
        bar.setEnabled(timed.isChecked()); bar.setAlpha(timed.isChecked() ? 1f : 0.35f);
        sliderPanel.addView(bar, new LinearLayout.LayoutParams(-1, dp(40)));
        LinearLayout limits = row();
        limits.addView(text("30 seconds", 11, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1f));
        limits.addView(text("15 minutes", 11, MUTED, false));
        sliderPanel.addView(limits);
        timerCard.addView(sliderPanel);
        timed.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean("timed_disconnect", checked).apply();
            bar.setEnabled(checked); bar.setAlpha(checked ? 1f : 0.35f);
            SessionLogoutAlarm.update(this);
            if (checked) { requestExactAlarm(); startMonitor(null); }
            else if (!prefs.getBoolean("screen_lock", true))
                stopService(new Intent(this, MonitorService.class));
        });
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int seconds = 30 + p * 30;
                value.setText(formatDuration(seconds));
                if (fromUser) {
                    prefs.edit().putInt("timed_seconds", seconds).apply();
                    SessionLogoutAlarm.update(MainActivity.this);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        content.addView(timerCard);
        space(content, 22);

        LinearLayout whatsappCard = card();
        Switch whats = toggle(prefs.getBoolean("auto_whatsapp", true));
        addSettingRow(whatsappCard, "◉", "Free WhatsApp Text",
                "Activate automatically after logout", whats);
        whats.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("auto_whatsapp", checked).apply());
        content.addView(whatsappCard);
        space(content, 16);
        content.addView(text("When both disconnect options are on, whichever happens first " +
                "requests logout. Android may delay an alarm while sleeping.", 11, MUTED, false));
        space(content, 12);
        content.addView(text("Automatic WhatsApp portal steps run when Crew Connect is visible. " +
                "After screen-lock logout, unlock the app to finish activation.", 11, MUTED, false));
    }
    private void addSettingRow(LinearLayout host, String glyph, String name, String subtitle, Switch toggle) {
        LinearLayout r = row();
        if ("Free WhatsApp Text".equals(name)) {
            r.addView(new MessageIcon(), new LinearLayout.LayoutParams(dp(34), dp(34)));
        } else {
            TextView icon = text(glyph, 23, MUTED, false);
            icon.setGravity(Gravity.CENTER);
            r.addView(icon, new LinearLayout.LayoutParams(dp(34), -2));
        }
        LinearLayout labels = column(); labels.setPadding(dp(8), 0, dp(8), 0);
        labels.addView(text(name, 13, WHITE, true)); space(labels, 6);
        labels.addView(text(subtitle, 11, MUTED, false));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(toggle);
        host.addView(r);
    }

    private EditText field(String hint, boolean secret) {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setTextColor(WHITE);
        input.setHintTextColor(MUTED); input.setTextSize(14);
        input.setPadding(dp(12), 0, dp(10), 0);
        input.setHint(hint); input.setBackground(outlined(INNER, 10));
        input.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD :
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        input.setImeOptions(secret ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        return input;
    }
    private void buildAccount() {
        space(content, 13);
        content.addView(text("Internet Account", 21, WHITE, true));
        space(content, 9);
        content.addView(text("Sign in securely through onboard iCafe.", 12, MUTED, false));
        space(content, 25);
        LinearLayout card = card();
        LinearLayout fields = row();
        LinearLayout userCol = column();
        userCol.addView(text("Username", 11, MUTED, false)); space(userCol, 9);
        EditText user = field("Username", false);
        user.setText(accountUsername);
        user.addTextChangedListener(new ShortTextWatcher() {
            @Override public void afterTextChanged(Editable e) { accountUsername = e.toString(); }
        });
        userCol.addView(user, new LinearLayout.LayoutParams(-1, dp(44)));
        fields.addView(userCol, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout pinCol = column(); pinCol.setPadding(dp(10), 0, 0, 0);
        pinCol.addView(text("PIN", 11, MUTED, false)); space(pinCol, 9);
        EditText pin = field("PIN", true);
        pin.setText(accountPin);
        pin.addTextChangedListener(new ShortTextWatcher() {
            @Override public void afterTextChanged(Editable e) { accountPin = e.toString(); }
        });
        pinCol.addView(pin, new LinearLayout.LayoutParams(-1, dp(44)));
        fields.addView(pinCol, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(fields);
        space(card, 20);
        Button sign = button("Sign In to Internet", WHITE, BG, 49);
        sign.setOnClickListener(v -> {
            if (accountUsername.trim().isEmpty() || accountPin.isEmpty()) {
                new AlertDialog.Builder(this).setMessage("Enter your username and PIN first.")
                        .setPositiveButton("OK", null).show();
                return;
            }
            Intent i = new Intent(this, PortalLoginActivity.class);
            i.putExtra(PortalLoginActivity.EXTRA_USER, accountUsername.trim());
            i.putExtra(PortalLoginActivity.EXTRA_PIN, accountPin);
            accountPin = ""; pin.setText("");
            startActivityForResult(i, 260);
        });
        card.addView(sign);
        space(card, 13);
        card.addView(text("Your PIN is never saved. The official portal will show your existing " +
                "packages so you can choose one. Purchases are not automatic.", 11, MUTED, false));
        content.addView(card);
        space(content, 18);
        LinearLayout info = card();
        info.addView(text("Your Internet Packages", 14, WHITE, true));
        space(info, 10);
        info.addView(text("Your purchased packages appear on the live portal after you sign in. " +
                "Select one and tap Log in to activate it.", 12, MUTED, false));
        space(info, 11);
        TextView current = text("Active plan: " +
                (connected() ? prefs.getString("active_package", "Internet") : "None"), 12, WHITE, false);
        info.addView(current);
        content.addView(info);
        space(content, 20);
        Button free = button("Activate Free WhatsApp Text", INNER, WHITE, 45);
        free.setOnClickListener(v -> launchFreePortal(false));
        content.addView(free);
    }

    private abstract static class ShortTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
    private static String formatDuration(int sec) {
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }
    private static String formatElapsed(long sec) {
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
    }
    private void updateSessionText() {
        if (statusView == null) return;
        boolean active = connected();
        String state = prefs.getString("connection_status", "offline");
        wifiCircle.setConnected(active);
        if (active) {
            statusView.setText("INTERNET ACTIVE");
            String svc = prefs.getString("active_package", "");
            statusDetailView.setText(svc.isEmpty() ? "Connected to internet" : svc);
            long start = prefs.getLong("session_started_elapsed", -1L);
            long now = SystemClock.elapsedRealtime();
            long spent = (start >= 0 && start <= now) ? (now - start) / 1000L : 0L;
            elapsedView.setText(formatElapsed(spent));
            int balance = prefs.getInt("reported_remaining_seconds", -1);
            remainingView.setText(balance < 0 ? "—" : formatDuration(Math.max(0, balance - (int)spent)));
        } else {
            boolean requested = "logout_requested".equals(state) || "unknown".equals(state);
            statusView.setText(requested ? "LOGOUT REQUESTED" : "INTERNET OFFLINE");
            statusDetailView.setText(requested ? "Paid session status not yet confirmed" :
                    "No paid session tracked in this app");
            elapsedView.setText("00:00:00");
            remainingView.setText("—");
        }
    }
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect paid internet?")
                .setMessage("Crew Connect will request logout. If enabled, it will then " +
                        "start Free WhatsApp Text activation while the app is open.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect", (dlg, which) -> startMonitor(MonitorService.ACTION_LOGOUT))
                .show();
    }
    private void launchFreePortal(boolean automatic) {
        if (portalOpening) return;
        portalOpening = true;
        Intent i = new Intent(this, FreeWhatsAppTestActivity.class);
        i.putExtra(FreeWhatsAppTestActivity.EXTRA_AUTOSTART, automatic);
        i.putExtra(FreeWhatsAppTestActivity.EXTRA_RETURN_ON_COMPLETE, automatic);
        startActivity(i);
    }
    private void maybeLaunchFreePortal() {
        if (portalOpening || !prefs.getBoolean("auto_whatsapp", true)
                || !prefs.getBoolean("logout_pending_whatsapp", false)) return;
        prefs.edit().putBoolean("logout_pending_whatsapp", false).apply();
        launchFreePortal(true);
    }
    private void startMonitor(String action) {
        Intent i = new Intent(this, MonitorService.class);
        if (action != null) i.setAction(action);
        try { startForegroundService(i); }
        catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Unable to start Crew Connect")
                    .setMessage("Android blocked the background service. Check notification settings.")
                    .setPositiveButton("OK", null).show();
        }
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 5);
        }
    }
    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT < 31 || promptShown) return;
        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
        if (am == null || am.canScheduleExactAlarms()) return;
        promptShown = true;
        new AlertDialog.Builder(this).setTitle("Allow precise disconnect times")
                .setMessage("Allow Alarms & reminders for accurate short timers. " +
                        "Without it, Android may delay a logout.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    try { startActivity(i); }
                    catch (Exception ignored) {}
                }).show();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 260 && resultCode == RESULT_OK) {
            accountPin = "";
            showTab("home");
        }
    }
    @Override protected void onResume() {
        super.onResume();
        portalOpening = false;
        if (prefs.getBoolean("timed_disconnect", false)) requestExactAlarm();
        SessionLogoutAlarm.update(this);
        if (content != null) showTab(selectedTab);
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 300);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    /** Compact line-icons for bottom navigation, without emoji or external images. */
    private class NavIcon extends View {
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String kind;
        private final boolean selected;
        NavIcon(String kind, boolean selected) {
            super(MainActivity.this);
            this.kind = kind; this.selected = selected;
        }
        @Override protected void onDraw(Canvas c) {
            float unit = Math.min(getWidth(), getHeight()) / 28f;
            c.save(); c.translate((getWidth() - unit*28f)/2f, (getHeight() - unit*28f)/2f);
            c.scale(unit, unit);
            pen.setColor(selected ? WHITE : MUTED);
            pen.setStrokeWidth(1.9f); pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
            pen.setStyle(Paint.Style.STROKE);
            if ("home".equals(kind)) {
                c.drawLine(4,13,14,5,pen); c.drawLine(14,5,24,13,pen);
                c.drawLine(7,12,7,24,pen); c.drawLine(21,12,21,24,pen);
                c.drawLine(7,24,21,24,pen); c.drawLine(12,24,12,17,pen); c.drawLine(12,17,16,17,pen);
                c.drawLine(16,17,16,24,pen);
            } else if ("automation".equals(kind)) {
                c.drawLine(4,7,24,7,pen); c.drawLine(4,14,24,14,pen);
                c.drawLine(4,21,24,21,pen);
                pen.setStyle(Paint.Style.FILL);
                pen.setColor(BG); c.drawCircle(11,7,3.5f,pen); c.drawCircle(19,14,3.5f,pen);
                c.drawCircle(9,21,3.5f,pen);
                pen.setStyle(Paint.Style.STROKE); pen.setColor(selected ? WHITE : MUTED);
                c.drawCircle(11,7,3.5f,pen); c.drawCircle(19,14,3.5f,pen);
                c.drawCircle(9,21,3.5f,pen);
            } else {
                c.drawCircle(14,9,4.5f,pen);
                c.drawArc(5,15,23,27,180,180,false,pen);
            }
            c.restore();
        }
    }

    /** Generic green text-message icon, inspired by free texting functionality. */
    private class MessageIcon extends View {
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        MessageIcon() { super(MainActivity.this); }
        @Override protected void onDraw(Canvas c) {
            float x = getWidth()/2f, y = getHeight()/2f;
            float r = Math.min(getWidth(), getHeight()) * 0.37f;
            pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(dp(2.4f));
            pen.setStrokeCap(Paint.Cap.ROUND); pen.setColor(GREEN);
            c.drawCircle(x,y,r,pen);
            c.drawLine(x-r*0.65f,y+r*0.72f,x-r*0.90f,y+r*1.2f,pen);
            c.drawLine(x-r*0.90f,y+r*1.2f,x-r*0.25f,y+r*0.94f,pen);
            c.drawArc(x-r*0.33f,y-r*0.42f,x+r*0.38f,y+r*0.42f,145,130,false,pen);
        }
    }

    private class WifiCircle extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;
        WifiCircle() { super(MainActivity.this); }
        void setConnected(boolean on) { active = on; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth()/2f, cy = getHeight()/2f;
            float r = Math.min(getWidth(), getHeight()) * 0.43f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(8)); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(active ? Color.rgb(26, 81, 62) : Color.rgb(41, 62, 83));
            c.drawCircle(cx, cy, r, p);
            if (active) {
                p.setColor(GREEN);
                c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r), -112, 292, false, p);
            }
            p.setStrokeWidth(dp(6)); p.setColor(active ? GREEN : Color.rgb(174, 193, 211));
            float ar = r*0.49f;
            c.drawArc(new RectF(cx-ar,cy-ar*0.38f,cx+ar,cy+ar*1.62f), 227, 86, false, p);
            ar *= 0.55f;
            c.drawArc(new RectF(cx-ar,cy+ar*0.1f,cx+ar,cy+ar*2.1f), 227, 86, false, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy+r*0.34f, dp(4), p);
        }
    }
}

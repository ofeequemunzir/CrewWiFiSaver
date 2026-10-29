package com.crewwifisaver.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Paid sign-in uses the official live portal. The user must personally choose an
 * owned plan and press Log in; the app never automatically buys a package.
 * PIN is used only in memory and is not logged or stored.
 */
public class PortalLoginActivity extends Activity {
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_PIN = "pin";
    private static final String URL = "https://onboardicafe.com/";
    private static final int BG = Color.rgb(7, 17, 27);
    private static final int SURFACE = Color.rgb(17, 30, 43);
    private static final int OFFWHITE = Color.rgb(246, 247, 247);
    private static final int MUTED = Color.rgb(164, 181, 197);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private Button retry;
    private String username;
    private String pin;
    private int stage = 0;
    private boolean working = false;
    private boolean connected = false;
    private long startedAt;
    private long lastClickAt = 0L;

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
    private GradientDrawable bg(int color) {
        GradientDrawable gd = new GradientDrawable(); gd.setColor(color);
        gd.setCornerRadius(dp(12)); return gd;
    }
    private static String quote(String v) { return JSONObject.quote(v == null ? "" : v); }
    private static String decoded(String raw) {
        try { return new JSONArray("[" + raw + "]").getString(0); }
        catch (Exception e) { return ""; }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        username = getIntent().getStringExtra(EXTRA_USER);
        pin = getIntent().getStringExtra(EXTRA_PIN);
        if (username == null) username = "";
        if (pin == null) pin = "";
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(BG); root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView title = new TextView(this); title.setText("Crew Connect · Internet Login");
        title.setTextColor(OFFWHITE); title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium", 0));
        top.addView(title);
        status = new TextView(this); status.setTextColor(MUTED); status.setTextSize(12);
        status.setText("Opening the official onboard portal…");
        status.setPadding(0, dp(6), 0, dp(8));
        top.addView(status);
        retry = new Button(this); retry.setAllCaps(false); retry.setText("Try Sign-In Steps");
        retry.setTextColor(OFFWHITE); retry.setTextSize(13); retry.setBackground(bg(SURFACE));
        top.addView(retry, new LinearLayout.LayoutParams(-1, dp(40)));
        root.addView(top);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                String host = req.getUrl().getHost();
                if (host != null && (host.equalsIgnoreCase("onboardicafe.com") ||
                        host.toLowerCase(Locale.ROOT).endsWith(".onboardicafe.com"))) return false;
                working = false;
                status.setText("This step opened an external page. For safety, use the onboard iCafe portal only.");
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) { inspect(); }
            @Override public void onReceivedError(WebView v, WebResourceRequest req,
                                                   android.webkit.WebResourceError error) {
                if (req.isForMainFrame()) {
                    working = false; status.setText("Portal unavailable. Check your ship's Wi-Fi.");
                }
            }
        });
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        retry.setOnClickListener(v -> {
            if (connected) return;
            stage = 0;
            working = true;
            startedAt = SystemClock.elapsedRealtime();
            lastClickAt = 0;
            status.setText("Looking for Access Internet…");
            browser.loadUrl(URL);
        });
        retry.performClick();
        handler.postDelayed(this::poll, 1200);
    }

    private void poll() {
        if (browser == null) return;
        inspect();
        handler.postDelayed(this::poll, 1500);
    }

    private void inspect() {
        if (browser == null || connected) return;
        // Only inspect portal text; do not collect PIN, cookies, user ID, or folio.
        String js = "(function(){try{var t=(document.body&&document.body.innerText||'');" +
                "var duration=t.match(/Duration\\s*:\\s*(\\d+)\\s*Minutes?\\s*[,\\s]*" +
                "(\\d+)\\s*(?:Secs?|Seconds?)/i);" +
                "var svc=t.match(/Service Type\\s*:\\s*([^\\n\\r]+)/i);" +
                "return JSON.stringify({connected:/you['’]re connected to\\s*internet/i.test(t)," +
                "minutes:duration?parseInt(duration[1],10):-1," +
                "seconds:duration?parseInt(duration[2],10):-1," +
                "service:svc?svc[1].trim().substring(0,80):''," +
                "packages:/log in with your active plan/i.test(t)," +
                "login:/sign in to connect/i.test(t)});" +
                "}catch(e){return '{}';}})();";
        browser.evaluateJavascript(js, raw -> {
            if (browser == null || connected) return;
            JSONObject result;
            try { result = new JSONObject(decoded(raw)); }
            catch (Exception e) { return; }
            if (result.optBoolean("connected")) {
                connected = true; working = false;
                // A server-rendered portal confirmation was observed. No fake "HTTP 200" success.
                int mins = result.optInt("minutes", -1);
                int secs = result.optInt("seconds", -1);
                int balance = (mins >= 0 && secs >= 0 && secs < 60) ? mins * 60 + secs : -1;
                String service = result.optString("service", "");
                getSharedPreferences("prefs", MODE_PRIVATE).edit()
                        .putBoolean("session_active", true)
                        .putString("connection_status", "connected")
                        .putString("active_package", service)
                        .putInt("reported_remaining_seconds", balance)
                        .putLong("session_started_elapsed", SystemClock.elapsedRealtime())
                        .putLong("session_started_wall", System.currentTimeMillis())
                        .putBoolean("logout_pending_whatsapp", false)
                        .apply();
                SessionLogoutAlarm.update(this);
                status.setText("Portal confirmed internet connected. Returning to Crew Connect…");
                pin = ""; username = "";
                setResult(RESULT_OK);
                handler.postDelayed(this::finish, 800);
                return;
            }
            if (result.optBoolean("packages")) {
                stage = 2; working = false; pin = "";
                status.setText("Choose an existing plan below and tap its Log in button. Purchases are never automatic.");
                retry.setText("Retry Sign-In Steps");
                return;
            }
            if (!working || SystemClock.elapsedRealtime() - startedAt > 30000L) {
                if (working) {
                    working = false;
                    status.setText("Automatic sign-in paused. Continue manually below or retry.");
                }
                return;
            }
            if (SystemClock.elapsedRealtime() - lastClickAt < 1800L) return;
            if (stage == 0) {
                // Click only the Access Internet tile, never the free-app tile.
                String step1 = "(function(){try{var nodes=document.querySelectorAll('a,button,[role=button],div,span');" +
                        "for(var i=0;i<nodes.length;i++){var el=nodes[i],s=(el.innerText||'').trim().toLowerCase();" +
                        "if(s==='access internet'&&el.getClientRects().length){" +
                        "(el.closest('a,button,[role=button],[onclick],[data-type=link]')||el).click();return 'clicked';}}" +
                        "return 'missing';}catch(e){return 'error';}})();";
                browser.evaluateJavascript(step1, val -> {
                    if ("clicked".equals(decoded(val))) {
                        stage = 1; lastClickAt = SystemClock.elapsedRealtime();
                        status.setText("Opening account sign-in…");
                    }
                });
            } else if (stage == 1 && result.optBoolean("login")) {
                // Set DOM inputs with native input/change events; do not expose credentials to logs.
                String fill = "(function(){try{var inputs=Array.from(document.querySelectorAll('input'));" +
                        "var u=inputs.find(function(e){return /user/i.test([e.name,e.id,e.placeholder].join(' '));});" +
                        "var p=inputs.find(function(e){return e.type==='password'||/pin/i.test([e.name,e.id,e.placeholder].join(' '));});" +
                        "if(!u||!p)return 'missing';" +
                        "var put=function(e,v){var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                        "setter.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "e.dispatchEvent(new Event('change',{bubbles:true}));};" +
                        "put(u," + quote(username) + ");put(p," + quote(pin) + ");" +
                        "var b=Array.from(document.querySelectorAll('button,input[type=submit],a')).find(function(e){" +
                        "return /sign in to connect/i.test(e.innerText||e.value||'');});" +
                        "if(!b)return 'filled';b.click();return 'submitted';" +
                        "}catch(e){return 'error';}})();";
                browser.evaluateJavascript(fill, value -> {
                    String r = decoded(value);
                    if ("submitted".equals(r)) {
                        working = false; stage = 2; pin = "";
                        status.setText("Signing in… Select your plan when it appears.");
                    } else if ("filled".equals(r)) {
                        working = false;
                        status.setText("Credentials filled. Tap Sign in to connect on the portal.");
                    } else if ("error".equals(r) || "missing".equals(r)) {
                        working = false;
                        status.setText("Could not locate sign-in fields. Enter them manually below.");
                    }
                });
                lastClickAt = SystemClock.elapsedRealtime();
            }
        });
    }
    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        pin = ""; username = "";
        if (browser != null) { browser.stopLoading(); browser.destroy(); browser = null; }
        super.onDestroy();
    }
}

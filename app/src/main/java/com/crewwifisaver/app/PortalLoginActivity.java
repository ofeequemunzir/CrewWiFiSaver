package com.crewwifisaver.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * An account sign-in assistant for the official onboard iCafe portal.
 * The user enters credentials once in Crew Connect. The WebView fills and
 * submits the official form, then stops at the package-selection page.
 * It never picks a plan or presses a purchase/activation button.
 * Credentials are never persisted or logged.
 */
public class PortalLoginActivity extends Activity {
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_PIN = "pin";
    public static final String EXTRA_LOGIN_ERROR = "login_error";
    public static final String ERROR_INVALID_PIN = "invalid_pin";
    public static final String ERROR_INVALID_USERNAME = "invalid_username";
    public static final String ERROR_INVALID_CREDENTIALS = "invalid_credentials";
    private static final String URL = "https://onboardicafe.com/";
    private static final int BG = Color.rgb(7, 17, 27);
    private static final int SURFACE = Color.rgb(17, 30, 43);
    private static final int OFFWHITE = Color.rgb(246, 247, 247);
    private static final int MUTED = Color.rgb(164, 181, 197);
    private static final long TIMEOUT_MS = 45000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private Button retry;
    private LinearLayout progressCover;
    private String username;
    private String pin;

    // 0 = portal entry; 1 = sign-in form; 2 = filling; 3 = submission pending;
    // 4 = package selection. Do not infer success merely from JS click().
    private int stage;
    private boolean working;
    private boolean connected;
    private boolean inspecting;
    private long startedAt;
    private long lastClickAt;
    private long submittedAt;

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
    private GradientDrawable bg(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(12));
        return gd;
    }
    private static String quote(String v) { return JSONObject.quote(v == null ? "" : v); }
    private static String decoded(String raw) {
        try { return new JSONArray("[" + raw + "]").getString(0); }
        catch (Exception e) { return ""; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        username = getIntent().getStringExtra(EXTRA_USER);
        pin = getIntent().getStringExtra(EXTRA_PIN);
        if (username == null) username = "";
        if (pin == null) pin = "";

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(BG);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView title = new TextView(this);
        title.setText("Crew Connect · Internet Login");
        title.setTextColor(OFFWHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium", 0));
        top.addView(title);
        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(12);
        status.setText("Opening the official onboard portal…");
        status.setPadding(0, dp(6), 0, dp(8));
        top.addView(status);
        retry = new Button(this);
        retry.setAllCaps(false);
        retry.setText("Retry Automatic Sign-In");
        retry.setTextColor(OFFWHITE);
        retry.setTextSize(13);
        retry.setBackground(bg(SURFACE));
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
                showManualPortal("External navigation blocked. Please use the official onboard iCafe portal.");
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) { inspect(); }
            @Override public void onReceivedError(WebView v, WebResourceRequest req,
                                                    android.webkit.WebResourceError error) {
                if (req.isForMainFrame()) {
                    working = false;
                    showManualPortal("Portal unavailable. Check your ship's Wi-Fi.");
                }
            }
        });
        // Keep the transitional sign-in page out of the way. Reveal the live
        // portal when packages are available or if manual assistance is needed.
        FrameLayout browserFrame = new FrameLayout(this);
        browserFrame.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        progressCover = new LinearLayout(this);
        progressCover.setGravity(Gravity.CENTER);
        progressCover.setOrientation(LinearLayout.VERTICAL);
        progressCover.setBackgroundColor(BG);
        TextView progressTitle = new TextView(this);
        progressTitle.setText("Signing in…");
        progressTitle.setTextSize(20);
        progressTitle.setTextColor(OFFWHITE);
        progressTitle.setGravity(Gravity.CENTER);
        progressCover.addView(progressTitle);
        TextView progressHint = new TextView(this);
        progressHint.setText("Your packages will appear here");
        progressHint.setTextSize(13);
        progressHint.setTextColor(MUTED);
        progressHint.setPadding(0, dp(12), 0, 0);
        progressHint.setGravity(Gravity.CENTER);
        progressCover.addView(progressHint);
        browserFrame.addView(progressCover, new FrameLayout.LayoutParams(-1, -1));
        root.addView(browserFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        retry.setOnClickListener(v -> beginSignIn());
        if (username.isEmpty() || pin.isEmpty()) {
            showManualPortal("Credentials missing. Go back and enter your username and PIN.");
            browser.loadUrl(URL);
        } else {
            beginSignIn();
        }
        handler.postDelayed(this::poll, 1200);
    }

    private void beginSignIn() {
        if (browser == null || connected) return;
        stage = 0;
        working = true;
        if (progressCover != null) progressCover.setVisibility(View.VISIBLE);
        startedAt = SystemClock.elapsedRealtime();
        lastClickAt = 0;
        submittedAt = 0;
        status.setText("Signing in automatically. You will choose your package next…");
        browser.loadUrl(URL);
    }

    private void showManualPortal(String message) {
        working = false;
        if (status != null) status.setText(message);
        if (progressCover != null) progressCover.setVisibility(View.GONE);
    }

    private void poll() {
        if (browser == null) return;
        inspect();
        handler.postDelayed(this::poll, 1500);
    }

    private static final String INSPECT_JS =
            "(function(){try{var t=(document.body&&document.body.innerText||'');" +
            "var duration=t.match(/Duration\\s*:\\s*(\\d+)\\s*Minutes?\\s*[,\\s]*" +
            "(\\d+)\\s*(?:Secs?|Seconds?)/i);" +
            "var svc=t.match(/Service Type\\s*:\\s*([^\\n\\r]+)/i);" +
            "return JSON.stringify({connected:/you['’]re connected to\\s*internet/i.test(t)," +
            "minutes:duration?parseInt(duration[1],10):-1," +
            "seconds:duration?parseInt(duration[2],10):-1," +
            "service:svc?svc[1].trim().substring(0,80):''," +
            "packages:/log in with your active plan|choose from\\s+\\d+\\s+options/i.test(t)," +
            "login:/sign in to connect/i.test(t)," +
            // Capture only a category, never the submitted username or PIN.
            "invalidPin:/invalid\\s+pin\\s+entered|(?:invalid|incorrect|wrong)\\s+(?:pin|password)/i.test(t)," +
            "invalidUser:/(?:invalid|incorrect|unknown|unrecognized)\\s+user(?:name)?|user(?:name)?\\s+(?:is\\s+)?(?:invalid|incorrect|not\\s+found|not\\s+recognized)/i.test(t)," +
            "invalidCredentials:/invalid\\s+(?:credentials|login\\s+details)|(?:incorrect|wrong)\\s+(?:username\\s+(?:or|and)\\s+pin|credentials)/i.test(t)});" +
            "}catch(e){return '{}';}})();";

    private static final String ACCESS_JS =
            "(function(){try{var nodes=document.querySelectorAll('a,button,[role=button],div,span');" +
            "var hit=null;for(var i=0;i<nodes.length;i++){var el=nodes[i];" +
            "var s=(el.innerText||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
            "if(s==='access internet'&&el.getClientRects().length){" +
            "if(!hit||el.querySelectorAll('*').length<hit.querySelectorAll('*').length)hit=el;}}" +
            "if(!hit)return 'missing';" +
            "var target=hit.closest('a,button,[role=button],[onclick],[data-type=link]')||hit;" +
            "target.click();return 'clicked';}catch(e){return 'error';}})();";

    private String fillCredentialsJs() {
        // Only fill the two visible fields on the official onboard iCafe login page.
        // Native DOM input/change/keyup events also notify portal-side validators.
        return "(function(){try{" +
                "var inputs=Array.from(document.querySelectorAll('input')).filter(function(e){" +
                "return e.type!=='hidden'&&e.type!=='submit'&&e.type!=='button'&&e.getClientRects().length;});" +
                "var meta=function(e){return [e.name,e.id,e.placeholder,e.getAttribute('aria-label')].join(' ').toLowerCase();};" +
                "var u=inputs.find(function(e){return /user|login/.test(meta(e));});" +
                "var p=inputs.find(function(e){return e.type==='password'||/pin|pass/.test(meta(e));});" +
                "if(!u)u=inputs.find(function(e){return e!==p&&(e.type==='text'||e.type==='email');});" +
                "if(!p)p=inputs.find(function(e){return e!==u;});" +
                "if(!u||!p||u===p)return 'missing';" +
                "var put=function(e,v){var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                "e.focus();setter.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));" +
                "e.dispatchEvent(new Event('change',{bubbles:true}));" +
                "e.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Tab'}));e.blur();};" +
                "put(u," + quote(username) + ");put(p," + quote(pin) + ");" +
                "return 'filled';}catch(e){return 'error';}})();";
    }

    private static final String SUBMIT_JS =
            "(function(){try{" +
            "var els=Array.from(document.querySelectorAll('button,input[type=submit],a,[role=button],[onclick]'));" +
            "var visible=function(e){return e.getClientRects().length>0;};" +
            "var label=function(e){return (e.innerText||e.value||'').replace(/\\s+/g,' ').trim().toLowerCase();};" +
            "var b=els.find(function(e){return visible(e)&&label(e)==='sign in to connect';});" +
            "if(!b)b=els.find(function(e){return visible(e)&&label(e).indexOf('sign in to connect')>=0;});" +
            "if(!b)return 'missing';" +
            "if(b.disabled||b.getAttribute('aria-disabled')==='true')return 'disabled';" +
            "b.click();return 'clicked';" +
            "}catch(e){return 'error';}})();";

    private void inspect() {
        if (browser == null || connected || inspecting) return;
        inspecting = true;
        browser.evaluateJavascript(INSPECT_JS, raw -> {
            inspecting = false;
            if (browser == null || connected) return;
            JSONObject result;
            try { result = new JSONObject(decoded(raw)); }
            catch (Exception e) { return; }

            if (result.optBoolean("connected")) {
                connected = true;
                working = false;
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
                        .putString("session_start_source", "portal")
                        .putBoolean("logout_pending_whatsapp", false)
                        .apply();
                SessionLogoutAlarm.update(this);
                status.setText("Internet connected. Returning to Crew Connect…");
                pin = "";
                username = "";
                setResult(RESULT_OK);
                handler.postDelayed(this::finish, 800);
                return;
            }
            if (result.optBoolean("packages")) {
                stage = 4;
                working = false;
                pin = "";
                username = "";
                status.setText("Signed in. Select your existing package and tap Log in below.");
                if (progressCover != null) progressCover.setVisibility(View.GONE);
                retry.setText("Signed In — Choose Package Below");
                retry.setEnabled(false);
                return;
            }
            // The portal can render this error without a full page navigation.
            // Never treat the submit button click as successful authentication.
            if (working && stage == 3 && (result.optBoolean("invalidPin") ||
                    result.optBoolean("invalidUser") || result.optBoolean("invalidCredentials"))) {
                String reason = result.optBoolean("invalidPin") ? ERROR_INVALID_PIN :
                        result.optBoolean("invalidCredentials") ? ERROR_INVALID_CREDENTIALS :
                        result.optBoolean("invalidUser") ? ERROR_INVALID_USERNAME : ERROR_INVALID_CREDENTIALS;
                reportInvalidLogin(reason);
                return;
            }
            if (!working) return;
            long now = SystemClock.elapsedRealtime();
            if (now - startedAt > TIMEOUT_MS) {
                showManualPortal("Automatic login timed out. You may continue manually on the portal below.");
                return;
            }
            if (stage == 3 && now - submittedAt > 15000L) {
                showManualPortal("Sign-in has not reached package selection. Check the portal for an error and continue manually if needed.");
                return;
            }
            if (result.optBoolean("login")) {
                // The WebView may open directly on the login page from an existing session.
                if (stage == 0) stage = 1;
                if (stage == 3) {
                    if (now - submittedAt > 7000L) {
                        showManualPortal("The portal has not confirmed sign-in. Check the form below for an error or tap Sign in manually.");
                        retry.setText("Retry Automatic Sign-In");
                    }
                    return;
                }
                if (stage == 1) fillSignIn();
                return;
            }
            if (stage == 0 && now - lastClickAt > 1800L) {
                lastClickAt = now;
                browser.evaluateJavascript(ACCESS_JS, value -> {
                    if (browser == null || !working || stage != 0) return;
                    if ("clicked".equals(decoded(value))) {
                        stage = 1;
                        status.setText("Opening the sign-in form…");
                    }
                });
            }
        });
    }

    private void reportInvalidLogin(String reason) {
        // Return to the app's Account tab with a safe error code. Do not return
        // the username/PIN, page HTML, or any session information.
        working = false;
        pin = "";
        username = "";
        Intent result = new Intent();
        result.putExtra(EXTRA_LOGIN_ERROR, reason);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    private void fillSignIn() {
        if (browser == null || stage != 1 || !working) return;
        stage = 2; // Prevent the polling loop from entering the form twice.
        status.setText("Entering your username and PIN…");
        browser.evaluateJavascript(fillCredentialsJs(), raw -> {
            if (browser == null || !working || stage != 2) return;
            String result = decoded(raw);
            if (!"filled".equals(result)) {
                showManualPortal("Could not fill the portal login fields. Please sign in manually below.");
                return;
            }
            status.setText("Submitting your login to the official portal…");
            // Allow the portal's client-side validation handlers to update the button.
            handler.postDelayed(this::submitSignIn, 450);
        });
    }

    private void submitSignIn() {
        if (browser == null || stage != 2 || !working) return;
        stage = 3;
        submittedAt = SystemClock.elapsedRealtime();
        browser.evaluateJavascript(SUBMIT_JS, raw -> {
            if (browser == null || !working || stage != 3) return;
            String result = decoded(raw);
            if ("clicked".equals(result)) {
                status.setText("Waiting for the package-selection page…");
            } else {
                showManualPortal("The portal did not accept the automatic Sign In click. The form is ready below; please tap Sign in to connect.");
                retry.setText("Retry Automatic Sign-In");
            }
        });
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        pin = "";
        username = "";
        if (browser != null) { browser.stopLoading(); browser.destroy(); browser = null; }
        super.onDestroy();
    }
}

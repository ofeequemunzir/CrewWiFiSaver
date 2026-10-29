package com.crewwifisaver.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/**
 * An opt-in, VISIBLE test of the two complimentary-app portal steps. It does not
 * assert that WhatsApp is active or that paid minutes have stopped. This activity
 * uses a separate WebView cookie jar, which may differ from the user's browser.
 */
public class FreeWhatsAppTestActivity extends Activity {
    public static final String EXTRA_AUTOSTART = "auto_start";
    public static final String EXTRA_RETURN_ON_COMPLETE = "return_on_complete";
    private static final String PORTAL = "https://onboardicafe.com/";
    private static final int DARK = Color.rgb(10, 12, 15);
    private static final int CARD = Color.rgb(26, 30, 35);
    private static final int WHITE = Color.rgb(246, 248, 249);
    private static final int MUTED = Color.rgb(167, 176, 186);
    private static final int GREEN = Color.rgb(154, 220, 188);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private TextView status;
    private Button autoButton;
    private boolean autoMode = false;
    private boolean returnWhenDone = false;
    private boolean reachedConfirmation = false;
    private boolean evaluating = false;
    private int step = 1;
    private long startedAt;

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private TextView text(String message, int size, int color) {
        TextView label = new TextView(this);
        label.setText(message);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private Button button(String title, int color, int textColor) {
        Button result = new Button(this);
        result.setText(title);
        result.setTextSize(14);
        result.setAllCaps(false);
        result.setTextColor(textColor);
        result.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        result.setBackground(shape(color));
        return result;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(DARK);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(10));
        panel.setBackgroundColor(CARD);
        TextView title = text("Free WhatsApp Text", 17, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(title);
        status = text("Log out of paid internet first. Then tap Try Automatic Steps.", 12, MUTED);
        status.setPadding(0, dp(8), 0, dp(8));
        panel.addView(status);
        autoButton = button("Try Automatic Steps", GREEN, DARK);
        panel.addView(autoButton, new LinearLayout.LayoutParams(-1, dp(48)));

        Button manual = button("Stop Automation / Use Manually", Color.rgb(51, 56, 63), WHITE);
        LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(-1, dp(44));
        manualLp.topMargin = dp(7);
        panel.addView(manual, manualLp);
        TextView note = text("This test does not start paid internet. A final portal page is not proof that WhatsApp works. Verify by sending a WhatsApp text.", 11, MUTED);
        note.setPadding(0, dp(9), 0, 0);
        panel.addView(note);
        root.addView(panel);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true); // Required by the onboard portal.
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);

        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                String host = req.getUrl().getHost();
                if (host != null && (host.equalsIgnoreCase("onboardicafe.com") ||
                        host.toLowerCase(java.util.Locale.ROOT).endsWith(".onboardicafe.com"))) {
                    return false;
                }
                status.setText("External navigation blocked. Only the onboard iCafe portal is used for this test.");
                autoMode = false;
                autoButton.setText("Try Automatic Steps");
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                evaluating = false;
                pollPage();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req,
                                                  android.webkit.WebResourceError err) {
                if (req.isForMainFrame()) {
                    status.setText("Portal unavailable. Check onboard Wi-Fi and try again.");
                    autoMode = false;
                    autoButton.setText("Try Automatic Steps");
                }
            }
        });
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        autoButton.setOnClickListener(v -> {
            if (reachedConfirmation) {
                status.setText("Portal completion screen reached. Test a WhatsApp text to verify free access.");
                return;
            }
            autoMode = true;
            step = 1;
            startedAt = android.os.SystemClock.elapsedRealtime();
            autoButton.setText("Automatic Test Running…");
            autoButton.setEnabled(false);
            status.setText("Looking for the first free-app button…");
            handler.postDelayed(() -> autoButton.setEnabled(true), 1000);
            pollPage();
        });
        manual.setOnClickListener(v -> {
            autoMode = false;
            autoButton.setText("Try Automatic Steps");
            autoButton.setEnabled(true);
            status.setText("Manual mode. Use the portal buttons yourself.\nThe test will watch for the final page.");
        });

        returnWhenDone = getIntent().getBooleanExtra(EXTRA_RETURN_ON_COMPLETE, false);
        if (getIntent().getBooleanExtra(EXTRA_AUTOSTART, false)) {
            autoMode = true;
            step = 1;
            startedAt = android.os.SystemClock.elapsedRealtime();
            autoButton.setText("Automatic Steps Running…");
            status.setText("Activating free WhatsApp text through the official portal…");
        }
        browser.loadUrl(PORTAL);
        handler.postDelayed(this::pollLoop, 1400);
    }

    /** Detect the real final-page text; do not claim the underlying entitlement succeeded. */
    private static final String INSPECT_JS =
            "(function(){try{var t=(document.body&&document.body.innerText||'').toLowerCase();" +
            "return t.includes('just launch the app')&&t.includes('ready to plan')?'complete':'waiting';" +
            "}catch(e){return 'waiting';}})();";

    /**
     * Look for ONLY the exact free-app labels. Never touch 'Access Internet',
     * purchase, sign-in, or paid-session controls. Click at most once per step.
     */
    private String clickScript(int currentStep) {
        String label = currentStep == 1 ? "use our app for free" : "use app for free";
        return "(function(){try{" +
                "var want='" + label + "';" +
                "var nodes=document.querySelectorAll('button,a,input,[role=button],[onclick],div,span,h1,h2,h3,h4,h5,h6,p,strong,em');" +
                "var hit=null;" +
                "for(var i=0;i<nodes.length;i++){var el=nodes[i];" +
                "var value=el.tagName==='INPUT'?el.value:(el.innerText||'');" +
                "value=value.toLowerCase().replace(/\\s+/g,' ').trim();" +
                "if(value===want&&el.getClientRects().length){" +
                "if(!hit||el.querySelectorAll('*').length<hit.querySelectorAll('*').length)hit=el;" +
                "}}" +
                "if(!hit)return 'missing';" +
                "var clickable=hit.closest('a,button,[role=button],[onclick],[data-type=link]')||hit;" +
                "clickable.click();return 'clicked';" +
                "}catch(e){return 'error';}})();";
    }

    private String decoded(String raw) {
        try { return new JSONArray("[" + raw + "]").getString(0); }
        catch (Exception ignored) { return "error"; }
    }

    private void pollLoop() {
        if (browser == null) return;
        pollPage();
        handler.postDelayed(this::pollLoop, 1300);
    }

    private void pollPage() {
        if (browser == null || reachedConfirmation || evaluating) return;
        evaluating = true;
        browser.evaluateJavascript(INSPECT_JS, raw -> {
            if (browser == null || reachedConfirmation) {
                evaluating = false;
                return;
            }
            if ("complete".equals(decoded(raw))) {
                evaluating = false;
                reachedConfirmation = true;
                autoMode = false;
                autoButton.setText("Portal Page Completed");
                autoButton.setEnabled(false);
                status.setText("Portal steps completed. Verify WhatsApp by sending a text; paid logout is not independently verified.");
                getSharedPreferences("prefs", MODE_PRIVATE).edit()
                        .putString("free_portal_state", "completed")
                        .putLong("free_portal_completed_at", System.currentTimeMillis())
                        .apply();
                if (returnWhenDone) handler.postDelayed(this::finish, 850);
                return;
            }
            if (!autoMode) {
                evaluating = false;
                return;
            }
            if (android.os.SystemClock.elapsedRealtime() - startedAt > 45000) {
                evaluating = false;
                autoMode = false;
                autoButton.setText("Retry Automatic Steps");
                autoButton.setEnabled(true);
                status.setText("Automatic clicks timed out. Use the portal manually or retry.");
                return;
            }
            final int expectedStep = step;
            browser.evaluateJavascript(clickScript(expectedStep), clickRaw -> {
                evaluating = false;
                if (!autoMode || step != expectedStep) return;
                String result = decoded(clickRaw);
                if ("clicked".equals(result)) {
                    status.setText(expectedStep == 1
                            ? "First free-app button clicked. Waiting for next page…"
                            : "Second free-app button clicked. Waiting for confirmation…");
                    step++;
                } else if ("error".equals(result)) {
                    autoMode = false;
                    autoButton.setText("Retry Automatic Steps");
                    autoButton.setEnabled(true);
                    status.setText("Automatic click failed. Please use the portal buttons manually.");
                }
            });
        });
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}

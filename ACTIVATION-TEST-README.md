# CrewWiFiSaver 2.5 — visible free WhatsApp activation test

This is an experimental test build; it does **not** yet perform invisible/background activation.

## What changed

- Preserved the existing `http://logout.com/` logout request and redirect handling.
- Removed the misleading method that claimed “Free WhatsApp Activated” when a website merely returned HTTP 200.
- Added a **Test Free WhatsApp Activation** button in the app main screen.
- Opens the official `https://onboardicafe.com/` portal inside an Android WebView.
- The **Try Automatic Steps** button looks for the two exact user-provided button labels: **Use our app for free** and **Use app for free**, and tries clicking them in order.
- The test only considers the final page reached when the words **Just launch the app** and **ready to plan** appear. Even this is **not independent proof WhatsApp is enabled**.
- Offers manual navigation if the portal does not allow scripted clicks.
- Uses no password collection or network interception; only first-party portal browsing.

## How to test

1. Use CrewWiFiSaver's working **Log Out Now**, and verify paid minutes are disconnected using the usual method.
2. On the same ship's Wi-Fi, open **Test Free WhatsApp Activation**.
3. If the portal shows the two familiar options, tap **Try Automatic Steps**.
4. Observe whether it completes the two clicks. If not, use **Stop Automation / Use Manually** to activate as usual.
5. When the final portal page appears, send a WhatsApp text to validate connectivity. Do not infer success from an HTTP code or the final webpage alone.
6. Send us the on-screen status message if it fails (don't send cookies, tokens, or personal account details).

## Caveats

- WebView has a different cookie jar from Chrome and Termux, so it may receive a different portal page.
- Website markup/JavaScript may prevent simulated clicks; the experiment is not guaranteed to succeed.
- The activity is visible on purpose so behavior can be checked before attempting reliable background automation.
- Android can restrict background launching, and offscreen WebViews may not reliably run. Background activation is deliberately deferred until we establish the activation mechanism.
- The success page is only portal evidence; WhatsApp activation and paid logout must be verified separately.

## Build

GitHub Actions workflow `.github/workflows/android.yml` builds using `gradle assembleDebug --no-daemon` when run manually or on a push to `main`.

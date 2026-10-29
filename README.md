# Crew Connect v2.7 Beta 2 — Minimal Home + Logout Feedback

An experimental Android application for users of a cruise-ship Wi-Fi captive portal.

**Beta 2 update:** See [V2.7-BETA2-FIX-NOTES.md](V2.7-BETA2-FIX-NOTES.md) for the corrected Wi-Fi glyph and the logout-status changes.

## What's new

- **Single, raised Wi-Fi action button.** Tap while offline to open the official `https://onboardicafe.com/` login page in your **regular browser**. After signing in and selecting a package there, return to Crew Connect; its ship-Wi-Fi-specific connectivity probe refreshes the button state.
- **Tap while connected** to request paid internet logout through the existing onboard logout endpoint, then verify the change in general internet access. The app does not open a browser just to log out.
- **No in-app username, PIN, or package-purchase fields.** The entire credential-collecting activity was removed from the source and Android manifest.
- **Minimal monochrome screen:** dark background, thin Wi-Fi icon, gentle raised-button shadow, no elapsed-session timer.
- **Two settings beneath the Wi-Fi button:** "Auto logout when phone is locked" and "Auto logout timer".
- **Native scrolling wheel timer:** hours (0–23), minutes (0–59), seconds (0–59). Appears only when the timer is enabled. Defaults to `00:05:00`, forbids zero length; runs once per paid internet session, regardless of activity.
- **Free WhatsApp Text:** preserves the existing verified two-button portal flow. After a verified paid logout, the free-app WebView flow starts when Android permits Crew Connect to display it; background/locked-screen completion is not guaranteed. A manual activation shortcut is available from the Settings gear.
- **Remaining minutes only:** shows a portal-reported balance if one exists. When sign-in uses the external browser, Crew Connect cannot read the browser's private account page and does not invent remaining minutes.

## Status reliability and device limitations

- Uses a lightweight `https://connectivitycheck.gstatic.com/generate_204` request **bound to Wi-Fi**, never cell data, to indicate general internet access. HTTP 204 is not authoritative proof of paid account entitlement. Network failures and inaccessible test servers can result in **Checking** or **Unknown**.
- The app does not attempt automatic paid logout when general internet access is not confirmed. After logout it verifies the change in connectivity before showing a tentative result.
- Logging in launches the **default browser** for privacy. Since the official portal has no known return-to-app callback, the browser may not automatically close; the user may need to return to Crew Connect manually.
- Android may defer background timers unless the user grants precise alarm permissions. A foreground notification may be required to monitor screen lock. Some Android versions and manufacturers restrict this behavior.
- Screenshots and status indicators should not be interpreted as authoritative minute billing, purchase status, or guaranteed WhatsApp activation.

## Build

Use the provided GitHub Actions workflow (`Actions` → `Build Android APK` → `Run workflow`) on a fresh branch, or compile with Gradle and Android SDK 35. The debug artifact is named `CrewConnect-v2.7-beta2-status-icon-debug-apk`. Application ID remains `com.crewwifisaver.app` so it can update the installed app; app display name is `Crew Connect`.

Do not install or distribute widely until the new flow has passed tests on the actual shipboard network. Preserve your previously working branch.

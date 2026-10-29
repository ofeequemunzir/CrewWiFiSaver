# Crew Connect v2.6 Beta 2 — login and incorrect-credentials fix

This is an **experimental** update to the existing Crew Connect v2.6 Beta 1 Android app.

## Intended login behavior

1. Enter a username and PIN once on Crew Connect's **Account** tab.
2. Tap **Sign In to Internet**. Crew Connect attempts to open **Access Internet** and fill and submit the official onboard iCafe form in a covered WebView.
3. If the portal confirms successful authentication, the real **package-selection page** appears. **You** choose the package to activate. The app never purchases or selects a package automatically.
4. If the portal displays an incorrect-PIN or incorrect-username error, Crew Connect returns to **Account** with a compact red message and red field outlines. Your username stays available for correction, but the PIN is cleared and must be re-entered.
5. Once a package is manually selected and the portal shows genuine internet-connected confirmation, the app returns Home and begins its session timer.

## What changed since the earlier Beta 2 draft

- Detects the portal's observed message: **“Invalid PIN entered, so please try again.”**
- Also recognizes a small number of common variations for invalid username/PIN/credentials; unrecognized errors still use the manual portal fallback.
- The app does not claim authentication succeeded until it sees the package-selection page.
- On recognized credential failure, the app **does not automatically retry**, to avoid repeatedly submitting incorrect PINs.
- Only an error code is sent back to the Account screen; PIN and portal page contents aren't logged or persisted.
- Dark minimal design and all existing disconnect/WhatsApp features are unchanged.
- Version code **8**, version name `2.6-beta2-login-errors`; package ID unchanged so it installs as an update when signed with the same key.

## Testing and caveats

- Java files passed a syntax parse and the error detection expressions passed offline sample tests. **No APK build or live ship portal test has been completed for this revision.** The portal could change its HTML or error wording.
- For testing, try an obviously invalid username/PIN **only once**. Expected: after submission the Account tab should show **“Invalid PIN entered. Please check your details and try again.”** if that is what the portal reports. Username should remain; PIN field should be empty.
- Correct credentials should still reach the package selection screen. Choose an existing package deliberately; you may consume paid minutes.
- Do not share a real PIN, folio number, or session cookies in screenshots.
- The paid login WebView is experimental. On timeouts or unrecognized portal states, the official page is revealed for manual recovery.

## Install as a test branch with Termux

Use the **small patch ZIP** (not the full source ZIP) and keep your existing `~/CrewWiFiSaver` repository on the `v2.6-beta1` branch first.

```sh
cd ~/CrewWiFiSaver
git switch -c v2.6-beta2-login-fix
unzip -o ~/storage/downloads/CrewConnect-v2.6-beta2-login-and-error-fix-patch.zip -d ~/CrewWiFiSaver
git add app/src/main/java/com/crewwifisaver/app/PortalLoginActivity.java app/src/main/java/com/crewwifisaver/app/MainActivity.java app/build.gradle.kts CREW-CONNECT-V2.6-BETA2-LOGIN-FIX.md
git commit -m "Fix duplicate login and invalid PIN feedback"
git push -u origin v2.6-beta2-login-fix
```

Then GitHub Actions → **Build Android APK** → **Run workflow** → branch `v2.6-beta2-login-fix`.

For users choosing the **full source ZIP** instead of the patch, extract it into a temporary folder and copy the project files; do **not** extract both ZIPs into the same repository.

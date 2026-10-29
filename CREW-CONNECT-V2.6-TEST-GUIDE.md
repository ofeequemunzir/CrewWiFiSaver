# Crew Connect v2.6 beta 1 — test build

This ZIP is the **Android SOURCE PROJECT**, not an APK. It is based on the successfully built CrewWiFiSaver v2.5 activation-test project and preserves the same Android application ID (`com.crewwifisaver.app`).

## What's included

- Updated app display name: **Crew Connect**.
- Dark, compact three-tab interface: **Home**, **Automation**, **Account**.
- Dark navy/near-black palette, understated off-white/blue controls, green online circle, no photo or tagline.
- Screen Lock Disconnect: request logout as soon as screen goes off (normally when pressing the lock button).
- **Independent** Timed Disconnect, starting at successful login and running even when phone is active. Slider: **30 seconds–15 minutes in 30-second increments**, default **5 minutes**.
- Preserves the existing `http://logout.com/` logout network request.
- Free WhatsApp Text: preserves the tested two-click WebView automation. When Crew Connect remains visible after logout, automatically opens the WebView activation sequence. When the phone is locked or app is in the background, the notification/opening Crew Connect is needed to finish the WebView sequence.
- Internet Account: compact username/PIN fields, attempts to navigate to Access Internet and submit credentials to the official `https://onboardicafe.com/` page, then shows **the live portal** to choose an owned plan manually. It **never automatically selects or purchases** a package.
- When the portal displays its connected confirmation page, the app attempts to extract the service name and remaining minutes, closes the WebView, returns Home, and starts session timer.
- PIN is not stored. Connection status, package, balance and elapsed timer are held in local app preferences.

## Important testing limitations

1. **This beta has not been compiled into an APK or tested on the ship yet.** GitHub Actions must build it and the login form may need adjustments after viewing the actual live portal in WebView.
2. We have only confirmed the v2.5 free WhatsApp two-button automation manually. Full invisible **background WebView automation has not been established**. v2.6 will attempt free activation while the app is visible; when locked, tap its notification/open Crew Connect to finish. Never label it successful simply because the final portal page loads; send a real WhatsApp text to verify.
3. The paid logout HTTP response is **not proof that paid internet has disconnected**. The home interface states “Logout requested” rather than falsely claiming full verification.
4. The displayed remaining minutes are extrapolated from the *last portal-reported value* and may differ from the server balance. Re-login or opening the portal is needed to refresh the actual balance.
5. Exact alarm permission and device power management affect how close to the chosen duration a timed disconnect occurs. On some Android versions the request may be delayed.
6. Neutraface is not bundled: it requires a licensed font file. A clean Android sans-serif fallback is used for this beta.
7. GitHub Actions debug APKs can be signed with different debug certificates between builds. If Android rejects an update due to signature mismatch, you may need to uninstall the previous debug APK (which resets the app's local settings). Back up settings first.
8. Because a login session costs internet minutes, first test with the free or lowest-risk existing package you choose yourself. Confirm charged internet time is not unexpectedly running before leaving it unattended.

## Upload from Termux without modifying the working branch

After downloading this ZIP to Android Downloads, use these commands **one at a time**, reviewing the current project status before overwriting files:

```sh
cd ~/CrewWiFiSaver
git status -sb
git switch -c v2.6-beta1
```

Extract the ZIP to a temporary directory and copy its contents to your project:

```sh
mkdir -p ~/CrewConnect-v2.6-beta1-source
unzip -q ~/storage/downloads/CrewConnect-v2.6-beta1-source.zip -d ~/CrewConnect-v2.6-beta1-source
cp -R ~/CrewConnect-v2.6-beta1-source/. ~/CrewWiFiSaver/
```

Stage **only the intended app/manifest/source files**, not historical backup files in your original repository:

```sh
cd ~/CrewWiFiSaver
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/crewwifisaver/app/ .github/workflows/android.yml CREW-CONNECT-V2.6-TEST-GUIDE.md
git commit -m "Add Crew Connect v2.6 beta interface and portal login test"
git push -u origin v2.6-beta1
```

Select branch `v2.6-beta1` in GitHub Actions and run **Build Android APK**, then download the `CrewConnect-v2.6-beta-debug-apk` artifact if the build succeeds. If the build fails, share the *first actual compiler error* (not login passwords or tokens).

## Suggested hands-on test order

1. Confirm app launches with the three dark tabs, updated name and icon.
2. Confirm Screen Lock and Timed Disconnect toggles persist, and slider runs from 00:30 to 15:00.
3. Use the existing manual Free WhatsApp Text flow on ship Wi-Fi. Test actual WhatsApp messaging.
4. Sign in through Account. Observe whether the two preliminary portal steps complete automatically. If not, operate the portal manually and tell us which step failed.
5. Choose your previously owned plan on the real portal and tap **Log in**. Confirm that the home shows the correct plan and session timer.
6. Test Instant Disconnect; then test a short timed disconnect while actively using the phone. Verify paid minutes stopped on the portal.
7. Finally test Screen Lock Disconnect and opening the app to complete free WhatsApp activation.

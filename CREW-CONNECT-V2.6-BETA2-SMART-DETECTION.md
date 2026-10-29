# Crew Connect v2.6 Beta 2 — Login, errors, and smart connection detection

This source builds on v2.6 Beta 1, with the existing sign-in automation and invalid-PIN error handling plus a new ship-Wi-Fi-only internet probe.

## What changes
- On opening/resuming Crew Connect, a lightweight GET to `https://connectivitycheck.gstatic.com/generate_204` is made specifically via the connected Wi-Fi network (never cellular). HTTP 204 shows full internet is reachable. The Wi-Fi ring turns green and the app shows its connected home mode.
- A successful probe when a session was not activated inside Crew Connect can only start an **elapsed time since detection** counter. It cannot reconstruct the actual earlier login time. It does not invent a package name or remaining minutes.
- For screen lock, elapsed timer, or manual disconnect, Crew Connect checks the same Wi-Fi endpoint *before* sending the paid logout request. If full access is not confirmed, it skips the logout and does not display an automatic logout alert.
- After a logout attempt, Crew Connect checks if full internet still works. When it stops working, it displays **“Internet appears disconnected”** rather than claiming server-side account minutes are definitively stopped. This check is an indicator, not a paid entitlement API.
- Automatic Free WhatsApp Text activation after background logout still requires opening Crew Connect; Android may not permit a hidden WebView while the phone is locked.
- The account sign-in flow still asks the user to select their existing package; it does not automatically purchase packages or save a PIN.

## Known limitations and safety
- HTTP 204 does not itself prove a particular paid package is active. Cellular traffic must never be used to infer shipboard paid status, so the check binds to Wi-Fi.
- A reset, timeout, or HTTP 000 can indicate temporary network trouble. The app shows an unverified/unknown state rather than a conclusive logout claim. It does not send a high-priority logout notification when the precheck fails.
- For short timed disconnects, Android's exact alarm permission and background foreground-service restrictions may affect timing.
- Depending on Celebrity's portal session/cookie behavior, login and activation still require onboard testing. Use a package with few minutes while testing.

## Build using GitHub Actions
From the existing Termux repository and after `git switch -c v2.6-beta2-smart`:

```bash
cp -R ~/CrewConnect-v2.6-beta2-smart/. ~/CrewWiFiSaver/
cd ~/CrewWiFiSaver
git add -u app
git add app/src/main/java/com/crewwifisaver/app/PaidInternetProbe.java CREW-CONNECT-V2.6-BETA2-SMART-DETECTION.md
# Also add PortalLoginActivity.java if it is not already tracked from Beta 1.
git commit -m "Crew Connect v2.6 Beta2 smart connection detection"
git push -u origin v2.6-beta2-smart
```

Select `v2.6-beta2-smart` in your GitHub Actions **Build Android APK** workflow.

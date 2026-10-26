# Crew WiFi Saver — v1.0 traffic baseline prototype

This is a deliberately limited Android test app. It displays **total device network traffic** (received + sent bytes per minute), sampled while the activity is visible. It does **not** measure Wi-Fi-only traffic, detect login state, monitor when locked, log out, or implement an idle threshold. Those require separate design, permissions and on-device validation. It does not ask for ship account credentials.

## Build entirely from your Android phone (GitHub)
1. Sign in to GitHub in your phone browser and create a new **private** repository.
2. Upload the contents of this ZIP into the repository, keeping `.github/workflows/android.yml` and the folder structure. GitHub's browser uploader may hide dotfolders on some file managers; verify the workflow exists. Alternatively use a phone-based Git client that can import a ZIP and push a repository.
3. Open the repository's **Actions** tab, choose **Build Android APK**, and tap **Run workflow** (or push to `main`).
4. Once the build succeeds, open its run page, download the `CrewWiFiSaver-debug-apk` artifact ZIP, extract `app-debug.apk`, and install it. Android may require granting your browser/file manager permission to install unknown apps.
5. Connect to the ship Wi-Fi and log in as normal. Open the app and observe idle vs active traffic. Do not rely on this prototype to save minutes.

**Privacy:** The source can be inspected before building. The current code never accesses usernames/passwords and makes no network requests of its own.

**Important:** Device-wide traffic counters include cellular and other interfaces, and may behave differently across Android versions. For production, evaluate Android `VpnService` for network traffic classification and a foreground service for locked-screen monitoring. A VPN can conflict with ship connectivity, so test carefully. Logout endpoint behavior must be validated with the actual captive portal.

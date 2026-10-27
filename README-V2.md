# CrewWiFiSaver v2 beta

New: background foreground-service monitoring, persistent notification, configurable 5–60 minute and 10–200 KB/min sliders, manual and threshold-triggered HTTP logout.com request over Wi-Fi.

**Important:** This is an experimental beta, not a validated paid-session safeguard. Android TrafficStats totals all network interfaces and can be affected by free traffic, mobile data, and the monitor's own traffic. It is NOT an accurate ship-Wi-Fi-only traffic reading. The app reports logout attempts as **unverified** because HTTP success does not establish that paid minutes stopped. The network may redirect or block requests. Do not rely on it to protect paid minutes until verified with the ship portal. Wi-Fi monitoring needs foreground service permissions and can stop under Android restrictions. The app will not auto-start after reboot. Android may restrict foreground-service starts, and some platforms require additional foreground-service type handling.

Build: use existing GitHub Actions workflow; install debug APK. Debug APK may use a different signing key than the previous download; if installation fails due to signature mismatch, back up settings and uninstall old app before installing beta.

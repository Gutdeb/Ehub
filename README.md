# Email Hub - Android App

A native Android shell for the [EmailHub](../emailhub) web app. It wraps your running
EmailHub server in a WebView and registers itself as a handler for `mailto:` links and
the "Send to" share intent, so tapping any email address on your phone opens EmailHub
with the recipient pre-filled and ready to send.

## Features

- **`mailto:` handler** - Tap any email link in any app (contacts, browser, WhatsApp, etc.)
  and Email Hub opens the Send page with the recipient already filled in.
- **Share intent handler** - Use "Share" on any text containing an email address; Email Hub
  extracts the first address and pre-fills it.
- **Auto-login** - Enter your EmailHub credentials once; the app logs in automatically and
  persists the session cookie across launches.
- **Encrypted storage** - Server URL, username, and password are stored with
  `EncryptedSharedPreferences` (Android Keystore).
- **Settings screen** - Change server URL / credentials any time from the menu.
- **No web-app changes** - Works against your existing, already-running EmailHub instance.
  The recipient is injected via JavaScript after the `/send` page loads.

## Requirements

- Android 7.0 (API 24) or newer
- A reachable EmailHub server (HTTP or HTTPS)
- Android Studio Koala (2024.1.1) or newer, OR Gradle 8.7+ with JDK 17

## Build

### From Android Studio
1. `File` > `Open` and select the `emailhub-android` folder.
2. Let Gradle sync finish.
3. Connect a device or start an emulator.
4. Click `Run` (or `Build` > `Build APK(s)`).

### From command line
```bash
cd emailhub-android
./gradlew assembleRelease
# APK at: app/build/outputs/apk/release/app-release-unsigned.apk
```
Sign the APK with your keystore before distributing, or use `assembleDebug` for testing:
```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

> Windows users: run `gradlew.bat` instead of `./gradlew`.

## First-run setup

1. Launch **Email Hub**.
2. On first run you are taken to **Settings**. Enter:
   - **EmailHub Server URL** - e.g. `https://mail.yourdomain.com` (no trailing slash)
   - **Username** - your EmailHub admin username (default: `admin`)
   - **Password** - your EmailHub password
3. Tap **Save & Connect**. The app logs in and shows the dashboard.

## Using it as your mail handler

After setup, any time you tap an email address (e.g. in Contacts, a website, or a
chat), Android will offer **Email Hub** as an option in the "Open with" / "Complete
action using" dialog. Choose it (and optionally tap **Always** to make it the default).

The Send page opens with the recipient pre-filled - just pick your SMTP server and
template and tap **Send**.

## Menu options

- **Refresh** - reload the current page.
- **Settings** - change server URL / credentials.
- **Logout** - clear the saved session and return to Settings.

## How it works (no web-app changes needed)

The app loads `/dashboard` (or `/send` when invoked from a `mailto:` link) in a WebView.
The custom `WebViewClient`:

1. Detects when the `/login` page loads and auto-submits the stored credentials via
   `document.forms[0]` field injection.
2. Captures the resulting `connect.sid` session cookie and persists it, so subsequent
   launches skip the login step.
3. After `/send` finishes loading, injects the recipient address into the `to_email`
   input via `document.getElementById('to_email').value = ...` and fires the existing
   `blur` handler so the "sent before" check still runs.

This means your running EmailHub web app does not need any modifications.

## Troubleshooting

- **Blank page / "ERR_CONNECTION_REFUSED"** - Make sure the server URL in Settings is
  reachable from the phone. If the server is on your LAN, use the LAN IP (e.g.
  `http://192.168.1.10:3000`), not `localhost`.
- **Login keeps failing** - Open Settings and re-enter the password. If you recently
  changed the EmailHub admin password via the web UI, update it here too.
- **App not offered for mailto: links** - Go to `Settings` > `Apps` > `Email Hub` >
  `Open by default` > `Add link` and enable `mailto`.
- **Mixed content blocked** - If EmailHub is served over HTTPS, the app will not load
  plain HTTP resources. Keep your EmailHub behind HTTPS in production.
- **Self-signed HTTPS certificate** - The WebView rejects self-signed certs by default.
  Use a real certificate (Let's Encrypt) or access the server over HTTP on your LAN.

## Project layout

```
emailhub-android/
├── build.gradle.kts            # Root Gradle config
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts        # App module + dependencies
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml # Intent filters for mailto: + SEND
        ├── java/com/emailhub/app/
        │   ├── AppConfig.kt        # EncryptedSharedPreferences for creds
        │   ├── MainActivity.kt     # WebView + auto-login + recipient injection
        │   └── SettingsActivity.kt # Server URL / username / password form
        └── res/
            ├── drawable/ic_launcher_foreground.xml
            ├── layout/activity_settings.xml
            ├── menu/main.xml
            ├── mipmap-anydpi-v26/  # Adaptive icons (API 26+)
            ├── mipmap-anydpi/      # Vector fallback (API 24-25)
            ├── values/{colors,strings,themes}.xml
            └── xml/{backup_rules,network_security_config}.xml
```

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Connect to your EmailHub server. |
| `ACCESS_NETWORK_STATE` | Detect connectivity changes (future use). |

No other permissions are requested. No analytics, no ads, no background services.

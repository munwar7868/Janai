# Munawar AI v0.5.0 (com.munawar.ai) — created by Munawar Uddin

Native Kotlin Android app (no AndroidX). Gemini chat + Gemini Live calls, 3D logo orb, "Hey Munawar" wake word,
phone automation, accessibility control, tools (photo/OCR/PDF/translate/study/planner/email), memory, smart home, updates.

> **Status:** written and statically cross-checked, but NOT compiled or run on a device by the author (no Android SDK available when it was written).
> If the first GitHub build fails, open the failed run → "Build release APK" step → copy the first lines starting with `e:` and send them to Claude.

## What's new in 0.5.0
- **Brand-new 3D look**: deep-space background (stars, planets, drifting colour clouds) that moves with your phone tilt; smaller 3D orb with colourful orbit rings passing in front of and behind it, glowing pedestal and audio-reactive lights; glass tiles with neon borders that tilt in 3D when pressed; bottom bar with a glowing crown button (talk) and a top bar with a live "Always Listening" pill.
- **Edge lighting rebuilt**: a colour wave now runs along the *whole* screen border (all four edges and the rounded corners), colours keep changing and speed up while listening/thinking/speaking.
- **New abilities** (inspired by the feature list you sent, implemented from scratch): SOS with location, answer/end calls, find a contact's number, copy to clipboard, storage analyser, phone-health check, save/find parking, Driving/Sleep/Work/Game modes (Do Not Disturb), read OTP on demand, missed calls, clear notifications, reply to notifications, missions (plan → cancel window → run, with pause/resume/cancel), deep research, website builder (saved to Downloads), game coach, GitHub repo list, triggers (charger, headphones, screen, battery, notification, daily time), learned habits shown as suggestions, History screen, OpenAI-compatible + Claude chat brains.
- Build summary: the GitHub workflow now prints compile errors on the run's summary page and uploads `build.log`, so a failed build can be fixed from one copy-paste.

## What's new in 0.4.0
- **Keeps running in the background.** The Gemini conversation and "Hey Munawar" now live in a foreground microphone service (`AssistantService`). Minimise the app, open YouTube, or swipe the app away: it keeps listening (notification with Mute / End call / Stop all). If Gemini drops it reconnects by itself, forever, until you stop it.
- **Cancel window for calls and messages.** "Call mom" first shows the number (and for SMS/WhatsApp the message) with a Cancel button. If you don't cancel within N seconds (Settings, default 5) it goes ahead. Works over other apps via an overlay card (needs "Display over other apps"), otherwise via a notification with a Cancel action; if neither can be shown, it does nothing instead of acting blindly.
- **Screen-edge lighting.** A fast, colourful light runs around the whole screen while Munawar is on, matched to your phone's rounded corners. It shows over every app when overlay is allowed. Thickness/speed/on-off in Settings.
- **Only Gemini speaks.** No greeting or local voice before Gemini connects; no spoken app prompts.
- **Gemini 3.5 Flash is preselected** on the first-run page (`gemini-3.5-flash`; Google currently routes it to 3.6 Flash). If your key doesn't offer it, setup silently picks the newest Flash model your key supports. Change it any time in Settings.
- Gemini 3.x fixes: temperature omitted for Gemini 3 models, function-call ids are echoed back, permission prompts blocked by Android now offer an "Open settings" shortcut.
- Floating orb tap now starts/stops Gemini directly (no need to open the app).

### Background limits Android enforces
- Android 10+ blocks apps from opening other apps while in the background **unless** "Display over other apps" is allowed. Without it, Munawar posts a "tap to continue" notification instead.
- Some brands (Xiaomi, Oppo, Vivo, Samsung, Huawei) kill background apps: set Munawar's battery to "No restrictions" and allow Autostart.
- After a phone reboot, open Munawar once (Android 14 forbids microphone services from starting at boot).

## What's new in 0.3.0
- Animated colour-shifting UI (aurora background, rainbow title, glowing orb) with a ☰ menu; Home = talk screen.
- Tap the orb → connects to Gemini Live and keeps listening hands-free. Gemini calls a `phone_action` tool, so any language/phrasing can open apps, play songs, send WhatsApp/SMS, call, etc.
- First-run setup page for the Gemini key and permissions.
- Hindi (Devanagari) speech fixed: "व्हाट्सएप खोलो", "यूट्यूब पे गाना चलाओ" now work.
- Floating orb is animated and its size is a setting. Image generation, notes/tasks, reminders, media keys, flashlight, volume, briefing.
- **Lag-free**: single-pass drawing, 30 fps background, cached shaders, animations pause when the app is hidden, and Settings → Lite animations for old phones.

### Permissions made easy
☰ → Control Center → **✨ Grant all permissions** asks for microphone, notifications, contacts, phone, SMS and location one by one, then lists the special Android screens still to open (overlay, Accessibility, notification access, battery). If Android has permanently blocked a permission, Munawar shows an "Open settings" shortcut.

### Doing things "hands-free" (what Android allows)
| Action | Needs |
|---|---|
| Open any app, URL, maps, alarm, flashlight, volume, music keys | nothing extra |
| WhatsApp message **sent automatically** | Accessibility ON (taps WhatsApp's Send) + a contact/number |
| YouTube song **plays automatically** | Accessibility ON (taps the first result); without it, YouTube Music plays directly if installed, else the results page opens |
| SMS / calls without confirm dialogs | Settings → Hands-free ON + SMS / Phone permission |
| Say "Hey Munawar" with screen off | ☰ → Control: wake word ON, battery Unrestricted, overlay allowed |
Contact names need Contacts permission; WhatsApp numbers without a country code use Settings → country code (default 91).

## Build on GitHub (easiest, no Termux needed)
1. Create a GitHub repo, upload everything in this folder (keep `.github/workflows/build.yml`).
2. **Actions** tab → "Build Munawar AI APK" runs on every push (or press **Run workflow**).
3. When green: open the run → **Artifacts** → `MunawarAI-apk` → unzip → install `MunawarAI.apk`.

### Releases + in-app updates
- Create a tag like `v0.2.1` (GitHub → Releases → Draft new release → new tag → Publish). The workflow builds and attaches `MunawarAI.apk`.
- In the app: Settings → Updates → repo `yourname/MunawarAI`; Control → "Check for updates".
- **Updates only install over the old app if every build is signed with the same key.** Without a key, GitHub signs with a different debug key each run, so you must uninstall first. To fix, create one key (Termux):
  ```bash
  pkg install openjdk-17
  keytool -genkeypair -v -keystore munawar.jks -alias munawar -keyalg RSA -keysize 2048 -validity 10000
  base64 -w0 munawar.jks      # copy the output
  ```
  GitHub repo → Settings → Secrets and variables → Actions → add: `KEYSTORE_BASE64` (the output), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (munawar), `KEY_PASSWORD`. Keep `munawar.jks` safe forever.

## Build in Termux (alternative)
```bash
pkg install -y openjdk-17 gradle aapt2 unzip wget
# Android SDK (platform 34 + build-tools 34.0.0 via cmdline-tools), then:
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle wrapper --gradle-version 8.9
./gradlew assembleRelease -Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2
# APK: app/build/outputs/apk/release/app-release.apk
```
JDK 17, Gradle 8.9, AGP 8.5.2, Kotlin 1.9.24, compileSdk/targetSdk 34, **minSdk 29 (Android 10+)**.

## First run
Settings → Gemini key → Save → "Load models" → pick one → Test connection. Then Control tab for wake word, floating orb, accessibility, notifications.

## Permissions (asked only when needed)
| Permission | Used for |
|---|---|
| Microphone | calls, voice typing, wake word |
| Notifications (Android 13+) | the required "listening" / "floating orb" notification |
| Contacts | call/SMS by name |
| Phone (CALL_PHONE) | direct call after you tap "Call now" (else dialer opens) |
| SMS | send after you tap "Send now" (result confirmed by Android) |
| Location | "where am I" |
| Display over other apps | floating orb; lets wake word / multi-step open apps from background |
| Battery: Unrestricted | keeps background listening alive |
| Accessibility (manual) | tap/type/scroll/back/home/read/explain screen (screenshot on Android 11+) |
| Notification access (manual) | "read notifications" (memory only) |
Camera/files use Android's own camera app and file picker, so no camera/storage permission is declared.
On Android 13+ apps installed outside Play Store: Settings → Apps → Munawar AI → ⋮ → **Allow restricted settings**, then enable Accessibility / Notification access.

## Wake word ("Hey Munawar")
Android has no free always-on keyword API. The app loops SpeechRecognizer and watches for the name. Needs internet on most phones, uses battery,
may beep on some phones, and pauses during calls/while she speaks. Foreground notification (with Stop) is required by Android.
Samsung/Xiaomi/Oppo/Vivo: also allow Autostart and "no battery restrictions" in the phone's own settings.

## Gemini Live
Uses the documented WebSocket protocol (BidiGenerateContent). The default model name is a setting: Settings → "Load Live models" lists what your key supports.
If Live can't connect the call falls back to classic mode and shows the reason.

## Security
API keys (Gemini, ElevenLabs, Home Assistant) are encrypted with the Android Keystore, sent only to their own service, never logged or hardcoded.
Never embed keys in an APK. Cleartext HTTP is allowed only so you can reach a Home Assistant server on your local network.

## Known limits
No on-device AI (offline = time/date/battery + phone commands). No light theme. Android never allows silent app self-update, calls or SMS without confirmation.

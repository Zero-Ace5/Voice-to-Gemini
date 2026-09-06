# Voice to Gemini ✦

[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![Android SDK](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Model](https://img.shields.io/badge/Model-Gemini%203.5%20Flash%20Lite-blue.svg)](https://ai.google.dev)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](https://kotlinlang.org)

> **Battery-efficient, open-source Android speech post-processor.**  
> Speak freely into Gboard voice typing with natural pauses. Tap the floating Star ✦ to clean disfluencies ("um", "uh", "you know"), fix recognition typos, and format text in-place using Google's **Gemini 3.5 Flash Lite**.

> [!IMPORTANT]  
> **Google Gemini API Key Required:** This app connects directly to Google Gemini and requires your own API key. You can generate a free key in seconds at **[Google AI Studio](https://aistudio.google.com/app/apikey)**. Paste it once into the app setup screen to activate.

---

## ✦ Why This Exists

Voice typing on mobile often produces raw, run-on text loaded with verbal fillers ("um", "uh", "like", "basically") and punctuation errors. Other AI voice utilities run continuous background audio recording loops that drain your battery and compromise privacy.

**Voice to Gemini** takes a cleaner approach:
1. You use your phone's native Gboard Google voice typing at your own pace.
2. A tiny, pure floating Star ✦ appears right above your keyboard while typing.
3. Tap the Star — the Android Accessibility Service reads the active text, sends it directly to Gemini Flash Lite via fast REST call, and replaces it in-place with clean, punctuated prose.
4. **Zero background battery drain** when idle.

---

## ✦ Features

- **Pure Floating Star ✦**: Clean icon with zero background or clutter.
- **Hold & Drag to Reposition**: Touch and hold the Star to move it anywhere on screen. Position is permanently remembered across sessions.
- **Smart Auto-Show / Auto-Hide**: Appears when the keyboard is open or a text field is active; completely vanishes when the keyboard closes.
- **In-Place Transformation**: No jumping or resizing. The Star smoothly transforms into a spinner while processing, then a checkmark upon completion.
- **Powered by Gemini Flash Lite**: Locked to `gemini-3.5-flash-lite` (primary) with automatic fallback to `gemini-3.1-flash-lite`.
- **Zero Intermediary Servers**: Direct connection from your device to Google's official Gemini API endpoint using your own API key.
- **Direct Voice Dictation (Optional)**: Can also use native `SpeechRecognizer` directly without Gboard.

---

## ✦ How It Works

```
[User speaks into Gboard voice typing with pauses & fillers]
                         │
                         ▼
        Raw text appears in any text field
                         │
              [User taps floating Star ✦]
                         │
                         ▼
 Accessibility Service grabs focused input text (ACTION_SET_TEXT)
                         │
                         ▼
      Google Gemini 3.5 Flash Lite (Fast REST API)
       • Strips vocal disfluencies ("um", "like", "uh")
       • Fixes punctuation & speech typos
       • Preserves original meaning and tone
                         │
                         ▼
Cleaned text replaced in-place + copied to clipboard as backup
```

---

## ✦ Download Pre-built APK

Go to the [**Releases**](../../releases) tab to download the latest `app-debug.apk`.

1. Download the `.apk` file to your Android phone.
2. Open the file and allow **"Install from unknown sources"** when prompted.
3. Open **Voice to Gemini**, paste your Google Gemini API key, and grant the required permissions (Accessibility & Display Over Other Apps).

---

## ✦ How to Build From Source

### Prerequisites
- **JDK 17** or higher
- **Android SDK** (API Level 35, Build-tools 34.0.0+)
- **Git**

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/voice-to-gemini.git
cd voice-to-gemini
```

### 2. Configure SDK Location
Create a `local.properties` file in the project root:
```properties
# Windows example:
sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk

# macOS/Linux example:
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
```

### 3. Build Debug APK
- **Windows (PowerShell):**
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **macOS / Linux:**
  ```bash
  chmod +x gradlew
  ./gradlew assembleDebug
  ```

Output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install onto Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ✦ Changing Gemini Models (Rebuilding)

By default, the app uses **Gemini 3.5 Flash Lite** as primary, with automatic fallback to **Gemini 3.1 Flash Lite** if the primary model is offline or returns an error.

If you want to use different models (e.g. `gemini-2.5-flash`, `gemini-pro`, etc.), edit [`app/src/main/java/com/poc/voicetogemini/GeminiClient.kt`](app/src/main/java/com/poc/voicetogemini/GeminiClient.kt):

```kotlin
companion object {
    // Modify these constants to change models:
    const val MODEL_PRIMARY = "gemini-3.5-flash-lite"
    const val MODEL_FALLBACK = "gemini-3.1-flash-lite"
}
```

Then rebuild:
```bash
./gradlew assembleDebug
```

---

## ✦ How to Serve APKs to Users (GitHub Releases)

This repository includes a pre-configured GitHub Actions CI/CD workflow (`.github/workflows/release.yml`).

### Automatic Release via Git Tags:
Whenever you push a git tag (e.g., `v1.0.0`), GitHub Actions will automatically compile the APK and attach it directly to a new GitHub Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Manual Release:
1. Go to your GitHub repository -> **Releases** -> **Draft a new release**.
2. Create a tag (e.g. `v1.0.0`).
3. Drag & drop `app-debug.apk` into the release binary section.
4. Click **Publish release**. Users can now download it directly!

---

## ✦ Recommended GitHub Topics / Search Tags

Add these topics in your GitHub repository settings (under the **About** gear icon) to maximize discoverability:

```
android, gemini, gemini-api, voice-typing, speech-to-text, gboard, accessibility-service,
ai, productivity, open-source, kotlin, flash-lite, speech-recognition, text-editor, android-app
```

---

## ✦ Permissions Explained

| Permission | Why It Is Required |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Required to display the floating Star ✦ above other applications. |
| `BIND_ACCESSIBILITY_SERVICE` | Required to detect active text fields and replace text in-place (`ACTION_SET_TEXT`). |
| `RECORD_AUDIO` | *(Optional)* Used only if you choose to record directly via Google's native speech recognizer. |
| `INTERNET` | Required to send text to Google's Gemini API. |

---

## ✦ Privacy

- **No audio is sent to third-party servers**: Audio transcription is handled on-device by Google Speech Services / Gboard.
- **Your API key never leaves your device**: Stored locally in Android private `SharedPreferences`.
- **Direct connection**: Only the raw text from the active field is sent directly to `generativelanguage.googleapis.com` upon tapping the Star.

---

## ✦ License

This project is open-source and licensed under the [MIT License](LICENSE).

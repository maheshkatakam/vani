# Vani

**Hold the power button, speak in any language, and get polished, professional text typed into whatever app you're in.**

Vani (వాణి / वाणी, "voice") is a personal Android take on [VoiceInk](https://github.com/beingpax/voiceink) for Mac. You talk the way you normally talk, in English, Telugu, Hindi or a mix, with "um"s and mid-sentence corrections. Vani hears it, rewrites it in the style you want (Professional, Email, Message, Formal, Concise, Diplomatic, or your own), translates it if you like, and puts it where your cursor is.

## Install or update

1. On your phone, open this repo's **Releases** page and download the latest `Vani-1.0.N.apk`.
2. Open the file and allow installing from your browser if Android asks.
3. Updates install over the old version and keep your settings and keys.

Every push to `main` builds a new APK (`.github/workflows/build.yml`).

## Set up (Pixel, about 3 minutes)

Open Vani; the Setup card walks you through these and ticks them off.

1. **Microphone**: tap Allow.
2. **AI key**: get a free Gemini key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey), paste it in Settings → AI engine, tap **Test**. (OpenAI and Groq keys work too.)
3. **Make Vani your digital assistant**: Settings → Apps → Default apps → Digital assistant app → Default digital assistant app → **Vani**.
4. **Hold power → Vani**: Settings → System → Gestures → Press & hold power button → **Digital assistant**.
   Pixel's double-tap power only offers camera or wallet, so Vani uses press-and-hold instead.
5. **Type into any app + Key mapper** (optional but recommended): Settings → Accessibility → Vani helper → on.
   If the switch is greyed out ("restricted setting"), go to Settings → Apps → Vani → ⋮ (top right) → **Allow restricted settings**, then turn it on. Android does this for every app installed outside the Play Store.

> If you ever reinstall Vani (not a normal update), Android may reset the digital assistant. Just pick Vani again in step 3.

## Using it

- **Dictate**: tap into a text box in any app, hold power, speak. Vani stops when you pause (or tap Done, press a volume key, or hold power again). Check the preview, then tap **Insert**.
- **Switch style or language after the fact**: tap a style chip (💼 ✉️ 💬 …) or English / As spoken / Other on the result. It rewrites the same words instantly; no need to speak again.
- **Polish what I typed**: if the text box already has text, tap *Polish what I typed* on the sheet. Select part of it first to polish just that part.
- **Voice edit**: with text in the box, turn on *Voice edit* and say what to change: "make it more polite", "shorter", "translate to Hindi".
- **Hold-to-talk**: in Buttons & Key mapper, map *Hold Volume down*. Hold it, speak, let go; like a walkie-talkie. Short presses still change the volume.
- **Without the accessibility helper**: the result is copied; long-press the text box and paste.
- **Text-selection menu**: select text in any app → ⋮ → **Polish with Vani** rewrites it in place.
- **Quick Settings tile** and **home-screen shortcuts** (long-press the icon): Dictate, Polish clipboard.

### Smart per-app styles

Gmail and Outlook default to ✉️ Email; WhatsApp, Telegram, Messages, Slack, Teams and Instagram to 💬 Message; LinkedIn to 💼 Professional. Change these in Settings → Style per app.

### Regional languages

- **Write in → English** (default): speak Telugu, Hindi, Tamil, Hinglish… and get natural professional English.
- **Write in → Same language I speak**: polished text in the language you spoke, in its own script (తెలుగు, हिंदी) or in Roman letters (Settings → Indian languages are written in).
- Add the languages you speak in Settings → Languages you speak; it helps speech recognition.

### Speed: picking the Gemini model

Settings → AI engine → Gemini model:

- **⚡ Fast (recommended)**: Gemini 3.6 Flash with minimal thinking. Quick and well written.
- **🧠 Smartest**: Gemini 3.8 Flash. Best for tricky rewrites, but it can't turn its thinking down as far, so it's a few seconds slower.
- **🚀 Fastest**: Gemini 3.5 Flash-Lite. Near-instant, simpler wording.

While Vani works, the sheet shows the upload, what it heard, and the text as it's being written. If something is slow or failing, Settings → Diagnostics lists each request's model, size and timings; tap copy to share it.

### Vocabulary and replacements

Add names and terms (your name, university, products) so they're always spelled right. Replacements turn phrases into text, e.g. "my email" → your address.

## How it works

| Piece | What it does |
|---|---|
| `dictation/DictationActivity` | Handles `android.intent.action.ASSIST`, which is what makes Vani selectable as the digital assistant and launchable by holding power. Shows the bottom sheet over the current app. |
| `audio/VoiceRecorder` | 16 kHz mono recording with a small voice-activity detector: live waveform, stop on pause, trims silence before upload. |
| `ai/VaniEngine` | Gemini fast path: one *streamed* request with the audio returns the transcript, the language and the polished text, which appears on screen as it's written. Other providers: speech-to-text then rewrite. Falls back to another provider if one fails, finds a replacement model if a model name disappears, and retries on Flash-Lite if a model goes quiet for 40 s. |
| `audio/AudioEncoder` | Compresses the recording to AAC (.m4a, about 6x smaller than WAV) before upload; falls back to WAV if a provider won't take it. |
| `ai/Prompts` | The rules (never answer the dictation, keep facts, handle self-corrections and spoken punctuation), the style and the language instructions. |
| `access/VaniAccessibilityService` | Reads the focused text box when you start Vani, types the result back (paste at the cursor, or set text), and runs the Key mapper. |
| `access/KeyGestureEngine` | Hold / double-press / single-press detection with volume passthrough. |

Your voice goes only to the provider you chose, only while you dictate. API keys are encrypted with the Android Keystore. History stays on the phone.

## Signing

Release APKs are signed with `signing/vani-release.p12`. Its password lives only in the repo secret `VANI_KEYSTORE_PASSWORD`. Without that secret the build still runs, but it doesn't publish a release (a differently signed APK can't update the installed app).

## Tech

Kotlin, Jetpack Compose (Material 3, dynamic color), OkHttp, coroutines. Min Android 10 (API 29), target API 35. Unit tests cover text insertion, key gestures, prompts, cleanup, settings and audio helpers.

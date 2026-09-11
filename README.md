# Lumen Keyboard

A custom Android keyboard (IME) built natively in **Kotlin** — no cross-platform
wrapper, no third-party keyboard SDK. It draws its own keys on a `Canvas` for
full control over the look and feel, the way Gboard/SwiftKey do it.

> **Why Kotlin?** It's the officially recommended, modern language for native
> Android — full Java interop, null-safety, and far less boilerplate than
> Java for a UI-heavy project like a keyboard. If you'd rather build a
> cross-platform (iOS + Android) keyboard from one codebase, Flutter/Dart is
> the usual alternative, but Android IMEs need real native `InputMethodService`
> hooks, so Kotlin is the right tool here.

## Features

- **Custom-drawn keyboard** — no deprecated `KeyboardView`/XML `Keyboard` API;
  a hand-built `View` with smooth press states and Material-style rounded keys
- **Light / Dark / AMOLED black / Follow-system** themes with a configurable accent color
- **Long-press accents & numbers** — e.g. long-press `e` → `3 è é ê ë`; drag to pick, release to insert
- **Smart Shift** — tap for one capital, double-tap for Caps Lock
- **Adaptive Enter key** — automatically shows "Go / Search / Send / Next / Done" based on the field
- **Auto-repeating Backspace** on long-press
- **Word suggestion bar** — lightweight, fully offline prefix-based suggestions
- **Emoji panel** — quick-access grid, no network or extra permissions
- **Symbols & extended-symbols pages** (`?123` / `=\<`)
- **Key sound + haptic feedback**, each independently toggleable
- **Settings screen** for all of the above, shared instantly with the running keyboard

## Project structure

```
LumenKeyboard/
├── app/src/main/java/com/lumen/keyboard/
│   ├── LumenInputMethodService.kt   # the IME itself — state machine & InputConnection calls
│   ├── KeyboardView.kt              # custom View: drawing + touch handling
│   ├── KeyboardTheme.kt             # theme colors per mode
│   ├── PreferencesManager.kt        # typed SharedPreferences wrapper
│   ├── SoundFeedback.kt             # click sound + vibration
│   ├── SuggestionBar.kt             # suggestion strip view
│   ├── WordDictionary.kt            # offline word list for suggestions
│   ├── EmojiPanelView.kt            # emoji grid
│   ├── MainActivity.kt              # "enable keyboard" onboarding screen
│   ├── SettingsActivity.kt          # preferences screen
│   └── model/
│       ├── Key.kt                   # KeyDef / KeyRow / KeyboardLayout data classes
│       └── Layouts.kt               # QWERTY + two symbol-page layouts
├── app/src/main/res/                # strings, colors, themes, XML layouts, adaptive icon
├── .github/workflows/android-build.yml  # CI: builds a debug APK on every push
└── build.gradle.kts / settings.gradle.kts / app/build.gradle.kts
```

## Build it

### Option A — GitHub Actions (recommended, matches how you said you build)
1. Push this whole folder to a new GitHub repo.
2. The included workflow (`.github/workflows/android-build.yml`) runs automatically
   on every push to `main` — it sets up JDK 17 + the Android SDK, generates the
   Gradle wrapper, and runs `./gradlew assembleDebug`.
3. Open the **Actions** tab → the latest run → download the `lumen-keyboard-debug-apk`
   artifact. That's your installable APK.
4. To build a signed **release** APK instead, add a signing config to
   `app/build.gradle.kts` and a `assembleRelease` step (kept to `assembleDebug`
   by default so it builds successfully with zero secrets configured).

### Option B — Android Studio
1. Open the `LumenKeyboard` folder as a project (Android Studio will generate
   the Gradle wrapper for you automatically).
2. Run on a device/emulator, or **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

Minimum supported Android version: **8.0 (API 26)**. Target/compile SDK: **34**.

## Installing & enabling it on a phone

1. Install the APK.
2. Open the app → **"Enable in System Settings"** → turn on *Lumen Keyboard*.
3. Tap **"Switch to Lumen Keyboard"** (or long-press the space bar / tap the
   keyboard-switch icon in any text field) to make it your active keyboard.
4. **"Customize Appearance"** opens the in-app settings — the same screen is
   also reachable from the system keyboard picker.

## Renaming / rebranding

"Lumen" was picked for the "clean, glowing, professional" feel — but it's a
placeholder if you want something else. Two quick alternatives if you'd like
options: **Nimbus Keyboard** (soft, cloud-like) or **Cadence Keyboard**
(emphasizing typing rhythm/flow). To rename:

1. Find-and-replace `Lumen Keyboard` → your name in `strings.xml`.
2. Find-and-replace the package `com.lumen.keyboard` → your own applicationId
   (rename the `com/lumen/keyboard` folder to match, and update `namespace`/
   `applicationId` in `app/build.gradle.kts`).
3. Swap the accent color in `colors.xml` / `PreferencesManager.DEFAULT_ACCENT`.

## Extending it

- **New layout/language**: add a `KeyboardLayout` in `model/Layouts.kt`, then
  wire a switch-key or `LANGUAGE` action to call `keyboardView.setKeyboardLayout(...)`.
- **Real dictionary/next-word prediction**: replace `WordDictionary` with a
  frequency-ranked word list (or an on-device ML model) — `LumenInputMethodService`
  already calls `updateSuggestions()` on every keystroke, so the wiring is in place.
- **Swipe/gesture typing**: `KeyboardView.onTouchEvent` already tracks per-key
  bounds and touch paths; a swipe-typing engine would consume the same touch
  stream before falling back to per-tap key detection.
- **More themes**: add a case to `Themes.forMode(...)` in `KeyboardTheme.kt`.

## License

MIT — do whatever you'd like with it.

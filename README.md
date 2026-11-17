# PadBoard for Android

[Download the latest APK](https://github.com/page-muncher/PadBoard-Android/releases/latest) · [Release v1.0.7](https://github.com/page-muncher/PadBoard-Android/releases/tag/v1.0.7)

PadBoard is a controller-operated Android system keyboard for the Retroid Pocket G2, with the Windows QWERTY and Symbols layouts adapted for a 1920 × 1080 landscape display. It installs separately from VitaPad.

## Install and enable

1. Copy `PadBoard-1.0.7.apk` to the G2 and open it in the device’s file manager. Install it over the previous PadBoard release to keep your settings. If Android prompts for permission, allow that file manager to install unknown apps and reopen the APK.
2. Open **PadBoard** from the launcher. Tap **Enable PadBoard**, switch on **PadBoard** in Android’s keyboard settings, then return to the app.
3. Tap **Select keyboard** and choose **PadBoard**. Tap the test field or a field in another app.
4. For the controller, open **Controller settings** in PadBoard. Under Accessibility, open **PadBoard Controller** and turn it on. If Android labels the switch a restricted setting, open **Settings → Apps → PadBoard → App info** and select **Allow restricted settings** from the three-dot menu, if offered. Return to Accessibility to enable the service. [Android restricted settings help](https://support.google.com/android/answer/12623953)

With USB debugging enabled, you can also sideload from a PC using `adb install -r PadBoard-1.0.7.apk`.

If Android detects a physical keyboard and hides the touch keyboard, turn on **Use on-screen keyboard** under **Settings → System → Keyboard → Physical keyboard**. [Android physical keyboard settings](https://support.google.com/android/answer/14114879)

The controller service requests key filtering and joystick input. PadBoard saves panel geometry and the face-button mapping locally.

## Controller

| Control | Action |
| --- | --- |
| D-pad | Move the highlight |
| Cross / Android A | Type the highlighted key |
| Circle / Android B | Shift on QWERTY or alternate keys on Symbols |
| Square / Android X | Backspace |
| Triangle / Android Y | Space |
| L1 | Switch QWERTY and Symbols |
| L2 / R2 | Jump to the left / right anchor |
| R1 + D-pad | Move the caret |
| R1 + Square | Ctrl+Backspace (delete the previous word in supported editors) |
| R1 + L1 + D-pad | Extend a selection |
| R1 + Cross | Enter or the editor’s action |
| R1 + Triangle | Copy |
| R1 + L1 + Triangle | Paste |
| R1 + Circle | Hide PadBoard |
| R3 | Show or hide PadBoard with a focused Android text field |
| Right stick | Move the panel |
| Left stick up / down | Enlarge / shrink in 5% steps |
| Left stick left / right | Reduce / increase opacity in 5% steps |
| L3 | Reset position, scale and opacity |

If the G2 reports the face buttons in Nintendo order, turn on **Swap A/B and X/Y** in PadBoard and refocus the text field. The names above refer to the physical positions of the corresponding PlayStation buttons.

Tap Circle to arm Shift or the alternate layer for one emitted key. Hold Circle to apply it during the hold. Double-tap within 300 ms to lock it, then tap once to unlock. Activate the visible Shift, Ctrl and Alt keys with Cross to arm or lock them. One dot marks an active modifier and two mark a lock. Circle on QWERTY and the visible Shift on Symbols share the same latch.

One-shot Shift, Ctrl, Alt and the Symbols alternate layer stay armed until you type or cancel them. After one second, activate the same modifier again to cancel it. Circle cancels one-shot Shift on QWERTY and the one-shot alternate layer on Symbols. A tap between 300 ms and one second keeps the modifier armed and starts a fresh double-tap window. Circle plays the same click as the modifier keys.

An emitted key consumes all one-shots, including Backspace, Space, Enter and caret arrows. Moving the highlight, changing layouts and arming another modifier preserve the one-shots. Closing PadBoard clears the modifiers. Alt is directly to the right of Ctrl on both layouts.

On Symbols, **Num Lock** switches the right bank between digits and navigation. Circle reveals F1–F12 and temporarily shows the nav faces on the numpad.

**All**, immediately to the right of **Paste** on Symbols, selects all text in the focused field.

## Touch and floating behavior

Tap the floating key grid to reveal the two touch controls. The bottom-left chevron hides PadBoard and the bottom-right keyboard icon opens Android’s keyboard picker. The buttons start hidden each time PadBoard opens. The grid absorbs touches while typing uses the controller. Touches outside the grid and visible controls go to the app underneath.

On Android 15 and later, PadBoard asks Android to hide the keyboard window’s extra pair of controls.

The controller can move the panel flush to all four edges of Android’s available keyboard area. Android reserves its system bars. PadBoard remembers the panel’s position, size and opacity. The panel scale range is 60–115% and its opacity range is 25–100%. The controller-highlighted key stays opaque.

R3 relies on Android delivering the button and allowing its selected IME to show for the active editor. The optional Accessibility service provides controller routing on supported devices and enables analog controls on Android 14 and later. Retroid button modes, remappers and other Accessibility services can affect delivery. Text fields that use their own keyboard or block third-party IMEs can limit PadBoard. Ctrl/Alt shortcuts, function keys and some navigation keys depend on how the receiving app handles Android key events. [Android IME API](https://developer.android.com/reference/android/inputmethodservice/InputMethodService) · [Android controller-event API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent))

## Build from source

The app targets Android 15 (API 35) and installs on Android 9 or later. To build on Windows, use JDK 17, SDK Platform 36 and Android Build Tools 35.0.0:

```powershell
.\build.ps1 -Sdk 'C:\Android\Sdk'
```

The APK is written to `build\PadBoard-1.0.7.apk`. A local signing key is created if one is absent. Keep it for future updates of your own builds. An APK signed with a new key requires uninstalling the existing build from the device before installing it. The distributed APK has its own signing key.

## Validation

Version 1.0.7 was verified in a 1920 × 1080 Android 15 emulator with a virtual gamepad and a separate editor app. The checks covered Ctrl+Backspace word deletion, caret movement, text selection, copy and paste, regular Backspace repeat, and switching layouts. Physical Retroid Pocket G2 testing remains pending.

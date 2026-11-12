# Task 06 physical-device and accessibility checks

No physical device has been verified in this task. Use a representative ARM64 16 KB
phone, an ordinary 4 KB phone, and a constrained-memory phone before release.

## Connect and identify
1. Enable Developer options and USB debugging; unlock the device and accept its RSA prompt.
2. Run `adb devices -l`. Select the physical serial explicitly for every command below.
3. Run `adb -s SERIAL shell getprop ro.product.cpu.abilist` (must include arm64-v8a),
   `adb -s SERIAL shell getprop ro.kernel.qemu` (physical device should not report 1),
   and `adb -s SERIAL shell getconf PAGE_SIZE`. Record model, API, refresh rate and page size.

## Install and exercise the ordinary app
1. `./gradlew :app:assembleDebug`
2. `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb -s SERIAL shell am start -n ge.gogichaishvili.backgammon/.MainActivity`
4. Start a disposable local match via New game / Start game. Roll opening dice, including ties.
   Select a checker and a legal destination with taps and with the equivalent buttons.
   Verify that used dice, four uses of doubles, turn ownership and End turn match the engine.
5. Exercise a hit, mandatory bar entry, bearing off, ambiguous die choice and the victory dialog.
   For reproducible fixtures, run the existing GameInteractionTest instrumentation cases on
   a disposable test installation; do not edit persisted match files.
6. Switch Classic / Royal / Ocean while selected and while moving. Rotate portrait/landscape
   during selection and animation. Check for duplicates, missing checkers, stale palettes,
   incorrect stacking or a stuck busy state. Rapid taps must not repeat a committed move.
7. Leave an exact mid-turn state with a die consumed. Force-stop using
   `adb -s SERIAL shell am force-stop ge.gogichaishvili.backgammon`, relaunch with step 3,
   and compare every board/bar/off count, dice, remaining uses and current player.
8. Enable sound/haptics, then disable each independently. Check dice, pickup, placement,
   hit, bearing-off and victory. Test media mute, silent mode, background/foreground,
   headphones and a device without a vibrator. No queued cue should replay on return.

## Profile the isolated non-debuggable target
These fixtures reset only the separate .profile package, not the ordinary app.
1. `./gradlew -PprofileTests=true :app:assembleProfile :app:assembleProfileAndroidTest`
2. Install both APKs using the explicit physical serial:
   `adb -s SERIAL install -r -t app/build/outputs/apk/profile/app-profile.apk`
   `adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/profile/app-profile-androidTest.apk`
3. `./gradlew -PprofileTests=true -I tools/game-profile.init.gradle.kts :app:captureGameProfile --no-configuration-cache -PprofileSerial=SERIAL -PprofileLabel=physical_run1`
4. Repeat at least three times under comparable thermal/power conditions, without Studio
   debugger, screen recording, UI inspector or unrelated workloads. Inspect FrameTimeline,
   main-thread scheduling/CPU, redraw sections, ART allocation deltas and HWUI/RSS counters.
   Keep sound off for paired rendering measurements, then separately measure audio-enabled startup.
   Check that all BG.Stage markers close with positive durations. For a slow run add
   -PprofileConfig=verification/task06/gameplay.pftxt to use the preserved 300-second config.
   Recorder wait derives from the configuration duration plus transport margin.
   Rerun with a new label; do not overwrite earlier evidence.
5. Inspect memory after repeated skin switches, rotations and a settling period. A cache
   high-water mark alone is not a leak; test retention and low-memory behavior separately.

## Accessibility
1. Install/enable TalkBack using Android Accessibility settings. Confirm focus order,
   spoken point/checker counts, selected state, source/destination action labels, consumed dice,
   current-player announcements and the equivalent large buttons. Complete a move and dialog
   using TalkBack double-tap actions, not a sighted single tap.
2. Test keyboard/D-pad traversal and activation; ensure dialogs retain/dismiss focus correctly.
3. At system font size 200% and the largest display size, repeat both languages, portrait
   and landscape. Scroll to every action and settings control; labels must remain readable.
4. Enable Reduced motion, and separately set Developer options Animator duration scale to 0.
   Verify final engine state without flights, spinning dice or navigation/theme fades.
   Restore the original system settings after testing.
5. Inspect all skins in sunlight/dim conditions. Legal moves retain gold/silver color plus
   a contrasting dark outline and explicit textual alternatives; color is not the sole cue.
6. Have a Georgian reader review pronunciation, grammar and wrapping. Resource parity and
   semantic tests do not establish translation quality or actual screen-reader speech.

Record serial/model/API/page size, APK revision, commands, exact results, screenshots,
frame distributions and any failures. Do not count these instructions as completed testing.

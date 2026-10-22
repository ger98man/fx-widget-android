# fx-widget-android
Home-screen widget (Kotlin) converting currencies, e.g. THB → EUR.

- Tap-keypad directly on the widget; tap the currency chips to cycle, ⇄ to swap.
- Rates come from open.er-api.com and are cached; offline the last cached rates are used (status line shows age; tap it to refresh).
- Build: `./gradlew assembleDebug`; tests: `./gradlew testDebugUnitTest`.

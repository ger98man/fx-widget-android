# fx-widget-android
Currency converter for Android (Kotlin): an app plus two home-screen widgets, default THB → EUR.

- **FX: быстрые суммы** (4×2) — rate plus ready-made amounts (50 / 100 / 300 / 500 / 1000 ฿ → €). Tap a currency chip to cycle, ⇄ to swap, ⟳ to refresh, tap the amounts to open the app.
- **FX: клавиатура** (4×3) — compact keypad to type any amount right on the widget.
- **App** — full-screen converter with the system keyboard and quick-amount chips. Opening it refreshes rates in the foreground.

Rates: open.er-api.com, falling back to Frankfurter (ECB). Cached for offline use; WorkManager refreshes every 3 h when the network is available. If refresh fails the status line shows the reason.

Build: `gradle assembleDebug` (Gradle 8.9); tests: `gradle testDebugUnitTest`. GitHub Actions builds the APK on every push — download it from the run's Artifacts.

# KIT Android Alpha 0.1

Native Android/Jetpack Compose dogfood prototype corresponding to KIT mockup V2.

## Implemented
- Pure white/black UI; System/Light/Dark setting.
- Decks home showing deck backs only.
- Deck tap -> description + card count + Edit deck / Deal a card.
- Bottom tabs: Decks / Cards / Settings.
- Edit deck fork -> View & edit cards / Deck settings.
- Deck settings: title, description, image, cadence.
- No ordinary "in this deck" browse page.
- No claims about time since last real-world contact.
- Native cards collection.
- Up to 4 external contact actions per card.
- Contact action immediately records a KIT action and launches external app/URL.
- Deal card + chevron-expanded secondary controls.
- Consequential, reversible "Trash from this deck".
- Local persistence with SharedPreferences for this alpha.
- Local notification test via WorkManager.
- Device image picker for deck/card images.

## Intentionally alpha / next iterations
- Weighted draw is simplified to random selection in this first phone build.
- Persistence uses SharedPreferences, not final Room schema.
- Contact import, Share-to-KIT, camera capture, rich item previews, notification-window learning, encrypted backup/import, and robust deep-link handling are not yet complete.
- External X/Discord/Substack/Calendly/etc. are URL handoffs, not service integrations.

## Build
Requires Android SDK 36, JDK 17+, Gradle 8.13, Android Gradle Plugin 8.13.2.

In Android Studio: open this folder, let Gradle sync, then Run on a connected device.

CLI with Gradle installed:
```bash
gradle :app:assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Package
`com.kit.prototype` — intentionally a prototype package, not the final Play Store application ID.

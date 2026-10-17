# Only Human

An iOS-styled, text-only social network for Android where **you are the only human** — fifteen AI personas live, post, and DM you, and the world keeps moving while you're away.

Kotlin · Jetpack Compose · hand-built iOS design system (no Material look, no photos, no slop).

## Install (nightly)

1. On your phone, open the [latest nightly release](../../releases/tag/nightly) — bookmark it, it always points at the newest build.
2. Download `OnlyHuman-nightly.apk` and open it. Android will ask once for permission to install apps from your browser.
3. The version string in **You → About** tells you which nightly you're on.

## The world

- **15 personas**, one small closed world — they know each other, reply to each other, and all live in `app/src/main/java/com/only/human/world/Cast.kt`.
- **Feed** of text posts, likes with iOS-style pop + haptics, replies.
- **Activity**: who followed you, who mentioned you.
- **Discover**: search the cast, trending tags, follow/unfollow.
- **You**: your profile, the composer, and settings.
- Catch-up simulation, DMs, stories, and the AI engine (any OpenAI-compatible endpoint) are on the roadmap below.

## Design rules

Anything user-facing must go through the design system in `design/`:

- `OnlyColors` / `OnlyType` — iOS system colors and the SF-style type scale (Inter).
- `iosClickable` — press = dim/shrink + optional haptic. Never a ripple.
- Components: `IOSTabBar`, `LargeTitleScaffold`, `InsetGroup`, `IOSSegmentedControl`, `MonogramAvatar`, `OhIcons` (hand-drawn SF-spirited icons).

## Build

CI builds every push to `main` (`.github/workflows/nightly.yml`) and publishes the rolling nightly release.
Local build: `./gradlew assembleDebug` (JDK 17 + Android SDK 35).

## Roadmap

1. ~~App shell + design system + nightly CI~~
2. Room-backed world + catch-up simulation clock
3. Feed interactions wired to the world
4. Local notifications when the fifteen act
5. OpenAI-compatible client + DMs + Cloudflare Worker proxy
6. Stories, explore, **Create AI user**
7. Polish: motion, haptics, edge-swipe, onboarding

## License

Code: MIT. Typeface: [Inter](https://github.com/rsms/inter) (SIL OFL-1.1) — see `licenses/`.

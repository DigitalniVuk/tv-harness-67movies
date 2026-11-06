# TV Harness

A generic Android TV harness that turns any server-rendered website into a
remote-friendly TV app. The catalogue screen is native (no site scripts run);
the site is opened in a WebView only for the playback/player screen.

The harness ships with **no site baked in**. Every site-specific value
(endpoints, HTML selectors, player hooks, host allowlists) lives in one
file, `src/tv/harness/SiteConfig.java`. An agent points the app at a new
website by editing only that file, following `docs/AGENT_GUIDE.md`.

## Features

- Config-driven catalogue tabs (any listing pages) with native TV cards and
  remote navigation.
- Category browsing discovered from the site's own links.
- Poster loading on background workers with an in-memory bitmap cache.
- WebView player screen with native, focusable remote controls (pause,
  prev/next, episode/section picker, autoplay, reload, browse).
- Pop-up and ad request blocking using a small EasyList-derived domain list
  plus URL checks; known media file paths are always allowed through.
- Catalogue refresh that bypasses HTTP caches and clears the poster cache.
- Config-driven player page customisation (hide site chrome, centre the
  site's own player in 16:9).

## Requirements

- Android SDK with platform 25 and Android build tools installed.
- Java 21 (the build uses `javac --release 8`).
- A signing keystore for release builds. Do not commit or share the keystore.

See `docs/REQUIREMENTS.md` for what the target website must provide.

## Build

Provide your signing key through environment variables:

```sh
export HARNESS_KEYSTORE=/path/to/release.keystore
export HARNESS_KEYSTORE_PASSWORD='your-keystore-password'
export HARNESS_KEY_PASSWORD='your-key-password'
./build.sh
```

The APK is written to `build/harness-tv.apk`. The script uses `ANDROID_HOME`
or `ANDROID_SDK_ROOT` when set; otherwise it uses the local SDK path in the
script. It uses `JAVA_HOME` when set, otherwise the local JDK path there.

## Install on a Fire TV

Enable ADB debugging on the Fire TV, connect it to the same network, then:

```sh
adb connect FIRE_TV_IP:5555
adb install -r build/harness-tv.apk
```

The app package is `tv.harness`. Updating with the same signing key preserves
the installed app and its data.

## Project layout

- `src/tv/harness/SiteConfig.java` — **the only site-specific file.**
  Endpoints, selectors, player hooks, host allowlists. Edit this to plug in
  a website.
- `src/tv/harness/CatalogActivity.java` — native TV catalogue, remote
  navigation, poster loading. Site-agnostic; tabs come from `SiteConfig`.
- `src/tv/harness/CatalogScraper.java` — site-agnostic HTML fetcher/parser
  (selectors and endpoints come from `SiteConfig`).
- `src/tv/harness/CatalogItem.java`, `CatalogCategory.java` — data records.
- `src/tv/harness/WatchActivity.java` — WebView playback screen and native
  remote controls, driven by `SiteConfig` player selectors.
- `src/tv/harness/AdBlocker.java` — ad domain/URL checks; first-party hosts
  come from `SiteConfig`.
- `assets/blocking-domains.txt` — bundled domain rules (see `assets/NOTICE.txt`).
- `AndroidManifest.xml`, `res/` — app manifest and resources.
- `build.sh` — compiles, aligns, and signs the APK.
- `docs/HARNESS_PLAN.md` — conversion plan, phases, and status.
- `docs/AGENT_GUIDE.md` — step-by-step playbook for plugging in a website.
- `docs/API.md` — documented endpoints and internal contracts.
- `docs/REQUIREMENTS.md` — what a target website must provide.
- `docs/ARCHITECTURE.md`, `docs/BUILDING.md` — flow and build notes.
- `scripts/check-site-refs.sh` — verifies no site-specific code exists
  outside `SiteConfig.java`.

## Site dependency

The harness is only as good as the website it points at. Site layout changes
can break extraction or playback and will require updating `SiteConfig.java`
only. The harness is an independent client and is not affiliated with or
endorsed by any website it is configured for.

## Content and rights

This project contains the app source and ad-filter data. Artwork,
descriptions, video streams, and other site content are served by the
configured website and remain subject to their respective owners' rights
and terms.

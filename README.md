# TB

A small Android player for an M3U/M3U8 playlist hosted on GitHub.

> **Disclaimer:** TB is a playlist player. It hosts no channels, is not
> affiliated with any broadcaster, and provides no content of its own. See
> [DISCLAIMER.md](DISCLAIMER.md).

Open the app and it starts playing the **first channel** in the playlist. Tap
anywhere on the video (or press **OK** on a TV remote) and the **channel list**
slides in from the left. Pick another channel and it starts immediately.

Built with Kotlin, Jetpack Compose and AndroidX Media3 (ExoPlayer). The playlist
is fetched from a raw GitHub URL on every launch, so updating the channel list
is just a GitHub commit — no app update required.

## Download

Installable builds are attached to the
[v1.1.0 release](https://github.com/ZeeyKhm/TB/releases/latest) and also
committed in [`dist/`](dist):

- `TB-release.apk` — signed release build, minified and non-debuggable (recommended).
- `TB-debug.apk` — debug build for testing.

Both are version 1.1.0 (versionCode 2) but use different signing keys, so
uninstall any previous TB install before switching between them.


| Playing | Channel list |
| --- | --- |
| ![Player](docs/screenshots/player.png) | ![Channels](docs/screenshots/channels.png) |

## What it does

- **Launches straight into playback** — no home screen, no "press play".
- **Works with a TV remote**: **up/down** changes the volume, **left/right**
  changes the channel (wrapping inside the current TV or Radio section), and
  **OK** opens the channel list. While the list is open, up/down moves through
  it and OK picks a row; left/right still zaps channels and closes the list.
- **Tap the video** to toggle the channel list (a side panel in landscape).
- **TV and Radio are separate sections** — the channel list has TV/Radio tabs
  with counts, and zapping stays inside the section that is playing.
- **Hidden channels** — tap the **info** button three times and enter the PIN
  (`1234` by default) to load the extra Pluto TV, Whale TV Plus, Xiaomi and
  Vidaa TV lists. They are split into TV and Radio too; repeat the same
  sequence to hide them again.
- **Search** the list by channel name or group.
- **Refresh** re-downloads the playlist without restarting the app.
- **Low-latency live tuning** — a short start-up buffer, live-edge targeting and
  small speed nudges keep channels close to live, and fast decoder fallback
  avoids stalls on devices with picky hardware decoders.
- **HLS and DASH playback**, with **Widevine** licences and **ClearKey** keys
  read from `#KODIPROP` lines.
- **Locked to landscape** — the player never rotates into portrait.
- **Android TV ready** — leanback launcher entry and banner, `sensorLandscape`
  playback, no touchscreen required.
- Keeps the last good playlist on disk, so the app still works if GitHub is
  temporarily unreachable (shows an "Offline copy" badge) — and after the first
  launch it starts playing from that copy immediately while refreshing in the
  background.
- Remembers playback across rotation and pauses when the app goes to the
  background.
- Cleartext HTTP is allowed because many IPTV endpoints still use it.

## Repo layout

```
TB/
├── app/                     Android app (Kotlin + Compose + Media3)
├── tizen/                   Samsung Tizen TV app (HTML/CSS/JS)
├── dist/                    Ready-to-install APK, AAB and WGT builds
├── playlist/tb.m3u8         ← the playlist people edit on GitHub
├── .github/workflows/       builds a debug APK on every push
└── README.md
```

The app's default playlist URL is:

```
https://raw.githubusercontent.com/ZeeyKhm/TB/main/playlist/tb.m3u8
```

It is defined once in `app/build.gradle.kts` (`playlistUrl`). Change it there if
you rename the repo or move the file.

> If the `.m3u8` file is missing (for example during the rename from `.m3u`), the
> app automatically retries the same URL with the `.m3u` extension, so older
> copies of the playlist keep working.

## Update the channel list

1. Open `playlist/tb.m3u8` on GitHub and press the pencil (Edit) button.
2. Add or edit entries:

   ```m3u
   #EXTM3U
   #EXTINF:-1 tvg-id="channel-id" tvg-logo="https://…/logo.png" group-title="News",Channel name
   https://example.com/live/channel/index.m3u8
   ```

   - The **first** `#EXTINF` + URL pair is what opens on launch.
   - `group-title` is shown under the channel name and is searchable.
   - Comments (`# ...`) and blank lines are ignored.
   - Duplicate URLs are dropped.

3. Commit the change on `main`.
4. In the app, tap the video → **Refresh**.

> `raw.githubusercontent.com` sits behind a CDN that may serve a stale copy for a
> few minutes. The app appends a cache-busting query parameter on refresh, so a
> manual refresh gets the new file immediately.

## Build it yourself

Requirements:

- JDK 17+ (Android Studio's bundled JDK 21 works)
- Android SDK with **platform 36** and build-tools 36+
- Gradle comes from the wrapper (8.13)

Create `local.properties` with your SDK path (this file is git-ignored):

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

Then:

```bat
gradlew.bat assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

For a signed, minified **release** build:

```bat
gradlew.bat assembleRelease
```

The release APK lands at `app/build/outputs/apk/release/app-release.apk`.

Release signing reads `keystore.properties` from the repo root (git-ignored):

```properties
storeFile=keystore/tb-release.jks
storePassword=…
keyAlias=tb
keyPassword=…
```

Create the keystore once with `keytool`, or copy an existing one to
`keystore/tb-release.jks`. **Back it up** — without it you cannot install an
update over a release build signed with the old key.

Run the parser unit tests with:

```bat
gradlew.bat testDebugUnitTest
```

### Point a test build at another playlist

```bat
gradlew.bat assembleDebug -PplaylistUrl=https://example.com/my-list.m3u8
```

This only changes the compiled-in default. There is no in-app settings screen;
change the URL here (or in `playlist/tb.m3u8`) and rebuild.

## Install on a phone or TV

With USB debugging enabled:

```bat
adb install -r app\build\outputs\apk\release\app-release.apk
```

Or copy the APK to the device and open it (allow "install unknown apps" for your
file manager). On Android TV the app appears in the leanback launcher as **TB**.

### Samsung Tizen TV (`.wgt`)

Samsung TVs cannot install APKs, so `tizen/` is a separate web app with the same
playlist and hidden-channel PIN. It shows a single channel list (TV and radio
together, in playlist order) because the TV remote has no tab keys. The signed
package is `dist/TB.wgt`.

1. On the TV, open **Apps**, then enter **Developer mode** (Apps → press `12345`
   on the remote → Developer mode ON → enter your PC's IP → reboot the TV).
2. Install the Tizen CLI/SDK on the PC, then connect:

   ```bat
   sdb connect <TV-IP>:26101
   sdb devices
   ```

3. Install and launch (use the TV name from `sdb devices`):

   ```bat
   tizen install -n dist\TB.wgt -t <TV model name>
   sdb shell 0 was_execute tbplayer01.tb
   ```

   For example `-t UA65AU7000KXXM`. The package ID is `tbplayer01` — Samsung
   TVs reject package IDs that are not exactly 10 characters, so keep it at
   10 if you rename the app.

   Alternatively open **Tizen Studio → Device Manager → right-click the TV →
   Install app**, and pick `dist/TB.wgt`.

Remote controls match the Android app: **OK** plays the focused channel,
**◀ ▶** zaps, **▲ ▼** moves the list, **BACK** hides/shows the channel list.
The on-screen **i** button at the top of the list opens the hidden-playlist PIN
prompt (PIN `1234`); walk up to it with **▲** from the first channel, or click it
with a pointer. Three presses of **INFO** do the same on remotes that have the
key.

Re-package after editing `tizen/`:

```bat
tizen package -t wgt -s <your-cert-profile> -o "%CD%\dist" -- "%CD%\tizen"
```

## GitHub setup (first time)

1. Create a **public** repository named `TB` on GitHub. Do not add a README,
   `.gitignore` or license — this project already has them.
2. From this folder, run:

   ```bat
   git remote add origin https://github.com/ZeeyKhm/TB.git
   git branch -M main
   git push -u origin main
   ```

3. Open the raw URL in a browser to confirm it returns the playlist:
   `https://raw.githubusercontent.com/ZeeyKhm/TB/main/playlist/tb.m3u8`
4. Install the APK. It will start with the first channel in that file.

The repository must be **public**. Raw GitHub URLs for private repos need a
token, and this app intentionally does not embed credentials.

## Notes and limitations

- HLS (`.m3u8`), DASH (`.mpd`) and progressive MP4/WebM are supported; raw
  MPEG-TS and RTMP streams are not.
- The Tizen web app plays HLS and DASH with the TV's built-in player and
  retries with Samsung AVPlay (which also handles raw MPEG-TS) when the built-in
  player rejects a stream. Short API links that redirect to a manifest are
  resolved before playback. If a channel still fails, the app shows the URL, the
  AVPlay state and the player's error instead of a generic message.
- `#TB-IFRAME:<url>` makes the Tizen app play that channel inside the
  broadcaster's own web player. It is used for RTB Sukmaindera and RTB Aneka,
  whose CDN only serves requests that carry the `rtbgo.bn` referrer, which a TV
  app cannot set. The app frames `rtbgo.bn/live/<CHANNEL>`, hides the channel
  list, and scales the frame so only the broadcaster's video area is visible
  (the surrounding website is cropped out). **BACK** brings the list back.
- If the built-in player starts a stream with sound but no picture (as the
  Malaysian RTM channels do), the Tizen app reopens it in AVPlay, and then in
  hls.js (software decode) when AVPlay reports an audio-only stream too.
- Referer-locked channels without a `#TB-IFRAME:` page can only play in the
  broadcaster's own app; the Tizen app says so instead of failing silently.
- Widevine (`com.widevine.alpha`) and ClearKey (`org.w3.clearkey`) DRM are read
  from `#KODIPROP` lines. Widevine needs a licence server in the playlist and a
  device that supports Widevine (most Play-certified phones do).
- `#EXTVLCOPT:http-user-agent` / `http-referrer` values and `|Referer=` /
  `|User-Agent=` URL suffixes are applied to that channel's requests.
- No EPG, favorites, resume position or picture-in-picture yet.
- Always opens the first channel on a cold launch, by design, using the playlist
  URL compiled into the app. The Tizen app instead starts on the channel list and
  waits for OK, so a dead first entry is not shown as an error at launch.
- The activity is locked to landscape (`sensorLandscape`), so it flips between
  the two landscape sides but never shows portrait.
- Release builds are signed with the keystore in `keystore/` and its passwords in
  `keystore.properties`; both are git-ignored. Back them up, because losing them
  means you cannot update an installed release build.
- The GitHub Actions workflow builds a debug APK as a quick sanity check. Install
  the signed release APK from `dist/` or `app/build/outputs/apk/release/`.

## Legal

**TB ships no channels and hosts no streams.** It is a player for playlist files.

This project is not affiliated with any broadcaster, and all channel names and
logos belong to their owners. Streams come from third-party URLs written in a
playlist; their legality and availability vary by country, and some may be
geo-restricted. You are responsible for having the right to watch what you play
and for following the law where you live. Personal, lawful use only.

Read the full [DISCLAIMER](DISCLAIMER.md) — it covers content ownership,
affiliation, takedown requests and liability.

Rightsholder takedown requests: open an issue at
<https://github.com/ZeeyKhm/TB/issues> with the exact URL; entries are removed
promptly.

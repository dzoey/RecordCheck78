# RecordCheck78 — 78rpm Record Donation Checker

An Android app for checking whether 78rpm vinyl records are already in the
[Internet Archive](https://archive.org) before donating them.

## How It Works

1. **Point your phone at a 78rpm record label** and take a photo
2. **ML Kit OCR** reads the catalog number, artist, title, and label name
3. **ML Kit Image Labeling** identifies the label style/era
4. **Internet Archive API** is queried with multiple search strategies:
   - By catalog number (most precise)
   - By artist + title
   - By title alone
   - Broad search with all available info
5. **Result**: Shows whether the record already exists on IA
   - ✅ Already exists → no need to donate
   - 📦 Not found → candidate for donation
6. **Save to donation list**: Track which records in your collection need donating

## Features

- 📷 Camera capture with live preview
- 🔍 OCR text extraction from record labels (ML Kit)
- 🎨 AI image recognition for label style/era identification
- ✏️ Editable fields — correct OCR mistakes before searching
- 📋 Batch queue — scan multiple records, review at the end
- 📝 Donation list — track records that need donating
- 🔗 Direct links to IA item pages for verification
- 📊 Stats: to donate / already on IA / donated

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX |
| OCR | ML Kit Text Recognition (Latin) |
| Image AI | ML Kit Image Labeling |
| Networking | OkHttp + Kotlin Coroutines |
| Persistence | SharedPreferences + JSON |
| Build | Gradle 8.9 + AGP 8.7.3 + JDK 17 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 (Android 16) |

## Building

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=$HOME/jdk/jdk-17.0.13+11
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Internet Archive Search

The app searches two 78rpm collections:
- `georgeblood` — The George Blood 78rpm collection (largest)
- `78rpm` — General 78rpm collection

Uses the [IA Advanced Search API](https://archive.org/advancedsearch.php)
with JSON output.

## Testing

```bash
./gradlew test
```

Tests cover:
- OCR text parsing (catalog numbers, label names, artist/title extraction)
- Internet Archive API query building and response parsing
- Error handling (HTTP errors, malformed JSON, empty results)

## License

MIT
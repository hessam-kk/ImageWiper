# PhotoSweep

A fast, minimal Android app for cleaning up your photo gallery. Swipe through photos month by month — swipe right to trash, swipe left to keep.

## Features

- **Swipe to trash** — Swipe right to move photos to a hidden `.PhotoSweepTrash` folder, swipe left to keep
- **Month-by-month review** — Photos are grouped by month for organized cleanup
- **Resume progress** — DataStore-persisted progress lets you pick up where you left off
- **Stats dashboard** — Track total deleted photos and saved space
- **Undo support** — Snackbar with undo action restores trashed photos
- **Full-screen immersive** — No status bar, distraction-free experience
- **Dark theme** — Photo-forward dark aesthetic with accent highlights

## Screenshots

| Main Screen | Swipe Screen |
|-------------|--------------|
| Dark grid with thumbnail previews, stats cards, and month completion tracking | Full-screen photo with swipe gestures and visual indicators |

## Requirements

- Android 7.0+ (API 24)
- `MANAGE_EXTERNAL_STORAGE` permission (requested at runtime for Android 11+)
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` for photo access

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/`

## CI/CD

GitHub Actions workflow (`.github/workflows/build.yml`) builds both debug and release APKs on push/PR to `master`.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material3
- **Image loading**: Coil
- **Permissions**: Accompanist
- **Storage**: DataStore Preferences
- **Build**: Gradle with Kotlin DSL

## How It Works

1. App loads all photos from `MediaStore`, grouped by month
2. Tap a month to start reviewing — choose "From beginning" or "Resume"
3. Swipe right to trash (moves file to `.PhotoSweepTrash/`), swipe left to keep
4. Back button lets you revisit the previous photo
5. Progress is saved automatically; return anytime to continue

## License

Personal use only.

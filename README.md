# MoviClub

MoviClub is an Android fitness app in Kotlin and Jetpack Compose. It turns walking,
running and gym workouts into points, streaks, leagues and monthly rankings, while
keeping the experience usable offline.

## Overview

The app provides the following experiences:

- Dashboard with weekly goals and league status
- Workout selection for walking, running and gym
- Active workout session screen
- Social rankings, groups and friends
- User profile, preferences and offline synchronization status
- Local-first data and future Firestore synchronization

> The repository currently contains the interface prototype and domain foundations.
> Firebase, Room, WorkManager, sensors and the complete workout engine are planned
> in the project specification and are not all implemented yet.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Navigation Compose
- Android Gradle Plugin
- Gradle Kotlin DSL
- JUnit and AndroidX test libraries

The target architecture and roadmap also include:

- MVVM and Unidirectional Data Flow
- Hilt
- Room
- WorkManager
- Firebase Authentication
- Cloud Firestore
- Android accelerometer, step and GPS sensors

## Architecture

The project follows a feature-first structure:

```text
app/src/main/java/com/moviclub/app/
├── core/
│   ├── domain/
│   │   └── model/
│   └── theme/
├── features/
│   ├── home/
│   ├── training/
│   ├── workout/
│   ├── social/
│   └── profile/
├── navigation/
├── MainActivity.kt
└── MoviClubApplication.kt
```

Feature views contain Compose presentation only. Feature-specific components belong
in the feature's `components/` folder. Shared reusable UI will live in
`core/designsystem/components/`.

More details are available in [CODE_RULES_GUIDELINE.md](docs/CODE_RULES_GUIDELINE.md).

## Project Documents

- [Architecture and code rules](docs/CODE_RULES_GUIDELINE.md)
- [Functional and technical requirements](docs/REQUIREMENTS.md)
- [Screen specification](docs/SCREENS.md)
- [Database and domain model](docs/DATABASE.md)
- [Design system](docs/DESIGN.md)

## Requirements

- Android Studio with Android SDK
- JDK 11
- Android SDK 37 for compilation
- Android SDK 29 or newer for the app

The configured application values are:

| Setting | Value |
| --- | --- |
| Application ID | `com.moviclub.app` |
| Minimum SDK | 29 |
| Target SDK | 36 |
| Compile SDK | 37 |
| Version | `1.0` |

## Getting Started

Clone the repository and open the project root in Android Studio.

From Windows PowerShell, build the debug APK with:

```powershell
.\gradlew.bat assembleDebug
```

Compile the Kotlin sources with:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Install and run the app in Android Studio by selecting the `app` configuration and
starting an emulator or connected Android device.

## Tests

Run unit tests:

```powershell
.\gradlew.bat test
```

Run all configured app checks:

```powershell
.\gradlew.bat check
```

## Current Screens

| Screen | Route area |
| --- | --- |
| Home | Dashboard, weekly goal and ranking status |
| Training | Walking, running and gym modality selection |
| Active workout | Timer, pause/resume and finish controls |
| Social | Rankings, groups and friends |
| Profile | Profile score and app settings |

## Offline-First Design

The planned data flow is:

1. Save workout sessions locally in Room.
2. Observe local database changes with Kotlin Flows.
3. Queue synchronization through WorkManager.
4. Sync idempotently with Firestore.
5. Update monthly rankings and user statistics atomically.

Monthly rankings use the `YYYY-MM` month key. Starting a new month therefore selects
a new ranking collection instead of requiring a server-side reset job.

## Code Conventions

- Keep `*View` composables stateless and presentation-only.
- Use `StateFlow<UiState>` and actions for stateful feature flows.
- Put feature-specific components under `features/<feature>/components/`.
- Promote a component to `core/designsystem/components/` only when another feature needs it.
- Put modifiers as the first optional parameter of reusable components.
- Keep dynamic telemetry values in monospaced tabular typography.
- Avoid hardcoded colors and text styles in feature views.

## Contributing

1. Create a feature branch.
2. Follow the patterns in [CODE_RULES_GUIDELINE.md](docs/CODE_RULES_GUIDELINE.md).
3. Keep UI previews independent of ViewModels and backend services.
4. Add or update tests for behavior and domain rules.
5. Run `.\gradlew.bat check` before opening a pull request.

# Rapid Fire 🎯

A brick-breaking cannon game for Android. Aim your cannon, fire bouncing balls, and destroy descending bricks before they reach the bottom!

## Features

- **Physics-based gameplay** — balls bounce off walls and bricks with realistic reflections
- **5 brick shapes** — squares and 4 triangle orientations with corner-radius collision
- **Progressive difficulty** — each round adds a new row of bricks with increasing values
- **Colorful visuals** — dark background with value-coded brick colors floating in space
- **Leaderboard** — local high score tracking with Room database
- **Sound effects** — original fire, bounce, explode, and game-over sounds

## Building

1. Open the project in an Android Studio version compatible with **AGP 9.5**
2. Let Gradle sync dependencies
3. Use **JDK 17** for Gradle (the project configures this through Gradle daemon JVM criteria)
4. Run on a device/emulator with **API 31+** (Android 12)

From PowerShell:

```bash
.\gradlew.bat assembleDebug
```

From macOS/Linux:

```bash
bash ./gradlew assembleDebug
```

## Architecture

- **Kotlin** + Android Canvas/SurfaceView — custom 60fps game loop
- **Navigation Component** — single-activity with fragments (menu, game, settings, leaderboard, game over)
- **Room** — local high score persistence
- **Material 3** — dark theme UI
- **GitHub Actions** — pull-request builds, tests, lint, and signed tag releases

## Project Structure

```
com.rapidfire.game
├── engine/      # GameView, GameLoop, GameState, Renderer, InputHandler
├── model/       # Brick, Ball, Cannon, GameBoard, BrickShape
├── physics/     # CollisionDetector, ReflectionCalculator, BrickShapeGeometry
├── generation/  # RowGenerator, GenerationParams
├── audio/       # SoundManager (SoundPool)
├── data/        # Room database (ScoreEntity, ScoreDao, ScoreDatabase)
├── ui/          # Fragments (MainMenu, Game, GameOver, Settings, Leaderboard)
└── util/        # Constants, ColorPalette
```

## CI/CD

GitHub Actions builds, tests, and lints pull requests to `main`. Pushing a version tag (for example, `v1.3.1`) creates a signed release. To set up signing, add these repository secrets:

- `KEYSTORE_BASE64` — base64-encoded release keystore
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — signing key alias
- `KEY_PASSWORD` — key password

## License

[MIT](LICENSE)

# Rapid Fire 🎯

A brick-breaking cannon game for Android. Aim your cannon, fire bouncing balls, and destroy descending bricks before they reach the bottom!

## Features

- **Physics-based gameplay** — balls bounce off walls and bricks with realistic reflections
- **5 brick shapes** — squares and 4 triangle orientations with corner-radius collision
- **Progressive difficulty** — each round adds a new row of bricks with increasing values
- **Colorful visuals** — dark background with value-coded brick colors floating in space
- **Leaderboard** — local high score tracking with Room database
- **Sound effects** — fire, bounce, explode, and game over SFX

## Building

1. Open the project in **Android Studio Ladybug** or later
2. Let Gradle sync dependencies
3. Run on a device/emulator with **API 31+** (Android 12)

Or from the command line:

```bash
./gradlew assembleDebug
```

## Architecture

- **Kotlin** + Android Canvas/SurfaceView — custom 60fps game loop
- **Navigation Component** — single-activity with fragments (menu, game, settings, leaderboard, game over)
- **Room** — local high score persistence
- **Material 3** — dark theme UI
- **GitHub Actions** — CI/CD with signed release artifacts

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

GitHub Actions builds on every PR and creates signed releases on push to `main`. To set up signing, add these repository secrets:

- `KEYSTORE_BASE64` — base64-encoded release keystore
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — signing key alias
- `KEY_PASSWORD` — key password

## License

MIT

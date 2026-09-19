# WSW Olomouc

**Weather Station Widget Olomouc** — an Android app showing the *current weather in Olomouc (Czech Republic) from direct measurements of local weather stations*, not model forecasts.

## Data sources

| Source | Type | Update latency | License |
|---|---|---|---|
| [infopocasi-olomouc.cz](https://infopocasi-olomouc.cz) (Davis station, `clientraw.txt`) | direct measurement | ~minutes | see station website |
| [CHMI Olomouc–Holice](https://opendata.chmi.cz) (station `0-203-0-11742`, measuring since 1850) | direct measurement, 10-min data | ~tens of minutes | CC BY 4.0 |

The widget synthesizes both stations when both are fresh (arithmetic mean of temperature, badge showing source: `Ø 2 stations` / single station name), and always shows the measurement age.

## Status

Personal-use project, Phase 1 (M1.x milestones). No Google Play release planned for Phase 1.

## Tech stack

Kotlin, Jetpack Compose, Glance (widget), Retrofit/OkHttp, Room, Hilt, WorkManager.

## Build

```bash
./gradlew assembleDebug      # build
./gradlew testDebugUnitTest   # unit tests
```

Requires JDK 17 and Android SDK 35.

## License

MIT — see [LICENSE](LICENSE). Weather data: CHMI open data under CC BY 4.0; infopocasi-olomouc.cz data for personal use only.

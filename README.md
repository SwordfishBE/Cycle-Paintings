# Cycle Paintings

Cycle placed paintings without breaking and replacing them.

## Installation and use

Install `build/libs/cycle_paintings-<version>.jar` in the server's `mods` directory, or in your local Fabric installation for singleplayer.
Multiplayer clients do not need this mod.

- Right-click a placed painting while holding a painting in either hand.
- Sneak while right-clicking to cycle backwards.
- Only paintings with the same width and height participate in a cycle.
- The held painting is never consumed. Existing position, facing and entity identity are preserved.
- Modded and datapack paintings participate through the `minecraft:placeable` painting variant tag, in tag order.
- Spectator and Adventure players cannot change paintings.

## Configuration

The server creates `config/cycle_paintings.json` on first world startup:

```json
{
  "ignorePaintingsInCycleIdentifier": "infernalexp,",
  "showRegisteredPaintingsDebug": false
}
```

The exclusion list accepts comma-separated complete namespaces (`examplemod`) or individual painting identifiers (`minecraft:kebab`). 
Whitespace, trailing commas and capitalization are accepted; empty entries are ignored.
Namespace matching is exact. An excluded current painting remains unchanged.

`showRegisteredPaintingsDebug` lists placeable painting identifiers and whether they are allowed, at **DEBUG** level. Enable DEBUG output for the


Invalid configuration emits a warning and uses defaults.
Existing files are never rewritten, including unknown settings and invalid files.
Restart the server or use `/reload` after editing the config.

## Build and verification

Run `./gradlew build` (Windows: `.\gradlew.bat build`) with Java 25.
The installable jar is in `build/libs`; do not install the `-sources.jar`.
`build` includes configuration and text-rendering regression verification.

## License

AGPL-3.0; see [LICENSE](LICENSE). Copyright (c) 2026 SwordfishBE.
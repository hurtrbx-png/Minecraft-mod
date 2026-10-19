# Giantess

A Fabric mod for Minecraft 26.3: a rare giant humanoid who wanders the world as a living landmark.
Work in progress: [SPEC.md](SPEC.md) is the design, [PROGRESS.md](PROGRESS.md) is the current status.

## Build and run

Requires JDK 25.

```
./gradlew build              # compile and run the server game tests
./gradlew runClient          # launch a development client
./gradlew runClientGameTest  # automated client test (needs a display, e.g. xvfb-run)
```

Server game tests need the Minecraft EULA (https://aka.ms/MinecraftEULA); pass `-Pminecraft.eula=true` once you accept it.
`tools/gate.sh` runs the whole check (build, server tests, headless client test, log scan); set `MINECRAFT_EULA=true` to accept the EULA for it.

## Playing

Find the Giantess Spawn Egg in the Spawn Eggs creative tab, or use `/summon giantess:giantess`.

## Config

Settings live in `config/giantess.json`, created with defaults on first launch and read at startup (restart after editing; invalid values are clamped with a log warning).

| Key | Default | Meaning |
| --- | --- | --- |
| `size.tier` | `MEDIUM` | `SMALL`, `MEDIUM` or `COLOSSAL`: which of the scales below she uses |
| `size.small` / `size.medium` / `size.colossal` | `4` / `8` / `16` | Scale of each tier relative to a normal humanoid (0.0625 to 16); hitbox and model follow it |
| `maxHealth` | `500` | Her health |
| `movementSpeed` | `0.25` | Base walking speed |
| `trackingRangeChunks` | `16` | How far away, in chunks, players receive her; the server view distance still caps it |
| `debugLogging` | `false` | Extra diagnostic logging |

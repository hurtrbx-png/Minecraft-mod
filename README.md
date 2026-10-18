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

## Config

Settings live in `config/giantess.json`, created with defaults on first launch.

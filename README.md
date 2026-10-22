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

She strolls slowly between pauses and looks at nearby players. Her step height grows with her size, she wades through shallow water and walks through trees without harming them (block tag `giantess:walks_through`, editable with a data pack). She avoids cliffs, deep water and lava, and never breaks blocks.

Each step is a heavy thud: the ground shakes the camera (less the farther you are), block dust and leaves fly up from her foot, and the step sound is the block's own pitched down. Nothing is damaged: she does not trample farmland or crush turtle eggs.

Right-click her with an empty hand to climb onto her shoulder; a second player can take the other one. You stay aboard as she walks and turns, and nothing she walks through can suffocate you. Sneak to climb down: you are set on the ground beside her, never dropped. If she dies, riders are set down the same way.

## Config

Settings live in `config/giantess.json`, created with defaults on first launch and read at startup (restart after editing; invalid values are clamped with a log warning).

| Key | Default | Meaning |
| --- | --- | --- |
| `size.tier` | `MEDIUM` | `SMALL`, `MEDIUM` or `COLOSSAL`: which of the scales below she uses |
| `size.small` / `size.medium` / `size.colossal` | `4` / `8` / `16` | Scale of each tier relative to a normal humanoid (0.0625 to 16); hitbox and model follow it |
| `maxHealth` | `500` | Her health |
| `movementSpeed` | `0.3` | Base walking speed |
| `trackingRangeChunks` | `16` | How far away, in chunks, players receive her; the server view distance still caps it |
| `movement.stepHeightPerScale` | `0.6` | Tallest ledge she walks up, per unit of scale (capped at 10 blocks) |
| `movement.wanderSpeedModifier` | `1.0` | Multiplier on `movementSpeed` while strolling |
| `movement.wanderMinDistance` / `wanderMaxDistance` | `16` / `48` | Length of each stroll, in blocks |
| `movement.idleMinTicks` / `idleMaxTicks` | `100` / `400` | Pause between strolls, in ticks (20 per second) |
| `movement.turnSpeedDegrees` | `4.0` | How fast she turns, in degrees per tick |
| `movement.maxWadeDepth` | `0.4` | Deepest water she wades into, as a fraction of her height |
| `movement.stuckCheckTicks` / `stuckMinProgress` | `60` / `1.0` | A stroll is given up when she covers less than this many blocks in this many ticks |
| `movement.lookAtPlayerRange` | `48` | How far away, in blocks, she notices and looks at players |
| `footsteps.enabled` | `true` | Server: announce a footfall every stride she walks; off silences every footstep effect |
| `footsteps.strideLengthPerScale` | `0.8` | Server: blocks per step, per unit of scale |
| `footsteps.cameraShake` / `reduceShake` | `true` / `false` | Client: shake the camera when she steps nearby; `reduceShake` cuts it to a quarter (accessibility) |
| `footsteps.shakeStrength` | `1.0` | Client: multiplier on the shake |
| `footsteps.shakeRangePerScale` | `6.0` | Client: the shake fades out at this many blocks per unit of her scale (48 blocks at medium) |
| `footsteps.soundVolume` / `soundPitch` | `4.0` / `0.5` | Client: step sound volume (hearing distance is 16 blocks times this) and pitch (0.5 to 2.0) |
| `footsteps.particleCount` | `24` | Client: block particles per step (the game's particle setting thins them further; `0` for none) |
| `footsteps.particleRangePerScale` | `8.0` | Client: no step particles beyond this many blocks per unit of scale |
| `climbing.enabled` | `true` | Right-click her with an empty hand to climb onto a shoulder; sneak to climb down |
| `climbing.seats` | `2` | Riders at once, one per shoulder (1 or 2) |
| `climbing.seatHeightPerScale` / `seatSidePerScale` | `1.5` / `0.375` | Where the shoulder seat is, per unit of her scale: height above her feet and distance from her centre line |
| `climbing.turnRidersWithHer` | `true` | Riders' view turns along with her |
| `climbing.riderShakeMultiplier` | `0.5` | Client: multiplier on footstep shake while riding her |
| `debugLogging` | `false` | Extra diagnostic logging |

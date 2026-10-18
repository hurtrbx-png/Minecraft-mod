# Giantess — Fabric mod for Minecraft 26.3

## Vision
A rare, giant humanoid mob that wanders the world as a living landmark. She is a spectacle and a gameplay platform, not a farm target. Scale is the central mechanic. Quality over quantity: ship a small set of features that feel polished.

## Scope (v0.1)
In: one giant entity, footstep shake, climb-on platform, disposition system, config.
Out (later): boss fight, shrink/grow potions, tamed companion, giant ruins.

## Hard rules
- Non-destructive: her footsteps never break blocks, so builds are safe.
- Client/server separation: gameplay logic server-side, effects and rendering client-side.
- All tunable values (size, speed, shake strength, spawn rate) live in a config file, never hardcoded.
- Content is family-friendly and non-sexual: a gameplay-focused giant character.
- Verify 26.3 APIs against project sources or mappings; never guess.

## Milestones

### M1 — Skeleton
- Fabric project builds and launches the client with no log errors.
- Mod id, metadata, registry setup, lang file, config loader stub.
- Done when: client launches, mod appears in the mod list.

### M2 — Entity with placeholder model
- Register the giant entity with attributes (health, speed, large hitbox scaled by a size constant).
- Placeholder model and texture. Spawn egg and `/summon` work.
- Done when: she spawns, stands, and the oversized hitbox behaves (no suffocation, no stuck pathing).

### M3 — Movement and scale
- Slow wandering AI scaled to size (long strides, wide turning).
- Pathfinding handles her size: steps over small terrain, avoids getting stuck in trees and hills.
- Size tier read from config (e.g. small / medium / colossal).
- Done when: she wanders 5+ minutes in varied terrain without getting stuck.

### M4 — Footsteps and world feel
- Screen shake for nearby players, falling off with distance.
- Dust/leaf particles, scattered dropped items, heavy footstep sounds.
- No block damage.
- Done when: effects are noticeable at close range and gone at long range, with no performance dip.

### M5 — Climb-on platform
- Players can ascend an arm or onto a shoulder and stay mounted as she moves.
- Handle dismount, falling off, multiplayer sync, and her turning.
- Done when: a player can board, ride for a minute, and dismount safely, in singleplayer and with two clients.

### M6 — Disposition system
- States: hostile, neutral, friendly. Start neutral.
- Gifts and protecting nearby villagers raise trust. Attacking her lowers it.
- Behavior and visuals change per state (e.g. hostile keeps distance and warns before acting).
- Disposition persists in NBT per player.
- Done when: state changes through gameplay and survives a save/reload.

### M7 — Real model, animations, polish
- Final model, textures, walk/idle/reaction animations.
- Sounds, config screen or file documentation, spawn rules (rare, biome-limited).
- Done when: full playthrough with no crashes, and a short test checklist passes.

## Quality gate for every milestone
1. Compiles and launches without log errors.
2. Tested in-game, with a short written checklist of what was verified.
3. Review pass: null checks, client/server mix-ups, hardcoded values.
4. PROGRESS.md updated: done, known issues, next step.

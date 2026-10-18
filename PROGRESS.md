# Giantess — PROGRESS
**Status:** PLAN DRAFT, awaiting user approval. No milestone started.
Source of truth alongside SPEC.md. After every milestone: update done / known issues / next step, then /compact.

## Working agreement (from user)
- Sonnet = architecture, mixins, rendering, networking, API problems, final review. Haiku subagents = narrow mechanical jobs only (lang/JSON, loot/recipes/tags, boilerplate, config scaffolding, tests, stack-trace summaries).
- Subagent brief = goal, exact files, spec excerpt, naming conventions, "return summary <120 words, no code dumps". Max 2-3 in parallel, different files only. No subagent for <1 min of work.
- Ask user to switch to Opus when a design step needs it.
- Tokens: never re-read just-written files (Grep / offset+limit). Never guess 26.3 APIs: check project jars/mappings (`javap`) or search once, then record under API notes. Build/run only at end of a chunk; read only relevant error lines. Short replies.
- Gate per milestone: compiles + client launches without log errors; tested in-game with short checklist; review pass (null checks, client/server mix-ups, hardcoded values -> config); update this file.
- User says "near limit" or session gets long: stop at next safe point, commit (on a branch, not main), write exact next steps + open bugs here, end.

## Environment (checked 2026-10-18)
- Network OK to maven.fabricmc.net, piston-meta.mojang.com, Maven Central. Gradle at /opt/gradle; Xvfb + xvfb-run present.
- NOT yet checked: Java version vs 26.3 requirement, Mesa/GL for client under Xvfb, Mojang assets host.
- Repo started empty (LICENSE, README.md); SPEC.md copied to root. On `main`: use a feature branch for commits, draft PR after push.
- Test strategy: server logic = Fabric gametests (headless). Client = Xvfb + client gametest + screenshots (viewable as PNG). Anything GL cannot cover here goes on a short manual checklist for the user.
- At M1 start read skill refs `writing-from-scratch.md` + `version-baselines.md` once; record the needed numbers under API notes.

## Design defaults (hypotheses; assumed unless user objects; verify APIs when implementing)
1. Size: vanilla scale attribute on a base-humanoid EntityType; size tier -> scale from config. Suffocation immunity, large tracking range.
2. Effects: server detects footsteps -> entity-event broadcast -> client does shake/particles/sound. No custom packets until needed.
3. Riding: vanilla passenger system, multiple shoulder seats, right-click to board, safe dismount.
4. Disposition: per-player global reputation stored on the player (persistent data attachment), not per-giantess. Her visible mood follows the nearest relevant player.
5. Pathing: custom oversize-aware navigation (virtual footprint / large step height), not vanilla A* over the full hitbox.
6. Config: one JSON file; each milestone adds its own keys; nothing tunable hardcoded (size, speed, shake, spawn rate, caps).

## Milestones (a/b/c = safe stopping points)
- [ ] **M1 Skeleton.** Fabric 26.3 project (mojmap, no remap), mod id `giantess`, fabric.mod.json, lang, registry bootstrap, config loader stub. Done: client launches under Xvfb, mod in Fabric startup log (+ Mod Menu if it supports 26.3), dedicated server boots to Done, no log errors. Haiku: none planned.
- [ ] **M2 Entity + placeholder.** (a) entity type, attributes, config-driven scale, spawn egg, `/summon`, placeholder humanoid model + texture; (b) hitbox sanity: no suffocation, tracking range, idle stand. Done: spawns, stands, gametest shows no damage over N ticks inside terrain, screenshot. Haiku: lang, egg/item definition JSON, loot table, gametest.
- [ ] **M3 Movement + scale.** (a) slow wander goal + move control scaled to size; (b) oversize terrain handling (hills, trees, water), size tiers small/medium/colossal from config, 5+ min soak with stuck detector. Done: wanders 5+ min in varied terrain, never stuck. **Risk HIGH: ask user for Opus for navigation design.**
- [ ] **M4 Footsteps + world feel.** (a) footstep events, distance-falloff shake (client camera mixin, strength + reduce-shake option in config), heavy step sound (vanilla sound pitched down as placeholder); (b) dust/leaf particles, capped scattered items, perf caps, no-block-damage assertion. Done: noticeable near, gone far, no perf dip.
- [ ] **M5 Climb-on.** (a) board/dismount single shoulder seat, singleplayer, safe dismount; (b) multi-seat, her turning, fall-off handling, 2-client sync. Done: board, ride 1 min, dismount safely, SP + 2 clients. **Risk HIGH: ask user for Opus for design.**
- [ ] **M6 Disposition.** (a) per-player data + inputs (gift via item tag, protecting villagers, attacking) + persistence + debug command, save/reload test; (b) per-state behavior (hostile keeps distance, warns before acting) + visuals. Done: state changes through play, survives reload.
- [ ] **M7 Polish.** (a) spawn rules (rare, biome-limited, config), sounds, config docs in README; (b) real model/texture + walk/idle animations; (c) full playthrough + checklist. Input needed from user before 7b: art/audio source (their Blockbench files, or I generate stylised placeholders).

## Cross-milestone constraints
- M5 seat anchors depend on model proportions: recheck in M7b.
- M4 footstep timing is distance-based: retune to walk animation in M7b.
- Every new tunable gets a config key and a README line.
- Cut first if budget runs short: config screen, reaction animations, scattered items, arm-climb path (keep shoulder only).

## API notes (look up once, record here)
(empty)

## Known issues / open bugs
(none)

## Next step
User approves milestone list -> start M1.

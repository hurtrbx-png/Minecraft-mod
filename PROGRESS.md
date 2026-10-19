# Giantess — PROGRESS
**Status:** Plan APPROVED by user (2026-10-18). M1 and M2 done and verified; next: M3 (ask user to switch to Opus for the navigation design).
**Art/audio decision:** user lets Claude generate models/textures/sounds procedurally OR source quality ones online. Only CC0/permissive licenses; log every external asset (URL, author, license) in CREDITS.md; family-friendly.
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
- Headless client VERIFIED: 26.3 tries a Vulkan backend first (fails, no ICD) then OpenGL. Plain Xvfb+GLX fails ("Couldn't find matching GLX visual"); what works is Mesa llvmpipe (GL 4.5) + apt `libegl1 libegl-mesa0 libgles2` + env `SDL_VIDEODRIVER=offscreen LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null` (the session hook installs/sets these). Then `./gradlew runClientGameTest` just works, no Xvfb needed. Mojang asset hosts reachable.
- Repo started empty (LICENSE, README.md); SPEC.md copied to root. On `main`: use a feature branch for commits, draft PR after push.
- Test strategy: server logic = Fabric gametests (headless). Client = headless client gametest + screenshots (PNG, viewable with Read). Anything GL cannot cover here goes on a short manual checklist for the user.
- At M1 start read skill refs `writing-from-scratch.md` + `version-baselines.md` once; record the needed numbers under API notes.

## Design defaults (hypotheses; assumed unless user objects; verify APIs when implementing)
1. Size: vanilla scale attribute on a base-humanoid EntityType; size tier -> scale from config. Suffocation immunity, large tracking range.
2. Effects: server detects footsteps -> entity-event broadcast -> client does shake/particles/sound. No custom packets until needed.
3. Riding: vanilla passenger system, multiple shoulder seats, right-click to board, safe dismount.
4. Disposition: per-player global reputation stored on the player (persistent data attachment), not per-giantess. Her visible mood follows the nearest relevant player.
5. Pathing: custom oversize-aware navigation (virtual footprint / large step height), not vanilla A* over the full hitbox.
6. Config: one JSON file; each milestone adds its own keys; nothing tunable hardcoded (size, speed, shake, spawn rate, caps).

## Milestones (a/b/c = safe stopping points)
- [x] **M1 Skeleton (done 2026-10-18).** Fabric 26.3 project (mojmap, no remap), mod id `giantess`, fabric.mod.json, lang, registry bootstrap, config loader stub. Done: client launches under Xvfb, mod in Fabric startup log (+ Mod Menu if it supports 26.3), dedicated server boots to Done, no log errors. Haiku: none used.
  - Verified: build OK; server game tests 3/3 (mod loaded, id helper, config defaults, corrupt config file kept untouched); headless client game test reaches a singleplayer world and the screenshot renders (flat world + HUD); startup log lists `giantess 0.1.0`; `config/giantess.json` written with defaults; no mod-tagged log errors beyond the intentional one from the corrupt-config test. Not tested: Mod Menu (may lack 26.3 support).
- [x] **M2 Entity + placeholder (done 2026-10-19).** (a) entity type, attributes, config-driven scale, spawn egg, `/summon`, placeholder humanoid model + texture; (b) hitbox sanity: no suffocation, tracking range, idle stand. Done: spawns, stands, gametest shows no damage over N ticks inside terrain, screenshot. Haiku: none used (every piece was under a minute of work).
  - Verified: server game tests 6/6 (hitbox = base 0.6x1.95 times configured scale, config scale limits match the vanilla attribute range, head buried in stone for 60 ticks = no damage and still on the ground, environmental damage ignored but ordinary damage hurts, egg maps to the entity, no despawn when far); headless client test: `/summon` works, exactly one unharmed giantess, screenshots checked (scaled, textured, face and egg icon correct, "Giantess" and "Giantess Spawn Egg" names). Size tiers (SMALL 4, MEDIUM 8, COLOSSAL 16, scale attribute max is 16) were done here instead of M3. No external assets: textures come from `tools/gen_textures.py`, so CREDITS.md is not needed yet.
- [ ] **M3 Movement + scale.** (a) slow wander goal + move control scaled to size; (b) oversize terrain handling (hills, trees, water), (size tiers already done in M2), 5+ min soak with stuck detector. Done: wanders 5+ min in varied terrain, never stuck. **Risk HIGH: ask user for Opus for navigation design.**
- [ ] **M4 Footsteps + world feel.** (a) footstep events, distance-falloff shake (client camera mixin, strength + reduce-shake option in config), heavy step sound (vanilla sound pitched down as placeholder); (b) dust/leaf particles, capped scattered items, perf caps, no-block-damage assertion. Done: noticeable near, gone far, no perf dip.
- [ ] **M5 Climb-on.** (a) board/dismount single shoulder seat, singleplayer, safe dismount; (b) multi-seat, her turning, fall-off handling, 2-client sync. Done: board, ride 1 min, dismount safely, SP + 2 clients. **Risk HIGH: ask user for Opus for design.**
- [ ] **M6 Disposition.** (a) per-player data + inputs (gift via item tag, protecting villagers, attacking) + persistence + debug command, save/reload test; (b) per-state behavior (hostile keeps distance, warns before acting) + visuals. Done: state changes through play, survives reload.
- [ ] **M7 Polish.** (a) spawn rules (rare, biome-limited, config), sounds, config docs in README; (b) real model/texture + walk/idle animations; (c) full playthrough + checklist. Art/audio: decided (see top); no user input needed.

## Cross-milestone constraints
- M5 seat anchors depend on model proportions: recheck in M7b.
- M4 footstep timing is distance-based: retune to walk animation in M7b.
- Every new tunable gets a config key and a README line.
- Cut first if budget runs short: config screen, reaction animations, scattered items, arm-climb path (keep shoulder only).

## API notes (look up once, record here)
- Toolchain (verified): plugin `net.fabricmc.fabric-loom` 1.18-SNAPSHOT, Gradle wrapper 9.7.1, JDK 25 (Temurin at /home/user/tools/jdk-25, wired through ~/.gradle/gradle.properties `org.gradle.java.home`; `.claude/hooks/session-start.sh` recreates both in new cloud sessions), Fabric Loader 0.19.5, Fabric API 0.163.0+26.3. Mojmap, no remap: deps use `implementation` (not `modImplementation`), no `mappings` line. Reference template (CC0): FabricMC/fabric-example-mod branch 26.3, cloned read-only at /home/user/fabricmc/fabric-example-mod.
- Run Gradle as `./gradlew ... --console=plain > $SCRATCH/x.log 2>&1` and read only grep'd/tail'd lines. Pass `-Pminecraft.eula=true` for server game tests (nothing accepts the Minecraft EULA by default).
- javap: MC jars at `~/.gradle/caches/fabric-loom/26.3/minecraft-{common,client,server}.jar`; add `2>/dev/null` to hide the JAVA_TOOL_OPTIONS banner.
- `net.minecraft.resources.Identifier.fromNamespaceAndPath(String,String)` (old ResourceLocation).
- Server game test: method annotated `net.fabricmc.fabric.api.gametest.v1.GameTest` taking `net.minecraft.gametest.framework.GameTestHelper` (`succeed()`, `fail(String)`, `assertTrue(boolean,String)`, `spawn(EntityType, BlockPos|Vec3|int..)`, `assertEntityPresent(..)`, `getLevel()`). Runs in `./gradlew build`.
- Client game test: `FabricClientGameTest.runTest(ClientGameTestContext)`; `context.worldBuilder().create()` gives `TestSingleplayerContext` (the Fabric docs example is outdated): use `.getConnection()` (`waitForChunksRender()`, `getClientLevel()/getClientPlayer()`, `getServerLevel()/getServerPlayer()`, `waitForClientboundEntityUpdates(types..)`) and `.getServer()`; `context.takeScreenshot(name)` returns the saved Path. Run with `xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest`. Setup: `fabricApi.configureTests { createSourceSet = true; modId = ... }` plus `src/gametest/resources/fabric.mod.json` with entrypoint keys `fabric-gametest` / `fabric-client-gametest`.
- Assets sources: freesound.org is blocked (403); opengameart.org reachable.
- Baseline log noise, ignore when judging "no log errors": authlib 401 "Failed to fetch user properties"; Realms errors; "Illegal option value 0 for Anisotropic Filtering"; "Failed to set window icon"; "Requested post effect does not exist: minecraft:end_of_frame"; "Failed to create backend Vulkan"; server `NoSuchFileException: server.properties` trace; the intentional `(giantess)` ERROR from the corrupt-config test. Judge by grep for `(giantess)` and for new exceptions.
- Client resources (assets/lang) live in `src/client/resources`; data and registry code in main. Gametests live in `src/gametest`.
- Decompiled 26.3 sources for grepping: /home/user/tools/mc-sources/{common,client} (made with `./gradlew genSources`, then unzip the `*-sources.jar` files under `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-{common,clientOnly}-*/26.3/`; redo after a container reset). Grep these instead of guessing.
- `tools/gate.sh` = full gate (build, server tests, headless client test, filtered log scan); `MINECRAFT_EULA=true tools/gate.sh [--no-client]`. Screenshots land in `build/run/clientGameTest/screenshots` (view with Read).
- Entity (M2): `LivingEntity` ctor does NOT refresh dimensions, so call `refreshDimensions()` at the end of the entity ctor when the default SCALE attribute is not 1. `SCALE` range 0.0625 to 16, syncable; `getDimensions(pose)` = default dims x scale; renderer applies `state.scale` itself. `EntityType.Builder.of(factory, MobCategory).sized().eyeHeight().clientTrackingRange(chunks).fireImmune().noLootTable().build(ResourceKey)`; attributes via `FabricDefaultAttributeRegistry.register(type, builder)`; `Mob.createMobAttributes()`.
- Entity overrides used to survive her size: `isInWall()` false, `canBreatheUnderwater()` true, `causeFallDamage` false, `pushEntities()` no-op (also avoids cramming damage), `isPushable()` false, `isInvulnerableTo(ServerLevel, DamageSource)` via tag `data/giantess/tags/damage_type/giantess_ignores.json`, `removeWhenFarAway` false, `canBeLeashed` false. Vanilla `Giant` also sets attribute `CAMERA_DISTANCE` (check for M5 riders).
- Renderer: `HumanoidMobRenderer<E, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>`, model `new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE))`, `createRenderState()` + `getTextureLocation(state)`; registered with `EntityRendererRegistry.register(type, Renderer::new)`. Texture layout is the 64x64 humanoid skin.
- Spawn egg: `new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type))`, plus `assets/<ns>/items/<id>.json`, `models/item/<id>.json` (parent `minecraft:item/generated`, own 16x16 texture; vanilla eggs are plain textures in 26.3). Creative tab: `CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"))).register(out -> out.accept(item))` (the vanilla key constant is private).
- Game tests: `@GameTest(maxTicks = N)` (default 20 ticks!), `context.runAfterDelay(ticks, runnable)`, `context.getLevel()` is the ServerLevel. Client: `TestServerContext.runCommand(cmd)` / `computeOnServer(fn)`, `context.waitTicks(n)`; superflat surface is y=-60; `/summon ... {Rotation:[180.0f,0.0f]}` turns her to face north.

## Known issues / open bugs
- The SessionStart hook (JDK 25, EGL libs, Gradle warm-up) only runs in new cloud sessions once `.claude/settings.json` is on the default branch.
- No CI workflow yet (builds and tests are run in the sandbox).
- Sandbox only: "Can't keep up!" warnings appear while the first world generates (CPU shared with software rendering); not caused by the giantess (none after `/summon`). For M3/M4 measure tick time explicitly instead of trusting this warning.
- Mod Menu still untested. Natural spawning is not implemented yet (M7a); she only appears via egg or `/summon`.

## Next step
M3 needs the Opus design step first (user must switch model): oversize-aware navigation for a 15.6-block (MEDIUM) up to 31-block (COLOSSAL) body. Questions to settle by reading `PathNavigation`, `GroundPathNavigation`, `WalkNodeEvaluator`, `MoveControl`, `Mob` step/collision code in /home/user/tools/mc-sources: virtual footprint vs steering without A*, `STEP_HEIGHT` scaling, water/trees/cliffs handling, tick cost of collisions with her big box. Then a 5+ minute soak test with a stuck detector (server gametest or headless run logging positions).

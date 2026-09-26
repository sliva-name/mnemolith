# Mnemolith architecture

Mnemolith (Мнемолит) is a content mod about memory written into the world. A world event writes an imprint on one chunk, memory pressure is recomputed for that chunk, and a player can extract a slip or compose slips at a reel. Fracture is logged and can spawn a moment replicant. Worldgen feeds that loop with archival veins, mute pockets, and the chronicle observatory. Phase 6 adds the lens overlay, the composition screen, and a per-player discovery catalog. Phase 7 adds budgeted particles for those beats. Phase 8 replaces the placeholder textures and vanilla sound redirects for the lens, reel, mute stone, archival stratum, resonator trap, and the three mobs. Sizes and UV notes are in `docs/asset-pipeline.md`. Phase 9 tunes the existing defaults so the early loop stays readable; the before-and-after numbers are in `docs/balance.md`. Phase 10 keeps those numbers and skips unchanged lens snapshots, quiet cooling, and per-score allocations; the timings are in `docs/performance.md`. Phase 11 checks the same loop with two players on a dedicated server; the hosting notes are in `docs/multiplayer.md`. There is no new biome, no Scar, and no strikethrough shaft.

## Identity

The world writes its history into stone. Players read imprints, compose memory, and survive recollection storms. The mod is not an RPG class system, an ore mod, or an automation framework.

Later content, not built yet:

- the rest of a ~16 block / ~18 item set
- 1 boss event (the Scar, during a recollection storm)
- strikethrough shafts

## Package map

| Package | Side | Role |
| --- | --- | --- |
| `com.mnemolith` | both | `@Mod` entry, mod id, logger |
| `com.mnemolith.common` | both | Names and other types that must load on every side |
| `com.mnemolith.content` | both | Deferred registers for blocks, items, and creative tabs |
| `com.mnemolith.imprint` | both | `Imprint`, `ChunkMemory`, `ImprintWriter`, chunk attachment |
| `com.mnemolith.pressure` | both | `MemoryPressure` and `PressureBand` |
| `com.mnemolith.entity` | both | Effects, entity types, attributes, spawn gates, `MemoryMob` |
| `com.mnemolith.entity.mob` | both | Echo strider, archivist, moment replicant |
| `com.mnemolith.entity.echo` | both | Echo and shell entities (`MemoryAvatar`, `EchoEntity`, `EchoShell`), the echo's 41-slot inventory, and the residual echo (`ResidueEntity`: state, timers, drift; rules in `echo.residue`) |
| `com.mnemolith.echo` | both | Echo recording format, recorder, replay hands (owner fake player), echo registry, possession swap and its player attachment. See [echo-design.md](echo-design.md) |
| `com.mnemolith.echo.graft` | both | Memory grafts: `Temper` (5 tempers from imprint tags), `EchoGraft` (slip + charge, codec), `EchoGrafts` (every graft rule; echo, job, possession and mob code only call it). See [echo-design.md](echo-design.md) §12 |
| `com.mnemolith.echo.residue` | both | Residual echoes: `Residues` holds every rule (condense, fester, lash, lens reading, needle capture, shard graft and release, possession absorb, observatory seeding, act-out). See [echo-design.md](echo-design.md) §13 |
| `com.mnemolith.echo.job` | both | Echo job: saved settings and a thin `tick` on `EchoJob`; mine, build, and farm controllers; shared path/dig (`JobMotion`) and chest I/O (`JobChest`) |
| `com.mnemolith.client.echo` | physical client | Echo renderer, client echo/shell subclasses, lens thermal view, targeting, HUD hints, return key, echo inventory screen |
| `com.mnemolith.entity.ai` | both | Path ledger, the replicant's action window, and the three mobs' goals |
| `com.mnemolith.client.model` | physical client | Placeholder models |
| `com.mnemolith.client.render` | physical client | Entity renderers. Registered from `MnemolithClient` |
| `com.mnemolith.world` | both | `LoadedChunkMemory` and `ChunkState` |
| `com.mnemolith.worldgen` | both | Vein and mute-pocket features, observatory structure type, processor |
| `com.mnemolith.worldgen.feature` | both | `ArchivalVeinFeature`, `MutePocketFeature` |
| `com.mnemolith.worldgen.structure` | both | `ObservatoryStructure`, structure type, reel processor |
| `com.mnemolith.event` | both | Vanilla listeners. `/mnemolith` is registered here and implemented in `command` |
| `com.mnemolith.command` | both | `/mnemolith` subcommands. Not loaded as client screens |
| `com.mnemolith.command.qa` | both | `/mnemolith qa`, `echoqa`, `jobqa`, `echo3qa`, `graftqa`, `residueqa`, `mpsmoke` checklists. Each suite is `check(level, origin)` returning a `QaReport` (named checks and notes); the command only sends it to chat |
| `com.mnemolith.gametest` | both, game test runs only | NeoForge game tests: every QA suite as one test (`SuiteTests`, reusing `check`), live residue tests with real server players (`ResidueLiveTests`, `LivePlayers`). Registered only when `GameTestHooks.isGametestEnabled()` (the game test server, dev runs); a production server registers nothing. See [Game tests](#game-tests) |
| `com.mnemolith.network` | both | Lens request, pressure snapshot, and the catalog-open payload. Client handlers are registered from `MnemolithClient` |
| `com.mnemolith.client.gui` | physical client | Lens overlay, composition screen, catalog screen, panel textures |
| `com.mnemolith.data` | both | Data component register |
| `com.mnemolith.audio` | both | Sound event register |
| `com.mnemolith.particle` | both | Particle types and edge-triggered `MemoryFx` bursts. Providers stay in `com.mnemolith.client.particle` |
| `com.mnemolith.config` | both | Common and server specs. Client values live in `com.mnemolith.client.config` |
| `com.mnemolith.client.config` | physical client | `ClientConfig`. Registered from `MnemolithClient` |
| `com.mnemolith.client.*` | physical client | Render, particles, audio playback, screens |
| `com.mnemolith.server` | dedicated server | Dedicated-server entry |

## Game tests

`./gradlew runGameTestServer` (CI runs it after `build`) boots `GameTestServer`, which runs every registered test and exits with the failure count. `MnemolithGameTests.register` is called from the mod constructor and returns at once unless game tests are enabled. When they are, it adds three things:

- Test functions in the vanilla `test_function` registry (not synced to clients) and a `live_players` environment type.
- On `RegisterGameTestsEvent`: two environments, `mnemolith:suites` and `mnemolith:live` (one batch each, run one after the other), and one `FunctionGameTestInstance` per test on the 3×3×3 air template `data/mnemolith/structure/gametest/empty.nbt` (written by `tools/gametest_structure.py`).
- The live environment's teardown removes every joined player and releases every forced chunk, whether the tests passed or failed.

The suites are synchronous: each runs inside its test's first tick, exactly as inside the command, and fails the test with the failed check names. `SuiteTests` gives each suite its own 4096-block lane and a surface origin. A check that cannot work in a game test world is waived by name with a reason (only `qa`'s `locate`: `GameTestServer` hard-codes structure generation off); its result is still logged.

`LivePlayers.join` creates a real `ServerPlayer` (its own subclass, so NeoForge's config sync skips it as it skips vanilla's test players). It connects it on an in-memory `Connection` that `NetworkRegistry.configureMockConnection` marks as a NeoForge client with every channel, places it with `PlayerList.placeNewPlayer`, marks the client loaded, and ticks it with `doTick` every game tick. The player is in `level.players()`, fires `PlayerTickEvent` and can use items and interact with entities through the normal server methods.

The world comes from `GameTestServer`'s fixed `minecraft:flat` preset, which the test-only datapack `src/gametest/packs/mnemolith_test_world` (passed with `--packs`, never in the jar) overrides with a deep superflat. Details and the coverage table are in [qa-checklist.md](qa-checklist.md#automated-game-tests-ci).

## Side split

NeoForge loads a `@Mod` class only on the distributions listed in `dist`.

- `Mnemolith` has no `dist` filter, so it loads on the physical client and the dedicated server. It must not reference `net.minecraft.client`.
- `MnemolithClient` is `Dist.CLIENT`. It registers the client config and the config screen, then initializes render, particle, audio, and screen hooks.
- `MnemolithServer` is `Dist.DEDICATED_SERVER`. It runs dedicated-server setup only.

Sound *events* are registered from `com.mnemolith.audio` because the server names the sound in play-sound packets. Playing the sound is client code.

Client config is registered in `MnemolithClient`. Dedicated servers do not load that class, so they do not load `ClientConfig` or anything under `com.mnemolith.client`.

## Loop

```text
LivingDeathEvent / ExplosionEvent.Detonate / LivingFallEvent / break / place
        │
        ▼
ImprintWriter.write on that chunk's ChunkMemory attachment
        │
        ▼
MemoryPressure.recompute (that chunk only)
        │
        ├── ExtractionNeedleItem → Imprint Slip (ImprintCast component)
        │
        ├── CompositionReelBlock menu → Composition.compose
        │
        └── band FRACTURE → log + ChunkMemory.fractured
```

`/mnemolith inspect` reads the chunk under the command source. `/mnemolith smoke` (gamemaster) clears that chunk, composes a deliberate mismatch on the neighbor chunk, drops a chicken through a fall, explodes, kills the chicken, places a mute stone, checks that a build write is refused, extracts one imprint, and composes the three formulas plus one failure. `/mnemolith perf` (gamemaster) times writes, scoring, a lens walk, and sensor scans on a chunk 48 blocks away. `/mnemolith qa` (gamemaster) runs the survival checklist in [qa-checklist.md](qa-checklist.md) on chunks east of the source, so smoke, perf, and mpsmoke keep their own chunks. The field guide is an item plus static pages; the illustrated screen is opened from `MnemolithClient` and is not loaded on a dedicated server.

### Imprints

`Imprint` is a record: `ImprintTag`, intensity 1–10, origin `BlockPos`, optional player UUID, context hash, and the game time it was written. Tags and weights: death 9, explosion 8, fall 5, fire 4, silence 4, player 2, build 2, redstone 2, path 1. Path is appended so older ordinal ids stay valid. One imprint's pressure contribution is intensity times weight. `MemoryPressure.score` keeps the strongest copy of each tag in full, then adds at most three more copies of that tag at a quarter of their contribution (minimum 1). Further copies are stored and can still be extracted, and they do not add pressure. A moving player writes a path imprint about every 12 blocks. `PathLedger` keeps a 16-step ring buffer in memory and does not allocate a position until the player has moved at least 8 blocks.

`ChunkMemory` is the `chunk_memory` attachment (`ModAttachments`). It stores the imprint list, mute-stone positions, resonator positions, archival-stratum positions, an observatory flag, fractured and archival flags, the last throttled write time, cached pressure, and instability from a failed composition. `resonators`, `strata`, and `observatory` are optional in the codec so older chunks still load. The codec skips empty memory. Mutating the object is followed by `ChunkAccess.markUnsaved()`, even when the clamped score does not move. The imprint list is capped by `gameplay.maxImprintsPerChunk`; the lowest intensity (then the oldest) is dropped. Mute-stone, resonator, and stratum marks stop at 64 per chunk. When a full list loses a mark, the chunk sections that may hold that block are rescanned and untracked blocks fill the free slots, so a chunk with more than 64 mute stones does not unmute while stones remain.

`ImprintWriter` is the only writer. Deaths, explosions, falls, and silence are not throttled. Build and redstone writes wait `gameplay.writeDebounceTicks`. A mute stone blocks writes after it has registered itself. Placing one first writes a silence imprint, then registers, so the death+silence formula can be gathered from an unwitnessed death or from the stone. A piston push only moves the mark: the landing replaces `moving_piston`, and that placement writes no silence. An echo body's death is written once, by `EchoLife.onEchoBodyDied` (DEATH plus `echoDeathPressureSpike`); `ImprintEvents.onDeath` skips `EchoEntity`.

### Memory pressure

`MemoryPressure.score` sums the diminished contributions, instability, and archival bleed (`min(strata, worldGen.archivalBleedCap) * worldGen.archivalBleed`), then clamps to `difficulty.pressureSoftCap`. A vein cannot saturate a chunk by itself: the default bleed cap is 6 and saturated starts at 20. `MemoryPressure.band` maps that score through `difficulty.recollectionStormThreshold` onto calm, saturated, overloaded, and fracture (`gameplay.saturatedThreshold`, `overloadedThreshold`, `fractureThreshold`). Each scaled threshold is capped at `difficulty.pressureSoftCap`, so a large multiplier cannot put fracture out of reach. Recompute runs when memory changes and once in `ChunkEvent.Load` for a chunk that already has memory. It does not scan the dimension. After a change the chunk is always marked unsaved; the load pass marks it only when a quiet imprint faded or the score moved. The lens epoch moves only when the cached pressure or the fractured flag changes. The load pass plays no sound or particles, and a chunk that fractures on load queues its replicant attempt for the end of that server tick. Scoring reuses one server-thread buffer of the strongest copy plus three diminished copies per tag.

Instability cools while a player stands in the chunk. Every `gameplay.instabilityDecayTicks` (default 200), that chunk loses `gameplay.instabilityDecay` (default 1). The same pulse drops one build, redstone, or path imprint older than `gameplay.quietFadeTicks` (default 6000). Chunk load may drop one such imprint as well. Deaths, explosions, falls, fire, silence, and player imprints stay until extracted. The cool mark is not written into the codec. A pulse touches only the chunk under that player, and it returns immediately when that chunk has no instability and no build, redstone, or path imprint. Loading the chunk rebuilds that flag.

Fracture sets `ChunkMemory.fractured`, logs `Mnemolith fracture`, and asks `MobSpawns.trySpawnReplicant` once for that chunk if no replicant is already within 24 blocks. `server.logPressureChanges` logs other band changes. No storm and no Scar are started. `server.allowRecollectionStorms`, `server.maxStormsPerDimension`, `spawnRates.stormAttemptChance`, and `spawnRates.imprintNodeWeight` are reserved for that later step: their comments and tooltips say "Reserved, not used yet", and the two server values are only logged at server start.

`ChunkState` is derived when something asks: fractured, else muted, else archival (set when an imprint is extracted), else normal.

### Extraction and the lens

`ChronicleLensItem` in either hand makes `PressureClient` send `RequestPressurePayload` every `visuals.lensPollInterval` ticks. `PressureSync` answers with `PressureSnapshotPayload` for loaded chunks in a Chebyshev radius of 2, or 3 when the player's chunk has at least one archival stratum. A request that still matches that player's memory epoch, dimension, chunk, and lens or ambient flags does not walk chunks and does not resend the snapshot. Logout and a dimension change drop the saved stamp. The client drops its snapshot when the dimension changes, so a nether chunk with the same coordinates cannot keep showing the overworld band. Vein particles still repeat every `gameplay.veinShimmerTicks` while the lens is held. The snapshot list is capped at 49 chunks. Each entry carries chunk coordinates, pressure, band, and a `ChunkState` ordinal. The same poll, and only while that player holds the lens, sends up to eight `imprint_shimmer` particles to that player on stratum marks within 24 blocks in the surrounding 3×3 loaded chunks. `visuals.ambientWithoutLens` lets the client keep polling and drawing saturated shimmer without a lens. The server sends a full snapshot (`Scope.FULL`) only when the player holds a lens, or when `gameplay.allowAmbientPressure` is true. The client's ambient bit is not permission. Any other request gets a band-only snapshot (`Scope.BANDS`, from `PressureCollector.collectBands`): loaded chunks within Chebyshev 2 whose band is overloaded or fracture, pressure sent as 0 and state as `NORMAL`, no mute scan, and no vein particles. The radius does not grow for archival strata, so the reply does not reveal one. The band-only reply is cached on the same stamp (lens and ambient both false) and is not resent for the same chunk and memory epoch. Vein marks stay lens-only. The attachment is not synced to every player tracking the chunk. `LensOverlay` draws one pill above the hotbar from that snapshot, and only while the lens is held and `visuals.lensOverlay` is on. The pill text is rebuilt when the pressure, band, chunk state, sneak, or numeric toggle changes. Saturated shimmer is drawn from the cached snapshot, so a skipped packet does not stop it, and motes farther than 48 blocks are dropped. Sneak adds the chunk state on a second line. `visuals.showNumericPressure` adds the number. `visuals.overlayOpacity` fades the pill; the band name stays in bone text. Putting the lens away hides the pill. The snapshot stays for the dimension. Saturated shimmer still requires the lens or `visuals.ambientWithoutLens`. While `visuals.pressureVignette`, `visuals.stormScreenShake`, or `visuals.fractureFringe` is on, fracture feel keeps the client sending the same read-only request with the ambient flag and renders from whichever snapshot arrived last, full or band-only. `PressureClient.origin` returns null for a band-only snapshot, so the lens pill has no reading from it; saturated shimmer and the lens chime skip it too. Picking up or putting away the lens sends a request on that tick, and the previous snapshot stays until the answer arrives, so the feel does not blink. Vein marks stay lens-only. Turning all three off, with no lens and no ambient shimmer, stops the poll. A chunk crossing sends the request on that tick instead of waiting out the interval. `ClientParticles.shimmer` draws `imprint_shimmer` on at most four saturated or higher chunks within Chebyshev 1 of the player, two motes each, when `visuals.imprintParticles` is on. `visuals.particleDensity` scales that count, and 0 disables it before the cap. `visuals.maxParticlesPerTick` (default 48) is the hard cap after that scale. `visuals.memoryAudioVolume` scales the local lens chime. World sounds are played by the server.

`ExtractionNeedleItem.useOn` asks `ImprintWriter.extract` for the highest-intensity imprint, stores it as `imprint_cast` on an `ImprintSlipItem`, and spends `gameplay.extractionDurabilityCost` (default 2). `gameplay.extractionCooldownTicks` (default 20) uses the player's item cooldown so a second pull in that window is refused. If the clicked chunk has strata and no local imprint, extract reads one highest imprint from a loaded neighbor chunk and logs `Mnemolith extract reach`. It does not scan blocks.

### Composition

`CompositionReelBlock` opens `CompositionMenu` (three slip slots, player inventory, three data slots). `CompositionScreen` is registered from `MnemolithClient` through `RegisterMenuScreensEvent`. It draws the archival panel and slot frames. The Compose button calls `clickMenuButton`, which runs `Composition.compose` on the server. The result status and formula ordinal are written into the menu's `SimpleContainerData` and synced with the container. The screen shows that line in words. Learned formulas appear as tag-icon silhouettes. With `gameplay.discoveryHints`, unread formulas are a dark bar and a question mark, with no ingredients. Without hints, unread formulas are omitted. Sounds stay on the server. Success and failure each send one `MemoryFx` burst. Stable formulas, matched as a sorted tag multiset:

| Slips | Result |
| --- | --- |
| death + silence | Unrecorded, 300 ticks. `LivingChangeTargetEvent` drops a mob target that is a player with the effect |
| fire + build | Fire Trail, 240 ticks, plus fire resistance. Movement speed gains +0.04, flame particles play, and snow under the player melts |
| fall + player | Landing Burst, 400 ticks. The next landing of at least 2 blocks uses a 0.35 damage multiplier, then the effect is removed |
| silence + player | Archivist bait, given to the player. Dropping it lures an archivist |

A mismatch consumes one slip, adds `gameplay.failurePressureSpike` (default 18) as instability, plays the fail sound, and asks for a moment replicant only when the chunk is then overloaded or fractured. A failed compose on a calm chunk does not spawn one. There is no compose cooldown: the lost slip and the spike are the limiter. `gameplay.compositionEnabled` refuses the attempt without consuming slips. Every attempt logs `Mnemolith compose status={} formula={}`. A spike logs `Mnemolith instability spike amount={} pressure={} band={}`, and a failure also logs `Mnemolith compose fail pressure={} band={} replicantAsked={}`.

### Discovery

`Discovery` is the `discovery` player attachment (`ModAttachments`). It stores a tag bitmask, a formula bitmask, and two first-use flags. The codec fields are optional. Empty progress is not written. `copyOnDeath()` keeps it. `sync` sends it only when the holder is the player being updated, and `syncData` runs after a new bit is set.

`DiscoveryNotes` is the writer. Extracting a slip notes that tag. A composition attempt notes the tags that were in the reel. A successful compose also notes the formula ordinal. Placing a mute stone notes the mute flag once and sends one action-bar line. Opening a reel in a chunk whose memory is already marked observatory does the same for the observatory flag. Nothing scans the world to discover these.

`CatalogFragmentItem.use` checks `gameplay.catalogEnabled` and sends `OpenCatalogPayload` with the bitmasks. `ClientPayloads` opens `CatalogScreen`. The dedicated server never references that screen. The screen prefers the synced attachment when it is already present, and otherwise uses the payload. `gameplay.discoveryHints` may add a count of unread patterns. It does not list them. An archivist theft uses the action bar, the same line as before.

### Mute stone

`MuteStoneBlock.onPlace` and `affectNeighborsAfterRemoval` maintain the mute list, including `/setblock`. `LoadedChunkMemory.isMuted` checks loaded chunks inside `gameplay.muteRadiusChunks` (default 0, this chunk only). An echo strider inside that radius flees. A mute pocket places the same block and calls `LoadedChunkMemory.addMuteStone` on the chunk being generated, because worldgen `setBlock` does not run `onPlace`. The registration is idempotent, so a later place event cannot double-count the stone.

`ResonatorTrapBlock` keeps the same place and remove path for resonator positions. An archivist within 4 blocks of one is stunned. The check reads the stored list on the loaded chunk and its neighbors. It does not scan chests or block columns.

### Mobs

Server-authoritative. Client classes under `client.model` and `client.render` are registered from `MnemolithClient` only.

| Mob | Pressure | Behavior | Counterplay |
| --- | --- | --- | --- |
| Echo strider | Natural spawn at `mobs.echoStriderMinPressure` (default 20, saturated). A path imprint lowers that gate by 8 when `worldGen.striderPathBias` is on. A muted chunk refuses the natural attempt. Charges at overloaded, or when hurt | `PathLedger` waypoints from a player in the same dimension whose oldest buffered step is within 48 blocks, then path and player imprint origins, then the higher-pressure neighbor among the 9 loaded chunks around it. A short phase step crosses non-solid blocks, at most 12 ticks | Mute radius or a mute pocket, or sneak while holding the chronicle lens |
| Archivist | Natural spawn at `mobs.archivistMinPressure` (default 50, overloaded). An observatory flag within 2 loaded chunks lowers that gate by 14 when `worldGen.archivistObservatoryBias` is on | `PlayerContainerEvent.Open`, a tossed slip, or a nearby player holding a high-weight slip. It takes one slip from the open container first, and from that player's inventory only when the container has none, then runs toward a higher-pressure chunk. Cooldown is `mobs.archivistStealCooldown` (default 300 ticks) | Resonator trap, or bait from silence + player |
| Moment replicant | Fracture, a failed composition that leaves the chunk overloaded or fractured, or natural spawn at `mobs.replicantMinPressure` (default 80). The telegraph is 60 ticks | Copies the last melee, jump, block place, or item use from the last 5 seconds, after a telegraph. A copied place becomes `mnemolith:replicated_moment` (no item, no loot table, fades after 200 ticks) in air only, gated by `EventHooks.canEntityGrief`. Fake-player actions (echo hands) are not recorded | Sneak and use the chronicle lens. Anything outside the whitelist is not copied |

A natural attempt is also kept only `mobs.*SpawnWeight` percent of the time. Eggs and `/mnemolith spawn` skip the pressure and weight gates. Biome weights live in `data/mnemolith/neoforge/biome_modifier/memory_mobs.json` (`neoforge:add_spawns` on `#minecraft:is_overworld`). The three types are in the `mnemolith:memory_mobs` entity tag.

About one in ten is a twin: the strider alternates two players' paths, and the replicant alternates two nearby players. Default natural keep-chances are 35, 25, and 12. The strider's charge tell is 45 ticks; its charge step is unchanged. Code drops stay a path slip for the strider, a carried slip half the time plus the loot table for the archivist (it holds one stolen slip at a time, saved with the mob, and does not steal again while holding it), and an unstable slip with a quarter chance of an explosion slip for the replicant. The tables add paper, copper, amethyst, or gunpowder, and the archivist's catalog fragment is one entry in a weighted scrap pool (husk chance 0.25). There is no separate composition experience item.

`/mnemolith mobs` (gamemaster) spawns all three at the command source, starts a strider charge, makes the archivist steal one death slip from a container, and starts a replicant melee telegraph. The log line is `Mnemolith mobs strider={} archivistStole={} replicant={}`.

### Worldgen

All of this is common code. A dedicated server loads it. Nothing under `com.mnemolith.client` is referenced.

| Feature | What it does | Datapack |
| --- | --- | --- |
| Archival vein | A short band of archival stratum in stone or deepslate. Each mark adds bleed when pressure is scored and can extend a lens read. The feature resamples Y, chance, and length from config when it places | `data/mnemolith/worldgen/configured_feature/archival_vein.json`, `placed_feature/archival_vein.json`, `neoforge/biome_modifier/archival_veins.json` (`neoforge:add_features`, `#minecraft:is_overworld`, step `underground_ores`) |
| Mute pocket | A 5×5 cavity lined with mute stone, at least 8 blocks under the surface, only if the floor is stone. Writes are suppressed by the existing mute list. About half of natural pockets include a chest (`loot_table/chests/mute_pocket.json`) | `configured_feature/mute_pocket.json`, `placed_feature/mute_pocket.json`, `neoforge/biome_modifier/mute_pockets.json` (step `underground_decoration`) |
| Chronicle observatory | One rigid template, 11×6×9, on the surface. A composition reel, crafting table, and chest. The chest always rolls a lens or a needle, then two teaching items (tablet, mute stone, reel, silence slip, or player slip). The processor marks the chunk when the reel is placed and logs `Mnemolith observatory` | `worldgen/structure/chronicle_observatory.json`, `structure_set/chronicle_observatory.json`, `template_pool/chronicle_observatory.json`, `processor_list/observatory.json`, `structure/chronicle_observatory.nbt`, `tags/worldgen/biome/has_observatory.json` |

`ObservatoryStructure` delegates to `JigsawStructure` (depth 1, `WORLD_SURFACE_WG`, `ConstantHeight.ZERO`). `worldGen.structuresEnabled` and `worldGen.observatoryEnabled` return an empty generation point, so both are `worldRestart()`. Spacing is the structure set: salt `49031415`, spacing 32 chunks, separation 12. `worldGen.structureSpacing` documents that number. Editing the toml does not move structures. Default vein chance is 8 out of 100. Default mute-pocket chance is 4 out of 100. A pocket chest rolls one of a tablet, a silence slip, or, less often, an extraction needle. Biome tags stay in the datapack because a stock biome modifier cannot read the config spec.

Vein and pocket toggles, chance, and Y are read inside `Feature.place`, so they apply to chunks generated after the config reloads. Natural pockets and veins do not scan the world on a tick. `/mnemolith worldgen` (gamemaster) force-places one vein and one pocket at the command source and logs `Mnemolith worldgen`. `/locate structure mnemolith:chronicle_observatory` and `/place` use the registered structure and placed features.

Strikethrough shafts are not generated.

### Recollection storm

Not started in this phase. The server config and spawn-rate values remain the knobs for it.

## Visual effects

`ModParticles` registers nine `SimpleParticleType` values on both sides: `imprint_shimmer`, `imprint_extract`, `compose_success`, `compose_fail`, `pressure_warn`, `mute_haze`, `strider_trail`, `archivist_snatch`, and `replicant_telegraph`. Sprites are 16×16 white shapes under `assets/mnemolith/textures/particle`. The client tints them. `ClientParticles` registers one `SimpleAnimatedParticle` provider per type from `MnemolithClient`. Those particles are translucent and fullbright.

`MemoryFx` is the only sender. It calls `ServerLevel.sendParticles`, which is the vanilla level-particles packet. There is no second custom FX payload and no per-tick stream. Call sites:

| Moment | Particle | When |
| --- | --- | --- |
| Imprint write | `imprint_shimmer` | Once per accepted write. No extra info log |
| Extract, including a neighbor reach | `imprint_extract` | Once. Logs `Mnemolith fx extract` |
| Compose success | `compose_success` | Once. Logs `Mnemolith fx compose_success` |
| Compose fail | `compose_fail` | Once. Logs `Mnemolith fx compose_fail` |
| Landing burst | `compose_success` | Once, when the effect fires. No extra log |
| Pressure band rises into overloaded or fracture | `pressure_warn` | Eight packets, one ring. Logs `Mnemolith fx pressure`. A saved cache that already matches does not fire it. Saturated alone does not |
| Mute stone newly registered on a `ServerLevel` | `mute_haze` | Once. Logs `Mnemolith fx mute`. Worldgen calls the `ChunkAccess` overload and does not |
| Strider charge | `strider_trail` | Once at `beginCharge`. Flee leaves a trail of two every 10 ticks |
| Archivist steal | `archivist_snatch` | Once in `finishSteal` |
| Replicant telegraph, blind, use, and place | `replicant_telegraph` | Once per action. The old every-4-tick end rod is gone |

The provider is the budget. It drops a particle when `visuals.imprintParticles` is off or `visuals.particleDensity` is 0, when it is more than 48 blocks from the player, at random beyond 24 blocks as density falls, and after `round(48 * density)` custom particles in one client tick. Density 0 therefore disables customs even if the server already sent the packet.

The chronicle lens and imprint slip set `ENCHANTMENT_GLINT_OVERRIDE`. Archival stratum emits light level 7.

`FractureFeel` reads the latest snapshot. The chunk under the player is full strength. Chebyshev 1 is 0.55, Chebyshev 2 is 0.28, and a third chunk (the archival-stratum ring) is 0.12. Overloaded is weight 0.48. Fracture is weight 1. The value eases over a few ticks. `visuals.pressureVignette` blits `textures/misc/pressure_vignette.png` under the hotbar, ink while overloaded and crimson in fracture. `visuals.stormScreenShake` adds a small roll and pitch on `ViewportEvent.ComputeCameraAngles`. The toml name is the old storm knob. No storm is started. `visuals.fractureFringe` runs `post_effect/fracture.json` at `RenderLevelStageEvent.AfterWeather`, the same `PostChain` path as the lens thermal view, and only once the eased weight is a fracture under the player. The fringe strength is baked in the shader. NeoForge 26.2 post uniforms are static JSON values, so the pass is on or off rather than a per-frame uniform. The thermal view skips the fringe so the pink grade stays intact. If the chain fails to load, one warning is logged and the vignette and shake still run. A screen or pause skips the overlay, the shake, and the fringe. Hiding the HUD skips the vignette only. Particles, glint, and stratum light are unchanged. `pressure_warn` still fires once, from the server, when a band rises into overloaded or fracture.

## Performance rules

1. Write an imprint in the event that created it, for that chunk. Do not rebuild imprints by scanning blocks during a tick.
2. Do not iterate every chunk in a dimension, and do not iterate every loaded chunk, on each server tick.
3. Keep a set of dirty chunks. A tick handler, if one is added, drains a bounded number of those chunks and then stops.
4. Recompute pressure when a chunk loads, unloads, or its imprint set changes. Cache the result until the next invalidation.
5. Network payloads carry deltas: one chunk's imprint change, or pressure near the player. They do not send the world's history.
6. The client renders particles, the lens pill, screens, the pressure vignette, screen shake, the fracture fringe, and memory audio from synced state and the client config. The overlay and the fracture feel read the latest snapshot. They do not scan chunks and they do not invent a band. Shimmer uses that snapshot and only the chunks next to the player. The client does not decide whether a storm starts. Custom particles are culled by distance and by `visuals.particleDensity`.
7. Registry work happens while the mod event bus is being constructed. Gameplay ticks do not register content.
8. Respect `maxImprintsPerChunk` and `maxStormsPerDimension` so a single chunk or dimension cannot queue unbounded work.
9. Vein and pocket placement run inside the chunk being generated. Observatory spacing is the structure set (32 / 12). Do not scan the world each tick to find them.

## Registries

Attached to the mod event bus during `Mnemolith` construction.

| Register | Contents |
| --- | --- |
| Blocks | Mute stone, composition reel, resonator trap, archival stratum |
| Items | Chronicle lens, extraction needle, imprint slip, archival tablet, bait, catalog fragment, husk, unstable slip, spawn eggs, block items |
| Features | Archival vein, mute pocket |
| Structure types | Chronicle observatory |
| Structure processors | Observatory (marks the chunk that receives the reel) |
| Block entities | Composition reel |
| Menus | Composition reel (`MenuType`, three slip slots, status data slots) |
| Creative tab | `mnemolith` |
| Sound events | Lens focus, imprint, extract, compose, pressure warn, mute and stratum break/place, and ambient / hurt / death / special for each mob. Each event plays a short mono ogg (`docs/asset-pipeline.md`) |
| Particle types | Shimmer, extract, compose success, compose fail, pressure warn, mute haze, strider trail, archivist snatch, replicant telegraph |
| Effects | Unrecorded, fire trail, landing burst |
| Entity types | Echo strider, archivist, moment replicant |
| Data components | `imprint_cast` |
| Attachments | `chunk_memory` (server only), `discovery` (player, owner sync, copied on death) |

## Config

Specs use `ModConfigSpec.Builder` and are registered with `ModContainer#registerConfig`.

| Type | File | Owner | Sections |
| --- | --- | --- | --- |
| `COMMON` | `mnemolith-common.toml` | both sides, not synced | difficulty, spawnRates, worldGen, gameplay, mobs |
| `SERVER` | `mnemolith-server.toml` | logical server, synced to clients; overridable per world | server |
| `CLIENT` | `mnemolith-client.toml` | physical client only | visuals |

`worldGen.structuresEnabled`, `worldGen.structureSpacing`, and `worldGen.observatoryEnabled` are marked `worldRestart()`. Vein, pocket, bleed, and spawn-bias values are read when a feature places or a mob spawn is tested. Gameplay values, including `catalogEnabled` and `discoveryHints`, are read when an imprint is written, extracted, composed, or when the catalog item is used. Client values are read when the lens polls, when the pill is drawn, when fracture feel draws, and when a custom particle is spawned. `visuals.particleDensity` at 0 and `visuals.imprintParticles` at false both drop those particles. `visuals.ambientWithoutLens` defaults to false and only decides whether the client asks and draws. `gameplay.allowAmbientPressure` defaults to false and is what lets a player receive full snapshots (shimmer, numbers, chunk state) without a lens. Fracture feel does not need it; it runs on the band-only snapshot. `ClientConfig` is still referenced only from `MnemolithClient`.

Read values with `ConfigValue#get()` at the moment of use. Common values are available from common setup onward. Server values are available once the server is starting. Client values are available from client setup onward.

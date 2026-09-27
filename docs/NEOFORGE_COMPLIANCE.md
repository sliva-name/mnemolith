# NeoForge compliance audit

Audit of branch `cursor/production-polish-75df` (and the fixes on top of it) against [NeoForge docs](https://docs.neoforged.net/) for Minecraft `26.2`, NeoForge `26.2.0.88`, ModDevGradle `2.0.147`, Java 25. Mod id `mnemolith`, package `com.mnemolith`.

This is an API and platform check. Gameplay formulas, content ids, packet action ordinals, config keys, command names, and item or block ids were not changed.

Ground truth used:

- [Events](https://docs.neoforged.net/docs/concepts/events/)
- [Sides](https://docs.neoforged.net/docs/concepts/sides/)
- [Registering Payloads](https://docs.neoforged.net/docs/networking/payload/)
- [Data Attachments](https://docs.neoforged.net/docs/datastorage/attachments/)
- [Entities](https://docs.neoforged.net/docs/entities/)

`@EventBusSubscriber` has no `bus` attribute on this line. NeoForge subscribes a handler to the mod bus when the event implements `IModBusEvent`, and to `NeoForge.EVENT_BUS` otherwise.

## Scope

Every class under `src/main/java/com/mnemolith` was reviewed at least at class level. NeoForge and Minecraft integration points (registries, events, networking, attachments, components, entities, worldgen, config, commands, menus, block entities, fake players, game tests, client rendering) were read end to end. Pure local math and string helpers were skimmed.

There is no datagen Java, no mixin config, and no access transformer. Worldgen, loot, recipes, and tags are hand-written datapack JSON. Game tests live in the main source set and register only when `GameTestHooks.isGametestEnabled()` is true.

## Subsystems

| Subsystem | Verdict |
| --- | --- |
| Bootstrap | OK. `@Mod` common entry, `@Mod(dist = CLIENT)` and `@Mod(dist = DEDICATED_SERVER)` splits. Client types stay behind `MnemolithClient`. |
| Registries | OK. `DeferredRegister` / `DeferredRegister.Entities` / `DeferredRegister.Items` / `DeferredRegister.DataComponents` on the mod bus. |
| Events | One HIGH misuse fixed (goals re-added on every join). Other listeners are on the game bus for game events, and they honor `isCanceled()` where they skip work. |
| Networking | OK. Play-phase payloads, version `"3"`, client handlers on `RegisterClientPayloadHandlersEvent`, server checks ownership and range. |
| Persistence | OK. Data attachments and data components. Chunk edits call `markUnsaved()` after in-place mutation. |
| Client | OK. Renderers, particles, keys, GUI layers, and screens register from the physical-client entry. |
| Entities | One HIGH side bug fixed (mite and witness item use). Attributes, spawn placements, and synched data match the current entity APIs. |
| Worldgen | OK. Feature and structure types registered in code; placement, biome modifiers, and templates are datapack JSON. |
| Config | OK. `ModConfigSpec` for common, server, and client. Server type is the one NeoForge syncs. |
| Commands | OK. `RegisterCommandsEvent`. Operator checks use `Commands.LEVEL_GAMEMASTERS.check(source.permissions())`. |
| Datagen | Not used. The `data` run config in `build.gradle` is unused MDK scaffolding. |
| Side safety | Client classes are confined to `com.mnemolith.client` and `MnemolithClient`, except the interaction bug fixed below. |
| Game tests | OK. Gated on `GameTestHooks.isGametestEnabled()`. Namespace `mnemolith` is set on the game-test run. |
| Fake players | OK. Echo edits go through `FakePlayerFactory` and the survival game mode so break, place, and use events fire. Replicant ghost blocks stay on `setBlock` by design. |
| Mixins / ATs | None. |

## Findings

### Fixed in this change

| Severity | Where | What the code did | What the docs say | Status |
| --- | --- | --- | --- | --- |
| HIGH | `EchoThreatEvents.onJoin` | Every `EntityJoinLevelEvent` added two `EchoHuntGoal`s. The same mob instance keeps its `GoalSelector` when it is added to another level, and `addGoal` does not replace an existing goal. | [Events](https://docs.neoforged.net/docs/concepts/events/): gameplay events such as entity-join are posted whenever that action happens, not once per mob. [Entities](https://docs.neoforged.net/docs/entities/) describes goals as state on the mob. | Fixed. A canceled join is ignored, and a mob that already has an `EchoHuntGoal` is left alone. Priorities and selectors are unchanged. |
| HIGH | `LedgerMite.mobInteract`, `KinWitness.mobInteract` | Paper taming, and hush-fiber trust, shrank the stack and rolled state on the logical client as well as the server. Anger and trust are not synched, so a client could consume a fiber the server refused. | [Sides](https://docs.neoforged.net/docs/concepts/sides/): game logic belongs on the logical server (`Level#isClientSide()` is false). Client-side mutation desyncs inventory and stats. `ResidueEntity` and `EchoEntity` already return `SUCCESS` on the client and `SUCCESS_SERVER` after the server work. | Fixed. The client only returns `SUCCESS` so the swing and the use packet still happen. The server performs the shrink, the tame roll, and the gift, and returns `SUCCESS_SERVER`. |

### Owner decisions (follow-up)

#### 1. Unrecorded retarget — fixed

Hook: `com.mnemolith.event.UnrecordedTargetEvent` on `NeoForge.EVENT_BUS` (game bus). Cancel it to keep the target.

`ImprintEvents.onChangeTarget` listens to `LivingChangeTargetEvent` at `LOWEST`. The target that would stick is `getNewAboutToBeSetTarget()` when the event is still open, or the hunter's current mob target / `ATTACK_TARGET` memory when a listener already canceled it. If that entity has Unrecorded, Mnemolith posts `UnrecordedTargetEvent` and, unless an addon cancels that hook, sets the new target to null. A prior cancel is cleared with `setCanceled(false)` so `Mob.setTarget` actually writes the null (`setTarget` ignores the new target while the event is canceled). For `BEHAVIOR_TARGET`, `StartAttacking` does not write when the new target is null, so the old `ATTACK_TARGET` memory is erased when it is the Unrecorded entity.

Addon authors: listen to `UnrecordedTargetEvent`, not only `LivingChangeTargetEvent`, to override Unrecorded. A third-party mod that only cancels or rewrites `LivingChangeTargetEvent` does not win.

#### 2. Direct `setBlock` paths — still intentional

`EchoNav.setOpen` (fence gates), `EchoHands.placeForJob` property fix-up, and `MomentReplicant.placeCopy` still call `Level#setBlock`. Behavior was not changed.

Residual risk: claim and protection mods that only listen to player place, break, or interact. Echo break, place, and chest already go through `FakePlayer`. The replicant path is still gated by `EventHooks.canEntityGrief`.

#### 3. Deprecated call sites — replaced, except `isSolid`

Checked against the Minecraft `26.2` patched sources and NeoForge `26.2.0.88` sources.

| Before | After | Why the behavior stays |
| --- | --- | --- |
| `Entity.hurt(DamageSource, float)` in `MemoryBolt`, `RecallBladeItem`, `ScarBrandItem`, `EchoRoles.strike`, `ArmoryQa` | `LivingEntity.hurtServer(ServerLevel, DamageSource, float)` | `hurt` is `final` and, on a `ServerLevel`, only forwards to `hurtServer`. It is a no-op on the client. `MemoryBolt` now calls `hurtServer` only when the level is a `ServerLevel`, which matches that no-op. Damage amounts are unchanged. |
| `HopperBlockEntity.getContainerAt(Level, BlockPos)` in `EchoWork.container` | `getContainerOrHandlerAt(level, pos, null).container()` | The new method returns a block `Container` first (same chest path, including double chests), then an item capability, then an entity container. Jobs keep the `Container` only, so a capability-only mod inventory is not opened. The existing block-entity gate is unchanged, so a minecart with no block entity is still ignored. |
| `Block.builtInRegistryHolder().tags()` in `MineController` | `BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()` | `wrapAsHolder` returns the registered holder (the same intrusive holder once the block is in the registry). Ore tag matching (`c:ores/…`) is unchanged. `builtInRegistryHolder()` is deprecated with no replacement comment. |
| `BlockState.rotate(Rotation)` in `EchoLesson.Blueprint.placed` | `BlockState.rotate(LevelAccessor, BlockPos, Rotation)` | The deprecated method's own note says to use the level-aware overload. Its default still calls `state.rotate(Rotation)`, so vanilla blocks rotate the same way. Callers (`EchoJob.plan`, build QA, and the client ghost) pass the level they already have. The world position passed in is the anchor plus the rotated offset. |
| `new SoundType(...)` subclass in `MemorySoundTypes` | `DeferredSoundType` | The `SoundType` constructor is deprecated: "Use `DeferredSoundType` instead for suppliers." Break and place still come from the mod `DeferredHolder`s. Step, hit, and fall still come from the vanilla fallback, resolved when played. Volume and pitch still come from that fallback. |
| `PostChain.process(RenderTarget, GraphicsResourceAllocator)` in `FractureFeel` and `ThermalClient` | `FrameGraphBuilder` + `PostChain.addToFrame` + `frame.execute` | `process` is a four-line wrapper around that exact sequence (`MAIN_TARGET_ID`, external target `"main"`, `GraphicsResourceAllocator.UNPOOLED`). |
| `Mob.finalizeSpawn` overrides (`MemoryMob`, `EchoStrider`, `FractureStalker`, `Archivist`, `MomentReplicant`) | Overrides kept. No `EventHooks.finalizeMobSpawn` call was added. | The method is `@Deprecated` `@ApiStatus.OverrideOnly`. The note says external callers should use `EventHooks.finalizeMobSpawn`, which posts `FinalizeSpawnEvent` and then calls `finalizeSpawn`. This mod only overrides the method and calls `super`. There is no external caller to retarget. Calling `EventHooks` from inside the override would post the event again. |
| `ModEntities` class comment | Comment now says the Scar is registered. | `SCAR` is registered as `scar`. The id did not change. |
| `BlockState.isSolid()` in `EchoStrider.stepPhase` | Kept. | Deprecated with no replacement in the 26.2 sources. It returns the private `legacySolid` flag (`forceSolidOn` / `forceSolidOff`, then collision bounds). `isSolidRender()` is full-cube occlusion. An empty collision shape treats signs, fences, and pressure plates (all `forceSolidOn`) as soft, and treats snow and ladders (`forceSolidOff`) differently. NeoForge `FluidUtil` still calls `isSolid()`. Replacing it would change which blocks the strider phases through. |

### Still left as they are

| Severity | Where | What the code did | What the docs say | Status |
| --- | --- | --- | --- | --- |
| LOW | `build.gradle` `data` run | MDK datagen run is configured. There is no `GatherDataEvent` provider. | [Datagen](https://docs.neoforged.net/docs/datagen/) is optional. Hand-written JSON under `src/main/resources/data` is valid. | Left. |
| UNCERTAIN | `EchoEvents.onDeathFirst` | Cancels `LivingDeathEvent` at `HIGHEST` while a player is possessing an echo, then sets health to 1. | The event is cancelable. Highest priority runs before totems and most other mods. | Left intentional. Documented in the method. Pack makers who also cancel death should know this listener runs first. |

## Verified OK

- **Dist split.** `Mnemolith` is the common `@Mod`. `MnemolithClient` is `@Mod(dist = Dist.CLIENT)` and is the only place that touches `net.minecraft.client` outside `com.mnemolith.client`. `MnemolithServer` is `@Mod(dist = Dist.DEDICATED_SERVER)`. Matches [Sides](https://docs.neoforged.net/docs/concepts/sides/).
- **Mod bus vs game bus.** Registry, attributes (`EntityAttributeCreationEvent`), spawn placements (`RegisterSpawnPlacementsEvent`), payloads (`RegisterPayloadHandlersEvent`), and game tests (`RegisterGameTestsEvent`) are `addListener` on the mod bus from the constructor. Gameplay listeners use `@EventBusSubscriber(modid = "mnemolith")`, which is the game bus unless the event is an `IModBusEvent`. `FMLClientSetupEvent` on `MnemolithClient` is an `IModBusEvent`, so it is routed to the mod bus. [Events](https://docs.neoforged.net/docs/concepts/events/).
- **Payloads.** `event.registrar("3")` then `playToServer` (handler on the server) and `playToClient` (codec only). Client handlers register on `RegisterClientPayloadHandlersEvent` and call `enqueueWork`. Default handler thread is the main thread. Sending uses `PacketDistributor` and `ClientPacketDistributor`. Matches [Registering Payloads](https://docs.neoforged.net/docs/networking/payload/). `EchoJobPayload.Action.INVALID` stays last so an unknown ordinal cannot decode as `STOP`. `EchoJob.setRadius` clamps to `ECHO_MINE_MAX_RADIUS` before the value is stored. Snapshot lists are capped with `ByteBufCodecs.list(LENS_CHUNK_LIMIT)`.
- **Attachments.** `DeferredRegister` on `NeoForgeRegistries.ATTACHMENT_TYPES`. Chunk memory serializes when non-empty and is not synced. Discovery, echo progress, and possession use `copyOnDeath`. Discovery and echo progress sync only when `holder == player`. In-place chunk edits call `markUnsaved()`; discovery mutations call `syncData`. Matches [Data Attachments](https://docs.neoforged.net/docs/datastorage/attachments/). No Forge `Capability` or `LazyOptional`.
- **Data components.** `persistent` plus `networkSynchronized` for imprint casts, recordings, lessons, farm lessons, relay links, and vault contents. Item-stack data is not stored as an attachment.
- **Entities.** Every living type that needs attributes is passed to `EntityAttributeCreationEvent`, including echoes, shells, residues, and the Scar. `memory_bolt` is a projectile and correctly has none. Spawn placements use `RegisterSpawnPlacementsEvent.Operation.REPLACE` with `SpawnPlacementTypes.ON_GROUND`. Synched data uses `SynchedEntityData.defineId` and `defineSynchedData`. Save uses `ValueInput` / `ValueOutput`.
- **Block entities.** `BlockEntityType` is constructed with the block it is valid for. The vault ticker is registered only on the server. Load and save use `ValueInput` / `ValueOutput`. `onLoad` only records a position; it does not touch other chunks.
- **Menus.** `IMenuTypeExtension.create`. Screens register on `RegisterMenuScreensEvent` from the client entry.
- **Worldgen.** Features, the observatory structure type, and the processor register on the mod bus. Biome modifiers are `neoforge:add_features` and `neoforge:add_spawns` under `data/mnemolith/neoforge/biome_modifier/`.
- **Config.** `modContainer.registerConfig` for `COMMON`, `SERVER`, and (client entry only) `CLIENT`. Common values are read in `FMLCommonSetupEvent`. Server values are read from `ServerStartingEvent`, not during common setup. Keys go through `SpecValues` with translation keys `mnemolith.configuration.<key>`.
- **Commands.** `/mnemolith` is built in `RegisterCommandsEvent`. Operator subcommands require gamemaster permissions. `inspect` is intentionally available without that check.
- **Game tests.** `MnemolithGameTests.register` returns immediately unless game tests are enabled, so a normal client or dedicated server does not register test functions. `LivePlayers` uses `NetworkRegistry.configureMockConnection` so the fake connection negotiates mod channels.
- **Fake players.** `FakePlayerFactory.get(level, profile)`, forced survival, then `gameMode.destroyBlock` / `useItemOn`. `EchoEvents` records those fake-player breaks and places and does not treat them as the real owner recording. `ActionMemory` ignores `FakePlayer`.
- **Client rendering.** Layers, renderers, particles, key mappings (category then keys), and GUI layers (`RegisterGuiLayersEvent`, `VanillaGuiLayers`) are registered from `MnemolithClient`. `ConfigurationScreen` is registered with `IConfigScreenFactory`.
- **Cancelable events that are used correctly.** Possession cancels death and dimension travel. Imprint and mob listeners bail out when the event is already canceled. Echo recording listens at `LOWEST` and ignores canceled breaks and clicks.

## Verification

First pass: `./gradlew build` succeeded, and `./gradlew runGameTestServer` succeeded with all 26 required tests in 14.13 s.

Owner-decision follow-up: `./gradlew compileJava -Xlint:deprecation` still reports five warnings, all intentional. `MemoryMob.finalizeSpawn` and `FractureStalker.finalizeSpawn` warn on the override and the `super` call (`@ApiStatus.OverrideOnly`; this mod has no external caller). `EchoStrider.stepPhase` still calls `BlockState.isSolid()` because 26.2 has no other reader of `legacySolid`. `build` and `runGameTestServer` for this follow-up are recorded in the next paragraph once they finish.

## Residual risk

Not covered by an in-game play session in this audit:

- Dedicated-server class loading was checked by import boundaries (`net.minecraft.client` only under the client packages and `MnemolithClient`). A full dedicated-server boot was not part of this pass.
- Worldgen JSON was checked against the NeoForge biome-modifier shape. A new world was not generated here.
- Direct `setBlock` paths (fence gates, blueprint property fix-up, replicant copies) are still intentional. The risk is claim and protection mods that only listen to player place, break, or interact. Echo break, place, and chest already use `FakePlayer`. The replicant is still gated by `EventHooks.canEntityGrief`.
- `copyOnDeath` on the possession attachment keeps a saved possession across death; login and respawn listeners clear it. That recovery path was read, not played.

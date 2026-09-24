# Mnemolith architecture

Mnemolith (Мнемолит) is a content mod about memory written into the world. Phase 3 runs the core loop: a world event writes an imprint on one chunk, memory pressure is recomputed for that chunk, and a player can extract a slip or compose slips at a reel. Fracture is a status and a log line. Mobs, the Scar, and structures are not in this phase.

## Identity

The world writes its history into stone. Players read imprints, compose memory, and survive recollection storms. The mod is not an RPG class system, an ore mod, or an automation framework.

Later content targets, not built in this phase:

- the rest of a ~16 block / ~18 item set
- 3 mobs
- 1 boss event (the Scar, during a recollection storm)
- 3 structure types

## Package map

| Package | Side | Role |
| --- | --- | --- |
| `com.mnemolith` | both | `@Mod` entry, mod id, logger |
| `com.mnemolith.common` | both | Names and other types that must load on every side |
| `com.mnemolith.content` | both | Deferred registers for blocks, items, and creative tabs |
| `com.mnemolith.imprint` | both | `Imprint`, `ChunkMemory`, `ImprintWriter`, chunk attachment |
| `com.mnemolith.pressure` | both | `MemoryPressure` and `PressureBand` |
| `com.mnemolith.entity` | both | Effect register. Entity type register stays empty |
| `com.mnemolith.world` | both | `LoadedChunkMemory` and `ChunkState` |
| `com.mnemolith.event` | both | Vanilla listeners and `/mnemolith` |
| `com.mnemolith.network` | both | Lens request and pressure snapshot. Client handler is registered from `MnemolithClient` |
| `com.mnemolith.data` | both | Data component register |
| `com.mnemolith.audio` | both | Sound event register |
| `com.mnemolith.config` | mixed | Common and server specs are common types. `ClientConfig` is referenced only from the client entry |
| `com.mnemolith.client.*` | physical client | Render, particles, audio playback, screens |
| `com.mnemolith.server` | dedicated server | Dedicated-server entry |

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

`/mnemolith inspect` reads the chunk under the command source. `/mnemolith smoke` (gamemaster) clears that chunk, drops a chicken through a fall, explodes, kills the chicken, places a mute stone, checks that a build write is refused, extracts one imprint, and composes the three formulas plus one failure.

### Imprints

`Imprint` is a record: `ImprintTag`, intensity 1–10, origin `BlockPos`, optional player UUID, context hash, and the game time it was written. Tags and weights: death 12, explosion 10, fall 8, fire 6, silence 5, player 4, build 3, redstone 3. Pressure contribution is intensity times weight.

`ChunkMemory` is the `chunk_memory` attachment (`ModAttachments`). It stores the imprint list, mute-stone positions in that chunk, fractured and archival flags, the last throttled write time, cached pressure, and instability from a failed composition. The codec skips empty memory. Mutating the object is followed by `LevelChunk.markUnsaved()`. The list is capped by `gameplay.maxImprintsPerChunk`; the lowest intensity (then the oldest) is dropped.

`ImprintWriter` is the only writer. Deaths, explosions, falls, and silence are not throttled. Build and redstone writes wait `gameplay.writeDebounceTicks`. A mute stone blocks writes after it has registered itself. Placing one first writes a silence imprint, then registers, so the death+silence formula can be gathered from an unwitnessed death or from the stone.

### Memory pressure

`MemoryPressure.score` sums contributions and instability, then clamps to `difficulty.pressureSoftCap`. `MemoryPressure.band` maps that score through `difficulty.recollectionStormThreshold` onto calm, saturated, overloaded, and fracture (`gameplay.saturatedThreshold`, `overloadedThreshold`, `fractureThreshold`). Recompute runs when memory changes and once in `ChunkEvent.Load` for a chunk that already has memory. It does not scan the dimension.

Fracture sets `ChunkMemory.fractured` and logs `Mnemolith fracture`. `server.logPressureChanges` logs other band changes. No storm and no Scar are started. `server.allowRecollectionStorms` and `spawnRates.stormAttemptChance` stay loaded for that later step.

`ChunkState` is derived when something asks: fractured, else muted, else archival (set when an imprint is extracted), else normal.

### Extraction and the lens

`ChronicleLensItem` in either hand makes `PressureClient` send `RequestPressurePayload` every `visuals.lensPollInterval` ticks. `PressureSync` answers with `PressureSnapshotPayload` for loaded chunks in a Chebyshev radius of 2, and writes the current chunk's band to the action bar. The attachment is not synced to every player tracking the chunk. `ClientParticles.shimmer` draws sculk soul particles on saturated and higher chunks when `visuals.imprintParticles` is on, scaled by `visuals.particleDensity`. `visuals.memoryAudioVolume` scales the local lens chime. World sounds are played by the server.

`ExtractionNeedleItem.useOn` asks `ImprintWriter.extract` for the highest-intensity imprint, stores it as `imprint_cast` on an `ImprintSlipItem`, and spends `gameplay.extractionDurabilityCost`.

### Composition

`CompositionReelBlock` opens `CompositionMenu` (three slip slots). The Compose button calls `clickMenuButton`, which runs `Composition.compose` on the server. Stable formulas, matched as a sorted tag multiset:

| Slips | Result |
| --- | --- |
| death + silence | Unrecorded, 200 ticks. `LivingChangeTargetEvent` drops a mob target that is a player with the effect |
| fire + build | Fire Trail, 160 ticks, plus fire resistance. A small movement bonus, flame particles, and snow under the player melts |
| fall + player | Landing Burst, 600 ticks. The next landing of at least 2 blocks uses a 0.2 damage multiplier, then the effect is removed |

A mismatch consumes one slip, adds `gameplay.failurePressureSpike` as instability, and plays the fail sound. `gameplay.compositionEnabled` refuses the attempt without consuming slips.

### Mute stone

`MuteStoneBlock.onPlace` and `affectNeighborsAfterRemoval` maintain the mute list, including `/setblock`. `LoadedChunkMemory.isMuted` checks loaded chunks inside `gameplay.muteRadiusChunks` (default 0, this chunk only).

### Recollection storm

Not started in this phase. The server config and spawn-rate values remain the knobs for it.

## Performance rules

1. Write an imprint in the event that created it, for that chunk. Do not rebuild imprints by scanning blocks during a tick.
2. Do not iterate every chunk in a dimension, and do not iterate every loaded chunk, on each server tick.
3. Keep a set of dirty chunks. A tick handler, if one is added, drains a bounded number of those chunks and then stops.
4. Recompute pressure when a chunk loads, unloads, or its imprint set changes. Cache the result until the next invalidation.
5. Network payloads carry deltas: one chunk's imprint change, or pressure near the player. They do not send the world's history.
6. The client renders particles, the pressure vignette, screen shake, and memory audio from synced state and the client config. The client does not decide whether a storm starts.
7. Registry work happens while the mod event bus is being constructed. Gameplay ticks do not register content.
8. Respect `maxImprintsPerChunk` and `maxStormsPerDimension` so a single chunk or dimension cannot queue unbounded work.

## Registries

Attached to the mod event bus during `Mnemolith` construction.

| Register | Contents |
| --- | --- |
| Blocks | Mute stone, composition reel |
| Items | Chronicle lens, extraction needle, imprint slip, block items |
| Block entities | Composition reel |
| Menus | Composition reel |
| Creative tab | `mnemolith` |
| Sound events | `imprint_write`, `extract`, `compose_success`, `compose_fail` (playback reuses vanilla events) |
| Effects | Unrecorded, fire trail, landing burst |
| Entity types | Empty |
| Data components | `imprint_cast` |
| Attachments | `chunk_memory` |

## Config

Specs use `ModConfigSpec.Builder` and are registered with `ModContainer#registerConfig`.

| Type | File | Owner | Sections |
| --- | --- | --- | --- |
| `COMMON` | `mnemolith-common.toml` | both sides, not synced | difficulty, spawnRates, worldGen, gameplay |
| `SERVER` | `mnemolith-server.toml` | logical server, synced to clients; overridable per world | server |
| `CLIENT` | `mnemolith-client.toml` | physical client only | visuals |

`worldGen.structuresEnabled` and `worldGen.structureSpacing` are marked `worldRestart()`. Gameplay values are read when an imprint is written, extracted, or composed. Client values are read when the lens polls. `ClientConfig` is still referenced only from `MnemolithClient`.

Read values with `ConfigValue#get()` at the moment of use. Common values are available from common setup onward. Server values are available once the server is starting. Client values are available from client setup onward.

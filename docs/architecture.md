# Mnemolith architecture

Mnemolith (Мнемолит) is a content mod about memory written into the world. Phase 2 is the project skeleton: registries, config, and the side split. Gameplay is not implemented. This document is the contract for the modules that come next.

## Identity

The world writes its history into stone. Players read imprints, compose memory, and survive recollection storms. The mod is not an RPG class system, an ore mod, or an automation framework.

Later content targets, not built in this phase:

- about 16 blocks
- about 18 items
- 3 mobs
- 1 boss event (the Scar, during a recollection storm)
- 3 structure types

## Package map

| Package | Side | Role |
| --- | --- | --- |
| `com.mnemolith` | both | `@Mod` entry, mod id, logger |
| `com.mnemolith.common` | both | Names and other types that must load on every side |
| `com.mnemolith.content` | both | Deferred registers for blocks, items, and creative tabs |
| `com.mnemolith.imprint` | both | Chunk-scoped imprints |
| `com.mnemolith.pressure` | both | Memory pressure derived from loaded imprints |
| `com.mnemolith.entity` | both | Entity type register |
| `com.mnemolith.world` | both | Loaded-chunk memory tracker |
| `com.mnemolith.event` | both | World and server events that write imprints |
| `com.mnemolith.network` | both | Payload registration |
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

## Future loop

```text
world event in a chunk
        │
        ▼
   write Imprint on that chunk
        │
        ▼
   recompute Memory Pressure for loaded, dirty chunks
        │
        ├── player extracts / composes memory
        │
        └── pressure crosses the threshold
                │
                ▼
        Recollection Storm (Scar boss event)
```

### Imprints

An imprint is a tagged memory attached to one chunk. A world event writes it: a block change, a death, a structure piece, a player action. The imprint records what happened and the tags later composition will read. Storage belongs to the chunk that changed. Phase 2 has the package and no storage.

### Memory pressure

Pressure is a number derived from the imprints currently loaded, compared with `difficulty.recollectionStormThreshold` and `difficulty.pressureSoftCap`. The logical server decides whether a storm starts. `server.allowRecollectionStorms` and `server.maxStormsPerDimension` cap that decision. Clients display the result.

### Composition

Composition is a player action: extract imprints, then combine them. `gameplay.compositionEnabled` and `gameplay.maxImprintsPerChunk` are the first knobs. The action has a fixed cost. It does not search the world for matching memories.

### Recollection storm

When pressure stays above the threshold, the server may start a recollection storm and, later, the Scar. `spawnRates.stormAttemptChance` is the chance that a pressure check makes the attempt. The storm is a scheduled event with a cap, not a search across every entity.

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

All of these are attached to the mod event bus during `Mnemolith` construction. They are empty on purpose.

| Register | Factory |
| --- | --- |
| Blocks | `DeferredRegister.createBlocks` |
| Items | `DeferredRegister.createItems` |
| Creative tabs | `DeferredRegister.create(Registries.CREATIVE_MODE_TAB, …)` |
| Sound events | `DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, …)` |
| Entity types | `DeferredRegister.createEntities` |
| Data components | `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, …)` |

## Config

Specs use `ModConfigSpec.Builder` and are registered with `ModContainer#registerConfig`.

| Type | File | Owner | Sections |
| --- | --- | --- | --- |
| `COMMON` | `mnemolith-common.toml` | both sides, not synced | difficulty, spawnRates, worldGen, gameplay |
| `SERVER` | `mnemolith-server.toml` | logical server, synced to clients; overridable per world | server |
| `CLIENT` | `mnemolith-client.toml` | physical client only | visuals |

`worldGen.structuresEnabled` and `worldGen.structureSpacing` are marked `worldRestart()`, so a world reload picks up the new value. Other values are read when the later system uses them. Phase 2 logs a few of them at startup and does not apply them to the world.

Read values with `ConfigValue#get()` at the moment of use. Common values are available from common setup onward. Server values are available once the server is starting. Client values are available from client setup onward.

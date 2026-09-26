# Multiplayer

Mnemolith on a dedicated server is server-authoritative. Clients send a lens request and receive a snapshot. They do not write imprints, extract, or compose on their own. NeoForge **26.2.0.88** or a newer 26.2.x build is required on the server and on every client. The same mod jar has to be on both. There is no Velocity or proxy support.

## What the server owns

| Action | Who decides |
| --- | --- |
| Imprint write, mute, extract | `ImprintWriter` on the server. A mute stone blocks every writer in its chunk, whoever placed it |
| Pressure and band | Cached on the chunk. Both lenses read that cache |
| Compose | `Composition.compose` when the open menu's button is clicked. A closed menu, a removed player, or a disconnected player is ignored |
| Discovery | Attachment on that player, `copyOnDeath`, synced only when the holder is that player |
| Replicant | One server entity. A replicant already within 24 blocks, or anywhere in that chunk's column, blocks another spawn |
| Archivist steal | One slip. An open container is taken before the player's inventory, so both players viewing that container see the same removal |

Payloads are registered as version `1` in `ModNetwork`:

| Payload | Direction | What it carries |
| --- | --- | --- |
| `mnemolith:request_pressure` | client to server | One boolean, ambient or lens. The server answers only for a live player who holds a lens, or who asked for the ambient read (shimmer without a lens, or fracture feel). It does not write memory |
| `mnemolith:pressure_snapshot` | server to that player | Up to 49 nearby chunks. A repeat is skipped when the memory epoch, dimension, chunk, and lens or ambient flag are unchanged |
| `mnemolith:open_catalog` | server to that player | That player's tag and formula bits. The screen class is client-only |

Logout and a dimension change drop the saved lens stamp. The client also drops its snapshot when the dimension changes, so matching chunk coordinates in another dimension cannot keep the previous band on screen. Overworld teleports already miss the stamp because the chunk coordinates are part of it.

Particles and sounds for writes, compose, pressure, and mute use the vanilla nearby-player send. Vein shimmer from a lens poll goes to the player who asked.

## `/mnemolith mpsmoke`

Gamemaster. It uses two `FakePlayer`s in the dedicated process, 96 blocks from the command source, so `/mnemolith smoke` and `/mnemolith perf` keep their own chunks. One line is logged:

```
Mnemolith mpsmoke sameBand=true discoveryIsolated=true steal=true reel=true muteBlocks=true replicants=true guarded=true
```

That line is the dedicated run of this command. The same server then ran `/mnemolith smoke` and still logged `pressure=75 band=OVERLOADED muted=true writeBlocked=true compose=3`, with the quiet fail at 18 (no replicant), the fall at 10, the explosion at 34, the mute at 77, three successful composes, and the loud fail at 89.

| Flag | What it checks |
| --- | --- |
| `sameBand` | Two lens walks of the same chunk agree with the cached band. An explosion imprint is saturated |
| `discoveryIsolated` | The player who extracts and composes learns the tag and the formula. The other player's bits stay empty |
| `steal` | The archivist takes the slip in the open reel and leaves a heavier slip in that player's inventory |
| `reel` | Composing one reel does not empty the other |
| `muteBlocks` | A mute stone blocks a build write from the second player |
| `replicants` | The fracture write spawns one replicant. The loud fail and a second ask do not spawn another |
| `guarded` | The compose button does nothing while the menu is not the player's open menu, then consumes one slip once, then does nothing after it is closed |

This is one server process. It does not open two Minecraft clients, so it does not prove a dropped TCP packet or two independent renderers. The catalog screen, the lens pill, and particle rendering stay client-only and were not driven from a second window in this check.

## Hosting

- View distance 8 to 10 is enough. The lens packet is a radius of 2 chunks, or 3 with archival strata, capped at 49 chunks.
- Leave `visuals.particleDensity` at 1 and `visuals.maxParticlesPerTick` at 48. Density 0 on a client silences that client's custom particles only.
- `mobs.sensorInterval` defaults to 10. A charge still aims every tick.
- Recommended for a small server: `server.logPressureChanges` off unless you are reading the log. Imprint lines are still logged on each write.
- The dedicated log must contain `Mnemolith dedicated server setup` and must not contain `Mnemolith client setup` or `com.mnemolith.client`.

## Known limits

- No Scar, no new dimension, and no proxy-specific handshake.
- Ambient lens reads trust the client's boolean. They are read-only. A client cannot create an imprint by sending a payload.
- Two players can share one reel. They see the same three slots, the same way they would share a chest. Separate reels do not share slots.
- Disconnect closes the menu. Slips stay in the reel. A button packet for a menu that is no longer open does not compose.
- `mpsmoke` does not connect a second game. A two-client check is still a manual join of the same dedicated server.

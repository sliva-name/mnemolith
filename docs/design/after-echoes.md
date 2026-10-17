# After-echoes (послезвучия видений)

After-images (memory feel stage 3) were silent and uniform. Now each burst remembers *what kind* of memory it stands for.

## Sound

- New sound events `mnemolith:memory.echo.<tag>` for all 15 imprint tags, built from existing samples in
  `sounds.json` with their own pitch/volume (explosion = `storm_wave` at 0.4, death = `faded_death` at 0.55,
  path/player = `echo_step`, build = `stratum_place`, forgotten = `hollows_mood`, …). RU/EN subtitles
  `subtitles.mnemolith.memory.echo.<tag>`.
- Playback is client-side: the after-image particles only ever reach the viewing player, and the particle
  provider plays the replay once per burst and kind (25-tick throttle per block). Only the viewer hears it.
- Client toggle `memoryAfterEchoes` (default on), beside `memoryAfterImages`.

## Motion (AfterEcho.Motion)

| Kinds | Motion |
| --- | --- |
| explosion, boss, portal | RIPPLE — motes expand in a ring |
| death, fall, forgotten | SINK — the column sinks |
| path, player, sculk, silence | GROUND — footprints fade along the ground |
| build, redstone | OUTLINE — motes trace the block's edges and shrink away |
| fire, lightning, trade | COLUMN — the classic rising column |

## No protocol change

The server sends each mote as a count-0 particle packet, whose velocity reaches the client exactly;
the x-velocity carries the tag ordinal (`AfterEcho.code`). The client decodes it for motion and sound.
Mote count per burst is unchanged (COLUMN × up to 3), so the particle budget and perfqa envelope hold.

## Checks

`/mnemolith afterechoqa` (also a GameTest): all 15 sound events + subtitles + toggle lang registered,
motion mapping for the headline kinds, bursts dispatched and tags recorded for a still player.
Sound and motion themselves are by-ear/by-eye: visual tour scenes `afterecho_<kind>`.

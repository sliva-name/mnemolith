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

## Polish (after #111)

- Build outline: motes now sit strictly on the block's 12 edges, size 0.34, life 115 ticks; the dissolve
  (shrink + fade) runs over the second half. Same mote count, budget unchanged.
- Sound levels, checked programmatically (`afterechoqa` "levels"): every `memory.echo.*` sample has
  volume ≤ 0.45 and pitch ≤ 0.85, against the shared cues at volume 0.45–0.8 / pitch 0.7–1.46 — and the
  client plays echoes at 0.55× on top, so an echo is always quieter and lower than the live event.
  Outliers fixed: explosion 0.6 → 0.45, fire's second sample pitch 1.2 → 0.8.
  Values: explosion storm_wave .40/.45 · death faded_death .55/.40 · build stratum_place .55/.40 ·
  path echo_step .65/.35 · player echo_step .80/.25 · fire imprint_write .50/.30 + storm_wave .80/.12 ·
  fall echo_hurt .45/.30 · portal relay_hop .45/.35 · silence mute_place .45/.30 · lightning
  replicant_telegraph .50/.35 · sculk mite_ambient .45/.30 · boss scar_ambient .45/.35 ·
  trade vault_chime .65/.30 · redstone lens_focus .55/.25 · forgotten hollows_mood .55/.30 (pitch/volume).
- Visual tour: toasts are cleared every tick in tour mode (advancement toasts no longer slip into shots),
  and afterecho scenes stage low-intensity imprints so the chunk stays saturated, not overloaded
  (no residues or vignette in frame).

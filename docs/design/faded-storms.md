# Faded storms (a storm nobody witnesses)

Status: implemented on `feature/faded-storms`.

## Problem

A recollection storm only ticks while its centre chunk is loaded. Until now a storm whose centre unloaded (the player
fled, teleported, slept in a far bed, or logged out in multiplayer while others played elsewhere) froze in
`StormData` forever. With the default `maxStormsPerDimension = 1` that one forgotten storm blocked every other storm
in the dimension, for every player, until someone walked back to it. A storm whose last wave was done but whose
residues sat in an unloaded chunk also waited forever to resolve. Both were listed under README known issues.

## Design

The world remembers what is witnessed. A storm with no witness does not merge into a Scar; it scatters.

- A storm counts **unwatched ticks** while its centre chunk is not loaded, or while it has finished its waves but is
  waiting for storm residues in unloaded chunks. Any tick with the centre loaded and nothing to wait for resets the
  count. The count is saved with the storm (`unwatched`), so restarts do not reset it.
- At `stormUnwatchedTicks` (server config, default 2400 = 2 minutes; 0 keeps the old freeze) the storm ends as
  **FADED**: it leaves `StormData` at once, so the dimension's storm cap frees. A faded storm never raises a Scar.
  Its residues fall back to ordinary residues the next time their chunk loads (they already drop a storm id that is no
  longer active).
- The storm **takes the noise with it**. The faded centre is remembered (`StormData.faded`, saved). The first time
  that centre chunk is loaded again, each loaded chunk of its 3x3 area loses half its instability. Players within 48
  blocks get «Здесь прошла буря без свидетелей: в округе стало тише.» with a soft amethyst sound and a few motes. A
  fracture usually drops out of the fracture band, so the player does not walk back into an instant second storm.
- The field guide page on storms gains one line, and the old README known issue is removed.

Not changed: storms near players behave exactly as before, mute stones and wards, waves, the Scar.

## Verification

- `stormqa` gets a `fadesUnwatched` check (unloaded centre fades after the configured ticks, the cap frees, the
  settle halves instability on load) and goes to 20/20.
- A GameTest drives a storm with an unloaded centre to FADED and checks the cap and the settle.
- In game: start a storm, walk away so the centre unloads, wait, check that the log says FADED and a new storm can
  gather elsewhere, then come back and see the settle message and the lower pressure on the lens.

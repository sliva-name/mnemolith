# The storm lull

Status: implemented on `feature/storm-lull`. Depth for recollection storms, no new item or block.

## Lore

A storm that has nothing left to recall, or that was held, leaves the air spent. Memory needs time to gather again
before it can rise in a storm.

## Rule

- When a storm ends **spent** (every storm residue gone) or **contained**, it leaves a lull for `Storms.LULL_TICKS`
  (24000 game ticks, one Minecraft day) around its centre chunk.
- The lull covers the storm-spacing range (`2 * AREA` chunks from the centre, the same range that keeps two storms
  apart). While it lasts the storm gate answers `LULL`: no natural storm, and a freed residual shard does not call one.
- A storm that **passed** (residues left standing), merged into the Scar, or faded unwatched leaves no lull: the work
  was not done.
- Players in range read `mnemolith.storm.lull` in chat (RU/EN).
- Saved in `mnemolith_storms.dat` (`lulls`: dimension, chunk, end time); expired lulls are dropped when a new one is
  added; at most 64 are kept.

## Feedback

A residual shard freed in a lulled chunk does not call a storm, and the player reads `mnemolith.storm.lulled` over the
hotbar: how many minutes are left (rounded up, at least 1; 20 for a full day). The storms page of the field guide
explains the lull in RU and EN. Added in PR #70.

## Config

`server.stormLullTicks` (`mnemolith-server.toml`, default 24000, 0 to 168000) sets the lull length. 0 turns lulls off:
no lull is recorded and no lull message is sent; lulls already recorded keep their end time. Added in PR #72.

## Balance

It rewards clearing a storm properly with a day of quiet, so a base in a loud area is not hit by storm after storm,
while a player who wants storms (for residues and shards) can walk to another fracture. Muting and scar glass are
unchanged and still the permanent answers.

## QA

- `/mnemolith lullqa` and `mnemolith:suite_lullqa` (13 checks).
- Live `storm_lull_refuses_shard`: a real player in a fracture; a storm there is spent; the residual shard the player
  then frees through the real item use calls no storm.
- Manual: the lull message in RU and EN after clearing a storm, and no new storm in that area until the next day.

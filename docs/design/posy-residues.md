# The posy and residual echoes

Status: implemented on `feature/posy-residues`. A missing interaction between two systems: the remembrance posy (PR #67)
laid only faded to rest, while residual echoes, the other half-forgotten memories, could only be read and captured,
fed or starved.

## Lore

A residue is a memory that came loose. Someone remembering it kindly is enough for it to settle.

## Rule (`RemembrancePosy.offerResidue`)

- Right-click a residual echo, ordinary or storm, with a posy: it is laid to rest at once. No lens read is needed.
- It leaves nothing: no shard, no slip, no imprint written back. One posy is spent (not in creative).
- A storm residue is dropped from its storm (one fewer toward the Scar; with none left the storm can end spent and
  leave its lull).
- A legendary residue (one of your own living memories) refuses, and the posy is kept.
- Overlay lines `mnemolith.posy.residue_rest`, `storm_residue_rest`, `residue_refuses` (RU/EN); the posy guide page
  explains it.

## Balance

It trades reward for safety. The needle on a read residue gives a shard; the posy gives nothing but needs no 3-second
read under lashes, so it is the calm way out of a storm going badly. Posies cost three forget-me-nots from Memory
Hollows and a string each, which keeps it from replacing mute stones or the needle.

## QA

- `/mnemolith posy2qa` and `mnemolith:suite_posy2qa` (8 checks).
- Live `posy_rests_residue`: a real player right-clicks a residue with a posy through the real entity interaction.
- Manual: the overlay lines in RU and EN; laying storm residues to rest during a real storm until it is spent.

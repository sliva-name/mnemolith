# The chronicle transcript

Status: implemented (PR #68). A multiplayer addition to discovery and the catalog
([../multiplayer.md](../multiplayer.md)): each player's discovery is private and synced only to them. Until now there was
no way to share it except by doing every extraction and formula again.

## Lore

The observatories kept copies; chroniclers copied each other's notes. A transcript is one sheet of those notes,
signed.

## Item

- `mnemolith:chronicle_transcript`: paper, an ink sac and a feather, shapeless. Stacks to 16. The recipe unlocks once
  you hold a catalog fragment. Blank and written sheets have their own textures (`minecraft:has_component` on
  `mnemolith:transcript`). Field guide page `transcript` right after `catalog` (`PAGE_COUNT` 44). The reference page
  grid leaves the two sheets out; it is full.
- Data component `mnemolith:transcript` (`Transcript`): tag bits, formula bits, author name.

## Use (`TranscriptItem`)

- **Blank:** copies your discovery (the tag and formula bits your catalog draws) onto one sheet signed with your name.
  One sheet is split off a stack; the rest stays blank. With nothing discovered it writes nothing.
- **Written:** a reader learns every tag and formula on it they did not know yet (through `DiscoveryNotes`, so it
  syncs to them only) and the sheet is used up. If nothing is new (the author, or someone who knows it all), it is
  kept. Creative keeps it either way.
- Refused when `catalogEnabled` is off, like the catalog fragment.

## Balance

Knowledge only: it fills in catalog entries and the reel's hints. Slips, shards, grafts and the mute and observatory
first-time notes are not shared. It saves a newcomer the reading, not the work. A wrong composition still costs
slips and pressure.

## Test hardening in this change

The live tests now take fixed sites (`LivePlayers.liveSite`) instead of sites relative to their own origin, which
moved with the test layout and could overlap. Tests that check imprint writes lift mutes in the mute radius first
(`LivePlayers.unmute`). `pickaxe_quiet_in_silent_chunk` breaks its second stone without moving: a teleport into that
chunk could write a path imprint whose write pause held back the build imprint (seen locally: 2 failures in 6 runs
before, 0 in 6 after).

## QA

- `/mnemolith transcriptqa` and `mnemolith:suite_transcriptqa` (12 checks) with two fake players.
- Live `transcript_shared_between_players`: two real players; the author writes through the real item use, the reader
  reads it and ends with exactly the author's discovery; the sheet is spent.
- Manual: the blank and written textures and the tooltip (author and counts), and the messages in RU and EN.

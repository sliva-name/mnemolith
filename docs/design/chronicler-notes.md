# The chronicler's copied notes

Status: implemented on `feature/chronicler-transcript`. Depth for the chronicle transcript (PR #68) and the chronicler
villager: no new item, block or texture.

## Lore

Chroniclers copied each other's notes. A journeyman sells a sheet of the oldest ones: what the body remembers.

## Trade

- `mnemolith:chronicler/3/emerald_and_paper_chronicler_transcript`, in the journeyman (level 3) pool next to the lens
  and the catalog fragment. The pool still draws 2, so a chronicler offers it about two times in three.
- 10 emeralds and a paper for a written chronicle transcript, 2 uses per restock, 15 xp.
- The sheet holds the five body tags (fire, fall, death, explosion, silence; `Transcript.CHRONICLER_TAGS` = 55), no
  formula, and the author `#chronicler`. No player name starts with `#`; the tooltip and the read message show the
  chronicler profession name (RU/EN) instead.

## Balance

Knowledge only, like any transcript: catalog entries and reel hints, not slips. The body tags are the first ones a
player meets, so this saves a solo player the reading, not the work. Formulas stay something to find or be taught by a
friend.

## QA

- `/mnemolith chroniclerqa` and `mnemolith:suite_chroniclerqa` (9 checks).
- Live `chronicler_sells_notes`: a real player and a real journeyman chronicler in the world; the trade is taken
  through the offer and the player reads the sheet through the real item use.
- Manual: buy one from a chronicler in the trading screen; the tooltip shows "Chronicler"/"Хронист" as the author.

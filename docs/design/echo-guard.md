# Echo guard (the post job)

Status: shipped in #56 (first stage). Stage 2 (bows, shields, escort): [echo-guard-2.md](echo-guard-2.md).

## Problem

Echoes mine, build, farm, chop and tend, but the world is hostile and every working echo only runs away. Players asked
for a way to keep a small spot safe (a farm, a doorway, a camp at night) without a full iron golem build. An echo that
remembers how its owner fought is the natural fit: the mod is about memories doing work.

## Design

**Lesson.** While a recording runs, every melee hit the player lands on a hostile mob (`Enemy`) is counted, with the mob
type and the kills. A recording with at least 2 such hits carries a **guard lesson** (`echo_guard` data component:
foes, hits, kills). Hits on players, villagers, animals or tamed pets are never counted. The recording tooltip shows
«Охрана: зомби, скелет».

**Job.** The echo screen gets a «Охрана» button (enabled when the echo has the lesson). The post is where the echo stands
when the job starts (`workAnchor`). The guard:

- needs a melee weapon (any item with an attack damage modifier, i.e. swords, axes, tridents, maces) in its
  inventory; it takes the best one into its hand. No weapon: stop «нет оружия». Every landed hit costs the weapon one
  durability point; a broken weapon falls back to the next one or stops with «инструмент сломался».
- picks the nearest valid foe within the **post radius** `min(job radius, echoGuardRadius = 8)` of the post, walks to
  it, and hits it every `echoGuardAttackTicks = 15` ticks for `weapon damage × echoGuardDamageScale (0.75)`. It never
  chases further than the radius + 3 from its post, and walks back when the area is clear («Охрана: на посту»).
- **fights back** instead of fleeing: the guard is the one job the attack alarm does not interrupt. A mob that hurts
  it becomes its target if it is near the post.
- **valid foes**: living `Enemy` mobs only. Never players (the owner or anyone else), never villagers, animals,
  passive mobs, anything tamed or owned, other echoes or this mod's bosses (the Scar), the Wither and the Ender
  Dragon. Creepers are skipped (echo work never causes terrain damage; the same rule hunting mobs already follow).
  Neutral mobs (endermen, zombified piglins, …) count only while they are angry at the owner or the echo.
- the damage source is the echo itself (`mobAttack`), with no sweep, so a hit can never land on anyone else.

**Balance vs the iron golem.** The golem has 100 HP, hits for 7.5–21.5 every 10 ticks and throws mobs. The guard keeps
the echo's 20 HP (plus Recall bonus), hits for 4.5 with an iron sword (5.25 diamond, 6 netherite) at most every
0.75 s, spends weapon durability, needs a recording, a weapon and an echo slot, and only covers 8 blocks around its
post. A guard beats a lone zombie or skeleton; a wave wins against it. It is a helper, not a fortress.

**Grafts.**

- *Grave* (decoy): hostile mobs already pick a grave echo over its owner. A grave guard also **pulls aggro**: when it
  strikes a mob that is after someone else (its owner, another player, a villager), the mob turns to the echo. Each hit it takes costs a charge (as before).
- *Volatile*: hits **×1.5**. Each landed hit costs one charge.
- *Hushed*: mobs do not notice it (as before), so it **approaches unseen**; a hit on a mob that is not targeting the
  echo is a hushed strike for ×1.5 and costs one charge.

**Death and return.** Unchanged echo rules: a guard that dies drops its items (including the weapon) where it fell and
its owner gets the usual echo-death handling (the slot frees, the death is reported). Lens orders (stay, follow,
return) pause the guard like any job; RETURN walks back to the post.

**Advancement.** «На посту» (`echo_guard`): an echo guard kills a hostile mob for you.

## Out of scope (later stages)

Ranged weapons (bows/crossbows), shields and armor use, patrol routes between posts, guarding a moving owner.

## Verification

- `guardqa` suite: lesson analysis, target filter (owner, other player, villager, cow, tamed wolf, creeper, zombie),
  weapon durability use, damage scale and graft multipliers, post leash, fight-back instead of flee.
- A live GameTest: a guard with a sword kills a zombie next to its post and spares a villager and a cow.
- In game (headless client): summoned zombies and skeletons, fighting, the radius, durability, grafts, death.

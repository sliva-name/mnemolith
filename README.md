# Mnemolith (Мнемолит)

The world writes its history into stone. Read imprints, compose memory, survive recollection storms.

This repository is **Phase 12**: the core memory loop, three mobs, three worldgen features, the archival interface, budgeted memory particles, a shared art pass, a balance pass, a dedicated-server performance pass, and a multiplayer pass on NeoForge 26.2. World events write imprints, chunks accumulate memory pressure, and a player can extract and compose a small set of formulas. Echo striders, archivists, and moment replicants use that pressure. Archival veins, mute pockets, and chronicle observatories feed the same systems. The chronicle lens draws a pressure pill, the composition reel has its own screen, and a catalog fragment remembers what you have learned. Writes, extracts, compose results, pressure warnings, mute stones, and mob tells each have their own particle. `visuals.particleDensity` set to 0 turns those particles off. A fractured chunk can desaturate the screen with one cheap fringe pass; there is no new biome and no Scar. Phase 12 adds the catalog-fragment recipe, a craftable illustrated field guide, `/mnemolith qa`, higher-contrast catalog and reel text, and a pass on the key item textures and sounds.

| | |
| --- | --- |
| Minecraft | Java Edition 26.2 |
| NeoForge | 26.2.0.88 or newer 26.2.x |
| Java | 25 |
| Mod id | `mnemolith` |
| Package | `com.mnemolith` |
| License | All Rights Reserved (`LICENSE`). The NeoForge MDK template files remain under `TEMPLATE_LICENSE.txt`. |

Mappings are Mojang's official names. The Gradle project is the [ModDevGradle 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle).

## Install

1. Install Minecraft Java Edition **26.2**.
2. Install **NeoForge 26.2.x** for that version (this project is built against 26.2.0.88).
3. Download `mnemolith-<version>.jar` from the project releases, or build it (see below).
4. Put the jar in the `mods` folder of that NeoForge instance.
5. Launch the game. Mnemolith appears in the mod list as **Mnemolith**.

The mod has no required dependencies beyond Minecraft and NeoForge.

## Gameplay

A fresh world should show a first imprint within the first minute of walking, and a first slip plus one successful compose inside about **10–20 minutes** of ordinary play. Walking writes a path imprint about every 12 blocks. The first place or break writes a build imprint after the chunk's write pause (4 seconds between build and redstone writes). A death or a creeper reaches the saturated band. Building a base, by itself, stays calm. Numbers and the reason for each default are in [docs/balance.md](docs/balance.md).

1. Deaths, explosions, long falls, and block changes write **imprints** on the chunk where they happened. Build and redstone writes are throttled. An unwitnessed death also writes silence. Placing a **mute stone** writes silence, then blocks further writes in its chunk.
2. Each chunk's **memory pressure** is the strongest copy of each tag, plus a small share of repeats, plus instability from a failed composition, clamped by the soft cap. Instability cools while you stand in the chunk. Old build, redstone, and path imprints fade. Loud imprints stay until you extract them. Bands are calm, saturated, overloaded, and fracture. Fracture is logged. It does not start a storm. Standing in an overloaded or fractured chunk, or in the chunk next to one, darkens the screen edge and shakes the camera. Fracture also drains the color. The lens is not required: without it the server sends a band-only read (overloaded and fracture chunks within two chunks, no numbers). The three toggles are `visuals.pressureVignette`, `visuals.stormScreenShake`, and `visuals.fractureFringe`.
3. Hold a **chronicle lens** to see a pressure pill above the hotbar: the band name, and the number unless you turn that off. Sneak to add the chunk state (clear, muted, archival, or fractured). Saturated chunks nearby still shimmer. Put the lens away and the pill is gone.
4. Use an **extraction needle** on a block in that chunk. The strongest imprint becomes an **imprint slip**, and that tag is added to your catalog.
5. Put slips in a **composition reel** and press Compose. Learned patterns show as tag icons. Unread ones stay a question mark, or stay hidden if discovery hints are off. Success and failure are written on the screen. The server decides the result.
6. Craft a **catalog fragment** (paper, amethyst shard, and ink sac, stacked) and use it to open the tags and patterns you have already learned. An observatory chest can also hold one. The screen shows only what that player has already discovered. Craft a **field guide** (a book over an amethyst shard) and use it for twenty-one illustrated pages: the first hour, imprints, bands, crafts, formulas, a defense for each mob, worldgen, echoes, recording and possession, memory grafts, and multiplayer. The pictures are drawn by the client. The dedicated server only registers the item. An observatory chest can hold the book as well.

| Slips | Result |
| --- | --- |
| Death + silence | Unrecorded, 15 seconds. Mobs lose you as a target |
| Fire + build | Fire trail for 12 seconds, fire resistance, and a small speed bonus. Snow underfoot melts |
| Fall + player | For 20 seconds, the next hard landing is softened once |
| Silence + player | Archivist bait. Drop it to freeze an archivist |

A wrong pair damages one slip and spikes pressure in the reel's chunk. A moment replicant is asked only if that chunk is then overloaded or fractured. A mistake on a quiet chunk does not call one. Recipes use amethyst, glass, copper, iron, sticks, paper, a crafting table, ink, cobblestone, and redstone. The catalog fragment is paper over an amethyst shard over an ink sac. Items are on the Mnemolith creative tab. The needle costs 2 durability per extract and rests for one second.

Three mobs spawn in the overworld only where the chunk is loud enough, and from eggs or `/mnemolith spawn`:

| Mob | What it does | How to answer it |
| --- | --- | --- |
| Echo strider | Walks your recent path, then charges when the chunk is overloaded or you hit it | Mute stone, or sneak with the chronicle lens |
| Archivist | Takes one slip from an open container, your hand, or the ground, then runs | Resonator trap, or archivist bait |
| Moment replicant | Copies your last hit, jump, placed block (as a fading, dropless copy), or item use after a short tell | Sneak and use the chronicle lens |

Three generated places use that loop. They are sparse, and each one can be turned off in `worldGen`.

| Place | Where | What it changes |
| --- | --- | --- |
| Archival vein | Underground stone, about 8% of overworld chunks, Y −48 to 32 | Archival stratum adds a small pressure bleed (default 1 each, at most 6) and, while you hold the lens, a wider read plus a few particles on the vein |
| Mute pocket | A small buried room, about 4% of chunks | Mute stone lining. Imprint writes in that chunk stop. Echo striders do not naturally spawn there. Some pockets have a chest, sometimes with a needle |
| Chronicle observatory | A ruined platform on forest, hill, taiga, jungle, mountain, plains, meadow, savanna, desert, or snowy ground. About one every 32 chunks, 12 chunks apart | A composition reel, a crafting table, and a chest that always has a lens or a needle, plus a tablet, mute stone, teaching slips, or rarely a catalog fragment. Archivists spawn nearby only once the chunk is loud |

### Echoes (stage 1)

Craft **blank echo slips** (paper + amethyst shard + redstone, shapeless, gives 2). Use one to record yourself for 25 seconds: movement, look, sneak and sprint, swings, broken and placed blocks, and levers, buttons and doors. The slip becomes an **echo recording**. Use it near where you recorded and an **echo** appears: a translucent pink copy of you that replays the recording in the world through a fake player owned by you, then stays as an idle helper. It starts with an empty inventory; sneak and right-click it to give it tools and blocks. Hold use with the chronicle lens for the pink thermal view: echoes glow through walls, and pressing attack on your targeted echo swaps you into it, leaving a shell behind. `V` swaps you back. The design, rules, edge cases and stage 2/3 plans are in [docs/echo-design.md](docs/echo-design.md) (Russian). `/mnemolith echoqa` checks the echo paths on a dedicated server.

### Memory grafts

Right-click your own echo with an **imprint slip** to graft that memory into it as a **temper**: silence makes it *hushed* (its work, and the work of your echoes within 8 blocks, leaves no imprints, and mobs ignore it), death makes it *grave* (a decoy mobs attack instead of your workers), fire makes it *kindled* (fireproof, mines beside lava, ore it or nearby echoes mine comes out smelted), fall makes it *plunging* (takes 12-block drops without damage and digs out its own floor), explosion makes it *volatile* (digs almost twice as fast, but its work writes explosions and its body bursts when it dies). A graft has charges spent on exactly those actions; a second slip of the same kind tops it up. The extraction needle unpicks a graft back into a slip, a fracture under the echo rejects it into the chunk, archivists steal it first. While you possess a grafted echo its temper works on you. No new items: grafts give imprint slips and the needle a second job. `/mnemolith graftqa` checks the whole system; design notes are in [docs/echo-design.md](docs/echo-design.md) §12 and [docs/design/roadmap.md](docs/design/roadmap.md).

### Residual echoes

An overloaded chunk sometimes condenses its loudest memory of a body (silence, death, fire, fall, explosion) into a **residual echo**: a faint fragment in that temper's color that drifts in the chunk. The imprint leaves the chunk, so pressure drops, but every minute the residue festers and writes it back; in a fracture it also acts it out (a zombie, a fire, a small blast, a lift, darkness), and it lashes anyone who walks too close. Raise the **lens** on it for 3 seconds to read it (tag noted, pinned for 10 seconds); then the **extraction needle** captures it as a **residual shard** (not craftable; archivists also archive unread residues and drop the shard when killed). A shard grafts your echo at full two slips' worth, or releases the residue wherever you use it. A matching grafted echo nearby drinks a residue, a possessed body absorbs one with an empty hand, a mute stone or a calm chunk starves it, and every observatory holds one old residue. `/mnemolith residueqa` checks the system; design notes are in [docs/echo-design.md](docs/echo-design.md) §13.

`/mnemolith inspect` prints pressure for the chunk under you. `/mnemolith smoke` is a gamemaster check of the write, mute, extract, and compose paths. `/mnemolith perf` times those write, score, lens, and sensor paths on a chunk beside you. `/mnemolith qa` checks the survival loop: imprint writes, pressure bands, extract, every formula, quiet and loud failures, mute, the lens band and dimension stamp, the catalog payload, the catalog recipe, vein, mute pocket, observatory locate and chest loot, and one action from each mob. The row-by-row map is in [docs/qa-checklist.md](docs/qa-checklist.md). `/mnemolith mobs` spawns all three and makes the archivist steal once. `/mnemolith worldgen` force-places a vein and a mute pocket at your feet. `/locate structure mnemolith:chronicle_observatory` finds an observatory.

Runtime rules are in [docs/architecture.md](docs/architecture.md). Measured timings are in [docs/performance.md](docs/performance.md).

## Config

NeoForge writes three files. Edit them while the game is closed, or use the in-game config screen (Mods → Mnemolith → Config) on the client. English and Russian strings are in `assets/mnemolith/lang`.

| File | Where it loads | What it holds |
| --- | --- | --- |
| `config/mnemolith-common.toml` | Client and dedicated server | Difficulty, spawn rates, world generation, gameplay (including the catalog and discovery hints), mobs |
| `config/mnemolith-server.toml` | Integrated and dedicated server; synced to clients | Whether storms are allowed, the per-dimension cap, pressure logging |
| `config/mnemolith-client.toml` | Physical client only | Custom memory particles, particle density, the per-tick particle cap, ambient shimmer without the lens, lens poll interval, lens overlay, overlay opacity, numeric pressure, pressure vignette, screen shake, fracture fringe, lens chime volume |

A world can override the server file by placing a copy in that world's `serverconfig` folder (`saves/<world>/serverconfig` on the client, `<server>/world/serverconfig` on a dedicated server).

`worldGen.structuresEnabled` and `worldGen.observatoryEnabled` apply the next time a world loads. Together they allow the chronicle observatory. `worldGen.structureSpacing` records the datapack spacing (32 chunks, separation 12 in `data/mnemolith/worldgen/structure_set/chronicle_observatory.json`). Changing the toml number does not move structures. Vein and mute-pocket toggles, chances, and Y ranges apply to chunks generated after the config is read. Bleed and the archivist and strider bias flags apply the next time pressure is scored or a mob tries to spawn. Gameplay values (write toggles, debounce, thresholds, extraction cost and cooldown, instability decay, quiet fade, composition, `catalogEnabled`, `discoveryHints`) apply the next time that action runs. `visuals.lensOverlay`, `visuals.overlayOpacity`, and `visuals.showNumericPressure` apply the next time the pill is drawn. `gameplay.allowAmbientPressure` (default false) is the server rule for full pressure snapshots without a held lens. `visuals.pressureVignette`, `visuals.stormScreenShake`, and `visuals.fractureFringe` apply on the next frame from the latest server snapshot. They do not need the lens: without one, and without `gameplay.allowAmbientPressure`, the server answers with a band-only snapshot that feeds only fracture feel. Turning all three off stops that extra poll. `visuals.particleDensity`, `visuals.maxParticlesPerTick`, `visuals.ambientWithoutLens`, and `visuals.lensPollInterval` apply on the client. Ambient shimmer is drawn only when the client toggle is on and the server rule allows the full snapshot. The same poll interval spaces fracture-feel requests. Density 0 stops custom particles before the cap. The default cap is 48. `gameplay.veinShimmerTicks` (default 40) spaces vein particles while a lens snapshot is unchanged. `mobs.sensorInterval` (default 10) spaces resonator scans, flee scans, and idle repaths. A charge still aims every tick. Ambient shimmer does not reveal vein marks. `visuals.memoryAudioVolume` scales the local lens chime. Server-played imprint sounds use the blocks and players sound categories.

## Performance

A dedicated view distance of 8 to 10 fits this loop. The lens packet stays a radius of 2 chunks (3 with archival strata). Leave `visuals.particleDensity` at 1 and `visuals.maxParticlesPerTick` at 48 to keep the same particles as before. Set density to 0 to turn custom memory particles off. The before-and-after `/mnemolith perf` numbers are in [docs/performance.md](docs/performance.md).

## Multiplayer

Install the same jar on the client and on the dedicated server. NeoForge 26.2.0.88 or a newer 26.2.x build is required on both. Imprint writes, extraction, composition, and pressure are decided on the server. The lens sends a request and receives a snapshot of nearby chunks. Fracture feel sends that same read-only request when the lens is put away. Unless `gameplay.allowAmbientPressure` is true, the answer is band-only: overloaded and fracture chunks within two chunks, with no pressure number, chunk state, or vein marks, and it never fills the lens pill. It does not write memory. Two players in one chunk read the same cached pressure. Discovery is stored on the player who extracted or composed, and it is synced only to that player.

`/mnemolith mpsmoke` (gamemaster) runs that checklist with two simulated players in the dedicated process: same lens band, catalog isolation, one archivist steal from the open reel, a second reel left untouched, a mute stone blocking the other player, one replicant after a loud fail, and a compose button ignored once the menu is closed. It does not open a second game client. Hosting notes, payload versions, and that limit are in [docs/multiplayer.md](docs/multiplayer.md).

Server options in `mnemolith-server.toml` are authoritative and are synced to connected clients. Client options in `mnemolith-client.toml` stay on that player's machine: particles, the lens pill, lens polling, vignette, screen shake, fracture fringe, and the lens chime. The catalog opens from a server payload; the screen class is client-only. Dedicated servers do not load `MnemolithClient` or the classes under `com.mnemolith.client`.

## Known issues

- No Scar boss, no new biome, and no strikethrough shafts. Fracture logs, and it can spawn a moment replicant. It does not start a recollection storm.
- Observatory spacing is the structure set, not `worldGen.structureSpacing`.
- Fracture feel is client-side and follows the synced band. `visuals.pressureVignette` darkens the edge in an overloaded or fractured chunk. `visuals.stormScreenShake` shakes the camera there. The name is the old storm knob; a storm is still not started. `visuals.fractureFringe` desaturates the fractured chunk under you with one fullscreen pass, the same post-chain path as the lens thermal view. If that chain fails to load, the log says so once and the vignette and shake still run. Particles, item glint, and archival stratum light are unchanged.
- The game test world has structure generation off (hard-coded in `GameTestServer`), so the `qa` `locate` check is waived there; run `/mnemolith qa` on a real world for it.
- A dedicated server that stops before the world loads may be waiting on `eula.txt`. Set `eula=true` and start it again.
- On a machine with no audio device, the client logs `Failed to open OpenAL device` and continues with sounds disabled. That message comes from the sound engine.

## Development

Requirements: JDK 25 (the Gradle toolchain can download it), Git, and a 64-bit JVM. Gradle 9.2.1 is provided through the wrapper.

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer   # all QA suites and live residue tests, headless; non-zero exit on failure
```

On Windows, use `gradlew.bat`.

Run directories:

| Task | Directory |
| --- | --- |
| `runClient` | `run/client` |
| `runServer` | `run/server` |
| `runData` | `run/data` |
| `runGameTestServer` | `run/gametest` |

`build` writes `build/libs/mnemolith-<version>.jar`.

### Dedicated server

`./gradlew runServer` uses `run/server`. Stop the server from its console with `stop`.

If the process exits and leaves `eula.txt`, set `eula=true` and start it again. Set `online-mode=false` in `server.properties` when the development account should be able to join.

A successful dedicated-server log contains `Mnemolith dedicated server setup` and `Mnemolith logical server starting`. It does not contain `Mnemolith client setup`. The server writes `config/mnemolith-common.toml` and `config/mnemolith-server.toml`.

### Client

`./gradlew runClient` opens the Minecraft client. The main menu and the mod list should show Mnemolith without a registry crash. The client log contains `Mnemolith client setup`.

### Game tests

`./gradlew runGameTestServer` boots a headless game test server in `run/gametest`, runs every Mnemolith game test and exits with the number of failures (about 20 seconds after a build). The seven `/mnemolith ...qa` suites run as one test each, calling the same code as the commands. Live residue tests use real server players ticked like connected clients, covering formation, lashes, sneaking, lens reading, the needle, festers, mute stones and the observatory seed. The report is `build/gametest/report.xml`. In a dev client or server, `/test runmultiple mnemolith:` runs them by hand. Coverage and what stays manual: [`docs/qa-checklist.md`](docs/qa-checklist.md#automated-game-tests-ci). The `/mnemolith ...qa` commands still work on any server.

### CI

`.github/workflows/build.yml` runs `./gradlew build`, then `./gradlew runGameTestServer`, on Ubuntu with Temurin JDK 25. A failing game test fails the job. The report and server log are uploaded as the `gametest-report` artifact.

## Layout

```text
com.mnemolith
  Mnemolith              common @Mod
  MnemolithClient        physical client @Mod
  common/                shared names
  content/               blocks, items, creative tabs
  imprint/  pressure/    chunk memory and pressure bands
  echo/  echo/job/       recording, replay, possession, mine/build/farm jobs
  entity/  entity/mob/  entity/ai/  entity/echo/
  world/  worldgen/    veins, mute pockets, observatory
  event/
  command/  command/qa/  /mnemolith and the QA suites
  gametest/              game tests (suites + live residue tests)
  network/  data/  audio/  particle/
  config/                common, client, and server specs
  client/echo/           echo renderer, HUD, inventory screen
  client/render|particle|audio|gui
  server/                dedicated server @Mod
```

## Art

The chronicle lens, extraction needle, imprint slip, catalog fragment, archivist bait, composition reel, mute stone, archival stratum, resonator trap, and the three mobs share one palette: deep indigo, bone, verdigris, and mute gray. Ember is reserved for failure and fire. The lens is a 32×32 ring. The four blocks are multi-part models, not flat cubes. Strider, archivist, and replicant silhouettes stay on the existing part-posing animation: walk, charge, snatch, and telegraph. Sounds are short mono `.ogg` tones. Particle sprites stay white and the client tints them. Sizes, pivots, and UV notes are in [`docs/asset-pipeline.md`](docs/asset-pipeline.md). Scar, a decoration set, and a music album are not in this pass.

## Русский

Мир записывает свою историю в камень. Читайте отпечатки, собирайте память, переживайте бури воспоминаний.

Это **фаза 12**: основной цикл памяти, три моба, три места генерации, интерфейс архива, частицы памяти, общий художественный проход и настройка баланса для NeoForge 26.2. События мира пишут отпечатки, в чанке растёт давление памяти, игрок извлекает бланк и составляет короткие формулы. Эхо-странник, архивариус и репликант момента живут на этом давлении. Архивные жилы, глухие карманы и хроникальные обсерватории работают с теми же системами. Хроникальная линза рисует плашку давления, у барабана составления свой экран, а фрагмент каталога помнит изученное. Запись, извлечение, успех и провал составления, предупреждение давления, глушащий камень и телеграфы мобов имеют свои частицы. `visuals.particleDensity` равный 0 их выключает. В разломе один дешёвый проход может обесцветить экран. Нового биома и Шрама нет. В фазе 12 добавлены крафт фрагмента каталога, иллюстрированный полевой справочник и `/mnemolith qa`.

### Оформление

Линза, игла, бланк, фрагмент каталога, приманка, барабан, глушащий камень, архивный слой, резонатор и три моба собраны в одной палитре: глубокий индиго, кость, ярь-медянка и глухой серый. Уголь — только для провала и огня. Линза — кольцо 32×32. Четыре блока собраны из нескольких деталей. Походка, рывок, похищение и телеграф остаются позами частей модели. Звуки — короткие моно `.ogg`. Спрайты частиц белые, клиент их окрашивает. Размеры, точки опоры и UV — в [`docs/asset-pipeline.md`](docs/asset-pipeline.md). Шрама, набора декора и музыкального альбома в этом проходе нет.

### Установка

1. Minecraft Java Edition **26.2**.
2. NeoForge **26.2.x** (сборка проверена на 26.2.0.88).
3. Положите `mnemolith-<версия>.jar` в папку `mods`.
4. Других модов для запуска не нужно.

В списке модов имя — **Mnemolith** (Мнемолит).

### Игра

На новом мире первый отпечаток появляется в первую минуту ходьбы, а первый бланк и одно удачное составление — примерно за **10–20 минут** обычной игры. Стройка сама по себе оставляет чанк спокойным. Смерть или крипер доводят его до насыщения. Числа и причины — в [docs/balance.md](docs/balance.md).

Смерть, взрыв, долгое падение и установка или разрушение блока оставляют **отпечаток** на чанке. **Глушащий камень** после записи тишины запрещает новые отпечатки в своём чанке и прогоняет эхо-странника. **Хроникальная линза** показывает плашку давления над панелью быстрого доступа; крадитесь, чтобы увидеть состояние чанка, и используйте её, чтобы ослепить репликанта момента. Без линзы плашки нет. **Игла извлечения** забирает сильнейший отпечаток в **бланк** и записывает метку в каталог. Между извлечениями она отдыхает секунду и тратит 2 прочности. **Барабан составления** принимает четыре формулы: смерть и тишина (незаписанный, 15 секунд), огонь и стройка (огненный след, 12 секунд), падение и игрок (всплеск приземления, 20 секунд), тишина и игрок (приманка архивариуса). Изученные узоры видны значками меток, неизвестные остаются вопросом. Неверная пара портит один бланк и поднимает давление. Репликант призывается, только если чанк после этого перегружен или в разломе. Нестабильность остывает, пока вы стоите в чанке. Старые отпечатки стройки, редстоуна и пути затухают. **Фрагмент каталога** крафтится из бумаги, осколка аметиста и чернильного мешка и открывает только то, что вы уже узнали. Редко он лежит в сундуке обсерватории. **Полевой справочник** крафтится из книги и осколка аметиста. Он открывает двадцать одну иллюстрированную страницу: первый час, отпечатки, полосы, крафт, формулы, защита от каждого моба, места мира, отголоски, запись и вселение, прививки памяти и игра вдвоём. Рисует страницы клиент. Выделенный сервер только регистрирует предмет. Сундук обсерватории тоже может его содержать. Разлом пишется в журнал и не начинает бурю. В перегруженном чанке и в разломе края экрана темнеют, камера дрожит, а в разломе под ногами цвет ещё и выцветает. Линза для этого не нужна: без неё сервер присылает только полосы (перегруженные чанки и разломы в радиусе двух чанков, без чисел). Выключатели: `visuals.pressureVignette`, `visuals.stormScreenShake`, `visuals.fractureFringe`.

Эхо-странник ходит по вашему пути и делает рывок в перегруженном чанке. Архивариус появляется в перегруженном чанке и забирает один бланк из открытого сундука, из руки или с земли. Резонаторная ловушка и приманка его останавливают. Репликант момента копирует удар, прыжок, установку блока или использование предмета после более длинного телеграфа.

Под землёй встречается **архивная жила** (около 8% чанков): пласт чуть поднимает давление и, пока в руке линза, расширяет чтение. **Глухой карман** (около 4% чанков) выложен глушащим камнем и не принимает новые отпечатки. **Хроникальная обсерватория** — руина с барабаном составления и сундуком, в котором всегда есть линза или игла, а иногда фрагмент каталога. Обсерватории стоят примерно раз в 32 чанка, с разделением 12. Шахт и нового биома нет.

**Отголоски (этап 1).** **Бланк отголоска** (бумага, осколок аметиста и красная пыль, 2 штуки) записывает вас 25 секунд: движение, взгляд, присед и бег, удары, сломанные и поставленные блоки, рычаги, кнопки и двери. Запись рядом с местом записи вызывает **отголосок** — полупрозрачную розовую копию, которая повторяет запись в мире через фейкового игрока от вашего имени и потом остаётся помощником. Инвентарь у него пустой; Shift+ПКМ — дать ему инструмент и блоки. Зажмите ПКМ с линзой — термовзгляд: отголоски светятся сквозь стены, ЛКМ по своему отголоску — вселиться (на месте остаётся оболочка), `V` — вернуться. Подробно — [docs/echo-design.md](docs/echo-design.md). `/mnemolith echoqa` проверяет отголоски на выделенном сервере.

**Прививки памяти.** ПКМ по своему отголоску **бланком отпечатка** прививает эту память как **нрав**: тишина — *безмолвный* (его работа и работа ваших отголосков в 8 блоках не оставляет отпечатков, мобы его не замечают), смерть — *могильный* (приманка, которую мобы бьют вместо ваших работников), огонь — *тлеющий* (не горит, копает у лавы, руда, добытая им или соседями, выходит слитками), падение — *ныряющий* (спускается на 12 блоков без урона и выкапывает пол под собой), взрыв — *гремучий* (копает почти вдвое быстрее, но работа пишет взрывы, а тело взрывается при смерти). Заряды тратятся ровно на эти действия; второй бланк того же вида подпитывает. Игла извлечения вынимает прививку обратно бланком, разлом под отголоском отторгает её в чанк, архивариус крадёт её первой. Вселившись в привитого отголоска, вы получаете его нрав. Новых предметов нет. `/mnemolith graftqa` проверяет систему; подробности — [docs/echo-design.md](docs/echo-design.md) §12.

**Осадки памяти.** Перегруженный чанк иногда сгущает свою самую громкую память о теле (тишина, смерть, огонь, падение, взрыв) в **осадок памяти** — бледный обрывок цвета нрава, который плавает по чанку. Отпечаток уходит из чанка, давление падает, но раз в минуту осадок бродит и пишет его обратно; в разломе ещё и разыгрывает (зомби, огонь, малый взрыв, подъём, тьма) и бьёт тех, кто подходит близко. Подержите на нём поднятую **линзу** 3 секунды — он прочитан (метка отмечена, скован на 10 секунд); тогда **игла извлечения** ловит его в **осколок осадка** (не крафтится; архивариусы тоже архивируют непрочитанные осадки и роняют осколок при смерти). Осколок прививает отголоску сразу два бланка или выпускает осадок там, где его применили. Привитый отголосок того же нрава пьёт осадок, тело во вселении впитывает его пустой рукой, глушащий камень и покой его морят, а в каждой обсерватории ждёт старый осадок. `/mnemolith residueqa` проверяет систему; подробности — [docs/echo-design.md](docs/echo-design.md) §13.

`/mnemolith inspect` печатает давление чанка. `/mnemolith smoke` — проверка записи и составления. `/mnemolith perf` замеряет запись, счёт, обход линзы и датчики на соседнем чанке. `/mnemolith qa` проверяет цикл выживания; таблица — в [docs/qa-checklist.md](docs/qa-checklist.md). `/mnemolith mpsmoke` проверяет двух игроков в одном процессе сервера. Второй клиент он не открывает. Заметки — в [docs/multiplayer.md](docs/multiplayer.md). `/mnemolith mobs` призывает всех трёх и один раз крадёт бланк. `/mnemolith worldgen` ставит жилу и глухой карман у ног. `/locate structure mnemolith:chronicle_observatory` ищет обсерваторию.

Для выделенного сервера хватает дальности прорисовки 8–10. Пакет линзы остаётся радиусом в 2 чанка (3, если в чанке есть архивный пласт). `visuals.particleDensity` 1 и `visuals.maxParticlesPerTick` 48 сохраняют прежние частицы. Плотность 0 их выключает. Замеры — в [docs/performance.md](docs/performance.md). Остывание идёт только в чанке игрока и пропускается, когда остывать нечему.

### Конфиг

| Файл | Где читается |
| --- | --- |
| `config/mnemolith-common.toml` | Клиент и выделенный сервер |
| `config/mnemolith-server.toml` | Логический сервер, синхронизируется клиентам |
| `config/mnemolith-client.toml` | Только физический клиент |

Экран конфига: «Моды» → Mnemolith → Config. Строки есть на английском и русском.

### Сеть

Одинаковый jar нужен и клиенту, и выделенному серверу. Серверный конфиг задаёт правила бурь. Клиентский конфиг (частицы, плашка линзы, виньетка, тряска, бахрома разлома, громкость) остаётся на компьютере игрока. Каталог открывается пакетом с сервера. Классы из `com.mnemolith.client` на выделенном сервере не загружаются.

### Сборка

Нужен JDK 25. Команды из корня репозитория:

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Готовый файл: `build/libs/mnemolith-<версия>.jar`.

`./gradlew runGameTestServer` запускает автотесты без клиента: все наборы `/mnemolith ...qa` и живые тесты осадков с настоящими серверными игроками. Код завершения равен числу упавших тестов. CI запускает эту задачу после `build`, и упавший тест роняет сборку. Что покрыто и что осталось ручным: [`docs/qa-checklist.md`](docs/qa-checklist.md#automated-game-tests-ci).

Если `runServer` остановится и создаст `run/server/eula.txt`, поставьте `eula=true` и запустите снова. Для входа дев-аккаунтом в `server.properties` укажите `online-mode=false`. В журнале выделенного сервера должны быть строки `Mnemolith dedicated server setup` и `Mnemolith logical server starting`, и не должно быть `Mnemolith client setup`.

# Mnemolith (Мнемолит)

The world writes its history into stone. Read imprints, compose memory, survive recollection storms.

This repository is **Phase 6**: the core memory loop, three mobs, three worldgen features, and the archival interface on NeoForge 26.2. World events write imprints, chunks accumulate memory pressure, and a player can extract and compose a small set of formulas. Echo striders, archivists, and moment replicants use that pressure. Archival veins, mute pockets, and chronicle observatories feed the same systems. The chronicle lens draws a pressure pill, the composition reel has its own screen, and a catalog fragment remembers what you have learned. There is no new biome and no Scar.

| | |
| --- | --- |
| Minecraft | Java Edition 26.2 |
| NeoForge | 26.2.0.88 or newer 26.2.x |
| Java | 25 |
| Mod id | `mnemolith` |
| Package | `com.mnemolith` |
| License | MIT (`LICENSE`). The NeoForge MDK template files remain under `TEMPLATE_LICENSE.txt`. |

Mappings are Mojang's official names. The Gradle project is the [ModDevGradle 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle).

## Install

1. Install Minecraft Java Edition **26.2**.
2. Install **NeoForge 26.2.x** for that version (this project is built against 26.2.0.88).
3. Download `mnemolith-<version>.jar` from the project releases, or build it (see below).
4. Put the jar in the `mods` folder of that NeoForge instance.
5. Launch the game. Mnemolith appears in the mod list as **Mnemolith**.

The mod has no required dependencies beyond Minecraft and NeoForge.

## Gameplay

1. Deaths, explosions, long falls, and block changes write **imprints** on the chunk where they happened. Build and redstone writes are throttled. An unwitnessed death also writes silence. Placing a **mute stone** writes silence, then blocks further writes in its chunk.
2. Each chunk's **memory pressure** is intensity times tag weight, plus instability from a failed composition, clamped by the soft cap. Bands are calm, saturated, overloaded, and fracture. Fracture is logged. It does not start a storm.
3. Hold a **chronicle lens** to see a pressure pill above the hotbar: the band name, and the number unless you turn that off. Sneak to add the chunk state (clear, muted, archival, or fractured). Saturated chunks nearby still shimmer. Put the lens away and the pill is gone.
4. Use an **extraction needle** on a block in that chunk. The strongest imprint becomes an **imprint slip**, and that tag is added to your catalog.
5. Put slips in a **composition reel** and press Compose. Learned patterns show as tag icons. Unread ones stay a question mark, or stay hidden if discovery hints are off. Success and failure are written on the screen. The server decides the result.
6. Use a **catalog fragment** to open the tags and patterns you have already learned.

| Slips | Result |
| --- | --- |
| Death + silence | Unrecorded. Mobs lose you as a target for a short time |
| Fire + build | Fire trail, brief fire resistance, and snow underfoot melts |
| Fall + player | The next hard landing is softened once |
| Silence + player | Archivist bait. Drop it to freeze an archivist |

A wrong pair damages one slip, spikes pressure in the reel's chunk, and can spawn a moment replicant. Recipes use amethyst, glass, copper, iron, sticks, paper, a crafting table, ink, cobblestone, and redstone. Items are on the Mnemolith creative tab.

Three mobs spawn in the overworld only where the chunk is loud enough, and from eggs or `/mnemolith spawn`:

| Mob | What it does | How to answer it |
| --- | --- | --- |
| Echo strider | Walks your recent path, then charges when the chunk is overloaded or you hit it | Mute stone, or sneak with the chronicle lens |
| Archivist | Takes one slip from an open container, your hand, or the ground, then runs | Resonator trap, or archivist bait |
| Moment replicant | Copies your last hit, jump, placed block, or item use after a short tell | Sneak and use the chronicle lens |

Three generated places use that loop. They are sparse, and each one can be turned off in `worldGen`.

| Place | Where | What it changes |
| --- | --- | --- |
| Archival vein | Underground stone, about 12% of overworld chunks, Y −48 to 32 | Archival stratum adds a small pressure bleed (default 1 each, at most 6) and, while you hold the lens, a wider read plus a few particles on the vein |
| Mute pocket | A small buried room, about 2% of chunks | Mute stone lining. Imprint writes in that chunk stop. Echo striders do not naturally spawn there. Some pockets have a chest |
| Chronicle observatory | A ruined platform on forest, hill, taiga, jungle, mountain, plains, meadow, savanna, desert, or snowy ground. About one every 40 chunks, 16 chunks apart | A composition reel, a crafting table, and a chest with a lens, a needle, slips, and an archival tablet. Archivists are a little more willing to spawn nearby |

`/mnemolith inspect` prints pressure for the chunk under you. `/mnemolith smoke` is a gamemaster check of the write, mute, extract, and compose paths. `/mnemolith mobs` spawns all three and makes the archivist steal once. `/mnemolith worldgen` force-places a vein and a mute pocket at your feet. `/locate structure mnemolith:chronicle_observatory` finds an observatory.

Runtime rules are in [docs/architecture.md](docs/architecture.md).

## Config

NeoForge writes three files. Edit them while the game is closed, or use the in-game config screen (Mods → Mnemolith → Config) on the client. English and Russian strings are in `assets/mnemolith/lang`.

| File | Where it loads | What it holds |
| --- | --- | --- |
| `config/mnemolith-common.toml` | Client and dedicated server | Difficulty, spawn rates, world generation, gameplay (including the catalog and discovery hints), mobs |
| `config/mnemolith-server.toml` | Integrated and dedicated server; synced to clients | Whether storms are allowed, the per-dimension cap, pressure logging |
| `config/mnemolith-client.toml` | Physical client only | Imprint particles, particle density, lens poll interval, lens overlay, overlay opacity, numeric pressure, pressure vignette, storm screen shake, lens chime volume |

A world can override the server file by placing a copy in that world's `serverconfig` folder (`saves/<world>/serverconfig` on the client, `<server>/world/serverconfig` on a dedicated server).

`worldGen.structuresEnabled` and `worldGen.observatoryEnabled` apply the next time a world loads. Together they allow the chronicle observatory. `worldGen.structureSpacing` records the datapack spacing (40 chunks, separation 16 in `data/mnemolith/worldgen/structure_set/chronicle_observatory.json`). Changing the toml number does not move structures. Vein and mute-pocket toggles, chances, and Y ranges apply to chunks generated after the config is read. Bleed and the archivist and strider bias flags apply the next time pressure is scored or a mob tries to spawn. Gameplay values (write toggles, debounce, thresholds, extraction cost, composition, `catalogEnabled`, `discoveryHints`) apply the next time that action runs. `visuals.lensOverlay`, `visuals.overlayOpacity`, and `visuals.showNumericPressure` apply the next time the pill is drawn. `visuals.particleDensity` and `visuals.lensPollInterval` apply on the client. `visuals.memoryAudioVolume` scales the local lens chime. Server-played imprint sounds use the blocks and players sound categories.

## Multiplayer

Install the same jar on the client and on the dedicated server. Imprint writes, extraction, composition, and pressure are decided on the server. The lens sends a request and receives a snapshot of nearby chunks. It does not write memory.

Server options in `mnemolith-server.toml` are authoritative and are synced to connected clients. Client options in `mnemolith-client.toml` stay on that player's machine: particles, the lens pill, lens polling, vignette, screen shake, and the lens chime. The catalog opens from a server payload; the screen class is client-only. Dedicated servers do not load `MnemolithClient` or the classes under `com.mnemolith.client`.

## Known issues

- No Scar boss, no new biome, and no strikethrough shafts. Fracture logs, and it can spawn a moment replicant. It does not start a recollection storm.
- Observatory spacing is the structure set, not `worldGen.structureSpacing`.
- `visuals.pressureVignette` and `visuals.stormScreenShake` are loaded and not drawn. The lens uses the action bar and particles.
- The `gameTestServer` run crashes until a game test is registered. That is the MDK default. `build` does not run it.
- A dedicated server that stops before the world loads may be waiting on `eula.txt`. Set `eula=true` and start it again.
- On a machine with no audio device, the client logs `Failed to open OpenAL device` and continues with sounds disabled. That message comes from the sound engine.

## Development

Requirements: JDK 25 (the Gradle toolchain can download it), Git, and a 64-bit JVM. Gradle 9.2.1 is provided through the wrapper.

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
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

### CI

`.github/workflows/build.yml` runs `./gradlew build` on Ubuntu with Temurin JDK 25.

## Layout

```text
com.mnemolith
  Mnemolith              common @Mod
  MnemolithClient        physical client @Mod
  common/                shared names
  content/               blocks, items, creative tabs
  imprint/  pressure/    chunk memory and pressure bands
  entity/  entity/mob/  entity/ai/
  world/  worldgen/    veins, mute pockets, observatory
  event/
  network/  data/  audio/
  config/                common, client, and server specs
  client/render|particle|audio|gui
  server/                dedicated server @Mod
```

## Русский

Мир записывает свою историю в камень. Читайте отпечатки, собирайте память, переживайте бури воспоминаний.

Это **фаза 6**: основной цикл памяти, три моба, три места генерации и интерфейс архива для NeoForge 26.2. События мира пишут отпечатки, в чанке растёт давление памяти, игрок извлекает бланк и составляет короткие формулы. Эхо-странник, архивариус и репликант момента живут на этом давлении. Архивные жилы, глухие карманы и хроникальные обсерватории работают с теми же системами. Хроникальная линза рисует плашку давления, у барабана составления свой экран, а фрагмент каталога помнит изученное. Нового биома и Шрама нет.

### Установка

1. Minecraft Java Edition **26.2**.
2. NeoForge **26.2.x** (сборка проверена на 26.2.0.88).
3. Положите `mnemolith-<версия>.jar` в папку `mods`.
4. Других модов для запуска не нужно.

В списке модов имя — **Mnemolith** (Мнемолит).

### Игра

Смерть, взрыв, долгое падение и установка или разрушение блока оставляют **отпечаток** на чанке. **Глушащий камень** после записи тишины запрещает новые отпечатки в своём чанке и прогоняет эхо-странника. **Хроникальная линза** показывает плашку давления над панелью быстрого доступа; крадитесь, чтобы увидеть состояние чанка, и используйте её, чтобы ослепить репликанта момента. Без линзы плашки нет. **Игла извлечения** забирает сильнейший отпечаток в **бланк** и записывает метку в каталог. **Барабан составления** принимает четыре формулы: смерть и тишина (незаписанный), огонь и стройка (огненный след), падение и игрок (всплеск приземления), тишина и игрок (приманка архивариуса). Изученные узоры видны значками меток, неизвестные остаются вопросом. Неверная пара портит один бланк, поднимает давление и может призвать репликанта. **Фрагмент каталога** открывает только то, что вы уже узнали. Разлом пишется в журнал и не начинает бурю.

Эхо-странник ходит по вашему пути и делает рывок в перегруженном чанке. Архивариус забирает один бланк из открытого сундука, из руки или с земли. Резонаторная ловушка и приманка его останавливают. Репликант момента копирует удар, прыжок, установку блока или использование предмета.

Под землёй встречается **архивная жила**: пласт чуть поднимает давление и, пока в руке линза, расширяет чтение. **Глухой карман** выложен глушащим камнем и не принимает новые отпечатки. **Хроникальная обсерватория** — редкая руина с барабаном составления и сундуком (линза, игла, бланки, архивная табличка). Обсерватории стоят примерно раз в 40 чанков. Шахт и нового биома нет.

`/mnemolith inspect` печатает давление чанка. `/mnemolith smoke` — проверка записи и составления. `/mnemolith mobs` призывает всех трёх и один раз крадёт бланк. `/mnemolith worldgen` ставит жилу и глухой карман у ног. `/locate structure mnemolith:chronicle_observatory` ищет обсерваторию.

Правила производительности — в [docs/architecture.md](docs/architecture.md): отпечаток пишется в событии, которое его породило; полный обход мира каждый тик не допускается.

### Конфиг

| Файл | Где читается |
| --- | --- |
| `config/mnemolith-common.toml` | Клиент и выделенный сервер |
| `config/mnemolith-server.toml` | Логический сервер, синхронизируется клиентам |
| `config/mnemolith-client.toml` | Только физический клиент |

Экран конфига: «Моды» → Mnemolith → Config. Строки есть на английском и русском.

### Сеть

Одинаковый jar нужен и клиенту, и выделенному серверу. Серверный конфиг задаёт правила бурь. Клиентский конфиг (частицы, плашка линзы, виньетка, тряска, громкость) остаётся на компьютере игрока. Каталог открывается пакетом с сервера. Классы из `com.mnemolith.client` на выделенном сервере не загружаются.

### Сборка

Нужен JDK 25. Команды из корня репозитория:

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Готовый файл: `build/libs/mnemolith-<версия>.jar`.

Если `runServer` остановится и создаст `run/server/eula.txt`, поставьте `eula=true` и запустите снова. Для входа дев-аккаунтом в `server.properties` укажите `online-mode=false`. В журнале выделенного сервера должны быть строки `Mnemolith dedicated server setup` и `Mnemolith logical server starting`, и не должно быть `Mnemolith client setup`.

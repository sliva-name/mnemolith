# Mnemolith (Мнемолит)

The world writes its history into stone. Read imprints, compose memory, survive recollection storms.

This repository is **Phase 4**: the core memory loop plus three mobs on NeoForge 26.2. World events write imprints, chunks accumulate memory pressure, and a player can extract and compose a small set of formulas. Echo striders, archivists, and moment replicants use that pressure. The Scar and structures are not in the game yet.

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
3. Hold a **chronicle lens** to see the band for your chunk and shimmer on saturated chunks nearby.
4. Use an **extraction needle** on a block in that chunk. The strongest imprint becomes an **imprint slip**.
5. Put slips in a **composition reel** and press Compose.

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

`/mnemolith inspect` prints pressure for the chunk under you. `/mnemolith smoke` is a gamemaster check of the write, mute, extract, and compose paths. `/mnemolith mobs` spawns all three and makes the archivist steal once.

Runtime rules are in [docs/architecture.md](docs/architecture.md).

## Config

NeoForge writes three files. Edit them while the game is closed, or use the in-game config screen (Mods → Mnemolith → Config) on the client. English and Russian strings are in `assets/mnemolith/lang`.

| File | Where it loads | What it holds |
| --- | --- | --- |
| `config/mnemolith-common.toml` | Client and dedicated server | Difficulty, spawn rates, world generation, gameplay, mobs |
| `config/mnemolith-server.toml` | Integrated and dedicated server; synced to clients | Whether storms are allowed, the per-dimension cap, pressure logging |
| `config/mnemolith-client.toml` | Physical client only | Imprint particles, particle density, lens poll interval, pressure vignette, storm screen shake, lens chime volume |

A world can override the server file by placing a copy in that world's `serverconfig` folder (`saves/<world>/serverconfig` on the client, `<server>/world/serverconfig` on a dedicated server).

`worldGen.structuresEnabled` and `worldGen.structureSpacing` apply the next time a world loads. Gameplay values (write toggles, debounce, thresholds, extraction cost, composition) apply the next time that action runs. `visuals.particleDensity` and `visuals.lensPollInterval` apply on the client. `visuals.memoryAudioVolume` scales the local lens chime. Server-played imprint sounds use the blocks and players sound categories.

## Multiplayer

Install the same jar on the client and on the dedicated server. Imprint writes, extraction, composition, and pressure are decided on the server. The lens sends a request and receives a snapshot of nearby chunks. It does not write memory.

Server options in `mnemolith-server.toml` are authoritative and are synced to connected clients. Client options in `mnemolith-client.toml` stay on that player's machine: particles, lens polling, vignette, screen shake, and the lens chime. Dedicated servers do not load `MnemolithClient` or the classes under `com.mnemolith.client`.

## Known issues

- No Scar boss or structures yet. Fracture logs, and it can spawn a moment replicant. It does not start a recollection storm.
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
  world/  event/
  network/  data/  audio/
  config/                common, client, and server specs
  client/render|particle|audio|gui
  server/                dedicated server @Mod
```

## Русский

Мир записывает свою историю в камень. Читайте отпечатки, собирайте память, переживайте бури воспоминаний.

Это **фаза 4**: основной цикл памяти и три моба для NeoForge 26.2. События мира пишут отпечатки, в чанке растёт давление памяти, игрок извлекает бланк и составляет короткие формулы. Эхо-странник, архивариус и репликант момента живут на этом давлении. Шрама и структур ещё нет.

### Установка

1. Minecraft Java Edition **26.2**.
2. NeoForge **26.2.x** (сборка проверена на 26.2.0.88).
3. Положите `mnemolith-<версия>.jar` в папку `mods`.
4. Других модов для запуска не нужно.

В списке модов имя — **Mnemolith** (Мнемолит).

### Игра

Смерть, взрыв, долгое падение и установка или разрушение блока оставляют **отпечаток** на чанке. **Глушащий камень** после записи тишины запрещает новые отпечатки в своём чанке и прогоняет эхо-странника. **Хроникальная линза** показывает полосу давления; крадитесь и используйте её, чтобы ослепить репликанта момента. **Игла извлечения** забирает сильнейший отпечаток в **бланк**. **Барабан составления** принимает четыре формулы: смерть и тишина (незаписанный), огонь и стройка (огненный след), падение и игрок (всплеск приземления), тишина и игрок (приманка архивариуса). Неверная пара портит один бланк, поднимает давление и может призвать репликанта. Разлом пишется в журнал и не начинает бурю.

Эхо-странник ходит по вашему пути и делает рывок в перегруженном чанке. Архивариус забирает один бланк из открытого сундука, из руки или с земли. Резонаторная ловушка и приманка его останавливают. Репликант момента копирует удар, прыжок, установку блока или использование предмета.

`/mnemolith inspect` печатает давление чанка. `/mnemolith smoke` — проверка записи и составления. `/mnemolith mobs` призывает всех трёх и один раз крадёт бланк.

Правила производительности — в [docs/architecture.md](docs/architecture.md): отпечаток пишется в событии, которое его породило; полный обход мира каждый тик не допускается.

### Конфиг

| Файл | Где читается |
| --- | --- |
| `config/mnemolith-common.toml` | Клиент и выделенный сервер |
| `config/mnemolith-server.toml` | Логический сервер, синхронизируется клиентам |
| `config/mnemolith-client.toml` | Только физический клиент |

Экран конфига: «Моды» → Mnemolith → Config. Строки есть на английском и русском.

### Сеть

Одинаковый jar нужен и клиенту, и выделенному серверу. Серверный конфиг задаёт правила бурь. Клиентский конфиг (частицы, виньетка, тряска, громкость) остаётся на компьютере игрока. Классы из `com.mnemolith.client` на выделенном сервере не загружаются.

### Сборка

Нужен JDK 25. Команды из корня репозитория:

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Готовый файл: `build/libs/mnemolith-<версия>.jar`.

Если `runServer` остановится и создаст `run/server/eula.txt`, поставьте `eula=true` и запустите снова. Для входа дев-аккаунтом в `server.properties` укажите `online-mode=false`. В журнале выделенного сервера должны быть строки `Mnemolith dedicated server setup` и `Mnemolith logical server starting`, и не должно быть `Mnemolith client setup`.

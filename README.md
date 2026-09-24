# Mnemolith (Мнемолит)

The world writes its history into stone. Read imprints, compose memory, survive recollection storms.

This repository is **Phase 2**: a NeoForge 26.2 mod skeleton. It loads on the client and the dedicated server, registers empty content registries, and ships common, client, and server configs. Imprints, mobs, structures, and custom art are not in the game yet.

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

Later phases add the actual loop:

1. World events write **imprints** — tagged memories stored on the chunk where they happened.
2. Imprints in loaded chunks raise **memory pressure**.
3. The player extracts and **composes** those memories.
4. Pressure past the threshold can start a **recollection storm**, and with it the **Scar**.

Planned content is about 16 blocks, 18 items, 3 mobs, one boss event, and 3 structure types. None of that content is registered yet. A new world with only this jar plays as vanilla Minecraft with the mod loaded.

How the systems are meant to stay cheap at runtime is written in [docs/architecture.md](docs/architecture.md).

## Config

NeoForge writes three files. Edit them while the game is closed, or use the in-game config screen (Mods → Mnemolith → Config) on the client. English and Russian strings are in `assets/mnemolith/lang`.

| File | Where it loads | What it holds |
| --- | --- | --- |
| `config/mnemolith-common.toml` | Client and dedicated server | Difficulty, spawn rates, world generation, gameplay |
| `config/mnemolith-server.toml` | Integrated and dedicated server; synced to clients | Whether storms are allowed, the per-dimension cap, pressure logging |
| `config/mnemolith-client.toml` | Physical client only | Imprint particles, pressure vignette, storm screen shake, memory audio volume |

A world can override the server file by placing a copy in that world's `serverconfig` folder (`saves/<world>/serverconfig` on the client, `<server>/world/serverconfig` on a dedicated server).

`worldGen.structuresEnabled` and `worldGen.structureSpacing` apply the next time a world loads. The other values are defaults for systems that are not built yet. Startup logs print a few of them so you can see that the files were read.

## Multiplayer

Install the same jar on the client and on the dedicated server. The mod registers common content (even while those registries are empty), so a client without the mod will not match a server that has it, and the reverse is also true.

Server options in `mnemolith-server.toml` are authoritative and are synced to connected clients. Client options in `mnemolith-client.toml` stay on that player's machine: particles, vignette, screen shake, and audio volume. Storms, when they exist, will be decided on the server.

Dedicated servers do not load `MnemolithClient` or the classes under `com.mnemolith.client`.

## Known issues

- No blocks, items, mobs, structures, sounds, or imprint gameplay yet.
- Config values are loaded and logged. Nothing in the world consumes them.
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
  imprint/  pressure/    future memory systems
  entity/  world/  event/
  network/  data/  audio/
  config/                common, client, and server specs
  client/render|particle|audio|gui
  server/                dedicated server @Mod
```

## Русский

Мир записывает свою историю в камень. Читайте отпечатки, собирайте память, переживайте бури воспоминаний.

Это **фаза 2**: каркас мода для NeoForge 26.2. Мод загружается на клиенте и на выделенном сервере, регистрирует пустые реестры и заводит общий, клиентский и серверный конфиг. Отпечатков, мобов, структур и своих текстур в игре ещё нет.

### Установка

1. Minecraft Java Edition **26.2**.
2. NeoForge **26.2.x** (сборка проверена на 26.2.0.88).
3. Положите `mnemolith-<версия>.jar` в папку `mods`.
4. Других модов для запуска не нужно.

В списке модов имя — **Mnemolith** (Мнемолит).

### Игра

Позже события мира будут оставлять **отпечатки** на чанке, отпечатки в загруженных чанках будут поднимать **давление памяти**, игрок сможет **составлять** память, а порог давления сможет начать **бурю воспоминаний** и босса **Шрам**. Сейчас мир с одним этим модом идёт как ванильный Minecraft.

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

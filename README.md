# Mnemolith (Мнемолит)

[Русский](#русский) · [English](#english)

## Русский

Мир записывает свою историю в камень. Смерти, взрывы, падения, стройка и даже ваши шаги оставляют **отпечатки** в чанке, где это случилось. Память копится **давлением**, и чем громче место помнит, тем сильнее отвечает: отголосками, осадками памяти, бурями воспоминаний и Шрамом. Вы читаете эту память линзой, вытягиваете её иглой, составляете из неё формулы и решаете, глушить её, использовать или залечить.

Последний релиз: **[Mnemolith 0.6.0 (бета)](https://github.com/sliva-name/mnemolith/releases/tag/v0.6.0)** · [все релизы](https://github.com/sliva-name/mnemolith/releases)

### Содержание

- [Возможности](#возможности)
- [Требования и установка](#требования-и-установка)
- [Настройки](#настройки)
- [Мультиплеер и производительность](#мультиплеер-и-производительность)
- [Известные проблемы](#известные-проблемы)
- [Разработка и QA](#разработка-и-qa)

## Возможности

### Память и отпечатки

- **Отпечатки (15 меток).** Огонь, Падение, Смерть, Стройка, Взрыв, Тишина, Игрок, Редстоун, Путь (примерно каждые 12 блоков), Молния, Портал, Скалк, Босс, Торговля и Забытое. «Забытое» пишется только в лощинах памяти.
- **Давление и полосы.** Спокойствие, насыщение, перегрузка, разлом. В перегруженном чанке края экрана темнеют и камера дрожит, а разлом ещё и вытягивает из мира цвет.
- **Хроникальная линза** показывает давление плашкой над хотбаром, а с 0.6.0 ещё и самое громкое воспоминание чанка цветным знаком. **Игла извлечения** вытягивает самый сильный отпечаток в **бланк отпечатка**.
- **Глушащий камень** не даёт чанку записывать. У тишины есть цена: в слишком долго или густо заглушённом чанке копится «пустота», и из неё может подняться **Зеркало тишины**.
- **Сон.** После полной ночи в кровати дом «оседает», и тихая память редеет. В перегруженном чанке может присниться кошмар.
- **Мир помнит вас.** Мир запоминает, где вы умерли, ваш дом, большую стройку и тяжёлый бой. Когда вы вернётесь, **Тень прошлого** с вашим скином иногда повторит этот момент.
- **Живая память.** Репликант момента повторяет ваш старый жест, на покинутых местах вспыхивают силуэты и следы, линза называет, чья это память. Место может предложить осадок, который помогает или врёт. Осколок Шрама переписывает то, что место уже записало.
- **Мир с прошлым.** Ванильные постройки помнят с генерации: Древний город — тишину и смерть, крепость Незера — огонь, город Края — взрыв, шахты — путь и стройку, крепость Края — тишину и стройку. Постройки мода тоже приходят с памятью.
- **Знание.** **Фрагмент каталога** хранит открытые метки и формулы. **Полевой справочник** — 51 иллюстрированная страница обо всех системах. **Выпиской из хроники** можно поделиться знанием с другим игроком.

### Барабан составления: 15 формул

Формулы лежат в датапаке (`data/mnemolith/mnemolith/composition_formula`). Неудачное составление резко поднимает давление.

| Формула | Бланки | Результат |
| --- | --- | --- |
| Безмолвный осадок | Тишина + Игрок | осколок осадка «Тишина» |
| Заряженный удар | Молния + Огонь | осколок осадка «Молния» |
| Глухая тишь | Скалк + Тишина | осколок осадка «Скалк» |
| Тлеющий осадок | Огонь + Стройка | осколок осадка «Огонь» |
| Забвение | Забытое + Тишина | осколок осадка «Забытое» |
| Печать отпечатка | Падение + Стройка | Печать отпечатка |
| Ныряющий осадок | Падение + Игрок | осколок осадка «Падение» |
| Усиленная игла | Редстоун + Тишина | Усиленная игла |
| Избирательная тишина | Смерть + Путь | Избирательный глушащий камень |
| Путь сигнала | Редстоун + Путь + Стройка | осколок осадка «Игрок» |
| Триумф | Босс + Смерть | осколок осадка «Смерть» |
| Настроенная линза | Огонь + Путь | настроенная хроникальная линза |
| Двойная игла | Падение + Редстоун | Двойная игла |
| Гремучий осадок | Смерть + Тишина | осколок осадка «Взрыв» |
| Врата странника | Портал + Путь | осколок осадка «Портал» |

**Нравы осадков** (прививаются отголоску): Безмолвный, Могильный, Тлеющий, Ныряющий, Гремучий, Заряженный, Странствующий, Глубинный, Забытый.

### Отголоски

- **Бланк отголоска** записывает вас на 25 секунд, потом **отголосок** — ваша полупрозрачная копия — повторяет запись и остаётся помощником.
- **Работы:** повтор записи, добыча (с отвозом в привязанный сундук), стройка по чертежу (берёт блоки из сундука), ферма, лесоруб, уход за животными, охрана (на посту или «со мной», ближний бой, лук и арбалет, щит, сам надевает броню).
- **Приказы:** «Стою», «Иду за вами», «Иду к точке», «Повторяю рутину».
- **Вселение.** Зажмите линзу — розовый термовзгляд, отголоски светятся сквозь стены; в своего можно вселиться, `V` возвращает.
- **Прививки.** Бланк отпечатка, привитый отголоску, даёт нрав: безмолвный работает тихо, могильный отвлекает мобов, тлеющий не горит и переплавляет руду, ныряющий не боится падений, гремучий копает почти вдвое быстрее и взрывается при смерти, заряженный не боится молний, странствующий идёт за вами через портал, глубинный оставляет скалк, забытый стирает громкую память места.
- **Нить связи** соединяет двух отголосков: общие ауры, прыжки между ними во вселении, зеркальная стройка.
- **Дом отголосков** — здесь праздные отголоски спят, отсюда их будят и здесь им назначают роль оружием.
- **Улучшения:** Бланк хора (+1 отголосок), Бланк долгого дубля (+10 с записи), Бланк прочного тела (+здоровье).
- **Архивное хранилище** вытягивает громкие отпечатки из соседних чанков, ничего не стирая: хранимое давит на свой чанк, а в разломе выплёскивается обратно.

### Осадки, бури и Шрам

- **Осадок памяти.** Перегруженный чанк сгущает самую громкую память в бледный обрывок. Прочтите его линзой и поймайте иглой в **осколок осадка**: привить отголоску, выпустить обратно или (огонь, падение) применить на себя.
- **Буря воспоминаний** на 3×3 чанка: сбор 10 секунд, шесть волн осадков, которые разыгрывают память. Гасится на сборе глушащим камнем или шрамовым стеклом; невидимая буря рассеивается сама. После неё — **затишье** на игровой день.
- **Шрам** — босс из трёх и более уцелевших осадков; неуязвим, пока не прочитан линзой. Оставляет **осколок Шрама**, **шрамовое стекло** (оберег от бурь) и **сердце Шрама**.
- **Как залечить Шрам.** Три дня ухода за сердцем букетиком памяти — и оно становится **памятным камнем**, вокруг растёт роща незабудок, осадки рядом успокаиваются. На камне можно высечь выписку из хроники.

### Лощины памяти (биом)

- Редкий бледный биом внутри равнин, лесов, лугов и тайги: выцветший дёрн, пустокамень, разбитые колонны, берёзы и незабудки. Своя музыка и стеклянный перезвон.
- **Реколлит:** руда под дёрном, осколок заменяет аметист во всех рецептах мода.
- **Вспышки** повторяют то, что помнит чанк, и ловятся иглой с линзой. **Блёклые** копируют вспышки и до раскрытия линзой получают половину урона. **Кафедра** проигрывает память из бланка.
- **Лощины забывают:** непойманная вспышка или погибший блёклый оставляют метку «Забытое».
- **Реколлитовые вещи:** линза (ловит дальше), игла (ловит без линзы), кирка (читает память камня), фонарь (лощина рядом не забывает), **букетик памяти** (упокаивает блёклых и осадки).

### Боссы

- **Шрам** — из непогашенной бури.
- **Зеркало тишины** — из «пустоты» слишком долго заглушённого чанка.
- **Страж архива** — ПКМ по святыне архива строит временную арену из шрамового стекла; стража надо прочитать линзой, награда — сундук. Каждая святыня отвечает один раз (по умолчанию раз на мир).
- **Хранитель архива** — поднимается при первом открытии сундука погребённого архива, всегда оставляет бланк «Забытое».
- **Беспамятный** (0.6.0) — призывается образом лощины там, где лощина забыла не меньше трёх раз. Без линзы почти неуязвим, ест воспоминания, затемняет экран волной забвения, зовёт блёклых. С него падает **слеза Леты** для **венца памяти**.

### Измерения

- **Угольки (Незер):** **тлеющий** (поджигает, боится воды и снежков), **уголёк памяти** (топливо), **жаровня угольков** каждые 5 секунд сжигает самый громкий отпечаток чанка.
- **Бледный хор (Энд):** **тишунец** истончает тихую память и глушит игрока; нотный блок рядом раскалывает его. **Станок тишины** бережёт выбранную метку от тишунцев и жаровен.
- **Подводное течение (рыбалка):** в помнящем чанке улов иногда приносит бланк самого старого отпечатка (30%, +10% за «Морскую удачу», до 60%). **Приливная блесна** во второй руке +20% и тянет самое громкое.

### Существа

Эхо-странник, Архивариус (против него — резонаторная ловушка и приманка архивариуса), Репликант момента, Клещ архива, Свидетель, Охотник разлома, Блёклый, Тлеющий, Тишунец, Осадок памяти, Отголосок, Тень прошлого.

### Снаряжение и предметы

- **Броня:** тишины, могильная, решётки, Шрама.
- **Оружие:** Клинок отзыва, Копьё тишины, Могильная булава, Праща хора (болт памяти), Клеймо Шрама.
- **Иглы:** Усиленная игла (без износа), Двойная игла (два отпечатка), Реколлитовая игла. **Печать отпечатка** впечатывает память из бланка в мир. **Избирательный глушащий камень** глушит одну метку.
- **Компас памяти** указывает на ближайший осадок, разлом или обсерваторию.
- **Редстоун и база:** датчик давления, лампа давления (светится по полосе чанка).
- **Декор:** кирпичи, ступени, плиты и стены архивного пласта, глушащего камня и пустокамня; панели шрамового стекла.
- **Зачарования** «Приглушённость», «Возврат», «Долг». **Отделка брони «Эхо»** (материал — осколок осадка).
- **Мемориал игрока** (по умолчанию выключен) хранит вещи на месте гибели.
- **Музыкальная пластинка** «Воспоминание — Мнемолит».

### Мир и постройки

| Постройка | Где |
| --- | --- |
| Хроникальная обсерватория | Верхний мир (барабан, верстак, сундук) |
| Поле памяти | Верхний мир |
| Глухая часовня | Верхний мир |
| Затопленный архив | Верхний мир, под водой |
| Погребённый архив | Только в лощинах памяти, под дёрном |
| Пепельный архив | Незер, над лавовым морем |
| Немая библиотека | Внешние острова Энда |

Плюс подземные **архивные жилы** и **глухие карманы**. В нескольких постройках стоит святыня архива, в добыче бывают схемы архива.

### Жители

- **Хронист** — профессия с пятью уровнями сделок (бумага, чернила, бланки, справочник, линза, фрагмент каталога, выписка, карта к обсерватории, табличка, барабан). Картограф и странствующий торговец тоже продают карту к обсерватории.
- **Деревенский стул** — см. ниже.

### Визуальный язык памяти

- Свои частицы, цвет и звук у пяти событий: записано, стёрто, сбережено, поймано, забыто.
- Сильно помнящий чанк «дышит» оттенком по краю экрана и при первом входе говорит «Это место помнит.». Если постоять неподвижно, встают видения самых громких воспоминаний. Надетый венец окрашивает край экрана.

### Не выпущено (после 0.6.0)

Только визуальная полировка, слита в `main`, войдёт в следующую версию: подсказка сильной памяти — мягкий индиговый край, уступающий виньетке давления; видения поднимаются бледными столбами; искры «стёрто» видны на светлой земле; невидимый Беспамятный бледнее и серее; новая текстура станка тишины (пурпурная рама с бледными нитями); больше места у знака в плашке линзы.

## Требования и установка

| | |
| --- | --- |
| Игра | Minecraft **Java Edition 26.2** |
| Загрузчик | **NeoForge 26.2.x** (собрано против 26.2.0.88) |
| Java | 25 |
| Файл | `mnemolith-0.6.0.jar` — один и тот же на **клиенте и сервере** |

Других модов не нужно; JEI и WTHIT подхватываются, если стоят.

1. Установите NeoForge 26.2.x с [projects.neoforged.net](https://projects.neoforged.net/neoforged/neoforge).
2. Скачайте `mnemolith-0.6.0.jar` со страницы [Releases](https://github.com/sliva-name/mnemolith/releases) (не zip с исходниками).
3. Положите jar в папку `mods` (не распаковывайте) и запустите профиль NeoForge.

**Сервер:** тот же jar в `mods` выделенного сервера NeoForge 26.2.x. Версии клиента и сервера должны совпадать: в 0.6.0 сетевой протокол версии 10. Миры 0.1.3–0.5.0 открываются на 0.6.0; мир 0.6.0 на старую версию не откатывайте.

## Настройки

Экран в игре: Моды → Mnemolith → Config. Три файла:

| Файл | Разделы |
| --- | --- |
| `config/mnemolith-common.toml` | `difficulty`, `spawnRates`, `worldGen` (каждую постройку, жилы, карманы и лощины можно выключить), `gameplay` (запись каждой метки, пороги полос, извлечение, составление, каталог), `mobs`, `echo` (отголоски, работы, охрана, прививки, улучшения), `residue`, `relay`, `vault`, `recall`, `investigate`, `use`, `intervene`, `remember`, `armory`, `armoryMobs`, `sleep`, `worldMemory` (в т.ч. `structureSeeds`), `silenceCost`, `memorial`, `archiveGuardian` |
| `config/mnemolith-server.toml` (синхронизируется клиентам; переопределяется в `serverconfig` мира) | `server` (бури: вкл/выкл, потолок, рассеивание, затишье; журнал давления), `unremembered`, `embers`, `paleChorus`, `undertow` |
| `config/mnemolith-client.toml` | `visuals`: частицы и их плотность (0 выключает), потолок частиц, мерцание без линзы, опрос линзы, плашка линзы и её прозрачность, числа давления, виньетка давления, подсветка края при повторе прошлого момента, тряска камеры, обесцвечивание разлома, громкость, термовзгляд, помощь прицеливания, подсказки клавиш, вспышки лощин, звуки памяти, видения, подсказка сильной памяти, знак в линзе, оттенок венца |

**Языки:** русский и английский полностью; немецкий и украинский частично (около 100 строк).

## Мультиплеер и производительность

- Сервер решает всё: запись, давление, извлечение, составление. Открытия у каждого игрока свои.
- Тень прошлого видите только вы (опция `othersSee`). Здоровье Беспамятного растёт с числом игроков (+50% за каждого), слеза Леты у каждого своя. Глушение тишунца личное; жаровня и станок общие; улов на рыбалке у каждого свой.
- `/mnemolith mpsmoke` прогоняет проверку с двумя смоделированными игроками. Подробнее — [docs/multiplayer.md](docs/multiplayer.md).
- Дальность прорисовки сервера 8–10 подходит. `visuals.particleDensity = 0` выключает частицы. Замеры `/mnemolith perf` — [docs/performance.md](docs/performance.md).

## Известные проблемы

- Звуки 0.6.0 (события памяти, Беспамятный, тлеющий, тишунец) собраны из имеющихся сэмплов и ещё не проверены на слух. Ручной чеклист — [docs/playtest-0.6.0.md](docs/playtest-0.6.0.md).
- Расстояние обсерваторий задаёт набор структур датапака, а не `worldGen.structureSpacing`.
- Без аудиоустройства клиент пишет `Failed to open OpenAL device` и продолжает без звука.
- Выделенный сервер при первом запуске ждёт `eula=true` в `eula.txt`.

## Разработка и QA

Требования: JDK 25, Git. Gradle 9.2.1 идёт через wrapper. Маппинги — официальные Mojang, проект — [ModDevGradle 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle).

```bash
./gradlew build               # build/libs/mnemolith-<version>.jar
./gradlew runClient           # run/client
./gradlew runServer           # run/server
./gradlew runGameTestServer   # все игровые тесты без GUI (131), ненулевой код при провале
```

- **QA:** `/mnemolith qa` и 46 наборов `/mnemolith <имя>qa` (echoqa, graftqa, stormqa, hollowsqa … feel4qa, perfqa); каждый набор идёт и игровым тестом. Покрытие — [docs/qa-checklist.md](docs/qa-checklist.md).
- **CI:** `.github/workflows/build.yml` — `build` и `runGameTestServer` на Ubuntu с Temurin JDK 25; отчёт в артефакте `gametest-report`.
- **Арт** генерируется скриптами `tools/art/` (`python tools/art/build.py`) — [docs/asset-pipeline.md](docs/asset-pipeline.md).
- **Визуальный тур** (только для разработки): `-Dmnemolith.visualTour=true` снимает настоящие скриншоты сцен — [docs/visual-tour.md](docs/visual-tour.md).
- Дизайн — [docs/design/](docs/design/) и [дорожная карта](docs/design/roadmap.md).

### Раскладка

```text
com.mnemolith
  Mnemolith, MnemolithClient   common и client @Mod
  armory/                броня и оружие
  audio/  particle/      звуки и частицы
  client/                рендер, HUD, экраны, частицы, конфиг клиента, dev/ (визуальный тур)
  command/  command/qa/  /mnemolith и наборы QA
  common/  config/       общие имена и палитра; common/server/client конфиги
  compat/                JEI, WTHIT
  content/               блоки, предметы, вкладки, составление, справочник, хронист
  data/  network/        компоненты данных и пакеты
  echo/                  отголоски: запись, вселение, работы, прививки, осадки, бури, связь
  entity/                мобы и боссы
  event/                 обработчики событий
  fishing/               подводное течение
  gametest/              игровые тесты
  imprint/  pressure/    память чанка и полосы давления
  mixin/                 вставка биома
  recall/                живая память и «мир помнит вас»
  server/                @Mod выделенного сервера
  vault/                 архивные хранилища
  world/  worldgen/      память чанков, мемориалы, постройки, лощины, жилы
```

## Деревенский стул

Примерно в одном доме деревни из восьми стоит бирюзовый пластиковый стул. Он покачивается, поворачивается к игроку и проигрывает короткий голосовой клип. Удар рукой убирает его. Появляется только в новых чанках; `/mnemolith spawn pleading_chair` ставит его у ног.

This work is based on "Стул Алексея Навального" (https://sketchfab.com/3d-models/09a4efbb81324e809900aa793cb84c66) by Chaalen (https://sketchfab.com/Chaalen) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/)

Скан упрощён до mesh сущности. Голос — предоставленная пользователем мем-запись, конвертированная в ogg.

## Лицензия

All Rights Reserved (`LICENSE`). Файлы шаблона NeoForge MDK — под `TEMPLATE_LICENSE.txt`. Mod id `mnemolith`, пакет `com.mnemolith`.

## English

The world writes its history into stone. Deaths, explosions, falls, building and even your footsteps leave **imprints** in the chunk where they happened. Memory builds up as **pressure**, and the louder a place remembers, the harder it answers: with echoes, residual echoes, recollection storms and the Scar. You read that memory with a lens, draw it out with a needle, compose it into formulas, and decide whether to silence it, use it or heal it.

Latest release: **[Mnemolith 0.6.0 (beta)](https://github.com/sliva-name/mnemolith/releases/tag/v0.6.0)** · [all releases](https://github.com/sliva-name/mnemolith/releases)

### Contents

- [Features](#features)
- [Requirements and install](#requirements-and-install)
- [Configuration](#configuration)
- [Multiplayer and performance](#multiplayer-and-performance)
- [Known issues](#known-issues)
- [Development and QA](#development-and-qa)

## Features

### Memory and imprints

- **Imprints (15 tags).** Fire, Fall, Death, Build, Explosion, Silence, Player, Redstone, Path (about every 12 blocks), Lightning, Portal, Sculk, Boss, Trade and Forgotten. Forgotten is written only in the Memory Hollows.
- **Pressure bands.** Calm, saturated, overloaded, fracture. An overloaded chunk darkens the screen edge and shakes the camera; a fracture also drains colour from the world.
- The **Chronicle Lens** shows pressure as a pill above the hotbar and, since 0.6.0, the chunk's loudest memory as a coloured glyph. The **Extraction Needle** draws the strongest imprint into an **Imprint Slip**.
- The **Mute Stone** stops a chunk from recording. Silence has a cost: a chunk muted too long or too densely builds up "emptiness", and a **Silence Mirror** can rise from it.
- **Sleep.** A full night in bed lets a home "settle" and thins quiet memory. In an overloaded chunk you may have a nightmare.
- **The world remembers you.** It remembers where you died, your home, a big build and a hard fight. When you come back, your **Past Self**, in your skin, sometimes replays the moment.
- **Living memory.** A Moment Replicant repeats an old gesture of yours, abandoned places flash silhouettes and traces, and the lens names whose memory it is. A place may offer a residue that helps or lies. A Scar Fragment rewrites what a place already wrote.
- **A world with a past.** Vanilla structures remember from generation: Ancient City silence and death, Nether Fortress fire, End City explosion, mineshafts path and build, stronghold silence and build. The mod's own structures come with memory too.
- **Knowledge.** The **Catalog Fragment** keeps discovered tags and formulas. The **Field Guide** has 51 illustrated pages covering every system. A **Chronicle Transcript** shares knowledge with another player.

### Composition Reel: 15 formulas

Formulas live in the datapack (`data/mnemolith/mnemolith/composition_formula`). A failed composition spikes pressure.

| Formula | Slips | Result |
| --- | --- | --- |
| Hushed Shard | Silence + Player | Residual Shard (Silence) |
| Charged Bolt | Lightning + Fire | Residual Shard (Lightning) |
| Deep Hush | Sculk + Silence | Residual Shard (Sculk) |
| Kindled Shard | Fire + Build | Residual Shard (Fire) |
| Forgetting | Forgotten + Silence | Residual Shard (Forgotten) |
| Imprint Seal | Fall + Build | Imprint Seal |
| Plunging Shard | Fall + Player | Residual Shard (Fall) |
| Reinforced Needle | Redstone + Silence | Reinforced Needle |
| Selective Hush | Death + Path | Selective Mute Stone |
| Signal Path | Redstone + Path + Build | Residual Shard (Player) |
| Triumph | Boss + Death | Residual Shard (Death) |
| Tuned Lens | Fire + Path | tuned Chronicle Lens |
| Twin Needle | Fall + Redstone | Twin Needle |
| Volatile Shard | Death + Silence | Residual Shard (Explosion) |
| Wanderer's Gate | Portal + Path | Residual Shard (Portal) |

**Tempers** (grafted onto echoes): Hushed, Grave, Kindled, Plunging, Volatile, Charged, Wandering, Deep, Forgotten.

### Echoes

- A **Blank Echo Slip** records you for 25 seconds; then an **Echo**, a translucent copy of you, replays it and stays on as a helper.
- **Jobs:** replay, mining (with deposits into a linked chest), building from a blueprint (fetching blocks from a chest), farming, lumber, animal care, guard (on post or escorting you; melee, bow and crossbow, shield, equips armour itself).
- **Orders:** stay, follow me, return to point, repeat routine.
- **Possession.** Hold the lens for a pink thermal view that shows echoes through walls; possess your own echo and press `V` to come back.
- **Grafts.** An imprint slip grafted onto an echo gives it a temper: Hushed works quietly, Grave draws mobs, Kindled doesn't burn and smelts ore, Plunging ignores falls, Volatile digs almost twice as fast and explodes on death, Charged ignores lightning, Wandering follows you through portals, Deep leaves sculk, Forgotten erases the place's loud memory.
- **Relay Thread** links two echoes: shared auras, hopping between them while possessed, mirrored building.
- **Echo Home**: idle echoes sleep here, are woken here, and are given roles with weapons.
- **Upgrades:** Chorus Slip (+1 echo), Long Take Slip (+10 s recording), Sturdy Body Slip (+health).
- The **Archive Vault** draws loud imprints from nearby chunks without erasing anything: what it holds presses on its own chunk and spills back in a fracture.

### Residues, storms and the Scar

- **Residual Echo.** An overloaded chunk condenses its loudest memory into a pale shred. Read it with the lens and catch it with the needle as a **Residual Shard**: graft it, release it, or (fire, fall) use it on yourself.
- **Recollection storm** over 3×3 chunks: 10 s gathering, six waves of residues acting out their memory. Snuff it while it gathers with a mute stone or Scar Glass; an unwatched storm fades. Afterwards comes a **lull** for one in-game day.
- **The Scar** is a boss formed from three or more surviving residues, immune until read with the lens. It leaves a **Scar Fragment**, **Scar Glass** (a storm ward) and a **Scar Heart**.
- **Mending the Scar.** Tend the heart with a Remembrance Posy on three days and it becomes a **Memorial Stone**; a forget-me-not grove grows and nearby residues calm down. A chronicle transcript can be carved into the stone.

### Memory Hollows (biome)

- A rare pale biome inside plains, forests, meadows and taiga: Faded Turf, Hollowstone, broken columns, birches and forget-me-nots, with its own music and glassy chimes.
- **Recollite:** ore under the turf; the shard replaces amethyst in every mod recipe.
- **Flickers** replay what the chunk remembers and can be caught with needle and lens. **Faded** copy flickers and take half damage until the lens reveals them. A **lectern** plays back a slip's memory.
- **Hollows forget:** an uncaught flicker or a slain Faded leaves a Forgotten imprint.
- **Recollite gear:** lens (catches further), needle (catches without a lens), pickaxe (reads the stone's memory), lantern (nearby hollow doesn't forget), **Remembrance Posy** (lays Faded and residues to rest).

### Bosses

- **The Scar** — from an unsnuffed storm.
- **Silence Mirror** — from the emptiness of a chunk muted too long.
- **Archive Guardian** — right-click an Archive Shrine to raise a temporary Scar Glass arena; read the guardian with the lens; the reward is a chest. Each shrine answers once (once per world by default).
- **Archive Keeper** — rises the first time a Sunken Archive chest is opened; always drops a Forgotten slip.
- **The Unremembered** (0.6.0) — summoned with a Hollow Effigy where a hollow has forgotten at least three times. Nearly invulnerable without the lens, eats memories, darkens the screen with a wave of forgetting, calls the Faded. Drops a **Lethe Tear** for the **Remembering Circlet**.

### Dimensions

- **Embers (Nether):** the **Cinderkin** (sets you alight, hurt by water and snowballs), **Memory Ember** (fuel), **Ember Brazier** burns the chunk's loudest imprint every 5 s.
- **Pale Chorus (End):** the **Hushling** thins quiet memory and hushes players; a note block nearby shatters it. The **Hush Loom** protects a chosen tag from hushlings and braziers.
- **Undertow (fishing):** in a remembering chunk, a catch sometimes brings up a slip of the oldest imprint (30%, +10% per Luck of the Sea, up to 60%). A **Tide Lure** in the offhand adds 20% and pulls the loudest.

### Creatures

Echo Strider, Archivist (countered by the Resonator Trap and Archivist Bait), Moment Replicant, Ledger Mite, Kin Witness, Fracture Stalker, Faded, Cinderkin, Hushling, Residual Echo, Echo, Past Self.

### Gear and items

- **Armour:** Hush, Grave, Lattice, Scar.
- **Weapons:** Recall Blade, Hush Spear, Grave Maul, Chorus Sling (Memory Bolt), Scar Brand.
- **Needles:** Reinforced Needle (no wear), Twin Needle (two imprints), Recollite Needle. The **Imprint Seal** stamps a slip's memory into the world. The **Selective Mute Stone** mutes one tag.
- **Memory Compass** points to the nearest residue, fracture or observatory.
- **Redstone and base:** Pressure Sensor, Pressure Lamp (lights by the chunk's band).
- **Decor:** bricks, stairs, slabs and walls of Archival Stratum, Mute Stone and Hollowstone; Scar Glass Panes.
- **Enchantments** Hush, Return, Debt. **Echo Armor Trim** (material: Residual Shard).
- **Player Memorial** (off by default) keeps your items where you died.
- **Music disc** "Recollection — Mnemolith".

### World and structures

| Structure | Where |
| --- | --- |
| Chronicle Observatory | Overworld (reel, crafting table, chest) |
| Memory Field | Overworld |
| Hush Chapel | Overworld |
| Flooded Archive | Overworld, underwater |
| Sunken Archive | Memory Hollows only, under the turf |
| Ashen Archive | Nether, above the lava sea |
| Mute Library | End outer islands |

Plus underground **archival veins** and **mute pockets**. Several structures hold an Archive Shrine; loot can include Archive Schematics.

### Villagers

- **Chronicler** — a profession with five trade tiers (paper, ink, slips, guide, lens, catalog fragment, transcript, observatory map, tablet, reel). Cartographers and wandering traders also sell the observatory map.
- **Village chair** — see below.

### Visual language of memory

- Five memory events each have their own particles, colour and sound: written, erased, kept, caught, forgotten.
- A strongly remembering chunk "breathes" a tint at the screen edge and says "This place remembers." on first entry. Stand still and after-images of the loudest memories rise. A worn circlet tints the screen edge.

### Unreleased (after 0.6.0)

Visual polish only, merged into `main`, coming in the next version: the strong-memory hint is a soft indigo edge that yields to the pressure vignette; after-images rise as pale columns; erased motes show on light ground; the unseen Unremembered is fainter and greyer; new Hush Loom texture (purpur frame, pale threads); more room around the lens glyph.

## Requirements and install

| | |
| --- | --- |
| Game | Minecraft **Java Edition 26.2** |
| Loader | **NeoForge 26.2.x** (built against 26.2.0.88) |
| Java | 25 |
| File | `mnemolith-0.6.0.jar` — the same jar on **client and server** |

No other mods needed; JEI and WTHIT are supported if installed.

1. Install NeoForge 26.2.x from [projects.neoforged.net](https://projects.neoforged.net/neoforged/neoforge).
2. Download `mnemolith-0.6.0.jar` from [Releases](https://github.com/sliva-name/mnemolith/releases) (not the source zip).
3. Put the jar in your `mods` folder (don't unzip it) and launch the NeoForge profile.

**Server:** the same jar in the `mods` folder of a NeoForge 26.2.x dedicated server. Client and server versions must match: 0.6.0 uses network protocol 10. Worlds from 0.1.3–0.5.0 open on 0.6.0; don't roll a 0.6.0 world back.

## Configuration

In-game screen: Mods → Mnemolith → Config. Three files:

| File | Sections |
| --- | --- |
| `config/mnemolith-common.toml` | `difficulty`, `spawnRates`, `worldGen` (every structure, veins, pockets and hollows can be switched off), `gameplay` (writing per tag, band thresholds, extraction, composition, catalog), `mobs`, `echo` (echoes, jobs, guard, grafts, upgrades), `residue`, `relay`, `vault`, `recall`, `investigate`, `use`, `intervene`, `remember`, `armory`, `armoryMobs`, `sleep`, `worldMemory` (incl. `structureSeeds`), `silenceCost`, `memorial`, `archiveGuardian` |
| `config/mnemolith-server.toml` (synced to clients; override per world in `serverconfig`) | `server` (storms on/off, cap, unwatched fade, lull; pressure logging), `unremembered`, `embers`, `paleChorus`, `undertow` |
| `config/mnemolith-client.toml` | `visuals`: particles and density (0 = off), particle cap, ambient shimmer without lens, lens poll, lens pill and opacity, numeric pressure, pressure vignette, edge glow during a past-moment replay, camera shake, fracture fringe, volume, thermal view, aim assist, key hints, hollow flickers, memory sounds, after-images, strong-memory hint, lens glyph, circlet tint |

**Languages:** Russian and English complete; German and Ukrainian partial (about 100 strings).

## Multiplayer and performance

- The server decides everything: writing, pressure, extraction, composition. Discoveries are per player.
- Only you see your Past Self (option `othersSee`). The Unremembered's health scales with players (+50% each) and every participant gets a Lethe Tear. A hushling's hush is personal; braziers and looms are shared; fishing catches are per player.
- `/mnemolith mpsmoke` runs a check with two simulated players. See [docs/multiplayer.md](docs/multiplayer.md).
- Server view distance 8–10 is fine. `visuals.particleDensity = 0` turns particles off. `/mnemolith perf` numbers: [docs/performance.md](docs/performance.md).

## Known issues

- The 0.6.0 sounds (memory events, Unremembered, Cinderkin, Hushling) are built from existing samples and not yet checked by ear. Manual checklist: [docs/playtest-0.6.0.md](docs/playtest-0.6.0.md).
- Observatory spacing comes from the datapack structure set, not `worldGen.structureSpacing`.
- Without an audio device the client logs `Failed to open OpenAL device` and runs silently.
- A dedicated server waits for `eula=true` in `eula.txt` on first start.

## Development and QA

Requires JDK 25 and Git. Gradle 9.2.1 comes with the wrapper. Mojang official mappings; project based on the [ModDevGradle 26.2 MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle).

```bash
./gradlew build               # build/libs/mnemolith-<version>.jar
./gradlew runClient           # run/client
./gradlew runServer           # run/server
./gradlew runGameTestServer   # all game tests headless (131), non-zero exit on failure
```

- **QA:** `/mnemolith qa` plus 46 `/mnemolith <name>qa` suites (echoqa, graftqa, stormqa, hollowsqa … feel4qa, perfqa); each also runs as a game test. Coverage: [docs/qa-checklist.md](docs/qa-checklist.md).
- **CI:** `.github/workflows/build.yml` runs `build` and `runGameTestServer` on Ubuntu with Temurin JDK 25; report in the `gametest-report` artifact.
- **Art** is generated by `tools/art/` (`python tools/art/build.py`): [docs/asset-pipeline.md](docs/asset-pipeline.md).
- **Visual tour** (dev only): `-Dmnemolith.visualTour=true` takes real rendered screenshots of staged scenes: [docs/visual-tour.md](docs/visual-tour.md).
- Design notes: [docs/design/](docs/design/) and the [roadmap](docs/design/roadmap.md).

### Layout

```text
com.mnemolith
  Mnemolith, MnemolithClient   common and client @Mod
  armory/                armour and weapons
  audio/  particle/      sounds and particles
  client/                rendering, HUD, screens, client config, dev/ (visual tour)
  command/  command/qa/  /mnemolith and the QA suites
  common/  config/       shared names and palette; common/server/client specs
  compat/                JEI, WTHIT
  content/               blocks, items, tabs, composition, guide, chronicler
  data/  network/        data components and payloads
  echo/                  echoes: recording, possession, jobs, grafts, residues, storms, relay
  entity/                mobs and bosses
  event/                 event handlers
  fishing/               undertow
  gametest/              game tests
  imprint/  pressure/    chunk memory and pressure bands
  mixin/                 biome injection
  recall/                living memory and "the world remembers you"
  server/                dedicated server @Mod
  vault/                 archive vaults
  world/  worldgen/      chunk memory, memorials, structures, hollows, veins
```

## Village chair

About one village house in eight has a turquoise plastic chair. It bobs, turns toward a nearby player and plays a short voice clip. Punching removes it. It only appears in newly generated chunks; `/mnemolith spawn pleading_chair` places one where you stand.

This work is based on "Стул Алексея Навального" (https://sketchfab.com/3d-models/09a4efbb81324e809900aa793cb84c66) by Chaalen (https://sketchfab.com/Chaalen) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/)

The scan is simplified into an entity mesh. The voice is a user-provided meme recording, converted to ogg.

## License

All Rights Reserved (`LICENSE`). NeoForge MDK template files remain under `TEMPLATE_LICENSE.txt`. Mod id `mnemolith`, package `com.mnemolith`.

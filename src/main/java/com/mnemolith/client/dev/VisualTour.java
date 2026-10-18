package com.mnemolith.client.dev;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mnemolith.Mnemolith;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.entity.mob.Hushling;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFeel;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev-only visual tour. Off unless the JVM runs with {@code -Dmnemolith.visualTour=true} (it is never registered
 * otherwise). From the title screen it creates a fresh creative world, stages each scene with server commands and
 * direct server calls on a sky platform (or a located natural spot), moves the camera, waits for rendering and saves a
 * screenshot to {@code mnemolith.visualTour.dir} (default {@code /workspace/visual-tour}), then quits. No input is
 * injected. {@code mnemolith.visualTour.only} (comma list) limits the scenes; {@code mnemolith.visualTour.lang} picks a
 * language code for a second pass (for example ru_ru).
 */
public final class VisualTour {
    public static final String PROPERTY = "mnemolith.visualTour";
    private static final int Y = 150;
    private static final Pattern COORDS = Pattern.compile("\\[(-?\\d+), (~|-?\\d+), (-?\\d+)\\]");

    private record Scene(String name, Consumer<Ctx> setup, double[] camera, int settle, boolean hud, Consumer<Ctx> pulse, Consumer<Minecraft> client) {}

    /** What a scene can do on the server thread. */
    public static final class Ctx {
        final MinecraftServer server;
        final ServerLevel level;
        final ServerPlayer player;
        String captured = "";

        Ctx(MinecraftServer server, ServerPlayer player) {
            this.server = server;
            this.player = player;
            this.level = player.level();
        }

        void cmd(String command) {
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    Ctx.this.captured += message.getString() + "\n";
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            CommandSourceStack source = this.server.createCommandSourceStack().withSource(capture).withLevel(this.level)
                    .withPosition(this.player.position()).withEntity(this.player);
            try {
                this.server.getCommands().getDispatcher().execute(command, source);
            } catch (Exception e) {
                Mnemolith.LOGGER.warn("Visual tour command failed: {}: {}", command, e.getMessage());
            }
        }

        void tp(double x, double y, double z, float yaw, float pitch) {
            cmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        }

        <T extends net.minecraft.world.entity.Entity> T summon(String id, double x, double z) {
            cmd(String.format(java.util.Locale.ROOT, "summon %s %.2f %d %.2f {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}", id, x, Y, z));
            List<net.minecraft.world.entity.Entity> found = this.level.getEntities((net.minecraft.world.entity.Entity) null,
                    new AABB(x - 1, Y - 1, z - 1, x + 1, Y + 4, z + 1), e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(id));
            @SuppressWarnings("unchecked")
            T out = found.isEmpty() ? null : (T) found.get(0);
            return out;
        }

        void write(int x, int z, ImprintTag tag) {
            ImprintWriter.write(this.level, new BlockPos(x, Y, z), List.of(tag), null, false);
        }
    }

    private static final List<Scene> SCENES = new ArrayList<>();
    private static int index = -1;
    private static int wait;
    private static int stage;
    private static Scene current;
    private static boolean started;
    private static Ctx ctx;
    private static final double[] FRONT = {8.5, Y, 4.5, 0, 14};

    private VisualTour() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    private static void platform(Ctx c, String floor) {
        c.cmd("mnemolith debug storm stop");
        for (var e : c.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(-120, Y - 40, -120, 140, Y + 60, 140),
                e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            e.discard();
        }
        c.cmd("fill -6 " + Y + " -6 22 " + (Y + 14) + " 22 air");
        c.cmd("fill -6 " + (Y - 1) + " -6 22 " + (Y - 1) + " 22 " + floor);
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                LoadedChunkMemory.clear(c.level.getChunk(cx, cz));
            }
        }
        c.cmd("item replace entity @s weapon.mainhand with air");
        c.cmd("item replace entity @s weapon.offhand with air");
        c.cmd("item replace entity @s armor.head with air");
        c.cmd("effect clear @s");
    }

    private static int firstOf(com.mnemolith.pressure.PressureBand band) {
        for (int p = 0; p < 1_000_000; p++) {
            if (com.mnemolith.pressure.MemoryPressure.band(p) == band) {
                return p;
            }
        }
        return 0;
    }

    private static void scene(String name, Consumer<Ctx> setup, double[] camera, int settle, boolean hud) {
        scene(name, setup, camera, settle, hud, null, null);
    }

    private static void scene(String name, Consumer<Ctx> setup, double[] camera, int settle, boolean hud, Consumer<Ctx> pulse, Consumer<Minecraft> client) {
        SCENES.add(new Scene(name, setup, camera, settle, hud, pulse, client));
    }

    private static void mob(String name, String id, Consumer<net.minecraft.world.entity.Entity> after) {
        scene(name, c -> {
            platform(c, "smooth_stone");
            var e = c.summon(id, 8.5, 8.5);
            if (e != null && after != null) {
                after.accept(e);
            }
        }, FRONT, 60, false);
    }

    private static void held(String name, String item) {
        scene(name, c -> {
            platform(c, "moss_block");
            c.cmd("item replace entity @s weapon.mainhand with " + item);
        }, new double[] {8.5, Y, 4.5, 0, 20}, 40, true);
    }

    private static void cue(String name, MemoryFeel.Kind kind) {
        scene("cue_" + name, c -> platform(c, "smooth_stone"), new double[] {8.5, Y, 5.5, 0, 25}, 30, false,
                c -> MemoryFeel.cue(c.level, kind, new BlockPos(8, Y, 8), 12), null);
    }

    private static void build() {
        String keep = System.getProperty("mnemolith.visualTour.only", "");
        mob("faded", "mnemolith:faded", null);
        mob("faded_revealed", "mnemolith:faded", e -> ((Faded) e).reveal(null));
        mob("archive_keeper", "mnemolith:faded", e -> ((Faded) e).makeKeeper(e.blockPosition()));
        scene("unremembered", c -> {
            platform(c, "smooth_stone");
            c.summon("mnemolith:unremembered", 8.5, 9.5);
        }, new double[] {8.5, Y, 2.5, 0, 0}, 60, false);
        scene("unremembered_revealed", c -> {
            platform(c, "smooth_stone");
            Unremembered u = c.summon("mnemolith:unremembered", 8.5, 9.5);
            if (u != null) {
                u.reveal();
            }
        }, new double[] {8.5, Y, 2.5, 0, 0}, 20, false);
        mob("cinderkin", "mnemolith:cinderkin", null);
        mob("hushling", "mnemolith:hushling", null);
        mob("hushling_shattered", "mnemolith:hushling", e -> ((Hushling) e).shatter());
        mob("archive_guardian", "mnemolith:archive_guardian", null);
        scene("ember_brazier", c -> {
            platform(c, "polished_blackstone");
            c.cmd("setblock 7 " + Y + " 8 mnemolith:ember_brazier");
            c.cmd("setblock 9 " + Y + " 8 mnemolith:ember_brazier[lit=true,charge=8]");
        }, new double[] {8.5, Y, 5.5, 0, 25}, 60, false);
        scene("hush_loom", c -> {
            platform(c, "end_stone_bricks");
            c.cmd("setblock 7 " + Y + " 8 mnemolith:hush_loom");
            c.cmd("setblock 9 " + Y + " 8 mnemolith:hush_loom[threaded=true]");
        }, new double[] {8.5, Y + 0.5, 5.5, 0, 35}, 40, false);
        scene("memorial_grove", c -> {
            platform(c, "moss_block");
            c.cmd("setblock 8 " + Y + " 8 mnemolith:memorial_stone");
            int[][] spots = {{2, 1}, {-2, 2}, {1, -2}, {-1, -3}, {3, -1}, {-3, 0}};
            for (int[] s : spots) {
                c.cmd("setblock " + (8 + s[0]) + " " + Y + " " + (8 + s[1]) + " mnemolith:forget_me_not");
            }
        }, new double[] {8.5, Y, 3.5, 0, 25}, 40, false);
        scene("residue", c -> {
            platform(c, "smooth_stone");
            c.player.teleportTo(7.5, Y, 9.5);
            c.cmd("mnemolith debug residue fire");
            c.player.teleportTo(10.0, Y, 9.5);
            c.cmd("mnemolith debug residue death");
            c.cmd("execute as @e[type=mnemolith:residue] run data merge entity @s {NoAI:1b}");
        }, new double[] {8.5, Y, 4.5, 0, 10}, 40, false);
        scene("storm", c -> {
            platform(c, "smooth_stone");
            c.player.teleportTo(8.5, Y, 8.5);
            c.cmd("mnemolith debug storm start");
        }, new double[] {8.5, Y, -1.5, 0, 10}, 120, true);
        cue("written", MemoryFeel.Kind.WRITTEN);
        cue("erased", MemoryFeel.Kind.ERASED);
        cue("kept", MemoryFeel.Kind.KEPT);
        cue("caught", MemoryFeel.Kind.CAUGHT);
        cue("forgotten", MemoryFeel.Kind.FORGOTTEN);
        // baselines first: the hint and circlet tint fade out slowly and would leak into a later baseline
        scene("edge_baseline", c -> platform(c, "moss_block"), new double[] {8.5, Y, 5.5, 0, 10}, 40, true);
        scene("strong_baseline", c -> platform(c, "smooth_stone"), new double[] {8.5, Y, 3.5, 0, 12}, 40, true);
        // strong hint alone: a saturated (not overloaded) chunk, written the normal way
        scene("strong_hint", c -> {
            platform(c, "smooth_stone");
            for (int i = 0; i < 6; i++) {
                c.write(6 + i, 10, ImprintTag.DEATH);
            }
        }, new double[] {8.5, Y, 3.5, 0, 12}, 35, true);
        // the pressure vignette on its own, with the lens out so the band is visible in the pill
        for (String[] v : new String[][] {{"overloaded_vignette", "OVERLOADED"}, {"fracture_vignette", "FRACTURE"}, {"hint_plus_overloaded", "OVERLOADED"}}) {
            Consumer<Ctx> hold = c -> { Mnemolith.LOGGER.info("Visual tour: hold {} = {}", v[1], firstOf(com.mnemolith.pressure.PressureBand.valueOf(v[1]))); LoadedChunkMemory.getOrCreate(c.level.getChunk(0, 0)).setCachedPressure(firstOf(com.mnemolith.pressure.PressureBand.valueOf(v[1]))); };
            scene(v[0], c -> {
                platform(c, "smooth_stone");
                com.mnemolith.command.qa.Feel3Qa.saturate(LoadedChunkMemory.getOrCreate(c.level.getChunk(0, 0)), new BlockPos(6, Y, 12), 2, c.level.getGameTime());
                hold.accept(c);
                c.cmd("item replace entity @s weapon.mainhand with mnemolith:chronicle_lens");
                if (!v[0].startsWith("hint")) {
                    c.tp(40.5, Y + 30, 40.5, 0, 0);
                }
            }, new double[] {8.5, Y, 3.5, 0, 12}, 160, true, hold, mc -> {});
        }
        // after-images: a saturated chunk (not louder, so no vignette or residues), the camera close and side-on
        scene("afterimages", c -> {
            platform(c, "smooth_stone");
            for (int i = 0; i < 6; i++) {
                c.write(6 + i, 10, ImprintTag.DEATH);
            }
        }, new double[] {8.5, Y, 4.5, 0, -8}, 120, true, c -> c.cmd("kill @e[type=!player]"), mc -> {});
        // after-echoes: one saturated chunk per kind, camera close and side-on; motion differs by kind
        for (ImprintTag tag : new ImprintTag[] {ImprintTag.EXPLOSION, ImprintTag.DEATH, ImprintTag.PATH, ImprintTag.BUILD, ImprintTag.PORTAL, ImprintTag.FORGOTTEN}) {
            scene("afterecho_" + tag.getSerializedName(), c -> {
                platform(c, "smooth_stone");
                // staged directly (like Feel3Qa.saturate): light tags alone would not reach the saturated band
                var memory = LoadedChunkMemory.getOrCreate(c.level.getChunk(0, 0));
                for (int i = 0; i < 6; i++) {
                    memory.addImprint(new com.mnemolith.imprint.Imprint(tag, 2, new BlockPos(6 + i, Y, 10), java.util.Optional.empty(), i, c.level.getGameTime(), false), 64);
                }
                memory.setCachedPressure(com.mnemolith.command.qa.Feel3Qa.saturatedPressure());
            }, new double[] {8.5, Y, 3.5, 0, -5}, 95, true, c -> {
                c.cmd("kill @e[type=!player]");
                LoadedChunkMemory.getOrCreate(c.level.getChunk(0, 0)).setCachedPressure(com.mnemolith.command.qa.Feel3Qa.saturatedPressure());
            }, mc -> {});
        }

        // visible memory: the same staged chunk at each band, no HUD — the band must read from the world alone
        for (String[] v : new String[][] {{"traces_calm", "CALM"}, {"traces_saturated", "SATURATED"}, {"traces_overloaded", "OVERLOADED"}, {"traces_fracture", "FRACTURE"}}) {
            Consumer<Ctx> hold = c -> LoadedChunkMemory.getOrCreate(c.level.getChunk(0, 0))
                    .setCachedPressure(v[1].equals("CALM") ? 3 : firstOf(com.mnemolith.pressure.PressureBand.valueOf(v[1])));
            scene(v[0], c -> {
                platform(c, "smooth_stone");
                for (int i = 0; i < 6; i++) {
                    c.write(3 + i * 2, 12, ImprintTag.DEATH);
                }
                hold.accept(c);
            }, new double[] {8.5, Y + 4, 0.5, 0, 22}, 240, false, hold, mc -> {});
        }

        // visible memory by kind: an overloaded chunk per dominant kind, lens in hand so the snapshot names it
        for (ImprintTag kind : new ImprintTag[] {ImprintTag.EXPLOSION, ImprintTag.DEATH, ImprintTag.PATH, ImprintTag.BUILD, ImprintTag.FIRE}) {
            Consumer<Ctx> hold = c -> LoadedChunkMemory.getOrCreate(c.level.getChunk(0, 0))
                    .setCachedPressure(firstOf(com.mnemolith.pressure.PressureBand.OVERLOADED));
            scene("traces_kind_" + kind.getSerializedName(), c -> {
                platform(c, "smooth_stone");
                for (int i = 0; i < 6; i++) {
                    c.write(3 + i * 2, 12, kind);
                }
                hold.accept(c);
                c.cmd("item replace entity @s weapon.mainhand with mnemolith:chronicle_lens");
            }, new double[] {8.5, Y + 4, 0.5, 0, 22}, 240, false, hold, mc -> {});
        }
        held("held_recollite_pickaxe", "mnemolith:recollite_pickaxe");
        held("held_recollite_lantern", "mnemolith:recollite_lantern");
        held("held_remembrance_posy", "mnemolith:remembrance_posy");
        held("held_remembering_circlet", "mnemolith:remembering_circlet");
        held("held_hollow_effigy", "mnemolith:hollow_effigy");
        scene("lens_hud", c -> {
            platform(c, "smooth_stone");
            for (int i = 0; i < 6; i++) {
                c.write(6 + i, 10, ImprintTag.DEATH);
            }
            c.write(9, 12, ImprintTag.BUILD);
            c.cmd("item replace entity @s weapon.mainhand with mnemolith:chronicle_lens");
        }, new double[] {8.5, Y, 5.5, 0, 20}, 60, true);
        scene("circlet_tint", c -> {
            platform(c, "moss_block");
            c.cmd("item replace entity @s armor.head with mnemolith:remembering_circlet");
        }, new double[] {8.5, Y, 5.5, 0, 10}, 50, true);
        scene("hollows_staged", c -> {
            platform(c, "mnemolith:hollow_turf");
            c.cmd("fillbiome -6 " + (Y - 4) + " -6 22 " + (Y + 20) + " 22 mnemolith:memory_hollows");
            c.cmd("setblock 10 " + Y + " 11 mnemolith:recollite_lantern");
            c.cmd("fill 4 " + Y + " 12 6 " + (Y + 2) + " 12 mnemolith:hollowstone_bricks");
            c.cmd("setblock 8 " + Y + " 10 mnemolith:forget_me_not");
            c.summon("mnemolith:faded", 7.0, 10.0);
        }, new double[] {8.5, Y, 4.5, 0, 10}, 80, false);
        scene("control_glowing_zombie", c -> {
            platform(c, "smooth_stone");
            c.summon("minecraft:zombie", 8.5, 8.5);
            c.cmd("effect give @e[type=minecraft:zombie] glowing infinite 0 true");
        }, FRONT, 40, false);
        scene("guide_unremembered", c -> platform(c, "smooth_stone"), FRONT, 20, true, null, mc -> guide(mc, "unremembered"));
        scene("guide_brazier", c -> { }, FRONT, 20, true, null, mc -> guide(mc, "brazier"));
        scene("guide_hushling", c -> { }, FRONT, 20, true, null, mc -> guide(mc, "hushling"));
        scene("guide_mending", c -> { }, FRONT, 20, true, null, mc -> guide(mc, "mending"));
        scene("guide_loom", c -> { }, FRONT, 20, true, null, mc -> guide(mc, "loom"));
        scene("guide_undertow", c -> { }, FRONT, 20, true, null, mc -> guide(mc, "undertow"));
        scene("creative_tab", c -> { }, FRONT, 20, true, null, VisualTour::creativeTab);
        for (String item : new String[] {"chronicle_lens", "remembering_circlet", "recollite_lantern", "hollow_effigy", "remembrance_posy", "ember_brazier", "hush_loom"}) {
            scene("tooltip_" + item, c -> platform(c, "smooth_stone"), FRONT, 15, true, null,
                    mc -> mc.gui.setScreen(new TipScreen(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, item))))));
        }
        scene("natural_memory_hollows", c -> natural(c, "locate biome mnemolith:memory_hollows"), null, 260, false);
        for (String s : new String[] {"sunken_archive", "chronicle_observatory", "hush_chapel", "memory_field"}) {
            scene("natural_" + s, c -> natural(c, "locate structure mnemolith:" + s), null, 260, false);
        }
        if (!keep.isEmpty()) {
            List<String> only = List.of(keep.split(","));
            SCENES.removeIf(s -> only.stream().noneMatch(s.name()::startsWith));
        }
    }

    private static void natural(Ctx c, String locate) {
        platform(c, "smooth_stone");
        c.captured = "";
        c.cmd(locate);
        Matcher m = COORDS.matcher(c.captured);
        if (!m.find()) {
            Mnemolith.LOGGER.warn("Visual tour: {} found nothing: {}", locate, c.captured.trim());
            return;
        }
        int x = Integer.parseInt(m.group(1));
        int z = Integer.parseInt(m.group(3));
        c.level.getChunk(x >> 4, z >> 4);
        int y;
        if ("~".equals(m.group(2))) {
            y = c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 6;
        } else {
            y = Integer.parseInt(m.group(2)) + 2;
            c.cmd("effect give @s night_vision infinite 0 true");
        }
        Mnemolith.LOGGER.info("Visual tour: {} -> {} {} {}", locate, x, y, z);
        c.tp(x + 0.5, y, z - 8.5, 0, 25);
    }

    private static void guide(Minecraft mc, String match) {
        var screen = new com.mnemolith.client.gui.FieldGuideScreen();
        mc.gui.setScreen(screen);
        for (int i = 0; i < com.mnemolith.content.guide.GuideBook.pageCount(); i++) {
            if (com.mnemolith.content.guide.GuideBook.pageId(i).equals(match)) {
                screen.showPage(i);
                return;
            }
        }
    }

    private static void creativeTab(Minecraft mc) {
        var screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true);
        mc.gui.setScreen(screen);
        try {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "mnemolith"));
            var method = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            method.setAccessible(true);
            method.invoke(screen, tab);
        } catch (ReflectiveOperationException e) {
            Mnemolith.LOGGER.warn("Visual tour: could not select the creative tab: {}", e.toString());
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        // toasts (advancements, recipe unlocks) keep arriving during a scene; clear them every tick, not only pre-shot
        Minecraft.getInstance().gui.toastManager().clear();
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            var open = mc.gui.screen();
            if (open != null && !(open instanceof TitleScreen)
                    && (open.getClass().getSimpleName().contains("LoadingError") || open.getClass().getSimpleName().contains("Onboarding"))) {
                // the dev run's mod-loading warnings and the first-run accessibility screen: go on to the title screen
                mc.gui.setScreen(new TitleScreen());
                return;
            }
            if (mc.gui.screen() instanceof TitleScreen) {
                started = true;
                build();
                String lang = System.getProperty("mnemolith.visualTour.lang", "");
                if (!lang.isEmpty() && !lang.equals(mc.getLanguageManager().getSelected())) {
                    mc.getLanguageManager().setSelected(lang);
                    mc.options.languageCode = lang;
                    mc.reloadResourcePacks();
                }
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(6);
                LevelSettings settings = new LevelSettings("visual-tour", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("visual-tour-" + System.currentTimeMillis(), settings,
                        new WorldOptions(424242L, true, false), WorldPresets::createNormalWorldDimensions, mc.gui.screen());
                Mnemolith.LOGGER.info("Visual tour: creating world with {} scenes", SCENES.size());
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        if (ctx == null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) {
                return;
            }
            ctx = new Ctx(server, sp);
            server.execute(() -> {
                ctx.cmd("gamerule advance_time false");
                ctx.cmd("gamerule advance_weather false");
                ctx.cmd("gamerule spawn_mobs false");
                ctx.cmd("time set 6000");
                ctx.cmd("weather clear");
                ctx.tp(0.5, Y, -5.5, 0, 12);
                platform(ctx, "smooth_stone");
            });
            wait = 200;
            return;
        }
        if (wait > 0) {
            wait--;
            if (current != null && current.pulse() != null && stage == 1 && wait % 15 == 3) {
                Scene s = current;
                server.execute(() -> s.pulse().accept(ctx));
            }
            return;
        }
        if (stage == 1) {
            shoot(mc, current.name());
            stage = 0;
            wait = 5;
            return;
        }
        index++;
        if (index >= SCENES.size()) {
            Mnemolith.LOGGER.info("Visual tour: done, {} scenes", SCENES.size());
            mc.stop();
            return;
        }
        current = SCENES.get(index);
        mc.gui.setScreen(null);
        if (mc.gui.hud.isHidden() == current.hud()) {
            mc.gui.hud.toggle();
        }
        Scene s = current;
        server.execute(() -> {
            try {
                s.setup().accept(ctx);
                if (s.camera() != null) {
                    double[] k = s.camera();
                    ctx.tp(k[0], k[1], k[2], (float) k[3], (float) k[4]);
                }
            } catch (RuntimeException e) {
                Mnemolith.LOGGER.warn("Visual tour scene {} failed", s.name(), e);
            }
        });
        if (s.client() != null) {
            s.client().accept(mc);
        }
        stage = 1;
        wait = s.settle();
        Mnemolith.LOGGER.info("Visual tour: scene {} ({}/{})", s.name(), index + 1, SCENES.size());
    }

    private static void shoot(Minecraft mc, String name) {
        mc.gui.toastManager().clear();
        File root = new File(System.getProperty("mnemolith.visualTour.dir", "/workspace/visual-tour"));
        String lang = mc.getLanguageManager().getSelected();
        String file = name + ("en_us".equals(lang) ? "" : "." + lang) + ".png";
        Screenshot.grab(root, file, mc.gameRenderer.mainRenderTarget(), 1, message -> {
            Path from = root.toPath().resolve("screenshots").resolve(file);
            try {
                if (Files.exists(from)) {
                    Files.move(from, root.toPath().resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                Mnemolith.LOGGER.warn("Visual tour: could not move {}", from, e);
            }
            Mnemolith.LOGGER.info("Visual tour: saved {}", file);
        });
    }

    /** Dev only: draws one item's real tooltip at a fixed spot, with no mouse involved. */
    private static final class TipScreen extends net.minecraft.client.gui.screens.Screen {
        private final net.minecraft.world.item.ItemStack stack;

        TipScreen(net.minecraft.world.item.ItemStack stack) {
            super(Component.empty());
            this.stack = stack;
        }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
            super.extractRenderState(graphics, mouseX, mouseY, partial);
            graphics.setTooltipForNextFrame(this.font, this.stack, 20, 30);
        }
    }
}

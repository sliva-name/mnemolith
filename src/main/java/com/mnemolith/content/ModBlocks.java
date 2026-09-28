package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.MemorySoundTypes;
import com.mnemolith.content.block.ArchivalStratumBlock;
import com.mnemolith.content.block.CompositionReelBlock;
import com.mnemolith.content.block.MuteStoneBlock;
import com.mnemolith.content.block.SelectiveMuteStoneBlock;
import com.mnemolith.content.block.ReplicatedMomentBlock;
import com.mnemolith.content.block.EchoHomeBlock;
import com.mnemolith.content.block.PressureLampBlock;
import com.mnemolith.content.block.PressureSensorBlock;
import com.mnemolith.content.block.ResonatorTrapBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mnemolith.MOD_ID);

    public static final DeferredBlock<MuteStoneBlock> MUTE_STONE = BLOCKS.registerBlock(
            "mute_stone",
            MuteStoneBlock::new,
            stoneProperties());
    public static final DeferredBlock<SelectiveMuteStoneBlock> SELECTIVE_MUTE_STONE = BLOCKS.registerBlock(
            "selective_mute_stone",
            SelectiveMuteStoneBlock::new,
            stoneProperties());
    public static final DeferredBlock<CompositionReelBlock> COMPOSITION_REEL = BLOCKS.registerBlock(
            "composition_reel",
            CompositionReelBlock::new,
            reelProperties());
    public static final DeferredBlock<ResonatorTrapBlock> RESONATOR_TRAP = BLOCKS.registerBlock(
            "resonator_trap",
            ResonatorTrapBlock::new,
            trapProperties());
    public static final DeferredBlock<ArchivalStratumBlock> ARCHIVAL_STRATUM = BLOCKS.registerBlock(
            "archival_stratum",
            ArchivalStratumBlock::new,
            stratumProperties());
    /** Left by a moment replicant's copied block place. No item, no drops, fades on its own. */
    public static final DeferredBlock<ReplicatedMomentBlock> REPLICATED_MOMENT = BLOCKS.registerBlock(
            "replicated_moment",
            ReplicatedMomentBlock::new,
            momentProperties());

    /** Grows where a recollection storm merged into the Scar. A storm ward: no storm gathers within one chunk. */
    public static final DeferredBlock<com.mnemolith.content.block.ScarGlassBlock> SCAR_GLASS = BLOCKS.registerBlock(
            "scar_glass",
            com.mnemolith.content.block.ScarGlassBlock::new,
            scarGlassProperties());
    /** Centre of a Scar site. No item, no drops; breaking it heals the scar. */
    public static final DeferredBlock<com.mnemolith.content.block.ScarHeartBlock> SCAR_HEART = BLOCKS.registerBlock(
            "scar_heart",
            com.mnemolith.content.block.ScarHeartBlock::new,
            scarHeartProperties());

    /** Banks imprints drawn from nearby chunks; what it holds bleeds pressure into its own chunk. */
    public static final DeferredBlock<com.mnemolith.content.block.ArchiveVaultBlock> ARCHIVE_VAULT = BLOCKS.registerBlock(
            "archive_vault",
            com.mnemolith.content.block.ArchiveVaultBlock::new,
            vaultProperties());
    public static final DeferredBlock<PressureSensorBlock> PRESSURE_SENSOR = BLOCKS.registerBlock(
            "pressure_sensor",
            PressureSensorBlock::new,
            sensorProperties());

    /** Chest-like memorial left on player death when memorial.enabled is on (P4). */
    public static final DeferredBlock<com.mnemolith.content.block.PlayerMemorialBlock> PLAYER_MEMORIAL = BLOCKS.registerBlock(
            "player_memorial",
            com.mnemolith.content.block.PlayerMemorialBlock::new,
            memorialProperties());


    public static final DeferredBlock<EchoHomeBlock> ECHO_HOME = BLOCKS.registerBlock(
            "echo_home",
            EchoHomeBlock::new,
            homeProperties());

    public static final DeferredBlock<Block> ARCHIVAL_STRATUM_BRICKS = BLOCKS.registerSimpleBlock(
            "archival_stratum_bricks", brickStratumProperties());
    public static final DeferredBlock<StairBlock> ARCHIVAL_STRATUM_STAIRS = BLOCKS.register(
            "archival_stratum_stairs",
            key -> new StairBlock(ARCHIVAL_STRATUM_BRICKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofLegacyCopy(ARCHIVAL_STRATUM_BRICKS.get())
                            .setId(ResourceKey.create(Registries.BLOCK, key))));
    public static final DeferredBlock<SlabBlock> ARCHIVAL_STRATUM_SLAB = BLOCKS.registerBlock(
            "archival_stratum_slab", SlabBlock::new, brickStratumProperties());
    public static final DeferredBlock<WallBlock> ARCHIVAL_STRATUM_WALL = BLOCKS.registerBlock(
            "archival_stratum_wall", WallBlock::new,
            props -> brickStratumProperties().apply(props).forceSolidOn());

    public static final DeferredBlock<Block> MUTE_STONE_BRICKS = BLOCKS.registerSimpleBlock(
            "mute_stone_bricks", brickMuteProperties());
    public static final DeferredBlock<StairBlock> MUTE_STONE_STAIRS = BLOCKS.register(
            "mute_stone_stairs",
            key -> new StairBlock(MUTE_STONE_BRICKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofLegacyCopy(MUTE_STONE_BRICKS.get())
                            .setId(ResourceKey.create(Registries.BLOCK, key))));
    public static final DeferredBlock<SlabBlock> MUTE_STONE_SLAB = BLOCKS.registerBlock(
            "mute_stone_slab", SlabBlock::new, brickMuteProperties());
    public static final DeferredBlock<WallBlock> MUTE_STONE_WALL = BLOCKS.registerBlock(
            "mute_stone_wall", WallBlock::new,
            props -> brickMuteProperties().apply(props).forceSolidOn());

    public static final DeferredBlock<IronBarsBlock> SCAR_GLASS_PANE = BLOCKS.registerBlock(
            "scar_glass_pane", IronBarsBlock::new, paneProperties());
    public static final DeferredBlock<PressureLampBlock> PRESSURE_LAMP = BLOCKS.registerBlock(
            "pressure_lamp", PressureLampBlock::new, lampProperties());

    private ModBlocks() {}

    private static UnaryOperator<BlockBehaviour.Properties> sensorProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_CYAN)
                .strength(1.5F, 6.0F)
                .sound(SoundType.COPPER)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    private static UnaryOperator<BlockBehaviour.Properties> vaultProperties() {
        return properties -> properties
                .mapColor(MapColor.TERRACOTTA_BLUE)
                .strength(3.0F, 6.0F)
                .sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> state.getValue(com.mnemolith.content.block.ArchiveVaultBlock.DRAWING) ? 7 : 2)
                .noOcclusion()
                .requiresCorrectToolForDrops();
    }

    private static UnaryOperator<BlockBehaviour.Properties> scarGlassProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 5)
                .noOcclusion()
                .requiresCorrectToolForDrops()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    private static UnaryOperator<BlockBehaviour.Properties> scarHeartProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_MAGENTA)
                .strength(30.0F, 1200.0F)
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 10)
                .noOcclusion()
                .noLootTable()
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    private static UnaryOperator<BlockBehaviour.Properties> stoneProperties() {
        return properties -> properties.mapColor(MapColor.COLOR_BLUE).strength(1.5F, 6.0F).sound(MemorySoundTypes.MUTE).noOcclusion();
    }

    private static UnaryOperator<BlockBehaviour.Properties> reelProperties() {
        return properties -> properties.mapColor(MapColor.TERRACOTTA_CYAN).strength(1.5F, 3.0F).sound(SoundType.AMETHYST).noOcclusion();
    }

    private static UnaryOperator<BlockBehaviour.Properties> trapProperties() {
        return properties -> properties.mapColor(MapColor.TERRACOTTA_CYAN).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    private static UnaryOperator<BlockBehaviour.Properties> momentProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(0.3F)
                .sound(SoundType.AMETHYST)
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.DESTROY)
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    private static UnaryOperator<BlockBehaviour.Properties> stratumProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_BLUE)
                .strength(3.0F, 6.0F)
                .sound(MemorySoundTypes.STRATUM)
                .lightLevel(state -> 7)
                .noOcclusion()
                .requiresCorrectToolForDrops();
    }

    private static UnaryOperator<BlockBehaviour.Properties> memorialProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(2.0F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion();
    }

    private static UnaryOperator<BlockBehaviour.Properties> homeProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_CYAN)
                .strength(2.0F, 6.0F)
                .sound(SoundType.AMETHYST)
                .noOcclusion()
                .lightLevel(state -> 4);
    }

    private static UnaryOperator<BlockBehaviour.Properties> brickStratumProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_BLUE)
                .strength(2.5F, 6.0F)
                .sound(MemorySoundTypes.STRATUM)
                .requiresCorrectToolForDrops();
    }

    private static UnaryOperator<BlockBehaviour.Properties> brickMuteProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_BLUE)
                .strength(1.5F, 6.0F)
                .sound(MemorySoundTypes.MUTE)
                .requiresCorrectToolForDrops();
    }

    private static UnaryOperator<BlockBehaviour.Properties> paneProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(1.0F, 3.0F)
                .sound(SoundType.AMETHYST)
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    private static UnaryOperator<BlockBehaviour.Properties> lampProperties() {
        return properties -> properties
                .mapColor(MapColor.COLOR_CYAN)
                .strength(1.0F, 3.0F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .lightLevel(PressureLampBlock::lightFor);
    }
}

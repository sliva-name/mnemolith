package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.MemorySoundTypes;
import com.mnemolith.content.block.ArchivalStratumBlock;
import com.mnemolith.content.block.CompositionReelBlock;
import com.mnemolith.content.block.MuteStoneBlock;
import com.mnemolith.content.block.ReplicatedMomentBlock;
import com.mnemolith.content.block.ResonatorTrapBlock;

import net.minecraft.world.level.block.SoundType;
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

    private ModBlocks() {}

    private static UnaryOperator<BlockBehaviour.Properties> vaultProperties() {
        return properties -> properties
                .mapColor(MapColor.TERRACOTTA_BLUE)
                .strength(3.0F, 6.0F)
                .sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> state.getValue(com.mnemolith.content.block.ArchiveVaultBlock.DRAWING) ? 7 : 2)
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
        return properties -> properties.mapColor(MapColor.COLOR_BLUE).strength(1.5F, 6.0F).sound(MemorySoundTypes.MUTE);
    }

    private static UnaryOperator<BlockBehaviour.Properties> reelProperties() {
        return properties -> properties.mapColor(MapColor.TERRACOTTA_CYAN).strength(1.5F, 3.0F).sound(SoundType.AMETHYST);
    }

    private static UnaryOperator<BlockBehaviour.Properties> trapProperties() {
        return properties -> properties.mapColor(MapColor.TERRACOTTA_CYAN).strength(1.5F, 6.0F).sound(SoundType.METAL);
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
                .requiresCorrectToolForDrops();
    }
}

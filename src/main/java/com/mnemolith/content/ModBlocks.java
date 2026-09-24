package com.mnemolith.content;

import java.util.function.UnaryOperator;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.MemorySoundTypes;
import com.mnemolith.content.block.ArchivalStratumBlock;
import com.mnemolith.content.block.CompositionReelBlock;
import com.mnemolith.content.block.MuteStoneBlock;
import com.mnemolith.content.block.ResonatorTrapBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    private ModBlocks() {}

    private static UnaryOperator<BlockBehaviour.Properties> stoneProperties() {
        return properties -> properties.mapColor(MapColor.COLOR_BLUE).strength(1.5F, 6.0F).sound(MemorySoundTypes.MUTE);
    }

    private static UnaryOperator<BlockBehaviour.Properties> reelProperties() {
        return properties -> properties.mapColor(MapColor.TERRACOTTA_CYAN).strength(1.5F, 3.0F).sound(SoundType.AMETHYST);
    }

    private static UnaryOperator<BlockBehaviour.Properties> trapProperties() {
        return properties -> properties.mapColor(MapColor.TERRACOTTA_CYAN).strength(1.5F, 6.0F).sound(SoundType.METAL);
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

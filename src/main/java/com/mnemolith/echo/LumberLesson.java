package com.mnemolith.echo;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lumberjack lesson from a recording: logs chopped and saplings planted. The echo chops taught logs and replants
 * matching saplings on dirt and grass in range — show once, they repeat.
 */
public record LumberLesson(List<Block> logs, List<Block> saplings, int chopped, int planted) {
    public static final int MAX = 8;
    public static final LumberLesson NONE = new LumberLesson(List.of(), List.of(), 0, 0);
    public static final Codec<LumberLesson> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().listOf().optionalFieldOf("logs", List.of()).forGetter(LumberLesson::logs),
            BuiltInRegistries.BLOCK.byNameCodec().listOf().optionalFieldOf("saplings", List.of()).forGetter(LumberLesson::saplings),
            Codec.INT.optionalFieldOf("chopped", 0).forGetter(LumberLesson::chopped),
            Codec.INT.optionalFieldOf("planted", 0).forGetter(LumberLesson::planted))
            .apply(instance, LumberLesson::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LumberLesson> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.BLOCK).apply(ByteBufCodecs.list(MAX)), LumberLesson::logs,
            ByteBufCodecs.registry(Registries.BLOCK).apply(ByteBufCodecs.list(MAX)), LumberLesson::saplings,
            ByteBufCodecs.VAR_INT, LumberLesson::chopped,
            ByteBufCodecs.VAR_INT, LumberLesson::planted,
            LumberLesson::new);

    public LumberLesson {
        List<Block> keptLogs = new ArrayList<>();
        for (Block block : logs) {
            if (isLog(block.defaultBlockState()) && !keptLogs.contains(block) && keptLogs.size() < MAX) {
                keptLogs.add(block);
            }
        }
        List<Block> keptSaplings = new ArrayList<>();
        for (Block block : saplings) {
            if (block instanceof SaplingBlock && !keptSaplings.contains(block) && keptSaplings.size() < MAX) {
                keptSaplings.add(block);
            }
        }
        logs = List.copyOf(keptLogs);
        saplings = List.copyOf(keptSaplings);
    }

    public boolean teaches() {
        return !this.logs.isEmpty() && this.chopped + this.planted >= 2;
    }

    public boolean knowsLog(Block block) {
        return this.logs.contains(block);
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    /** Sapling item that matches {@code log}, or the first taught sapling's item. */
    public Item saplingItemFor(Block log) {
        Block sapling = saplingFor(log);
        if (sapling == null && !this.saplings.isEmpty()) {
            sapling = this.saplings.get(0);
        }
        return sapling == null ? Items.AIR : sapling.asItem();
    }

    public Block saplingFor(Block log) {
        Block mapped = mapLogToSapling(log);
        if (mapped != null && (this.saplings.isEmpty() || this.saplings.contains(mapped))) {
            return mapped;
        }
        for (Block sapling : this.saplings) {
            return sapling;
        }
        return mapped;
    }

    /** Best-effort oak_log → oak_sapling (and wood / stripped variants). */
    public static Block mapLogToSapling(Block log) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(log);
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        path = path.replace("stripped_", "");
        path = path.replace("_wood", "_sapling").replace("_log", "_sapling").replace("_stem", "_sapling").replace("_hyphae", "_sapling");
        if (!path.endsWith("_sapling")) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), path)).orElse(null);
    }

    public static boolean canPlantOn(BlockState ground) {
        return ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL)
                || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.ROOTED_DIRT) || ground.is(Blocks.MOSS_BLOCK)
                || ground.is(Blocks.MUD) || ground.is(Blocks.PALE_MOSS_BLOCK);
    }

    public Component logNames() {
        MutableComponent out = Component.empty();
        for (int i = 0; i < this.logs.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(this.logs.get(i).getName());
        }
        return out;
    }
}

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stage 3 farming lesson, recognised from a recording: tilling with a hoe, planting crops and harvesting mature ones.
 * Kept apart from {@link EchoLesson} (whose codecs stay as they are) in its own item component and job field.
 * {@code crops} are the crop blocks it learned, most used first.
 */
public record FarmLesson(List<Block> crops, int tilled, int planted, int harvested) {
    public static final int MAX_CROPS = 4;
    public static final FarmLesson NONE = new FarmLesson(List.of(), 0, 0, 0);
    public static final Codec<FarmLesson> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().listOf().optionalFieldOf("crops", List.of()).forGetter(FarmLesson::crops),
            Codec.INT.optionalFieldOf("tilled", 0).forGetter(FarmLesson::tilled),
            Codec.INT.optionalFieldOf("planted", 0).forGetter(FarmLesson::planted),
            Codec.INT.optionalFieldOf("harvested", 0).forGetter(FarmLesson::harvested))
            .apply(instance, FarmLesson::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmLesson> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.BLOCK).apply(ByteBufCodecs.list(MAX_CROPS)), FarmLesson::crops,
            ByteBufCodecs.VAR_INT, FarmLesson::tilled,
            ByteBufCodecs.VAR_INT, FarmLesson::planted,
            ByteBufCodecs.VAR_INT, FarmLesson::harvested,
            FarmLesson::new);

    public FarmLesson {
        List<Block> kept = new ArrayList<>();
        for (Block block : crops) {
            if (block instanceof CropBlock && !kept.contains(block) && kept.size() < MAX_CROPS) {
                kept.add(block);
            }
        }
        crops = List.copyOf(kept);
    }

    /** At least two farming actions and a known crop. */
    public boolean teaches() {
        return !this.crops.isEmpty() && this.tilled + this.planted + this.harvested >= 2;
    }

    public boolean knows(Block block) {
        return this.crops.contains(block);
    }

    /** The item that plants {@code crop} (seeds, carrot, potato). */
    public static Item seedFor(Block crop) {
        Item item = EchoLesson.itemFor(crop.defaultBlockState());
        return item == null ? Items.AIR : item;
    }

    public static boolean isMature(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    /**
     * What a player calls the crop: the harvest item for the vanilla crops ("Wheat", not the block's "Wheat Crops"),
     * else the block name.
     */
    public static Component cropName(Block crop) {
        if (crop == net.minecraft.world.level.block.Blocks.WHEAT) {
            return new net.minecraft.world.item.ItemStack(Items.WHEAT).getHoverName();
        }
        if (crop == net.minecraft.world.level.block.Blocks.CARROTS) {
            return new net.minecraft.world.item.ItemStack(Items.CARROT).getHoverName();
        }
        if (crop == net.minecraft.world.level.block.Blocks.POTATOES) {
            return new net.minecraft.world.item.ItemStack(Items.POTATO).getHoverName();
        }
        if (crop == net.minecraft.world.level.block.Blocks.BEETROOTS) {
            return new net.minecraft.world.item.ItemStack(Items.BEETROOT).getHoverName();
        }
        return crop.getName();
    }

    /** "Wheat, Carrots" for the label and tooltips. */
    public Component cropNames() {
        MutableComponent out = Component.empty();
        for (int i = 0; i < this.crops.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(cropName(this.crops.get(i)));
        }
        return out;
    }
}

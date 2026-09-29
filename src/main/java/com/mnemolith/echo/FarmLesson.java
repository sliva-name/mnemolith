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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Farming lesson from a recording: tilling, planting and harvesting. Crops include the classic four plus melon,
 * pumpkin, sugar cane, bamboo and nether wart (O1). Kept apart from {@link EchoLesson} so its codecs stay as they are.
 */
public record FarmLesson(List<Block> crops, int tilled, int planted, int harvested) {
    public static final int MAX_CROPS = 8;
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
            Block normalized = normalize(block);
            if (normalized != null && !kept.contains(normalized) && kept.size() < MAX_CROPS) {
                kept.add(normalized);
            }
        }
        crops = List.copyOf(kept);
    }

    /** Maps stem blocks to their fruit for the saved lesson list. */
    public static Block normalize(Block block) {
        if (block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof SugarCaneBlock
                || block == Blocks.BAMBOO || block == Blocks.MELON || block == Blocks.PUMPKIN) {
            return block;
        }
        if (block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM) {
            return Blocks.MELON;
        }
        if (block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) {
            return Blocks.PUMPKIN;
        }
        if (block instanceof StemBlock) {
            // Unknown stem: keep as-is only when we recognise fruit elsewhere.
            return null;
        }
        return null;
    }

    public static boolean isTeachable(Block block) {
        return normalize(block) != null;
    }

    /** At least two farming actions and a known crop. */
    public boolean teaches() {
        return !this.crops.isEmpty() && this.tilled + this.planted + this.harvested >= 2;
    }

    public boolean knows(Block block) {
        Block normalized = normalize(block);
        return normalized != null && this.crops.contains(normalized);
    }

    /** The item that plants {@code crop}. */
    public static Item seedFor(Block crop) {
        if (crop == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS;
        }
        if (crop == Blocks.CARROTS) {
            return Items.CARROT;
        }
        if (crop == Blocks.POTATOES) {
            return Items.POTATO;
        }
        if (crop == Blocks.BEETROOTS) {
            return Items.BEETROOT_SEEDS;
        }
        if (crop == Blocks.MELON) {
            return Items.MELON_SEEDS;
        }
        if (crop == Blocks.PUMPKIN) {
            return Items.PUMPKIN_SEEDS;
        }
        if (crop == Blocks.NETHER_WART) {
            return Items.NETHER_WART;
        }
        if (crop == Blocks.SUGAR_CANE) {
            return Items.SUGAR_CANE;
        }
        if (crop == Blocks.BAMBOO) {
            return Items.BAMBOO;
        }
        Item item = EchoLesson.itemFor(crop.defaultBlockState());
        return item == null ? Items.AIR : item;
    }

    public static boolean isMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.hasProperty(BlockStateProperties.AGE_3) && state.getValue(BlockStateProperties.AGE_3) >= 3;
        }
        if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
            return true;
        }
        if (block instanceof SugarCaneBlock || block == Blocks.BAMBOO) {
            // Harvestable when it is not the root (something of the same kind sits below).
            return true;
        }
        return false;
    }

    /** True when this harvest should leave the bottom block (sugar cane / bamboo). */
    public static boolean harvestAboveOnly(Block block) {
        return block instanceof SugarCaneBlock || block == Blocks.BAMBOO;
    }

    /** Ground the seed needs under the planting cell. */
    public static boolean canPlantOn(Block crop, BlockState ground) {
        if (crop == Blocks.NETHER_WART) {
            return ground.is(Blocks.SOUL_SAND);
        }
        if (crop == Blocks.SUGAR_CANE) {
            return ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.SAND)
                    || ground.is(Blocks.RED_SAND) || ground.is(Blocks.MUD) || ground.is(Blocks.SUGAR_CANE);
        }
        if (crop == Blocks.BAMBOO) {
            return ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.SAND)
                    || ground.is(Blocks.PODZOL) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.BAMBOO)
                    || ground.is(Blocks.MOSS_BLOCK);
        }
        if (crop == Blocks.MELON || crop == Blocks.PUMPKIN) {
            return ground.is(Blocks.FARMLAND);
        }
        return ground.is(Blocks.FARMLAND);
    }

    /** Block state to place when planting {@code crop} (stems for melon/pumpkin). */
    public static BlockState plantState(Block crop) {
        if (crop == Blocks.MELON) {
            return Blocks.MELON_STEM.defaultBlockState();
        }
        if (crop == Blocks.PUMPKIN) {
            return Blocks.PUMPKIN_STEM.defaultBlockState();
        }
        return crop.defaultBlockState();
    }

    /**
     * What a player calls the crop: the harvest item for the vanilla crops ("Wheat", not the block's "Wheat Crops"),
     * else the block name.
     */
    public static Component cropName(Block crop) {
        if (crop == Blocks.WHEAT) {
            return new net.minecraft.world.item.ItemStack(Items.WHEAT).getHoverName();
        }
        if (crop == Blocks.CARROTS) {
            return new net.minecraft.world.item.ItemStack(Items.CARROT).getHoverName();
        }
        if (crop == Blocks.POTATOES) {
            return new net.minecraft.world.item.ItemStack(Items.POTATO).getHoverName();
        }
        if (crop == Blocks.BEETROOTS) {
            return new net.minecraft.world.item.ItemStack(Items.BEETROOT).getHoverName();
        }
        if (crop == Blocks.MELON) {
            return new net.minecraft.world.item.ItemStack(Items.MELON).getHoverName();
        }
        if (crop == Blocks.PUMPKIN) {
            return new net.minecraft.world.item.ItemStack(Items.PUMPKIN).getHoverName();
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

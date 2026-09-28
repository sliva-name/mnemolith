package com.mnemolith.echo;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Animal-care lesson from a recording: shear, milk, and/or breed. The echo repeats those cares on nearby animals.
 */
public record CareLesson(boolean shear, boolean milk, boolean breed, Optional<Item> breedFood, int actions) {
    public static final CareLesson NONE = new CareLesson(false, false, false, Optional.empty(), 0);
    public static final Codec<CareLesson> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("shear", false).forGetter(CareLesson::shear),
            Codec.BOOL.optionalFieldOf("milk", false).forGetter(CareLesson::milk),
            Codec.BOOL.optionalFieldOf("breed", false).forGetter(CareLesson::breed),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("breed_food").forGetter(CareLesson::breedFood),
            Codec.INT.optionalFieldOf("actions", 0).forGetter(CareLesson::actions))
            .apply(instance, CareLesson::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CareLesson> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CareLesson::shear,
            ByteBufCodecs.BOOL, CareLesson::milk,
            ByteBufCodecs.BOOL, CareLesson::breed,
            ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.ITEM)), CareLesson::breedFood,
            ByteBufCodecs.VAR_INT, CareLesson::actions,
            CareLesson::new);

    public boolean teaches() {
        return this.actions >= 2 && (this.shear || this.milk || this.breed);
    }

    public Component describe() {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean first = true;
        if (this.shear) {
            out.append(Component.translatable("mnemolith.care.shear"));
            first = false;
        }
        if (this.milk) {
            if (!first) {
                out.append(", ");
            }
            out.append(Component.translatable("mnemolith.care.milk"));
            first = false;
        }
        if (this.breed) {
            if (!first) {
                out.append(", ");
            }
            if (this.breedFood.isPresent() && this.breedFood.get() != Items.AIR) {
                out.append(Component.translatable("mnemolith.care.breed_food", this.breedFood.get().getName(new net.minecraft.world.item.ItemStack(this.breedFood.get()))));
            } else {
                out.append(Component.translatable("mnemolith.care.breed"));
            }
        }
        return out;
    }
}

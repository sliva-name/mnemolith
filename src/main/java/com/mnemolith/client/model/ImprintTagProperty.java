package com.mnemolith.client.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mnemolith.Mnemolith;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Item model selector {@code mnemolith:imprint_tag}: the tag of the imprint a stack carries ({@code "fire"},
 * {@code "death"} ...), or {@code "none"} for a blank. {@code items/imprint_slip.json} uses it to give every written
 * slip the seal of its tag, so slips can be told apart at a glance in a chest or the reel.
 */
public record ImprintTagProperty() implements SelectItemModelProperty<String> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "imprint_tag");
    public static final SelectItemModelProperty.Type<ImprintTagProperty, String> TYPE =
            SelectItemModelProperty.Type.create(MapCodec.unit(new ImprintTagProperty()), Codec.STRING);

    public static void register(RegisterSelectItemModelPropertyEvent event) {
        event.register(ID, TYPE);
    }

    @Override
    public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
        ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        return cast == null ? "none" : cast.tag().getSerializedName();
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public SelectItemModelProperty.Type<ImprintTagProperty, String> type() {
        return TYPE;
    }
}

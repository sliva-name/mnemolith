package com.mnemolith.entity.effect;

import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ImprintConstants;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class FireTrailEffect extends MobEffect {
    public FireTrailEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE6DCC8);
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "fire_trail_speed"),
                ImprintConstants.FIRE_TRAIL_SPEED_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
    }
}

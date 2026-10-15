package com.mnemolith.content.villager;

import org.jspecify.annotations.Nullable;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.data.Transcript;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

/** Helpers for QA and live tests: a chronicler of a given level and its copied-notes offer. */
public final class ChroniclerOffers {
    private ChroniclerOffers() {}

    /** A chronicler villager at {@code level} with no AI, not yet added to the world unless {@code add}. */
    public static Villager chronicler(ServerLevel world, BlockPos pos, int level, boolean add) {
        Villager villager = EntityTypes.VILLAGER.create(world, EntitySpawnReason.COMMAND);
        if (villager == null) {
            throw new IllegalStateException("villager did not create");
        }
        villager.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(ModVillagers.CHRONICLER).withLevel(level));
        if (add) {
            world.addFreshEntity(villager);
        }
        return villager;
    }

    /** The copied-notes offer among the villager's offers, or null. */
    public static @Nullable MerchantOffer transcriptOffer(Villager villager) {
        for (MerchantOffer offer : villager.getOffers()) {
            Transcript notes = offer.getResult().get(ModDataComponents.TRANSCRIPT.get());
            if (offer.getResult().is(ModItems.CHRONICLE_TRANSCRIPT.get()) && notes != null && notes.byChronicler()) {
                return offer;
            }
        }
        return null;
    }
}

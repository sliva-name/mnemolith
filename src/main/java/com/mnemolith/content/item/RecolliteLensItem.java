package com.mnemolith.content.item;

import net.minecraft.world.entity.LivingEntity;

/**
 * Chronicle lens with recollite set into the rim (Memory Hollows stage 2). It is a chronicle lens in every way, and in
 * Memory Hollows it draws memory flickers more often and lets a needle catch them from farther away
 * ({@link com.mnemolith.worldgen.hollows.HollowFlickers}).
 */
public class RecolliteLensItem extends ChronicleLensItem {
    public RecolliteLensItem(Properties properties) {
        super(properties);
    }

    /** True when either hand holds a recollite lens. */
    public static boolean holds(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof RecolliteLensItem
                || entity.getOffhandItem().getItem() instanceof RecolliteLensItem;
    }
}

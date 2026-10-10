package com.mnemolith.content.item;

/**
 * An extraction needle tipped with recollite: extracts like the plain needle (twice the durability) and catches memory
 * flickers with no lens, from {@link com.mnemolith.worldgen.hollows.HollowFlickers#NEEDLE_REACH} blocks. With a
 * recollite lens in the other hand the lens's longer reach wins.
 */
public class RecolliteNeedleItem extends ExtractionNeedleItem {
    public RecolliteNeedleItem(Properties properties) {
        super(properties, false, false);
    }

    @Override
    public boolean catchesUnaided() {
        return true;
    }
}

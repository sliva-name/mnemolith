package com.mnemolith.content.item;

import com.mnemolith.Mnemolith;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * An iron-tier pickaxe with a recollite head. It mines like iron, lasts longer, and reads the chunk while it digs: each
 * block it breaks is weighed against the loudest imprint of that chunk ({@link PickaxeReading}).
 */
public class RecollitePickaxeItem extends Item {
    public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "repairs_recollite_pickaxe"));
    /** Iron mining level and speed, 500 uses, enchants a little better than iron. */
    public static final ToolMaterial MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 6.5F, 2.0F, 16, REPAIRS);

    public RecollitePickaxeItem(Properties properties) {
        super(properties);
    }
}

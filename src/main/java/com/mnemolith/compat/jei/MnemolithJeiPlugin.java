package com.mnemolith.compat.jei;

import java.util.List;

import com.mnemolith.network.ServerTuning;
import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.composition.CompositionRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.ingredients.subtypes.UidContext;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class MnemolithJeiPlugin implements IModPlugin {
    public static final Identifier UID = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /**
     * Residual shards (and written imprint slips) differ only by the imprint they carry: tell JEI the imprint tag is
     * the subtype, so the seven shards in the creative tab are seven entries rather than "duplicates".
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.RESIDUAL_SHARD.get(), MnemolithJeiPlugin::imprintTag);
        registration.registerSubtypeInterpreter(ModItems.IMPRINT_SLIP.get(), MnemolithJeiPlugin::imprintTag);
    }

    private static Object imprintTag(ItemStack stack, UidContext context) {
        ImprintCast cast = stack.get(ModDataComponents.IMPRINT_CAST.get());
        return cast == null ? null : cast.tag().getSerializedName();
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CompositionRecipeCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<CompositionRecipe> recipes = ServerTuning.formulas();
        registration.addRecipes(CompositionRecipeCategory.TYPE, recipes);

        registration.addIngredientInfo(
                new ItemStack(ModItems.ECHO_CHORUS_SLIP.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.upgrade.chorus"));
        registration.addIngredientInfo(
                new ItemStack(ModItems.ECHO_LONG_SLIP.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.upgrade.long_take"));
        registration.addIngredientInfo(
                new ItemStack(ModItems.ECHO_STURDY_SLIP.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.upgrade.sturdy"));
        registration.addIngredientInfo(
                new ItemStack(ModItems.COMPOSITION_REEL.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.composition.hint"));
        // Memory Hollows: where the biome's blocks come from (their crafting, smelting and stonecutting recipes are
        // vanilla recipe types, which JEI lists on its own).
        registration.addIngredientInfo(
                List.of(new ItemStack(ModItems.RECOLLITE_ORE.get()), new ItemStack(ModItems.RECOLLITE_SHARD.get())),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.hollows.recollite"));
        registration.addIngredientInfo(
                List.of(new ItemStack(ModItems.HOLLOW_TURF.get()), new ItemStack(ModItems.HOLLOWSTONE.get()), new ItemStack(ModItems.FORGET_ME_NOT.get())),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.hollows.ground"));
        registration.addIngredientInfo(
                new ItemStack(ModItems.RECOLLITE_LENS.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.hollows.lens"));
        registration.addIngredientInfo(
                new ItemStack(ModItems.RECOLLITE_NEEDLE.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.hollows.needle"));
        registration.addIngredientInfo(
                new ItemStack(net.minecraft.world.item.Items.LECTERN),
                VanillaTypes.ITEM_STACK,
                Component.translatable("mnemolith.jei.hollows.lectern"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CompositionRecipeCategory.TYPE, ModItems.COMPOSITION_REEL.get());
    }
}

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
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CompositionRecipeCategory.TYPE, ModItems.COMPOSITION_REEL.get());
    }
}

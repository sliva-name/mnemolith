package com.mnemolith.compat.jei;

import java.util.List;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.imprint.ImprintTag;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** JEI category for drum composition formulas (imprint slips → shard or item). */
public final class CompositionRecipeCategory extends AbstractRecipeCategory<CompositionRecipe> {
    public static final IRecipeType<CompositionRecipe> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "composition"),
            CompositionRecipe.class);

    private static final int WIDTH = 140;
    private static final int HEIGHT = 54;

    public CompositionRecipeCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("mnemolith.jei.composition"),
                guiHelper.createDrawableItemLike(ModItems.COMPOSITION_REEL.get()),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CompositionRecipe recipe, IFocusGroup focuses) {
        List<ImprintTag> tags = recipe.tags();
        int startX = 8;
        for (int i = 0; i < tags.size(); i++) {
            builder.addInputSlot(startX + i * 22, 18)
                    .setStandardSlotBackground()
                    .add(ImprintSlips.of(tags.get(i)));
        }
        ItemStack output = Composition.rewardOf(recipe, BlockPos.ZERO, 0L);
        builder.addOutputSlot(110, 18)
                .setOutputSlotBackground()
                .add(output);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, CompositionRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(78, 18);
        builder.addText(Component.translatable(recipe.translationKey()), WIDTH - 8, 12)
                .setPosition(4, 2);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CompositionRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        tooltip.add(Component.translatable(recipe.translationKey()));
        tooltip.add(Component.literal(recipe.id().toString()));
    }
}

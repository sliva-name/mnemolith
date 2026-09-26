package com.mnemolith.command.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mnemolith.armory.Armory;
import com.mnemolith.armory.ArmoryItems;
import com.mnemolith.armory.ArmorySet;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.EchoRole;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.ai.PathLedger;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.mob.FractureStalker;
import com.mnemolith.entity.mob.KinWitness;
import com.mnemolith.entity.mob.LedgerMite;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * One-tick checks for the armory: a mite can be tamed, a witness remembers trust, a stalker drops a scale,
 * an echo keeps a taught role, and a full hush set writes no path while the wearer sneaks.
 */
public final class ArmoryQa {
    private static final String[] NAMES = {"recipes", "mite", "witness", "stalker", "role", "hush"};

    private ArmoryQa() {}

    public static QaReport check(ServerLevel level, BlockPos origin) {
        List<String> notes = new ArrayList<>();
        BlockPos ground = QaSupport.column(level, (origin.getX() >> 4) + 48, (origin.getZ() >> 4) + 48);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("55555555-5555-5555-5555-555555555555"), "ArmoryQa"));
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS, true, level.getServer());

        boolean recipes = recipes(level, notes);
        boolean mite = mite(level, player, ground, notes);
        boolean witness = witness(level, player, ground.offset(3, 0, 0), notes);
        boolean stalker = stalker(level, player, ground.offset(6, 0, 0), notes);
        boolean role = role(level, player, ground.offset(0, 0, 3), notes);
        boolean hush = hush(level, ground.offset(0, 0, 20), notes);

        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return new QaReport("armoryqa", NAMES, new boolean[] {recipes, mite, witness, stalker, role, hush}, notes).log();
    }

    private static boolean recipes(ServerLevel level, List<String> notes) {
        boolean fiber = QaSupport.resourceContains(level, "recipe/hush_fiber.json", "\"id\": \"mnemolith:hush_fiber\"");
        boolean brand = QaSupport.resourceContains(level, "recipe/scar_brand.json", "\"id\": \"mnemolith:scar_brand\"");
        boolean scaleCrafted = QaSupport.resourceContains(level, "recipe/grave_scale.json", "mnemolith:grave_scale");
        notes.add("recipes fiber=" + fiber + " brand=" + brand + " scaleCrafted=" + scaleCrafted);
        return fiber && brand && !scaleCrafted;
    }

    private static boolean mite(ServerLevel level, FakePlayer player, BlockPos pos, List<String> notes) {
        LedgerMite mite = place(ModEntities.LEDGER_MITE.get(), level, pos);
        if (mite == null) {
            notes.add("mite missing");
            return false;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER, 64));
        boolean tamed = false;
        for (int i = 0; i < 48 && !tamed; i++) {
            mite.mobInteract(player, InteractionHand.MAIN_HAND);
            tamed = mite.isTame();
        }
        notes.add("mite tame=" + tamed);
        mite.discard();
        return tamed;
    }

    private static boolean witness(ServerLevel level, FakePlayer player, BlockPos pos, List<String> notes) {
        KinWitness witness = place(ModEntities.KIN_WITNESS.get(), level, pos);
        if (witness == null) {
            notes.add("witness missing");
            return false;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmoryItems.HUSH_FIBER.get(), 4));
        for (int i = 0; i < 3; i++) {
            witness.mobInteract(player, InteractionHand.MAIN_HAND);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        witness.mobInteract(player, InteractionHand.MAIN_HAND);
        boolean tablet = player.getInventory().contains(new ItemStack(ModItems.ARCHIVAL_TABLET.get()));
        KinWitness loaded = reload(level, witness);
        boolean remembered = loaded != null && loaded.trust() == 3;
        notes.add("witness trust=" + (loaded == null ? witness.trust() : loaded.trust()) + " tablet=" + tablet + " remembered=" + remembered);
        if (loaded != null) {
            loaded.discard();
        }
        return witness.trust() == 3 && tablet && remembered;
    }

    private static boolean stalker(ServerLevel level, FakePlayer player, BlockPos pos, List<String> notes) {
        boolean dropped = false;
        for (int i = 0; i < 16 && !dropped; i++) {
            FractureStalker stalker = place(ModEntities.FRACTURE_STALKER.get(), level, pos);
            if (stalker == null) {
                notes.add("stalker missing");
                return false;
            }
            stalker.hurt(level.damageSources().playerAttack(player), 100.0F);
            dropped = !stalker.isAlive() && stalker.gaveScale();
            if (!stalker.isRemoved()) {
                stalker.discard();
            }
        }
        notes.add("stalker scale=" + dropped);
        return dropped;
    }

    private static boolean role(ServerLevel level, FakePlayer player, BlockPos pos, List<String> notes) {
        EchoEntity echo = place(ModEntities.ECHO.get(), level, pos);
        if (echo == null) {
            notes.add("echo missing");
            return false;
        }
        echo.setOwner(player);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmoryItems.HUSH_SPEAR.get()));
        InteractionResult taught = echo.interact(player, InteractionHand.MAIN_HAND, echo.position());
        EchoEntity loaded = reload(level, echo);
        boolean kept = loaded != null && loaded.role() == EchoRole.SCOUT;
        notes.add("role result=" + taught + " kept=" + kept);
        if (loaded != null) {
            loaded.discard();
        }
        return taught == InteractionResult.SUCCESS_SERVER && kept;
    }

    private static boolean hush(ServerLevel level, BlockPos pos, List<String> notes) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("66666666-6666-6666-6666-666666666666"), "ArmoryHush"));
        level.getChunkAt(pos);
        BlockPos next = pos.offset(16, 0, 0);
        level.getChunkAt(next);
        QaSupport.clear(level, pos);
        QaSupport.clear(level, next);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        PathLedger.note(player);
        boolean wrote = QaSupport.hasTag(level, pos, ImprintTag.PATH);
        wear(player, ArmorySet.HUSH);
        player.setShiftKeyDown(true);
        player.setPos(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
        PathLedger.note(player);
        boolean quiet = !QaSupport.hasTag(level, next, ImprintTag.PATH) && Armory.full(player, ArmorySet.HUSH);
        notes.add("hush wrote=" + wrote + " quiet=" + quiet);
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        return wrote && quiet;
    }

    private static void wear(FakePlayer player, ArmorySet set) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(piece(set, EquipmentSlot.HEAD)));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(piece(set, EquipmentSlot.CHEST)));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(piece(set, EquipmentSlot.LEGS)));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(piece(set, EquipmentSlot.FEET)));
    }

    private static net.minecraft.world.item.Item piece(ArmorySet set, EquipmentSlot slot) {
        return switch (set) {
            case HUSH -> switch (slot) {
                case HEAD -> ArmoryItems.HUSH_HELMET.get();
                case CHEST -> ArmoryItems.HUSH_CHESTPLATE.get();
                case LEGS -> ArmoryItems.HUSH_LEGGINGS.get();
                default -> ArmoryItems.HUSH_BOOTS.get();
            };
            case GRAVE -> switch (slot) {
                case HEAD -> ArmoryItems.GRAVE_HELMET.get();
                case CHEST -> ArmoryItems.GRAVE_CHESTPLATE.get();
                case LEGS -> ArmoryItems.GRAVE_LEGGINGS.get();
                default -> ArmoryItems.GRAVE_BOOTS.get();
            };
            case ECHO -> switch (slot) {
                case HEAD -> ArmoryItems.ECHO_HELMET.get();
                case CHEST -> ArmoryItems.ECHO_CHESTPLATE.get();
                case LEGS -> ArmoryItems.ECHO_LEGGINGS.get();
                default -> ArmoryItems.ECHO_BOOTS.get();
            };
            case SCAR -> switch (slot) {
                case HEAD -> ArmoryItems.SCAR_HELMET.get();
                case CHEST -> ArmoryItems.SCAR_CHESTPLATE.get();
                case LEGS -> ArmoryItems.SCAR_LEGGINGS.get();
                default -> ArmoryItems.SCAR_BOOTS.get();
            };
        };
    }

    private static <T extends net.minecraft.world.entity.Entity> T place(EntityType<T> type, ServerLevel level, BlockPos pos) {
        T entity = type.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (entity == null) {
            entity = type.create(level, EntitySpawnReason.COMMAND);
            if (entity == null) {
                return null;
            }
            entity.setPos(pos.getX() + 0.5D, pos.getY() + 0.0D, pos.getZ() + 0.5D);
            if (!level.addFreshEntity(entity)) {
                return null;
            }
        }
        return entity;
    }

    private static <T extends net.minecraft.world.entity.Entity> T reload(ServerLevel level, T entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        EntityType<?> type = entity.getType();
        entity.discard();
        @SuppressWarnings("unchecked")
        T copy = (T) type.create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            return null;
        }
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return level.addFreshEntity(copy) ? copy : null;
    }
}

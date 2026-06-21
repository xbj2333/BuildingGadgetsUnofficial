package com.direwolf20.buildinggadgets.common.integration.mods;

import com.direwolf20.buildinggadgets.common.blocks.ConstructionBlockTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;

public final class StageRestrictions {
    private static final String ITEM_STAGES_MODID = "itemstages";
    private static final String ORE_STAGES_MODID = "orestages";
    private static final String MISSING_STAGE_KEY = "message.gadget.building.missing_stage";

    private static Method itemStagesGetStage;
    private static Method oreStagesGetStageInfo;
    private static Method gameStageHasStage;
    private static boolean reflectionInitialized;
    private static boolean reflectionAvailable;

    private StageRestrictions() {
    }

    public static boolean canUseBlock(EntityPlayer player, World world, BlockPos pos, IBlockState state, boolean notify) {
        if (!isActive()) {
            return true;
        }

        Set<String> stages = getRequiredStages(player, world, pos, state);
        if (stages.isEmpty()) {
            return true;
        }

        Set<String> missingStages = new LinkedHashSet<>();
        for (String stage : stages) {
            if (!hasStage(player, stage)) {
                missingStages.add(stage);
            }
        }

        if (missingStages.isEmpty()) {
            return true;
        }

        if (notify) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                    + new TextComponentTranslation(MISSING_STAGE_KEY, joinStages(missingStages)).getUnformattedComponentText()), true);
        }
        return false;
    }

    public static boolean canUseBlock(EntityPlayer player, World world, BlockPos pos, boolean notify) {
        return canUseBlock(player, world, pos, getRestrictedState(world, pos), notify);
    }

    public static IBlockState getRestrictedState(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ConstructionBlockTileEntity) {
            IBlockState actualState = ((ConstructionBlockTileEntity) te).getActualBlockState();
            if (actualState != null) {
                return actualState;
            }
        }
        return state;
    }

    private static boolean isActive() {
        return Loader.isModLoaded(ITEM_STAGES_MODID) && Loader.isModLoaded(ORE_STAGES_MODID) && initReflection();
    }

    private static Set<String> getRequiredStages(EntityPlayer player, World world, BlockPos pos, IBlockState state) {
        Set<String> stages = new LinkedHashSet<>();
        String oreStage = getOreStage(state);
        if (oreStage != null && !oreStage.isEmpty()) {
            stages.add(oreStage);
        }

        String itemStage = getItemStage(player, world, pos, state);
        if (itemStage != null && !itemStage.isEmpty()) {
            stages.add(itemStage);
        }
        return stages;
    }

    private static String getOreStage(IBlockState state) {
        try {
            Object result = oreStagesGetStageInfo.invoke(null, state);
            if (result instanceof Tuple) {
                Object stage = ((Tuple) result).getFirst();
                return stage instanceof String ? (String) stage : null;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static String getItemStage(EntityPlayer player, World world, BlockPos pos, IBlockState state) {
        try {
            ItemStack stack = getRepresentativeStack(player, world, pos, state);
            if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                Object stage = itemStagesGetStage.invoke(null, stack);
                return stage instanceof String ? (String) stage : null;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static ItemStack getRepresentativeStack(EntityPlayer player, World world, BlockPos pos, IBlockState state) {
        try {
            ItemStack picked = state.getBlock().getPickBlock(state, null, world, pos, player);
            if (!picked.isEmpty() && picked.getItem() != Items.AIR) {
                return picked;
            }
        } catch (Exception ignored) {
        }

        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        Block block = state.getBlock();
        return new ItemStack(item, 1, block.damageDropped(state));
    }

    private static boolean hasStage(EntityPlayer player, String stage) {
        try {
            Object result = gameStageHasStage.invoke(null, player, stage);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean initReflection() {
        if (reflectionInitialized) {
            return reflectionAvailable;
        }

        reflectionInitialized = true;
        try {
            Class<?> itemStages = Class.forName("net.darkhax.itemstages.ItemStages");
            Class<?> oreTiersApi = Class.forName("net.darkhax.orestages.api.OreTiersAPI");
            Class<?> gameStageHelper = Class.forName("net.darkhax.gamestages.GameStageHelper");
            itemStagesGetStage = itemStages.getMethod("getStage", ItemStack.class);
            oreStagesGetStageInfo = oreTiersApi.getMethod("getStageInfo", IBlockState.class);
            gameStageHasStage = gameStageHelper.getMethod("hasStage", EntityPlayer.class, String.class);
            reflectionAvailable = true;
        } catch (ReflectiveOperationException e) {
            reflectionAvailable = false;
        }
        return reflectionAvailable;
    }

    private static String joinStages(Set<String> stages) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String stage : stages) {
            joiner.add(stage);
        }
        return joiner.toString();
    }
}

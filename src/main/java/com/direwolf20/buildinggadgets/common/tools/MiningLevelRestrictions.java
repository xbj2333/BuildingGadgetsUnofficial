package com.direwolf20.buildinggadgets.common.tools;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.lang.reflect.Method;
import java.util.List;

public final class MiningLevelRestrictions {
    private static final String TAG_MINING_LEVEL = "miningLevel";
    private static final String TOOLTIP_KEY = "tooltip.gadget.mining_level";
    private static final String INSUFFICIENT_KEY = "message.gadget.mining_level.insufficient";
    private static final String UNBREAKABLE_KEY = "message.gadget.mining_level.unbreakable";

    private MiningLevelRestrictions() {
    }

    public static boolean hasMiningLevel(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_MINING_LEVEL, Constants.NBT.TAG_INT);
    }

    public static void addTooltip(List<String> tooltip, ItemStack stack, boolean reduced) {
        if (!hasMiningLevel(stack)) {
            return;
        }

        tooltip.add(TextFormatting.DARK_GREEN + I18n.format(TOOLTIP_KEY) + ": " + getDisplayMiningLevel(stack, reduced));
    }

    public static boolean canPlace(ItemStack tool, EntityPlayer player, World world, BlockPos pos, IBlockState state, boolean notify) {
        return canUse(tool, player, world, pos, state, getMiningLevel(tool), notify);
    }

    public static boolean canBreak(ItemStack tool, EntityPlayer player, World world, BlockPos pos, IBlockState state, boolean notify) {
        return canUse(tool, player, world, pos, state, getReducedMiningLevel(tool), notify);
    }

    public static boolean isAdditionsAddedBlock(IBlockState state) {
        String blockClassName = state.getBlock().getClass().getName();
        if (!blockClassName.startsWith("com.tmtravlr.additions.addon.blocks.")) {
            return false;
        }

        return implementsInterface(state.getBlock().getClass(), "com.tmtravlr.additions.addon.blocks.IBlockAdded");
    }

    private static boolean canUse(ItemStack tool, EntityPlayer player, World world, BlockPos pos, IBlockState state, int allowedLevel, boolean notify) {
        if (!hasMiningLevel(tool)) {
            return true;
        }

        if (state.getBlockHardness(world, pos) == -1.0F) {
            if (notify) {
                sendMessage(player, UNBREAKABLE_KEY);
            }
            return false;
        }

        int blockMiningLevel = getRequiredMiningLevel(state);
        if (blockMiningLevel > allowedLevel) {
            if (notify) {
                sendMessage(player, INSUFFICIENT_KEY);
            }
            return false;
        }

        return true;
    }

    private static int getRequiredMiningLevel(IBlockState state) {
        int level = state.getBlock().getHarvestLevel(state);
        int customLevel = getAdditionsMiningLevel(state);
        return Math.max(Math.max(level, customLevel), 0);
    }

    private static int getAdditionsMiningLevel(IBlockState state) {
        if (!isAdditionsAddedBlock(state)) {
            return -1;
        }

        try {
            Method method = state.getBlock().getClass().getMethod("getHarvestLevel");
            if (method.getReturnType() == int.class || method.getReturnType() == Integer.class) {
                Object result = method.invoke(state.getBlock());
                return result instanceof Integer ? (Integer) result : -1;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return -1;
    }

    private static boolean implementsInterface(Class<?> clazz, String interfaceName) {
        for (Class<?> blockInterface : clazz.getInterfaces()) {
            if (interfaceName.equals(blockInterface.getName()) || implementsInterface(blockInterface, interfaceName)) {
                return true;
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        return superclass != null && implementsInterface(superclass, interfaceName);
    }

    private static int getMiningLevel(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0 : tag.getInteger(TAG_MINING_LEVEL);
    }

    private static int getReducedMiningLevel(ItemStack stack) {
        return Math.max(getMiningLevel(stack) - 3, 0);
    }

    private static int getDisplayMiningLevel(ItemStack stack, boolean reduced) {
        return reduced ? getReducedMiningLevel(stack) : getMiningLevel(stack);
    }

    private static void sendMessage(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentString(TextFormatting.RED
                + new TextComponentTranslation(key).getUnformattedComponentText()), true);
    }
}

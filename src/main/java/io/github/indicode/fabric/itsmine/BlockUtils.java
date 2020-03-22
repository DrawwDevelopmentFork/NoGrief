package io.github.indicode.fabric.itsmine;

import net.minecraft.block.*;

public class BlockUtils {

    public static boolean isContainer(Block block) {
        return block instanceof AbstractChestBlock<?> || isChest(block) || isEnderchest(block) || isShulkerBox(block);
    }

    public static boolean isChest(Block block) {
        return block == Blocks.CHEST || block == Blocks.BARREL;
    }

    public static boolean isEnderchest(Block block) {
        return block == Blocks.ENDER_CHEST;
    }

    public static boolean isShulkerBox(Block block) {
        return block == Blocks.SHULKER_BOX;
    }

    public static boolean isButton(Block block) {
        return block instanceof AbstractButtonBlock;
    }

    public static boolean isLever(Block block) {
        return block instanceof LeverBlock;
    }

    public static boolean isDoor(Block block) {
        return block instanceof DoorBlock || block instanceof TrapdoorBlock;
    }

    public static boolean isLectern(Block block) {
        return block instanceof LecternBlock;
    }
}

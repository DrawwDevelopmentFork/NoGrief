package io.github.indicode.fabric.itsmine;

import io.github.indicode.fabric.itsmine.mixin.BlockActionPacketMixin;
import io.github.indicode.fabric.itsmine.mixin.BucketItemMixin;
import io.github.indicode.fabric.permissions.Thimble;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.BlockActionS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/**
 * @author Indigo Amann
 */
public class Functions {
    public static void doPistonUpdate(ServerWorld world, Packet packet) {
        if (packet instanceof BlockActionS2CPacket) {
            BlockActionPacketMixin accessor = (BlockActionPacketMixin) packet;
            if ((accessor.getBlock() == Blocks.PISTON || accessor.getBlock() == Blocks.PISTON_HEAD || accessor.getBlock() == Blocks.STICKY_PISTON)) {
                Direction direction = Direction.byId(accessor.getData());
                BlockPos pos = BlockPos.fromLong(accessor.getPos().offset(2, direction));
                world.getChunkManager().markForUpdate(pos);
            }
        }
    }
    public static boolean canPlayerActuallyModifyAt(ServerWorld world, PlayerEntity playerEntity_1, BlockPos blockPos_1) {
        return !world.getServer().isSpawnProtected(world, blockPos_1, playerEntity_1) && world.getWorldBorder().contains(blockPos_1);
    };
    public static boolean isBucketEmpty(BucketItem item) {
        return ((BucketItemMixin)item).getFluid() != Fluids.EMPTY;
    }
    private static Map<UUID, Boolean> flyers = new HashMap<>();
    public static boolean canClaimFly(ServerPlayerEntity player) {
        if (flyers.containsKey(player.getGameProfile().getId())) {
            return flyers.get(player.getGameProfile().getId());
        } else {
            refreshFly(player);
            return canClaimFly(player);
        }
    }
    public static void refreshFly(ServerPlayerEntity player) {
        flyers.put(player.getGameProfile().getId(), player.world.getServer().getPlayerManager().isOperator(player.getGameProfile()) || Thimble.PERMISSIONS.hasPermission("itsmine.claim_fly", player.getGameProfile().getId()));
    }
    private static List<UUID> claimFlyNow = new ArrayList<>();
    public static boolean isClaimFlying(UUID player) {
        return claimFlyNow.contains(player);
    }
    public static void setClaimFlying(UUID player, boolean flying) {
        if (flying && !claimFlyNow.contains(player)) {
            claimFlyNow.add(player);
        } else if (!flying) {
            claimFlyNow.remove(player);
        }
    }
    public static void updateInventory(ServerPlayerEntity player) {
        player.inventory.updateItems();
    }
}

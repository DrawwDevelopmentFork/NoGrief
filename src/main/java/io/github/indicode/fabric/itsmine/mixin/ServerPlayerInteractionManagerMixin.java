package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * @author Indigo Amann
 */
@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Shadow public ServerPlayerEntity player;

    @Redirect(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;onUse(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"))
    public ActionResult activateIfPossible(BlockState state, World world, PlayerEntity playerEntity_1, Hand hand_1, BlockHitResult blockHitResult_1) {
        BlockPos pos = blockHitResult_1.getBlockPos();
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, world.getDimension().getType());
        if (claim != null) {
            UUID uuid = playerEntity_1.getGameProfile().getId();
            if (
                    claim.hasPermission(uuid, Claim.Permission.INTERACT_BLOCKS) ||
                            (BlockUtils.isButton(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.PRESS_BUTTONS)) ||
                            (BlockUtils.isLever(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.USE_LEVERS)) ||
                            (BlockUtils.isDoor(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.INTERACT_DOORS)) ||
                            (BlockUtils.isContainer(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.CONTAINER)) ||
                            (BlockUtils.isChest(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.CONTAINER_CHEST)) ||
                            (BlockUtils.isEnderchest(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.CONTAINER_ENDERCHEST)) ||
                            (BlockUtils.isShulkerBox(state.getBlock()) && claim.hasPermission(uuid, Claim.Permission.CONTAINER_SHULKERBOX))
            ) {
                return state.onUse(world, playerEntity_1, hand_1, blockHitResult_1);
            }
            else {
                if (state.getBlock() instanceof DoorBlock && playerEntity_1 instanceof ServerPlayerEntity) {
                    DoubleBlockHalf half = state.get(DoorBlock.HALF);
                    ((ServerPlayerEntity) playerEntity_1).networkHandler.sendPacket(new BlockUpdateS2CPacket(world, half == DoubleBlockHalf.LOWER ? pos.up() : pos.down(1)));
                }

                if (BlockUtils.isContainer(state.getBlock())) {
                    playerEntity_1.sendMessage(Messages.MSG_OPEN_CONTAINER);
                }

                player.inventory.markDirty();
                player.inventory.updateItems();
                return ActionResult.FAIL;
            }
        }

        player.inventory.updateItems();
        return state.onUse(world, playerEntity_1, hand_1, blockHitResult_1);
    }



    @Redirect(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z", ordinal = 2))
    public boolean allowItemUse(ItemStack stack, PlayerEntity playerEntity_1, World world_1, ItemStack itemStack_1, Hand hand_1, BlockHitResult blockHitResult_1) {
        BlockPos pos = blockHitResult_1.getBlockPos().offset(blockHitResult_1.getSide());
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, playerEntity_1.world.getDimension().getType());
        if (claim != null && !stack.isEmpty()) {
            UUID uuid = playerEntity_1.getGameProfile().getId();
            if (
                    claim.hasPermission(uuid, Claim.Permission.USE_ITEMS_ON_BLOCKS) ||
                            (stack.getItem() instanceof BlockItem && claim.hasPermission(uuid, Claim.Permission.BUILD)) ||
                            (stack.getItem() instanceof BucketItem && claim.hasPermission(uuid, Claim.Permission.BUILD))
            )
                return false;

            if (!playerEntity_1.getStackInHand(hand_1).isEmpty())
                playerEntity_1.sendMessage(Messages.MSG_PLACE_BLOCK);

            Functions.updateInventory(player);
            return true;
        }

        Functions.updateInventory(player);
        return stack.isEmpty();
    }

    @Redirect(method = "processBlockBreakingAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;canPlayerModifyAt(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;)Z"))
    public boolean canBreak(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (player.inventory.getMainHandStack().getItem() == Items.STICK) {
            if (!player.isSneaking()) {
                Pair<BlockPos, BlockPos> posPair = ClaimManager.INSTANCE.stickPositions.get(player);
                if (posPair != null) {
                    posPair = new Pair<>(posPair.getLeft(), pos);
                    ClaimManager.INSTANCE.stickPositions.put(player, posPair);
                    player.sendMessage(new LiteralText("Position #2 set: " + pos.getX() + (Config.claims2d ? "" : " " + pos.getY()) + " " + pos.getZ()).formatted(Formatting.GREEN));
                    if (posPair.getLeft() != null) {
                        player.sendMessage(new LiteralText("Area Selected. Type /claim create <name> to create your claim!").formatted(Formatting.GOLD));
                        if (!Config.claims2d)
                            player.sendMessage(new LiteralText("Remember that claims are three dimensional. Don't forget to expand up/down or select a big enough area...").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC));
                    }
                    return false;
                }
            }
        }
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, player.world.getDimension().getType());
        if (claim != null) {
            UUID uuid = player.getGameProfile().getId();
            if (
                    claim.hasPermission(uuid, Claim.Permission.BUILD)
            ) return Functions.canPlayerActuallyModifyAt(world, player, pos);
            player.sendMessage(Messages.MSG_BREAK_BLOCK);
            return false;
        }
        return world.canPlayerModifyAt(player, pos);
    }
}

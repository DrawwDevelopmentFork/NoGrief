package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import io.github.indicode.fabric.itsmine.Functions;
import io.github.indicode.fabric.itsmine.Messages;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author Indigo Amann
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
//    @Inject(method = "canPlayerModifyAt", at = @At("HEAD"), cancellable = true)
//    private void canMine(PlayerEntity player, BlockPos blockPos_1, CallbackInfoReturnable<Boolean> ci) {
//        if (player.world.isClient()) return;
//        Claim claim = ClaimManager.INSTANCE.getClaimAt(blockPos_1, player.getEntityWorld().getDimension().getType());
//        if (claim != null) {
//            if (!claim.hasPermission(player.getGameProfile().getId(), Claim.Permission.SPAWN_PROTECTION)) {
//                player.sendMessage(Messages.NO_PERMISSION);
//                ci.setReturnValue(false);
//            }
//        }
//    }
    @Redirect(method = "sendBlockActions", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAround(Lnet/minecraft/entity/player/PlayerEntity;DDDDLnet/minecraft/world/dimension/DimensionType;Lnet/minecraft/network/Packet;)V"))
    private void sendPistonUpdate(PlayerManager manager, PlayerEntity playerEntity_1, double double_1, double double_2, double double_3, double double_4, DimensionType dimensionType_1, Packet<?> packet_1) {
        manager.sendToAround(playerEntity_1, double_1, double_2, double_3, double_4, dimensionType_1, packet_1);
        Functions.doPistonUpdate((ServerWorld) (Object)this, packet_1);
    }
}

package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import io.github.indicode.fabric.itsmine.Functions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.LilyPadItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author Indigo Amann
 */
@Mixin({BucketItem.class, LilyPadItem.class})
public class PlaceOnUseBlockMixin extends Item {
    public PlaceOnUseBlockMixin(Settings settings) {
        super(settings);
    }

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;canPlayerModifyAt(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;)Z"))
    public boolean canActuallyModify(World world, PlayerEntity playerEntity_1, BlockPos blockPos_1) {
        if (!(world instanceof ServerWorld)) return world.canPlayerModifyAt(playerEntity_1, blockPos_1);
        Claim claim = ClaimManager.INSTANCE.getClaimAt(blockPos_1, playerEntity_1.world.getDimension().getType());
        if (claim != null && claim.hasPermission(playerEntity_1.getGameProfile().getId(), Claim.Permission.BUILD))
            return Functions.canPlayerActuallyModifyAt((ServerWorld) world, playerEntity_1, blockPos_1);
        else {
            if ((Object) this instanceof LilyPadItem){
                ((ServerWorld)world).getChunkManager().markForUpdate(blockPos_1);
            }
            return false;
        }
    }
}

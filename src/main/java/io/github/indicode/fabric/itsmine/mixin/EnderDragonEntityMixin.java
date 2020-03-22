package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnderDragonEntity.class)
public class EnderDragonEntityMixin {

    @Redirect(method = "destroyBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean dontTouchieMyBlocksStupidDragon(World world, BlockPos pos, boolean move) {
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, world.dimension.getType());

        if (claim != null && !claim.settings.getSetting(Claim.ClaimSettings.Setting.EXPLOSION_DESTRUCTION)) {
            return false;
        }

        return world.removeBlock(pos, false);
    }

}

package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {

    @Inject(method = "onLandedUpon", at = @At(value = "HEAD", target = "Lnet/minecraft/block/FarmlandBlock;onLandedUpon(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;F)V"), cancellable = true)
    private void dontYouDareFarmMe(World world, BlockPos pos, Entity entity, float distance, CallbackInfo ci) {
        Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, world.dimension.getType());

        if (claim != null && !claim.settings.getSetting(Claim.ClaimSettings.Setting.BREAK_FARMLANDS)) {
            ci.cancel();
        }
    }

}

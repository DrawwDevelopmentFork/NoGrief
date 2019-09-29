package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author Indigo Amann
 */
@Mixin(Explosion.class)
public class ExplosionMixin {
    @Redirect(method = "collectBlocksAndDamageEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState theyCallMeBedrock(World world, BlockPos blockPos_1) {
        Claim claim = ClaimManager.INSTANCE.getClaimAt(blockPos_1, world.getDimension().getType());
        if (claim != null && !world.isAir(blockPos_1)) {
            if (!(Boolean) claim.getSettingsAt(blockPos_1).getSetting(Claim.ClaimSettings.Setting.EXPLOSIONS)) return Blocks.BEDROCK.getDefaultState();
        }
        return world.getBlockState(blockPos_1);
    }
}
package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.SpawnType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(SpawnRestriction.class)
public class SpawnRestrictionMixin {
	@Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
	private static void canSpawnInClaim(EntityType<?> type, IWorld world, SpawnType spawnType, BlockPos pos, Random random, CallbackInfoReturnable<Boolean> cir) {
		if (ClaimManager.INSTANCE == null) {
			cir.setReturnValue(false);
			return;
		}
		Claim claim = ClaimManager.INSTANCE.getClaimAt(pos, world.getDimension().getType());
		if (claim != null && !claim.settings.getSetting(Claim.ClaimSettings.Setting.MOB_SPAWNING)) {
			cir.setReturnValue(false);
		}
	}
}

package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractFireBlock.class)
public abstract class AbstractFireBlockMixin {

    @Redirect(method = "onEntityCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isFireImmune()Z"))
    private boolean neverGonnaBurnYouUp(Entity entity) {
        PlayerEntity player = (PlayerEntity) entity;
        Claim claim = ClaimManager.INSTANCE.getClaimAt(entity.getSenseCenterPos(), entity.dimension);
        if (claim != null && !claim.settings.getSetting(Claim.ClaimSettings.Setting.FIRE_DAMAGE)) {
            return true;
        }

        return entity.isFireImmune();
    }

}

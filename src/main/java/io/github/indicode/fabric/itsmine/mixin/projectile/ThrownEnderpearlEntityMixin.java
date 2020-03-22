package io.github.indicode.fabric.itsmine.mixin.projectile;

import io.github.indicode.fabric.itsmine.Functions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.thrown.ThrownEnderpearlEntity;
import net.minecraft.entity.thrown.ThrownItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrownEnderpearlEntity.class)
public abstract class ThrownEnderpearlEntityMixin {

    @Redirect(method = "onEntityHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean imInvincible(Entity entity, DamageSource source, float amount) {
        if (Functions.canDamageWithProjectile((ThrownItemEntity) (Object) this, entity)) {
            return entity.damage(source, amount);
        }

        return false;
    }

}

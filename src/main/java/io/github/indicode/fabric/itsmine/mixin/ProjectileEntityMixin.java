package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import io.github.indicode.fabric.itsmine.Messages;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.Projectile;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * @author Indigo Amann
 */
@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {
    @Redirect(method = "onEntityHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    public boolean imInvincible(Entity entity, DamageSource damageSource_1, float float_1) {
        if (entity.world.isClient()) return entity.damage(damageSource_1, float_1);
        ProjectileEntity projectile = (ProjectileEntity)(Object)this;

        if (((ProjectileEntity)(Object)this).getServer().getPlayerManager().getPlayer(((OwnedProjectile)projectile).getOwner()) != null) {
            PlayerEntity playerEntity_1 = ((ProjectileEntity)(Object)this).getServer().getPlayerManager().getPlayer(((OwnedProjectile)projectile).getOwner());
            Claim claim = ClaimManager.INSTANCE.getClaimAt(entity.getSenseCenterPos(), entity.world.getDimension().getType());
            if (claim != null && entity != playerEntity_1) {
                if (!claim.hasPermission(playerEntity_1.getGameProfile().getId(), Claim.Permission.DAMAGE_ENTITY)) {
                    playerEntity_1.sendMessage(Messages.MSG_DAMAGE_ENTITY);
                    projectile.kill(); // You do not want an arrow bouncing between two armor stands
                    return false;
                }
            }
        }
        return entity.damage(damageSource_1, float_1);
    }
}

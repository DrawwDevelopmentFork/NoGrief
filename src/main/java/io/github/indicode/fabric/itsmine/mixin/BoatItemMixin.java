package io.github.indicode.fabric.itsmine.mixin;

import io.github.indicode.fabric.itsmine.Claim;
import io.github.indicode.fabric.itsmine.ClaimManager;
import io.github.indicode.fabric.itsmine.Messages;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ModifiableWorld;
import net.minecraft.world.RayTraceContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoatItem.class)
public abstract class BoatItemMixin extends Item {

    public BoatItemMixin(Settings settings) {
        super(settings);
    }

    @Inject(method = "use", at = @At(value = "HEAD"), cancellable = true)
    private void dontSpawn(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack itemStack = user.getStackInHand(hand);
        HitResult hitResult = rayTrace(world, user, RayTraceContext.FluidHandling.ANY);
        Vec3d pos = hitResult.getPos();

        Claim claim = ClaimManager.INSTANCE.getClaimAt(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), user.dimension);

        if (claim != null && !claim.hasPermission(user.getUuid(), Claim.Permission.BUILD)) {
            user.sendMessage(Messages.NO_PERMISSION);
            cir.setReturnValue(TypedActionResult.fail(itemStack));
        }

    }

}

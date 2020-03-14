package io.github.indicode.fabric.itsmine;

import net.minecraft.entity.Entity;

public class EntityUtils {

    public static boolean isPassive(Entity entity) {
        return entity.getType().getCategory().isPeaceful();
    }

    public static boolean isHostile(Entity entity) {
        return !entity.getType().getCategory().isPeaceful();
    }
}

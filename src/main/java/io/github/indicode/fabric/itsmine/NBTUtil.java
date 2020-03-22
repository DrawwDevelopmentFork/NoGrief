package io.github.indicode.fabric.itsmine;


import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class NBTUtil {

    public static UUID getUUID(CompoundTag tag, String key){
        if (tag.contains(key + "Least") && tag.contains(key + "Most")) {
            final long least = tag.getLong(key + "Least");
            final long most = tag.getLong(key + "Most");
            return new UUID (most, least);
        }

        return tag.getUuidNew(key);
    }

    public static boolean containsUUID(CompoundTag tag, String key){
        return tag.contains(key + "Least") && tag.contains(key + "Most");
    }

}

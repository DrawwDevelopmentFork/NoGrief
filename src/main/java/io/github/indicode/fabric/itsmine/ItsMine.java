package io.github.indicode.fabric.itsmine;

import io.github.indicode.fabric.permissions.PermChangeBehavior;
import io.github.indicode.fabric.permissions.Thimble;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.server.command.ServerCommandSource;

import java.lang.reflect.InvocationTargetException;
import java.security.Permission;
import java.util.UUID;

/**
 * @author Indigo Amann
 */
public class ItsMine implements ModInitializer {
    private static Permissions permissions;

    @Override
    public void onInitialize() {
        Config.sync(false);
        permissions = new Permissions();

        //TODO: Enable when developing
        //SharedConstants.isDevelopment = true;
    }
    public static Permissions permissions() {
        return permissions;
    }
    public static String blocksToAreaString3d(int blocks) {
        int base = (int) Math.floor(Math.cbrt(blocks));
        int additionalBlocks = blocks - (int) Math.pow(base, 3);
        int extraRows = (int) Math.floor(Math.cbrt(Math.floor((float)additionalBlocks / base)));
        int leftoverBlocks = additionalBlocks % base;
        return (base + extraRows) + "x" + base + "x" + base + "(+" + leftoverBlocks + ")";
    }
    public static String blocksToAreaString2d(int blocks) {
        int base = (int) Math.floor(Math.sqrt(blocks));
        int additionalBlocks = blocks - (int) Math.pow(base, 2);
        int extraRows = (int) Math.floor((float)additionalBlocks / base);
        int leftoverBlocks = additionalBlocks % base;
        return (base + extraRows) + "x" + base + "(+" + leftoverBlocks + ")";
    }
    public static String blocksToAreaString(int blocks) {
        return Config.claims2d ? blocksToAreaString2d(blocks) : blocksToAreaString3d(blocks);
    }
}

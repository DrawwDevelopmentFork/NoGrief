package io.github.indicode.fabric.itsmine;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.indicode.fabric.permissions.PermChangeBehavior;
import io.github.indicode.fabric.permissions.Thimble;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public class Permissions {
    private Manager manager;
    private boolean present;

    public Permissions() {
        Logger logger = (Logger) LogManager.getLogger("ItsMine");
        logger.info("Setting up Permissions...");
        this.manager = Manager.fromString(Config.permissionManager);

        if (manager == Manager.VANILLA) {
            this.present = false;
            return;
        }

        logger.info("Checking " + manager.getName() + " for Availability");

        this.present = this.checkPresent();

        if (!this.present) {
            logger.warn("**** " + manager.getName() + " is not present! Switching to vanilla operator system");
            logger.warn("     You need to install either LuckPerms for Fabric Or Thimble to manage the permissions");
            this.manager = Manager.NONE;
            return;
        }

        logger.info("Using " + manager.getName() + " as the Permission Manager");


        if (manager == Manager.THIMBLE) {
            Thimble.permissionWriters.add((map, server) -> {
                for (Command value : Command.values()) {
                    map.registerPermission(value.NODE, PermChangeBehavior.UPDATE_COMMAND_TREE);
                }
            });
        }
    }

    public boolean hasPermission(ServerCommandSource src, Command permission, int opLevel) {
        if (present) {
            if (manager == Manager.LUCKPERMS) {
                return fromLuckPerms(src, permission.getNode(), opLevel);
            }

            if (manager == Manager.THIMBLE) {
                return fromThimble(src, permission.getNode(), opLevel);
            }
        }

        return src.hasPermissionLevel(opLevel);
    }

    public boolean hasPermission(ServerCommandSource src, String permission, int opLevel) {
        if (present) {
            if (manager == Manager.LUCKPERMS) {
                return fromLuckPerms(src, permission, opLevel);
            }

            if (manager == Manager.THIMBLE) {
                return fromThimble(src, permission, opLevel);
            }
        }

        return src.hasPermissionLevel(opLevel);
    }

    public boolean hasPermission(UUID uuid, String permission) {
        if (present) {
            if (manager == Manager.LUCKPERMS) {
                LuckPerms luckPerms = LuckPermsProvider.get();

                User user = luckPerms.getUserManager().getUser(uuid);

                if (user != null) {
                    QueryOptions options = luckPerms.getContextManager().getStaticQueryOptions();
                    return user.getCachedData().getPermissionData(options).checkPermission(permission).asBoolean();
                }
            }

            if (manager == Manager.THIMBLE) {
                return Thimble.PERMISSIONS.hasPermission(permission, uuid);
            }
        }

        return false;
    }

    private boolean fromLuckPerms(ServerCommandSource src, String perm, int op) {
        LuckPerms luckPerms = LuckPermsProvider.get();

        try {
            ServerPlayerEntity player = src.getPlayer();
            User user = luckPerms.getUserManager().getUser(player.getUuid());

            if (user != null) {
                QueryOptions options = luckPerms.getContextManager().getQueryOptions(player);
                return user.getCachedData().getPermissionData(options).checkPermission(perm).asBoolean();
            }

        } catch (CommandSyntaxException ignored) {
        }

        return src.hasPermissionLevel(op);
    }

    private boolean fromThimble(ServerCommandSource src, String perm, int op) {
        return Thimble.hasPermissionOrOp(src, perm, op);
    }

    private boolean checkPresent() {
        if (manager == Manager.NONE) {
            return false;
        }

        try {
            if (manager == Manager.LUCKPERMS) {
                try {
                    LuckPermsProvider.get();
                    return true;
                } catch (Throwable ignored) {
                }
            }

            if (manager == Manager.THIMBLE) {
                Thimble.permissionWriters.get(0);
            }

            return FabricLoader.getInstance().getModContainer(manager.getName().toLowerCase(Locale.ROOT)).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean managerPresent() {
        return this.present;
    }

    public Manager getManager() {
        return this.manager;
    }

    public enum Manager {
        VANILLA("Vanilla", ""),
        NONE("none", ""),
        LUCKPERMS("LuckPerms", "net.luckperms.api.LuckPerms"),
        THIMBLE("Thimble", "io.github.indicode.fabric.permissions.Thimble");

        private final String name;
        private final String classPath;

        Manager(final String name, final String classPath) {
            this.name = name;
            this.classPath = classPath;
        }

        public String getName() {
            return this.name;
        }

        public final String getClassPath() {
            return this.classPath;
        }

        @NotNull
        public static Manager fromString(String str) {
            for (Manager value : Manager.values()) {
                if (value.name.equalsIgnoreCase(str)) {
                    return value;
                }
            }

            return Manager.NONE;
        }
    }

    public enum Command {
        SPECIFY_GROUPS("specify_groups"),
        CLAIM_FLY("claim_fly"),
        INFINITE_BLOCKS("infinite_blocks"),
        ADMIN_INFINITE_CLAIM("admin.infinite_claim"),
        ADMIN_CHECK_OTHERS("admin.check_others"),
        ADMIN_MODIFY_BALANCE("admin.modify_balance"),
        ADMIN_MODIFY("admin.modify"),
        ADMIN_MODIFY_PERMISSIONS("admin.modify_permissions"),
        ADMIN_IGNORE_CLAIMS("admin.ignore_claims");

        private final String NODE;
        Command(final String node) {
            this.NODE = node;
        }

        public String getNode() {
            return "itsmine." + this.NODE;
        }
    }
}

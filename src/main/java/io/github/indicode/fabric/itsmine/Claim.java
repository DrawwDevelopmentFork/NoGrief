package io.github.indicode.fabric.itsmine;

import blue.endless.jankson.annotation.Nullable;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.dimension.DimensionType;

import java.util.*;

import static io.github.indicode.fabric.itsmine.NBTUtil.*;

/**
 * @author Indigo Amann
 */
public class Claim {
    public String name;
    public BlockPos min, max;
    public @Nullable BlockPos tpPos;
    public DimensionType dimension;
    public List<Claim> children = new ArrayList<>();
    public ClaimSettings settings = new ClaimSettings();
    public PermissionManager permissionManager = new PermissionManager();
    public UUID claimBlockOwner = null;
    public String customOwnerName, enterMessage, leaveMessage;
    public Claim() {

    }
    public Claim(CompoundTag tag) {
        fromTag(tag);
    }
    public Claim(String name, UUID claimBlockOwner, BlockPos min, BlockPos max, DimensionType dimension) {
        this(name, claimBlockOwner, min, max, dimension, null);
    }
    public Claim(String name, UUID claimBlockOwner, BlockPos min, BlockPos max, DimensionType dimension, @Nullable BlockPos tpPos) {
        this.claimBlockOwner = claimBlockOwner;
        this.min = min;
        this.max = max;
        this.name = name;
        this.dimension = dimension;
        this.tpPos = tpPos;
    }
    public boolean includesPosition(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getY() >= min.getY() && pos.getZ() >= min.getZ() &&
                pos.getX() <= max.getX() && pos.getY() <= max.getY() && pos.getZ() <= max.getZ();
    }
    public boolean intersects(Claim claim) {
        return intersects(claim, true);
    }
    public boolean intersects(Claim claim, boolean checkOther) {
        if (claim == null) return false;
        if (!claim.dimension.equals(dimension)) return false;
        BlockPos a = min,
                b = max,
                c = new BlockPos(max.getX(), min.getY(), min.getZ()),
                d = new BlockPos(min.getX(), max.getY(), min.getZ()),
                e = new BlockPos(min.getX(), min.getY(), max.getZ()),
                f = new BlockPos(max.getX(), max.getY(), min.getZ()),
                g = new BlockPos(max.getX(), min.getY(), max.getZ()),
                h = new BlockPos(min.getX(), max.getY(), max.getZ());
        if (
                claim.includesPosition(a) ||
                claim.includesPosition(b) ||
                claim.includesPosition(c) ||
                claim.includesPosition(d) ||
                claim.includesPosition(e) ||
                claim.includesPosition(f) ||
                claim.includesPosition(g) ||
                claim.includesPosition(h)
        ) return true;
        else return checkOther && claim.intersects(this, false);
    }
    @Nullable
    public Claim getZoneCovering(BlockPos pos) {
        if (includesPosition(pos)) {
            for (Claim child : children) {
                Claim value = child.getZoneCovering(pos);
                if (value != null) {
                    return value;
                }
            }

            return this;
        }

        return null;
    }
    /*public ClaimSettings getSettingsAt(BlockPos pos) {
        Claim at = getZoneCovering(pos);
        if (at != null) {
            return at.settings;
        } else return settings;
    }
    public ClaimPermissions getPermissionsAt(UUID player, BlockPos pos) {
        Claim at = getZoneCovering(pos);
        if (at != null) {
            return at.getPlayerPermissions(player);
        } else return settings;
    }*/
    public boolean hasPermission(UUID player, Permission permission) {
        return ClaimManager.INSTANCE.ignoringClaims.contains(player) || permissionManager.hasPermission(player, permission);
    }
    //public boolean hasPermissionAt(UUID player, ClaimPermissions.Permission permission, BlockPos pos) {
    //    return player.equals(owner) || ClaimManager.INSTANCE.ignoringClaims.contains(player) || getPermissionsAt(player, pos).hasPermission(permission);
    //}
    public void addSubzone(Claim claim) {
        if (claim != null && claim.dimension == dimension && includesPosition(claim.min) && includesPosition(claim.max)) {
            children.add(claim);
        } else throw new IllegalArgumentException("Subzone must be inside the original claim, in the same dimension, and not null");
    }
    public void expand(BlockPos min, BlockPos max) {
        this.min = this.min.add(min);
        this.max = this.max.add(max);
    }
    public BlockPos getSize() {
        return max.subtract(min);
    }
    public void expand(BlockPos modifier) {
        if (modifier.getX() > 0) max = max.add(modifier.getX(), 0, 0);
        else min = min.add(modifier.getX(), 0, 0);
        if (modifier.getY() > 0) max = max.add(0, modifier.getY(), 0);
        else min = min.add(0, modifier.getY(), 0);
        if (modifier.getZ() > 0) max = max.add(0, 0, modifier.getZ());
        else min = min.add(0, 0, modifier.getZ());
    }
    public boolean shrink(BlockPos modifier) {
        if (modifier.getX() < 0) {
            if (min.getX() - modifier.getX() > max.getX()) return false;
            min = min.add(-modifier.getX(), 0, 0);
        } else {
            if (max.getX() - modifier.getX() < min.getX()) return false;
            max = max.add(-modifier.getX(), 0, 0);
        }
        if (modifier.getY() < 0) {
            if (min.getY() - modifier.getY() > max.getY()) return false;
            min = min.add(0, -modifier.getY(), 0);
        } else {
            if (max.getY() - modifier.getY() < min.getY()) return false;
            max = max.add(0, -modifier.getY(), 0);
        }
        if (modifier.getZ() < 0) {
            if (min.getZ() - modifier.getZ() > max.getZ()) return false;
            min = min.add(0, 0, -modifier.getZ());
        } else {
            if (max.getZ() - modifier.getZ() < min.getZ()) return false;
            max = max.add(0, 0, -modifier.getZ());
        }
        return true;
    }
    public void expand(Direction direction, int distance) {
        expand(new BlockPos(direction.getOffsetX() * distance, direction.getOffsetY() * distance, direction.getOffsetZ() * distance));
    }
    public boolean shrink(Direction direction, int distance) {
        return shrink(new BlockPos(direction.getOffsetX() * distance, direction.getOffsetY() * distance, direction.getOffsetZ() * distance));
    }
    public int getArea() {
        return getSize().getX() * (Config.claims2d ? 1 : getSize().getY()) * getSize().getZ();
    }


    public CompoundTag toTag() {
        CompoundTag tag =  new CompoundTag();
        {
            CompoundTag pos = new CompoundTag();
            pos.putInt("minX", min.getX());
            pos.putInt("minY", min.getY());
            pos.putInt("minZ", min.getZ());
            pos.putInt("maxX", max.getX());
            pos.putInt("maxY", max.getY());
            pos.putInt("maxZ", max.getZ());
            pos.putString("dimension", DimensionType.getId(dimension).toString());
            if (tpPos != null) {
                pos.putInt("tpX", this.tpPos.getX());
                pos.putInt("tpY", this.tpPos.getY());
                pos.putInt("tpZ", this.tpPos.getZ());
            }
            tag.put("position", pos);
        }
        {
            ListTag subzoneList = new ListTag();
            children.forEach(it -> subzoneList.add(it.toTag()));
            tag.put("subzones", subzoneList);
        }
        {
            tag.put("settings", settings.toTag());
            tag.put("permissions", permissionManager.toNBT());
            if(claimBlockOwner != null) tag.putUuidNew("top_owner", claimBlockOwner);
//            if (claimBlockOwner != null) tag.putUuidNew("top_owner", claimBlockOwner);

        }
        {
            CompoundTag meta = new CompoundTag();
            if (this.enterMessage != null) meta.putString("enterMsg", this.enterMessage);
            if (this.leaveMessage != null) meta.putString("leaveMsg", this.leaveMessage);
            tag.put("meta", meta);
        }
        if (this.customOwnerName != null) tag.putString("cOwnerName", this.customOwnerName);
        tag.putString("name", name);
        return tag;
    }
    public void fromTag(CompoundTag tag) {
        {
            CompoundTag pos = tag.getCompound("position");
            int minX = pos.getInt("minX");
            int minY = pos.getInt("minY");
            int minZ = pos.getInt("minZ");
            int maxX = pos.getInt("maxX");
            int maxY = pos.getInt("maxY");
            int maxZ = pos.getInt("maxZ");
            if (maxY == 0) maxY = 255;
            this.min = new BlockPos(minX, minY, minZ);
            this.max = new BlockPos(maxX, maxY, maxZ);
            if (pos.contains("tpX") && pos.contains("tpY") && pos.contains("tpZ")) {
                this.tpPos = new BlockPos(pos.getInt("tpX"), pos.getInt("tpY"), pos.getInt("tpZ"));
            }
            this.dimension = DimensionType.byId(new Identifier(pos.getString("dimension")));
        }
        {
            children = new ArrayList<>();
            ListTag subzoneList = (ListTag) tag.get("subzones");
            if (subzoneList != null) {
                subzoneList.forEach(it -> children.add(new Claim((CompoundTag) it)));
            }
        }
        {
            this.settings = new ClaimSettings(tag.getCompound("settings"));
            permissionManager = new PermissionManager();
            permissionManager.fromNBT(tag.getCompound("permissions"));
            if (containsUUID(tag, "top_owner")) claimBlockOwner = getUUID(tag,"top_owner");

        }
        {
            CompoundTag meta = tag.getCompound("meta");
            if (meta.contains("enterMsg")) this.enterMessage = meta.getString("enterMsg");
            if (meta.contains("leaveMsg")) this.leaveMessage = meta.getString("leaveMsg");
        }
        if (tag.contains("cOwnerName")) this.customOwnerName = tag.getString("cOwnerName");
        name = tag.getString("name");
    }

    public boolean is2d() {
        return min.getY() == 0 && max.getY() == 255;
    }

    public enum Permission {
        //Admin
        REMOVE_CLAIM("remove_claim", "Remove Claim"),
        MODIFY_SIZE("modify.size", "Modify Claim Size"),
        MODIFY_SETTINGS("modify.settings", "Change Claim Settings"),
        MODIFY_PERMISSIONS("modify.permissions", "Change Permissions"),
        //Normal
        MODIFY_PROPERTIES("modify.properties", "Modify Claim Properties"),
        BUILD("build", "Place/Break Blocks"),
        INTERACT_BLOCKS("interact_blocks", "Interact With Blocks"),
        USE_ITEMS_ON_BLOCKS("use_items_on_blocks", "Use Block Modifying items"),
        USE_LEVERS("use.levers", "Use Levers"),
        INTERACT_DOORS("interact_doors", "Use Doors"),
        INTERACT_ENTITY("interact_entity", "Entity Interaction"),
        DAMAGE_ENTITY("damage_entities", "Hurt Entities"),
        DAMAGE_ENTITY_HOSTILE("damage_entities.hostile", "Hurt Hostile Entities"),
        DAMAGE_ENTITY_PASSIVE("damage_entities.passive", "Hurt Passive Entities"),
        FLIGHT("flight", "Flight"),
        CONTAINER("container", "Open Containers"),
        CONTAINER_ENDERCHEST("container.enderchest", "Open Enderchests"),
        CONTAINER_CHEST("container.chest", "Open Chests"),
        CONTAINER_SHULKERBOX("container.shulkerbox", "Open Shulker Boxes"),
        USE_ENDER_PEARL("use.enderpearl", "Use Ender Pearls"),
        USE_BUTTONS("use.button", "Use Buttons");

        String id, name;
        Permission(String id, String name) {
            this.id = id;
            this.name =  name;
        }
        public static Permission byId(String id) {
            for (Permission permission: values()) {
                if (permission.id.equals(id)) return permission;
            }
            return null;
        }
    }
    public static class PermissionManager {
        public ClaimPermissionMap defaults = new DefaultPermissionMap();
        protected Map<UUID, ClaimPermissionMap> playerPermissions = new HashMap<>();
        protected Map<String, ClaimPermissionMap> groupPermissions = new HashMap<>();
        public boolean isPermissionSet(UUID player, Permission permission) {
            return playerPermissions.get(player) != null && playerPermissions.get(player).isPermissionSet(permission);
        }
        public boolean hasPermission(UUID player, Permission permission) {
            if (isPermissionSet(player, permission))
                return playerPermissions.get(player).hasPermission(permission);

            for (Map.Entry<String, ClaimPermissionMap> entry : groupPermissions.entrySet()) {
//                if (Thimble.PERMISSIONS.hasPermission(entry.getKey(), player) && entry.getValue().hasPermission(permission))
//                    return true;
                return ItsMine.permissions().hasPermission(player, entry.getKey()) && entry.getValue().hasPermission(permission);
            }

            return defaults.hasPermission(permission);
        }
        public void setPermission(UUID player, Permission permission, boolean enabled) {
            if (!playerPermissions.containsKey(player)) playerPermissions.put(player, new DefaultPermissionMap());
            playerPermissions.get(player).setPermission(permission, enabled);
        }
        public void clearPermission(UUID player, Permission permission) {
            if (!playerPermissions.containsKey(player)) playerPermissions.put(player, new DefaultPermissionMap());
            playerPermissions.get(player).clearPermission(permission);
        }
        public void resetPermissions(UUID player) {
            playerPermissions.remove(player);
        }
        public boolean isPermissionSet(String group, Permission permission) {
            return groupPermissions.get(group) != null && groupPermissions.get(group).isPermissionSet(permission);
        }
        public boolean hasPermission(String group, Permission permission) {
            if (isPermissionSet(group, permission)) return groupPermissions.get(group).hasPermission(permission);
            return defaults.hasPermission(permission);
        }
        public void setPermission(String group, Permission permission, boolean enabled) {
            if (!groupPermissions.containsKey(group)) groupPermissions.put(group, new DefaultPermissionMap());
            groupPermissions.get(group).setPermission(permission, enabled);
        }
        public void clearPermission(String group, Permission permission) {
            if (!groupPermissions.containsKey(group)) groupPermissions.put(group, new DefaultPermissionMap());
            groupPermissions.get(group).clearPermission(permission);
        }
        public void resetPermissions(String group) {
            groupPermissions.remove(group);
        }
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put("defaults", defaults.toRegisteredNBT());
            {
                CompoundTag players = new CompoundTag();
                if (playerPermissions != null) playerPermissions.forEach((player, map) -> {if (player != null && map != null) players.put(player.toString(), map.toRegisteredNBT());});
                tag.put("players", players);
            }
            {
                CompoundTag groups = new CompoundTag();
                groupPermissions.forEach((group, map) -> groups.put(group, map.toRegisteredNBT()));
                tag.put("groups", groups);
            }
            return tag;
        }
        public void fromNBT(CompoundTag tag) {
            defaults = ClaimPermissionMap.fromRegisteredNBT(tag.getCompound("defaults"));
            {
                CompoundTag players = tag.getCompound("players");
                playerPermissions.clear();
                players.getKeys().forEach(player -> playerPermissions.put(UUID.fromString(player), ClaimPermissionMap.fromRegisteredNBT(players.getCompound(player))));
            }
            {
                CompoundTag groups = tag.getCompound("groups");
                groupPermissions.clear();
                groups.getKeys().forEach(group -> groupPermissions.put(group, ClaimPermissionMap.fromRegisteredNBT(groups.getCompound(group))));
            }
        }
    }
    public static abstract class ClaimPermissionMap {
        protected static HashMap<String, Class<? extends ClaimPermissionMap>> mapTypes = new HashMap<>();
        protected static HashMap<Class<? extends ClaimPermissionMap>, String> reverseMapTypes = new HashMap<>();
        static {
            mapTypes.put("default", DefaultPermissionMap.class);
            reverseMapTypes.put(DefaultPermissionMap.class, "default");
            mapTypes.put("inverted", InvertedPermissionMap.class);
            reverseMapTypes.put(InvertedPermissionMap.class, "inverted");
        }
        public abstract boolean isPermissionSet(Permission permission);
        public abstract boolean hasPermission(Permission permission);
        public abstract void setPermission(Permission permission, boolean has);
        public abstract void clearPermission(Permission permission);
        public abstract void fromNBT(CompoundTag tag);
        public abstract CompoundTag toNBT();
        public CompoundTag toRegisteredNBT() {
            CompoundTag tag = toNBT();
            tag.putString("type", reverseMapTypes.get(this.getClass()));
            return tag;
        }
        public static ClaimPermissionMap fromRegisteredNBT(CompoundTag tag) {
            String type = tag.getString("type");
            tag.remove("type");
            Class<? extends ClaimPermissionMap> clazz = mapTypes.get(type);
            if (clazz == null) return new DefaultPermissionMap();
            try {
                ClaimPermissionMap map = clazz.newInstance();
                map.fromNBT(tag);
                return map;
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    public static class DefaultPermissionMap extends ClaimPermissionMap {
        private HashMap<Permission, Boolean> permissions = new HashMap<>();
        @Override
        public boolean isPermissionSet(Permission permission) {
            return permissions.containsKey(permission);
        }

        @Override
        public boolean hasPermission(Permission permission) {
            return isPermissionSet(permission) && permissions.get(permission);
        }

        @Override
        public void setPermission(Permission permission, boolean has) {
            permissions.put(permission, has);
        }

        @Override
        public void clearPermission(Permission permission) {
            permissions.remove(permission);
        }

        @Override
        public void fromNBT(CompoundTag tag) {
            permissions.clear();
            for (String permissionString : tag.getKeys()) {
                Permission permission = Permission.byId(permissionString);
                if (permission == null) continue;
                boolean allowed = tag.getBoolean(permissionString);
                permissions.put(permission, allowed);
            }
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            permissions.forEach((permission, allowed) -> {
                if (allowed != null) tag.putBoolean(permission.id, allowed);
            });
            return tag;
        }
    }
    public static class InvertedPermissionMap extends ClaimPermissionMap {

        private HashMap<Permission, Boolean> permissions = new HashMap<>();
        @Override
        public boolean isPermissionSet(Permission permission) {
            return true;
        }

        @Override
        public boolean hasPermission(Permission permission) {
            return !permissions.containsKey(permission) || permissions.get(permission);
        }

        @Override
        public void setPermission(Permission permission, boolean has) {
            permissions.put(permission, has);
        }

        @Override
        public void clearPermission(Permission permission) {
            permissions.remove(permission);
        }

        @Override
        public void fromNBT(CompoundTag tag) {
            permissions.clear();
            for (String permissionString : tag.getKeys()) {
                Permission permission = Permission.byId(permissionString);
                if (permission == null) continue;
                boolean allowed = tag.getBoolean(permissionString);
                permissions.put(permission, allowed);
            }
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            permissions.forEach((permission, allowed) -> {
                if (allowed != null) tag.putBoolean(permission.id, allowed);
            });
            return tag;
        }
    }
    public static class ClaimSettings{
        public enum Setting {
            FLIGHT_ALLOWED("flight_allowed", "Flying Enabled", true),
            EXPLOSION_DESTRUCTION("explosion_destruction", "Explosion Destroys Blocks", false),
            EXPLOSION_DAMAGE("explosion_damage", "Explosion Damages Entities", false),
            FLUID_CROSSES_BORDERS("fluid_crosses_borders", "Fluid Crosses Borders", false),
            FIRE_CROSSES_BORDERS("fire_crosses_borders", "Fire Crosses Borders", false),
            FIRE_DAMAGE("fire_damage", "Fire Damages Entities", false),
            PISTON_FROM_INSIDE("pistons_inside_border", "Pistons Cross border from Inside", true),
            PISTON_FROM_OUTSIDE("pistons_outside_border", "Pistons Cross border from Outside", false),
            MOB_SPAWNING("mob_spawn", "Natural mob spawning", true),
//            KEEP_INVENTORY("keep_inventory", "Keep Inventory", true),
            ENTER_SOUND("enter_sound", "Enter Sound", false),
            BREAK_FARMLANDS("break_farmlands", "Break Farmlands", false);

            String id, name;
            boolean defaultValue;
            Setting(String id, String name, boolean defaultValue) {
                this.id = id;
                this.name =  name;
                this.defaultValue = defaultValue;
            }
            public static ClaimSettings.Setting byId(String id) {
                for (ClaimSettings.Setting permission: values()) {
                    if (permission.id.equals(id)) return permission;
                }
                return null;
            }
        }
        public  Map<Setting, Boolean> settings = new HashMap<>();
        public ClaimSettings(CompoundTag tag) {
            fromTag(tag);
        }
        public ClaimSettings() {
        }
        public boolean getSetting(Setting setting) {
            return settings.getOrDefault(setting, setting.defaultValue);
        }
        public CompoundTag toTag() {
            CompoundTag tag =  new CompoundTag();
            this.settings.forEach((setting, data) -> {
                tag.putBoolean(setting.id, data);
            });
            return tag;
        }
        public void fromTag(CompoundTag tag) {
            settings.clear();
            tag.getKeys().forEach(key -> {
                Setting setting = Setting.byId(key);
                if (setting == null) return;
                this.settings.put(setting, tag.getBoolean(key));
            });
        }
    }
    public enum Event {
        ENTER_CLAIM("enter", Config.msg_enter_default),
        LEAVE_CLAIM("leave", Config.msg_leave_default);

        String id;
        String defaultValue;
        Event(String id, String defaultValue) {
            this.id = id;
            this.defaultValue = defaultValue;
        }

        @Nullable
        public static Event getById(String id) {
            for (Event value : values()) {
                if (value.id.equalsIgnoreCase(id)) {
                    return value;
                }
            }

            return null;
        }
    }

    public enum HelpBook {
        GET_STARTED("getStarted", Messages.GET_STARTED, "Get Started"),
        COMMAND("commands", Messages.HELP, "Claim Commands"),
        PERMS_AND_SETTINGS("perms_and_settings", Messages.SETTINGS_AND_PERMISSIONS, "Claim Permissions and Settings");

        String id;
        String title;
        Text[] texts;
        HelpBook(String id, Text[] texts, String title) {
            this.id = id;
            this.title = title;
            this.texts = texts;
        }

        public String getCommand() {
            return "/claim help " + this.id + " %page%";
        }

        @Nullable
        public static HelpBook getById(String id) {
            for (HelpBook value : values()) {
                if (value.id.equalsIgnoreCase(id)) {
                    return value;
                }
            }

            return null;
        }
    }
}

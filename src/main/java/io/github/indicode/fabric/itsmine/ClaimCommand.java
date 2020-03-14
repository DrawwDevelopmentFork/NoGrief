package io.github.indicode.fabric.itsmine;

import blue.endless.jankson.annotation.Nullable;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.indicode.fabric.itsmine.mixin.BlockUpdatePacketMixin;
import io.github.indicode.fabric.permissions.Thimble;
import io.github.indicode.fabric.permissions.command.PermissionCommand;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.arguments.BlockPosArgumentType;
import net.minecraft.command.arguments.EntityArgumentType;
import net.minecraft.command.arguments.GameProfileArgumentType;
import net.minecraft.command.arguments.PosArgument;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;

/**
 * @author Indigo Amann
 */
public class ClaimCommand {
    private static void validateCanAccess(ServerPlayerEntity player, Claim claim, boolean admin) throws CommandSyntaxException {
        if (claim == null) {
            throw new SimpleCommandExceptionType(Messages.INVALID_CLAIM).create();
        }

        if (!admin && !claim.permissionManager.hasPermission(player.getGameProfile().getId(), Claim.Permission.MODIFY_FLAGS)) {
            throw new SimpleCommandExceptionType(Messages.NO_PERMISSION).create();
        }
    }

    private static void validateClaim(Claim claim) throws CommandSyntaxException {
        if (claim == null) throw new SimpleCommandExceptionType(Messages.INVALID_CLAIM).create();
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> getClaimArgument() {
        return argument("claim", StringArgumentType.word()).suggests(CLAIM_PROVIDER);
    }

    private static Predicate<ServerCommandSource> perm(String str) {
        return perm(str, 2);
    }
    private static Predicate<ServerCommandSource> perm(String str, int op) {
        return source -> Thimble.hasPermissionOrOp(source, "itsmine." + str, op);
    }
    private static final Predicate<ServerCommandSource> PERMISSION_CHECK_ADMIN = src -> perm("admin").test(src) || perm("admin.modify_balance").test(src) ||
            perm("admin.infinite_blocks").test(src) || perm("admin.modify").test(src) ||
            perm("admin.modify_balance").test(src) || perm("admin.ignore_claims").test(src) ||
            perm("admin.modify_permissions").test(src);

    public static final SuggestionProvider<ServerCommandSource> DIRECTION_SUGGESTION_BUILDER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (Direction direction: Direction.values()) {
            if (Config.claims2d && (direction == Direction.DOWN || direction == Direction.UP)) continue;
            strings.add(direction.getName());
        };
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> BOOK_SUGGESTIONS = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (Claim.HelpBook value : Claim.HelpBook.values()) {
            strings.add(value.id);
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> CLAIM_PROVIDER = (source, builder) -> {
        ServerPlayerEntity player = source.getSource().getPlayer();
        List<String> names = new ArrayList<>();
        Claim current = ClaimManager.INSTANCE.getClaimAt(player.getSenseCenterPos(), player.dimension);
        if (current != null) names.add(current.name);
        for (Claim claim : ClaimManager.INSTANCE.getPlayerClaims(player.getGameProfile().getId())) {
            if (claim != null) {
                names.add(claim.name);
            }
        }
        return CommandSource.suggestMatching(names, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> PLAYERS_PROVIDER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (ServerPlayerEntity player : source.getSource().getMinecraftServer().getPlayerManager().getPlayerList()) {
            strings.add(player.getEntityName());
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> SETTINGS_PROVIDER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (Claim.ClaimSettings.Setting value : Claim.ClaimSettings.Setting.values()) {
            strings.add(value.id);
        }
        for (Claim.Permission value : Claim.Permission.values()) {
            strings.add(value.id);
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> PERMISSIONS_PROVIDER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (Claim.Permission value : Claim.Permission.values()) {
            strings.add(value.id);
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> MESSAGE_EVENTS_PROVIDER = (source, builder) -> {
        List<String> strings = new ArrayList<>();
        for (Claim.Event value : Claim.Event.values()) {
            strings.add(value.id);
        }
        return CommandSource.suggestMatching(strings, builder);
    };
    public static final SuggestionProvider<ServerCommandSource> EVENT_MESSAGE_PROVIDER = (source, builder) -> {
        if (!builder.getRemaining().isEmpty())
            return builder.buildFuture();

        List<String> strings = new ArrayList<>();
        strings.add("reset");
        try {
            Claim claim = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(source, "claim"));
            Claim.Event eventType = Claim.Event.getById(StringArgumentType.getString(source, "messageEvent"));

            if (eventType != null && claim != null) {
                String message = eventType == Claim.Event.ENTER_CLAIM ? claim.enterMessage : claim.leaveMessage;
                if (message != null) strings.add(message);
            }

        } catch (Exception ignored) {
        }

        return CommandSource.suggestMatching(strings, builder);
    };

    private static void registerHelp(LiteralArgumentBuilder<ServerCommandSource> builder, String id, Text[] texts, String title) {
        LiteralArgumentBuilder<ServerCommandSource> argumentBuilder = literal(id)
                .executes((context) -> sendPage(context.getSource(), texts, 1, title, "/claim help " + id + " %page%"));
        RequiredArgumentBuilder<ServerCommandSource, Integer> pageArgument = argument("page", IntegerArgumentType.integer(1, texts.length))
                .executes((context) -> sendPage(context.getSource(), texts, IntegerArgumentType.getInteger(context, "page"), title, "/claim help " + id + " %page%"));

        argumentBuilder.then(pageArgument);
        builder.then(argumentBuilder);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal("claim")
                .executes((context) -> sendPage(context.getSource(), Messages.GET_STARTED, 1, "Get Started", "/claim help getStarted %page%"));

        {
            LiteralArgumentBuilder<ServerCommandSource> help = literal("help");
            help.executes((context) -> sendPage(context.getSource(), Messages.HELP, 1, "Its Mine!", "/claim help commands %page%"));

            RequiredArgumentBuilder<ServerCommandSource, String> id = argument("id", StringArgumentType.word())
                    .suggests(BOOK_SUGGESTIONS);
            RequiredArgumentBuilder<ServerCommandSource, Integer> page = argument("page", IntegerArgumentType.integer(1));

            page.executes((context) -> {
                Claim.HelpBook book = Claim.HelpBook.getById(StringArgumentType.getString(context, "id"));
                if (book == null) {
                    context.getSource().sendError(new LiteralText("Invalid Book!"));
                    return -1;
                }
                int p = IntegerArgumentType.getInteger(context, "page");

                if (p > book.texts.length) p = 1;
                return sendPage(context.getSource(), book.texts, p, book.title, book.getCommand());
            });

            id.then(page);
            help.then(id);
            command.then(help);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> create = literal("create");
            RequiredArgumentBuilder<ServerCommandSource, String> name = argument("name", StringArgumentType.word());

            name.executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            Pair<BlockPos, BlockPos> selectedPositions = ClaimManager.INSTANCE.stickPositions.get(player);
                if (selectedPositions == null) {
                    context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select them with a stick.").formatted(Formatting.RED), false);
                } else if (selectedPositions.getLeft() == null) {
                    context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select position #1(Right Click) with a stick.").formatted(Formatting.RED), false);
                } else if (selectedPositions.getRight() == null) {
                    context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select position #2(Left Click) with a stick.").formatted(Formatting.RED), false);
                } else {
                    String cname = StringArgumentType.getString(context, "name");
                    if (createClaim(cname, context.getSource(), selectedPositions.getLeft(), selectedPositions.getRight(), false, null) > 0) {
                        ClaimManager.INSTANCE.stickPositions.remove(player);
                    }
                }
                return 0;
            });

            ArgumentBuilder<ServerCommandSource, ?> min = argument("min", BlockPosArgumentType.blockPos());
            RequiredArgumentBuilder<ServerCommandSource, PosArgument> max = argument("max", BlockPosArgumentType.blockPos());
            max.executes(context -> createClaim(
                    StringArgumentType.getString(context, "name"),
                    context.getSource(),
                    BlockPosArgumentType.getBlockPos(context, "min"),
                    BlockPosArgumentType.getBlockPos(context, "max"),
                    false,
                    null
            ));
            min.then(max);
            name.then(min);
            create.then(name);
            command.then(create);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> rename = literal("rename");
            RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = argument("claim", StringArgumentType.word())
                    .suggests(CLAIM_PROVIDER);
            RequiredArgumentBuilder<ServerCommandSource, String> nameArgument = argument("name", StringArgumentType.word());
            nameArgument.executes((context) -> rename(context, false));
            claimArgument.then(nameArgument);
            rename.then(claimArgument);
            command.then(rename);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> trusted = literal("trusted");
            RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = getClaimArgument();
            trusted.executes((context)-> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                Claim claim = ClaimManager.INSTANCE.getClaimAt(player.getSenseCenterPos(), player.dimension);
                if (claim == null) {
                    context.getSource().sendError(new LiteralText("That claim does not exist"));
                    return -1;
                }
                return showTrustedList(context, claim, false);
            });

            claimArgument.executes((context) -> {
                Claim claim = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                if (claim == null) {
                    context.getSource().sendError(new LiteralText("That claim does not exist"));
                    return -1;
                }
                return showTrustedList(context, claim, false);
            });
            trusted.then(claimArgument);
            command.then(trusted);
            dispatcher.register(trusted);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> stick = literal("stick");
            stick.executes(context -> {
                Pair<BlockPos, BlockPos> posPair = ClaimManager.INSTANCE.stickPositions.get(context.getSource().getPlayer());
                context.getSource().sendFeedback(new LiteralText(posPair == null ? "You can now use a stick to create claims. Run this command again to disable" : "Claim stick disabled. Run this command again to enable").formatted(Formatting.DARK_PURPLE), false);
                if (posPair == null) {
                    ClaimManager.INSTANCE.stickPositions.put(context.getSource().getPlayer(), new Pair<>(null, null));
                } else {
                    ClaimManager.INSTANCE.stickPositions.remove(context.getSource().getPlayer());
                }
                return 0;
            });
            command.then(stick);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> show = literal("show");
            show.executes(context -> showClaim(context.getSource(), ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().dimension.getType()), false));
            RequiredArgumentBuilder<ServerCommandSource, String> name = argument("name", StringArgumentType.word());
            name.suggests(CLAIM_PROVIDER);
            name.executes(context -> showClaim(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "name")), false));
            show.then(name);
            command.then(show);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> hide = literal("hide");
            hide.executes(context -> showClaim(context.getSource(), null, true));
            command.then(hide);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> check = literal("blocks");
            RequiredArgumentBuilder<ServerCommandSource, EntitySelector> other = argument("player", EntityArgumentType.player());
            other.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.checkothers", 2));
            other.executes(ctx -> checkPlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player").getGameProfile().getId()));
            check.then(other);
            check.executes(ctx -> checkPlayer(ctx.getSource(), ctx.getSource().getPlayer().getGameProfile().getId()));
            command.then(check);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> delete = literal("remove");
            RequiredArgumentBuilder<ServerCommandSource, String> claim = getClaimArgument();
            claim.suggests(CLAIM_PROVIDER);
            LiteralArgumentBuilder<ServerCommandSource> confirm = literal("confirm");
            confirm.executes(context -> delete(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), false));
            claim.executes(context -> requestDelete(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), false));
            claim.then(confirm);
            delete.then(claim);
            delete.executes(context -> requestDelete(context.getSource(), ClaimManager.INSTANCE.getClaimAt(new BlockPos(context.getSource().getPosition()), context.getSource().getWorld().getDimension().getType()), false));
            command.then(delete);
        }
        {
            {
                LiteralArgumentBuilder<ServerCommandSource> expand = literal("expand");
                RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("distance", IntegerArgumentType.integer(1, Integer.MAX_VALUE));
                RequiredArgumentBuilder<ServerCommandSource, String> direction = argument("direction", StringArgumentType.word());
                direction.suggests(DIRECTION_SUGGESTION_BUILDER);

                direction.executes(context -> expand(
                        ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                        IntegerArgumentType.getInteger(context, "distance"),
                        directionByName(StringArgumentType.getString(context, "direction")),
                        context.getSource(),
                        false
                ));

                amount.executes(context -> expand(
                        ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                        IntegerArgumentType.getInteger(context, "distance"),
                        Direction.getEntityFacingOrder(context.getSource().getPlayer())[0],
                        context.getSource(),
                        false
                ));

                amount.then(direction);
                expand.then(amount);
                command.then(expand);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> shrink = literal("shrink");
                RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("distance", IntegerArgumentType.integer(1, Integer.MAX_VALUE));
                RequiredArgumentBuilder<ServerCommandSource, String> direction = argument("direction", StringArgumentType.word());
                direction.suggests(DIRECTION_SUGGESTION_BUILDER);

                direction.executes(context -> expand(
                        ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                        -IntegerArgumentType.getInteger(context, "distance"),
                        directionByName(StringArgumentType.getString(context, "direction")),
                        context.getSource(),
                        false
                ));

                amount.executes(context -> expand(
                        ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                        -IntegerArgumentType.getInteger(context, "distance"),
                        Direction.getEntityFacingOrder(context.getSource().getPlayer())[0],
                        context.getSource(),
                        false
                ));

                amount.then(direction);
                shrink.then(amount);
                command.then(shrink);
            }
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> delete = literal("remove");
            RequiredArgumentBuilder<ServerCommandSource, String> claim = getClaimArgument();
            LiteralArgumentBuilder<ServerCommandSource> confirm = literal("confirm");
            confirm.executes(context -> delete(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), false));
            claim.executes(context -> requestDelete(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), false));
            claim.then(confirm);
            delete.then(claim);
            delete.executes(context -> requestDelete(context.getSource(), ClaimManager.INSTANCE.getClaimAt(new BlockPos(context.getSource().getPosition()), context.getSource().getWorld().getDimension().getType()), false));
            command.then(delete);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> transfer = literal("transfer");
            RequiredArgumentBuilder<ServerCommandSource, String> claim = argument("claim", StringArgumentType.word()).suggests(CLAIM_PROVIDER);
            RequiredArgumentBuilder<ServerCommandSource, EntitySelector> player = argument("player", EntityArgumentType.player());
            LiteralArgumentBuilder<ServerCommandSource> confirm = literal("confirm");
            confirm.executes(context -> {
                final String string = "-accept-";
                ServerPlayerEntity p = EntityArgumentType.getPlayer(context, "player");
                String input = StringArgumentType.getString(context, "claim");
                String claimName = input.replace(string, "");
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(claimName);
                if (claim1 == null) {
                    context.getSource().sendError(Messages.INVALID_CLAIM);
                    return -1;
                }

                if (input.startsWith(string)) {
                    return acceptTransfer(context.getSource());
                }

                return transfer(context.getSource(), claim1, p, false);
            });
            player.executes(context -> requestTransfer(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), EntityArgumentType.getPlayer(context, "player"), false));
            player.then(confirm);
            claim.then(player);
            transfer.then(claim);
            transfer.executes(context -> requestTransfer(context.getSource(), ClaimManager.INSTANCE.getClaimAt(new BlockPos(context.getSource().getPosition()), context.getSource().getWorld().getDimension().getType()), EntityArgumentType.getPlayer(context, "player"), false));
            command.then(transfer);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> info = literal("info");
            RequiredArgumentBuilder<ServerCommandSource, String> claim = getClaimArgument();
            info.executes(context -> info(
                    context.getSource(),
                    ClaimManager.INSTANCE.getClaimAt(new BlockPos(context.getSource().getPosition()), context.getSource().getWorld().getDimension().getType())
            ));
            claim.executes(context -> info(
                    context.getSource(),
                    ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"))
            ));
            info.then(claim);
            command.then(info);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> list = literal("list");
            RequiredArgumentBuilder<ServerCommandSource, String> player = argument("player", StringArgumentType.word());
            player.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.check_others", 2));
            player.suggests(PLAYERS_PROVIDER);
            list.executes(context -> list(context.getSource(), null));
            player.executes(context -> list(context.getSource(), StringArgumentType.getString(context, "player")));
            list.then(player);
            command.then(list);

            LiteralArgumentBuilder<ServerCommandSource> claims = literal("claims")
                    .executes(context -> list(context.getSource(), null))
                    .then(player);
            dispatcher.register(claims);
        }
        {
            //TODO: FIX THIS
//            LiteralArgumentBuilder<ServerCommandSource> listall = literal("listall");
//            RequiredArgumentBuilder<ServerCommandSource, Integer> page = argument("page", IntegerArgumentType.integer(1));
//
//            listall.executes(context -> listAll(context.getSource(), 1));
//            page.executes(context -> listAll(context.getSource(), IntegerArgumentType.getInteger(context, "page")));
//            listall.then(page);
//            command.then(listall);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> trust = literal("trust");
            RequiredArgumentBuilder<ServerCommandSource, EntitySelector> playerArgument = argument("player", EntityArgumentType.player());
            RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = getClaimArgument();

            playerArgument.executes((context -> executeTrust(context, EntityArgumentType.getPlayer(context, "player"), true, null)));
            claimArgument.executes((context -> executeTrust(context, EntityArgumentType.getPlayer(context, "player"), true, StringArgumentType.getString(context, "claim"))));

            playerArgument.then(claimArgument);
            trust.then(playerArgument);
            command.then(trust);
            dispatcher.register(trust);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> distrust = literal("distrust");
            RequiredArgumentBuilder<ServerCommandSource, EntitySelector> playerArgument = argument("player", EntityArgumentType.player());
            RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = getClaimArgument();

            playerArgument.executes((context -> executeTrust(context, EntityArgumentType.getPlayer(context, "player"), false, null)));
            claimArgument.executes((context -> executeTrust(context, EntityArgumentType.getPlayer(context, "player"), false, StringArgumentType.getString(context, "claim"))));

            playerArgument.then(claimArgument);
            distrust.then(playerArgument);
            command.then(distrust);
            dispatcher.register(distrust);
        }

        createExceptionCommand(command, false);

        {
            LiteralArgumentBuilder<ServerCommandSource> admin = literal("admin");
            admin.requires(PERMISSION_CHECK_ADMIN);
            {
                LiteralArgumentBuilder<ServerCommandSource> add = literal("addBlocks");
                add.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.modify_balance", 2));
                RequiredArgumentBuilder<ServerCommandSource, EntitySelector> player = argument("player", EntityArgumentType.players());
                RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("amount", IntegerArgumentType.integer());
                amount.executes(context -> {
                    ClaimManager.INSTANCE.addClaimBlocks(EntityArgumentType.getPlayers(context, "player"), IntegerArgumentType.getInteger(context, "amount"));
                    context.getSource().sendFeedback(new LiteralText("Gave " + IntegerArgumentType.getInteger(context, "amount") + " claim blocks").formatted(Formatting.GREEN), true);
                    return 0;
                });
                player.then(amount);
                add.then(player);
                admin.then(add);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> set = literal("setOwner");
                RequiredArgumentBuilder<ServerCommandSource, GameProfileArgumentType.GameProfileArgument> newOwner = argument("newOwner", GameProfileArgumentType.gameProfile());
                RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = getClaimArgument();

                newOwner.executes((context) -> {
                    Claim claim = ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getPlayer().dimension);
                    if (claim == null) {
                        context.getSource().sendError(Messages.INVALID_CLAIM);
                        return -1;
                    }

                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "newOwner");

                    if (profiles.size() > 1) {
                        context.getSource().sendError(Messages.TOO_MANY_SELECTIONS);
                        return -1;
                    }
                    return setOwner(context.getSource(), claim, profiles.iterator().next());
                });

                claimArgument.executes((context) -> {
                    Claim claim = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (claim == null) {
                        context.getSource().sendError(Messages.INVALID_CLAIM);
                        return -1;
                    }

                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "newOwner");

                    if (profiles.size() > 1) {
                        context.getSource().sendError(Messages.TOO_MANY_SELECTIONS);
                        return -1;
                    }
                    return setOwner(context.getSource(), claim, profiles.iterator().next());
                });

                newOwner.then(claimArgument);
                set.then(newOwner);
                admin.then(set);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> set = literal("setOwnerName");
                RequiredArgumentBuilder<ServerCommandSource, String> nameArgument = argument("newName", StringArgumentType.word());
                RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = getClaimArgument();

                nameArgument.executes((context) -> {
                   Claim claim = ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getPlayer().dimension);
                   if (claim == null) {
                       context.getSource().sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
                       return -1;
                   }
                    return setOwnerName(context.getSource(), claim, StringArgumentType.getString(context, "newName"));
                });

                claimArgument.executes((context) -> {
                    Claim claim = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (claim == null) {
                        context.getSource().sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
                        return -1;
                    }
                    return setOwnerName(context.getSource(), claim, StringArgumentType.getString(context, "newName"));
                });

                nameArgument.then(claimArgument);
                set.then(nameArgument);
                admin.then(set);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> rename = literal("rename");
                RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = argument("claim", StringArgumentType.word())
                        .suggests(CLAIM_PROVIDER);
                RequiredArgumentBuilder<ServerCommandSource, String> nameArgument = argument("name", StringArgumentType.word());
                nameArgument.executes((context) -> rename(context, true));
                claimArgument.then(nameArgument);
                rename.then(claimArgument);
                admin.then(rename);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> remove = literal("removeBlocks");
                remove.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.modify_balance", 2));
                RequiredArgumentBuilder<ServerCommandSource, EntitySelector> player = argument("player", EntityArgumentType.players());
                RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("amount", IntegerArgumentType.integer());
                amount.executes(context -> {
                    ClaimManager.INSTANCE.addClaimBlocks(EntityArgumentType.getPlayers(context, "player"), -IntegerArgumentType.getInteger(context, "amount"));
                    context.getSource().sendFeedback(new LiteralText("Took " + IntegerArgumentType.getInteger(context, "amount") + " claim blocks").formatted(Formatting.GREEN), true);
                    return 0;
                });
                player.then(amount);
                remove.then(player);
                admin.then(remove);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> set = literal("setBlocks");
                set.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.modify_balance", 2));
                RequiredArgumentBuilder<ServerCommandSource, EntitySelector> player = argument("player", EntityArgumentType.players());
                RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("amount", IntegerArgumentType.integer());
                amount.executes(context -> {
                    ClaimManager.INSTANCE.setClaimBlocks(EntityArgumentType.getPlayers(context, "player"), IntegerArgumentType.getInteger(context, "amount"));
                    context.getSource().sendFeedback(new LiteralText("Set claim block amount to " + IntegerArgumentType.getInteger(context, "amount")).formatted(Formatting.GREEN), true);
                    return 0;
                });
                player.then(amount);
                set.then(player);
                admin.then(set);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> delete = literal("remove");
                delete.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.modify", 4));
                RequiredArgumentBuilder<ServerCommandSource, String> claim = getClaimArgument();
                LiteralArgumentBuilder<ServerCommandSource> confirm = literal("confirm");
                confirm.executes(context -> delete(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), true));
                claim.executes(context -> requestDelete(context.getSource(), ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim")), true));
                claim.then(confirm);
                delete.then(claim);
                delete.executes(context -> requestDelete(context.getSource(), ClaimManager.INSTANCE.getClaimAt(new BlockPos(context.getSource().getPosition()), context.getSource().getWorld().getDimension().getType()), true));
                admin.then(delete);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> create = literal("create");
                create.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.infinite_claim", 4));
                ArgumentBuilder<ServerCommandSource, ?> name = argument("name", StringArgumentType.word());
                ArgumentBuilder<ServerCommandSource, ?> customOwner = argument("customOwnerName", StringArgumentType.word());
                ArgumentBuilder<ServerCommandSource, ?> min = argument("min", BlockPosArgumentType.blockPos());
                RequiredArgumentBuilder<ServerCommandSource, PosArgument> max = argument("max", BlockPosArgumentType.blockPos());
                max.executes(context -> createClaim(
                        StringArgumentType.getString(context, "name"),
                        context.getSource(),
                        BlockPosArgumentType.getBlockPos(context, "min"),
                        BlockPosArgumentType.getBlockPos(context, "max"),
                        true,
                        null
                ));
                name.executes(context ->  {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    Pair<BlockPos, BlockPos> selectedPositions = ClaimManager.INSTANCE.stickPositions.get(player);
                    if (selectedPositions == null) {
                        context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select them with a stick.").formatted(Formatting.RED), false);
                    } else if (selectedPositions.getLeft() == null) {
                        context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select position #1(Right Click) with a stick.").formatted(Formatting.RED), false);
                    } else if (selectedPositions.getRight() == null) {
                        context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select position #2(Left Click) with a stick.").formatted(Formatting.RED), false);
                    } else {
                        String cname = StringArgumentType.getString(context, "name");
                        if (createClaim(cname, context.getSource(), selectedPositions.getLeft(), selectedPositions.getRight(), true, null) > 0) {
                            ClaimManager.INSTANCE.stickPositions.remove(player);
                        }
                    }
                    return 0;
                });
                customOwner.executes(context ->  {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    Pair<BlockPos, BlockPos> selectedPositions = ClaimManager.INSTANCE.stickPositions.get(player);
                    if (selectedPositions == null) {
                        context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select them with a stick.").formatted(Formatting.RED), false);
                    } else if (selectedPositions.getLeft() == null) {
                        context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select position #1(Right Click) with a stick.").formatted(Formatting.RED), false);
                    } else if (selectedPositions.getRight() == null) {
                        context.getSource().sendFeedback(new LiteralText("You need to specify block positions or select position #2(Left Click) with a stick.").formatted(Formatting.RED), false);
                    } else {
                        String cname = StringArgumentType.getString(context, "name");
                        if (createClaim(cname, context.getSource(), selectedPositions.getLeft(), selectedPositions.getRight(), true, StringArgumentType.getString(context, "customOwnerName")) > 0) {
                            ClaimManager.INSTANCE.stickPositions.remove(player);
                        }
                    }
                    return 0;
                });
                min.then(max);
                name.then(customOwner);
                name.then(min);
                create.then(name);
                admin.then(create);
            }
            {
                LiteralArgumentBuilder<ServerCommandSource> ignore = literal("ignoreClaims");
                ignore.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.ignore_claims", 4));
                ignore.executes(context -> {
                    UUID id = context.getSource().getPlayer().getGameProfile().getId();
                    boolean isIgnoring = ClaimManager.INSTANCE.ignoringClaims.contains(id);
                    if (isIgnoring) ClaimManager.INSTANCE.ignoringClaims.remove(id);
                    else ClaimManager.INSTANCE.ignoringClaims.add(id);
                    context.getSource().sendFeedback(new LiteralText("You are " + (isIgnoring ? "no longer" : "now") + " ignoring claims.").formatted(Formatting.GREEN), false);
                    return 0;
                });
                admin.then(ignore);
            }
            {
                {
                    LiteralArgumentBuilder<ServerCommandSource> expand = literal("expand");
                    expand.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.infinite_claim", 4) && Thimble.hasPermissionOrOp(source, "itsmine.admin.modify", 4));
                    RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("distance", IntegerArgumentType.integer(1, Integer.MAX_VALUE));
                    RequiredArgumentBuilder<ServerCommandSource, String> direction = argument("direction", StringArgumentType.word());
                    direction.suggests(DIRECTION_SUGGESTION_BUILDER);

                    direction.executes(context -> expand(
                            ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                            IntegerArgumentType.getInteger(context, "distance"),
                            directionByName(StringArgumentType.getString(context, "direction")),
                            context.getSource(),
                            true
                    ));

                    amount.executes(context -> expand(
                            ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                            IntegerArgumentType.getInteger(context, "distance"),
                            Direction.getEntityFacingOrder(context.getSource().getPlayer())[0],
                            context.getSource(),
                            true
                    ));

                    amount.then(direction);
                    expand.then(amount);
                    admin.then(expand);
                }
                {
                    LiteralArgumentBuilder<ServerCommandSource> shrink = literal("shrink");
                    shrink.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.infinite_claim", 4) && Thimble.hasPermissionOrOp(source, "itsmine.admin.modify", 4));
                    RequiredArgumentBuilder<ServerCommandSource, Integer> amount = argument("distance", IntegerArgumentType.integer(1, Integer.MAX_VALUE));
                    RequiredArgumentBuilder<ServerCommandSource, String> direction = argument("direction", StringArgumentType.word());
                    direction.suggests(DIRECTION_SUGGESTION_BUILDER);

                    direction.executes(context -> expand(
                            ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                            -IntegerArgumentType.getInteger(context, "distance"),
                            directionByName(StringArgumentType.getString(context, "direction")),
                            context.getSource(),
                            true
                    ));

                    amount.executes(context -> expand(
                            ClaimManager.INSTANCE.getClaimAt(context.getSource().getPlayer().getSenseCenterPos(), context.getSource().getWorld().getDimension().getType()),
                            -IntegerArgumentType.getInteger(context, "distance"),
                            Direction.getEntityFacingOrder(context.getSource().getPlayer())[0],
                            context.getSource(),
                            true
                    ));

                    amount.then(direction);
                    shrink.then(amount);
                    admin.then(shrink);
                }
            }
            createExceptionCommand(admin, true);
            command.then(admin);
        }
        dispatcher.register(command);
    }

    private static int sendPage(ServerCommandSource source, Text[] text, int page, String title, String command) {
        int prevPage = page - 2;
        int thisPage = page - 1;
        int nextPage = page + 1;
        final String SEPARATOR = "-----------------------------------------------------";
        Text header =  new LiteralText("")
                .append(new LiteralText("- [ ").formatted(Formatting.GRAY))
                .append(new LiteralText(title).formatted(Formatting.GOLD))
                .append(" ] ")
                .append(SEPARATOR.substring(ChatColor.removeAlternateColorCodes('&', title).length() + 4))
                .formatted(Formatting.GRAY);

        Text button_prev = new LiteralText("")
                .append(new LiteralText("<-").formatted(Formatting.WHITE, Formatting.BOLD))
                .append(" ").append(new LiteralText("Prev").formatted(Formatting.GOLD))
                .styled((style) -> {
                    style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText((prevPage >= 0) ? "<<<" : "|<").formatted(Formatting.GRAY)));
                    if (prevPage >= 0)
                        style.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command.replace("%page%",  String.valueOf(page - 1))));
                });

        Text button_next = new LiteralText("")
                .append(new LiteralText("Next").formatted(Formatting.GOLD))
                .append(" ").append(new LiteralText("->").formatted(Formatting.WHITE, Formatting.BOLD)).append(" ")
                .styled((style) -> {
                    style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText((nextPage <= text.length) ? ">>>" : ">|").formatted(Formatting.GRAY)));
                    if (nextPage <= text.length)
                        style.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command.replace("%page%",  String.valueOf(nextPage))));
                });

        Text buttons = new LiteralText("")
                .append(new LiteralText("[ ").formatted(Formatting.GRAY))
                .append(button_prev)
                .append(" ")
                .append(
                        new LiteralText(String.valueOf(page)).formatted(Formatting.GREEN)
                        .append(new LiteralText("/").formatted(Formatting.GRAY))
                        .append(new LiteralText(String.valueOf(text.length)).formatted(Formatting.GREEN))
                )
                .append(" ")
                .append(button_next)
                .append(new LiteralText("] ").formatted(Formatting.GRAY));

        Text footer = new LiteralText("- ")
                .formatted(Formatting.GRAY)
                .append(buttons).append(new LiteralText(" ------------------------------").formatted(Formatting.GRAY));

        header.append("\n").append(text[thisPage]).append("\n").append(footer);
        source.sendFeedback(header, false);
        return 1;
    }

    private static void createExceptionCommand(LiteralArgumentBuilder<ServerCommandSource> command, boolean admin) {
        {
            LiteralArgumentBuilder<ServerCommandSource> settings = literal("settings");
            RequiredArgumentBuilder<ServerCommandSource, String> claim = getClaimArgument();

            if (!admin) {
                settings.executes((context) -> sendPage(context.getSource(), Messages.SETTINGS_AND_PERMISSIONS, 1, "Claim Permissions and Settings", "/claim help perms_and_settings %page%"));

                claim.executes((context) -> {
                    Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (claim1 == null) {
                        context.getSource().sendError(Messages.INVALID_CLAIM);
                        return -1;
                    }
                    return querySettings(context.getSource(), claim1);
                });
            }

            RequiredArgumentBuilder<ServerCommandSource, String> id = argument("setting", StringArgumentType.word()).suggests(SETTINGS_PROVIDER);
            RequiredArgumentBuilder<ServerCommandSource, Boolean> set = argument("set", BoolArgumentType.bool());

            id.executes((context) -> executeSetting(context.getSource(), StringArgumentType.getString(context, "setting"), StringArgumentType.getString(context, "claim"), true, false, admin));
            set.executes((context) -> executeSetting(context.getSource(), StringArgumentType.getString(context, "setting"), null, false, BoolArgumentType.getBool(context, "set"), admin));

            id.then(set);
            claim.then(id);
            settings.then(claim);
            command.then(settings);
        }

        LiteralArgumentBuilder<ServerCommandSource> exceptions = literal("permissions");
        if (admin) exceptions.requires(source -> Thimble.hasPermissionOrOp(source, "itsmine.admin.modify_permissions", 2));
        RequiredArgumentBuilder<ServerCommandSource, String> claim = getClaimArgument();
        if (!admin) {
            exceptions.executes((context) -> sendPage(context.getSource(), Messages.SETTINGS_AND_PERMISSIONS, 1, "Claim Permissions and Settings", "/claim help perms_and_settings %page%"));

            claim.executes((context) -> {
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                if (claim1 == null) {
                    context.getSource().sendError(Messages.INVALID_CLAIM);
                    return -1;
                }
                return showTrustedList(context, claim1, false);
            });
        }

        if (!admin) {
            claim.executes((context) -> {
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                if (claim1 == null) {
                    context.getSource().sendError(new LiteralText("That claim does not exist"));
                    return -1;
                }
                return showTrustedList(context, claim1, true);
            });
        }

        LiteralArgumentBuilder<ServerCommandSource> playerLiteral = literal("player");
        {
            RequiredArgumentBuilder<ServerCommandSource, EntitySelector> player = argument("player", EntityArgumentType.player());
            LiteralArgumentBuilder<ServerCommandSource> remove = literal("remove");
            remove.executes(context -> {
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                if (verifyPermission(claim1, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
                    ServerPlayerEntity player1 = EntityArgumentType.getPlayer(context, "player");
                    claim1.permissionManager.resetPermissions(player1.getGameProfile().getId());
                    context.getSource().sendFeedback(new LiteralText(player1.getGameProfile().getName() + " no longer has an exception in the claim").formatted(Formatting.YELLOW), false);
                }
                return 0;
            });
            player.then(remove);
            LiteralArgumentBuilder<ServerCommandSource> all = literal("*");
            RequiredArgumentBuilder<ServerCommandSource, Boolean> allstate = argument("allow", BoolArgumentType.bool());
            allstate.executes(context -> {
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                ServerPlayerEntity player1 = EntityArgumentType.getPlayer(context, "player");
                validateClaim(claim1);
                return setTrust(context, claim1, player1, BoolArgumentType.getBool(context, "allow"), admin);
            });
            all.then(allstate);
            player.then(all);
            for (Claim.Permission value : Claim.Permission.values()) {
                LiteralArgumentBuilder<ServerCommandSource> permNode = literal(value.id);
                RequiredArgumentBuilder<ServerCommandSource, Boolean> allow = argument("allow", BoolArgumentType.bool());
                allow.executes(context -> {
                    Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (verifyPermission(claim1, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
                        ServerPlayerEntity player1 = EntityArgumentType.getPlayer(context, "player");
                        boolean permission = BoolArgumentType.getBool(context, "allow");
                        modifyException(claim1, player1, value, permission);
                        context.getSource().sendFeedback(new LiteralText(player1.getGameProfile().getName() + (permission ? " now" : " no longer") + " has the permission " + value.name).formatted(Formatting.YELLOW), false);
                    }
                    return 0;
                });
                permNode.executes(context -> {
                    Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (verifyPermission(claim1, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
                        ServerPlayerEntity player1 = EntityArgumentType.getPlayer(context, "player");
                        boolean permission = hasPermission(claim1, player1, value);
                        context.getSource().sendFeedback(new LiteralText(player1.getGameProfile().getName() + (permission ? " has" : " does not have") + " the permission " + value.name).formatted(Formatting.YELLOW), false);
                    }
                    return 0;
                });
                permNode.then(allow);
                player.then(permNode);
            }
            playerLiteral.then(player);
        }
        LiteralArgumentBuilder<ServerCommandSource> groupLiteral = literal("group");
        groupLiteral.requires(sender -> Thimble.hasPermissionOrOp(sender, "itsmine.specify_groups", 2));
        {
            RequiredArgumentBuilder<ServerCommandSource, String> group = argument("group", StringArgumentType.word());
            group.suggests(PermissionCommand.SUGGESTIONS_BUILDER);
            LiteralArgumentBuilder<ServerCommandSource> remove = literal("remove");
            remove.executes(context -> {
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                if (verifyPermission(claim1, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
                    String group1 = StringArgumentType.getString(context, "group");
                    verifyGroup(group1);
                    claim1.permissionManager.resetPermissions(group1);
                    context.getSource().sendFeedback(new LiteralText("Members of " + group1 + " no longer have that exception in the claim").formatted(Formatting.YELLOW), false);
                }
                return 0;
            });
            group.then(remove);
            for (Claim.Permission value : Claim.Permission.values()) {
                LiteralArgumentBuilder<ServerCommandSource> permNode = literal(value.id);
                RequiredArgumentBuilder<ServerCommandSource, Boolean> allow = argument("allow", BoolArgumentType.bool());
                allow.executes(context -> {
                    Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (verifyPermission(claim1, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
                        String group1 = StringArgumentType.getString(context, "group");
                        verifyGroup(group1);
                        boolean permission = BoolArgumentType.getBool(context, "allow");
                        modifyException(claim1, group1, value, permission);
                        context.getSource().sendFeedback(new LiteralText("Members of " + group1 + (permission ? " now" : " no longer") + " has the permission " + value.name).formatted(Formatting.YELLOW), false);
                    }
                    return 0;
                });
                permNode.executes(context -> {
                    Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                    if (verifyPermission(claim1, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
                        String group1 = StringArgumentType.getString(context, "group");
                        verifyGroup(group1);
                        boolean permission = hasPermission(claim1, group1, value);
                        context.getSource().sendFeedback(new LiteralText("Members of " + group1 + (permission ? " now" : " do not") + " have the permission " + value.name).formatted(Formatting.YELLOW), false);
                    }
                    return 0;
                });
                permNode.then(allow);
                group.then(permNode);
            }
            groupLiteral.then(group);
        }
        {
            LiteralArgumentBuilder<ServerCommandSource> message = literal("message");
            RequiredArgumentBuilder<ServerCommandSource, String> claimArgument = getClaimArgument();
            RequiredArgumentBuilder<ServerCommandSource, String> messageEvent = argument("messageEvent", StringArgumentType.word())
                    .suggests(MESSAGE_EVENTS_PROVIDER);
            RequiredArgumentBuilder<ServerCommandSource, String> messageArgument = argument("message", StringArgumentType.greedyString())
                    .suggests(EVENT_MESSAGE_PROVIDER);

            messageArgument.executes(context -> {
                Claim claim1 = ClaimManager.INSTANCE.claimsByName.get(StringArgumentType.getString(context, "claim"));
                if (verifyPermission(claim1, Claim.Permission.MODIFY_PROPERTIES, context, admin)) {
                    Claim.Event event = Claim.Event.getById(StringArgumentType.getString(context, "messageEvent"));

                    if (event == null) {
                        context.getSource().sendError(Messages.INVALID_MESSAGE_EVENT);
                        return -1;
                    }

                    return setEventMessage(context.getSource(), claim1, event, StringArgumentType.getString(context, "message"));
                }

                return -1;
            });

            messageEvent.then(messageArgument);
            claimArgument.then(messageEvent);
            message.then(claimArgument);
            command.then(message);
        }

        claim.then(playerLiteral);
        claim.then(groupLiteral);
        exceptions.then(claim);
        command.then(exceptions);
    }
    private static boolean verifyPermission(Claim claim, Claim.Permission permission, CommandContext<ServerCommandSource> context, boolean admin) throws CommandSyntaxException {
        if (verifyExists(claim, context)) {
            if (claim.permissionManager.hasPermission(context.getSource().getPlayer().getGameProfile().getId(), permission)) {
                return true;
            } else {
                context.getSource().sendFeedback(new LiteralText(admin ? "You are modifying a claim using admin privileges" : "You cannot modify exceptions for this claim").formatted(admin ? Formatting.DARK_RED : Formatting.RED), false);
                return admin;
            }
        } else {
            return false;
        }
    }
    private static boolean verifyExists(Claim claim, CommandContext<ServerCommandSource> context) {
        if (claim == null) {
            context.getSource().sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return false;
        } else {
            return true;
        }
    }
    private static void verifyGroup(String permission) {
        if (Thimble.PERMISSIONS.permissionExists(permission)) {
            throw new CommandException(new LiteralText("Nonexistant permission group").formatted(Formatting.RED));
        }
    }


    private static int showClaim(ServerCommandSource source, Claim claim, boolean reset) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        if (!reset && ((ClaimShower)player).getShownClaim() != null && !(!Config.claims2d &&((ClaimShower)player).getShownClaim() != claim)) showClaim(source, ((ClaimShower)player).getShownClaim(), true);
        if (reset && ((ClaimShower)player).getShownClaim() != null) claim = ((ClaimShower)player).getShownClaim();
        if (claim != null) {
            if (!claim.dimension.equals(source.getWorld().getDimension().getType())) {
                if (claim == ((ClaimShower)player).getShownClaim()) ((ClaimShower)player).setShownClaim(null); // just so we dont have extra packets on this
                source.sendFeedback(new LiteralText("That claim is not in this dimension").formatted(Formatting.RED), false);
                return 0;
            }
            source.sendFeedback(new LiteralText((!reset ? "Showing" : "Hiding") + " claim: " + claim.name).formatted(Formatting.GREEN), false);
            silentHideShow(player, claim, reset, true);

        } else {
            source.sendFeedback(new LiteralText("That is not a valid claim").formatted(Formatting.RED), false);
        }
        return 0;
    }
    private static void silentHideShow(ServerPlayerEntity player, Claim claim, boolean hide, boolean updateStatus) {
        BlockState block = hide ? null : Blocks.SEA_LANTERN.getDefaultState();
        int showRange = 5;
        int closeShowRange = 8;
        BlockPos pos = hide ? ((ClaimShower)player).getLastShowPos() : player.getSenseCenterPos();
        ((ClaimShower)player).setLastShowPos(pos);
        for (int x = claim.min.getX(); x <= claim.min.getX() + showRange; x++) {
            sendBlockPacket(player, new BlockPos(x, claim.min.getY(), claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, claim.max.getY(), claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, claim.min.getY(), claim.max.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, claim.max.getY(), claim.max.getZ()), block);
        }
        for (int x = claim.max.getX() - showRange; x <= claim.max.getX(); x++) {
            sendBlockPacket(player, new BlockPos(x, claim.min.getY(), claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, claim.max.getY(), claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, claim.min.getY(), claim.max.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, claim.max.getY(), claim.max.getZ()), block);
        }
        if (claim.includesPosition(pos)) for (int x = pos.getX() - closeShowRange; x <= pos.getX() + closeShowRange; x++) {
            if (x < claim.min.getX() || x > claim.max.getX()) continue;
            sendBlockPacket(player, new BlockPos(x, pos.getY(), claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(x, pos.getY(), claim.max.getZ()), block);
        }
        for (int y = claim.min.getY(); y <= claim.min.getY() + showRange; y++) {
            sendBlockPacket(player, new BlockPos(claim.min.getX(), y, claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), y, claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.min.getX(), y, claim.max.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), y, claim.max.getZ()), block);
        }
        for (int y = claim.max.getY() - showRange; y <= claim.max.getY(); y++) {
            sendBlockPacket(player, new BlockPos(claim.min.getX(), y, claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), y, claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.min.getX(), y, claim.max.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), y, claim.max.getZ()), block);
        }
        if (claim.includesPosition(pos)) for (int y = pos.getY() - closeShowRange; y <= pos.getY() + closeShowRange; y++) {
            if (y < claim.min.getY() || y > claim.max.getY()) continue;
            sendBlockPacket(player, new BlockPos(pos.getX(), y, claim.min.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), y, pos.getZ()), block);
            sendBlockPacket(player, new BlockPos(claim.min.getX(), y, pos.getZ()), block);
            sendBlockPacket(player, new BlockPos(pos.getX(), y, claim.max.getZ()), block);
        }
        for (int z = claim.min.getZ(); z <= claim.min.getZ() + showRange; z++) {
            sendBlockPacket(player, new BlockPos(claim.min.getX(), claim.min.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), claim.min.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.min.getX(), claim.max.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), claim.max.getY(), z), block);
        }
        for (int z = claim.max.getZ() - showRange; z <= claim.max.getZ(); z++) {
            sendBlockPacket(player, new BlockPos(claim.min.getX(), claim.min.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), claim.min.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.min.getX(), claim.max.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), claim.max.getY(), z), block);
        }
        if (claim.includesPosition(pos)) for (int z = pos.getZ() - closeShowRange; z <= pos.getZ() + closeShowRange; z++) {
            if (z < claim.min.getZ() || z > claim.max.getZ()) continue;
            sendBlockPacket(player, new BlockPos(claim.min.getX(), pos.getY(), z), block);
            sendBlockPacket(player, new BlockPos(claim.max.getX(), pos.getY(), z), block);
        }
        if (updateStatus) {
            if (!hide) ((ClaimShower) player).setShownClaim(claim);
            else ((ClaimShower) player).setShownClaim(null);
        }
    }
    private static void sendBlockPacket(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        BlockUpdateS2CPacket packet =  new BlockUpdateS2CPacket(player.world, pos);
        if (state != null) ((BlockUpdatePacketMixin)packet).setState(state);
        player.networkHandler.sendPacket(packet);
    }
    private static int createClaim(String name, ServerCommandSource owner, BlockPos posA, BlockPos posB, boolean admin, @Nullable String cOwnerName) throws CommandSyntaxException {
        UUID ownerID = owner.getPlayer().getGameProfile().getId();
        int x, y = 0, z, mx, my = 255, mz;
        if (posA.getX() > posB.getX()) {
            x = posB.getX();
            mx = posA.getX();
        } else {
            x =  posA.getX();
            mx = posB.getX();
        }
        if (!Config.claims2d) {
            if (posA.getY() > posB.getY()) {
                y = posB.getY();
                my = posA.getY();
            } else {
                y = posA.getY();
                my = posB.getY();
            }
        }
        if (posA.getZ() > posB.getZ()) {
            z = posB.getZ();
            mz = posA.getZ();
        } else {
            z =  posA.getZ();
            mz = posB.getZ();
        }
        BlockPos min = new BlockPos(x, y, z);
        BlockPos max = new BlockPos(mx, my, mz);
        BlockPos sub = max.subtract(min);
        sub = sub.add(1, Config.claims2d ? 0 : 1,1);
        int subInt = sub.getX() * (Config.claims2d ? 1 : sub.getY()) * sub.getZ();

        Claim claim = new Claim(name, admin ? null : ownerID, min, max, owner.getWorld().getDimension().getType(), owner.getPlayer().getSenseCenterPos());
        if (cOwnerName != null) claim.customOwnerName = cOwnerName;
        claim.permissionManager.playerPermissions.put(ownerID, new Claim.InvertedPermissionMap());
        if (!ClaimManager.INSTANCE.claimsByName.containsKey(name)) {
            if (!ClaimManager.INSTANCE.wouldIntersect(claim)) {
                // works because only the first statement is evaluated if true
                if ((admin && Thimble.hasPermissionOrOp(owner, "itsmine.admin.infinite_blocks", 4)) || ClaimManager.INSTANCE.useClaimBlocks(ownerID, subInt)) {
                    ClaimManager.INSTANCE.addClaim(claim);
                    owner.sendFeedback(new LiteralText("").append(new LiteralText("Your claim was created").formatted(Formatting.GREEN)).append(new LiteralText("(Area: " + sub.getX() + "x" + sub.getY() + "x" + sub.getZ() + ")").setStyle(new Style()
                            .setColor(Formatting.GREEN).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(subInt + " blocks").formatted(Formatting.YELLOW))))), false);
                    checkPlayer(owner, owner.getPlayer().getGameProfile().getId());
                    showClaim(owner, claim, false);
                    if (admin)
                        owner.getMinecraftServer().sendMessage(new LiteralText(owner.getPlayer().getGameProfile().getName() + " Has created a new claim(" + claim.name + ") using the admin command."));
                    return 1;
                } else {
                    owner.sendFeedback(new LiteralText("You don't have enough claim blocks. You have " + ClaimManager.INSTANCE.getClaimBlocks(ownerID) + ", you need " + subInt + "(" + (subInt - ClaimManager.INSTANCE.getClaimBlocks(ownerID)) + " more)").formatted(Formatting.RED), false);
                }
            } else {
                owner.sendFeedback(new LiteralText("Your claim would overlap with another claim").formatted(Formatting.RED), false);
            }
        } else {
            owner.sendFeedback(new LiteralText("The name \"" + name + "\" is already taken.").formatted(Formatting.RED), false);
        }
        return 0;
    }
    private static int checkPlayer(ServerCommandSource ret, UUID player) throws CommandSyntaxException {
        int blocks = ClaimManager.INSTANCE.getClaimBlocks(player);
        ret.sendFeedback(new LiteralText((ret.getPlayer().getGameProfile().getId().equals(player) ? "You have " : "They have ") + blocks + " blocks left").setStyle(new Style()
                .setColor(Formatting.YELLOW)), false);
        return 0;
    }
    private static int requestDelete(ServerCommandSource sender, Claim claim, boolean admin) throws CommandSyntaxException {
        if (claim == null) {
            sender.sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return 0;
        }
        if (!claim.permissionManager.hasPermission(sender.getPlayer().getGameProfile().getId(), Claim.Permission.DELETE_CLAIM)) {
            if (admin && Thimble.hasPermissionOrOp(sender, "itsmine.admin.modify", 4)) {
                sender.sendFeedback(new LiteralText("WARNING: This is not your claim...").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            } else {
                sender.sendFeedback(new LiteralText("You cannot delete that claim").formatted(Formatting.RED), false);
                return 0;
            }
        }
        sender.sendFeedback(new LiteralText("").append(new LiteralText("Are you sure you want to delete the claim \"" + claim.name + "\"? ").formatted(Formatting.GOLD))
                .append(new LiteralText("[I'M SURE]").setStyle(new Style()
                        .setColor(Formatting.DARK_RED)
                        .setBold(true)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, (admin ? "/claim admin" : "/claim") + " destroy " + claim.name + " confirm")))), false);
        return 0;
    }
    private static int delete(ServerCommandSource sender, Claim claim, boolean admin) throws CommandSyntaxException {
        if (claim == null) {
            sender.sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return 0;
        }
        if (!claim.permissionManager.hasPermission(sender.getPlayer().getGameProfile().getId(), Claim.Permission.DELETE_CLAIM)) {
            if (admin && Thimble.hasPermissionOrOp(sender, "itsmine.admin.modify", 4)) {
                sender.sendFeedback(new LiteralText("Deleting a claim belonging to somebody else").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            } else {
                sender.sendFeedback(new LiteralText("You cannot delete that claim").formatted(Formatting.RED), false);
                return 0;
            }
        }
        ClaimManager.INSTANCE.releaseBlocksToOwner(claim);
        ClaimManager.INSTANCE.claimsByName.remove(claim.name);
        sender.getWorld().getPlayers().forEach(playerEntity -> {
            if (((ClaimShower)playerEntity).getShownClaim() != null && ((ClaimShower)playerEntity).getShownClaim().name.equals(claim.name)) silentHideShow(playerEntity, claim, true, true);
        });
        sender.sendFeedback(new LiteralText("Deleted the claim \"" + claim.name + "\"").formatted(Formatting.GREEN), !claim.permissionManager.hasPermission(sender.getPlayer().getGameProfile().getId(), Claim.Permission.DELETE_CLAIM));
        return 0;
    }
    private static int requestTransfer(ServerCommandSource sender, Claim claim, ServerPlayerEntity player, boolean admin) throws CommandSyntaxException {
        if (claim == null) {
            sender.sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return 0;
        }
        if (!claim.claimBlockOwner.equals(sender.getPlayer().getGameProfile().getId())) {
            if (admin && Thimble.hasPermissionOrOp(sender, "itsmine.admin.modify", 4)) {
                sender.sendFeedback(new LiteralText("WARNING: This is not your claim...").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            } else {
                sender.sendFeedback(new LiteralText("You cannot transfer ownership that claim").formatted(Formatting.RED), false);
                return 0;
            }
        }
        sender.sendFeedback(new LiteralText("").append(new LiteralText("Are you sure you want to transfer ownership of \"" + claim.name + "\" to " + player.getGameProfile().getName() + "? ").formatted(Formatting.GOLD))
                .append(new LiteralText("[YES]").setStyle(new Style()
                        .setColor(Formatting.DARK_RED)
                        .setBold(true)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, (admin ? "/claim admin" : "/claim") + " transfer " + claim.name + " " + player.getGameProfile().getName() + " confirm")))), false);
        return 0;
    }
    private static Map<UUID, String> pendingClaimTransfers = new HashMap<>();
    private static int transfer(ServerCommandSource sender, Claim claim, ServerPlayerEntity player, boolean admin) throws CommandSyntaxException {
        if (claim == null) {
            sender.sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return 0;
        }
        if (!claim.claimBlockOwner.equals(sender.getPlayer().getGameProfile().getId())) {
            if (admin && Thimble.hasPermissionOrOp(sender, "itsmine.admin.modify", 4)) {
                sender.sendFeedback(new LiteralText("Transfering ownership of a claim belonging to somebody else").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            } else {
                sender.sendFeedback(new LiteralText("You cannot transfer ownership that claim").formatted(Formatting.RED), false);
                return 0;
            }
        }

        GameProfile profile = sender.getWorld().getServer().getUserCache().getByUuid(claim.claimBlockOwner);
        sender.sendFeedback(new LiteralText("Transferring ownership of the claim \"" + claim.name + "\" to " + player.getGameProfile().getName() + " if they accept").formatted(Formatting.GREEN), claim.claimBlockOwner != player.getGameProfile().getId());
        player.sendMessage(new LiteralText("").append(new LiteralText("Do you want to accept ownership of the claim \"" + claim.name + "\" from " + profile == null ? "Not Present" : profile.getName() + "? ").formatted(Formatting.GOLD))
                .append(new LiteralText("[ACCEPT]").setStyle(new Style()
                        .setColor(Formatting.GREEN)
                        .setBold(true)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim transfer -accept-" + claim.name + " " + player.getEntityName() + " confirm")))));
        pendingClaimTransfers.put(player.getGameProfile().getId(), claim.name);
        return 0;
    }
    public static int acceptTransfer(ServerCommandSource sender) throws CommandSyntaxException {
        Claim claim = ClaimManager.INSTANCE.claimsByName.get(pendingClaimTransfers.get(sender.getPlayer().getGameProfile().getId()));
        if (claim == null) {
            sender.sendFeedback(new LiteralText("You have no pending claim transfers").formatted(Formatting.RED), false);
            return 0;
        }
        if (sender.getMinecraftServer().getPlayerManager().getPlayer(claim.claimBlockOwner) != null) {
            sender.getMinecraftServer().getPlayerManager().getPlayer(claim.claimBlockOwner).sendMessage(new LiteralText("").append(new LiteralText(sender.getPlayer().getGameProfile().getName() + " has taken ownership of the claim \"" + claim.name + "\"").formatted(Formatting.YELLOW)));
        }
        Claim.ClaimPermissionMap op = claim.permissionManager.playerPermissions.get(claim.claimBlockOwner);
        claim.permissionManager.playerPermissions.put(claim.claimBlockOwner, claim.permissionManager.playerPermissions.get(sender.getPlayer().getGameProfile().getId()));
        claim.permissionManager.playerPermissions.put(sender.getPlayer().getGameProfile().getId(), op);
        claim.claimBlockOwner = sender.getPlayer().getGameProfile().getId();
        return 0;
    }
    private static int modifyException(Claim claim, ServerPlayerEntity exception, Claim.Permission permission, boolean allowed) {
        claim.permissionManager.setPermission(exception.getGameProfile().getId(), permission, allowed);
        return 0;
    }
    private static int modifyException(Claim claim, String exception, Claim.Permission permission, boolean allowed) {
        claim.permissionManager.setPermission(exception, permission, allowed);
        return 0;
    }
    private static boolean hasPermission(Claim claim, ServerPlayerEntity exception, Claim.Permission permission) {
        return claim.permissionManager.hasPermission(exception.getGameProfile().getId(), permission);
    }
    private static boolean hasPermission(Claim claim, String exception, Claim.Permission permission) {
        return claim.permissionManager.hasPermission(exception, permission);
    }
    private static Direction directionByName(String name) {
        for (Direction direction : Direction.values()) {
            if (name.equals(direction.getName())) return direction;
        }
        return null;
    }
    private static int expand(Claim claim, int amount, Direction direction, ServerCommandSource source, boolean admin) throws CommandSyntaxException {
        UUID ownerID = source.getPlayer().getGameProfile().getId();
        if (claim == null) {
            source.sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return 0;
        }
        if (direction == null) {
            source.sendFeedback(new LiteralText("That is not a valid direction").formatted(Formatting.RED), false);
            return 0;
        }
        if (!claim.permissionManager.hasPermission(ownerID, Claim.Permission.MODIFY_SIZE)) {
            source.sendFeedback(new LiteralText("You do not have border change permissions in that claim").formatted(Formatting.RED), false);
            if (!admin) return 0;
        }
        int oldArea = claim.getArea();
        if (amount > 0) claim.expand(direction, amount);
        else {
            if (!claim.shrink(direction, amount)) {
                source.sendFeedback(new LiteralText("You can't shrink your claim that far. It would pass its opposite wall.").formatted(Formatting.RED), false);
                return 0;
            }
        }
        if (ClaimManager.INSTANCE.wouldIntersect(claim)) {
            if (amount < 0) claim.expand(direction, amount);
            else claim.shrink(direction, amount);
            source.sendFeedback(new LiteralText("Expansion would result in hitting another claim").formatted(Formatting.RED), false);
            return 0;
        }
        int newArea = claim.getArea() - oldArea;
        if (!admin && claim.claimBlockOwner != null && ClaimManager.INSTANCE.getClaimBlocks(ownerID) < newArea) {
            if (amount < 0) claim.expand(direction, amount);
            else claim.shrink(direction, amount);
            source.sendFeedback(new LiteralText("You don't have enough claim blocks. You have " + ClaimManager.INSTANCE.getClaimBlocks(ownerID) + ", you need " + newArea + "(" + (newArea - ClaimManager.INSTANCE.getClaimBlocks(ownerID)) + " more)").formatted(Formatting.RED), false);
            checkPlayer(source, ownerID);
            return 0;
        } else {
            if (!admin && claim.claimBlockOwner != null) ClaimManager.INSTANCE.useClaimBlocks(ownerID, newArea);
            source.sendFeedback(new LiteralText("Your claim was " + (amount > 0 ? "expanded" : "shrunk") + " by " + (amount < 0 ? -amount : amount) + " blocks " + direction.getName()).formatted(Formatting.GREEN), false);
            checkPlayer(source, ownerID);
            if (amount < 0) claim.expand(direction, amount);
            else claim.shrink(direction, amount);
            source.getWorld().getPlayers().forEach(playerEntity -> {
                if (((ClaimShower)playerEntity).getShownClaim() != null && ((ClaimShower)playerEntity).getShownClaim().name.equals(claim.name)) silentHideShow(playerEntity, claim, true, false);
            });
            if (amount > 0) claim.expand(direction, amount);
            else claim.shrink(direction, -amount);
            source.getWorld().getPlayers().forEach(playerEntity -> {
                if (((ClaimShower)playerEntity).getShownClaim() != null && ((ClaimShower)playerEntity).getShownClaim().name.equals(claim.name)) silentHideShow(playerEntity, claim, false, false);
            });
        }
        return 0;
    }
    private static int info(ServerCommandSource source, Claim claim) {
        if (claim == null) {
            source.sendFeedback(new LiteralText("That claim does not exist").formatted(Formatting.RED), false);
            return 0;
        }

        GameProfile owner = claim.claimBlockOwner == null ? null : source.getMinecraftServer().getUserCache().getByUuid(claim.claimBlockOwner);
        BlockPos size = claim.getSize();

        Text text = new LiteralText("\n");
        text.append(new LiteralText("Claim Info: " + claim.name).formatted(Formatting.GOLD)).append("\n");
        text.append(newInfoLine("Name", new LiteralText(claim.name).formatted(Formatting.WHITE)));
        text.append(newInfoLine("Owner",
                owner != null ? new LiteralText(owner.getName()).formatted(Formatting.GOLD) :
                        claim.customOwnerName != null ? new LiteralText(claim.customOwnerName).formatted(Formatting.GOLD) :
                                new LiteralText("Not Present").formatted(Formatting.RED, Formatting.ITALIC)));
        text.append(newInfoLine("Size", new LiteralText(size.getX() + (claim.is2d() ? "x" : ("x" + size.getY() + "x")) + size.getZ()).formatted(Formatting.GREEN)));


        text.append(new LiteralText("").append(new LiteralText("* Settings:").formatted(Formatting.YELLOW))
                .append(Messages.Command.getSettings(claim)).append("\n"));
        Text pos = new LiteralText("");
        Text min = newPosLine(claim.min, Formatting.AQUA, Formatting.DARK_AQUA);
        Text max = newPosLine(claim.max, Formatting.LIGHT_PURPLE, Formatting.DARK_PURPLE);

        if (PERMISSION_CHECK_ADMIN.test(source)) {
            String format = "/execute in " + Objects.requireNonNull(Registry.DIMENSION_TYPE.getId(claim.dimension), "Dimension Doesn't Exist!!").toString() + " run tp " + source.getName() + (Config.claims2d ? " %s ~ %s" : " %s %s %s");
            min.styled(style -> style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    String.format(format, claim.min.getX(), Config.claims2d ? claim.min.getZ() : claim.min.getY(), claim.min.getZ()))));
            max.styled(style -> style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    String.format(format, claim.max.getX(), Config.claims2d ? claim.max.getZ() : claim.max.getY(), claim.max.getZ()))));
        }

        pos.append(newInfoLine("Position", new LiteralText("")
                .append(new LiteralText("Min ").formatted(Formatting.WHITE).append(min))
                .append(" ")
                .append(new LiteralText("Max ").formatted(Formatting.WHITE).append(max))));
        text.append(pos);
        text.append(newInfoLine("Dimension", new LiteralText(Registry.DIMENSION_TYPE.getId(claim.dimension).getPath())));
        source.sendFeedback(text, false);
        return 1;
    }
    private static Text newPosLine(BlockPos pos, Formatting form1, Formatting form2) {
        return new LiteralText("")
                .append(new LiteralText(String.valueOf(pos.getX())).formatted(form1))
                .append(" ")
                .append(new LiteralText(String.valueOf(pos.getY())).formatted(form2))
                .append(" ")
                .append(new LiteralText(String.valueOf(pos.getZ())).formatted(form1));
    }
    private static Text newInfoLine(String title, Text text) {
        return new LiteralText("").append(new LiteralText("* " + title + ": ").formatted(Formatting.YELLOW))
                .append(text).append("\n");
    }
    private static int list(ServerCommandSource source, String target) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        GameProfile profile = target == null ? player.getGameProfile() : source.getMinecraftServer().getUserCache().findByName(target);

        if (profile == null) {
            source.sendError(Messages.INVALID_PLAYER);
            return -1;
        }

        List<Claim> claims = ClaimManager.INSTANCE.getPlayerClaims(profile.getId());
        if (claims.isEmpty()) {
            source.sendFeedback(new LiteralText("No Claims").formatted(Formatting.RED), false);
            return -1;
        }


        Text text = new LiteralText("\n").append(new LiteralText("Claims: " + source.getName()).formatted(Formatting.GOLD)).append("\n ");
        boolean nextColor = false;
        for (Claim claim : claims) {
            Text cText = new LiteralText(claim.name).formatted(nextColor ? Formatting.YELLOW : Formatting.GOLD).styled((style) -> {
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("Click for more Info").formatted(Formatting.GREEN)));
                style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim info " + claim.name));
            });

            nextColor = !nextColor;
            text.append(cText.append(" "));
        }

        source.sendFeedback(text.append("\n"), false);
        return 1;
    }
    private static int listAll(ServerCommandSource source, int page) {
        List<Claim> claims = new ArrayList<>();
        ClaimManager.INSTANCE.claimsByName.forEach((name, claim) -> claims.add(claim));

        if (claims.isEmpty()) {
            source.sendFeedback(new LiteralText("No Claims").formatted(Formatting.RED), false);
            return -1;
        }

        List<Text> list = new ArrayList<>(claims.size() / 10);

        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);

            Text cText = new LiteralText("").append(new LiteralText(i + ". ").formatted(Formatting.GOLD))
                    .append(new LiteralText(claim.name).formatted(Formatting.GOLD)).append(new LiteralText(" in ").formatted(Formatting.GRAY))
                    .append(new LiteralText(Objects.requireNonNull(Registry.DIMENSION_TYPE.getId(claim.dimension), "Dimension Doesn't Exist!!").getPath()).formatted(Formatting.WHITE));

            cText.styled((style) -> {
                style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("Click for more Info").formatted(Formatting.GREEN)));
                style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim info " + claim.name));
            });

            list.add(cText.append("\n"));
        }

        Text[] texts = new Text[]{};
        texts = list.toArray(texts);
        return sendPage(source, texts, page, "Claims", "/claim listall %page%");
    }
    private static int rename(CommandContext<ServerCommandSource> context, boolean admin) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "claim");
        String newName = StringArgumentType.getString(context, "name");
        Claim claimToRename = ClaimManager.INSTANCE.claimsByName.get(name);
        if (claimToRename == null) {
            context.getSource().sendError(Messages.INVALID_CLAIM);
            return -1;
        }
        if (ClaimManager.INSTANCE.claimsByName.containsKey(newName)) {
            context.getSource().sendError(new LiteralText("That name is already taken!"));
            return -1;
        }
        if (!admin && !claimToRename.hasPermission(context.getSource().getPlayer().getUuid(), Claim.Permission.MODIFY_PROPERTIES)) {
            context.getSource().sendError(new LiteralText("You don't have permission to modify claim properties!"));
            return -1;
        }
        ClaimManager.INSTANCE.claimsByName.remove(name);
        claimToRename.name = newName;
        ClaimManager.INSTANCE.addClaim(claimToRename);
        claimToRename.name = newName;
        context.getSource().sendFeedback(new LiteralText("Renamed Claim " + name + " to " + newName).formatted(Formatting.GOLD), admin);
        return -1;
    }
    private static int showTrustedList(CommandContext<ServerCommandSource> context, Claim claim, boolean showSelf) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        int mapSize = claim.permissionManager.playerPermissions.size();

        if (mapSize == 1 && !showSelf) {
            source.sendError(new LiteralText(claim.name + " is not trusting anyone!"));
            return -1;
        }

        Text text = new LiteralText("\n");
        text.append(new LiteralText("Trusted players for Claim ").formatted(Formatting.YELLOW))
                .append(new LiteralText(claim.name).formatted(Formatting.GOLD)).append("\n");

        AtomicInteger atomicInteger = new AtomicInteger();
        claim.permissionManager.playerPermissions.forEach((uuid, perm) -> {
            atomicInteger.incrementAndGet();
            Text pText = new LiteralText("");
            GameProfile profile = source.getMinecraftServer().getUserCache().getByUuid(uuid);
            String name = profile != null ? profile.getName() : uuid.toString();

            pText.append(new LiteralText(atomicInteger.get() + ". ").formatted(Formatting.GOLD))
                    .append(new LiteralText(name).formatted(Formatting.YELLOW));

            Text hover = new LiteralText("");
            hover.append(new LiteralText("Permissions:").formatted(Formatting.WHITE)).append("\n");

            int allowed = 0;
            int i = 0;
            boolean nextColor = false;
            for (Claim.Permission value : Claim.Permission.values()) {
                if (claim.permissionManager.hasPermission(uuid, value)) {
                    Formatting formatting = nextColor ? Formatting.GREEN : Formatting.DARK_GREEN;
                    hover.append(new LiteralText(value.id).formatted(formatting)).append(" ");
                    if (i == 3) hover.append("\n");
                    allowed++;
                    i++;
                    nextColor = !nextColor;
                }
            }

            pText.append(new LiteralText(" ")
                    .append(new LiteralText("(").formatted(Formatting.GOLD))
                    .append(new LiteralText(String.valueOf(allowed)).formatted(Formatting.GREEN))
                    .append(new LiteralText("/").formatted(Formatting.GOLD))
                    .append(new LiteralText(String.valueOf(Claim.Permission.values().length)).formatted(Formatting.YELLOW))
                    .append(new LiteralText(")").formatted(Formatting.GOLD))
            );

            pText.styled((style) -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
            text.append(pText).append("\n");
        });

        source.sendFeedback(text, false);
        return 1;
    }
    private static int setOwnerName(ServerCommandSource source, Claim claim, String input) {
        String name = input.equals("reset") ? null : input;
        source.sendFeedback(new LiteralText("Set the Custom Owner Name to ")
                .formatted(Formatting.YELLOW).append(new LiteralText(name == null ? "Reset" : name).formatted(Formatting.GOLD)).append(new LiteralText(" from "))
                        .append(new LiteralText(claim.customOwnerName == null ? "Not Present" : claim.customOwnerName).formatted(Formatting.GOLD))
                        .append(new LiteralText(" for ").formatted(Formatting.YELLOW)).append(new LiteralText(claim.name).formatted(Formatting.GOLD))
                , false);
        claim.customOwnerName = input;
        return 1;
    }
    private static int setOwner(ServerCommandSource source, Claim claim, GameProfile profile) {
        GameProfile oldOwner = source.getMinecraftServer().getUserCache().getByUuid(claim.claimBlockOwner);
        source.sendFeedback(new LiteralText("Set the Claim Owner to ")
                        .formatted(Formatting.YELLOW).append(new LiteralText(profile.getName()).formatted(Formatting.GOLD)).append(new LiteralText(" from "))
                        .append(new LiteralText(oldOwner == null ? "(" + claim.claimBlockOwner + ")" : oldOwner.getName()).formatted(Formatting.GOLD))
                        .append(new LiteralText(" for ").formatted(Formatting.YELLOW)).append(new LiteralText(claim.name).formatted(Formatting.GOLD))
                , false);
        claim.claimBlockOwner = profile.getId();
        return 1;
    }
    private static int setEventMessage(ServerCommandSource source, Claim claim, Claim.Event event, String message) {
        switch (event) {
            case ENTER_CLAIM:
                claim.enterMessage = message.equalsIgnoreCase("reset") ? null : message;
                break;
            case LEAVE_CLAIM:
                claim.leaveMessage = message.equalsIgnoreCase("reset") ? null : message;
                break;
        }

        if (message.equalsIgnoreCase("reset")) {
            source.sendFeedback(new LiteralText("Reset ").append(new LiteralText(event.id).formatted(Formatting.GOLD)
                            .append(new LiteralText(" Event Message for claim ").formatted(Formatting.YELLOW))
                            .append(new LiteralText(claim.name).formatted(Formatting.GOLD))).formatted(Formatting.YELLOW)
                    , false);
            return -1;
        }

        source.sendFeedback(new LiteralText("Set ").append(new LiteralText(event.id).formatted(Formatting.GOLD)
                        .append(new LiteralText(" Event Message for claim ").formatted(Formatting.YELLOW))
                        .append(new LiteralText(claim.name).formatted(Formatting.GOLD)).append(new LiteralText(" to:").formatted(Formatting.YELLOW)))
                        .append("\n").append(new LiteralText(ChatColor.translate(message)))
                        .formatted(Formatting.YELLOW)
                , false);
        return 1;
    }
    private static int executeSetting(ServerCommandSource source, String input, @Nullable String claimName, boolean isQuery, boolean value, boolean admin) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        Claim claim1 = claimName == null || claimName.isEmpty() ? ClaimManager.INSTANCE.getClaimAt(player.getSenseCenterPos(), player.dimension) :
                ClaimManager.INSTANCE.claimsByName.get(claimName);
        if (claim1 == null) {
            source.sendError(Messages.INVALID_CLAIM);
            return -1;
        }

        if (input == null) {
            return querySettings(source, claim1);
        }

        validateCanAccess(player, claim1, admin);
        Claim.ClaimSettings.Setting setting = Claim.ClaimSettings.Setting.byId(input);
        Claim.Permission permission = Claim.Permission.byId(input);

        if (setting != null && permission == null)
            return isQuery ? querySetting(source, claim1, setting) : setSetting(source, claim1, setting, value);

        if (setting == null && permission != null)
            return isQuery ? queryPermission(source, claim1, permission) : setPermission(source, claim1, permission, value);

        source.sendError(Messages.INVALID_SETTING);
        return -1;
    }
    private static int executePermission(ServerCommandSource source, String input, @Nullable String claimName, boolean isQuery, boolean value, boolean admin) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        Claim claim1 = claimName == null ? ClaimManager.INSTANCE.getClaimAt(player.getSenseCenterPos(), player.dimension) :
                ClaimManager.INSTANCE.claimsByName.get(claimName);
        if (claim1 == null) {
            source.sendError(Messages.INVALID_CLAIM);
            return -1;
        }

        validateCanAccess(player, claim1, admin);
        Claim.Permission permission = Claim.Permission.byId(input);
        if (permission != null)
            return !isQuery ? setPermission(source, claim1, permission, value) : queryPermission(source, claim1, permission);

        source.sendError(Messages.INVALID_SETTING);
        return -1;
    }
    private static int querySetting(ServerCommandSource source, Claim claim, Claim.ClaimSettings.Setting setting) {
        boolean enabled = claim.settings.getSetting(setting);
        source.sendFeedback(new LiteralText(ChatColor.translate("&eSetting &6" + setting.name + " &e is set to " + (enabled ? "&a" : "&c") + enabled + "&e for &6" + claim.name)), false);
        return 1;
    }
    private static int setSetting(ServerCommandSource source, Claim claim, Claim.ClaimSettings.Setting setting, boolean set) {
        claim.settings.settings.put(setting, set);
        source.sendFeedback(new LiteralText(ChatColor.translate("&eSet setting &6" + setting.name + "&e to " + (set ? "&a" : "&c") + set + "&e for &6" + claim.name)), false);
        return 0;
    }
    private static int queryPermission(ServerCommandSource source, Claim claim, Claim.Permission permission) {
        boolean defaultPerm = claim.permissionManager.defaults.hasPermission(permission);
        source.sendFeedback(new LiteralText(ChatColor.translate("&ePermission &6" + permission.id + "&e is set to " + (defaultPerm ? "&a" : "&c") + defaultPerm + "&e for &6" + claim.name)), false);
        return 1;
    }
    private static int setPermission(ServerCommandSource source, Claim claim, Claim.Permission permission, boolean set) {
        claim.permissionManager.defaults.setPermission(permission, set);
        source.sendFeedback(new LiteralText(ChatColor.translate("&eSet permission &6" + permission.id + "&e to " + (set ? "&a" : "&c") + set + "&e for &6" + claim.name)), false);
        return 1;
    }
    private static int querySettings(ServerCommandSource source, Claim claim) {
        source.sendFeedback(new LiteralText("\n").append(new LiteralText("Settings: " + claim.name).formatted(Formatting.YELLOW)).append("\n")
                .append(Messages.Command.getSettings(claim)).append("\n"), false);
        return 1;
    }
    private static int executeTrust(CommandContext<ServerCommandSource> context, ServerPlayerEntity target, boolean set, @Nullable String claimName) throws CommandSyntaxException {
        ServerPlayerEntity p = context.getSource().getPlayer();
        Claim claim1 = claimName == null ? ClaimManager.INSTANCE.getClaimAt(p.getSenseCenterPos(), p.dimension) : ClaimManager.INSTANCE.claimsByName.get(claimName);
        validateClaim(claim1);

        return setTrust(context, claim1, target, set, false);
    }
    private static int setTrust(CommandContext<ServerCommandSource> context, Claim claim, ServerPlayerEntity target, boolean set, boolean admin) throws CommandSyntaxException {
        if (verifyPermission(claim, Claim.Permission.MODIFY_PERMISSIONS, context, admin)) {
            claim.permissionManager.playerPermissions.put(target.getGameProfile().getId(), set ? new Claim.InvertedPermissionMap() : new Claim.DefaultPermissionMap());
            context.getSource().sendFeedback(new LiteralText(target.getGameProfile().getName() + (set ? " now" : " no longer") + " has all the permissions").formatted(Formatting.YELLOW), false);

            String message;
            if (set) message = "&aTrusted player &6" + target.getEntityName() + "&a in &6" + claim.name + "\n&aThey now have all the default permissions";
            else message = "&cDistrusted player &6" + target.getEntityName() + "&c in &6" + claim.name + "\n&cThey don't have any permissions now";
            context.getSource().sendFeedback(new LiteralText(ChatColor.translate(message)), false);
        }
        return 1;
    }
}
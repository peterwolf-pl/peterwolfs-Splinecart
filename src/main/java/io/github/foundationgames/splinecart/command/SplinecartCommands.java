package io.github.foundationgames.splinecart.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.foundationgames.splinecart.BankingPreferences;
import io.github.foundationgames.splinecart.SupportMode;
import io.github.foundationgames.splinecart.SupportPreferences;
import io.github.foundationgames.splinecart.util.TrackBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;

public final class SplinecartCommands {
    private SplinecartCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("splinecart")
                .then(supportsCommand())
                .then(bankingCommand())
                .then(Commands.literal("connect")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(trackArgs(connectPositions())))
                .then(Commands.literal("build")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("line")
                                .then(trackArgs(linePositions())))
                        .then(Commands.literal("path")
                                .then(trackArgs(pathPositions())))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> supportsCommand() {
        return Commands.literal("supports")
                .executes(SplinecartCommands::executeSupportsQuery)
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("mode", StringArgumentType.word())
                        .suggests(SupportMode::suggest)
                        .executes(SplinecartCommands::executeSupportsSet));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> bankingCommand() {
        return Commands.literal("banking")
                .executes(SplinecartCommands::executeBankingQuery)
                .then(Commands.literal("on").executes(ctx -> executeBankingSet(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> executeBankingSet(ctx, false)))
                .then(Commands.literal("toggle").executes(SplinecartCommands::executeBankingToggle));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> trackArgs(
            RequiredArgumentBuilder<CommandSourceStack, ?> positions
    ) {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument("track", StringArgumentType.word())
                .suggests(TrackTypeArgument::suggest)
                .then(positions);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> connectPositions() {
        return RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("from", BlockPosArgument.blockPos())
                .then(RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("to", BlockPosArgument.blockPos())
                        .executes(ctx -> executeConnect(ctx, List.of(
                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                BlockPosArgument.getLoadedBlockPos(ctx, "to")
                        ))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> linePositions() {
        return RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("from", BlockPosArgument.blockPos())
                .then(RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("to", BlockPosArgument.blockPos())
                        .executes(ctx -> executeBuildLine(ctx, interpolateLine(ctx, 0)))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("segments", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> executeBuildLine(ctx, interpolateLine(ctx, IntegerArgumentType.getInteger(ctx, "segments"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> pathPositions() {
        var start = RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("start", BlockPosArgument.blockPos());
        start.executes(ctx -> executeBuildPath(ctx, List.of(BlockPosArgument.getLoadedBlockPos(ctx, "start"))));
        appendPathWaypoint(start, 1);
        return start;
    }

    private static void appendPathWaypoint(
            RequiredArgumentBuilder<CommandSourceStack, Coordinates> parent,
            int index
    ) {
        if (index > 16) {
            return;
        }

        var waypoint = RequiredArgumentBuilder.<CommandSourceStack, Coordinates>argument("waypoint" + index, BlockPosArgument.blockPos());
        waypoint.executes(ctx -> executeBuildPath(ctx, collectPath(ctx, index)));
        appendPathWaypoint(waypoint, index + 1);
        parent.then(waypoint);
    }

    private static List<BlockPos> collectPath(CommandContext<CommandSourceStack> ctx, int lastIndex) throws CommandSyntaxException {
        var positions = new ArrayList<BlockPos>(lastIndex + 1);
        positions.add(BlockPosArgument.getLoadedBlockPos(ctx, "start"));
        for (int i = 1; i <= lastIndex; i++) {
            positions.add(BlockPosArgument.getLoadedBlockPos(ctx, "waypoint" + i));
        }
        return positions;
    }

    private static List<BlockPos> interpolateLine(CommandContext<CommandSourceStack> ctx, int segments) throws CommandSyntaxException {
        var from = BlockPosArgument.getLoadedBlockPos(ctx, "from");
        var to = BlockPosArgument.getLoadedBlockPos(ctx, "to");
        int count = segments > 0 ? segments : TrackBuilder.defaultSegmentCount(from, to);
        return TrackBuilder.interpolateLine(from, to, count);
    }

    private static SupportMode resolveSupports(CommandContext<CommandSourceStack> ctx) {
        return SupportPreferences.get(ctx.getSource().getPlayer());
    }

    private static boolean resolveBanking(CommandContext<CommandSourceStack> ctx) {
        return BankingPreferences.get(ctx.getSource().getPlayer());
    }

    private static int executeBankingQuery(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.splinecart.banking_player_only"));
            return 0;
        }

        boolean enabled = BankingPreferences.get(player);
        source.sendSuccess(() -> Component.translatable("commands.splinecart.banking_query", stateName(enabled)), false);
        return 1;
    }

    private static int executeBankingSet(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            source.sendFailure(Component.translatable("commands.splinecart.banking_player_only"));
            return 0;
        }

        BankingPreferences.set(serverPlayer, enabled);
        source.sendSuccess(() -> Component.translatable("commands.splinecart.banking_set", stateName(enabled)), false);
        return 1;
    }

    private static int executeBankingToggle(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            source.sendFailure(Component.translatable("commands.splinecart.banking_player_only"));
            return 0;
        }

        boolean enabled = BankingPreferences.toggle(serverPlayer);
        source.sendSuccess(() -> Component.translatable("commands.splinecart.banking_set", stateName(enabled)), false);
        return 1;
    }

    private static String stateName(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private static int executeSupportsQuery(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.splinecart.supports_player_only"));
            return 0;
        }

        var mode = SupportPreferences.get(player);
        source.sendSuccess(() -> Component.translatable("commands.splinecart.supports_query", mode.name().toLowerCase()), false);
        return 1;
    }

    private static int executeSupportsSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            source.sendFailure(Component.translatable("commands.splinecart.supports_player_only"));
            return 0;
        }

        var mode = SupportMode.get(ctx, "mode");
        SupportPreferences.set(serverPlayer, mode);
        source.sendSuccess(() -> Component.translatable("commands.splinecart.supports_set", mode.name().toLowerCase()), false);
        return 1;
    }

    private static int executeConnect(CommandContext<CommandSourceStack> ctx, List<BlockPos> positions) throws CommandSyntaxException {
        if (positions.size() != 2) {
            return 0;
        }

        var source = ctx.getSource();
        ServerLevel level = source.getLevel();
        var track = TrackTypeArgument.get(ctx, "track");
        var supports = resolveSupports(ctx);
        boolean banking = resolveBanking(ctx);

        if (!TrackBuilder.connect(level, positions.get(0), positions.get(1), track, supports, banking)) {
            source.sendFailure(Component.translatable("commands.splinecart.connect_failed",
                    positions.get(0).getX(), positions.get(0).getY(), positions.get(0).getZ(),
                    positions.get(1).getX(), positions.get(1).getY(), positions.get(1).getZ()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.splinecart.connect_success",
                track.name().toLowerCase(), supports.name().toLowerCase(),
                positions.get(0).getX(), positions.get(0).getY(), positions.get(0).getZ(),
                positions.get(1).getX(), positions.get(1).getY(), positions.get(1).getZ()), true);
        return 1;
    }

    private static int executeBuildLine(CommandContext<CommandSourceStack> ctx, List<BlockPos> positions) throws CommandSyntaxException {
        return executeBuildChain(ctx, positions, "commands.splinecart.build_line_success");
    }

    private static int executeBuildPath(CommandContext<CommandSourceStack> ctx, List<BlockPos> positions) throws CommandSyntaxException {
        return executeBuildChain(ctx, positions, "commands.splinecart.build_path_success");
    }

    private static int executeBuildChain(CommandContext<CommandSourceStack> ctx, List<BlockPos> positions, String successKey)
            throws CommandSyntaxException {
        if (positions.size() < 2) {
            ctx.getSource().sendFailure(Component.translatable("commands.splinecart.need_two_positions"));
            return 0;
        }

        var source = ctx.getSource();
        ServerLevel level = source.getLevel();
        var track = TrackTypeArgument.get(ctx, "track");
        var supports = resolveSupports(ctx);
        boolean banking = resolveBanking(ctx);

        int segments = TrackBuilder.buildChain(level, positions, track, supports, banking);
        if (segments <= 0) {
            source.sendFailure(Component.translatable("commands.splinecart.build_failed"));
            return 0;
        }

        int finalSegments = segments;
        source.sendSuccess(() -> Component.translatable(successKey,
                finalSegments, track.name().toLowerCase(), supports.name().toLowerCase()), true);
        return segments;
    }
}

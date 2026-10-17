package io.github.foundationgames.splinecart.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.foundationgames.splinecart.TrackType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class TrackTypeArgument {
    private static final List<String> SUGGESTIONS = List.of("default", "chain_drive", "magnetic");

    public static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
            Component.translatable("commands.splinecart.unknown_track_type"));

    private TrackTypeArgument() {
    }

    public static TrackType get(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return parse(StringArgumentType.getString(ctx, name));
    }

    public static TrackType parse(String name) throws CommandSyntaxException {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "default", "track", "normal" -> TrackType.DEFAULT;
            case "chain_drive", "chain" -> TrackType.CHAIN_DRIVE;
            case "magnetic", "magnet" -> TrackType.MAGNETIC;
            default -> throw UNKNOWN.create();
        };
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (var track : SUGGESTIONS) {
            if (track.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(track);
            }
        }
        return builder.buildFuture();
    }
}
package io.github.foundationgames.splinecart;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public enum SupportMode {
    /** Do not place structural supports under the spline. */
    NONE,
    /** Place wooden fence supports (default player behaviour for horizontal segments). */
    WOOD,
    /** Place iron bar supports along the full spline. */
    METAL,
    /** Place iron-block pillars every 10-15 blocks, with a third rail under the sleepers. */
    STEEL,
    /** Infer support material from segment geometry and nearby blocks. */
    AUTO;

    private static final List<String> SUGGESTIONS = List.of("none", "wood", "metal", "steel", "auto");

    public static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
            Component.translatable("commands.splinecart.unknown_support_mode"));

    public static SupportMode parse(String name) throws CommandSyntaxException {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "none", "bez", "bez_supportow", "no_supports" -> NONE;
            case "wood", "wooden", "drewniane", "drewno" -> WOOD;
            case "metal", "metalowe", "iron", "zelazo" -> METAL;
            case "steel", "stal", "slup", "pillar", "steel_pillar", "slup_stalowy", "metal_pillar" -> STEEL;
            case "auto" -> AUTO;
            default -> throw UNKNOWN.create();
        };
    }

    public static SupportMode fromSaved(String name) {
        try {
            return parse(name);
        } catch (CommandSyntaxException | NullPointerException e) {
            return AUTO;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SupportMode get(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return parse(StringArgumentType.getString(ctx, name));
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (var mode : SUGGESTIONS) {
            if (mode.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(mode);
            }
        }
        return builder.buildFuture();
    }
}
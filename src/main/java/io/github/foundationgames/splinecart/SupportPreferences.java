package io.github.foundationgames.splinecart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player support mode used for manual and command track building. */
public final class SupportPreferences {
    private static final Map<UUID, SupportMode> MODES = new ConcurrentHashMap<>();

    private SupportPreferences() {
    }

    public static SupportMode get(@Nullable Player player) {
        if (player == null) {
            return SupportMode.STEEL;
        }
        return MODES.getOrDefault(player.getUUID(), SupportMode.STEEL);
    }

    public static SupportMode set(ServerPlayer player, SupportMode mode) {
        MODES.put(player.getUUID(), mode);
        return mode;
    }

    public static void clear(ServerPlayer player) {
        MODES.remove(player.getUUID());
    }
}
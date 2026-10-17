package io.github.foundationgames.splinecart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player setting applied to newly placed or linked spline track ties. */
public final class SmoothingPreferences {
    private static final Map<UUID, Boolean> PREFERENCES = new ConcurrentHashMap<>();

    private SmoothingPreferences() {
    }

    public static boolean get(@Nullable Player player) {
        if (player == null) {
            return true;
        }
        return PREFERENCES.getOrDefault(player.getUUID(), true);
    }

    public static boolean set(ServerPlayer player, boolean enabled) {
        PREFERENCES.put(player.getUUID(), enabled);
        return enabled;
    }

    public static boolean toggle(ServerPlayer player) {
        return set(player, !get(player));
    }
}

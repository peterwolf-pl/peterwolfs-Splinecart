package io.github.foundationgames.splinecart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player setting applied to newly linked spline track sections. */
public final class BankingPreferences {
    private static final Map<UUID, Boolean> ENABLED = new ConcurrentHashMap<>();

    private BankingPreferences() {
    }

    public static boolean get(@Nullable Player player) {
        return player != null && ENABLED.getOrDefault(player.getUUID(), false);
    }

    public static boolean set(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED.put(player.getUUID(), true);
        } else {
            ENABLED.remove(player.getUUID());
        }
        return enabled;
    }

    public static boolean toggle(ServerPlayer player) {
        return set(player, !get(player));
    }
}

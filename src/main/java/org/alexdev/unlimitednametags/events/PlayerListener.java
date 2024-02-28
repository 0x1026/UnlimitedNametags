package org.alexdev.unlimitednametags.events;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final UnlimitedNameTags plugin;
    @Getter
    private final Multimap<UUID, UUID> trackedPlayers;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.trackedPlayers = Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet);
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getNametagManager().addPlayer(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            plugin.getNametagManager().removePlayer(event.getPlayer(), true);
        }, 1);
    }

    @EventHandler
    public void onTrack(@NotNull PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        if (!target.isOnline()) {
            return;
        }

        trackedPlayers.put(event.getPlayer().getUniqueId(), target.getUniqueId());

        final boolean isVanished = plugin.getVanishManager().isVanished(target);
        if (isVanished && !plugin.getVanishManager().canSee(event.getPlayer(), target)) {
            return;
        }

        final Optional<PacketDisplayText> display = plugin.getNametagManager().getPacketDisplayText(target);
        final Runnable runnable = () -> plugin.getNametagManager().updateDisplay(event.getPlayer(), target);

        if (display.isEmpty()) {
            plugin.getNametagManager().addPending(target, runnable);
            return;
        }

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, runnable, 2);
    }

    @EventHandler
    public void onUnTrack(@NotNull PlayerUntrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        trackedPlayers.remove(event.getPlayer().getUniqueId(), target.getUniqueId());

        plugin.getNametagManager().removeDisplay(event.getPlayer(), target);
    }

    @EventHandler
    public void onPotion(@NotNull EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED) {
            if (event.getNewEffect() == null || event.getNewEffect().getType() != PotionEffectType.INVISIBILITY) {
                return;
            }
            plugin.getNametagManager().removePlayerDisplay(player);
            plugin.getNametagManager().blockPlayer(player);
        } else if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (event.getOldEffect() == null || event.getOldEffect().getType() != PotionEffectType.INVISIBILITY) {
                return;
            }
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                plugin.getNametagManager().unblockPlayer(player);
                plugin.getNametagManager().addPlayer(player, false);
            }, 2);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().addPlayer(e.getPlayer(), false);
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().removePlayer(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        plugin.getNametagManager().removePlayerDisplay(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getNametagManager().addPlayer(event.getPlayer(), false);
        }, 1);
    }

}

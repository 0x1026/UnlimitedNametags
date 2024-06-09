package org.alexdev.unlimitednametags.events;

import com.github.Anon8281.universalScheduler.foliaScheduler.FoliaScheduler;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final UnlimitedNameTags plugin;
    @Getter
    private final Multimap<UUID, UUID> trackedPlayers;
    private final Set<UUID> diedPlayers;
    private final Map<Integer, UUID> playerEntityId;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.trackedPlayers = Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet);
        this.diedPlayers = Sets.newConcurrentHashSet();
        this.playerEntityId = Maps.newConcurrentMap();
        this.loadFoliaRespawnTask();
        this.loadEntityIds();
        this.loadTracker();
    }

    @SuppressWarnings("unchecked")
    private void loadTracker() {
        final Object trackedPlayersObject = System.getProperties().get("UnlimitedNameTags.trackedPlayers");
        if (trackedPlayersObject instanceof Multimap<?, ?> multimap) {
            this.trackedPlayers.putAll((Multimap<UUID, UUID>) multimap);
        }
    }

    private void loadEntityIds() {
        Bukkit.getOnlinePlayers().forEach(player -> playerEntityId.put(player.getEntityId(), player.getUniqueId()));
    }

    private void loadFoliaRespawnTask() {
        if (!(plugin.getTaskScheduler() instanceof FoliaScheduler)) {
            return;
        }

        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> diedPlayers.forEach(player -> {
            final Player p = plugin.getServer().getPlayer(player);
            if (p == null || p.isDead()) {
                return;
            }
            plugin.getNametagManager().addPlayer(p);
            diedPlayers.remove(player);
        }), 1, 1);
    }

    public Optional<Player> getPlayerFromEntityId(int entityId) {
        final UUID player = playerEntityId.get(entityId);
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugin.getServer().getPlayer(player));
    }

    public void onDisable() {
        System.getProperties().put("UnlimitedNameTags.trackedPlayers", trackedPlayers);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().addPlayer(event.getPlayer()), 1);
        playerEntityId.put(event.getPlayer().getEntityId(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        diedPlayers.remove(event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().removePlayer(event.getPlayer(), true), 1);
        playerEntityId.remove(event.getPlayer().getEntityId());
    }

    @EventHandler
    public void onTrack(@NotNull PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        if (!target.isOnline()) {
            return;
        }

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            trackedPlayers.put(event.getPlayer().getUniqueId(), target.getUniqueId());

            final boolean isVanished = plugin.getVanishManager().isVanished(target);
            if (isVanished && !plugin.getVanishManager().canSee(event.getPlayer(), target)) {
                return;
            }

            final Optional<PacketDisplayText> display = plugin.getNametagManager().getPacketDisplayText(target);

            if (display.isEmpty()) {
                plugin.getLogger().warning("Display is empty for " + target.getName());
                return;
            }

            plugin.getNametagManager().updateDisplay(event.getPlayer(), target);
        }, 3);
    }

    @EventHandler
    public void onUnTrack(@NotNull PlayerUntrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            trackedPlayers.remove(event.getPlayer().getUniqueId(), target.getUniqueId());

            plugin.getNametagManager().removeDisplay(event.getPlayer(), target);
        });
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
            plugin.getNametagManager().removeAllViewers(player);
        } else if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (event.getOldEffect() == null || event.getOldEffect().getType() != PotionEffectType.INVISIBILITY) {
                return;
            }
            plugin.getNametagManager().showToTrackedPlayers(player, trackedPlayers.get(player.getUniqueId()));

        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().showToTrackedPlayers(e.getPlayer(), trackedPlayers.get(e.getPlayer().getUniqueId()));
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().removeAllViewers(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        diedPlayers.add(event.getEntity().getUniqueId());
        plugin.getNametagManager().removeAllViewers(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        diedPlayers.remove(event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            plugin.getNametagManager().getPacketDisplayText(event.getPlayer()).ifPresent(d -> d.setVisible(true));
        }, 1);
    }

}

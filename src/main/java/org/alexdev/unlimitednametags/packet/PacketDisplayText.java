package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.entity.WrapperEntity;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.meta.types.DisplayMeta;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Getter
@SuppressWarnings("unused")
public class PacketDisplayText {

    private final UnlimitedNameTags plugin;
    private final WrapperEntity entity;
    private final TextDisplayMeta meta;
    private final Player owner;
    private final Set<UUID> blocked;

    public PacketDisplayText(@NotNull UnlimitedNameTags plugin, @NotNull Player owner) {
        this.plugin = plugin;
        this.owner = owner;
        final int randomId = (int) (Math.random() * 10000000);
        this.entity = EntityLib.createEntity(randomId, UUID.randomUUID(), EntityTypes.TEXT_DISPLAY);
        this.meta = (TextDisplayMeta) entity.getMeta();
        this.blocked = Sets.newConcurrentHashSet();
    }

    public void text(@NotNull Component text) {
        fixViewers();
        meta.setText(text);
    }

    public void setBillboard(@NotNull DisplayMeta.BillboardConstraints billboard) {
        meta.setBillboardConstraints(billboard);
    }

    public void setBillboard(@NotNull Display.Billboard billboard) {
        meta.setBillboardConstraints(DisplayMeta.BillboardConstraints.valueOf(billboard.name()));
    }

    public void setShadowed(boolean shadowed) {
        meta.setShadow(shadowed);
    }

    public void setSeeThrough(boolean seeThrough) {
        meta.setSeeThrough(seeThrough);
    }

    public void setBackgroundColor(int color) {
        meta.setBackgroundColor(color);
    }

    public void setBackgroundColor(@NotNull Color color) {
        meta.setBackgroundColor(color.asARGB());
    }

    public void setInvisibleBackground() {
        setBackgroundColor(Color.BLACK.setAlpha(0).asRGB());
    }

    public void setTransformation(@NotNull Vector3f vector3f) {
        meta.setTranslation(vector3f);
    }

    public void setYOffset(float offset) {
        this.setTransformation(new Vector3f(0, offset, 0));
    }

    public void setViewRange(float range) {
        meta.setViewRange(range);
    }

    public void showToPlayer(@NotNull Player player) {
        if (player == owner) {
            return;
        }
        if (blocked.contains(player.getUniqueId())) {
            return;
        }

        setPosition();
        final boolean result = entity.addViewer(player.getUniqueId());
        if (!result) {
            plugin.getLogger().warning("Failed to add viewer " + player.getName() + " to " + owner.getName());
            return;
        }

        plugin.getPacketManager().sendPassengersPacket(player, this);
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            plugin.getPacketManager().sendPassengersPacket(player, this);
        }, 4); //min is 3
    }

    public void sendPassengersPacket(@NotNull Player player) {
        plugin.getPacketManager().sendPassengersPacket(player, this);
    }

    @SneakyThrows
    private void setPosition() {
        final Location location = owner.getLocation().clone();
        location.setY(location.getY() + 1.8);
        entity.setLocation(SpigotConversionUtil.fromBukkitLocation(location));
    }

    public void hideFromPlayer(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        boolean result = entity.getViewers().contains(player.getUniqueId());
        try {
            entity.removeViewer(player.getUniqueId());
        } catch (Exception ignored) {
            //packet events bug
        }
        plugin.getPacketManager().removePassenger(player, entity.getEntityId());
    }

    public void hideFromPlayerSilenty(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        entity.removeViewerSilently(player.getUniqueId());
    }

    public void showFromPlayerSilenty(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        entity.addViewerSilently(player.getUniqueId());
    }

    public boolean canPlayerSee(@NotNull Player player) {
        return entity.getViewers().contains(player.getUniqueId());
    }

    public void spawn(@NotNull Player player) {
        entity.spawn(SpigotConversionUtil.fromBukkitLocation(player.getLocation()));
    }

    @NotNull
    public UUID getUniqueId() {
        return entity.getUuid();
    }

    public void refresh() {
        fixViewers();
        final PacketWrapper<?> packet = meta.createPacket();
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        entity.getViewers().forEach(u -> {
            try {
                final Optional<? extends Player> optionalPlayer = Optional.ofNullable(Bukkit.getPlayer(u));
                if (optionalPlayer.isEmpty()) {
                    return;
                }
                final User user = PacketEvents.getAPI().getPlayerManager().getUser(optionalPlayer.get());
                if (user == null || user.getChannel() == null) {
                    return;
                }
                api.getProtocolManager().sendPacket(user.getChannel(), packet);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send packet to " + u, e);
            }
        });
    }

    private void fixViewers() {
        entity.getViewers().stream().filter(u -> {
            final Player player = Bukkit.getPlayer(u);
            return player == null || !player.isOnline();
        }).forEach(e -> {
            plugin.getLogger().warning("Removing viewer " + e + " from " + owner.getName());
            entity.removeViewerSilently(e);
        });
    }

    public void remove() {
        entity.remove();
        plugin.getPacketManager().removePassenger(entity.getEntityId());
    }

    public void handleQuit(@NotNull Player player) {
        entity.removeViewerSilently(player.getUniqueId());
        plugin.getPacketManager().removePassenger(player, entity.getEntityId());
    }

    public void setTextOpacity(byte b) {
        meta.setTextOpacity(b);
    }

    @NotNull
    public Set<Player> findNearbyPlayers() {
        final float viewDistance = 100;
        final List<Player> players = List.copyOf(owner.getWorld().getPlayers());
        return players.stream()
                .filter(p -> p.getLocation().distance(owner.getLocation()) <= viewDistance)
                .filter(Player::isOnline)
                .collect(Collectors.toSet());
    }
}

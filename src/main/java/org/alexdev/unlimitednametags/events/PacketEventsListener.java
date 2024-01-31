package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.retrooper.packetevents.injector.SpigotChannelInjector;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;

    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        //Are all listeners read only?
        PacketEvents.getAPI().getSettings().reEncodeByDefault(true)
                .checkForUpdates(false)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        PacketEvents.getAPI().init();
        inject();
    }

    private void inject() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            SpigotChannelInjector injector = (SpigotChannelInjector) PacketEvents.getAPI().getInjector();

            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

            // Set bukkit player object in the injectors
            injector.updatePlayer(user, player);
        });
    }

    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            handleDestroyEntities(event);
        }
    }

    private void handleDestroyEntities(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        WrapperPlayServerDestroyEntities destroyEntities = new WrapperPlayServerDestroyEntities(event);
        Arrays.stream(destroyEntities.getEntityIds())
                .mapToObj(id -> Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == id).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(target -> {
                    plugin.getNametagManager().getPacketDisplayText(target).ifPresent(packetDisplayText -> {
                        if (packetDisplayText.canPlayerSee(player)) {
                            packetDisplayText.hideFromPlayer(player);
                        }
                    });
                });
    }

    private void handlePassengers(PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> player = Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == packet.getEntityId()).findFirst();
        if (player.isEmpty()) {
            return;
        }

        final Optional<PacketDisplayText> optionalPacketDisplayText = plugin.getNametagManager().getPacketDisplayText(player.get());
        if (optionalPacketDisplayText.isEmpty()) {
            return;
        }

        plugin.getPacketManager().setPassengers(player.get(), Arrays.stream(packet.getPassengers()).boxed().toList());
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if (!plugin.getConfigManager().getSettings().isDisableDefaultNameTag() &&
                !plugin.getConfigManager().getSettings().isDisableDefaultNameTagBedrock() && plugin.getFloodgateHook().map(h -> h.isBedrock((Player) event.getPlayer())).orElse(false)) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
            packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
            event.markForReEncode(true);
        }
    }

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        PacketEvents.getAPI().terminate();
    }
}

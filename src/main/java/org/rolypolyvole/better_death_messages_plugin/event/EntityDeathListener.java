package org.rolypolyvole.better_death_messages_plugin.event;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Cat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;
import org.rolypolyvole.better_death_messages_plugin.BetterDeathMessagesPlugin;

import java.util.Objects;

public class EntityDeathListener implements Listener {

    private final BetterDeathMessagesPlugin plugin;

    public EntityDeathListener(BetterDeathMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!plugin.getEntityManager().shouldDeathBeAnnounced(entity)) {
            return;
        }

        net.minecraft.world.entity.LivingEntity nmsEntity = ((CraftLivingEntity) entity).getHandle();
        MutableComponent deathMessage = nmsEntity.getCombatTracker().getDeathMessage().copy();

        YamlConfiguration configuration = (YamlConfiguration) plugin.getConfig();

        Location deathLocation = entity.getLocation();

        ConfigurationSection messageSettings = configuration.getConfigurationSection("messages");
        assert messageSettings != null;

        boolean includeCoordinates = messageSettings.getBoolean("include-coordinates");

        String locationString = "(" + deathLocation.getBlockX() + ", " + deathLocation.getBlockY() + ", " + deathLocation.getBlockZ() + ")";

        if (includeCoordinates) {
            deathMessage = Component.literal(locationString + ": ").append(deathMessage);
        }

        String deathMessageString = deathMessage.getString();

        boolean announceToAll = Objects.equals(configuration.getString("announce-to"), "everyone");

        int announcementRadius = Math.abs(configuration.getInt("announcement-radius"));

        for (Player player : deathLocation.getWorld().getPlayers()) {
            if (announceToAll || deathLocation.distance(player.getLocation()) <= announcementRadius) {
                if (entity instanceof Tameable tameableEntity && player.equals(tameableEntity.getOwner())) {
                    if (includeCoordinates && (tameableEntity instanceof Parrot || tameableEntity instanceof Cat || tameableEntity instanceof Wolf)) {
                        player.sendMessage(locationString);
                        continue;
                    }
                }

                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

                serverPlayer.displayClientMessage(
                        deathMessage,
                        false // Whether the message is an action bar message
                );
            }
        }

        plugin.getLogger().info(deathMessageString);

        String channelId = messageSettings.getString("discord-messages.channel-id");
        assert channelId != null;

        JDA jda = plugin.getJda();

        if (jda == null) {
            return;
        }

        TextChannel textChannel = jda.getChannelById(TextChannel.class, channelId);
        assert textChannel != null;

        MessageCreateAction messageCreateAction = textChannel.sendMessageEmbeds(new MessageEmbed(
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                new MessageEmbed.AuthorInfo(deathMessageString, null, null, null),
                null,
                null,
                null,
                null));

        messageCreateAction.submit();
    }
}

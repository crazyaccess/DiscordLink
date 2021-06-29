package com.github.riku32.discordlink.Discord.Listeners;

import com.github.riku32.discordlink.Constants;
import com.github.riku32.discordlink.DiscordLink;
import com.github.riku32.discordlink.PlayerInfo;
import com.github.riku32.discordlink.Util;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatColor;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class VerificationListener extends ListenerAdapter {
    private final DiscordLink plugin;

    public VerificationListener(DiscordLink plugin) {
        this.plugin = plugin;
    }

    @SneakyThrows
    public void onButtonClick(ButtonClickEvent e) {
        if (e.getChannelType().isGuild()) return;
        if (!e.getComponentId().startsWith("link.")) return;

        // Make sure user is in guild before processing event
        plugin.getBot().getGuild().retrieveMemberById(e.getUser().getId()).queue(member -> {
            Objects.requireNonNull(e.getMessage()).editMessage(new MessageBuilder()
                    .setContent(" ")
                    .setActionRows(ActionRow.of(e.getMessage().getButtons().stream().map(Button::asDisabled).collect(Collectors.toList())
                    )).build()).queue();

            Optional<PlayerInfo> optionalPlayerInfo;
            try {
                optionalPlayerInfo = plugin.getDatabase().getPlayerInfo(e.getUser().getId());
            } catch (SQLException exception) {
                exception.printStackTrace();
                return;
            }

            if (!optionalPlayerInfo.isPresent()) {
                e.deferEdit().queue();
                return;
            }

            PlayerInfo playerInfo = optionalPlayerInfo.get();

            if (playerInfo.isVerified()) {
                e.deferEdit().queue();
                return;
            }

            try {
                if (!plugin.getDatabase().getMessageId(playerInfo.getDiscordID()).equals(e.getMessageId()))
                    return;
            } catch (SQLException exception) {
                exception.printStackTrace();
            }

            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerInfo.getUuid());

            switch (e.getComponentId()) {
                case "link.verify": {
                    e.replyEmbeds(new EmbedBuilder()
                            .setTitle("Linked")
                            .setDescription(String.format("Your discord account has been linked to %s", offlinePlayer.getName()))
                            .setColor(Constants.Colors.SUCCESS)
                            .build()).queue();

                    try {
                        plugin.getDatabase().verifyPlayer(e.getUser().getId());
                    } catch (SQLException exception) {
                        exception.printStackTrace();
                    }

                    if (offlinePlayer.isOnline()) {
                        Player player = Objects.requireNonNull(offlinePlayer.getPlayer());
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                String.format("&7Your minecraft account has been linked to &e%s", e.getUser().getAsTag())));

                        plugin.getFrozenPlayers().remove(player.getUniqueId());

                        if ((boolean) Objects.requireNonNull(plugin.getConfig().get("status_messages.enabled"))) {
                            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                                    String.valueOf(plugin.getConfig().get("status_messages.join"))
                                            .replaceAll("%color%", Util.colorToChatString(
                                                    member.getColor() != null ? member.getColor() : ChatColor.GRAY.getColor()))
                                            .replaceAll("%username%", offlinePlayer.getPlayer().getName())
                                            .replaceAll("%tag%", e.getUser().getAsTag())));
                        }

                        if ((boolean) Objects.requireNonNull(plugin.getConfig().get("chat.crosschat.enabled"))) {
                            if (plugin.getBot().getChannel() != null)
                                plugin.getBot().getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Constants.Colors.SUCCESS)
                                        .setAuthor(String.format("%s (%s) has joined", e.getUser().getName(), Bukkit.getOfflinePlayer(playerInfo.getUuid()).getName()),
                                                null, e.getUser().getAvatarUrl())
                                        .build())
                                        .queue();
                        }

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.teleport(player.getWorld().getSpawnLocation());
                            player.setGameMode(plugin.getServer().getDefaultGameMode());
                        });
                    }

                    break;
                }
                case "link.cancel": {
                    e.replyEmbeds(new EmbedBuilder()
                            .setTitle("Cancelled")
                            .setDescription("You have cancelled the linking process")
                            .setColor(Constants.Colors.FAIL)
                            .build()).queue();

                    if (offlinePlayer.isOnline()) {
                        Objects.requireNonNull(offlinePlayer.getPlayer()).sendMessage(ChatColor.translateAlternateColorCodes('&',
                                String.format("&e%s&7 has cancelled the linking process", e.getUser().getAsTag())));
                    }

                    try {
                        plugin.getDatabase().deletePlayer(e.getUser().getId());
                    } catch (SQLException exception) {
                        exception.printStackTrace();
                    }
                }
            }
        });
    }
}
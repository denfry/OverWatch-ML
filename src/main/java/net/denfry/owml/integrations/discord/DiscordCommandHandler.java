package net.denfry.owml.integrations.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Interface for Discord command handlers
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public interface DiscordCommandHandler {

    /**
     * Handle Discord slash command
     *
     * @param event the command event
     */
    void handle(SlashCommandInteractionEvent event);
}

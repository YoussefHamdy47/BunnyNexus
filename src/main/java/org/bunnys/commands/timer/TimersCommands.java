package org.bunnys.commands.timer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.bunnys.executors.timer.commands.StatsSubcommandExecutor;
import org.bunnys.handler.BunnyNexus;
import org.bunnys.handler.commands.slash.SlashCommandConfig;
import org.bunnys.handler.spi.SlashCommand;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

@SuppressWarnings("unused")
public class TimersCommands extends SlashCommand {

    @Autowired
    private StatsSubcommandExecutor statsExecutor;

    @Override
    protected void commandOptions(SlashCommandConfig.Builder options) {
        options.name("timer")
                .description("Manager all GBF Timer features")
                .addSubcommand(new SubcommandData("register", "Register a new semester")
                        .addOption(OptionType.STRING, "semester-name", "The name of the semester", true))
                .addSubcommand(new SubcommandData("stats", "View your study stats")
                        .addOption(OptionType.BOOLEAN, "ephemeral", "Whether the message should be ephemeral", false));
    }

    @Override
    public void execute(BunnyNexus client, SlashCommandInteractionEvent interaction) {
        String subCommand = interaction.getSubcommandName();

        if (subCommand == null) {
            interaction.reply("You must pick a subcommand").setEphemeral(true).queue();
            return;
        }

        switch (subCommand) {
            case "stats" -> {
                // read ephemeral option (defaults to false)
                boolean ephemeral = false;
                if (interaction.getOption("ephemeral") != null) {
                    try {
                        ephemeral = Objects.requireNonNull(interaction.getOption("ephemeral")).getAsBoolean();
                    } catch (Exception ignored) {
                    }
                }

                // Delegate to the StatsSubcommandExecutor (handles replies and error messages)
                if (statsExecutor == null) {
                    // Fallback: if autowiring didn't run, notify the user
                    interaction.reply("Stats executor not available. Please contact the bot admin.").setEphemeral(true).queue();
                    return;
                }

                statsExecutor.execute(client, interaction, ephemeral);
            }

            case "register" -> {
                interaction.reply("Register subcommand not implemented in this handler yet.").setEphemeral(true).queue();
            }

            default -> interaction.reply("Unknown subcommand").setEphemeral(true).queue();
        }
    }
}

package org.bunnys.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.bunnys.database.models.UserProfile;
import org.bunnys.database.repositories.UserRepository;
import org.bunnys.handler.BunnyNexus;
import org.bunnys.handler.commands.slash.SlashCommandConfig;
import org.bunnys.handler.database.providers.MongoProvider;
import org.bunnys.handler.spi.SlashCommand;

public class RegisterCommand extends SlashCommand {

    @Override
    protected void commandOptions(SlashCommandConfig.Builder options) {
        options.name("register")
                .description("Save your profile to the database");
    }

    @Override
    public void execute(BunnyNexus client, SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String username = event.getUser().getName();

        // Get Mongo provider from BunnyNexus
        MongoProvider mongo = client.getMongoProvider();
        if (mongo == null) {
            event.reply("❌ Database is not connected. Please try again later.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        UserRepository repo = new UserRepository(mongo.getConnection());

        // Check if user already exists
        UserProfile existing = repo.findById(userId);
        if (existing != null) {
            event.reply("⚠️ You are already registered as **"
                            + existing.getUsername() + "** (`" + existing.getUserId() + "`)")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Save new profile
        UserProfile profile = new UserProfile(userId, username);
        repo.save(profile);

        event.reply("✅ Registered profile for **" + username + "** (`" + userId + "`)").queue();
    }
}

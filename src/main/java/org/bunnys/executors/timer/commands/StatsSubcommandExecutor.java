package org.bunnys.executors.timer.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.bunnys.database.models.timer.Semester;
import org.bunnys.database.models.timer.Subject;
import org.bunnys.database.models.timer.TimerData;
import org.bunnys.database.models.users.GBFUser;
import org.bunnys.database.services.GBFUserService;
import org.bunnys.database.services.TimerDataService;
import org.bunnys.executors.timer.engine.TimerStats;
import org.bunnys.handler.BunnyNexus;
import org.bunnys.handler.utils.handler.Emojis;
import org.bunnys.handler.utils.handler.logging.Logger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Handles the /timer stats subcommand logic.
 * Usage: inject this class where your command dispatcher needs it and call execute(...)
 */
@Component
@SuppressWarnings("unused")
public class StatsSubcommandExecutor {

    private final TimerDataService timerDataService;
    private final GBFUserService userService;

    public StatsSubcommandExecutor(TimerDataService timerDataService, GBFUserService userService) {
        this.timerDataService = timerDataService;
        this.userService = userService;
    }

    /**
     * Execute the stats subcommand, replies to the provided interaction.
     *
     * @param client    BunnyNexus client (kept for parity with your handlers; not required here)
     * @param interaction JDA SlashCommandInteractionEvent
     * @param ephemeral Whether the reply should be ephemeral
     */
    public void execute(BunnyNexus client, SlashCommandInteractionEvent interaction, boolean ephemeral) {
        String userId = interaction.getUser().getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTimestamp(Instant.now());

        try {
            // debug - print interaction user id
            String interactingUserId = interaction.getUser().getId();
            Logger.info("Interaction userId = " + interactingUserId);

// debug - print GBFUserService lookup result
            userService.findByUserID(interactingUserId)
                    .ifPresentOrElse(u -> Logger.info("GBFUser found for userID=" + interactingUserId),
                            () -> Logger.info("GBFUser NOT found for userID=" + interactingUserId));

// debug - print TimerData accounts available
            List<TimerData> all = timerDataService.findAll();
            Logger.info("TimerDataService.findAll() returned " + all.size() + " documents for diagnostics.");
            for (TimerData td : all) {
                Logger.info("TimerData id=" + td.getId() + " account.userID=" + (td.getAccount() == null ? "null" : td.getAccount().getUserID()));
            }

            // Load user data
            Optional<GBFUser> maybeUser = userService.findByUserID(userId);
            if (maybeUser.isEmpty()) {
                embed.setTitle("No Timers Account");
                embed.setDescription("You don't have a GBF Timers account yet. Use `/timer register` to create one.");
                interaction.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }
            GBFUser user = maybeUser.get();

            System.out.println(user);

            // Load timer data: TimerDataService doesn't expose findByAccountUserID in your repo
            // so we search findAll (the repository is small enough; if you add findByAccountUserID you can replace this)
            TimerData timerData = timerDataService.findAll().stream()
                    .filter(td -> td != null && td.getAccount() != null && userId.equals(td.getAccount().getUserID()))
                    .findFirst()
                    .orElse(null);

            if (timerData == null) {
                embed.setTitle("No Timers Account");
                embed.setDescription("You don't have a GBF Timers account yet. Use `/timer register` to create one.");
                interaction.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            TimerStats stats = new TimerStats(timerData, user);
            TimerStats.Summary s = stats.snapshot();

            StringBuilder desc = new StringBuilder();

            // Lifetime study time
            desc.append("• Lifetime Study Time: ");
            if (s.totalStudyTimeMs() > 0) {
                desc.append(stats.humanDuration(s.totalStudyTimeMs()))
                        .append(" [")
                        .append(String.format(Locale.US, "%,.0f", s.totalStudyTimeMs() / (1000.0 * 60.0 * 60.0)))
                        .append(" hours]");
            } else {
                desc.append("0s");
            }
            desc.append("\n");

            // If there is an active semester
            Semester sem = timerData.getCurrentSemester();
            boolean hasSemester = sem != null && sem.getSemesterName() != null && !sem.getSemesterName().isBlank();

            if (hasSemester) {
                desc.append("• Semester: ").append(sem.getSemesterName()).append("\n");

                // Semester study time
                desc.append("• Semester Study Time: ");
                if (s.semesterTimeMs() > 0) {
                    desc.append(stats.humanDuration(s.semesterTimeMs()))
                            .append(" [")
                            .append(String.format(Locale.US, "%,.2f", s.semesterTimeMs() / (1000.0 * 60.0 * 60.0)))
                            .append(" hours]");
                } else {
                    desc.append("0s");
                }
                desc.append("\n");

                // Average per 7 sessions (safe)
                int sessions = s.sessionCount();
                int weeksBy7Sessions = Math.max(1, sessions / 7); // at least 1 to avoid division by zero
                long avgPer7 = sessions == 0 ? 0L : s.semesterTimeMs() / weeksBy7Sessions;
                desc.append("• Average Session Time / 7 sessions: ")
                        .append(avgPer7 > 0 ? stats.humanDuration(avgPer7) : "0s")
                        .append("\n");

                // Average session & total sessions
                desc.append("• Average Session Time: ")
                        .append(s.avgSessionMs() > 0 ? stats.humanDuration(s.avgSessionMs()) : "0s")
                        .append("\n");
                desc.append("• Total Sessions: ").append(s.sessionCount()).append("\n\n");
            }

            // Streaks
            desc.append("• Study Streak: ").append(stats.currentStreak()).append(" 🔥\n");
            desc.append("• Longest Study Streak: ").append(stats.longestStreak()).append(" 🔥\n\n");

            // Records
            desc.append("• Longest Session: ").append(s.longestSessionMs() > 0 ? stats.humanDuration(s.longestSessionMs()) : "0s").append("\n");
            stats.longestSemester().ifPresent(ls ->
                    desc.append("• Longest Semester: ")
                            .append(stats.humanDuration(Math.round(ls.getSemesterTime() * 1000.0)))
                            .append(" [")
                            .append(String.format(Locale.US, "%,.2f", ls.getSemesterTime() / 3600.0))
                            .append(" hours] - [")
                            .append(ls.getSemesterName())
                            .append("]\n")
            );
            desc.append("\n");

            // Breaks (if semester exists)
            if (hasSemester) {
                desc.append("• Semester Break Time: ").append(s.breakTimeMs() > 0 ? stats.humanDuration(s.breakTimeMs()) : "0s").append("\n");
                desc.append("• Total Breaks: ").append(s.breakCount()).append("\n");
                desc.append("• Average Break Time: ").append(s.avgBreakMs() > 0 ? stats.humanDuration(s.avgBreakMs()) : "0s").append("\n\n");
            }

            // Subjects
            desc.append("• Total Subjects: ").append(s.subjectCount()).append("\n");
            desc.append("• Total study instances across all subjects: ").append(s.totalTimesStudied()).append("\n");

            List<Subject> top = stats.topSubjects(10);
            if (!top.isEmpty()) {
                desc.append("\n**Subject Stats**\n");
                top.forEach(sub -> desc.append("• ").append(sub.getSubjectName()).append(" [").append(sub.getTimesStudied()).append("]\n"));
            } else {
                desc.append("\n**Subject Stats**\nN/A\n");
            }
            desc.append("\n");
            desc.append("• Average Study Time Per Subject: ").append(s.avgStudyOccurrenceMs() > 0 ? stats.humanDuration(s.avgStudyOccurrenceMs()) : "N/A").append("\n\n");

            // Level details (if semester exists)
            if (hasSemester) {
                desc.append(Emojis.get("default_verify")).append(" Semester Level: ").append(s.semesterLevel()).append("\n");
                desc.append("• XP: ").append(String.format(Locale.US, "%,d", s.semesterXP())).append("\n");
                int pctLevel = s.pctToNextLevel();
                desc.append(stats.progressBarForLevel()).append(" [").append(pctLevel).append("%]\n");
                desc.append("• Time until next level: ").append(stats.humanDuration(stats.msToNextLevel())).append("\n\n");

                desc.append(Emojis.get("default_verify")).append(" Account Level: ").append(s.accountLevel()).append("\n");
                desc.append("• RP: ").append(String.format(Locale.US, "%,d", s.accountRP())).append("\n");
                int pctRank = s.pctToNextRank();
                desc.append(stats.progressBarForRank()).append(" [").append(pctRank).append("%]\n");
                desc.append("• Time until next rank: ").append(stats.humanDuration(stats.msToNextRank())).append("\n\n");

                // GPA
                stats.gpaResult();
                double gpa = stats.gpaResult().gpa().doubleValue();
                // Show to 3 decimal places like your TS
                desc.append("• GPA: ").append(String.format(Locale.US, "%.3f", gpa)).append("\n");
            }

            // Average start time
            stats.averageStartTimeUnixSeconds().ifPresentOrElse(
                    unix -> desc.append("\n• Average Start Time: <t:").append(unix).append(":t>\n"),
                    () -> desc.append("\n• Average Start Time: N/A\n")
            );

            embed.setTitle(interaction.getUser().getName() + " — Study Stats")
                    .setDescription(desc.toString())
                    .setFooter("Stats for " + interaction.getUser().getName())
                    .setTimestamp(Instant.now());

            interaction.replyEmbeds(embed.build()).setEphemeral(ephemeral).queue();

        } catch (Exception e) {
            Logger.error("Error while building timer stats for user " + userId, e);
            embed.setTitle("Error")
                    .setDescription("I ran into an error while trying to fetch your stats. Please try again later.\n\n```\n"
                            + e.getMessage() + "\n```");
            interaction.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }
}

package org.bunnys.executors.timer.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.bunnys.database.models.timer.Subject;
import org.bunnys.database.models.timer.TimerData;
import org.bunnys.database.models.users.GBFUser;
import org.bunnys.database.services.GBFUserService;
import org.bunnys.database.services.TimerDataService;
import org.bunnys.executors.timer.engine.LevelEngine;
import org.bunnys.executors.timer.engine.TimerStats;
import org.bunnys.handler.utils.handler.colors.ColorCodes;
import org.bunnys.handler.utils.handler.logging.Logger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Optimized stats executor with better performance, error handling, and code
 * organization.
 */
@Component
public class StatsSubcommandExecutor {

        private final TimerDataService timerDataService;
        private final GBFUserService userService;

        public StatsSubcommandExecutor(TimerDataService timerDataService, GBFUserService userService) {
                this.timerDataService = timerDataService;
                this.userService = userService;
        }

        public void execute(SlashCommandInteractionEvent interaction, boolean ephemeral) {
                String userId = interaction.getUser().getId();
                String username = interaction.getUser().getName();

                // Use async processing to avoid blocking
                CompletableFuture.supplyAsync(() -> loadUserData(userId))
                                .thenAccept(result -> {
                                        if (result.isError()) {
                                                sendErrorEmbed(interaction, result.errorMessage(), ephemeral);
                                                return;
                                        }

                                        TimerStats stats = new TimerStats(result.timerData(), result.userData());
                                        EmbedBuilder embed = buildStatsEmbed(stats, username);
                                        interaction.replyEmbeds(embed.build()).setEphemeral(ephemeral).queue();
                                })
                                .exceptionally(throwable -> {
                                        Logger.error("Unexpected error in stats command for user " + userId, throwable);
                                        sendErrorEmbed(interaction,
                                                        "An unexpected error occurred. Please try again later.", true);
                                        return null;
                                });
        }

        // Data loading with proper error handling
        private DataLoadResult loadUserData(String userId) {
                try {
                        Optional<GBFUser> maybeUser = userService.findByUserID(userId);
                        if (maybeUser.isEmpty()) {
                                return DataLoadResult.error(
                                                "You don't have a GBF Timers account yet. Use `/timer register` to create one.");
                        }

                        TimerData timerData = timerDataService.findAll().stream()
                                        .filter(td -> td != null && td.getAccount() != null
                                                        && userId.equals(td.getAccount().getUserID()))
                                        .findFirst()
                                        .orElse(null);

                        if (timerData == null) {
                                return DataLoadResult.error(
                                                "You don't have timer data. Use `/timer register` to create an account.");
                        }

                        return DataLoadResult.success(timerData, maybeUser.get());

                } catch (Exception e) {
                        Logger.error("Error loading user data for userId: " + userId, e);
                        return DataLoadResult.error("Failed to load your data. Please try again later.");
                }
        }

        // Optimized embed building with better organization
        private EmbedBuilder buildStatsEmbed(TimerStats stats, String username) {
                TimerStats.OptimizedSummary summary = stats.snapshot();

                StringBuilder description = new StringBuilder(2000); // Pre-allocate reasonable size

                // Build sections
                addLifetimeSection(description, summary, stats);
                addSemesterSection(description, summary, stats);
                addStreakSection(description, stats);
                addRecordsSection(description, summary, stats);
                addBreaksSection(description, summary, stats);
                addSubjectsSection(description, summary, stats);
                addProgressSection(description, summary, stats);
                addGPASection(description, stats);

                return new EmbedBuilder()
                                .setTitle(username + " — Study Stats")
                                .setDescription(description.toString())
                                .setColor(ColorCodes.DEFAULT)
                                .setFooter("Stats for " + username)
                                .setTimestamp(Instant.now());
        }

        private void addLifetimeSection(StringBuilder desc, TimerStats.OptimizedSummary s, TimerStats stats) {
                desc.append("• Lifetime Study Time: ");
                if (s.totalStudyTimeMs() > 0) {
                        desc.append(stats.humanDuration(s.totalStudyTimeMs()))
                                        .append(" [")
                                        .append(String.format(Locale.US, "%,.0f", s.totalStudyTimeMs() / 3_600_000.0))
                                        .append(" hours]");
                } else {
                        desc.append("0s");
                }
                desc.append("\n\n");
        }

        private void addSemesterSection(StringBuilder desc, TimerStats.OptimizedSummary s, TimerStats stats) {
                if (s.semesterTimeMs() == 0)
                        return; // No semester data

                desc.append("• Semester Study Time: ")
                                .append(stats.humanDuration(s.semesterTimeMs()))
                                .append(" [")
                                .append(String.format(Locale.US, "%,.2f", s.semesterTimeMs() / 3_600_000.0))
                                .append(" hours]\n");

                // Average per 7 sessions
                int completeWeeks = s.sessionCount() / 7;
                if (completeWeeks > 0) {
                        long avgPer7 = s.semesterTimeMs() / completeWeeks;
                        desc.append("• Average per 7 sessions: ").append(stats.humanDuration(avgPer7)).append("\n");
                } else {
                        desc.append("• Average per 7 sessions: N/A (less than 7 sessions)\n");
                }

                desc.append("• Average Session Time: ")
                                .append(s.avgSessionMs() > 0 ? stats.humanDuration(s.avgSessionMs()) : "0s")
                                .append("\n");
                desc.append("• Total Sessions: ").append(s.sessionCount()).append("\n\n");
        }

        private void addStreakSection(StringBuilder desc, TimerStats stats) {
                desc.append("• Study Streak: ").append(stats.currentStreak()).append(" 🔥\n");
                desc.append("• Longest Study Streak: ").append(stats.longestStreak()).append(" 🔥\n\n");
        }

        private void addRecordsSection(StringBuilder desc, TimerStats.OptimizedSummary s, TimerStats stats) {
                desc.append("• Longest Session: ")
                                .append(s.longestSessionMs() > 0 ? stats.humanDuration(s.longestSessionMs()) : "0s")
                                .append("\n");

                stats.longestSemester().ifPresent(ls -> desc.append("• Longest Semester: ")
                                .append(stats.humanDuration(Math.round(ls.getSemesterTime() * 1000.0)))
                                .append(" [")
                                .append(String.format(Locale.US, "%,.2f", ls.getSemesterTime() / 3600.0))
                                .append(" hours] - [")
                                .append(ls.getSemesterName())
                                .append("]\n"));

                // Average start time
                stats.averageStartTimeUnixSeconds().ifPresentOrElse(
                                unix -> desc.append("• Average Start Time: <t:").append(unix).append(":t>\n"),
                                () -> desc.append("• Average Start Time: N/A\n"));
                desc.append("\n");
        }

        private void addBreaksSection(StringBuilder desc, TimerStats.OptimizedSummary s, TimerStats stats) {
                if (s.breakCount() == 0) {
                        desc.append("• No breaks taken this semester\n\n");
                        return;
                }

                desc.append("• Semester Break Time: ").append(stats.humanDuration(s.breakTimeMs())).append("\n");
                desc.append("• Total Breaks: ").append(s.breakCount()).append("\n");
                desc.append("• Average Break Time: ").append(stats.humanDuration(s.avgBreakMs())).append("\n\n");
        }

        private void addSubjectsSection(StringBuilder desc, TimerStats.OptimizedSummary s, TimerStats stats) {
                desc.append("• Total Subjects: ").append(s.subjectCount()).append("\n");
                desc.append("• Total study instances: ").append(s.totalTimesStudied()).append("\n");

                List<Subject> topSubjects = stats.topSubjects(5);
                if (!topSubjects.isEmpty()) {
                        desc.append("\n**Top Subjects**\n");
                        topSubjects.forEach(subject -> desc.append("• ").append(subject.getSubjectName())
                                        .append(" [").append(subject.getTimesStudied()).append("]\n"));
                } else {
                        desc.append("\n**Subject Stats**\nNo subjects studied\n");
                }
                desc.append("\n");
        }

        private void addProgressSection(StringBuilder desc, TimerStats.OptimizedSummary s, TimerStats stats) {
                if (s.semesterTimeMs() == 0)
                        return; // No semester progress to show

                // Semester Level
                desc.append(LevelEngine.rankUpEmoji(s.semesterLevel()))
                                .append(" Semester Level: ").append(s.semesterLevel()).append("\n")
                                .append("• XP to reach level ").append(s.semesterLevel() + 1).append(": ")
                                .append(String.format(Locale.US, "%,d", s.semesterXP())).append("/")
                                .append(String.format(Locale.US, "%,d", s.semesterXPRequired())).append("\n")
                                .append(stats.progressBarForLevel()).append(" [").append(s.semesterPercent())
                                .append("%]\n")
                                .append("• Time until next level: ").append(stats.humanDuration(stats.msToNextLevel()))
                                .append("\n\n");

                // Account Level
                desc.append(LevelEngine.rankUpEmoji(s.accountLevel()))
                                .append(" Account Level: ").append(s.accountLevel()).append("\n")
                                .append("• RP to reach level ").append(s.accountLevel() + 1).append(": ")
                                .append(String.format(Locale.US, "%,d", s.accountRP())).append("/")
                                .append(String.format(Locale.US, "%,d", s.accountRPRequired())).append("\n")
                                .append(stats.progressBarForRank()).append(" [").append(s.accountPercent())
                                .append("%]\n")
                                .append("• Time until next level: ").append(stats.humanDuration(stats.msToNextRank()))
                                .append("\n\n");
        }

        private void addGPASection(StringBuilder desc, TimerStats stats) {
                try {
                        double gpa = stats.gpaResult().gpa().doubleValue();
                        desc.append("• GPA: ").append(String.format(Locale.US, "%.3f", gpa));
                } catch (Exception e) {
                        desc.append("• GPA: N/A");
                }
        }

        private void sendErrorEmbed(SlashCommandInteractionEvent interaction, String message, boolean ephemeral) {
                EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("No Timers Account")
                                .setDescription(message)
                                .setColor(ColorCodes.DEFAULT)
                                .setTimestamp(Instant.now());
                interaction.replyEmbeds(embed.build()).setEphemeral(ephemeral).queue();
        }

        // Result holder for data loading
        private record DataLoadResult(TimerData timerData, GBFUser userData, String errorMessage) {
                public boolean isError() {
                        return errorMessage != null;
                }

                public static DataLoadResult success(TimerData timerData, GBFUser userData) {
                        return new DataLoadResult(timerData, userData, null);
                }

                public static DataLoadResult error(String message) {
                        return new DataLoadResult(null, null, message);
                }
        }
}
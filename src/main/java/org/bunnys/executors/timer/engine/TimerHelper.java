package org.bunnys.executors.timer.engine;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bunnys.database.models.timer.Semester;
import org.bunnys.database.models.timer.TimerData;
import org.bunnys.database.models.users.GBFUser;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class TimerHelper {
    public enum TimerButtonID {
        Start("startTimer"),
        Pause("pauseTimer"),
        Info("timerInfo"),
        Stop("stopTimer"),
        Unpause("unpauseTimer");

        private final String ID;

        TimerButtonID(String ID) {
            this.ID = ID;
        }

        public String getID() {
            return this.ID;
        }
    }

    public enum TimerEventsReturns {
        TimerAlreadyRunning("You have an active session."),
        TimerStarted("Timer Started"),
        TimerAlreadyPaused("The timer is already paused, stop it before starting a new break."),
        TimerNotStarted("You don't have an active session."),
        TimerNotPaused("The timer is not paused."),
        CannotStopPaused("You cannot end the session when the timer is paused.");

        private final String message;

        TimerEventsReturns(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public enum ActivityType {
        SESSION,
        SEMESTER
    }

    public record ButtonFixerOptions(List<TimerButtonID> enabledButtons, boolean isPaused) {
    }

    public record RecordBrokenOptions(ActivityType type, SlashCommandInteraction interaction, Long sessionTime,
                                      Semester semester) {
    }

    public record LevelUpOptions(SlashCommandInteraction interaction, int levelUps, int carryOverXP,
                                 TimerData timerData, GBFUser userData) {
    }

    // --- Utility Functions --- //

    public static String formatHours(double hours) {
        int minutes = (int) Math.round(hours * 60);
        return minutes > 0
                ? minutes + "m (" + String.format("%.3f", hours) + " hours)"
                : String.format("%.3f", hours) + " hours";
    }

    public static ActionRow createTimerActionRow(Map<TimerButtonID, Boolean> disabledButtons, boolean isPaused) {
        return ActionRow.of(
                Button.secondary(TimerButtonID.Start.getID(), "🕛 Start Session")
                        .withDisabled(disabledButtons.getOrDefault(TimerButtonID.Start, false)),

                Button.secondary(isPaused ? TimerButtonID.Unpause.getID() : TimerButtonID.Pause.getID(),
                        isPaused ? "▶️ Unpause Timer" : "⏰ Pause Timer")
                        .withDisabled(disabledButtons.getOrDefault(
                                isPaused ? TimerButtonID.Unpause : TimerButtonID.Pause, false)),

                Button.secondary(TimerButtonID.Info.getID(), "ℹ️ Session Stats")
                        .withDisabled(disabledButtons.getOrDefault(TimerButtonID.Info, false)),

                Button.secondary(TimerButtonID.Stop.getID(), "🕧 End Session")
                        .withDisabled(disabledButtons.getOrDefault(TimerButtonID.Stop, false)));
    }

    public static String messageURL(String guildID, String channelID, String messageID) {
        return "https://discord.com/channels/" + guildID + "/" + channelID + "/" + messageID;
    }
}

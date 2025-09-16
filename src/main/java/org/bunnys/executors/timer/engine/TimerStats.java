package org.bunnys.executors.timer.engine;

import org.bunnys.database.models.timer.Account;
import org.bunnys.database.models.timer.Semester;
import org.bunnys.database.models.timer.Session;
import org.bunnys.database.models.timer.TimerData;
import org.bunnys.database.models.timer.Subject;
import org.bunnys.database.models.users.GBFUser;
import org.bunnys.handler.utils.handler.Emojis;
import org.bunnys.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Professional timer/statistics helper for the timer subsystem.
 * All time-returning methods are in milliseconds (ms) unless otherwise
 * documented.
 * Methods are null-safe and return sensible defaults (0 or Optional.empty())
 * when data is missing.
 */
@SuppressWarnings("unused")
public final class TimerStats {

    private final TimerData timerData;
    private final GBFUser userData;

    public TimerStats(TimerData timerData, GBFUser userData) {
        this.timerData = Objects.requireNonNull(timerData, "timerData cannot be null");
        this.userData = Objects.requireNonNull(userData, "userData cannot be null");
    }

    // -------------------------
    // Account / Semester basics
    // -------------------------

    /**
     * Lifetime study time in milliseconds (account.lifetimeTime is stored in
     * seconds).
     */
    public long totalStudyTimeMs() {
        return secondsToMs(safeDouble(() -> Optional.ofNullable(timerData.getAccount())
                .map(Account::getLifetimeTime)
                .orElse(0.0)));
    }

    /**
     * Current semester total time in milliseconds (semesterTime stored in seconds).
     */
    public long semesterTimeMs() {
        return secondsToMs(safeDouble(() -> Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getSemesterTime)
                .orElse(0.0)));
    }

    /** Number of recorded session starts in current semester. */
    public int sessionCount() {
        return Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getSessionStartTimes)
                .map(List::size)
                .orElse(0);
    }

    /** Average session time (ms). Returns 0 when no sessions. */
    public long averageSessionTimeMs() {
        int count = sessionCount();
        return count == 0 ? 0L : semesterTimeMs() / count;
    }

    // -------------------------
    // Breaks
    // -------------------------

    /** Total break time in milliseconds (totalBreakTime stored in seconds). */
    public long breakTimeMs() {
        return secondsToMs(safeDouble(() -> Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getTotalBreakTime)
                .orElse(0.0)));
    }

    /** Break count. */
    public int breakCount() {
        return Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getBreakCount)
                .orElse(0);
    }

    /** Average break time (ms). Returns 0 when no breaks. */
    public long averageBreakTimeMs() {
        int bc = breakCount();
        return bc == 0 ? 0L : breakTimeMs() / bc;
    }

    /** Average time between breaks = semesterTime / breakCount (ms). */
    public long averageTimeBetweenBreaksMs() {
        int bc = breakCount();
        return bc == 0 ? 0L : semesterTimeMs() / bc;
    }

    // -------------------------
    // Subjects
    // -------------------------

    /** Number of subjects listed in the current semester. */
    public int subjectCount() {
        return Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getSemesterSubjects)
                .map(List::size)
                .orElse(0);
    }

    /** Sorted subjects by timesStudied DESC. Safe empty list fallback. */
    public List<Subject> subjectsByTimesStudied() {
        return Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getSemesterSubjects)
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparingInt(Subject::getTimesStudied).reversed())
                .toList();
    }

    /** Total times studied across all subjects. */
    public int totalTimesStudied() {
        return subjectsByTimesStudied().stream().mapToInt(Subject::getTimesStudied).sum();
    }

    /**
     * Average study time per subject occurrence (ms). If no studied occurrences
     * returns 0.
     */
    public long averageStudyTimePerOccurrenceMs() {
        int total = totalTimesStudied();
        return total == 0 ? 0L : semesterTimeMs() / total;
    }

    /** Top N subjects (by times studied) */
    public List<Subject> topSubjects(int n) {
        if (n <= 0)
            return Collections.emptyList();
        return subjectsByTimesStudied().stream().limit(n).toList();
    }

    // -------------------------
    // Last session
    // -------------------------

    /** Last session topic or "No Data". */
    public String lastSessionTopic() {
        return Optional.ofNullable(timerData.getSessionData())
                .map(Session::getLastSessionTopic)
                .orElse("No Data");
    }

    /** Last recorded session time (ms). sessionTime stored in seconds. */
    public long lastSessionTimeMs() {
        return secondsToMs(Optional.ofNullable(timerData.getSessionData())
                .map(Session::getSessionTime).orElse(0.0));
    }

    /** Unix timestamp (seconds) for last session date, or empty if none. */
    public OptionalLong lastSessionDateUnixSeconds() {
        return Optional.ofNullable(timerData.getSessionData())
                .map(Session::getLastSessionDate)
                .map(Date::toInstant)
                .map(Instant::getEpochSecond)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    // -------------------------
    // Average start time
    // -------------------------

    /**
     * Average session start time as Unix seconds (suitable for Discord <t:...>).
     * Returns Optional.empty() if no starts recorded.
     */
    public OptionalLong averageStartTimeUnixSeconds() {
        List<Long> starts = Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getSessionStartTimes)
                .orElse(Collections.emptyList());
        if (starts.isEmpty())
            return OptionalLong.empty();
        long avg = (long) starts.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return OptionalLong.of(avg / 1000L);
    }

    /**
     * Average time per session (ms) — alias to averageSessionTimeMs for clarity.
     */
    public long averageTimePerSessionMs() {
        return averageSessionTimeMs();
    }

    // -------------------------
    // Levels & progress
    // -------------------------

    public int semesterLevel() {
        return Optional.ofNullable(timerData.getCurrentSemester()).map(Semester::getSemesterLevel).orElse(0);
    }

    public int semesterXP() {
        return Optional.ofNullable(timerData.getCurrentSemester()).map(Semester::getSemesterXP).orElse(0);
    }

    public int accountLevel() {
        return Optional.ofNullable(userData.getRank()).orElse(0);
    }

    public int accountRP() {
        return Optional.ofNullable(userData.getRP()).orElse(0);
    }

    /** Percentage toward next account rank (0-100). */
    public int percentageToNextRank() {
        return LevelEngine.safePercentage(accountRP(), LevelEngine.rpRequired(accountLevel() + 1));
    }

    /** Percentage toward next semester level (0-100). */
    public int percentageToNextLevel() {
        return LevelEngine.safePercentage(semesterXP(), LevelEngine.xpRequired(semesterLevel() + 1));
    }

    /**
     * Estimated milliseconds to next semester level (based on
     * LevelEngine.hoursRequired).
     */
    public long msToNextLevel() {
        int xpLeft = LevelEngine.xpRequired(semesterLevel() + 1) - semesterXP();
        double hours = LevelEngine.hoursRequired(Math.max(0, xpLeft));
        return (long) (hours * 60.0 * 60.0 * 1000.0);
    }

    /** Estimated milliseconds to next account rank. */
    public long msToNextRank() {
        int rpLeft = LevelEngine.rpRequired(accountLevel() + 1) - accountRP();
        double hours = LevelEngine.hoursRequired(Math.max(0, rpLeft));
        return (long) (hours * 60.0 * 60.0 * 1000.0);
    }

    /**
     * Human friendly progress bar using your emoji constants (3 segments by
     * default).
     */
    public String generateProgressBar(int percentageComplete, int totalSegments) {
        int clamped = Math.min(Math.max(percentageComplete, 0), 100);
        int filled = Math.round((clamped / 100.0f) * totalSegments);

        // Using the three-segment progress bar glyphs from Emojis
        String left = filled >= 1 ? Emojis.PROGRESS_BAR_LEFT_FULL : Emojis.PROGRESS_BAR_LEFT_EMPTY;
        String middle = filled >= 2 ? Emojis.PROGRESS_BAR_MIDDLE_FULL : Emojis.PROGRESS_BAR_MIDDLE_EMPTY;
        String right = filled >= 3 ? Emojis.PROGRESS_BAR_RIGHT_FULL : Emojis.PROGRESS_BAR_RIGHT_EMPTY;

        // If totalSegments isn't 3, build generic bars using middle full/empty pairs
        if (totalSegments == 3) {
            return left + middle + right;
        } else {
            StringBuilder sb = getStringBuilder(totalSegments, filled);
            return sb.toString();
        }
    }

    @NotNull
    private static StringBuilder getStringBuilder(int totalSegments, int filled) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalSegments; i++) {
            if (i == 0)
                sb.append(i < filled ? Emojis.PROGRESS_BAR_LEFT_FULL : Emojis.PROGRESS_BAR_LEFT_EMPTY);
            else if (i == totalSegments - 1)
                sb.append(i < filled ? Emojis.PROGRESS_BAR_RIGHT_FULL : Emojis.PROGRESS_BAR_RIGHT_EMPTY);
            else
                sb.append(i < filled ? Emojis.PROGRESS_BAR_MIDDLE_FULL : Emojis.PROGRESS_BAR_MIDDLE_EMPTY);
        }
        return sb;
    }

    /** Convenience: generate progress bar for semester level. */
    public String progressBarForLevel() {
        return generateProgressBar(percentageToNextLevel(), 3);
    }

    /** Convenience: generate progress bar for account rank. */
    public String progressBarForRank() {
        return generateProgressBar(percentageToNextRank(), 3);
    }

    // -------------------------
    // GPA
    // -------------------------

    public GradeEngine.GpaResult gpaResult() {
        return GradeEngine.calculateGPAFromGBF(userData.getSubjects());
    }

    // -------------------------
    // Records & streaks
    // -------------------------

    /**
     * Longest single session (ms) — longestSession stored in seconds. Returns 0
     * when missing.
     */
    public long longestSessionMs() {
        return secondsToMs(Optional.ofNullable(timerData.getCurrentSemester())
                .map(Semester::getLongestSession)
                .orElseGet(() -> (double) 0L));
    }

    /** Optional longest semester record (if present on Account). */
    public Optional<Semester> longestSemester() {
        return Optional.ofNullable(timerData.getAccount()).map(Account::getLongestSemester);
    }

    public int currentStreak() {
        return Optional.ofNullable(timerData.getCurrentSemester()).map(Semester::getStreak).orElse(0);
    }

    public int longestStreak() {
        return Optional.ofNullable(timerData.getCurrentSemester()).map(Semester::getLongestStreak).orElse(0);
    }

    // -------------------------
    // Summary DTO
    // -------------------------

    /**
     * Immutable summary snapshot for easy display. Times are ms-based, and
     * some fields are Optional (absent when no underlying data).
     */
    public record Summary(
            long totalStudyTimeMs,
            long semesterTimeMs,
            int sessionCount,
            long avgSessionMs,
            long longestSessionMs,
            long breakTimeMs,
            int breakCount,
            long avgBreakMs,
            int subjectCount,
            int totalTimesStudied,
            long avgStudyOccurrenceMs,
            OptionalLong avgStartDateUnixSec,
            int semesterLevel,
            int semesterXP,
            int accountLevel,
            int accountRP,
            int pctToNextLevel,
            int pctToNextRank) {
    }

    /** Build a snapshot summary of the important fields. */
    public Summary snapshot() {
        return new Summary(
                totalStudyTimeMs(),
                semesterTimeMs(),
                sessionCount(),
                averageSessionTimeMs(),
                longestSessionMs(),
                breakTimeMs(),
                breakCount(),
                averageBreakTimeMs(),
                subjectCount(),
                totalTimesStudied(),
                averageStudyTimePerOccurrenceMs(),
                averageStartTimeUnixSeconds(),
                semesterLevel(),
                semesterXP(),
                accountLevel(),
                accountRP(),
                percentageToNextLevel(),
                percentageToNextRank());
    }

    // -------------------------
    // Private helpers
    // -------------------------

    private static long secondsToMs(double seconds) {
        return Math.round(seconds * 1000.0);
    }

    private static long secondsToMs(long seconds) {
        return seconds * 1000L;
    }

    private static double safeDouble(SupplierWithException<Double> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    // -------------------------
    // Formatting helpers (human readable)
    // -------------------------

    /** Human-readable duration using Utils (delegates to Utils.formatDuration). */
    public String humanDuration(long ms) {
        return Utils.formatDuration(ms);
    }

    /** Discord-style timestamp for a date (useful when posting to Discord). */
    public String discordTimestampDate(long unixSeconds, char type) {
        return Utils.getTimestamp(Date.from(Instant.ofEpochSecond(unixSeconds)), type);
    }
}

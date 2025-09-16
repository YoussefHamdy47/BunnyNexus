package org.bunnys.executors.timer;

import org.bunnys.database.models.timer.*;
import org.bunnys.database.models.users.GBFUser;
import org.bunnys.database.services.TimerDataService;
import org.bunnys.database.services.GBFUserService;
import org.bunnys.executors.timer.engine.LevelEngine;
import org.bunnys.executors.timer.engine.TimerHelper;
import org.bunnys.executors.timer.engine.TimerStats;
import org.bunnys.handler.utils.handler.logging.Logger;
import org.bunnys.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * A core service class for managing all user-specific timer and progression
 * data. Optimized to work with the cached LevelEngine for improved performance
 * in XP/RP calculations and level progression.
 * </p>
 *
 * <p>
 * The design of this class follows the Builder pattern to ensure proper
 * initialization and state management. It provides a robust interface for
 * command handlers with optimized database operations and caching support.
 * </p>
 */
@SuppressWarnings("unused")
public final class Timers {

    private final String userID;

    private final TimerDataService timerDataService;
    private final GBFUserService gbfUserService;
    private final TimerEventPublisher eventPublisher;

    private TimerData timerData;
    private GBFUser userData;
    private TimerStats timerStats;

    private boolean isDataLoaded = false;

    private Timers(String userID,
            TimerDataService timerDataService,
            GBFUserService gbfUserService,
            TimerEventPublisher eventPublisher,
            TimerEvents timerEventsBean) {
        this.userID = Objects.requireNonNull(userID, "userID cannot be null");
        this.timerDataService = Objects.requireNonNull(timerDataService, "timerDataService cannot be null");
        this.gbfUserService = Objects.requireNonNull(gbfUserService, "gbfUserService cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
    }

    public static Timers create(String userID,
            TimerDataService timerDataSvc,
            GBFUserService userSvc,
            TimerEventPublisher eventPublisher,
            TimerEvents timerEventsBean,
            boolean initStats,
            boolean initEvents) {

        if (initEvents && timerEventsBean == null)
            throw new IllegalArgumentException("timerEventsBean must be provided when initEvents == true");

        Timers instance = new Timers(userID, timerDataSvc, userSvc, eventPublisher, timerEventsBean);
        instance.setUser();

        if (initStats)
            instance.timerStats = new TimerStats(instance.timerData, instance.userData);

        return instance;
    }

    private void setUser() {
        this.timerData = timerDataService.findAll().stream()
                .filter(td -> td != null && td.getAccount() != null
                        && userID.equals(td.getAccount().getUserID()))
                .findFirst()
                .orElse(null);
        this.userData = gbfUserService.findByUserID(userID).orElse(null);
        this.isDataLoaded = true;
    }

    private void ensureDataLoaded() {
        if (!isDataLoaded)
            throw new IllegalStateException(
                    "User data not loaded. Call create(...) to initialize Timers for userID: " + userID);
    }

    private void checkUser() {
        ensureDataLoaded();
        if (timerData == null && userData == null)
            throw new IllegalStateException("You don't have a GBF Timers account.");
    }

    private void checkSemester() {
        checkUser();
        if (timerData == null || timerData.getCurrentSemester() == null
                || timerData.getCurrentSemester().getSemesterName() == null)
            throw new IllegalStateException("You don't have an active semester");
    }

    // ---------------- Subjects (account vs semester) ----------------

    public void addSubjectAccount(org.bunnys.database.models.timer.Subject subject) {
        checkUser();
        Objects.requireNonNull(subject, "subject cannot be null");

        if (this.userData == null)
            this.userData = new GBFUser(userID);

        List<GBFUser.Subject> accountSubjects = Optional.ofNullable(userData.getSubjects()).orElseGet(ArrayList::new);

        boolean existsAccount = accountSubjects.stream()
                .anyMatch(s -> s.getSubjectName().equalsIgnoreCase(subject.getSubjectName()));

        if (existsAccount)
            throw new IllegalStateException("Subject '" + subject.getSubjectName() + "' already exists for userID: "
                    + userID + " in the account subjects list");

        boolean existsSemester = Optional.ofNullable(timerData)
                .map(TimerData::getCurrentSemester)
                .map(Semester::getSemesterSubjects)
                .orElse(Collections.emptyList())
                .stream()
                .anyMatch(s -> s.getSubjectName().equalsIgnoreCase(subject.getSubjectName()));

        GBFUser.Subject accountSubject = getSubject(subject, existsSemester);

        userData.getSubjects().add(accountSubject);
        gbfUserService.saveUser(userData);
    }

    private GBFUser.Subject getSubject(Subject subject, boolean existsSemester) {
        if (existsSemester)
            throw new IllegalStateException("Subject '" + subject.getSubjectName() + "' already exists for userID: "
                    + userID + " in the current semester");

        String grade = "N/A";
        Integer creditHours = subject.getCreditHours() != null ? subject.getCreditHours() : 1;
        return new GBFUser.Subject(
                subject.getSubjectName(),
                grade,
                subject.getSubjectCode(),
                creditHours);
    }

    public void addSubjectSemester(org.bunnys.database.models.timer.Subject subject) {
        checkSemester();
        Objects.requireNonNull(subject, "subject cannot be null");

        Semester cs = timerData.getCurrentSemester();

        boolean exists = cs.getSemesterSubjects().stream()
                .anyMatch(s -> Objects.equals(s.getSubjectCode(), subject.getSubjectCode()));

        if (exists)
            throw new IllegalStateException(
                    "Subject '" + subject.getSubjectCode() + "' already in the current semester");

        boolean existsInAccount = Optional.ofNullable(userData)
                .map(GBFUser::getSubjects)
                .orElse(Collections.emptyList())
                .stream()
                .anyMatch(s -> s.getSubjectName().equalsIgnoreCase(subject.getSubjectName()));

        if (existsInAccount)
            throw new IllegalStateException(
                    "Subject '" + subject.getSubjectName() + "' already exists in the account subjects list");

        cs.getSemesterSubjects().add(subject);
        timerDataService.save(timerData);
    }

    public void removeSubjectAccount(String subjectCode) {
        checkUser();
        if (subjectCode == null || subjectCode.isBlank())
            throw new IllegalArgumentException("subjectCode cannot be null/blank");

        List<GBFUser.Subject> list = Optional.ofNullable(userData.getSubjects()).orElse(Collections.emptyList());
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getSubjectCode().trim().equalsIgnoreCase(subjectCode.trim())) {
                index = i;
                break;
            }
        }
        if (index == -1)
            throw new IllegalStateException("You haven't registered '" + subjectCode + "' in your account.");

        list.remove(index);
        gbfUserService.saveUser(userData);
    }

    public void removeSubjectSemester(String subjectCode) {
        checkSemester();
        if (subjectCode == null || subjectCode.isBlank())
            throw new IllegalArgumentException("subjectCode cannot be null/blank");

        Semester cs = timerData.getCurrentSemester();
        int index = -1;
        for (int i = 0; i < cs.getSemesterSubjects().size(); i++) {
            if (cs.getSemesterSubjects().get(i).getSubjectCode().equalsIgnoreCase(subjectCode)) {
                index = i;
                break;
            }
        }
        if (index == -1)
            throw new IllegalStateException("You haven't registered '" + subjectCode + "' for this semester");

        cs.getSemesterSubjects().remove(index);
        timerDataService.save(timerData);
    }

    public String getSemesterName() {
        checkSemester();
        return timerData.getCurrentSemester().getSemesterName();
    }

    // ---------------- Registration flows ----------------

    public boolean register(String semesterName) {
        if (semesterName != null && semesterName.trim().isEmpty())
            throw new IllegalArgumentException("Semester name cannot be empty");

        try {
            if (userData == null)
                registerUser();
            if (semesterName != null)
                registerSemester(semesterName);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void registerUser() {
        if (this.userData != null)
            throw new IllegalStateException("An existing account already exists for user ID '" + userID + "'");

        GBFUser newUser = new GBFUser(userID);
        newUser.setSubjects(new ArrayList<>());
        newUser.setFriends(new ArrayList<>());
        this.userData = gbfUserService.saveUser(newUser);

        if (this.timerData == null) {
            TimerData td = new TimerData();
            Account account = new Account();
            account.setUserID(userID);
            td.setAccount(account);
            td.setCurrentSemester(new Semester());
            td.setSessionData(new Session());
            this.timerData = timerDataService.save(td);
        }
    }

    private void registerSemester(String semesterName) {
        checkUser();

        if (this.timerData == null) {
            TimerData td = new TimerData();
            Account account = new Account();
            account.setUserID(userID);
            td.setAccount(account);

            Semester s = createNewSemester(semesterName);
            td.setCurrentSemester(s);
            td.setSessionData(new Session());
            this.timerData = timerDataService.save(td);
            return;
        }

        if (this.timerData.getCurrentSemester() != null
                && this.timerData.getCurrentSemester().getSemesterName() != null)
            throw new IllegalStateException("Semester '" + this.timerData.getCurrentSemester().getSemesterName()
                    + "' is already active, end it before you can start a new one.");

        Semester resetSemester = createNewSemester(semesterName);
        this.timerData.setCurrentSemester(resetSemester);
        timerDataService.save(timerData);
        Logger.info("Registered semester '" + semesterName + "' for user " + userID);
    }

    @NotNull
    private static Semester createNewSemester(String semesterName) {
        Semester semester = new Semester();
        semester.setBreakCount(0);
        semester.setLongestSession(0L);
        semester.setSemesterLevel(1);
        semester.setSemesterName(semesterName);
        semester.setSemesterSubjects(new ArrayList<>());
        semester.setSemesterTime(0.0);
        semester.setSemesterXP(0);
        semester.setSessionStartTimes(new ArrayList<>());
        semester.setTotalBreakTime(0.0);
        semester.setLongestStreak(0);
        semester.setStreak(0);
        semester.setLastStreakUpdate(null);
        return semester;
    }

    // ---------------- Optimized statDisplay using LevelEngine caching
    // ----------------

    /**
     * Generates comprehensive study statistics with optimized calculations
     * using LevelEngine's caching system for improved performance.
     */
    public String statDisplay() {
        checkUser();
        TimerStats localStats = new TimerStats(timerData, userData);

        StringBuilder stats = new StringBuilder();
        String gap = "\n";
        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        // Lifetime stats
        long lifetimeMs = localStats.totalStudyTimeMs();
        stats.append("• Lifetime Study Time: ");
        if (lifetimeMs != 0) {
            stats.append(Utils.formatDuration(lifetimeMs))
                    .append(" [")
                    .append(nf.format((double) lifetimeMs / (1000.0 * 60.0 * 60.0)))
                    .append(" hours]");
        } else {
            stats.append("0s");
        }
        stats.append("\n");

        // Semester-specific stats
        if (hasSemester()) {
            appendSemesterStats(stats, localStats, nf, gap);
        }

        // Common stats
        stats.append("• Study Streak: ").append(localStats.currentStreak()).append(" 🔥\n");
        stats.append("• Longest Study Streak: ").append(localStats.longestStreak()).append(" 🔥\n");
        stats.append(gap);

        if (hasSemester()) {
            appendDetailedSemesterStats(stats, localStats, gap);
            appendLevelingStats(stats, localStats);
        }

        return stats.toString();
    }

    private boolean hasSemester() {
        return timerData.getCurrentSemester() != null && timerData.getCurrentSemester().getSemesterName() != null;
    }

    private void appendSemesterStats(StringBuilder stats, TimerStats localStats, NumberFormat nf, String gap) {
        long semMs = localStats.semesterTimeMs();
        stats.append("• Semester Study Time: ");
        if (semMs != 0) {
            stats.append(Utils.formatDuration(semMs))
                    .append(" [")
                    .append(nf.format((double) semMs / (1000.0 * 60.0 * 60.0)))
                    .append(" hours]");
        } else {
            stats.append("0s");
        }
        stats.append("\n");

        int sessions = localStats.sessionCount();
        double weeksCount = Math.max(1, Math.floor((double) sessions / 7.0));
        long avgPerWeekMs = (long) (semMs / weeksCount);
        stats.append("• Average Session Time / 7 Sessions: ");
        stats.append(avgPerWeekMs > 0
                ? Utils.formatDuration(avgPerWeekMs) + " [" + Utils.formatDuration(avgPerWeekMs) + "]"
                : "0s");
        stats.append("\n");

        stats.append("• Average Session Time: ").append(Utils.formatDuration(localStats.averageSessionTimeMs()))
                .append("\n");
        stats.append("• Total Sessions: ").append(sessions).append("\n");
        stats.append(gap);
    }

    private void appendDetailedSemesterStats(StringBuilder stats, TimerStats localStats, String gap) {
        double lsSeconds = timerData.getCurrentSemester().getLongestSession();
        String longestSession = lsSeconds > 0.0 ? Utils.formatDuration((long) (lsSeconds * 1000.0)) : "0s";
        stats.append("• Longest Session: ").append(longestSession).append("\n");

        Optional<Semester> longestSemester = localStats.longestSemester();
        longestSemester.ifPresent(s -> stats.append("• Longest Semester: ")
                .append(Utils.formatDuration((long) (s.getSemesterTime() * 1000.0)))
                .append(" [").append(s.getSemesterTime() / 3600.0).append(" hours] - [").append(s.getSemesterName())
                .append("]\n"));

        stats.append(gap);

        // Break stats
        stats.append("• Semester Break Time: ").append(Utils.formatDuration(localStats.breakTimeMs())).append("\n");
        stats.append("• Total Breaks: ").append(localStats.breakCount()).append("\n");
        stats.append("• Average Break Time: ").append(Utils.formatDuration(localStats.averageBreakTimeMs()))
                .append("\n");
        stats.append("• Average Time Between Breaks: ")
                .append(Utils.formatDuration(localStats.averageTimeBetweenBreaksMs())).append("\n");
        stats.append(gap);

        // Start time stats
        stats.append("• Average Start Time: ");
        localStats.averageStartTimeUnixSeconds()
                .ifPresentOrElse(sec -> stats.append("<t:").append(sec).append(":t>"),
                        () -> stats.append("N/A"));
        stats.append("\n").append(gap);

        // Subject stats
        appendSubjectStats(stats, localStats, gap);
    }

    private void appendSubjectStats(StringBuilder stats, TimerStats localStats, String gap) {
        stats.append("• Total Subjects: ").append(localStats.subjectCount()).append("\n");
        stats.append("• Total study instances across all subjects: ").append(localStats.totalTimesStudied())
                .append("\n");

        List<org.bunnys.database.models.timer.Subject> ordered = localStats.subjectsByTimesStudied();
        if (!ordered.isEmpty()) {
            stats.append("**\nSubject Stats**\n");
            ordered.forEach(s -> stats.append("• ").append(s.getSubjectName()).append(" [")
                    .append(s.getTimesStudied()).append("]\n"));
        } else {
            stats.append("**Subject Stats**\nN/A\n");
        }

        stats.append("• Average Study Time Per Subject: ")
                .append(Utils.formatDuration(localStats.averageStudyTimePerOccurrenceMs())).append("\n");
        stats.append(gap);
    }

    /**
     * Optimized leveling stats using LevelEngine's cached calculations
     */
    private void appendLevelingStats(StringBuilder stats, TimerStats localStats) {
        int semLevel = localStats.actualSemesterLevel();
        int semXP = localStats.actualSemesterXP();
        int accLevel = localStats.actualAccountLevel();
        int accRP = localStats.actualAccountRP();

        // Use LevelEngine's cached methods for requirements
        int xpToNextLevel = LevelEngine.xpRequired(semLevel + 1);
        int rpToNextLevel = LevelEngine.rpRequired(accLevel + 1);

        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        // Semester progression
        stats.append("Semester Level: ").append(semLevel).append("\n");
        stats.append("• XP to reach level ").append(semLevel + 1).append(": ")
                .append(nf.format(semXP))
                .append("/")
                .append(nf.format(xpToNextLevel))
                .append("\n");

        int pctSem = localStats.percentageToNextLevel();
        stats.append(localStats.progressBarForLevel()).append(" [").append(pctSem).append("%]\n");

        // Use LevelEngine's hoursRequired method for time calculations
        double hoursToNext = LevelEngine.hoursRequired(xpToNextLevel - semXP);
        stats.append("• Time until level ").append(semLevel + 1).append(": ")
                .append(Utils.formatDuration((long) (hoursToNext * 3600 * 1000))).append("\n");

        // Account progression
        stats.append("Account Level: ").append(accLevel).append("\n");
        stats.append("• RP to reach level ").append(accLevel + 1).append(": ")
                .append(nf.format(accRP))
                .append("/")
                .append(nf.format(rpToNextLevel))
                .append("\n");

        int pctAcc = localStats.percentageToNextRank();
        stats.append(localStats.progressBarForRank()).append(" [").append(pctAcc).append("%]\n");

        double hoursToNextRank = LevelEngine.hoursRequired(rpToNextLevel - accRP);
        stats.append("• Time until level ").append(accLevel + 1).append(": ")
                .append(Utils.formatDuration((long) (hoursToNextRank * 3600 * 1000))).append("\n");

        stats.append("\n");
        stats.append("• GPA: ");
        try {
            stats.append(localStats.gpaResult().gpa().setScale(3, java.math.RoundingMode.HALF_UP));
        } catch (Exception ignored) {
            stats.append("N/A");
        }
    }

    // ---------------- GPA menu ----------------

    public String GPAMenu() {
        checkUser();
        TimerStats tStats = new TimerStats(timerData, userData);

        Map<String, Integer> gradeOrder = new LinkedHashMap<>();
        gradeOrder.put("A+", 10);
        gradeOrder.put("A", 9);
        gradeOrder.put("A-", 8);
        gradeOrder.put("B+", 7);
        gradeOrder.put("B", 6);
        gradeOrder.put("B-", 5);
        gradeOrder.put("C+", 4);
        gradeOrder.put("C", 3);
        gradeOrder.put("C-", 2);
        gradeOrder.put("D+", 1);
        gradeOrder.put("D", 0);
        gradeOrder.put("F", -1);

        StringBuilder sb = new StringBuilder();
        Map<String, Integer> gradeCounts = new HashMap<>();

        List<GBFUser.Subject> subjects = new ArrayList<>(
                Optional.ofNullable(userData.getSubjects()).orElse(Collections.emptyList()));
        subjects.sort((a, b) -> Integer.compare(
                gradeOrder.getOrDefault(b.getGrade(), -1),
                gradeOrder.getOrDefault(a.getGrade(), -1)));

        for (GBFUser.Subject s : subjects) {
            sb.append("• ").append(s.getSubjectName()).append(" - ")
                    .append(s.getGrade() == null ? "N/A" : s.getGrade()).append("\n");
            if (s.getGrade() != null) {
                String g = s.getGrade().trim();
                gradeCounts.put(g, gradeCounts.getOrDefault(g, 0) + 1);
            }
        }

        try {
            double gpa = tStats.gpaResult().gpa().doubleValue();
            sb.append("• GPA: ").append(BigDecimal.valueOf(gpa).setScale(3, java.math.RoundingMode.HALF_UP))
                    .append("\n");
        } catch (Exception e) {
            sb.append("• GPA: N/A\n");
        }

        String gradeSummary = gradeCounts.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(gradeOrder.getOrDefault(e2.getKey(), -1),
                        gradeOrder.getOrDefault(e1.getKey(), -1)))
                .map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", "));

        if (!gradeSummary.isBlank())
            sb.append("• ").append(gradeSummary);

        return sb.toString();
    }

    // ---------------- Optimized end semester ----------------

    /**
     * Optimized end semester handling using LevelEngine's cached calculations
     * and new Result records for cleaner progression logic.
     */
    public String endSemester() {
        checkSemester();

        Semester semester = timerData.getCurrentSemester();

        double longestSessionSeconds = semester.getLongestSession();
        String longestSession = longestSessionSeconds > 0.0
                ? Utils.formatDuration((long) (longestSessionSeconds * 1000.0))
                : "0s";
        String totalTime = Utils.formatDuration((long) (semester.getSemesterTime() * 1000.0));
        String totalBreakTime = Utils.formatDuration((long) (semester.getTotalBreakTime() * 1000.0));
        int sessionCount = Optional.ofNullable(semester.getSessionStartTimes())
                .orElse(Collections.emptyList()).size();

        // Use LevelEngine's cached total calculation
        long totalSemesterXP = LevelEngine.calculateTotalSemesterXP(semester.getSemesterLevel())
                + semester.getSemesterXP();

        String recap = "• Total Time: " + totalTime + "\n" +
                "• Number of Sessions: " + sessionCount + "\n" +
                "• Total Break Time: " + totalBreakTime + "\n" +
                "• Longest Session: " + longestSession + "\n" +
                "• Semester Level: " + semester.getSemesterLevel() + "\n" +
                "• Semester XP: " + NumberFormat.getInstance(Locale.US).format(totalSemesterXP) + "\n" +
                "• Account XP Converted: " + NumberFormat.getInstance(Locale.US).format(totalSemesterXP) + "\n";

        // Check for longest semester record
        if (timerData.getAccount() != null && (timerData.getAccount().getLongestSemester() == null
                || timerData.getAccount().getLongestSemester().getSemesterTime() < semester.getSemesterTime())) {
            timerData.getAccount().setLongestSemester(semester);
            TimerEvents.TimerContext ctx = new TimerEvents.TimerContext(timerData, userData, userID, null, null, null);
            eventPublisher.publishRecordBroken(ctx, TimerHelper.ActivityType.SEMESTER,
                    (long) semester.getSemesterTime());
        }

        // Add semester time to account lifetime
        if (timerData.getAccount() != null) {
            double currLifetime = Optional.of(timerData.getAccount().getLifetimeTime()).orElse(0.0);
            timerData.getAccount().setLifetimeTime(currLifetime + semester.getSemesterTime());
        }

        // Optimized rank conversion using new Result record
        int safeRank = Optional.ofNullable(userData.getRank()).orElse(0);
        int safeRP = Optional.ofNullable(userData.getRP()).orElse(0);

        LevelEngine.RankResult rankResult = LevelEngine.checkRank(safeRank, safeRP, (int) totalSemesterXP);

        if (rankResult.hasRankedUp()) {
            userData.setRank(safeRank + rankResult.addedRanks());
            userData.setRP(rankResult.remainingRP());

            TimerEvents.TimerContext ctx = new TimerEvents.TimerContext(timerData, userData, userID, null, null, null);
            eventPublisher.publishRankUp(ctx, rankResult.addedRanks(), rankResult.remainingRP());
        } else {
            userData.setRP(safeRP + (int) totalSemesterXP);
        }

        // Reset semester
        Semester reset = createNewSemester(null);
        timerData.setCurrentSemester(reset);

        // Batch save operations
        timerDataService.save(timerData);
        gbfUserService.saveUser(userData);

        return recap;
    }
}
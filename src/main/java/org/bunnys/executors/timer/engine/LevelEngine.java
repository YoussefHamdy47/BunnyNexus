package org.bunnys.executors.timer.engine;

import org.bunnys.handler.utils.handler.Emojis;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class LevelEngine {
    private static final int MAX_RANK = 5000;

    /** XP required to reach the next level */
    public static int xpRequired(int level) {
        return level * 400 + (level - 1) * 200 - 300;
    }

    /** RP required to reach the next rank */
    public static int rpRequired(int rank) {
        return rank * 800 + (rank - 1) * 400 - 500;
    }

    /** Hours required to reach a certain XP (given 180 XP / 5 mins) */
    public static double hoursRequired(int XP) {
        double minutes = (5.0 * XP) / 180.0;
        return minutes / 60.0;
    }

    /** XP gained for time spent in minutes */
    public static int calculateXP(int minutes) {
        return (minutes / 5) * 180;
    }

    // --- DTO --- //
    public static class LevelResult {
        public boolean hasLeveledUp;
        public int addedLevels;
        public int remainingXP;

        public LevelResult(boolean hasLeveledUp, int addedLevels, int remainingXP) {
            this.hasLeveledUp = hasLeveledUp;
            this.addedLevels = addedLevels;
            this.remainingXP = remainingXP;
        }
    }

    public static class RankResult {
        public boolean hasRankedUp;
        public int addedLevels;
        public int remainingRP;

        public RankResult(boolean hasRankedUp, int addedLevels, int remainingRP) {
            this.hasRankedUp = hasRankedUp;
            this.addedLevels = addedLevels;
            this.remainingRP = remainingRP;
        }
    }

    // --- End of DTO --- //

    // --- Checkers --- //

    public static LevelResult checkLevel(int currentLevel, int currentXP, int addedXP) {
        int addedLevels = 0;
        int userXP = currentXP + addedXP;
        int requiredXP = xpRequired(currentLevel + addedLevels);

        while (userXP >= requiredXP) {
            userXP -= requiredXP;
            addedLevels++;

            if (currentLevel + addedLevels >= MAX_RANK) {
                addedLevels--;
                userXP += requiredXP;
                break;
            }

            requiredXP = xpRequired(currentLevel + addedLevels);
        }

        return new LevelResult(addedLevels > 0, addedLevels, userXP);
    }

    public static RankResult checkRank(int currentRank, int currentRP, int addedRP) {
        int addedLevels = 0;
        int userRP = currentRP + addedRP;
        int requiredRP = rpRequired(currentRank + addedLevels);

        while (userRP >= requiredRP) {
            userRP -= requiredRP;
            addedLevels++;

            if (currentRank + addedLevels >= MAX_RANK) {
                addedLevels--;
                userRP += requiredRP;
                break;
            }

            requiredRP = rpRequired(currentRank + addedLevels);
        }

        return new RankResult(addedLevels > 0, addedLevels, userRP);
    }

    // --- Calculators --- //
    public static int calculateTotalSemesterXP(int semesterLevel) {
        int totalXP = 0;

        for (int level = 1; level <= semesterLevel; level++)
            totalXP += level * 400 + (level - 1) * 200 - 300;

        return totalXP;
    }

    public static int calculateTotalAccountRP(int accountRank) {
        int totalRP = 0;

        for (int rank = 1; rank <= accountRank; rank++)
            totalRP += rank * 800 + (rank - 1) * 400 - 500;

        return totalRP;
    }

    public static int safePercentage(int value, int required) {
        if (required <= 0) return 0;
        double percentage = (value * 100.0) / required;
        return (int) Math.min(100, Math.max(0, Math.round(percentage)));
    }

    // --- Cosmetics --- //
    public static class EmojiRange {
        public int min;
        public int max;
        public String emoji;

        public EmojiRange(int min, int max, String emoji) {
            this.max = max;
            this.min = min;
            this.emoji = emoji;
        }
    }

    private static final List<EmojiRange> emojiRanges = new ArrayList<>();

    static {
        emojiRanges.add(new EmojiRange(0, 25, Emojis.DEFAULT_VERIFY));
        emojiRanges.add(new EmojiRange(26, 50, Emojis.BLACK_HEART_SPIN));
        emojiRanges.add(new EmojiRange(51, 100, Emojis.WHITE_HEART_SPIN));
        emojiRanges.add(new EmojiRange(101, 150, Emojis.PINK_HEART_SPIN));
        emojiRanges.add(new EmojiRange(151, 250, Emojis.RED_HEART_SPIN));
        emojiRanges.add(new EmojiRange(251, 1000, Emojis.PINK_CRYSTAL_HEART_SPIN));
        emojiRanges.add(new EmojiRange(1001, MAX_RANK, Emojis.DONUT_SPIN));
    }

    public static String rankUpEmoji(int level) {
        for (EmojiRange range : emojiRanges)
            if (level >= range.min && level <= range.max)
                return range.emoji;

        return Emojis.DEFAULT_VERIFY;
    }
}

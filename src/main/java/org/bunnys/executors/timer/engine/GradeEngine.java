package org.bunnys.executors.timer.engine;

import org.bunnys.database.models.timer.Subject;
import org.bunnys.database.models.users.GBFUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Engine for handling grade calculations and GPA computations
 * Provides thread-safe grade operations with precise decimal calculations
 */
@SuppressWarnings("unused")
public final class GradeEngine {
    // Grades that don't count towards GPA calculation
    private static final Set<Grade> NON_GPA_GRADES = EnumSet.of(Grade.W, Grade.P);

    public enum Grade {
        A_PLUS("A+", 4.0),
        A("A", 4.0),
        A_MINUS("A-", 3.7),
        B_PLUS("B+", 3.3),
        B("B", 3.0),
        B_MINUS("B-", 2.7),
        C_PLUS("C+", 2.3),
        C("C", 2.0),
        C_MINUS("C-", 1.7),
        D_PLUS("D+", 1.3),
        D("D", 1.0),
        F("F", 0.0),
        W("Withdraw", 0.0),
        P("Pass", 0.0);

        private final String displayName;
        private final BigDecimal gpaValue;

        Grade(String displayName, double gpaValue) {
            this.displayName = displayName;
            this.gpaValue = BigDecimal.valueOf(gpaValue);
        }

        public BigDecimal getGPAValue() {
            return gpaValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Parse grade from string representation
         */
        public static Optional<Grade> fromString(String gradeStr) {
            if (gradeStr == null || gradeStr.trim().isEmpty())
                return Optional.empty();

            String normalized = gradeStr.trim().toUpperCase();
            for (Grade grade : Grade.values())
                if (grade.name().replace("_", "").equals(normalized.replace("-", "").replace("+", "PLUS")) ||
                        grade.displayName.equals(gradeStr.trim()))
                    return Optional.of(grade);

            return Optional.empty();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record GpaResult(BigDecimal gpa, int totalCreditHours, int qualityPoints) {

        public double getGpaAsDouble() {
            return gpa.doubleValue();
        }

        public boolean hasValidGpa() {
            return totalCreditHours > 0;
        }
    }

    private GradeEngine() {
    }

    /**
     * Calculate GPA from a list of subjects with precise decimal arithmetic
     *
     * @param subjects List of subjects to calculate GPA for
     * @return GpaResult containing calculated GPA and metadata
     * @throws IllegalArgumentException if the subjects list is null
     */
    public static GpaResult calculateGPA(List<Subject> subjects) {
        if (subjects == null)
            throw new IllegalArgumentException("Subjects list cannot be null");

        BigDecimal totalQualityPoints = BigDecimal.ZERO;
        int totalCreditHours = 0;

        for (Subject subject : subjects) {
            if (!isValidForGpaCalculation(subject))
                continue;

            BigDecimal gradePoints = subject.getGrade().getGPAValue();
            BigDecimal creditHours = BigDecimal.valueOf(subject.getCreditHours());
            BigDecimal qualityPoints = gradePoints.multiply(creditHours);

            totalQualityPoints = totalQualityPoints.add(qualityPoints);
            totalCreditHours += subject.getCreditHours();
        }

        BigDecimal gpa = totalCreditHours == 0
                ? BigDecimal.ZERO
                : totalQualityPoints.divide(BigDecimal.valueOf(totalCreditHours), 3, RoundingMode.HALF_UP);

        return new GpaResult(gpa, totalCreditHours, totalQualityPoints.intValue());
    }

    /**
     * Calculate GPA with custom rounding precision
     */
    public static GpaResult calculateGPA(List<Subject> subjects, int decimalPlaces) {
        if (decimalPlaces < 0 || decimalPlaces > 10)
            throw new IllegalArgumentException("Decimal places must be between 0 and 10");

        GpaResult result = calculateGPA(subjects);
        BigDecimal roundedGpa = result.gpa().setScale(decimalPlaces, RoundingMode.HALF_UP);

        return new GpaResult(roundedGpa, result.totalCreditHours(), result.qualityPoints());
    }

    public static GpaResult calculateGPAFromGBF(List<GBFUser.Subject> subjects) {
        if (subjects == null) throw new IllegalArgumentException("Subjects list cannot be null");

        List<Subject> converted = subjects.stream()
                .map(s -> Grade.fromString(s.getGrade())
                        .map(g -> {
                            Subject subj = new Subject();
                            subj.setSubjectName(s.getSubjectName());
                            subj.setSubjectCode(s.getSubjectCode());
                            subj.setCreditHours(s.getCreditHours());
                            subj.setGrade(g);
                            return subj;
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        return calculateGPA(converted);
    }

    /**
     * Check if a subject should be included in GPA calculation
     */
    private static boolean isValidForGpaCalculation(Subject subject) {
        return subject != null
                && subject.getGrade() != null
                && !NON_GPA_GRADES.contains(subject.getGrade())
                && subject.getCreditHours() > 0;
    }

    /**
     * Get letter grade classification
     */
    public static String getGradeClassification(Grade grade) {
        if (grade == null)
            return "Unknown";

        return switch (grade) {
            case A_PLUS, A, A_MINUS -> "Excellent";
            case B_PLUS, B, B_MINUS -> "Good";
            case C_PLUS, C, C_MINUS -> "Satisfactory";
            case D_PLUS, D -> "Below Average";
            case F -> "Failing";
            case W -> "Withdrawn";
            case P -> "Pass";
        };
    }
}
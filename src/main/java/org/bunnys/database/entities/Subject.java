package org.bunnys.database.entities;

import org.bson.Document;

import java.util.Objects;

public class Subject {
    private String subjectName;
    private String grade;
    private int creditHours;
    private String subjectCode;

    public Subject() {}

    public Subject(String subjectName, String grade, int creditHours, String subjectCode) {
        this.subjectName = subjectName;
        this.grade = grade;
        this.creditHours = creditHours;
        this.subjectCode = subjectCode;
        validate();
    }

    public Document toDocument() {
        return new Document("subjectName", subjectName)
                .append("grade", grade)
                .append("creditHours", creditHours)
                .append("subjectCode", subjectCode);
    }

    public static Subject fromDocument(Document doc) {
        if (doc == null) return null;
        Subject subject = new Subject();
        subject.subjectName = doc.getString("subjectName");
        subject.grade = doc.getString("grade");
        subject.creditHours = doc.getInteger("creditHours", 0);
        subject.subjectCode = doc.getString("subjectCode");
        return subject;
    }

    public void validate() {
        if (subjectName == null || subjectName.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject name cannot be null or empty");
        }
        if (subjectName.length() > 100) {
            throw new IllegalArgumentException("Subject name cannot exceed 100 characters");
        }
        if (grade == null || grade.trim().isEmpty()) {
            throw new IllegalArgumentException("Grade cannot be null or empty");
        }
        if (subjectCode == null || subjectCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject code cannot be null or empty");
        }
        if (creditHours < 1) {
            throw new IllegalArgumentException("Credit hours must be at least 1");
        }
    }

    // Getters and setters
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
        validate();
    }

    public String getGrade() { return grade; }
    public void setGrade(String grade) {
        this.grade = grade;
        validate();
    }

    public int getCreditHours() { return creditHours; }
    public void setCreditHours(int creditHours) {
        this.creditHours = creditHours;
        validate();
    }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
        validate();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject subject = (Subject) o;
        return creditHours == subject.creditHours &&
                Objects.equals(subjectName, subject.subjectName) &&
                Objects.equals(grade, subject.grade) &&
                Objects.equals(subjectCode, subject.subjectCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectName, grade, creditHours, subjectCode);
    }

    @Override
    public String toString() {
        return "Subject{" +
                "subjectName='" + subjectName + '\'' +
                ", grade='" + grade + '\'' +
                ", creditHours=" + creditHours +
                ", subjectCode='" + subjectCode + '\'' +
                '}';
    }
}
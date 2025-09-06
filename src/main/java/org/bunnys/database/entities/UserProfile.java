package org.bunnys.database.entities;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserProfile extends BaseEntity {
    private String userId;
    private String username;
    private List<String> friends = new ArrayList<>();
    private boolean privateProfile = false;
    private int rank = 0;
    private int rp = 0;
    private List<Subject> subjects = new ArrayList<>();

    public UserProfile() {
        super();
    }

    public UserProfile(String userId, String username) {
        super();
        this.userId = userId;
        this.username = username;
        validate();
    }

    @Override
    public Document toDocument() {
        Document doc = super.toDocument();
        doc.put("userId", userId);
        doc.put("username", username);
        doc.put("friends", friends);
        doc.put("privateProfile", privateProfile);
        doc.put("rank", rank);
        doc.put("rp", rp);

        List<Document> subjectDocs = subjects.stream()
                .map(Subject::toDocument)
                .collect(Collectors.toList());
        doc.put("subjects", subjectDocs);

        return doc;
    }

    @Override
    public void fromDocument(Document document) {
        super.fromDocument(document);
        this.userId = document.getString("userId");
        this.username = document.getString("username");
        this.friends = document.getList("friends", String.class, new ArrayList<>());
        this.privateProfile = document.getBoolean("privateProfile", false);
        this.rank = document.getInteger("rank", 0);
        this.rp = document.getInteger("rp", 0);

        List<Document> subjectDocs = document.getList("subjects", Document.class, new ArrayList<>());
        this.subjects = subjectDocs.stream()
                .map(Subject::fromDocument)
                .collect(Collectors.toList());
    }

    public static UserProfile fromDocument(Document doc) {
        if (doc == null) return null;
        UserProfile user = new UserProfile();
        user.fromDocument(doc);
        return user;
    }

    @Override
    public void validate() {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (rank < 0 || rank > 5000) {
            throw new IllegalArgumentException("Rank must be between 0 and 5000");
        }
        if (rp < 0) {
            throw new IllegalArgumentException("RP cannot be negative");
        }
        // Validate all subjects
        subjects.forEach(Subject::validate);
    }

    // Business methods
    public void addFriend(String friendId) {
        if (friendId != null && !friends.contains(friendId)) {
            friends.add(friendId);
            updateTimestamp();
        }
    }

    public void removeFriend(String friendId) {
        if (friends.remove(friendId)) {
            updateTimestamp();
        }
    }

    public void addSubject(Subject subject) {
        if (subject != null) {
            subject.validate();
            subjects.add(subject);
            updateTimestamp();
        }
    }

    public void removeSubject(String subjectCode) {
        if (subjects.removeIf(s -> s.getSubjectCode().equals(subjectCode))) {
            updateTimestamp();
        }
    }

    public void updateRank(int newRank) {
        if (newRank < 0 || newRank > 5000) {
            throw new IllegalArgumentException("Rank must be between 0 and 5000");
        }
        this.rank = newRank;
        updateTimestamp();
    }

    public void updateRP(int newRP) {
        if (newRP < 0) {
            throw new IllegalArgumentException("RP cannot be negative");
        }
        this.rp = newRP;
        updateTimestamp();
    }

    // Getters and setters with validation
    public String getUserId() { return userId; }
    public void setUserId(String userId) {
        this.userId = userId;
        updateTimestamp();
        validate();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = username;
        updateTimestamp();
        validate();
    }

    public List<String> getFriends() { return new ArrayList<>(friends); }
    public void setFriends(List<String> friends) {
        this.friends = new ArrayList<>(friends != null ? friends : List.of());
        updateTimestamp();
    }

    public boolean isPrivateProfile() { return privateProfile; }
    public void setPrivateProfile(boolean privateProfile) {
        this.privateProfile = privateProfile;
        updateTimestamp();
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { updateRank(rank); }

    public int getRp() { return rp; }
    public void setRp(int rp) { updateRP(rp); }

    public List<Subject> getSubjects() { return new ArrayList<>(subjects); }
    public void setSubjects(List<Subject> subjects) {
        this.subjects = new ArrayList<>(subjects != null ? subjects : List.of());
        this.subjects.forEach(Subject::validate);
        updateTimestamp();
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", friendsCount=" + friends.size() +
                ", rank=" + rank +
                ", rp=" + rp +
                ", subjectsCount=" + subjects.size() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
package org.bunnys.database.models;

import org.bson.Document;

public class UserProfile {
    private String userId;
    private String username;

    public UserProfile(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    // --- Convert to/from MongoDB ---
    public Document toDocument() {
        return new Document("userId", userId)
                .append("username", username);
    }

    public static UserProfile fromDocument(Document doc) {
        if (doc == null) return null;
        return new UserProfile(
                doc.getString("userId"),
                doc.getString("username")
        );
    }

    // --- Getters/Setters ---
    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }
}

package org.bunnys.database.repositories;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bunnys.database.entities.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class UserRepository extends BaseMongoRepository<UserProfile> {

    public UserRepository(MongoDatabase database) {
        super(database, "users");
    }

    @Override
    protected UserProfile createEntity() {
        return new UserProfile();
    }

    // Custom query methods
    public CompletableFuture<Optional<UserProfile>> findByUserId(String userId) {
        return findOne((Document) eq("userId", userId));
    }

    public CompletableFuture<Optional<UserProfile>> findByUsername(String username) {
        return findOne((Document) eq("username", username));
    }

    public CompletableFuture<List<UserProfile>> findByRankRange(int minRank, int maxRank) {
        return findBy((Document) and(gte("rank", minRank), lte("rank", maxRank)));
    }

    public CompletableFuture<List<UserProfile>> findPublicProfiles() {
        return findBy((Document) eq("privateProfile", false));
    }

    public CompletableFuture<List<UserProfile>> findUsersFriends(String userId) {
        return findBy((Document) in("userId", userId)); // This would need the friend IDs list
    }

    // Custom update methods
    public CompletableFuture<Long> updateUserRank(String userId, int newRank) {
        Document filter = (Document) eq("userId", userId);
        Document update = (Document) combine(
                set("rank", newRank),
                set("updatedAt", java.time.Instant.now())
        );
        return updateMany(filter, update);
    }

    public CompletableFuture<Long> addFriendToUsers(List<String> userIds, String friendId) {
        Document filter = (Document) in("userId", userIds);
        Document update = (Document) combine(
                addToSet("friends", friendId),
                set("updatedAt", java.time.Instant.now())
        );
        return updateMany(filter, update);
    }

    public CompletableFuture<Long> removeFriendFromUsers(List<String> userIds, String friendId) {
        Document filter = (Document) in("userId", userIds);
        Document update = (Document) combine(
                pull("friends", friendId),
                set("updatedAt", java.time.Instant.now())
        );
        return updateMany(filter, update);
    }

    // Aggregation methods
    public CompletableFuture<Long> countByRank(int rank) {
        return count((Document) eq("rank", rank));
    }

    public CompletableFuture<Long> countPublicProfiles() {
        return count((Document) eq("privateProfile", false));
    }

    // Batch operations
    public CompletableFuture<Long> updateMultipleRanks(List<String> userIds, int rankIncrement) {
        Document filter = (Document) in("userId", userIds);
        Document update = (Document) combine(
                inc("rank", rankIncrement),
                set("updatedAt", java.time.Instant.now())
        );
        return updateMany(filter, update);
    }
}
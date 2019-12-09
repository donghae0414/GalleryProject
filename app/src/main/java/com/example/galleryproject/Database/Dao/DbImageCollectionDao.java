package com.example.galleryproject.Database.Dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.galleryproject.Database.Entity.DbImageCollection;
import com.example.galleryproject.Database.Entity.DbImageGroup;

import java.util.ArrayList;
import java.util.List;

@Dao
public abstract class DbImageCollectionDao {
    @Query("SELECT * FROM image_collection")
    public abstract List<DbImageCollection> getAll();

    @Query("SELECT * FROM image_collection ORDER BY start_time DESC LIMIT :count OFFSET :offset")
    public abstract List<DbImageCollection> getRange(int count, int offset);

    @Query("SELECT * FROM image_collection WHERE id = (:id)")
    public abstract List<DbImageCollection> loadAllById(int id);

    @Insert
    public abstract void insertAll(DbImageCollection... dbImageCollections);

    @Transaction
    public List<Long> insertWithGroups(int collectionId, List<DbImageGroup> groups) {
        List<Long> groupIds = new ArrayList<>();
        for (DbImageGroup group: groups) {
            group.setCollectionId(collectionId);
            Long groupId = insert(group);

            groupIds.add(groupId);
        }

        return groupIds;
    }

    @Query("UPDATE image_collection SET memo = :memo WHERE id = :id;")
    public abstract void updateMemo(int id, String memo);

    @Insert
    public abstract long insert(DbImageCollection dbImageCollection);

    @Insert
    public abstract long insert(DbImageGroup dbImageGroup);

    @Delete
    public abstract void delete(DbImageCollection dbImageCollection);
}
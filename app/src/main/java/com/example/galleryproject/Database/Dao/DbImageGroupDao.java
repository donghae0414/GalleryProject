package com.example.galleryproject.Database.Dao;

        import androidx.room.Dao;
        import androidx.room.Delete;
        import androidx.room.Insert;
        import androidx.room.Query;

        import com.example.galleryproject.Database.Entity.DbImageGroup;

        import java.util.List;

@Dao
public interface DbImageGroupDao {
    @Query("SELECT * FROM image_group")
    List<DbImageGroup> getAll();

    @Query("SELECT * FROM image_group WHERE collection_id = :collectionId")
    List<DbImageGroup> loadAllWithCollectionId(int collectionId);

    @Query("SELECT * FROM image_group WHERE id IN (:ids)")
    List<DbImageGroup> loadAllByIds(int[] ids);


    @Query("SELECT * FROM image_group WHERE id = (:id)")
    List<DbImageGroup> loadAllById(int id);

    @Insert
    void insertAll(DbImageGroup... dbImageGroups);

    @Delete
    void delete(DbImageGroup dbImageGroup);
}
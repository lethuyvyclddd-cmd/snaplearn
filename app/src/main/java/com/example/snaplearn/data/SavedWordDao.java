package com.example.snaplearn.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SavedWordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(SavedWord word);

    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    List<SavedWord> getAll();

    @Query("SELECT COUNT(*) FROM saved_words WHERE vocabIndex = :vocabIndex")
    int countByIndex(int vocabIndex);

    @Query("SELECT COUNT(*) FROM saved_words WHERE en = :en")
    int countByEn(String en);

    @Delete
    void delete(SavedWord word);

    /** Xóa sạch sổ từ vựng trên máy (dùng khi đăng xuất). */
    @Query("DELETE FROM saved_words")
    void clear();
}

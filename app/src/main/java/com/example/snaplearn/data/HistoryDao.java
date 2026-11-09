package com.example.snaplearn.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {
    @Insert
    long insert(HistoryItem item);

    @Query("SELECT * FROM history ORDER BY time DESC")
    List<HistoryItem> getAll();

    /** Các mục cũ hơn {@code keep} mục mới nhất (dùng để dọn lịch sử). */
    @Query("SELECT * FROM history ORDER BY time DESC LIMIT -1 OFFSET :keep")
    List<HistoryItem> getOlderThan(int keep);

    /** Khi người dùng chọn lại từ khác trong top 3 thì cập nhật dòng lịch sử của lần chụp đó. */
    @Query("UPDATE history SET vocabKey = :key, en = :en, vi = :vi, phonetic = :phonetic, "
            + "confidence = :confidence WHERE id = :id")
    void updateWord(long id, int key, String en, String vi, String phonetic, float confidence);

    /** Lấy 1 dòng lịch sử theo id (để đẩy bản cập nhật lên Firestore). */
    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    HistoryItem getById(long id);

    /** Kiểm tra 1 lần nhận dạng (theo thời điểm chụp) đã có trên máy chưa. */
    @Query("SELECT COUNT(*) FROM history WHERE time = :time")
    int countByTime(long time);

    @Delete
    void delete(HistoryItem item);

    @Delete
    void deleteAll(List<HistoryItem> items);

    @Query("DELETE FROM history")
    void clear();
}

package com.example.snaplearn.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Một lần nhận dạng trong lịch sử. */
@Entity(tableName = "history")
public class HistoryItem {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public int vocabKey;
    public String en;
    public String vi;
    public String phonetic;
    public String imagePath;
    public float confidence;
    public long time;
}
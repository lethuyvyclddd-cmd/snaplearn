package com.example.snaplearn.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Một từ đã lưu vào sổ từ vựng. */
@Entity(tableName = "saved_words", indices = {@Index(value = {"vocabIndex"}, unique = true)})
public class SavedWord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public int vocabIndex;
    public String en;
    public String vi;
    public String phonetic;
    public String example;
    public String imagePath;
    public long savedAt;
}

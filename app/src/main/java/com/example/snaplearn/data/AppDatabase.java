package com.example.snaplearn.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {SavedWord.class, HistoryItem.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** Luồng nền dùng chung cho mọi thao tác database (Room không cho chạy trên luồng chính). */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** Phiên bản 1 -> 2: thêm bảng lịch sử, giữ nguyên sổ từ vựng đã lưu. */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `history` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`vocabKey` INTEGER NOT NULL, "
                    + "`en` TEXT, `vi` TEXT, `phonetic` TEXT, `imagePath` TEXT, "
                    + "`confidence` REAL NOT NULL, "
                    + "`time` INTEGER NOT NULL)");
        }
    };

    private static volatile AppDatabase instance;

    public abstract SavedWordDao savedWordDao();

    public abstract HistoryDao historyDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, "snaplearn.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
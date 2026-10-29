package com.example.wspro;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ScheduleItem.class, AssignmentItem.class, SyncChange.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schedule_table ADD COLUMN cloudId TEXT");
            database.execSQL("ALTER TABLE schedule_table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE assignment_table ADD COLUMN cloudId TEXT");
            database.execSQL("ALTER TABLE assignment_table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_table_cloudId ON schedule_table(cloudId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_assignment_table_cloudId ON assignment_table(cloudId)");
            database.execSQL("CREATE TABLE IF NOT EXISTS pending_sync_changes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "entityType TEXT NOT NULL, localId INTEGER NOT NULL, "
                    + "operation TEXT NOT NULL, queuedAt INTEGER NOT NULL, "
                    + "isOffline INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_sync_changes_entityType_localId "
                    + "ON pending_sync_changes(entityType, localId)");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE pending_sync_changes ADD COLUMN isOffline INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public abstract ScheduleDao scheduleDao();
    public abstract AssignmentDao assignmentDao();
    public abstract SyncChangeDao syncChangeDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "work_study_pro_db")
                            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

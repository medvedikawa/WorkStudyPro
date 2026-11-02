package com.example.wspro;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {ScheduleItem.class, AssignmentItem.class, SyncChange.class, UserProfile.class, NotificationItem.class},
        version = 8,
        exportSchema = false
)
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

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS user_profile ("
                    + "userId TEXT NOT NULL PRIMARY KEY, "
                    + "name TEXT, email TEXT, course TEXT, "
                    + "avatarPath TEXT, avatarUrl TEXT, "
                    + "updatedAt INTEGER NOT NULL DEFAULT 0)");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS notifications ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "type TEXT, "
                    + "title TEXT, "
                    + "message TEXT, "
                    + "createdAt INTEGER NOT NULL, "
                    + "isRead INTEGER NOT NULL DEFAULT 0, "
                    + "relatedId TEXT, "
                    + "dedupeKey TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notifications_dedupeKey ON notifications(dedupeKey)");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schedule_table ADD COLUMN groupId TEXT");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_table_groupId ON schedule_table(groupId)");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public abstract ScheduleDao scheduleDao();
    public abstract AssignmentDao assignmentDao();
    public abstract SyncChangeDao syncChangeDao();
    public abstract UserProfileDao userProfileDao();
    public abstract NotificationDao notificationDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "work_study_pro_db")
                            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

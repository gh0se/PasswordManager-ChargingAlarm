package com.example.passwordmanager.data.model.databae;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.passwordmanager.data.model.Website;
import com.example.passwordmanager.data.model.dao.WebsiteDao;

@Database(entities = {Website.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract WebsiteDao websiteDao();

    private static volatile AppDatabase INSTANCE;
    private static final String DATABASE_NAME = "app_database";

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}




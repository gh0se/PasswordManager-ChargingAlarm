package com.example.passwordmanager.data.model.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.passwordmanager.data.model.Website;

import java.util.List;

@Dao
public interface WebsiteDao {

    @Query("SELECT * FROM websites WHERE website_name LIKE :filter")
    List<Website> getAllWebsites(String filter);

    @Insert
    void insert(Website website);

    @Delete
    void delete(Website website);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Website> websites);

    @Update
    void update(Website website);
}


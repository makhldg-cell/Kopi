package com.example.kopi;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface GoalDao {
    @Insert
    void insert(Goal goal);

    @Update
    void update(Goal goal);

    @Query("SELECT * FROM goals")
    List<Goal> getAllGoals();

    @Query("SELECT * FROM goals WHERE id = :id")
    Goal getGoalById(int id);
}

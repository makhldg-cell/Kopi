package com.example.kopi;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface GoalDao {
    @Insert
    long insert(Goal goal);

    @Update
    void update(Goal goal);

    @Query("SELECT * FROM goals")
    List<Goal> getAllGoals();

    @Query("SELECT * FROM goals WHERE id = :id")
    Goal getGoalById(int id);

    @Query("DELETE FROM goals WHERE id = :goalId")
    void deleteGoal(int goalId);

    @Query("DELETE FROM payments WHERE goalId = :goalId")
    void deletePaymentsForGoal(int goalId);

    @Insert
    void insertPayment(Payment payment);

    @Update
    void updatePayment(Payment payment);

    @Query("DELETE FROM payments WHERE id = :paymentId")
    void deletePayment(Payment payment);

    @Query("SELECT * FROM payments WHERE goalId = :goalId ORDER BY id ASC")
    List<Payment> getPaymentsForGoal(int goalId);
}

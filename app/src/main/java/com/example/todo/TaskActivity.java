package com.example.todo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TaskActivity extends AppCompatActivity {

    private LinearLayout taskContainer;
    private DatabaseReference taskDatabaseRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        taskContainer = findViewById(R.id.taskContainer);
        Button addTaskButton = findViewById(R.id.buttonAddTask);
        Button signOutButton = findViewById(R.id.buttonsignOut);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in. Redirecting to login page...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(TaskActivity.this, LoginActivity.class));
            finish();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        taskDatabaseRef = FirebaseDatabase.getInstance().getReference("tasks").child(userId);

        // Load tasks from Firebase
        loadTasksFromFirebase();

        // Handle Add Task button click
        addTaskButton.setOnClickListener(v -> startActivity(new Intent(TaskActivity.this, AddTaskActivity.class)));

        // Handle Sign Out button click
        signOutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "Signed out successfully!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(TaskActivity.this, WelcomeActivity.class));
            finish();
        });
    }

    private void loadTasksFromFirebase() {
        taskDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                taskContainer.removeAllViews();  // Clear previous views

                for (DataSnapshot taskSnapshot : snapshot.getChildren()) {
                    Task task = taskSnapshot.getValue(Task.class);  // Map snapshot to Task object
                    if (task != null) {
                        addTaskToContainer(task, taskSnapshot.getKey());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TaskActivity.this, "Failed to load tasks: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addTaskToContainer(Task task, String taskId) {
        View taskView = getLayoutInflater().inflate(R.layout.item_task, taskContainer, false);

        EditText titleEditText = taskView.findViewById(R.id.editTextTaskTitle);
        EditText descriptionEditText = taskView.findViewById(R.id.editTextTaskDescription);
        TextView dateTextView = taskView.findViewById(R.id.textViewTaskDate);
        TextView timeTextView = taskView.findViewById(R.id.textViewTaskTime);
        Button editButton = taskView.findViewById(R.id.buttonEditTask);
        Button completeButton = taskView.findViewById(R.id.buttonCompleteTask);

        // Set task data
        titleEditText.setText(task.getTitle());
        descriptionEditText.setText(task.getDescription());
        dateTextView.setText(task.getDate());
        timeTextView.setText(task.getTime());

        if ("completed".equals(task.getStatus())) {
            completeButton.setVisibility(View.GONE);
            titleEditText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            descriptionEditText.setTextColor(getResources().getColor(android.R.color.darker_gray));
            titleEditText.setEnabled(false);
            descriptionEditText.setEnabled(false);
        } else {
            editButton.setVisibility(View.VISIBLE);
            completeButton.setVisibility(View.VISIBLE);
        }

        // Handle Edit Task button click
        editButton.setOnClickListener(v -> {
            titleEditText.setEnabled(true);
            descriptionEditText.setEnabled(true);
            titleEditText.requestFocus();
        });

        // Handle Complete Task button click
        completeButton.setOnClickListener(v -> {
            taskDatabaseRef.child(taskId).child("status").setValue("completed")
                    .addOnCompleteListener(task1 -> {
                        if (task1.isSuccessful()) {
                            Toast.makeText(TaskActivity.this, "Task marked as completed", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TaskActivity.this, "Failed to mark task as completed", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Schedule alarm for the task if it hasn't been completed
        if ("pending".equals(task.getStatus())) {
            scheduleTaskAlarm(task, taskId);
        }

        taskContainer.addView(taskView);
    }

    private void scheduleTaskAlarm(Task task, String taskId) {
        try {
            String dateTimeString = task.getDate() + " " + task.getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Date date = sdf.parse(dateTimeString);

            if (date != null) {
                long taskTimeInMillis = date.getTime();
                taskDatabaseRef.child(taskId).child("scheduledTime").setValue(taskTimeInMillis);

                Intent intent = new Intent(this, AlarmReceiver.class);
                intent.putExtra("taskId", taskId);
                intent.putExtra("taskTitle", task.getTitle());
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        taskId.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                try {
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    if (alarmManager != null) {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, taskTimeInMillis, pendingIntent);
                    }
                } catch (SecurityException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error scheduling alarm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

            }
        } catch (Exception e) {
            Log.e("TaskActivity", "Error scheduling alarm", e);
            Toast.makeText(this, "Error scheduling alarm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }
}

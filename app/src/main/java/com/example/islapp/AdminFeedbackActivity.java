package com.example.islapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminFeedbackActivity extends AppCompatActivity {

    ListView feedbackList;
    FirebaseFirestore fStore;
    DatabaseHelper db;
    private HandLandmarker handLandmarker;

    private String selectedDocId;
    private String selectedUser;
    private String selectedWord;
    private Uri tempVideoUri;

    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_feedback);

        fStore = FirebaseFirestore.getInstance();
        db = new DatabaseHelper(this);
        feedbackList = findViewById(R.id.feedbackList);

        initHandLandmarker(); // Initialize AI for landmark extraction
        setupCamera();
        loadFeedbackFromCloud();

        feedbackList.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, String> item = (Map<String, String>) parent.getItemAtPosition(position);
            selectedDocId = item.get("doc_id");
            selectedUser = item.get("username");
            selectedWord = item.get("message");
            showActionDialog();
        });
    }

    private void initHandLandmarker() {
        try {
            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(com.google.mediapipe.tasks.core.BaseOptions.builder()
                            .setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.IMAGE)
                    .build();
            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e("AI_ADMIN", "Landmarker init failed", e);
        }
    }

    private void loadFeedbackFromCloud() {
        fStore.collection("feedback").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Map<String, String>> dataList = new ArrayList<>();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("message", doc.getString("message"));
                    item.put("username", doc.getString("username"));
                    item.put("doc_id", doc.getId());
                    dataList.add(item);
                }
                String[] from = {"message", "username"};
                int[] to = {android.R.id.text1, android.R.id.text2};
                SimpleAdapter adapter = new SimpleAdapter(this, dataList, R.layout.item_feedback, from, to);
                adapter.setViewBinder((view, data, textRepresentation) -> {
                    if (view.getId() == android.R.id.text2) {
                        ((TextView) view).setText("Sent by: " + data);
                        return true;
                    }
                    return false;
                });
                feedbackList.setAdapter(adapter);
            }
        });
    }

    private void showActionDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_admin_review, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView title = view.findViewById(R.id.reviewWordTitle);
        TextView subtitle = view.findViewById(R.id.reviewUserSubtitle);
        Button btnReject = view.findViewById(R.id.btnReject);
        Button btnApprove = view.findViewById(R.id.btnApprove);

        title.setText(selectedWord);
        subtitle.setText("Requested by: " + selectedUser);

        btnReject.setOnClickListener(v -> {
            dialog.dismiss();
            deleteFeedbackFromCloud();
        });

        btnApprove.setOnClickListener(v -> {
            dialog.dismiss();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
            } else {
                openCamera();
            }
        });
        dialog.show();
    }

    private void setupCamera() {
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                tempVideoUri = result.getData().getData();
                saveVideoAndNotify();
            }
        });
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void saveVideoAndNotify() {
        try {
            String fileName = "video_" + System.currentTimeMillis() + ".mp4";
            FileOutputStream fos = openFileOutput(fileName, Context.MODE_PRIVATE);
            InputStream is = getContentResolver().openInputStream(tempVideoUri);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) fos.write(buffer, 0, len);
            fos.close();
            is.close();
            String path = getFileStreamPath(fileName).getAbsolutePath();

            // 1. EXTRACT LANDMARKS FROM THE RECORDED VIDEO
            String landmarkString = extractLandmarksFromVideo(path);

            // 2. SAVE TO LOCAL DB (Fix for the 4 arguments error)
            db.addGesture(selectedWord, "custom", path, landmarkString);

            // 3. CLOUD UPDATES
            Map<String, Object> notif = new HashMap<>();
            notif.put("target_user", selectedUser);
            notif.put("message", "The word '" + selectedWord + "' has been added.");
            notif.put("is_read", false);
            notif.put("timestamp", System.currentTimeMillis());
            fStore.collection("notifications").add(notif);
            deleteFeedbackFromCloud();

            Toast.makeText(this, "Word Added & AI Signature Saved!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String extractLandmarksFromVideo(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            Bitmap frame = retriever.getFrameAtTime(0);
            if (frame != null && handLandmarker != null) {
                MPImage mpImage = new BitmapImageBuilder(frame).build();
                HandLandmarkerResult result = handLandmarker.detect(mpImage);
                if (result.landmarks() != null && !result.landmarks().isEmpty()) {
                    List<NormalizedLandmark> hand = result.landmarks().get(0);
                    StringBuilder sb = new StringBuilder();
                    for (NormalizedLandmark pt : hand) {
                        sb.append(pt.x()).append(",").append(pt.y()).append("|");
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            Log.e("AI_EXTRACT", "Failed: " + e.getMessage());
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
        return null;
    }

    private void deleteFeedbackFromCloud() {
        fStore.collection("feedback").document(selectedDocId).delete()
                .addOnSuccessListener(aVoid -> loadFeedbackFromCloud());
    }
}
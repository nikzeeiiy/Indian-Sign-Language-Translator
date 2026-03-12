
        package com.example.islapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class AddVideoActivity extends AppCompatActivity {

    EditText inputWordName;
    VideoView videoPreview;
    Button btnRecord, btnUpload, btnSave;

    private Uri videoUri;
    private DatabaseHelper db;
    private HandLandmarker handLandmarker;

    private ActivityResultLauncher<Intent> recordLauncher;
    private ActivityResultLauncher<String> uploadLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_video);

        db = new DatabaseHelper(this);
        initHandLandmarker();

        inputWordName = findViewById(R.id.inputWordName);
        videoPreview = findViewById(R.id.videoPreview);
        btnRecord = findViewById(R.id.btnRecord);
        btnUpload = findViewById(R.id.btnUpload);
        btnSave = findViewById(R.id.btnSave);

        recordLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        videoUri = result.getData().getData();
                        playPreview();
                    }
                });

        uploadLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        videoUri = uri;
                        playPreview();
                    }
                });

        btnRecord.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, 100);
            } else {
                Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                recordLauncher.launch(intent);
            }
        });

        btnUpload.setOnClickListener(v -> uploadLauncher.launch("video/*"));
        btnSave.setOnClickListener(v -> saveToDatabase());
    }

    private void initHandLandmarker() {
        try {
            HandLandmarker.HandLandmarkerOptions options =
                    HandLandmarker.HandLandmarkerOptions.builder()
                            .setBaseOptions(
                                    com.google.mediapipe.tasks.core.BaseOptions.builder()
                                            .setModelAssetPath("hand_landmarker.task")
                                            .build())
                            .setRunningMode(RunningMode.IMAGE)
                            .build();

            handLandmarker = HandLandmarker.createFromOptions(this, options);

        } catch (Exception e) {
            Log.e("AI_EXTRACT", "Failed to init landmarker", e);
        }
    }

    private void playPreview() {
        if (videoUri != null) {
            videoPreview.setVideoURI(videoUri);
            videoPreview.setOnPreparedListener(mp -> mp.setLooping(true));
            videoPreview.start();
        }
    }

    private void saveToDatabase() {

        String word = inputWordName.getText().toString().trim();

        if (word.isEmpty() || videoUri == null) {
            Toast.makeText(this,
                    "Enter word and select video",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String landmarkString = extractLandmarksFromVideo(videoUri);

        if (landmarkString == null) {
            Toast.makeText(this,
                    "AI could not find a hand in this video. Try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String savedPath = copyVideoToInternalStorage(videoUri);

        if (savedPath == null) {
            Toast.makeText(this,
                    "Error saving video file",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success =
                db.addGesture(word, "custom", savedPath, landmarkString);

        if (success) {
            Toast.makeText(this,
                    "Saved with AI Signature!",
                    Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this,
                    "Error saving to database",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String copyVideoToInternalStorage(Uri uri) {

        try {

            String fileName = "video_" + System.currentTimeMillis() + ".mp4";
            File file = new File(getFilesDir(), fileName);

            InputStream inputStream =
                    getContentResolver().openInputStream(uri);

            FileOutputStream outputStream =
                    new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            return file.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String extractLandmarksFromVideo(Uri uri) {

        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        try {

            retriever.setDataSource(this, uri);

            Bitmap frame = retriever.getFrameAtTime(0);

            if (frame != null && handLandmarker != null) {

                MPImage mpImage =
                        new BitmapImageBuilder(frame).build();

                HandLandmarkerResult result =
                        handLandmarker.detect(mpImage);

                if (result.landmarks() != null &&
                        !result.landmarks().isEmpty()) {

                    List<NormalizedLandmark> hand =
                            result.landmarks().get(0);

                    StringBuilder sb = new StringBuilder();

                    for (NormalizedLandmark pt : hand) {
                        sb.append(pt.x())
                                .append(",")
                                .append(pt.y())
                                .append("|");
                    }

                    return sb.toString();
                }
            }

        } catch (Exception e) {
            Log.e("AI_EXTRACT",
                    "Error extracting: " + e.getMessage());
        }

        finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }

        return null;
    }
}


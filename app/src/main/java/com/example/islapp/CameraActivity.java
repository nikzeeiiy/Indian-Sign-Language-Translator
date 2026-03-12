package com.example.islapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private PreviewView viewFinder;
    private TextView resultText;
    private HandLandmarker handLandmarker;
    private ExecutorService backgroundExecutor;
    private DatabaseHelper db;
    private volatile boolean isBusy = false;

    private List<String> sentenceList = new ArrayList<>();
    private String lastDetectedWord = "";
    private long lastDetectionTime = 0;

    // --- OPTIMIZATION CONSTANTS ---
    private static final float SIMILARITY_LIMIT = 0.45f; // Loosened for left/right hand flexibility
    private static final long DETECTION_COOLDOWN_MS = 2000;
    private static final int STABILITY_THRESHOLD = 3;
    private int stabilityCounter = 0;
    private String pendingWord = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        db = new DatabaseHelper(this);
        viewFinder = findViewById(R.id.viewFinder);
        resultText = findViewById(R.id.resultText);

        findViewById(R.id.btnDeleteLast).setOnClickListener(v -> {
            if (!sentenceList.isEmpty()) {
                sentenceList.remove(sentenceList.size() - 1);
                updateUI();
            }
        });

        findViewById(R.id.btnClearAll).setOnClickListener(v -> {
            sentenceList.clear();
            lastDetectedWord = "";
            updateUI();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        backgroundExecutor = Executors.newSingleThreadExecutor();
        setupHandLandmarker();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 101);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(backgroundExecutor, image -> {
                    if (!isBusy && handLandmarker != null) {
                        isBusy = true;
                        Bitmap bitmap = imageProxyToBitmap(image);
                        if (bitmap != null) {
                            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                            processInference(handLandmarker.detect(mpImage));
                        }
                        isBusy = false;
                    }
                    image.close();
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e("ISL_CAMERA", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processInference(HandLandmarkerResult result) {
        if (result.landmarks() != null && !result.landmarks().isEmpty()) {
            List<NormalizedLandmark> liveLandmarks = result.landmarks().get(0);
            String matchedWord = findBestMatchInDatabase(liveLandmarks);

            if (!matchedWord.equals("TRANSLATING...")) {
                if (matchedWord.equals(pendingWord)) {
                    stabilityCounter++;
                } else {
                    pendingWord = matchedWord;
                    stabilityCounter = 0;
                }

                if (stabilityCounter >= STABILITY_THRESHOLD) {
                    long currentTime = System.currentTimeMillis();
                    if (!matchedWord.equals(lastDetectedWord) || (currentTime - lastDetectionTime > DETECTION_COOLDOWN_MS)) {
                        sentenceList.add(matchedWord);
                        lastDetectedWord = matchedWord;
                        lastDetectionTime = currentTime;
                        updateUI();
                        stabilityCounter = 0;
                    }
                }
            }
        }
    }

    private String findBestMatchInDatabase(List<NormalizedLandmark> live) {
        Cursor cursor = db.getAllSignatures();
        String bestMatch = "TRANSLATING...";
        float lowestScore = SIMILARITY_LIMIT;

        if (cursor != null) {
            int wordIdx = cursor.getColumnIndex("word");
            int landIdx = cursor.getColumnIndex("landmarks");

            while (cursor.moveToNext()) {
                String word = cursor.getString(wordIdx);
                String savedLandmarks = cursor.getString(landIdx);

                if (savedLandmarks != null && !savedLandmarks.isEmpty()) {
                    // Check normal (Right Hand / Direct)
                    float score = compareSignatures(live, savedLandmarks, false);
                    // Check mirrored (Left Hand / Flipped)
                    float mirroredScore = compareSignatures(live, savedLandmarks, true);

                    float finalScore = Math.min(score, mirroredScore);

                    if (finalScore < lowestScore) {
                        lowestScore = finalScore;
                        bestMatch = word.toUpperCase();
                    }
                }
            }
            cursor.close();
        }
        return bestMatch;
    }

    private float compareSignatures(List<NormalizedLandmark> live, String saved, boolean isMirrored) {
        String[] sPoints = saved.split("\\|");
        if (sPoints.length < 21) return 100f;

        float diff = 0;
        try {
            // Reference: Wrist (0) to Middle Finger Base (9)
            float liveScale = (float) Math.sqrt(Math.pow(live.get(9).x() - live.get(0).x(), 2) +
                    Math.pow(live.get(9).y() - live.get(0).y(), 2));

            String[] p0 = sPoints[0].split(",");
            String[] p9 = sPoints[9].split(",");
            float savedScale = (float) Math.sqrt(Math.pow(Float.parseFloat(p9[0]) - Float.parseFloat(p0[0]), 2) +
                    Math.pow(Float.parseFloat(p9[1]) - Float.parseFloat(p0[1]), 2));

            if (liveScale == 0 || savedScale == 0) return 100f;

            for (int i = 0; i < 21; i++) {
                String[] sCoord = sPoints[i].split(",");

                // Get relative coordinates from wrist
                float lRelX = (live.get(i).x() - live.get(0).x()) / liveScale;
                // --- MIRROR LOGIC ---
                if (isMirrored) lRelX = -lRelX;

                float lRelY = (live.get(i).y() - live.get(0).y()) / liveScale;

                float sRelX = (Float.parseFloat(sCoord[0]) - Float.parseFloat(p0[0])) / savedScale;
                float sRelY = (Float.parseFloat(sCoord[1]) - Float.parseFloat(p0[1])) / savedScale;

                diff += Math.sqrt(Math.pow(lRelX - sRelX, 2) + Math.pow(lRelY - sRelY, 2));
            }
        } catch (Exception e) {
            return 100f;
        }
        return diff / 21;
    }

    private void updateUI() {
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            for (String s : sentenceList) sb.append(s).append(" ");
            resultText.setText(sb.toString().trim().isEmpty() ? "READY..." : sb.toString().trim());
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);

        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        // Mirror the preview for natural movement
        matrix.postScale(-1f, 1f, image.getWidth() / 2f, image.getHeight() / 2f);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void setupHandLandmarker() {
        backgroundExecutor.execute(() -> {
            try {
                handLandmarker = HandLandmarker.createFromOptions(this,
                        HandLandmarker.HandLandmarkerOptions.builder()
                                .setBaseOptions(com.google.mediapipe.tasks.core.BaseOptions.builder()
                                        .setModelAssetPath("hand_landmarker.task").build())
                                .setRunningMode(RunningMode.IMAGE)
                                .build());
            } catch (Exception e) {
                Log.e("AI_INIT", "HandLandmarker failed", e);
            }
        });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdown();
        if (handLandmarker != null) handLandmarker.close();
    }
}
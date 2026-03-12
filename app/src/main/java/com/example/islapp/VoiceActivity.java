package com.example.islapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

public class VoiceActivity extends AppCompatActivity {

    private TextView spokenText, statusText;
    private VideoView signVideoView;
    private FloatingActionButton micButton;
    private Button btnReplay;
    private DatabaseHelper db;

    private Queue<String> wordQueue = new LinkedList<>();
    private ArrayList<String> lastSentenceWords = new ArrayList<>();

    // Permission Request Code
    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);

        db = new DatabaseHelper(this);

        // Bind Views
        spokenText = findViewById(R.id.txtSpokenText);
        statusText = findViewById(R.id.statusText);
        signVideoView = findViewById(R.id.signVideoView);
        micButton = findViewById(R.id.btnMic);
        btnReplay = findViewById(R.id.btnReplay);

        // --- FIX 1: Check Permissions on Mic Click ---
        micButton.setOnClickListener(v -> {
            if (checkPermission()) {
                startVoiceInput();
            } else {
                requestPermission();
            }
        });

        // Video Completion Listener
        signVideoView.setOnCompletionListener(mp -> playNextVideo());

        // Replay Button Logic
        btnReplay.setOnClickListener(v -> {
            if (!lastSentenceWords.isEmpty()) {
                playSentence(lastSentenceWords);
            }
        });
    }

    // --- PERMISSION LOGIC START ---
    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(this, "Permission Denied. Cannot use voice.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    // --- PERMISSION LOGIC END ---

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a sentence...");

        try {
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String fullSentence = result.get(0).toLowerCase().trim();
                spokenText.setText(fullSentence);

                String[] words = fullSentence.split("\\s+");
                ArrayList<String> wordList = new ArrayList<>(Arrays.asList(words));

                // Save for Replay
                lastSentenceWords = new ArrayList<>(wordList);

                playSentence(wordList);
            }
        }
    }

    private void playSentence(ArrayList<String> words) {
        wordQueue.clear();
        wordQueue.addAll(words);

        signVideoView.setVisibility(View.VISIBLE);
        btnReplay.setVisibility(View.GONE); // Hide Replay during playback

        playNextVideo();
    }

    private void playNextVideo() {
        if (wordQueue.isEmpty()) {
            statusText.setText("Finished");
            btnReplay.setVisibility(View.VISIBLE); // Show Replay when done
            return;
        }

        String currentWord = wordQueue.poll();

        // 1. Check Raw (Built-in videos)
        String formattedWord = currentWord.replace(" ", "_");
        int resId = getResources().getIdentifier(formattedWord, "raw", getPackageName());

        if (resId != 0) {
            String videoPath = "android.resource://" + getPackageName() + "/" + resId;
            signVideoView.setVideoURI(Uri.parse(videoPath));
            signVideoView.start();
            statusText.setText("Playing: " + currentWord);
        } else {
            // 2. Check Database (Custom videos)
            String customVideoPath = db.getVideoUriForWord(currentWord);
            if (customVideoPath != null) {
                signVideoView.setVideoURI(Uri.parse(customVideoPath));
                signVideoView.start();
                statusText.setText("Playing: " + currentWord);
            } else {
                // 3. Skip (Word not found)
                statusText.setText("Skipping: " + currentWord);

                // Wait 1 second before skipping to next word
                new Handler().postDelayed(this::playNextVideo, 1000);
            }
        }
    }
}
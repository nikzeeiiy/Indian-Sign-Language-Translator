
        package com.example.islapp;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageDatabaseActivity extends AppCompatActivity {

    private ListView gestureListView;
    private DatabaseHelper db;//o

    private Uri tempVideoUri;
    private GestureItem selectedItemForUpdate;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    private HandLandmarker handLandmarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_database);

        db = new DatabaseHelper(this);

        gestureListView = findViewById(R.id.gestureListView);

        initHandLandmarker();

        setupLaunchers();

        insertDefaultGestures();

        gestureListView.setOnItemClickListener((parent, view, position, id) -> {

            GestureItem item = (GestureItem) parent.getItemAtPosition(position);

            showBottomSheetOptions(item);
        });

        loadGestures();
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

            Log.e("LANDMARK_INIT", e.toString());
        }
    }

    private void setupLaunchers() {

        cameraLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                        result -> {

                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                                tempVideoUri = result.getData().getData();

                                performUpdate();
                            }
                        });

        galleryLauncher =
                registerForActivityResult(new ActivityResultContracts.GetContent(),
                        uri -> {

                            if (uri != null) {

                                tempVideoUri = uri;

                                performUpdate();
                            }
                        });
    }

    private void insertDefaultGestures() {

        try {

            Field[] fields = R.raw.class.getFields();

            for (Field field : fields) {

                String name = field.getName().toLowerCase();

                if (db.gestureExists(name)) {
                    continue;
                }

                Uri uri = Uri.parse("android.resource://" + getPackageName() + "/raw/" + name);

                String landmarks = extractLandmarksFromVideo(uri);

                if (landmarks != null) {

                    db.addGesture(name, "default", uri.toString(), landmarks);
                }
            }

        } catch (Exception e) {

            Log.e("DEFAULT_INSERT", e.toString());
        }
    }

    private void performUpdate() {

        try {

            String fileName = "video_" + System.currentTimeMillis() + ".mp4";

            File file = new File(getFilesDir(), fileName);

            InputStream input = getContentResolver().openInputStream(tempVideoUri);

            FileOutputStream output = new FileOutputStream(file);

            byte[] buffer = new byte[4096];

            int length;

            while ((length = input.read(buffer)) > 0) {

                output.write(buffer, 0, length);
            }

            input.close();

            output.close();

            String absolutePath = file.getAbsolutePath();

            db.updateGestureVideo(selectedItemForUpdate.id, absolutePath);

            Toast.makeText(this, "Gesture Updated", Toast.LENGTH_SHORT).show();

            loadGestures();

        } catch (Exception e) {

            Log.e("UPDATE_ERROR", e.toString());
        }
    }

    private String extractLandmarksFromVideo(Uri uri) {

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {

            if (uri.toString().startsWith("android.resource://")) {

                int resId = getResources().getIdentifier(
                        uri.getLastPathSegment(),
                        "raw",
                        getPackageName());

                AssetFileDescriptor afd =
                        getResources().openRawResourceFd(resId);

                retriever.setDataSource(
                        afd.getFileDescriptor(),
                        afd.getStartOffset(),
                        afd.getLength());

            } else {

                retriever.setDataSource(this, uri);
            }

            Bitmap frame = retriever.getFrameAtTime(0);

            if (frame != null && handLandmarker != null) {

                MPImage mpImage = new BitmapImageBuilder(frame).build();

                HandLandmarkerResult result =
                        handLandmarker.detect(mpImage);

                if (!result.landmarks().isEmpty()) {

                    List<NormalizedLandmark> hand =
                            result.landmarks().get(0);

                    StringBuilder sb = new StringBuilder();

                    for (NormalizedLandmark p : hand) {

                        sb.append(p.x()).append(",").append(p.y()).append("|");
                    }

                    return sb.toString();
                }
            }

        } catch (Exception e) {

            Log.e("LANDMARK_EXTRACT", e.toString());
        }

        return null;
    }

    private void playVideoPreview(String path) {

        VideoView videoView = new VideoView(this);

        if (path.startsWith("/")) {

            videoView.setVideoPath(path);

        } else {

            videoView.setVideoURI(Uri.parse(path));
        }

        videoView.setOnPreparedListener(mp -> {

            mp.setLooping(true);

            videoView.start();
        });

        new AlertDialog.Builder(this)
                .setView(videoView)
                .show();
    }

    private void showBottomSheetOptions(GestureItem item) {

        BottomSheetDialog dialog = new BottomSheetDialog(this);

        View view =
                getLayoutInflater().inflate(R.layout.layout_bottom_sheet_video, null);

        dialog.setContentView(view);

        ((TextView) view.findViewById(R.id.sheetTitle)).setText(item.word);

        view.findViewById(R.id.btnSheetPlay).setOnClickListener(v -> {

            dialog.dismiss();

            playVideoPreview(item.uri);
        });

        view.findViewById(R.id.btnSheetUpdate).setOnClickListener(v -> {

            dialog.dismiss();

            selectedItemForUpdate = item;

            showSourceSelectionDialog();
        });

        view.findViewById(R.id.btnSheetDelete).setOnClickListener(v -> {

            dialog.dismiss();

            new AlertDialog.Builder(this)
                    .setTitle("Delete Gesture?")
                    .setPositiveButton("Delete", (d, w) -> {

                        db.deleteGesture(item.id);

                        loadGestures();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        dialog.show();
    }

    private void showSourceSelectionDialog() {

        String[] options = {"Record New", "Choose From Gallery"};

        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {

                        Intent intent =
                                new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

                        cameraLauncher.launch(intent);

                    } else {

                        galleryLauncher.launch("video/*");
                    }
                })
                .show();
    }

    private void loadGestures() {

        List<GestureItem> list = new ArrayList<>();

        Cursor cursor = db.getAllGesturesWithIds();

        if (cursor != null) {

            while (cursor.moveToNext()) {

                int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));

                String word = cursor.getString(cursor.getColumnIndexOrThrow("word"));

                String uri = cursor.getString(cursor.getColumnIndexOrThrow("video_uri"));

                list.add(new GestureItem(id, word, uri));
            }

            cursor.close();
        }

        Collections.sort(list,
                (o1, o2) -> o1.word.compareToIgnoreCase(o2.word));

        gestureListView.setAdapter(new GestureAdapter(this, list));
    }

    private static class GestureItem {

        int id;
        String word;
        String uri;

        GestureItem(int id, String word, String uri) {

            this.id = id;

            this.word = word;

            this.uri = uri;
        }
    }

    private class GestureAdapter extends ArrayAdapter<GestureItem> {

        public GestureAdapter(Context context, List<GestureItem> items) {

            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {

                convertView =
                        LayoutInflater.from(getContext())
                                .inflate(R.layout.item_gesture, parent, false);
            }

            GestureItem item = getItem(position);

            TextView txtWord =
                    convertView.findViewById(R.id.gestureWord);

            txtWord.setText(item.word);

            return convertView;
        }
    }
}


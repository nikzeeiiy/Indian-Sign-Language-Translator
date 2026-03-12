
        package com.example.islapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ISL_Project.db";
    private static final int DATABASE_VERSION = 6;

    private static final String TABLE_USERS = "users";
    private static final String TABLE_GESTURES = "gestures";
    private static final String TABLE_FEEDBACK = "feedback";
    private static final String TABLE_NOTIFICATIONS = "notifications";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE " + TABLE_USERS +
                " (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, password TEXT, role TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_GESTURES +
                " (id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT UNIQUE, gesture_label TEXT, video_uri TEXT, landmarks TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_FEEDBACK +
                " (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, message TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_NOTIFICATIONS +
                " (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, message TEXT, is_read INTEGER)");

        db.execSQL("INSERT INTO " + TABLE_USERS +
                " (username, password, role) VALUES ('admin', 'admin123', 'admin')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + TABLE_GESTURES + " ADD COLUMN landmarks TEXT");
        }
    }

    public boolean addGesture(String word, String label, String videoUri, String landmarkString) {

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put("word", word.toLowerCase().trim());
        values.put("gesture_label", label);
        values.put("video_uri", videoUri);
        values.put("landmarks", landmarkString);

        return db.insert(TABLE_GESTURES, null, values) != -1;
    }

    public boolean gestureExists(String word) {

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id FROM " + TABLE_GESTURES + " WHERE word=?",
                new String[]{word.toLowerCase().trim()});

        boolean exists = cursor.moveToFirst();

        cursor.close();

        return exists;
    }

    public String getVideoUriForWord(String word) {

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT video_uri FROM " + TABLE_GESTURES + " WHERE word=?",
                new String[]{word.toLowerCase().trim()});

        String uri = null;

        if (cursor.moveToFirst()) {
            uri = cursor.getString(0);
        }

        cursor.close();

        return uri;
    }

    public Cursor getAllSignatures() {

        SQLiteDatabase db = this.getReadableDatabase();

        return db.rawQuery(
                "SELECT word, landmarks FROM " + TABLE_GESTURES +
                        " WHERE landmarks IS NOT NULL",
                null);
    }

    public boolean updateGestureVideo(int id, String newUri) {

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put("video_uri", newUri);

        return db.update(TABLE_GESTURES,
                values,
                "id=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteGesture(int id) {

        SQLiteDatabase db = this.getWritableDatabase();

        return db.delete(TABLE_GESTURES,
                "id=?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public Cursor getAllGesturesWithIds() {

        SQLiteDatabase db = this.getReadableDatabase();

        return db.rawQuery(
                "SELECT id as _id, word, video_uri FROM " + TABLE_GESTURES,
                null);
    }

    public String checkLogin(String username, String password) {

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT role FROM " + TABLE_USERS +
                        " WHERE username=? AND password=?",
                new String[]{username, password});

        String role = "fail";

        if (cursor.moveToFirst()) {
            role = cursor.getString(0);
        }

        cursor.close();

        return role;
    }
}


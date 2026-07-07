package com.example.myapplication3.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLogger {
    private static final String TAG = "LightAutoClicker";
    private static final String LOG_FILE = "app-debug.log";
    private static final int MAX_LOG_CHARS = 20000;

    private AppLogger() {
    }

    public static void i(Context context, String message) {
        write(context, "INFO", message, null);
    }

    public static void e(Context context, String message, Throwable throwable) {
        write(context, "ERROR", message, throwable);
    }

    public static String read(Context context) {
        File file = getLogFile(context);
        if (!file.exists()) {
            return "暂无日志";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (IOException e) {
            return "读取日志失败：" + e.getMessage();
        }
        if (builder.length() > MAX_LOG_CHARS) {
            return builder.substring(builder.length() - MAX_LOG_CHARS);
        }
        return builder.toString();
    }

    public static void clear(Context context) {
        File file = getLogFile(context);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete app log file");
        }
    }

    private static void write(Context context, String level, String message, Throwable throwable) {
        String line = timestamp() + " " + level + " " + message;
        if (throwable == null) {
            Log.i(TAG, line);
        } else {
            Log.e(TAG, line, throwable);
        }
        File file = getLogFile(context);
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            writer.write('\n');
            if (throwable != null) {
                writer.write(throwable.getClass().getName());
                writer.write(": ");
                writer.write(String.valueOf(throwable.getMessage()));
                writer.write('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write app log file", e);
        }
    }

    private static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(new Date());
    }
}

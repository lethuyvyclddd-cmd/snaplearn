package com.example.snaplearn.util;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/** Đọc từ tiếng Anh bằng TextToSpeech. Dùng singleton để tránh tạo/shutdown liên tục. */
public class Speaker {

    private static final String TAG = "Speaker";
    private static Speaker instance;

    private TextToSpeech tts;
    private boolean ready = false;

    private Speaker(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.US);
                ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                if (!ready) Log.w(TAG, "Không hỗ trợ giọng tiếng Anh");
            } else {
                Log.w(TAG, "Khởi tạo TTS thất bại, status=" + status);
            }
        });
    }

    /** Lấy instance dùng chung cho toàn app */
    public static synchronized Speaker getInstance(Context context) {
        if (instance == null) {
            instance = new Speaker(context.getApplicationContext());
        }
        return instance;
    }

    /** @return false nếu máy chưa có giọng đọc tiếng Anh hoặc TTS chưa sẵn sàng */
    public boolean speak(String text) {
        if (!ready || tts == null || text == null || text.isEmpty()) return false;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "snaplearn");
        return true;
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    /**
     * Chỉ nên gọi khi app thật sự thoát (ví dụ MainActivity.onDestroy).
     * Không gọi trong onDestroy của các Activity khác.
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            ready = false;
            instance = null;
        }
    }
}
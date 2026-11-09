package com.example.snaplearn.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đồng bộ sổ từ vựng và lịch sử nhận dạng lên Cloud Firestore theo từng tài khoản.
 *
 * Cấu trúc trên Firestore:
 *   users/{uid}/words/{vocabIndex}   : từ đã lưu + ảnh thu nhỏ (chuỗi Base64)
 *   users/{uid}/history/{time}       : lần nhận dạng + ảnh thu nhỏ (chuỗi Base64)
 *
 * Room trên máy vẫn là nguồn dữ liệu chính (app chạy được cả khi mất mạng),
 * Firestore là bản sao lưu: ghi lên khi có thay đổi, kéo về khi đăng nhập.
 * Các hàm push/delete nên gọi từ luồng nền (AppDatabase.IO) vì có đọc file ảnh.
 */
public final class CloudSync {

    private static final String TAG = "CloudSync";
    private static final int THUMB_SIZE = 192;   // cạnh dài tối đa của ảnh gửi lên (px)
    private static final int THUMB_QUALITY = 75; // chất lượng JPEG

    private static volatile boolean synced = false;

    private CloudSync() {}

    private static DocumentReference userDoc() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return null;
        return FirebaseFirestore.getInstance().collection("users").document(u.getUid());
    }

    // ================================================================ Sổ từ vựng

    public static void pushWord(SavedWord w) {
        DocumentReference user = userDoc();
        if (user == null || w == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("vocabIndex", w.vocabIndex);
        m.put("en", w.en);
        m.put("vi", w.vi);
        m.put("phonetic", w.phonetic);
        m.put("example", w.example);
        m.put("savedAt", w.savedAt);
        m.put("thumb", thumbBase64(w.imagePath));
        user.collection("words").document(String.valueOf(w.vocabIndex)).set(m)
                .addOnFailureListener(e -> Log.w(TAG, "pushWord lỗi", e));
    }

    public static void deleteWord(SavedWord w) {
        DocumentReference user = userDoc();
        if (user == null || w == null) return;
        user.collection("words").document(String.valueOf(w.vocabIndex)).delete()
                .addOnFailureListener(e -> Log.w(TAG, "deleteWord lỗi", e));
    }

    // ================================================================ Lịch sử

    public static void pushHistory(HistoryItem h) {
        DocumentReference user = userDoc();
        if (user == null || h == null) return;
        Map<String, Object> m = historyFields(h);
        m.put("thumb", thumbBase64(h.imagePath));
        user.collection("history").document(String.valueOf(h.time)).set(m)
                .addOnFailureListener(e -> Log.w(TAG, "pushHistory lỗi", e));
    }

    /** Người dùng chọn lại từ khác: chỉ cập nhật chữ, không gửi lại ảnh. */
    public static void updateHistoryWord(HistoryItem h) {
        DocumentReference user = userDoc();
        if (user == null || h == null) return;
        user.collection("history").document(String.valueOf(h.time))
                .set(historyFields(h), SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "updateHistoryWord lỗi", e));
    }

    public static void deleteHistory(HistoryItem h) {
        DocumentReference user = userDoc();
        if (user == null || h == null) return;
        user.collection("history").document(String.valueOf(h.time)).delete()
                .addOnFailureListener(e -> Log.w(TAG, "deleteHistory lỗi", e));
    }

    public static void deleteHistories(List<HistoryItem> list) {
        DocumentReference user = userDoc();
        if (user == null || list == null || list.isEmpty()) return;
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        int n = 0;
        for (HistoryItem h : list) {
            batch.delete(user.collection("history").document(String.valueOf(h.time)));
            if (++n == 450) { // mỗi batch tối đa 500 thao tác
                batch.commit();
                batch = FirebaseFirestore.getInstance().batch();
                n = 0;
            }
        }
        if (n > 0) batch.commit().addOnFailureListener(e -> Log.w(TAG, "deleteHistories lỗi", e));
    }

    private static Map<String, Object> historyFields(HistoryItem h) {
        Map<String, Object> m = new HashMap<>();
        m.put("vocabKey", h.vocabKey);
        m.put("en", h.en);
        m.put("vi", h.vi);
        m.put("phonetic", h.phonetic);
        m.put("confidence", (double) h.confidence);
        m.put("time", h.time);
        return m;
    }

    // ================================================================ Kéo về / gộp

    /**
     * Kéo dữ liệu của tài khoản hiện tại về máy (chỉ thêm cái còn thiếu), đồng thời đẩy lên
     * những mục có trên máy mà Firestore chưa có. Xong thì gọi onDone trên luồng chính
     * (cả khi mất mạng hoặc lỗi, để app vẫn chạy tiếp được).
     */
    public static void pullAll(Context context, Runnable onDone) {
        final Context app = context.getApplicationContext();
        DocumentReference user = userDoc();
        if (user == null) { finish(onDone); return; }

        final Task<QuerySnapshot> tw = user.collection("words").get();
        final Task<QuerySnapshot> th = user.collection("history").get();
        Tasks.whenAllComplete(tw, th).addOnCompleteListener(t -> {
            final QuerySnapshot ws = tw.isSuccessful() ? tw.getResult() : null;
            final QuerySnapshot hs = th.isSuccessful() ? th.getResult() : null;
            if (ws == null || hs == null) {
                Log.w(TAG, "Không tải được dữ liệu từ Firestore (có thể đang offline)");
            }
            AppDatabase.IO.execute(() -> {
                try {
                    merge(app, ws, hs);
                    if (ws != null && hs != null) synced = true;
                } catch (Exception e) {
                    Log.w(TAG, "merge lỗi", e);
                }
                finish(onDone);
            });
        });
    }

    /** Đồng bộ 1 lần cho mỗi lần mở app (dùng ở MainActivity khi đã đăng nhập sẵn). */
    public static void syncOnce(Context context) {
        if (!synced) pullAll(context, null);
    }

    private static void merge(Context app, QuerySnapshot ws, QuerySnapshot hs) throws IOException {
        AppDatabase db = AppDatabase.get(app);
        SavedWordDao wd = db.savedWordDao();
        HistoryDao hd = db.historyDao();

        if (ws != null) {
            Set<String> cloud = new HashSet<>();
            for (DocumentSnapshot d : ws.getDocuments()) {
                cloud.add(d.getId());
                Long idx = d.getLong("vocabIndex");
                if (idx == null || wd.countByIndex(idx.intValue()) > 0) continue;
                SavedWord w = new SavedWord();
                w.vocabIndex = idx.intValue();
                w.en = d.getString("en");
                w.vi = d.getString("vi");
                w.phonetic = d.getString("phonetic");
                w.example = d.getString("example");
                Long at = d.getLong("savedAt");
                w.savedAt = at == null ? System.currentTimeMillis() : at;
                w.imagePath = writeThumb(app, "thumbs", "cloud_" + idx + ".jpg", d.getString("thumb"));
                wd.insert(w);
            }
            // Từ có trên máy mà trên Firestore chưa có (ví dụ lưu từ trước khi có đăng nhập)
            for (SavedWord w : wd.getAll()) {
                if (!cloud.contains(String.valueOf(w.vocabIndex))) pushWord(w);
            }
        }

        if (hs != null) {
            Set<String> cloud = new HashSet<>();
            for (DocumentSnapshot d : hs.getDocuments()) {
                cloud.add(d.getId());
                Long time = d.getLong("time");
                if (time == null || hd.countByTime(time) > 0) continue;
                HistoryItem h = new HistoryItem();
                Long key = d.getLong("vocabKey");
                h.vocabKey = key == null ? 0 : key.intValue();
                h.en = d.getString("en");
                h.vi = d.getString("vi");
                h.phonetic = d.getString("phonetic");
                Double conf = d.getDouble("confidence");
                h.confidence = conf == null ? 0f : conf.floatValue();
                h.time = time;
                h.imagePath = writeThumb(app, "history", time + ".jpg", d.getString("thumb"));
                hd.insert(h);
            }
            for (HistoryItem h : hd.getAll()) {
                if (!cloud.contains(String.valueOf(h.time))) pushHistory(h);
            }
        }
    }

    // ================================================================ Đăng xuất

    /** Xóa dữ liệu trên máy (sổ từ vựng, lịch sử, ảnh) để tài khoản khác không thấy. Dữ liệu vẫn còn trên Firestore. */
    public static void clearLocal(Context context) {
        final Context app = context.getApplicationContext();
        synced = false;
        AppDatabase.IO.execute(() -> {
            try {
                AppDatabase db = AppDatabase.get(app);
                for (SavedWord w : db.savedWordDao().getAll()) deleteFile(w.imagePath);
                for (HistoryItem h : db.historyDao().getAll()) deleteFile(h.imagePath);
                db.savedWordDao().clear();
                db.historyDao().clear();
            } catch (Exception e) {
                Log.w(TAG, "clearLocal lỗi", e);
            }
        });
    }

    // ================================================================ Ảnh

    private static void deleteFile(String path) {
        if (path != null) {
            new File(path).delete();
        }
    }

    /** Thu nhỏ ảnh rồi mã hóa Base64 (khoảng 10-15 KB mỗi ảnh). */
    private static String thumbBase64(String path) {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int max = Math.max(o.outWidth, o.outHeight);
            int sample = 1;
            while (max / (sample * 2) >= THUMB_SIZE) sample *= 2;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(path, o2);
            if (b == null) return null;
            float s = THUMB_SIZE / (float) Math.max(b.getWidth(), b.getHeight());
            if (s < 1f) {
                b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)),
                        Math.max(1, Math.round(b.getHeight() * s)), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "thumbBase64 lỗi", e);
            return null;
        }
    }

    /** Giải mã Base64 và ghi ra file ảnh; trả về đường dẫn (hoặc null nếu không có ảnh). */
    private static String writeThumb(Context app, String folder, String name, String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            File dir = new File(app.getFilesDir(), folder);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "writeThumb lỗi", e);
            return null;
        }
    }

    private static void finish(Runnable r) {
        if (r != null) new Handler(Looper.getMainLooper()).post(r);
    }
}

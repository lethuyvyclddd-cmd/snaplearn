package com.example.snaplearn.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Đọc từ vựng trong assets/vocab.json. Tra theo index của lớp (không bị nhầm nhãn trùng như crane, maillot). */
public class VocabRepository {

    public static class Entry {
        public final int index;   // chỉ số lớp trong bộ nhãn của mô hình
        public final int key;     // khóa duy nhất giữa các bộ từ vựng (dùng khi lưu vào sổ)
        public final String label, en, vi, phonetic, example;

        Entry(int index, int key, String label, String en, String vi, String phonetic, String example) {
            this.index = index;
            this.key = key;
            this.label = label;
            this.en = en;
            this.vi = vi;
            this.phonetic = phonetic;
            this.example = example;
        }
    }

    private final Map<Integer, Entry> byIndex = new HashMap<>();

    public VocabRepository(Context context) throws IOException {
        this(context, "vocab.json", 0);
    }

    /** @param fileName   tên file trong assets (vocab.json hoặc coco_vocab.json)
     *  @param keyOffset  cộng vào index để tạo khóa duy nhất giữa các bộ từ vựng */
    public VocabRepository(Context context, String fileName, int keyOffset) throws IOException {
        try (InputStream in = context.getAssets().open(fileName)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONArray arr = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int index = o.optInt("index", i);
                byIndex.put(index, new Entry(
                        index, index + keyOffset, o.getString("label"), o.getString("en"), o.getString("vi"),
                        o.optString("phonetic", ""), o.optString("example", "")));
            }
        } catch (JSONException e) {
            throw new IOException("Vocab không hợp lệ: " + e.getMessage());
        }
    }

    /** Toàn bộ từ trong bộ từ vựng này. */
    public List<Entry> all() {
        return new ArrayList<>(byIndex.values());
    }

    /** Tra theo index lớp do mô hình dự đoán. Trả về null nếu không có. */
    public Entry findByIndex(int index) {
        return byIndex.get(index);
    }
}
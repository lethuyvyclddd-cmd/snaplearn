package com.example.snaplearn.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snaplearn.data.AppDatabase;
import com.example.snaplearn.data.CloudSync;
import com.example.snaplearn.R;
import com.example.snaplearn.data.SavedWord;
import com.example.snaplearn.util.Speaker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Màn hình sổ từ vựng: danh sách từ đã lưu, nghe phát âm, xóa. */
public class NotebookActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View layoutEmpty;          // khối "trống" (biểu tượng + dòng chữ)
    private TextView tvCount;          // "N từ đã lưu"
    private WordAdapter adapter;
    private Speaker speaker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notebook);

        recycler = findViewById(R.id.recycler);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvCount = findViewById(R.id.tvCount);
        speaker = Speaker.getInstance(this);

        adapter = new WordAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.IO.execute(() -> {
            List<SavedWord> list = AppDatabase.get(this).savedWordDao().getAll();
            runOnUiThread(() -> {
                adapter.setItems(list);
                updateEmpty();
            });
        });
    }

    /** Cập nhật trạng thái trống và số từ đã lưu. */
    private void updateEmpty() {
        int count = adapter.getItemCount();
        boolean empty = count == 0;
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvCount.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) tvCount.setText(getString(R.string.notebook_count, count));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private static class WordViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final TextView tvEn, tvVi, tvPhonetic;
        final Button btnSpeak, btnDelete;

        WordViewHolder(@NonNull View v) {
            super(v);
            ivThumb = v.findViewById(R.id.ivThumb);
            tvEn = v.findViewById(R.id.tvEn);
            tvVi = v.findViewById(R.id.tvVi);
            tvPhonetic = v.findViewById(R.id.tvPhonetic);
            btnSpeak = v.findViewById(R.id.btnSpeak);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }

    private class WordAdapter extends RecyclerView.Adapter<WordViewHolder> {
        private final List<SavedWord> items = new ArrayList<>();

        void setItems(List<SavedWord> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_word, parent, false);
            return new WordViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder h, int position) {
            SavedWord w = items.get(position);
            h.tvEn.setText(w.en);
            h.tvVi.setText(w.vi);
            // Phiên âm có thể rỗng: ẩn đi cho gọn
            if (w.phonetic == null || w.phonetic.isEmpty()) {
                h.tvPhonetic.setVisibility(View.GONE);
            } else {
                h.tvPhonetic.setText(w.phonetic);
                h.tvPhonetic.setVisibility(View.VISIBLE);
            }

            Bitmap thumb = null;
            if (w.imagePath != null && new File(w.imagePath).exists()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4; // 512 -> 128px cho nhẹ
                thumb = BitmapFactory.decodeFile(w.imagePath, o);
            }
            h.ivThumb.setImageBitmap(thumb);

            h.btnSpeak.setOnClickListener(v -> {
                if (!speaker.speak(w.en)) {
                    Toast.makeText(NotebookActivity.this, R.string.msg_no_tts, Toast.LENGTH_SHORT).show();
                }
            });
            h.btnDelete.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                SavedWord removed = items.remove(pos);
                notifyItemRemoved(pos);
                updateEmpty();
                AppDatabase.IO.execute(() -> {
                    AppDatabase.get(NotebookActivity.this).savedWordDao().delete(removed);
                    CloudSync.deleteWord(removed); // đồng bộ Firestore
                    if (removed.imagePath != null) new File(removed.imagePath).delete();
                });
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
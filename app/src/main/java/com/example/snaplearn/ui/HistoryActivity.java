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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snaplearn.data.AppDatabase;
import com.example.snaplearn.data.CloudSync;
import com.example.snaplearn.data.HistoryDao;
import com.example.snaplearn.data.HistoryItem;
import com.example.snaplearn.R;
import com.example.snaplearn.util.Speaker;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lịch sử các lần nhận dạng gần đây. */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View layoutEmpty;          // khối "trống" (biểu tượng + dòng chữ)
    private TextView tvCount;          // "N lần nhận dạng gần đây"
    private Button btnClearAll;
    private HistoryAdapter adapter;
    private Speaker speaker;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recycler = findViewById(R.id.recycler);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvCount = findViewById(R.id.tvCount);
        btnClearAll = findViewById(R.id.btnClearAll);
        speaker = Speaker.getInstance(this);

        adapter = new HistoryAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnClearAll.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.dlg_clear_title)
                .setMessage(R.string.dlg_clear_message)
                .setPositiveButton(R.string.btn_delete, (d, which) -> clearAll())
                .setNegativeButton(R.string.btn_cancel, null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppDatabase.IO.execute(() -> {
            List<HistoryItem> list = AppDatabase.get(this).historyDao().getAll();
            runOnUiThread(() -> {
                adapter.setItems(list);
                updateEmpty();
            });
        });
    }

    private void clearAll() {
        AppDatabase.IO.execute(() -> {
            HistoryDao dao = AppDatabase.get(this).historyDao();
            List<HistoryItem> all = dao.getAll();
            for (HistoryItem h : all) {
                if (h.imagePath != null) new File(h.imagePath).delete();
            }
            CloudSync.deleteHistories(all); // đồng bộ Firestore
            dao.clear();
            runOnUiThread(() -> {
                adapter.setItems(new ArrayList<>());
                updateEmpty();
            });
        });
    }

    /** Cập nhật trạng thái trống, số lượng và nút "Xóa tất cả". */
    private void updateEmpty() {
        int count = adapter.getItemCount();
        boolean empty = count == 0;
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnClearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvCount.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) tvCount.setText(getString(R.string.history_count, count));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final TextView tvEn, tvVi, tvMeta, tvConfidence;
        final Button btnSpeak, btnDelete;

        HistoryViewHolder(@NonNull View v) {
            super(v);
            ivThumb = v.findViewById(R.id.ivThumb);
            tvEn = v.findViewById(R.id.tvEn);
            tvVi = v.findViewById(R.id.tvVi);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvConfidence = v.findViewById(R.id.tvConfidence);
            btnSpeak = v.findViewById(R.id.btnSpeak);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolder> {
        private final List<HistoryItem> items = new ArrayList<>();

        void setItems(List<HistoryItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new HistoryViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder h, int position) {
            HistoryItem item = items.get(position);
            h.tvEn.setText(item.en);
            h.tvVi.setText(item.vi);
            // Độ tin cậy hiện ở nhãn riêng, thời gian ở dòng bên cạnh
            h.tvConfidence.setText(getString(R.string.res_confidence, Math.round(item.confidence * 100)));
            h.tvMeta.setText(timeFormat.format(new Date(item.time)));

            Bitmap thumb = null;
            if (item.imagePath != null && new File(item.imagePath).exists()) {
                thumb = BitmapFactory.decodeFile(item.imagePath);
            }
            h.ivThumb.setImageBitmap(thumb);

            h.btnSpeak.setOnClickListener(v -> {
                if (!speaker.speak(item.en)) {
                    Toast.makeText(HistoryActivity.this, R.string.msg_no_tts, Toast.LENGTH_SHORT).show();
                }
            });
            h.btnDelete.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                HistoryItem removed = items.remove(pos);
                notifyItemRemoved(pos);
                updateEmpty();
                AppDatabase.IO.execute(() -> {
                    AppDatabase.get(HistoryActivity.this).historyDao().delete(removed);
                    CloudSync.deleteHistory(removed); // đồng bộ Firestore
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
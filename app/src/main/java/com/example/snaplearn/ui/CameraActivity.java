package com.example.snaplearn.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.snaplearn.data.AppDatabase;
import com.example.snaplearn.data.CloudSync;
import com.example.snaplearn.ml.Candidate;
import com.example.snaplearn.ml.Classifier;
import com.example.snaplearn.data.HistoryDao;
import com.example.snaplearn.data.HistoryItem;
import com.example.snaplearn.util.ImageUtils;
import com.example.snaplearn.ml.ObjectDetector;
import com.example.snaplearn.R;
import com.example.snaplearn.data.SavedWord;
import com.example.snaplearn.data.SavedWordDao;
import com.example.snaplearn.util.Speaker;
import com.example.snaplearn.data.VocabRepository;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    /** Dưới ngưỡng này thì coi là chưa chắc chắn (có thể là vật mô hình không biết). */
    private static final float CONFIDENCE_THRESHOLD = 0.40f;
    /** Cạnh ảnh hiển thị/lưu vào sổ. Ảnh đưa vào mô hình luôn là 224x224. */
    private static final int DISPLAY_SIZE = 512;

    private PreviewView previewView;
    private ImageView ivResult;
    private View groupCamera, groupResult;
    private TextView tvConfidence, tvWord, tvPhonetic, tvVi, tvExample, tvChoicesLabel;
    private LinearLayout choicesLayout;
    private Button btnCapture, btnGallery, btnRetake, btnSave, btnSpeak;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Classifier classifier;
    private ObjectDetector detector;
    private VocabRepository vocab;       // từ vựng ImageNet
    private VocabRepository cocoVocab;   // từ vựng COCO
    private Speaker speaker;

    private Bitmap displayBitmap;                 // ảnh vuông đang hiển thị
    private VocabRepository.Entry currentEntry;   // từ đang hiển thị (null nếu chưa chắc chắn)
    private long historyId = -1;                  // dòng lịch sử của lần chụp này (-1 chưa có, -2 đang ghi)

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, R.string.msg_need_camera, Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    /** Bước 1: chọn ảnh từ thư viện bằng Photo Picker của hệ thống (không cần xin quyền đọc ảnh). */
    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) prepareGalleryImage(uri);
            });

    /** Bước 2: người dùng kéo/phóng to ảnh để đưa vật vào giữa khung (CropActivity). */
    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String path = result.getData().getStringExtra(CropActivity.EXTRA_RESULT_PATH);
                    if (path != null) processCroppedImage(path);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        ivResult = findViewById(R.id.ivResult);
        groupCamera = findViewById(R.id.groupCamera);
        groupResult = findViewById(R.id.groupResult);
        tvConfidence = findViewById(R.id.tvConfidence);
        tvWord = findViewById(R.id.tvWord);
        tvPhonetic = findViewById(R.id.tvPhonetic);
        tvVi = findViewById(R.id.tvVi);
        tvExample = findViewById(R.id.tvExample);
        tvChoicesLabel = findViewById(R.id.tvChoicesLabel);
        choicesLayout = findViewById(R.id.choicesLayout);
        btnCapture = findViewById(R.id.btnCapture);
        btnGallery = findViewById(R.id.btnGallery);
        btnRetake = findViewById(R.id.btnRetake);
        btnSave = findViewById(R.id.btnSave);
        btnSpeak = findViewById(R.id.btnSpeak);

        cameraExecutor = Executors.newSingleThreadExecutor();
        speaker = Speaker.getInstance(this);

        // Load model ở background để không block main thread
        cameraExecutor.execute(() -> {
            try {
                classifier = new Classifier(this);
            } catch (Exception e) {
                classifier = null;
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.msg_model_error, e.getMessage()), Toast.LENGTH_LONG).show());
            }
            try {
                detector = new ObjectDetector(this);
            } catch (Exception e) {
                detector = null;
            }
            try {
                vocab = new VocabRepository(this, "vocab.json", 0);
            } catch (Exception e) {
                vocab = null;
            }
            try {
                cocoVocab = new VocabRepository(this, "coco_vocab.json", 10000);
            } catch (Exception e) {
                cocoVocab = null;
            }
        });

        btnCapture.setOnClickListener(v -> takePhoto());
        btnGallery.setOnClickListener(v -> galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        btnRetake.setOnClickListener(v -> showCameraMode());
        btnSpeak.setOnClickListener(v -> {
            if (currentEntry != null && !speaker.speak(currentEntry.en)) {
                Toast.makeText(this, R.string.msg_no_tts, Toast.LENGTH_SHORT).show();
            }
        });
        btnSave.setOnClickListener(v -> saveCurrentWord());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Giới hạn độ phân giải chụp (~1.2MP) để tránh tràn bộ nhớ trên máy yếu
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(new Size(960, 1280))
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, getString(R.string.msg_camera_error, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        setInputButtonsEnabled(false);

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    // 1. Cắt vuông giữa ảnh (trước khi xoay cho nhẹ), rồi xoay đúng chiều
                    Bitmap square = ImageUtils.centerSquare(image.toBitmap());
                    Bitmap upright = ImageUtils.rotate(square, image.getImageInfo().getRotationDegrees());
                    // 2. Ảnh hiển thị 512 và ảnh đầu vào mô hình 224
                    Bitmap display = ImageUtils.resize(upright, DISPLAY_SIZE);
                    Bitmap input = ImageUtils.resize(display, ImageUtils.INPUT_SIZE);
                    // 3. Nhận dạng bằng cả hai mô hình rồi gộp kết quả
                    List<Candidate> preds = (classifier != null || detector != null)
                            ? collectCandidates(input, display) : null;

                    runOnUiThread(() -> showResult(display, preds));
                } catch (Exception | OutOfMemoryError e) {
                    runOnUiThread(() -> {
                        Toast.makeText(CameraActivity.this,
                                getString(R.string.msg_capture_error, e.getMessage()), Toast.LENGTH_LONG).show();
                        setInputButtonsEnabled(true);
                    });
                } finally {
                    image.close();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this,
                            getString(R.string.msg_capture_error, exception.getMessage()), Toast.LENGTH_LONG).show();
                    setInputButtonsEnabled(true);
                });
            }
        });
    }

    // ---------------------------------------------------------------- Chọn ảnh từ thư viện

    /** Khóa/mở nút Chụp ảnh và nút Chọn ảnh cùng lúc (tránh bấm khi đang xử lý). */
    private void setInputButtonsEnabled(boolean enabled) {
        btnCapture.setEnabled(enabled);
        btnGallery.setEnabled(enabled);
    }

    /** Đọc ảnh đã chọn, lưu bản đã xoay đúng chiều ra file tạm rồi mở màn hình chỉnh ảnh. */
    private void prepareGalleryImage(Uri uri) {
        setInputButtonsEnabled(false);
        cameraExecutor.execute(() -> {
            try {
                File src = saveUprightCopy(uri);
                runOnUiThread(() -> {
                    setInputButtonsEnabled(true);
                    cropLauncher.launch(new Intent(this, CropActivity.class)
                            .putExtra(CropActivity.EXTRA_SRC_PATH, src.getAbsolutePath()));
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this,
                            getString(R.string.msg_gallery_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    setInputButtonsEnabled(true);
                });
            }
        });
    }

    /** Ảnh sau khi người dùng đã căn chỉnh (vuông, 512px): nhận dạng như ảnh chụp từ camera. */
    private void processCroppedImage(String path) {
        setInputButtonsEnabled(false);
        cameraExecutor.execute(() -> {
            try {
                Bitmap cropped = BitmapFactory.decodeFile(path);
                if (cropped == null) throw new IOException("Không đọc được ảnh đã chỉnh");
                Bitmap display = ImageUtils.resize(cropped, DISPLAY_SIZE);
                Bitmap input = ImageUtils.resize(display, ImageUtils.INPUT_SIZE);
                List<Candidate> preds = (classifier != null || detector != null)
                        ? collectCandidates(input, display) : null;

                new File(path).delete();
                new File(getCacheDir(), "gallery_src.jpg").delete();
                runOnUiThread(() -> showResult(display, preds));
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this,
                            getString(R.string.msg_gallery_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    setInputButtonsEnabled(true);
                });
            }
        });
    }

    /**
     * Đọc ảnh từ Uri: thu nhỏ khi đọc (cạnh dài tối đa ~1600px để tiết kiệm bộ nhớ), đặt nền trắng
     * cho ảnh PNG trong suốt, xoay đúng chiều theo EXIF rồi lưu ra file tạm trong bộ nhớ đệm.
     */
    private File saveUprightCopy(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (maxSide / (sample * 2) >= 1600) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Không đọc được ảnh");

        if (bmp.hasAlpha()) { // PNG trong suốt: đặt nền trắng, tránh nền đen khi lưu JPEG
            Bitmap flat = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flat);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bmp, 0, 0, null);
            bmp = flat;
        }

        int degrees = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
        } catch (Exception ignored) {
            // không có EXIF thì coi như ảnh đã đúng chiều
        }

        Bitmap upright = ImageUtils.rotate(bmp, degrees);
        return ImageUtils.saveJpeg(upright, new File(getCacheDir(), "gallery_src.jpg"));
    }

    // ---------------------------------------------------------------- Hiển thị kết quả

    private void showResult(Bitmap display, List<Candidate> preds) {
        displayBitmap = display;
        ivResult.setImageBitmap(display);
        ivResult.setVisibility(View.VISIBLE);
        groupCamera.setVisibility(View.GONE);
        groupResult.setVisibility(View.VISIBLE);
        choicesLayout.removeAllViews();
        currentEntry = null;
        historyId = -1;

        if (preds == null || preds.isEmpty()) {
            tvWord.setText(R.string.msg_no_model);
            setTextOrHide(tvPhonetic, null);
            setTextOrHide(tvVi, null);
            setTextOrHide(tvExample, null);
            setTextOrHide(tvConfidence, null);
            tvChoicesLabel.setVisibility(View.GONE);
            btnSpeak.setEnabled(false);
            btnSave.setEnabled(false);
            return;
        }

        buildChoices(preds);
        Candidate top = preds.get(0);
        if (top.confidence < CONFIDENCE_THRESHOLD) {
            // Chưa chắc: không khẳng định, để người dùng tự chọn trong top 3
            tvWord.setText(R.string.res_uncertain_title);
            setTextOrHide(tvVi, getString(R.string.res_uncertain, Math.round(top.confidence * 100)));
            setTextOrHide(tvPhonetic, null);
            setTextOrHide(tvExample, null);
            setTextOrHide(tvConfidence, null);
            tvChoicesLabel.setText(R.string.res_maybe);
            btnSpeak.setEnabled(false);
            btnSave.setText(R.string.btn_save);
            btnSave.setEnabled(false);
        } else {
            tvChoicesLabel.setText(R.string.res_not_right);
            showPrediction(top);
        }
        tvChoicesLabel.setVisibility(View.VISIBLE);
    }

    /** Hiện thẻ từ vựng cho một dự đoán (dùng cho kết quả đầu và khi người dùng chọn lại). */
    private void showPrediction(Candidate p) {
        VocabRepository.Entry entry = entryOf(p);
        currentEntry = entry;
        setTextOrHide(tvConfidence, getString(R.string.res_confidence, Math.round(p.confidence * 100)));

        if (entry == null) {
            tvWord.setText(p.label);
            setTextOrHide(tvVi, getString(R.string.res_no_vocab));
            setTextOrHide(tvPhonetic, null);
            setTextOrHide(tvExample, null);
            btnSpeak.setEnabled(false);
            btnSave.setEnabled(false);
            return;
        }

        tvWord.setText(entry.en);
        setTextOrHide(tvVi, entry.vi);
        setTextOrHide(tvPhonetic, entry.phonetic);
        setTextOrHide(tvExample, entry.example.isEmpty() ? null : "\"" + entry.example + "\"");
        btnSpeak.setEnabled(true);
        refreshSaveButton(entry);
        recordHistory(entry, p.confidence);
    }

    private void buildChoices(List<Candidate> preds) {
        for (Candidate p : preds) {
            MaterialButton b = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(nameOf(p) + "\n" + Math.round(p.confidence * 100) + "%");
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setMaxLines(3);
            b.setOnClickListener(v -> showPrediction(p));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            int m = dp(3);
            lp.setMargins(m, 0, m, 0);
            choicesLayout.addView(b, lp);
        }
    }

    /** Tra từ vựng đúng bộ theo mô hình đã sinh ra kết quả. */
    private VocabRepository.Entry entryOf(Candidate c) {
        VocabRepository repo = (c.source == Candidate.COCO) ? cocoVocab : vocab;
        return repo != null ? repo.findByIndex(c.index) : null;
    }

    /**
     * Gộp kết quả của EfficientNet (top 3) và COCO (đồ vật điểm >= 0.30), xếp theo độ tin cậy,
     * bỏ trùng theo từ tiếng Anh (vd laptop có ở cả hai mô hình), lấy 3 kết quả đầu.
     */
    private List<Candidate> collectCandidates(Bitmap input224, Bitmap display) {
        List<Candidate> all = new ArrayList<>();
        if (classifier != null) {
            for (Classifier.Prediction p : classifier.classify(input224, 3)) {
                if (p.index >= 0) all.add(new Candidate(Candidate.IMAGENET, p.index, p.label, p.confidence));
            }
        }
        if (detector != null) {
            for (ObjectDetector.Detection d : detector.detect(display, 0.30f)) {
                all.add(new Candidate(Candidate.COCO, d.classId, d.label, d.score));
            }
        }
        Collections.sort(all, (a, b) -> Float.compare(b.confidence, a.confidence));

        List<Candidate> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate c : all) {
            if (seen.add(nameOf(c).toLowerCase(Locale.ROOT))) merged.add(c);
            if (merged.size() == 3) break;
        }
        return merged;
    }

    private String nameOf(Candidate p) {
        VocabRepository.Entry e = entryOf(p);
        return e != null ? e.en : p.label;
    }

    private void setTextOrHide(TextView tv, String text) {
        if (text == null || text.isEmpty()) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(text);
            tv.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showCameraMode() {
        ivResult.setVisibility(View.GONE);
        groupResult.setVisibility(View.GONE);
        groupCamera.setVisibility(View.VISIBLE);
        setInputButtonsEnabled(true);
        currentEntry = null;
    }

    // ---------------------------------------------------------------- Lịch sử

    private static final int HISTORY_LIMIT = 100;

    /** Ghi lần nhận dạng này vào lịch sử; nếu người dùng chọn lại từ khác thì cập nhật cùng dòng đó. */
    private void recordHistory(VocabRepository.Entry entry, float confidence) {
        final long existingId = historyId;
        if (existingId == -2) return; // đang ghi, bỏ qua lần bấm quá nhanh
        final Bitmap bmp = displayBitmap;
        if (existingId == -1) historyId = -2;

        AppDatabase.IO.execute(() -> {
            try {
                HistoryDao dao = AppDatabase.get(this).historyDao();
                if (existingId >= 0) {
                    dao.updateWord(existingId, entry.key, entry.en, entry.vi, entry.phonetic, confidence);
                    HistoryItem updated = dao.getById(existingId);
                    if (updated != null) CloudSync.updateHistoryWord(updated); // đồng bộ Firestore
                    return;
                }
                File dir = new File(getFilesDir(), "history");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, System.currentTimeMillis() + ".jpg");
                ImageUtils.saveJpeg(ImageUtils.resize(bmp, 256), file);

                HistoryItem h = new HistoryItem();
                h.vocabKey = entry.key;
                h.en = entry.en;
                h.vi = entry.vi;
                h.phonetic = entry.phonetic;
                h.imagePath = file.getAbsolutePath();
                h.confidence = confidence;
                h.time = System.currentTimeMillis();
                final long id = dao.insert(h);
                h.id = id;
                CloudSync.pushHistory(h); // đồng bộ Firestore
                runOnUiThread(() -> historyId = id);

                // Chỉ giữ HISTORY_LIMIT lần gần nhất
                List<HistoryItem> old = dao.getOlderThan(HISTORY_LIMIT);
                for (HistoryItem o : old) {
                    if (o.imagePath != null) new File(o.imagePath).delete();
                }
                if (!old.isEmpty()) {
                    dao.deleteAll(old);
                    CloudSync.deleteHistories(old); // đồng bộ Firestore
                }
            } catch (Exception e) {
                runOnUiThread(() -> historyId = -1);
            }
        });
    }

    // ---------------------------------------------------------------- Sổ từ vựng

    private void refreshSaveButton(VocabRepository.Entry entry) {
        btnSave.setText(R.string.btn_save);
        btnSave.setEnabled(false);
        AppDatabase.IO.execute(() -> {
            boolean saved = AppDatabase.get(this).savedWordDao().countByEn(entry.en) > 0;
            runOnUiThread(() -> {
                if (currentEntry == entry) {
                    btnSave.setText(saved ? R.string.btn_saved : R.string.btn_save);
                    btnSave.setEnabled(!saved);
                }
            });
        });
    }

    private void saveCurrentWord() {
        final VocabRepository.Entry entry = currentEntry;
        final Bitmap bmp = displayBitmap;
        if (entry == null || bmp == null) return;
        btnSave.setEnabled(false);

        AppDatabase.IO.execute(() -> {
            try {
                SavedWordDao dao = AppDatabase.get(this).savedWordDao();
                if (dao.countByEn(entry.en) > 0) {
                    runOnUiThread(() -> btnSave.setText(R.string.btn_saved));
                    return;
                }
                File dir = new File(getFilesDir(), "thumbs");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, entry.index + "_" + System.currentTimeMillis() + ".jpg");
                ImageUtils.saveJpeg(bmp, file);

                SavedWord w = new SavedWord();
                w.vocabIndex = entry.key;
                w.en = entry.en;
                w.vi = entry.vi;
                w.phonetic = entry.phonetic;
                w.example = entry.example;
                w.imagePath = file.getAbsolutePath();
                w.savedAt = System.currentTimeMillis();
                dao.insert(w);
                CloudSync.pushWord(w); // đồng bộ Firestore

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show();
                    if (currentEntry == entry) btnSave.setText(R.string.btn_saved);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.msg_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final Classifier c = classifier;
        final ObjectDetector d = detector;
        cameraExecutor.execute(() -> { // đóng sau khi tác vụ đang chạy xong
            if (c != null) c.close();
            if (d != null) d.close();
        });
        cameraExecutor.shutdown();
    }
}
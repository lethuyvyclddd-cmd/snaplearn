package com.example.snaplearn.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.snaplearn.R;
import com.example.snaplearn.util.ImageUtils;

import java.io.File;

/**
 * Màn hình chỉnh ảnh lấy từ thư viện: người dùng kéo/phóng to để đưa vật vào giữa khung vuông.
 * Nhận đường dẫn ảnh nguồn qua EXTRA_SRC_PATH, trả về đường dẫn ảnh đã cắt qua EXTRA_RESULT_PATH.
 */
public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_SRC_PATH = "src_path";
    public static final String EXTRA_RESULT_PATH = "result_path";
    private static final int OUTPUT_SIZE = 512;

    private CropImageView cropView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.cropView);

        String path = getIntent().getStringExtra(EXTRA_SRC_PATH);
        Bitmap bmp = path == null ? null : BitmapFactory.decodeFile(path);
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.msg_gallery_error, "ảnh nguồn"), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        cropView.setBitmap(bmp);

        findViewById(R.id.btnCropCancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.btnCropReset).setOnClickListener(v -> cropView.reset());
        findViewById(R.id.btnCropUse).setOnClickListener(v -> finishWithCrop());
    }

    private void finishWithCrop() {
        try {
            Bitmap out = cropView.getCroppedBitmap(OUTPUT_SIZE);
            File f = new File(getCacheDir(), "gallery_crop.jpg");
            ImageUtils.saveJpeg(out, f);
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT_PATH, f.getAbsolutePath()));
            finish();
        } catch (Exception | OutOfMemoryError e) {
            Toast.makeText(this, getString(R.string.msg_gallery_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
}

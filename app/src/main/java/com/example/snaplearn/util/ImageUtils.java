package com.example.snaplearn.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Các hàm xử lý ảnh dùng chung. */
public final class ImageUtils {

    /** Kích thước đầu vào của EfficientNet-Lite0. */
    public static final int INPUT_SIZE = 224;

    private ImageUtils() {}

    /** Cắt hình vuông ở giữa ảnh (cạnh = cạnh ngắn). Cắt TRƯỚC khi xoay để tiết kiệm bộ nhớ. */
    public static Bitmap centerSquare(Bitmap src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        if (x == 0 && y == 0 && side == src.getWidth() && side == src.getHeight()) return src;
        return Bitmap.createBitmap(src, x, y, side, side);
    }

    /** Xoay ảnh theo số độ camera báo về. */
    public static Bitmap rotate(Bitmap src, int degrees) {
        if (degrees == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    /** Thu/phóng ảnh vuông về size x size. */
    public static Bitmap resize(Bitmap src, int size) {
        if (src.getWidth() == size && src.getHeight() == size) return src;
        return Bitmap.createScaledBitmap(src, size, size, true);
    }

    /** Lưu ảnh JPEG ra file. */
    public static File saveJpeg(Bitmap bmp, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
        }
        return file;
    }
}

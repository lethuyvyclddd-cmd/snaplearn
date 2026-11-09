package com.example.snaplearn.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * View cho phép kéo (1 ngón) và phóng to/thu nhỏ (2 ngón) ảnh bên dưới một khung vuông cố định.
 * Phần ảnh nằm trong khung chính là phần được cắt ra để nhận dạng.
 * Có thể kéo ảnh sao cho bất kỳ điểm nào cũng nằm giữa khung; vùng trống trong khung được tô nền trắng.
 */
public class CropImageView extends View {

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final float[] vals = new float[9];
    private final RectF cropRect = new RectF();
    private final RectF tmp = new RectF();

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint whitePaint = new Paint();
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ScaleGestureDetector scaleDetector;
    private float minScale = 0.1f, maxScale = 10f;
    private float lastX, lastY;
    private float lastFocusX, lastFocusY;
    private float density = 1f;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;

        whitePaint.setColor(Color.WHITE);
        dimPaint.setColor(0xB3000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);
        gridPaint.setColor(0x88FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                lastFocusX = d.getFocusX();
                lastFocusY = d.getFocusY();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float current = getScale();
                float target = Math.max(minScale, Math.min(maxScale, current * d.getScaleFactor()));
                float factor = target / current;
                matrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
                // hai ngón di chuyển cùng lúc thì ảnh cũng được kéo theo
                matrix.postTranslate(d.getFocusX() - lastFocusX, d.getFocusY() - lastFocusY);
                lastFocusX = d.getFocusX();
                lastFocusY = d.getFocusY();
                clamp();
                invalidate();
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ API

    public void setBitmap(Bitmap b) {
        this.bitmap = b;
        if (getWidth() > 0) {
            layoutCrop();
        }
        invalidate();
    }

    /** Đưa ảnh về vị trí ban đầu (phủ kín khung, căn giữa). */
    public void reset() {
        resetMatrix();
    }

    /** Cắt phần ảnh nằm trong khung ra thành ảnh vuông size x size (vùng trống tô nền trắng). */
    public Bitmap getCroppedBitmap(int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        if (bitmap != null && !cropRect.isEmpty()) {
            Matrix m = new Matrix(matrix);
            m.postTranslate(-cropRect.left, -cropRect.top);
            float s = size / cropRect.width();
            m.postScale(s, s);
            c.drawBitmap(bitmap, m, bitmapPaint);
        }
        return out;
    }

    // ------------------------------------------------------------------ Bố cục

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutCrop();
    }

    private void layoutCrop() {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h) - 2f * 20f * density;
        float cx = w / 2f, cy = h / 2f;
        cropRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        if (bitmap != null) resetMatrix();
    }

    private void resetMatrix() {
        if (bitmap == null || cropRect.isEmpty()) return;
        float size = cropRect.width();
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float cover = Math.max(size / bw, size / bh);
        float fit = Math.min(size / bw, size / bh);
        minScale = fit;
        maxScale = Math.max(cover, fit) * 6f;
        matrix.reset();
        matrix.postScale(cover, cover);
        matrix.postTranslate(cropRect.centerX() - bw * cover / 2f, cropRect.centerY() - bh * cover / 2f);
        invalidate();
    }

    private float getScale() {
        matrix.getValues(vals);
        return vals[Matrix.MSCALE_X];
    }

    /** Giữ cho tâm khung luôn nằm trong ảnh (để mọi điểm của ảnh đều có thể đưa vào giữa khung). */
    private void clamp() {
        if (bitmap == null) return;
        tmp.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(tmp);
        float cx = cropRect.centerX(), cy = cropRect.centerY();
        float dx = 0, dy = 0;
        if (tmp.left > cx) dx = cx - tmp.left;
        else if (tmp.right < cx) dx = cx - tmp.right;
        if (tmp.top > cy) dy = cy - tmp.top;
        else if (tmp.bottom < cy) dy = cy - tmp.bottom;
        if (dx != 0 || dy != 0) matrix.postTranslate(dx, dy);
    }

    // ------------------------------------------------------------------ Vẽ

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;

        canvas.drawRect(cropRect, whitePaint);          // nền trắng cho vùng trống trong khung
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);

        float w = getWidth(), h = getHeight();
        // làm tối vùng ngoài khung
        canvas.drawRect(0, 0, w, cropRect.top, dimPaint);
        canvas.drawRect(0, cropRect.bottom, w, h, dimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, dimPaint);

        // lưới chia ba
        float tw = cropRect.width() / 3f, th = cropRect.height() / 3f;
        for (int i = 1; i <= 2; i++) {
            canvas.drawLine(cropRect.left + tw * i, cropRect.top, cropRect.left + tw * i, cropRect.bottom, gridPaint);
            canvas.drawLine(cropRect.left, cropRect.top + th * i, cropRect.right, cropRect.top + th * i, gridPaint);
        }
        // dấu cộng nhỏ ở tâm giúp căn vật vào giữa
        float cx = cropRect.centerX(), cy = cropRect.centerY(), r = 10f * density;
        canvas.drawLine(cx - r, cy, cx + r, cy, borderPaint);
        canvas.drawLine(cx, cy - r, cx, cy + r, borderPaint);

        canvas.drawRect(cropRect, borderPaint);
    }

    // ------------------------------------------------------------------ Cảm ứng

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null) return false;
        scaleDetector.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex() == 0 ? 1 : 0; // chuyển sang ngón còn lại
                lastX = e.getX(idx);
                lastY = e.getY(idx);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && e.getPointerCount() == 1) {
                    matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    clamp();
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_UP:
                performClick();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

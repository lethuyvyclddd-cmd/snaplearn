package com.example.snaplearn.ml;

import android.content.Context;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mô hình phát hiện đồ vật COCO (SSD MobileNet, 80 loại: chair, book, bottle, apple, person...).
 * Bổ sung cho EfficientNet-Lite0 vì ImageNet thiếu nhiều từ đời thường.
 */
public class ObjectDetector implements Closeable {

    public static final String MODEL_FILE = "coco_ssd.tflite";
    public static final String LABEL_FILE = "coco_labels.txt";
    private static final int MAX_DETECTIONS = 10;

    public static class Detection {
        public final int classId;
        public final String label;
        public final float score;

        Detection(int classId, String label, float score) {
            this.classId = classId;
            this.label = label;
            this.score = score;
        }
    }

    private final Interpreter interpreter;
    private final List<String> labels = new ArrayList<>();
    private final int inputSize;
    private final boolean inputIsFloat;

    public ObjectDetector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        interpreter = new Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE), options);

        // Giữ nguyên số dòng (có dòng "???") để chỉ số lớp khớp với vị trí trong file
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(LABEL_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) labels.add(line.trim());
        }

        inputSize = interpreter.getInputTensor(0).shape()[1];   // 300
        inputIsFloat = interpreter.getInputTensor(0).dataType() == DataType.FLOAT32;
    }

    /** Trả về các loại đồ vật tìm thấy (mỗi loại giữ điểm cao nhất), điểm >= minScore, sắp giảm dần. */
    public List<Detection> detect(Bitmap bitmap, float minScore) {
        Bitmap scaled = bitmap;
        if (bitmap.getWidth() != inputSize || bitmap.getHeight() != inputSize) {
            scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        }
        ByteBuffer input = toBuffer(scaled);

        // Thứ tự đầu ra của TFLite_Detection_PostProcess: boxes, classes, scores, count
        float[][][] boxes = new float[1][MAX_DETECTIONS][4];
        float[][] classes = new float[1][MAX_DETECTIONS];
        float[][] scores = new float[1][MAX_DETECTIONS];
        float[] count = new float[1];
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, boxes);
        outputs.put(1, classes);
        outputs.put(2, scores);
        outputs.put(3, count);
        interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);

        int n = Math.min(MAX_DETECTIONS, Math.round(count[0]));
        Map<Integer, Float> best = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int id = Math.round(classes[0][i]);
            float s = scores[0][i];
            if (s < minScore || id < 0 || id >= labels.size()) continue;
            if ("???".equals(labels.get(id)) || labels.get(id).isEmpty()) continue;
            Float old = best.get(id);
            if (old == null || s > old) best.put(id, s);
        }

        List<Detection> result = new ArrayList<>();
        for (Map.Entry<Integer, Float> e : best.entrySet()) {
            result.add(new Detection(e.getKey(), labels.get(e.getKey()), e.getValue()));
        }
        result.sort((a, b) -> Float.compare(b.score, a.score));
        return result;
    }

    private ByteBuffer toBuffer(Bitmap bmp) {
        ByteBuffer buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * (inputIsFloat ? 4 : 1));
        buf.order(ByteOrder.nativeOrder());
        int[] pixels = new int[inputSize * inputSize];
        bmp.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);
        for (int p : pixels) {
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            if (inputIsFloat) {
                buf.putFloat((r - 127.5f) / 127.5f);
                buf.putFloat((g - 127.5f) / 127.5f);
                buf.putFloat((b - 127.5f) / 127.5f);
            } else {
                // Bản quantized nhận trực tiếp giá trị điểm ảnh 0..255
                buf.put((byte) r);
                buf.put((byte) g);
                buf.put((byte) b);
            }
        }
        buf.rewind();
        return buf;
    }

    @Override
    public void close() {
        interpreter.close();
    }
}

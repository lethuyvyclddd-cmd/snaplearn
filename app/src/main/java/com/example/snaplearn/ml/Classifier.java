package com.example.snaplearn.ml;

import android.content.Context;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Nạp mô hình TFLite (EfficientNet-Lite0) và dự đoán vật thể từ ảnh. */
public class Classifier implements Closeable {

    public static final String MODEL_FILE = "efficientnet_lite0.tflite";
    public static final String LABEL_FILE = "labels.txt";

    // Chuẩn hóa ảnh cho EfficientNet-Lite0 bản float32: (pixel - 127) / 128
    private static final float MEAN = 127f;
    private static final float STD = 128f;

    public static class Prediction {
        public final int index;        // vị trí lớp trong labels.txt (0..999), -1 nếu là background
        public final String label;
        public final float confidence; // 0..1

        Prediction(int index, String label, float confidence) {
            this.index = index;
            this.label = label;
            this.confidence = confidence;
        }
    }

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int inputSize;
    private final DataType inputType;
    private final int numClasses;
    private final int labelOffset;

    public Classifier(Context context) throws IOException {
        MappedByteBuffer model = FileUtil.loadMappedFile(context, MODEL_FILE);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        interpreter = new Interpreter(model, options);

        labels = loadLabels(context);

        Tensor in = interpreter.getInputTensor(0);      // [1, 224, 224, 3]
        inputSize = in.shape()[1];
        inputType = in.dataType();
        numClasses = interpreter.getOutputTensor(0).shape()[1];
        // Một số mô hình có thêm lớp "background" ở vị trí 0 (1001 lớp)
        labelOffset = (numClasses == labels.size() + 1) ? 1 : 0;
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(LABEL_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) result.add(line);
            }
        }
        return result;
    }

    /** Dự đoán và trả về topK kết quả có độ tin cậy cao nhất. */
    public List<Prediction> classify(Bitmap bitmap, int topK) {
        Bitmap scaled = bitmap;
        if (bitmap.getWidth() != inputSize || bitmap.getHeight() != inputSize) {
            scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        }
        ByteBuffer input = toBuffer(scaled);
        float[] probs = run(input);

        Integer[] idx = new Integer[probs.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Float.compare(probs[b], probs[a]));

        List<Prediction> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, idx.length); i++) {
            int labelIndex = idx[i] - labelOffset;
            String name = (labelIndex >= 0 && labelIndex < labels.size())
                    ? labels.get(labelIndex) : "background";
            result.add(new Prediction(labelIndex, name, probs[idx[i]]));
        }
        return result;
    }

    private ByteBuffer toBuffer(Bitmap bmp) {
        boolean isFloat = inputType == DataType.FLOAT32;
        ByteBuffer buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * (isFloat ? 4 : 1));
        buf.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputSize * inputSize];
        bmp.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);
        for (int p : pixels) {
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            if (isFloat) {
                buf.putFloat((r - MEAN) / STD);
                buf.putFloat((g - MEAN) / STD);
                buf.putFloat((b - MEAN) / STD);
            } else {
                buf.put((byte) r);
                buf.put((byte) g);
                buf.put((byte) b);
            }
        }
        buf.rewind();
        return buf;
    }

    private float[] run(ByteBuffer input) {
        Tensor out = interpreter.getOutputTensor(0);
        float[] probs = new float[numClasses];
        if (out.dataType() == DataType.FLOAT32) {
            float[][] output = new float[1][numClasses];
            interpreter.run(input, output);
            probs = output[0];
        } else {
            byte[][] output = new byte[1][numClasses];
            interpreter.run(input, output);
            Tensor.QuantizationParams q = out.quantizationParams();
            for (int i = 0; i < numClasses; i++) {
                probs[i] = ((output[0][i] & 0xFF) - q.getZeroPoint()) * q.getScale();
            }
        }
        return normalize(probs);
    }

    /** Nếu đầu ra chưa phải xác suất (tổng khác 1 hoặc có số âm) thì áp dụng softmax. */
    private float[] normalize(float[] v) {
        float sum = 0f, min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float x : v) {
            sum += x;
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        if (min >= 0f && Math.abs(sum - 1f) < 0.05f) return v;

        float total = 0f;
        float[] e = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            e[i] = (float) Math.exp(v[i] - max);
            total += e[i];
        }
        for (int i = 0; i < v.length; i++) e[i] /= total;
        return e;
    }

    @Override
    public void close() {
        interpreter.close();
    }
}

package com.example.snaplearn.ml;

/** Một kết quả nhận dạng từ một trong hai mô hình (ImageNet hoặc COCO). */
public class Candidate {
    public static final int IMAGENET = 0; // EfficientNet-Lite0, 1000 lớp
    public static final int COCO = 1;     // SSD MobileNet, 80 loại đồ vật đời thường

    public final int source;
    public final int index;        // chỉ số lớp trong bộ nhãn của mô hình đó
    public final String label;
    public final float confidence; // 0..1

    public Candidate(int source, int index, String label, float confidence) {
        this.source = source;
        this.index = index;
        this.label = label;
        this.confidence = confidence;
    }
}

package com.example.snaplearn.ui;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.snaplearn.data.AppDatabase;
import com.example.snaplearn.R;
import com.example.snaplearn.data.SavedWord;
import com.example.snaplearn.util.Speaker;
import com.example.snaplearn.data.VocabRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Ôn tập từ vựng bằng trắc nghiệm, dùng các từ đã lưu trong sổ. */
public class QuizActivity extends AppCompatActivity {

    private static final int MAX_QUESTIONS = 10;
    private static final int OPTIONS = 4;

    private static final int T_PHOTO_TO_WORD = 0;     // nhìn ảnh, chọn từ tiếng Anh
    private static final int T_WORD_TO_MEANING = 1;   // thấy từ tiếng Anh, chọn nghĩa tiếng Việt
    private static final int T_LISTEN_TO_WORD = 2;    // nghe phát âm, chọn từ tiếng Anh

    private static final int GREEN = Color.parseColor("#2E7D32");
    private static final int RED = Color.parseColor("#C62828");

    private static class Question {
        int type;
        SavedWord word;
        List<String> options = new ArrayList<>();
        int correctIndex;
    }

    private TextView tvProgress, tvQuestion, tvWordBig, tvPhonetic, tvFeedback, tvScore, tvResultMsg, tvEmoji;
    private ImageView ivPhoto;
    private View layoutEmpty, groupQuiz, groupResult;
    private LinearProgressIndicator progressBar;
    private Button btnListen, btnNext, btnRetry, btnExit;
    private MaterialButton[] optionButtons;
    private ColorStateList defaultTint, defaultTextColor, defaultStroke;

    private final Random random = new Random();
    private Speaker speaker;

    private List<SavedWord> saved = new ArrayList<>();
    private final List<String[]> commonPool = new ArrayList<>(); // {en, vi} từ COCO (đời thường)
    private final List<String[]> widePool = new ArrayList<>();   // {en, vi} từ ImageNet
    private List<Question> questions = new ArrayList<>();
    private int current, score;
    private boolean answered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        tvProgress = findViewById(R.id.tvProgress);
        progressBar = findViewById(R.id.progressBar);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvWordBig = findViewById(R.id.tvWordBig);
        tvPhonetic = findViewById(R.id.tvPhonetic);
        tvFeedback = findViewById(R.id.tvFeedback);
        tvScore = findViewById(R.id.tvScore);
        tvResultMsg = findViewById(R.id.tvResultMsg);
        tvEmoji = findViewById(R.id.tvEmoji);
        ivPhoto = findViewById(R.id.ivPhoto);
        groupQuiz = findViewById(R.id.groupQuiz);
        groupResult = findViewById(R.id.groupResult);
        btnListen = findViewById(R.id.btnListen);
        btnNext = findViewById(R.id.btnNext);
        btnRetry = findViewById(R.id.btnRetry);
        btnExit = findViewById(R.id.btnExit);
        optionButtons = new MaterialButton[]{
                findViewById(R.id.btnOpt1), findViewById(R.id.btnOpt2),
                findViewById(R.id.btnOpt3), findViewById(R.id.btnOpt4)};

        defaultTint = optionButtons[0].getBackgroundTintList();
        defaultTextColor = optionButtons[0].getTextColors();
        defaultStroke = optionButtons[0].getStrokeColor();

        speaker = Speaker.getInstance(this);

        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i].setOnClickListener(v -> onAnswer(index));
        }
        btnListen.setOnClickListener(v -> {
            if (current < questions.size()) speaker.speak(questions.get(current).word.en);
        });
        btnNext.setOnClickListener(v -> {
            current++;
            if (current >= questions.size()) showResult(); else showQuestion();
        });
        btnRetry.setOnClickListener(v -> startQuiz());
        btnExit.setOnClickListener(v -> finish());

        // Đọc sổ từ vựng và kho từ làm đáp án gây nhiễu ở luồng nền
        AppDatabase.IO.execute(() -> {
            List<SavedWord> list = AppDatabase.get(this).savedWordDao().getAll();
            List<String[]> common = loadPool("coco_vocab.json", 10000);
            List<String[]> wide = loadPool("vocab.json", 0);
            runOnUiThread(() -> {
                saved = list;
                commonPool.addAll(common);
                widePool.addAll(wide);
                startQuiz();
            });
        });
    }

    private List<String[]> loadPool(String file, int keyOffset) {
        List<String[]> pool = new ArrayList<>();
        try {
            for (VocabRepository.Entry e : new VocabRepository(this, file, keyOffset).all()) {
                pool.add(new String[]{e.en, e.vi});
            }
        } catch (Exception ignored) {
            // thiếu file thì chỉ dùng từ trong sổ
        }
        return pool;
    }

    // ------------------------------------------------------------------ Tạo câu hỏi

    private void startQuiz() {
        groupResult.setVisibility(View.GONE);
        if (saved.isEmpty()) {
            groupQuiz.setVisibility(View.GONE);
            tvProgress.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }
        layoutEmpty.setVisibility(View.GONE);
        tvProgress.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        List<SavedWord> shuffled = new ArrayList<>(saved);
        Collections.shuffle(shuffled, random);
        int n = Math.min(MAX_QUESTIONS, shuffled.size());
        questions = new ArrayList<>();
        for (int i = 0; i < n; i++) questions.add(buildQuestion(shuffled.get(i)));
        current = 0;
        score = 0;
        progressBar.setMax(questions.size());
        progressBar.setProgressCompat(0, false);
        showQuestion();
    }

    private Question buildQuestion(SavedWord w) {
        boolean hasPhoto = w.imagePath != null && new File(w.imagePath).exists();
        List<Integer> types = new ArrayList<>();
        types.add(T_WORD_TO_MEANING);
        types.add(T_LISTEN_TO_WORD);
        if (hasPhoto) types.add(T_PHOTO_TO_WORD);

        Question q = new Question();
        q.word = w;
        q.type = types.get(random.nextInt(types.size()));
        boolean meaning = q.type == T_WORD_TO_MEANING;

        String correct = meaning ? w.vi : w.en;
        q.options.add(correct);
        Set<String> used = new HashSet<>();
        used.add(correct.toLowerCase(Locale.ROOT));

        // Đáp án gây nhiễu: ưu tiên từ khác trong sổ, rồi từ đời thường (COCO), cuối cùng từ ImageNet
        List<SavedWord> others = new ArrayList<>(saved);
        others.remove(w);
        Collections.shuffle(others, random);
        for (SavedWord o : others) {
            if (q.options.size() >= OPTIONS) break;
            addOption(q, used, meaning ? o.vi : o.en);
        }
        fillFromPool(q, used, commonPool, meaning);
        fillFromPool(q, used, widePool, meaning);

        Collections.shuffle(q.options, random);
        q.correctIndex = q.options.indexOf(correct);
        return q;
    }

    private void fillFromPool(Question q, Set<String> used, List<String[]> pool, boolean meaning) {
        if (q.options.size() >= OPTIONS || pool.isEmpty()) return;
        List<String[]> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        for (String[] p : copy) {
            if (q.options.size() >= OPTIONS) break;
            addOption(q, used, meaning ? p[1] : p[0]);
        }
    }

    private void addOption(Question q, Set<String> used, String text) {
        if (text == null || text.isEmpty()) return;
        if (used.add(text.toLowerCase(Locale.ROOT))) q.options.add(text);
    }

    // ------------------------------------------------------------------ Hiển thị

    private void showQuestion() {
        Question q = questions.get(current);
        answered = false;
        groupQuiz.setVisibility(View.VISIBLE);
        tvProgress.setText(getString(R.string.quiz_progress, current + 1, questions.size(), score));
        progressBar.setProgressCompat(current, true);
        tvFeedback.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);

        for (int i = 0; i < optionButtons.length; i++) {
            MaterialButton b = optionButtons[i];
            if (i < q.options.size()) {
                b.setText(q.options.get(i));
                b.setVisibility(View.VISIBLE);
            } else {
                b.setVisibility(View.GONE);
            }
            b.setEnabled(true);
            b.setBackgroundTintList(defaultTint);
            b.setTextColor(defaultTextColor);
            b.setStrokeColor(defaultStroke);
        }

        ivPhoto.setVisibility(View.GONE);
        tvWordBig.setVisibility(View.GONE);
        tvPhonetic.setVisibility(View.GONE);
        btnListen.setVisibility(View.GONE);

        switch (q.type) {
            case T_PHOTO_TO_WORD:
                tvQuestion.setText(R.string.quiz_q_photo);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                Bitmap bmp = BitmapFactory.decodeFile(q.word.imagePath, o);
                ivPhoto.setImageBitmap(bmp);
                ivPhoto.setVisibility(View.VISIBLE);
                break;
            case T_WORD_TO_MEANING:
                tvQuestion.setText(R.string.quiz_q_meaning);
                tvWordBig.setText(q.word.en);
                tvWordBig.setVisibility(View.VISIBLE);
                if (q.word.phonetic != null && !q.word.phonetic.isEmpty()) {
                    tvPhonetic.setText(q.word.phonetic);
                    tvPhonetic.setVisibility(View.VISIBLE);
                }
                btnListen.setVisibility(View.VISIBLE);
                break;
            default: // T_LISTEN_TO_WORD
                tvQuestion.setText(R.string.quiz_q_listen);
                btnListen.setVisibility(View.VISIBLE);
                final String word = q.word.en;
                btnListen.postDelayed(() -> speaker.speak(word), 600); // tự đọc 1 lần khi hiện câu hỏi
                break;
        }
    }

    private void onAnswer(int chosen) {
        if (answered) return;
        answered = true;
        Question q = questions.get(current);
        boolean correct = chosen == q.correctIndex;
        if (correct) score++;

        for (MaterialButton b : optionButtons) b.setEnabled(false);
        paint(optionButtons[q.correctIndex], GREEN);
        if (!correct) paint(optionButtons[chosen], RED);

        SavedWord w = q.word;
        String detail = w.en + (w.phonetic != null && !w.phonetic.isEmpty() ? "  " + w.phonetic : "") + "  -  " + w.vi;
        String head = correct ? getString(R.string.quiz_correct)
                : getString(R.string.quiz_wrong, q.options.get(q.correctIndex));

        // Dòng đầu in đậm, tô xanh nếu đúng, đỏ nếu sai; dòng sau là thông tin từ
        SpannableString fb = new SpannableString(head + "\n" + detail);
        fb.setSpan(new StyleSpan(Typeface.BOLD), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fb.setSpan(new ForegroundColorSpan(correct ? GREEN : RED), 0, head.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvFeedback.setText(fb);
        tvFeedback.setVisibility(View.VISIBLE);

        tvProgress.setText(getString(R.string.quiz_progress, current + 1, questions.size(), score));
        progressBar.setProgressCompat(current + 1, true);
        btnNext.setText(current + 1 >= questions.size() ? R.string.quiz_finish : R.string.quiz_next);
        btnNext.setVisibility(View.VISIBLE);
        speaker.speak(w.en); // đọc lại từ đúng để nhớ phát âm
    }

    private void paint(MaterialButton b, int color) {
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setStrokeColor(ColorStateList.valueOf(color));
        b.setTextColor(Color.WHITE);
    }

    private void showResult() {
        groupQuiz.setVisibility(View.GONE);
        groupResult.setVisibility(View.VISIBLE);
        tvProgress.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        int total = questions.size();
        tvScore.setText(getString(R.string.quiz_score, score, total));
        double ratio = total == 0 ? 0 : (double) score / total;
        tvEmoji.setText(ratio >= 0.8 ? "🏆" : ratio >= 0.5 ? "👍" : "💪");
        tvResultMsg.setText(ratio >= 0.8 ? R.string.quiz_msg_great
                : ratio >= 0.5 ? R.string.quiz_msg_good : R.string.quiz_msg_try);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
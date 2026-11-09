package com.example.snaplearn.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.snaplearn.R;
import com.example.snaplearn.data.CloudSync;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chưa đăng nhập thì chuyển sang màn hình đăng nhập
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        // Đã đăng nhập sẵn từ lần trước: đồng bộ với Firestore (1 lần mỗi lần mở app)
        CloudSync.syncOnce(this);

        // Lời chào: ưu tiên tên hiển thị, không có thì dùng phần đầu email
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            String email = user.getEmail() == null ? "" : user.getEmail();
            name = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        }
        tvWelcome.setText(getString(R.string.welcome_user, name));

        Button btnCapture = findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class)));

        Button btnQuiz = findViewById(R.id.btnQuiz);
        btnQuiz.setOnClickListener(v ->
                startActivity(new Intent(this, QuizActivity.class)));

        Button btnNotebook = findViewById(R.id.btnNotebook);
        btnNotebook.setOnClickListener(v ->
                startActivity(new Intent(this, NotebookActivity.class)));

        Button btnHistory = findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            CloudSync.clearLocal(this);          // xóa dữ liệu trên máy (vẫn còn trên Firestore)
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, R.string.logout_done, Toast.LENGTH_SHORT).show();
            goLogin();
        });
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}

    package com.example.snaplearn.ui;

    import android.content.Intent;
    import android.os.Bundle;
    import android.util.Patterns;
    import android.view.View;
    import android.widget.ProgressBar;
    import android.widget.Toast;

    import androidx.appcompat.app.AppCompatActivity;

    import com.example.snaplearn.R;
    import com.example.snaplearn.data.CloudSync;
    import com.google.android.material.button.MaterialButton;
    import com.google.android.material.dialog.MaterialAlertDialogBuilder;
    import com.google.android.material.textfield.TextInputEditText;
    import com.google.android.material.textfield.TextInputLayout;
    import com.google.firebase.auth.FirebaseAuth;

    public class LoginActivity extends AppCompatActivity {

        private FirebaseAuth auth;
        private TextInputLayout tilEmail, tilPassword;
        private TextInputEditText edtEmail, edtPassword;
        private MaterialButton btnLogin;
        private ProgressBar progress;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_login);

            auth = FirebaseAuth.getInstance();

            tilEmail = findViewById(R.id.tilEmail);
            tilPassword = findViewById(R.id.tilPassword);
            edtEmail = findViewById(R.id.edtEmail);
            edtPassword = findViewById(R.id.edtPassword);
            btnLogin = findViewById(R.id.btnLogin);
            progress = findViewById(R.id.progress);

            btnLogin.setOnClickListener(v -> doLogin());
            findViewById(R.id.tvForgot).setOnClickListener(v -> showResetDialog());
            findViewById(R.id.tvGoRegister).setOnClickListener(v ->
                    startActivity(new Intent(this, RegisterActivity.class)));
        }

        @Override
        protected void onStart() {
            super.onStart();
            // Đã đăng nhập sẵn (ví dụ vừa đăng ký xong) thì vào thẳng app
            if (auth.getCurrentUser() != null) goMain();
        }

        private void doLogin() {
            tilEmail.setError(null);
            tilPassword.setError(null);

            String email = text(edtEmail);
            String pass = text(edtPassword);

            if (email.isEmpty()) { tilEmail.setError(getString(R.string.err_email_empty)); return; }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError(getString(R.string.err_email_invalid)); return;
            }
            if (pass.isEmpty()) { tilPassword.setError(getString(R.string.err_password_empty)); return; }

            setLoading(true);
            auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener(r -> CloudSync.pullAll(this, this::goMain)) // kéo dữ liệu về rồi vào app
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, AuthErrors.message(this, e), Toast.LENGTH_LONG).show();
                    });
        }

        /** Hộp thoại quên mật khẩu: nhập email, Firebase gửi thư đặt lại. */
        private void showResetDialog() {
            TextInputLayout til = new TextInputLayout(this);
            til.setHint(getString(R.string.hint_email));
            TextInputEditText input = new TextInputEditText(til.getContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            input.setText(text(edtEmail));
            til.addView(input);
            int pad = (int) (24 * getResources().getDisplayMetrics().density);
            android.widget.FrameLayout box = new android.widget.FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(til);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.reset_title)
                    .setMessage(R.string.reset_message)
                    .setView(box)
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.reset_send, (d, w) -> {
                        String email = input.getText() == null ? "" : input.getText().toString().trim();
                        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            Toast.makeText(this, R.string.err_email_invalid, Toast.LENGTH_LONG).show();
                            return;
                        }
                        auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener(x -> Toast.makeText(this,
                                        getString(R.string.reset_sent, email), Toast.LENGTH_LONG).show())
                                .addOnFailureListener(e -> Toast.makeText(this,
                                        AuthErrors.message(this, e), Toast.LENGTH_LONG).show());
                    })
                    .show();
        }

        private void goMain() {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }

        private void setLoading(boolean loading) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!loading);
        }

        private static String text(TextInputEditText e) {
            return e.getText() == null ? "" : e.getText().toString().trim();
        }
    }

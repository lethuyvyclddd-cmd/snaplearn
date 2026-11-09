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
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText edtName, edtEmail, edtPassword, edtConfirm;
    private MaterialButton btnRegister;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);
        edtName = findViewById(R.id.edtName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirm = findViewById(R.id.edtConfirm);
        btnRegister = findViewById(R.id.btnRegister);
        progress = findViewById(R.id.progress);

        btnRegister.setOnClickListener(v -> doRegister());
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> finish());
    }

    private void doRegister() {
        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        String name = text(edtName);
        String email = text(edtEmail);
        String pass = edtPassword.getText() == null ? "" : edtPassword.getText().toString();
        String confirm = edtConfirm.getText() == null ? "" : edtConfirm.getText().toString();

        if (name.isEmpty()) { tilName.setError(getString(R.string.err_name_empty)); return; }
        if (email.isEmpty()) { tilEmail.setError(getString(R.string.err_email_empty)); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.err_email_invalid)); return;
        }
        if (pass.length() < 6) { tilPassword.setError(getString(R.string.err_password_short)); return; }
        if (!pass.equals(confirm)) { tilConfirm.setError(getString(R.string.err_confirm_mismatch)); return; }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) { CloudSync.pullAll(this, this::goMain); return; }
                    // Lưu tên hiển thị rồi vào app (có lỗi cũng vẫn vào được)
                    UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build();
                    user.updateProfile(req).addOnCompleteListener(t -> CloudSync.pullAll(this, this::goMain));
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, AuthErrors.message(this, e), Toast.LENGTH_LONG).show();
                });
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}

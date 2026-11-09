package com.example.snaplearn.ui;

import android.content.Context;

import com.example.snaplearn.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Đổi lỗi của Firebase thành câu tiếng Việt dễ hiểu. */
final class AuthErrors {

    private AuthErrors() {}

    static String message(Context c, Exception e) {
        if (e instanceof FirebaseNetworkException) return c.getString(R.string.err_network);
        if (e instanceof FirebaseTooManyRequestsException) return c.getString(R.string.err_too_many);
        // WeakPassword là lớp con của InvalidCredentials nên phải kiểm tra trước
        if (e instanceof FirebaseAuthWeakPasswordException) return c.getString(R.string.err_weak_password);
        if (e instanceof FirebaseAuthUserCollisionException) return c.getString(R.string.err_email_in_use);
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return c.getString(R.string.err_wrong_credential);
        }
        String detail = (e != null && e.getMessage() != null) ? e.getMessage() : "";
        return c.getString(R.string.err_unknown, detail);
    }
}

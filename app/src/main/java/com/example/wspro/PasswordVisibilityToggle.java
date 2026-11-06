package com.example.wspro;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageButton;

final class PasswordVisibilityToggle {
    private PasswordVisibilityToggle() {}

    static void bind(EditText input, ImageButton button) {
        button.setImageResource(R.drawable.ic_eye);
        button.setContentDescription("Show password");
        button.setOnClickListener(view -> {
            int selectionStart = input.getSelectionStart();
            int selectionEnd = input.getSelectionEnd();
            boolean showing = input.getTransformationMethod()
                    instanceof PasswordTransformationMethod;
            input.setTransformationMethod(showing
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            button.setImageResource(showing ? R.drawable.ic_eye_off : R.drawable.ic_eye);
            button.setContentDescription(showing ? "Hide password" : "Show password");
            int textLength = input.length();
            if (selectionStart >= 0 && selectionEnd >= 0) {
                input.setSelection(Math.min(selectionStart, textLength),
                        Math.min(selectionEnd, textLength));
            }
        });
    }
}

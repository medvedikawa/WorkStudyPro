package com.example.wspro;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

/**
 * Reusable Code/OTP Input View.
 * Supports auto-advance between boxes, backspace navigation, paste distribution,
 * error state styling, and completion callbacks.
 */
public class CodeInputView extends LinearLayout {

    public static final int DEFAULT_CODE_LENGTH = 6;
    private int codeLength = DEFAULT_CODE_LENGTH;

    private CodeBoxEditText[] editTexts;
    private OnCodeCompleteListener onCodeCompleteListener;
    private boolean isDistributing = false;
    private boolean hasError = false;

    public interface OnCodeCompleteListener {
        void onCodeComplete(@NonNull String code);
        void onCodeChanged(@NonNull String code);
    }

    public CodeInputView(Context context) {
        super(context);
        init(context, null);
    }

    public CodeInputView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CodeInputView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);
        buildBoxes();
    }

    public void setCodeLength(int length) {
        if (length > 0 && length != this.codeLength) {
            this.codeLength = length;
            buildBoxes();
        }
    }

    public int getCodeLength() {
        return codeLength;
    }

    private void buildBoxes() {
        removeAllViews();
        editTexts = new CodeBoxEditText[codeLength];

        int heightPx = dpToPx(52);
        int marginPx = dpToPx(4);

        for (int i = 0; i < codeLength; i++) {
            final int index = i;
            CodeBoxEditText editText = new CodeBoxEditText(getContext());
            editText.setPasteListener(text -> pasteCode(text, index));

            LayoutParams lp = new LayoutParams(0, heightPx, 1.0f);
            lp.setMargins(marginPx, 0, marginPx, 0);
            editText.setLayoutParams(lp);

            editText.setBackgroundResource(R.drawable.bg_code_box);
            editText.setGravity(Gravity.CENTER);
            editText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            editText.setTextColor(ContextCompat.getColor(getContext(), R.color.ws_text));
            editText.setTypeface(Typeface.DEFAULT_BOLD);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setImeOptions(i == codeLength - 1 ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
            editText.setCursorVisible(true);
            editText.setMaxLines(1);
            editText.setSelectAllOnFocus(true);

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(Editable s) {
                    if (isDistributing) return;

                    if (hasError) {
                        setError(false);
                    }

                    if (s == null) return;
                    String text = s.toString();

                    if (text.length() > 1) {
                        pasteCode(text, index);
                        return;
                    }

                    if (text.length() == 1) {
                        if (index < codeLength - 1) {
                            editTexts[index + 1].requestFocus();
                            editTexts[index + 1].selectAll();
                        }
                    }

                    notifyCodeChanged();
                }
            });

            editText.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (editText.getText().length() == 0 && index > 0) {
                        editTexts[index - 1].requestFocus();
                        editTexts[index - 1].setText("");
                        return true;
                    }
                }
                return false;
            });

            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    editText.selectAll();
                }
            });

            editTexts[i] = editText;
            addView(editText);
        }
    }

    public void pasteCode(@Nullable String rawCode) {
        pasteCode(rawCode, 0);
    }

    public void pasteCode(@Nullable String rawCode, int startIndex) {
        if (rawCode == null || rawCode.isEmpty() || editTexts == null) return;

        // Strip non-digit characters (OTP is numeric)
        String digits = rawCode.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            // Fallback for alphanumeric tokens if any
            digits = rawCode.trim().replaceAll("\\s+", "");
        }
        if (digits.isEmpty()) return;

        isDistributing = true;
        try {
            int targetStart = (digits.length() >= codeLength) ? 0 : startIndex;
            int digitIndex = 0;

            for (int i = targetStart; i < codeLength && digitIndex < digits.length(); i++) {
                editTexts[i].setText(String.valueOf(digits.charAt(digitIndex)));
                digitIndex++;
            }

            int nextFocus = Math.min(targetStart + digitIndex, codeLength - 1);
            editTexts[nextFocus].requestFocus();
            editTexts[nextFocus].setSelection(editTexts[nextFocus].getText().length());
        } finally {
            isDistributing = false;
        }

        notifyCodeChanged();
    }

    private void notifyCodeChanged() {
        String code = getCode();
        if (onCodeCompleteListener != null) {
            onCodeCompleteListener.onCodeChanged(code);
            if (code.length() == codeLength) {
                onCodeCompleteListener.onCodeComplete(code);
            }
        }
    }

    @NonNull
    public String getCode() {
        if (editTexts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (EditText et : editTexts) {
            if (et != null && et.getText() != null) {
                sb.append(et.getText().toString().trim());
            }
        }
        return sb.toString();
    }

    public boolean isComplete() {
        return getCode().length() == codeLength;
    }

    public void clearCode() {
        if (editTexts == null) return;
        isDistributing = true;
        try {
            for (EditText et : editTexts) {
                if (et != null) et.setText("");
            }
        } finally {
            isDistributing = false;
        }
        setError(false);
        requestInputFocus();
        notifyCodeChanged();
    }

    public void setCode(@Nullable String code) {
        clearCode();
        if (code != null && !code.isEmpty()) {
            pasteCode(code, 0);
        }
    }

    public void setError(boolean error) {
        this.hasError = error;
        if (editTexts == null) return;
        int resId = error ? R.drawable.bg_code_box_error : R.drawable.bg_code_box;
        for (EditText et : editTexts) {
            if (et != null) {
                et.setBackgroundResource(resId);
            }
        }
    }

    public void requestInputFocus() {
        if (editTexts == null || editTexts.length == 0) return;
        for (EditText et : editTexts) {
            if (et.getText().length() == 0) {
                et.requestFocus();
                return;
            }
        }
        editTexts[editTexts.length - 1].requestFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (editTexts == null) return;
        for (EditText et : editTexts) {
            if (et != null) {
                et.setEnabled(enabled);
            }
        }
    }

    public void setOnCodeCompleteListener(OnCodeCompleteListener listener) {
        this.onCodeCompleteListener = listener;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    /**
     * Internal EditText with custom paste listener to capture system context menu paste operations.
     */
    public static class CodeBoxEditText extends AppCompatEditText {
        private OnPasteListener pasteListener;

        public interface OnPasteListener {
            void onPaste(String pastedText);
        }

        public CodeBoxEditText(@NonNull Context context) {
            super(context);
        }

        public CodeBoxEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public void setPasteListener(OnPasteListener listener) {
            this.pasteListener = listener;
        }

        @Override
        public boolean onTextContextMenuItem(int id) {
            if (id == android.R.id.paste) {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                    ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                    CharSequence text = item.coerceToText(getContext());
                    if (text != null && text.length() > 0) {
                        if (pasteListener != null) {
                            pasteListener.onPaste(text.toString());
                            return true;
                        }
                    }
                }
            }
            return super.onTextContextMenuItem(id);
        }
    }
}

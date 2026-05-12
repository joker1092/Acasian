package com.acasian.iot;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.acasian.iot.model.request.LoginRequest;
import com.acasian.iot.model.response.LoginResponse;
import com.acasian.iot.repository.UserRepository;
import com.acasian.iot.storage.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private LinearLayout containerPhoneField;
    private LinearLayout containerPasswordField;
    private EditText     etPhone;
    private EditText     etPassword;
    private ImageView    btnTogglePassword;
    private Button       btnLogin;
    private ProgressBar  progressBar;

    private Drawable bgNormal;
    private Drawable bgFocused;
    private Drawable bgError;

    private boolean isPasswordVisible = false;

    private SessionManager   session;
    private UserRepository   userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        setContentView(R.layout.activity_login);

        // 상태바 높이만큼 헤더 상단 패딩 추가
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
            int statusH = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            // NestedScrollView → LinearLayout → FrameLayout(헤더) → LinearLayout(콘텐츠, index 1)
            try {
                android.view.View nsv = ((android.view.ViewGroup) v).getChildAt(0);
                android.view.View root = ((android.view.ViewGroup) nsv).getChildAt(0);
                android.view.View frame = ((android.view.ViewGroup) root).getChildAt(0);
                android.view.View hContent = ((android.view.ViewGroup) frame).getChildAt(1);
                final int basePad = (int) (56 * v.getResources().getDisplayMetrics().density);
                hContent.setPadding(
                        hContent.getPaddingLeft(),
                        basePad + statusH,
                        hContent.getPaddingRight(),
                        hContent.getPaddingBottom()
                );
            } catch (Exception ignored) {}
            return insets;
        });

        session         = SessionManager.getInstance(this);
        userRepository  = new UserRepository(this);

        initViews();
        setupPhoneFormatter();
        setupClickListeners();
    }

    // ── 뷰 초기화 ────────────────────────────────────────────────────────
    private void initViews() {
        containerPhoneField    = findViewById(R.id.containerPhoneField);
        containerPasswordField = findViewById(R.id.containerPasswordField);
        etPhone                = findViewById(R.id.etPhone);
        etPassword             = findViewById(R.id.etPassword);
        btnTogglePassword      = findViewById(R.id.btnTogglePassword);
        btnLogin               = findViewById(R.id.btnLogin);
        progressBar            = findViewById(R.id.progressBar);

        bgNormal  = ContextCompat.getDrawable(this, R.drawable.bg_input_normal);
        bgFocused = ContextCompat.getDrawable(this, R.drawable.bg_input_focused);
        bgError   = ContextCompat.getDrawable(this, R.drawable.bg_input_error);

        etPhone.setOnFocusChangeListener((v, hasFocus) ->
                containerPhoneField.setBackground(hasFocus ? bgFocused : bgNormal));

        etPassword.setOnFocusChangeListener((v, hasFocus) ->
                containerPasswordField.setBackground(hasFocus ? bgFocused : bgNormal));
    }

    // ── 전화번호 자동 하이픈 포맷 (010-0000-0000) ────────────────────────
    private void setupPhoneFormatter() {
        etPhone.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            private String  prev = "";

            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("[^0-9]", "");
                String formatted;

                if (digits.length() <= 3) {
                    formatted = digits;
                } else if (digits.length() <= 7) {
                    formatted = digits.substring(0, 3) + "-" + digits.substring(3);
                } else if (digits.length() <= 11) {
                    formatted = digits.substring(0, 3) + "-"
                              + digits.substring(3, 7) + "-"
                              + digits.substring(7);
                } else {
                    formatted = prev;
                }

                s.replace(0, s.length(), formatted);
                prev = formatted;
                isFormatting = false;
            }
        });
    }

    // ── 클릭 리스너 ─────────────────────────────────────────────────────
    private void setupClickListeners() {
        if (btnLogin != null) btnLogin.setOnClickListener(v -> attemptLogin());

        // 비밀번호 표시/숨기기 토글
        if (btnTogglePassword != null) btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_eye_on);
            } else {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_eye_off);
            }
            // 커서를 텍스트 끝으로 이동
            etPassword.setSelection(etPassword.getText().length());
        });

        // 배경 탭 → 키보드 숨기기
        View root = findViewById(android.R.id.content);
        if (root != null) root.setOnClickListener(v -> hideKeyboard());
    }

    // ── 로그인 실행 ──────────────────────────────────────────────────────
    private void attemptLogin() {
        String phone    = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString();

        boolean hasError = false;

        // 전화번호 유효성 검사
        if (phone.isEmpty()) {
            setFieldError(containerPhoneField, true);
            Toast.makeText(this, "전화번호를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            hasError = true;
        } else {
            String digits = phone.replaceAll("[^0-9]", "");
            if (digits.length() < 10 || digits.length() > 11) {
                setFieldError(containerPhoneField, true);
                Toast.makeText(this, "올바른 전화번호를 입력해 주세요.", Toast.LENGTH_SHORT).show();
                hasError = true;
            } else {
                setFieldError(containerPhoneField, false);
            }
        }

        // 비밀번호 유효성 검사
        if (password.isEmpty()) {
            setFieldError(containerPasswordField, true);
            if (!hasError) Toast.makeText(this, "비밀번호를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            hasError = true;
        } else {
            setFieldError(containerPasswordField, false);
        }

        if (hasError) return;

        hideKeyboard();
        setLoading(true);

        String userId = phone.replaceAll("[^0-9]", "");

        // ── 데모 계정: API 없이 더미 데이터로 바로 진입 ────────────────
        if ("01000000000".equals(userId) && "0000".equals(password)) {
            setLoading(false);
            // ── DEV_MODE 활성화 + 더미 데이터 주입 ──────────────
            AppConfig.getInstance().setDevMode(true);
            AppConfig.injectDemoZones();                    // ZoneStore 채우기
            DemoData.applyProfiles(this);                   // 관수 유형 프리셋
            // ─────────────────────────────────────────────────────
            session.saveLogin(phone, "데모 농장");
            Intent intent = new Intent(this, HomeDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            return;
        }
        // ────────────────────────────────────────────────────────────────

        // ── API 연동 ────────────────────────────────────────────────────
        userRepository.login(userId, password, new UserRepository.LoginCallback() {
            @Override
            public void onSuccess(com.acasian.iot.model.response.LoginResponse response) {
                runOnUiThread(() -> {
                    setLoading(false);
                    // userId + 농장명(nickName) 세션 저장
                    String farmName = response.getFarmName();
                    session.saveLogin(phone, farmName != null ? farmName : "");
                    goToMain();
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
        // ────────────────────────────────────────────────────────────────
    }

    // ── 화면 전환 ────────────────────────────────────────────────────────
    private void goToMain() {
        // 실 계정 로그인 — DEV_MODE 비활성
        AppConfig.getInstance().setDevMode(false);
        Intent intent = new Intent(this, HomeDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────
    private void setFieldError(LinearLayout container, boolean isError) {
        container.setBackground(isError ? bgError : bgNormal);
    }

    private void setLoading(boolean loading) {
        if (btnLogin     != null) btnLogin.setEnabled(!loading);
        if (progressBar  != null) progressBar.setVisibility(
                loading ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}

package com.acasian.iot;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.acasian.iot.storage.SessionManager;
import com.acasian.iot.view.StarFieldView;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION  = 3000L;
    private static final long LOGO_ANIM_DELAY  = 400L;
    private static final long LOADER_ANIM_DELAY = 900L;

    private LinearLayout logoGroup;
    private LinearLayout loaderGroup;
    private View dot1, dot2, dot3;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        // ── 엣지-투-엣지 (API 29+에서 권장) ──
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // API 30+ : WindowInsetsController 사용
        // API 29   : deprecated setSystemUiVisibility 대신 WindowCompat 사용
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().getInsetsController().hide(
                android.view.WindowInsets.Type.statusBars() |
                android.view.WindowInsets.Type.navigationBars()
            );
            getWindow().getInsetsController().setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            // API 29 fallback
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }


        initViews();
        startAnimations();
        scheduleNavigation();
    }

    private void initViews() {
        logoGroup   = findViewById(R.id.logoGroup);
        loaderGroup = findViewById(R.id.loaderGroup);
        dot1        = findViewById(R.id.dot1);
        dot2        = findViewById(R.id.dot2);
        dot3        = findViewById(R.id.dot3);
    }

    private void startAnimations() {
        // 로고 페이드인 + 슬라이드업
        handler.postDelayed(() -> {
            AnimatorSet logoAnim = new AnimatorSet();
            logoAnim.playTogether(
                ObjectAnimator.ofFloat(logoGroup, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logoGroup, View.TRANSLATION_Y, 40f, 0f)
            );
            logoAnim.setDuration(800);
            logoAnim.setInterpolator(new DecelerateInterpolator(2f));
            logoAnim.start();
        }, LOGO_ANIM_DELAY);

        // 로딩 닷 페이드인
        handler.postDelayed(() -> {
            ObjectAnimator.ofFloat(loaderGroup, View.ALPHA, 0f, 1f)
                .setDuration(500);
            ObjectAnimator loaderFade = ObjectAnimator.ofFloat(loaderGroup, View.ALPHA, 0f, 1f);
            loaderFade.setDuration(500);
            loaderFade.start();
            startDotPulse();
        }, LOADER_ANIM_DELAY);
    }

    private void startDotPulse() {
        animateDot(dot1, 0);
        animateDot(dot2, 220);
        animateDot(dot3, 440);
    }

    private void animateDot(View dot, long delay) {
        Runnable pulse = new Runnable() {
            @Override
            public void run() {
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                    ObjectAnimator.ofFloat(dot, View.ALPHA, 0.2f, 1f, 0.2f),
                    ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.8f, 1.3f, 0.8f),
                    ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.8f, 1.3f, 0.8f)
                );
                set.setDuration(1400);
                set.start();
                handler.postDelayed(this, 1400);
            }
        };
        handler.postDelayed(pulse, delay);
    }

    private void scheduleNavigation() {
        handler.postDelayed(() -> {
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(
                getWindow().getDecorView(), View.ALPHA, 1f, 0f);
            fadeOut.setDuration(400);
            fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    navigateNext();
                }
            });
            fadeOut.start();
        }, SPLASH_DURATION);
    }

    private void navigateNext() {
        SessionManager session = SessionManager.getInstance(this);
        Intent intent;

        // 항상 LoginActivity로 이동
        intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

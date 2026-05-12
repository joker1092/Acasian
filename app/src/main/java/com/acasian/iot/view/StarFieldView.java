package com.acasian.iot.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws animated twinkling stars for the splash screen.
 */
public class StarFieldView extends View {

    private static final int STAR_COUNT = 40;
    private static final long FRAME_DELAY_MS = 50;

    private final List<Star> stars = new ArrayList<>();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean attached = false;

    private final Runnable drawLoop = new Runnable() {
        @Override
        public void run() {
            if (!attached) return;
            for (Star s : stars) s.update();
            invalidate();
            handler.postDelayed(this, FRAME_DELAY_MS);
        }
    };

    private static class Star {
        float x, y, radius;
        float alpha;
        float alphaSpeed;
        float alphaMin, alphaMax;
        boolean increasing;

        Star(float x, float y, float radius, float alpha,
              float alphaSpeed, float alphaMin, float alphaMax) {
            this.x = x; this.y = y;
            this.radius = radius;
            this.alpha = alpha;
            this.alphaSpeed = alphaSpeed;
            this.alphaMin = alphaMin;
            this.alphaMax = alphaMax;
            this.increasing = true;
        }

        void update() {
            if (increasing) {
                alpha += alphaSpeed;
                if (alpha >= alphaMax) { alpha = alphaMax; increasing = false; }
            } else {
                alpha -= alphaSpeed;
                if (alpha <= alphaMin) { alpha = alphaMin; increasing = true; }
            }
        }
    }

    public StarFieldView(@NonNull Context context) {
        super(context);
        init();
    }

    public StarFieldView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        generateStars(w, h);
    }

    private void generateStars(int w, int h) {
        stars.clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            float x = random.nextFloat() * w;
            // Concentrate stars in upper 60% of screen
            float y = random.nextFloat() * (h * 0.6f);
            float radius = 0.8f + random.nextFloat() * 1.8f;
            float alphaMin = 0.05f + random.nextFloat() * 0.15f;
            float alphaMax = 0.5f  + random.nextFloat() * 0.5f;
            float alphaSpeed = 0.005f + random.nextFloat() * 0.015f;
            float alpha = alphaMin + random.nextFloat() * (alphaMax - alphaMin);
            stars.add(new Star(x, y, radius, alpha, alphaSpeed, alphaMin, alphaMax));
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for (Star s : stars) {
            starPaint.setAlpha((int)(s.alpha * 255));
            canvas.drawCircle(s.x, s.y, s.radius, starPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        handler.post(drawLoop);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        handler.removeCallbacks(drawLoop);
    }
}

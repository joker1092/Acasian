package com.acasian.iot.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom view that draws layered forest tree silhouettes and a moon glow
 * for the splash screen background.
 */
public class ForestSilhouetteView extends View {

    private final Paint treePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mistPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Path farTreesPath;
    private Path midTreesPath;
    private Path nearTreesPath;
    private Path groundPath;
    private Path mistPath;

    public ForestSilhouetteView(@NonNull Context context) {
        super(context);
        init();
    }

    public ForestSilhouetteView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        treePaint.setStyle(Paint.Style.FILL);
        groundPaint.setStyle(Paint.Style.FILL);
        groundPaint.setColor(0xFF1B5E20);
        mistPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildPaths(w, h);
        buildMoonGlow(w, h);
    }

    private void buildPaths(int w, int h) {
        float sh = h;   // screen height
        float sw = w;   // screen width

        // ── Far trees (darkest) ──
        farTreesPath = new Path();
        addTree(farTreesPath, sw * 0.07f, sh,       sw * 0.11f, sh * 0.72f, sw * 0.04f);
        addTree(farTreesPath, sw * 0.13f, sh,       sw * 0.19f, sh * 0.63f, sw * 0.06f);
        addTree(farTreesPath, sw * 0.21f, sh,       sw * 0.25f, sh * 0.70f, sw * 0.04f);
        addTree(farTreesPath, sw * 0.75f, sh,       sw * 0.81f, sh * 0.65f, sw * 0.06f);
        addTree(farTreesPath, sw * 0.85f, sh,       sw * 0.89f, sh * 0.72f, sw * 0.04f);
        addTree(farTreesPath, sw * 0.93f, sh,       sw * 0.97f, sh * 0.67f, sw * 0.04f);

        // ── Mid trees ──
        midTreesPath = new Path();
        addTree(midTreesPath, -sw * 0.02f, sh,      sw * 0.06f, sh * 0.67f, sw * 0.08f);
        addTree(midTreesPath,  sw * 0.09f, sh,       sw * 0.17f, sh * 0.60f, sw * 0.10f);
        addTree(midTreesPath,  sw * 0.20f, sh,       sw * 0.30f, sh * 0.63f, sw * 0.12f);
        addTree(midTreesPath,  sw * 0.62f, sh,       sw * 0.72f, sh * 0.62f, sw * 0.12f);
        addTree(midTreesPath,  sw * 0.74f, sh,       sw * 0.83f, sh * 0.65f, sw * 0.10f);
        addTree(midTreesPath,  sw * 0.86f, sh,       sw * 0.95f, sh * 0.67f, sw * 0.09f);
        addTree(midTreesPath,  sw * 0.94f, sh,  sw * 1.02f, sh * 0.68f, sw * 0.08f);

        // ── Near trees (foreground) ──
        nearTreesPath = new Path();
        addTree(nearTreesPath, -sw * 0.03f, sh,     sw * 0.07f, sh * 0.75f, sw * 0.10f);
        addTree(nearTreesPath,  sw * 0.16f, sh,      sw * 0.26f, sh * 0.68f, sw * 0.14f);
        addTree(nearTreesPath,  sw * 0.31f, sh,      sw * 0.41f, sh * 0.72f, sw * 0.12f);
        addTree(nearTreesPath,  sw * 0.50f, sh,      sw * 0.61f, sh * 0.70f, sw * 0.13f);
        addTree(nearTreesPath,  sw * 0.68f, sh,      sw * 0.79f, sh * 0.72f, sw * 0.13f);
        addTree(nearTreesPath,  sw * 0.87f, sh,      sw * 0.97f, sh * 0.75f, sw * 0.12f);
        addTree(nearTreesPath,  sw * 0.94f, sh,  sw * 1.04f, sh * 0.77f, sw * 0.10f);

        // Ground
        groundPath = new Path();
        groundPath.moveTo(0, sh * 0.91f);
        groundPath.lineTo(sw, sh * 0.91f);
        groundPath.lineTo(sw, sh);
        groundPath.lineTo(0, sh);
        groundPath.close();

        // Mist layer
        mistPath = new Path();
        mistPath.moveTo(0, sh * 0.82f);
        mistPath.cubicTo(sw * 0.25f, sh * 0.78f, sw * 0.75f, sh * 0.88f, sw, sh * 0.82f);
        mistPath.lineTo(sw, sh * 0.92f);
        mistPath.cubicTo(sw * 0.75f, sh * 0.86f, sw * 0.25f, sh * 0.96f, 0, sh * 0.90f);
        mistPath.close();
    }

    private void addTree(Path path, float baseLeft, float baseY,
                          float apexX, float apexY, float halfBase) {
        path.moveTo(baseLeft, baseY);
        path.lineTo(apexX, apexY);
        path.lineTo(baseLeft + halfBase * 2, baseY);
        path.close();
    }

    private void buildMoonGlow(int w, int h) {
        float moonX = w * 0.82f;
        float moonY = h * 0.12f;
        moonPaint.setShader(new RadialGradient(
                moonX, moonY, w * 0.20f,
                new int[]{ 0xFFFFFFEE, 0x80FFFFCC, 0x20FFFF99, 0x00FFFF00 },
                new float[]{ 0.0f, 0.25f, 0.6f, 1.0f },
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (farTreesPath == null) return;

        int w = getWidth();
        int h = getHeight();

        // Moon glow
        canvas.drawCircle(w * 0.82f, h * 0.12f, w * 0.20f, moonPaint);

        // Far trees
        treePaint.setColor(0xFF1B5E20);
        canvas.drawPath(farTreesPath, treePaint);

        // Mid trees
        treePaint.setColor(0xFF2E7D32);
        canvas.drawPath(midTreesPath, treePaint);

        // Mist
        mistPaint.setColor(0x1466BB6A);
        canvas.drawPath(mistPath, mistPaint);

        // Near trees
        treePaint.setColor(0xFF388E3C);
        canvas.drawPath(nearTreesPath, treePaint);

        // Ground
        canvas.drawPath(groundPath, groundPaint);
    }
}

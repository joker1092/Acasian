package com.acasian.iot.Calendar.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import androidx.core.content.ContextCompat;

import com.acasian.iot.R;

public class WheelTimePickerView extends View {

    public interface OnValueChangeListener {
        void onValueChange(WheelTimePickerView view, int oldVal, int newVal);
    }

    private int      minValue   = 0;
    private int      maxValue   = 23;
    private String[] displayedValues;
    private boolean  wrapAround = true;
    private OnValueChangeListener listener;

    // 스크롤: initVal(정수 확정값) + totalScrollPx(픽셀 오프셋)
    private int   initVal       = 0;
    private float totalScrollPx = 0f;
    private boolean isAnimating = false;

    private static final int VISIBLE_ITEMS = 5;
    private int itemH = 0;

    private final Paint pSel  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNorm = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDiv  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float spSel, spNorm;

    private VelocityTracker velTracker;
    private float   lastY;
    private boolean dragging;
    private int     touchSlop;

    private final Scroller scroller;

    public WheelTimePickerView(Context context) { this(context, null); }

    public WheelTimePickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = context.getResources().getDisplayMetrics().density;
        spSel  = 34 * d;
        spNorm = 17 * d;

        pSel.setTextAlign(Paint.Align.CENTER);
        pSel.setTypeface(Typeface.DEFAULT_BOLD);
        pSel.setColor(ContextCompat.getColor(context, R.color.forest_dark));
        pSel.setTextSize(spSel);

        pNorm.setTextAlign(Paint.Align.CENTER);
        pNorm.setTypeface(Typeface.DEFAULT);
        pNorm.setColor(ContextCompat.getColor(context, R.color.inactive_gray));
        pNorm.setTextSize(spNorm);

        pDiv.setColor(ContextCompat.getColor(context, R.color.divider));
        pDiv.setStrokeWidth(2f);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scroller  = new Scroller(context);
    }

    // ── 공개 API ────────────────────────────────────────────────────────
    public void setRange(int min, int max) { minValue = min; maxValue = max; }
    public void setDisplayedValues(String[] v) { displayedValues = v; }
    public void setWrapSelectorWheel(boolean w) { wrapAround = w; }
    public void setOnValueChangeListener(OnValueChangeListener l) { listener = l; }

    public void setValue(int value) {
        int old = getValue();
        scroller.forceFinished(true);
        isAnimating   = false;
        initVal       = clampVal(value);
        totalScrollPx = 0f;
        if (old != initVal && listener != null)
            listener.onValueChange(this, old, initVal);
        invalidate();
    }

    public int getValue() {
        if (itemH == 0) return initVal;
        int steps = Math.round(totalScrollPx / itemH);
        return clampVal(initVal + steps);
    }

    // ── 측정 ────────────────────────────────────────────────────────────
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float d = getResources().getDisplayMetrics().density;
        itemH = (int)(spSel * 1.9f);
        setMeasuredDimension(
                resolveSize((int)(80 * d), wSpec),
                resolveSize(itemH * VISIBLE_ITEMS, hSpec));
    }

    // ── 터치 ────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (velTracker == null) velTracker = VelocityTracker.obtain();
        velTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                isAnimating = false;
                commitCurrent();   // 진행 중 애니메이션 위치 확정
                dragging = false;
                lastY    = ev.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                float delta = lastY - ev.getY();
                if (!dragging && Math.abs(delta) > touchSlop) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    totalScrollPx += delta;
                    clampScroll();
                    lastY = ev.getY();
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    velTracker.computeCurrentVelocity(1000);
                    float vy = -velTracker.getYVelocity();
                    launchFling(vy);
                } else {
                    int slot = Math.round((ev.getY() - getHeight() / 2f) / itemH);
                    if (slot != 0) animateTo(clampVal(initVal + slot));
                    else           snapToNearest();
                }
                velTracker.recycle();
                velTracker = null;
                dragging   = false;
                break;
        }
        return true;
    }

    // ── 플링 / 스냅 ─────────────────────────────────────────────────────
    private void launchFling(float velocityPx) {
        int cur = (int) totalScrollPx;
        if (Math.abs(velocityPx) < 200) { snapToNearest(); return; }

        int range = maxValue - minValue + 1;
        int minPx = wrapAround ? cur - itemH * range * 3 : (minValue - initVal) * itemH;
        int maxPx = wrapAround ? cur + itemH * range * 3 : (maxValue - initVal) * itemH;

        scroller.fling(0, cur, 0, (int) velocityPx, 0, 0, minPx, maxPx);
        isAnimating = true;
        invalidate();
    }

    private void snapToNearest() {
        int cur     = (int) totalScrollPx;
        int nearest = Math.round(totalScrollPx / itemH) * itemH;
        // wrapAround=false 일 때 경계 clamp
        if (!wrapAround) {
            int lo = (minValue - initVal) * itemH;
            int hi = (maxValue - initVal) * itemH;
            nearest = Math.max(lo, Math.min(hi, nearest));
        }
        scroller.startScroll(0, cur, 0, nearest - cur, 180);
        isAnimating = true;
        invalidate();
    }

    private void animateTo(int targetVal) {
        int target = clampVal(targetVal);
        int targetPx;
        if (wrapAround) {
            int range = maxValue - minValue + 1;
            int cur   = clampVal(initVal + Math.round(totalScrollPx / itemH));
            int diff  = target - cur;
            if (diff >  range / 2) diff -= range;
            if (diff < -range / 2) diff += range;
            targetPx = (int)(totalScrollPx + diff * itemH);
        } else {
            targetPx = (target - initVal) * itemH;
        }
        int cur = (int) totalScrollPx;
        scroller.startScroll(0, cur, 0, targetPx - cur, 200);
        isAnimating = true;
        invalidate();
    }

    // ── 애니메이션 루프 ─────────────────────────────────────────────────
    @Override
    public void computeScroll() {
        if (!isAnimating) return;
        if (scroller.computeScrollOffset()) {
            totalScrollPx = scroller.getCurrY();
            clampScroll();
            invalidate();
        } else {
            isAnimating = false;
            commitCurrent();
            invalidate();
        }
    }

    /** totalScrollPx → initVal 확정, offset 리셋 */
    private void commitCurrent() {
        if (itemH == 0) return;
        int steps  = Math.round(totalScrollPx / itemH);
        int newVal = clampVal(initVal + steps);
        // totalScrollPx를 확정된 값 기준으로 보정 (경계에서 잔여 픽셀 제거)
        totalScrollPx = totalScrollPx - steps * itemH;
        // wrap=false 경계에서는 잔여 픽셀도 0으로
        if (!wrapAround) totalScrollPx = 0f;

        int old = initVal;
        initVal = newVal;
        if (old != newVal && listener != null)
            listener.onValueChange(this, old, newVal);
    }

    // ── 그리기 ──────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        if (itemH == 0) return;

        int w       = getWidth();
        int centerY = getHeight() / 2;
        int cx      = w / 2;

        float dh = itemH * 0.5f;
        canvas.drawLine(cx - w * 0.3f, centerY - dh, cx + w * 0.3f, centerY - dh, pDiv);
        canvas.drawLine(cx - w * 0.3f, centerY + dh, cx + w * 0.3f, centerY + dh, pDiv);

        float rawSteps = totalScrollPx / itemH;
        int   baseStep = (int) Math.floor(rawSteps);
        float frac     = rawSteps - baseStep;

        int half = VISIBLE_ITEMS / 2 + 1;
        for (int slot = -half; slot <= half; slot++) {
            int dataVal = clampVal(initVal + baseStep + slot);
            float yCenter = centerY + (slot - frac) * itemH;

            if (yCenter < -itemH || yCenter > getHeight() + itemH) continue;

            // wrapAround=false 이고 범위 밖이면 그리지 않음
            if (!wrapAround) {
                int rawVal = initVal + baseStep + slot;
                if (rawVal < minValue || rawVal > maxValue) continue;
            }

            float dist  = Math.abs(yCenter - centerY) / itemH;
            float t     = Math.min(1f, dist);
            float size  = spSel + (spNorm - spSel) * t;
            int   alpha = Math.max(40, (int)(255 - 200 * t));

            Paint p      = (dist < 0.5f) ? pSel : pNorm;
            float saved  = p.getTextSize();
            int   savedA = p.getAlpha();
            p.setTextSize(size);
            p.setAlpha(alpha);

            Paint.FontMetrics fm = p.getFontMetrics();
            canvas.drawText(getLabel(dataVal), cx,
                    yCenter - (fm.ascent + fm.descent) / 2f, p);

            p.setTextSize(saved);
            p.setAlpha(savedA);
        }
    }

    // ── 내부 유틸 ───────────────────────────────────────────────────────
    private void clampScroll() {
        if (wrapAround) return;
        float lo = (minValue - initVal) * (float) itemH;
        float hi = (maxValue - initVal) * (float) itemH;
        totalScrollPx = Math.max(lo, Math.min(hi, totalScrollPx));
    }

    private int clampVal(int val) {
        if (wrapAround) {
            int range = maxValue - minValue + 1;
            return ((val - minValue) % range + range) % range + minValue;
        }
        return Math.max(minValue, Math.min(maxValue, val));
    }

    private String getLabel(int val) {
        int idx = val - minValue;
        if (displayedValues != null && idx >= 0 && idx < displayedValues.length)
            return displayedValues[idx];
        return String.format("%02d", val);
    }
}

package com.acasian.iot.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * 경량 라인 차트 뷰 — 외부 라이브러리 없이 Canvas 로 직접 렌더.
 *
 *  • Y축 값 눈금선 + 라벨
 *  • X축 일자/요일 라벨 (간격 자동 조절)
 *  • min~max 변동폭 음영 + (min+max)/2 추세선
 *  • 관리범위(환경기준) 음영 밴드 + 이상값 빨간 마커 (관리범위 지정 시)
 *  • 탭/드래그 시 해당 지점의 일자·평균·최저·최고 말풍선 표시
 *
 * 사용: chartView.setData(avg, min, max, labels, rangeLo, rangeHi);
 */
public class SensorChartView extends View {

    private float[] avg;
    private float[] min;
    private float[] max;
    private String[] labels;
    private double rangeLo = Double.NaN;
    private double rangeHi = Double.NaN;
    private int lineColor = Color.parseColor("#2E7D32");   // forest_mid

    private int selected = -1;   // 탭한 포인트 인덱스

    private final Paint pBand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOver = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLimit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEnv  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDot  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBad  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAxisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSel   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTipTx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path   = new Path();

    private final float d;        // density
    private final float padL, padR, padT, padB;

    public SensorChartView(Context c) { this(c, null); }
    public SensorChartView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        padL = 38 * d;   // Y축 라벨 공간
        padR = 12 * d;
        padT = 12 * d;
        padB = 26 * d;   // X축 라벨 공간
        setClickable(true);

        pBand.setStyle(Paint.Style.FILL);
        pBand.setColor(Color.parseColor("#1A66BB6A"));      // 관리범위 sage 10%

        pOver.setStyle(Paint.Style.FILL);
        pOver.setColor(Color.parseColor("#14F44336"));      // 한계 초과 구간 연빨강 8%
        pLimit.setStyle(Paint.Style.STROKE);
        pLimit.setStrokeWidth(1.5f * d);
        pLimit.setColor(Color.parseColor("#80F44336"));     // 한계선

        pEnv.setStyle(Paint.Style.FILL);
        pEnv.setColor(Color.parseColor("#332E7D32"));       // min~max 변동폭 20%

        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeWidth(2.5f * d);
        pLine.setStrokeCap(Paint.Cap.ROUND);
        pLine.setStrokeJoin(Paint.Join.ROUND);
        pLine.setColor(lineColor);

        pDot.setStyle(Paint.Style.FILL);
        pDot.setColor(lineColor);

        pBad.setStyle(Paint.Style.FILL);
        pBad.setColor(Color.parseColor("#F44336"));         // 이상값

        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(1f * d);
        pGrid.setColor(Color.parseColor("#E0EBE0"));

        pAxisText.setColor(Color.parseColor("#9E9E9E"));
        pAxisText.setTextSize(10 * d);

        pSel.setStyle(Paint.Style.STROKE);
        pSel.setStrokeWidth(1f * d);
        pSel.setColor(Color.parseColor("#80666666"));

        pTipBg.setStyle(Paint.Style.FILL);
        pTipBg.setColor(Color.parseColor("#E6212121"));     // 말풍선 배경

        pTipTx.setColor(Color.WHITE);
        pTipTx.setTextSize(11 * d);
    }

    public void setLineColor(int color) {
        lineColor = color; pLine.setColor(color); pDot.setColor(color); invalidate();
    }

    public void setData(float[] avg, float[] min, float[] max,
                        String[] labels, double rangeLo, double rangeHi) {
        this.avg = avg; this.min = min; this.max = max; this.labels = labels;
        this.rangeLo = rangeLo; this.rangeHi = rangeHi;
        this.selected = -1;
        invalidate();
    }

    // ── 그리기 영역 좌표 ──
    private float left()   { return padL; }
    private float right()  { return getWidth() - padR; }
    private float top()    { return padT; }
    private float bottom() { return getHeight() - padB; }
    private float stepX()  { int n = (avg == null) ? 0 : avg.length;
                             return (n > 1) ? (right() - left()) / (n - 1) : 0; }
    private float xAt(int i){ int n = avg.length;
                              return (n > 1) ? left() + stepX() * i : (left() + right()) / 2f; }

    // Y 스케일 — 실측 데이터(min~max)에만 맞추고 ±10% 여백.
    //   관리범위(상·하한)는 축에 강제로 포함하지 않는다 → 데이터가 한계에서 멀면
    //   변동이 시원하게 펼쳐지고, 한계선은 화면 범위에 들어올 때만 그려진다.
    private float yMin, yMax;
    private void computeY() {
        float dMin = Float.MAX_VALUE, dMax = -Float.MAX_VALUE;
        for (int i = 0; i < avg.length; i++) {
            dMin = Math.min(dMin, safe(min, i, avg[i]));
            dMax = Math.max(dMax, safe(max, i, avg[i]));
        }
        if (dMax - dMin < 1e-3) { dMax += 1; dMin -= 1; }
        float span = dMax - dMin;
        yMin = dMin - span * 0.10f;
        yMax = dMax + span * 0.10f;
    }
    private float yToPx(float v) {
        float t = (v - yMin) / (yMax - yMin);
        return bottom() - t * (bottom() - top());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();

        if (avg == null || avg.length == 0) {
            pAxisText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("— 데이터 없음 —", w / 2f, h / 2f, pAxisText);
            pAxisText.setTextAlign(Paint.Align.LEFT);
            return;
        }
        computeY();
        int n = avg.length;

        // ── Y축 눈금선 + 값 라벨 (4구간) ──
        pAxisText.setTextAlign(Paint.Align.RIGHT);
        int gridN = 4;
        for (int g = 0; g <= gridN; g++) {
            float val = yMin + (yMax - yMin) * g / gridN;
            float y = yToPx(val);
            canvas.drawLine(left(), y, right(), y, pGrid);
            canvas.drawText(fmt1(val), left() - 4 * d, y + 3.5f * d, pAxisText);
        }
        pAxisText.setTextAlign(Paint.Align.LEFT);

        // ── 관리범위 표시 — 화면(Y범위) 안에 들어오는 한계만 선+음영 ──
        //   상한: 선 위쪽을 초과 음영 / 하한: 선 아래쪽을 미만 음영.
        //   한계가 데이터 범위 밖이면(=화면에 없으면) 그리지 않음.
        if (!Double.isNaN(rangeHi) && rangeHi >= yMin && rangeHi <= yMax) {
            float y = yToPx((float) rangeHi);
            canvas.drawRect(left(), top(), right(), y, pOver);   // 상한 초과 구간
            canvas.drawLine(left(), y, right(), y, pLimit);
        }
        if (!Double.isNaN(rangeLo) && rangeLo >= yMin && rangeLo <= yMax) {
            float y = yToPx((float) rangeLo);
            canvas.drawRect(left(), y, right(), bottom(), pOver); // 하한 미만 구간
            canvas.drawLine(left(), y, right(), y, pLimit);
        }

        // ── min~max 변동폭 음영 ──
        if (min != null && max != null) {
            Path env = new Path();
            for (int i = 0; i < n; i++) {
                float x = xAt(i), y = yToPx(safe(max, i, avg[i]));
                if (i == 0) env.moveTo(x, y); else env.lineTo(x, y);
            }
            for (int i = n - 1; i >= 0; i--) env.lineTo(xAt(i), yToPx(safe(min, i, avg[i])));
            env.close();
            canvas.drawPath(env, pEnv);
        }

        // ── 추세선 ──
        path.reset();
        for (int i = 0; i < n; i++) {
            float x = xAt(i), y = yToPx(avg[i]);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        canvas.drawPath(path, pLine);

        // ── 포인트 + 이상값 ──
        float dotR = 3f * d;
        for (int i = 0; i < n; i++) {
            boolean bad = isOutOfRange(i);
            canvas.drawCircle(xAt(i), yToPx(avg[i]), bad ? dotR * 1.6f : dotR, bad ? pBad : pDot);
        }

        // ── X축 라벨 (간격 자동: 최대 6개) ──
        if (labels != null) {
            pAxisText.setTextAlign(Paint.Align.CENTER);
            int every = Math.max(1, (int) Math.ceil(n / 6.0));
            for (int i = 0; i < n; i++) {
                if (i % every != 0 && i != n - 1) continue;
                if (i < labels.length && labels[i] != null)
                    canvas.drawText(labels[i], xAt(i), bottom() + 14 * d, pAxisText);
            }
            pAxisText.setTextAlign(Paint.Align.LEFT);
        }

        // ── 탭 말풍선 ──
        if (selected >= 0 && selected < n) drawTooltip(canvas, selected);
    }

    private void drawTooltip(Canvas canvas, int i) {
        float x = xAt(i);
        canvas.drawLine(x, top(), x, bottom(), pSel);            // 세로 가이드
        canvas.drawCircle(x, yToPx(avg[i]), 5 * d, pBad);        // 강조 점

        String l1 = (labels != null && i < labels.length && labels[i] != null) ? labels[i] : "";
        String l2 = "평균 " + fmt1(avg[i]);
        String l3 = "최저 " + fmt1(safe(min, i, avg[i])) + " / 최고 " + fmt1(safe(max, i, avg[i]));

        float pad = 6 * d, lh = 14 * d;
        float tw = Math.max(pTipTx.measureText(l1), Math.max(pTipTx.measureText(l2), pTipTx.measureText(l3)));
        float bw = tw + pad * 2, bh = lh * 3 + pad;
        float bx = x - bw / 2f;
        bx = Math.max(left(), Math.min(bx, right() - bw));       // 화면 밖 방지
        float by = top() + 2 * d;

        RectF box = new RectF(bx, by, bx + bw, by + bh);
        canvas.drawRoundRect(box, 6 * d, 6 * d, pTipBg);
        float tx = bx + pad, ty = by + pad + 10 * d;
        canvas.drawText(l1, tx, ty, pTipTx);
        canvas.drawText(l2, tx, ty + lh, pTipTx);
        canvas.drawText(l3, tx, ty + lh * 2, pTipTx);
    }

    // ── 터치: 가장 가까운 포인트 선택 ──
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (avg == null || avg.length == 0) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                int idx = nearestIndex(e.getX());
                if (idx != selected) { selected = idx; invalidate(); }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { return super.performClick(); }

    private int nearestIndex(float px) {
        int n = avg.length;
        if (n <= 1) return 0;
        float rel = (px - left()) / stepX();
        int i = Math.round(rel);
        return Math.max(0, Math.min(n - 1, i));
    }

    private boolean isOutOfRange(int i) {
        boolean over  = !Double.isNaN(rangeHi) && safe(max, i, avg[i]) > rangeHi; // 상한 초과
        boolean under = !Double.isNaN(rangeLo) && safe(min, i, avg[i]) < rangeLo; // 하한 미만
        return over || under;
    }

    private static float safe(float[] arr, int i, float fb) {
        return (arr != null && i < arr.length) ? arr[i] : fb;
    }

    private static String fmt1(double v) {
        return String.format(Locale.getDefault(), "%.1f", v);
    }
}

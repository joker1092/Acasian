package com.acasian.iot.Calendar.model;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import com.acasian.iot.R;

/**
 * 달력 스타일 통합 관리 클래스.
 *
 * 값 우선순위:
 *   1. createFromTheme(context) → Theme의 ?attr 값을 런타임에 읽음  ← 권장
 *   2. createDefault()          → 코드 내 fallback 기본값 사용
 *
 * 사용법:
 *   CalendarConfig config = CalendarConfig.createFromTheme(context);
 *   // 특정 값만 덮어쓰기
 *   config.sundayColor = Color.RED;
 *   new MonthCalendarView(grid, config);
 */
public class CalendarConfig {

    // ── 배경 ──────────────────────────────────────────
    public int   backgroundColor      = Color.parseColor("#1A2E1A");

    // ── 헤더 ──────────────────────────────────────────
    public int   headerTextColor      = Color.WHITE;
    public float headerTextSize       = 20f;   // sp

    // ── 요일 라벨 ─────────────────────────────────────
    public int   sundayLabelColor     = Color.WHITE;
    public int   saturdayLabelColor   = Color.parseColor("#8AAF72");
    public int   weekdayLabelColor    = Color.WHITE;
    public float weekdayLabelTextSize = 12f;   // sp

    // ── 날짜 셀 텍스트 ────────────────────────────────
    public float dayTextSize          = 15f;   // sp
    public int   defaultDayColor      = Color.WHITE;
    public int   sundayColor          = Color.parseColor("#FF5252");
    public int   saturdayColor        = Color.parseColor("#64B5F6");

    // ── 날짜 셀 상태 ──────────────────────────────────
    public float   otherMonthAlpha      = 0.35f;
    public int     todayStrokeColor     = Color.parseColor("#B8D4A0");
    public int     todayFillColor       = Color.parseColor("#2D4A2D");
    public Integer todayTextColor       = null;   // null → 요일 색상 그대로
    public int     selectedDayFillColor = Color.parseColor("#4A6741");
    public int     selectedDayTextColor = Color.WHITE;

    // ── 셀 크기/여백 ──────────────────────────────────
    public int   cellMinSizeDp  = 32;   // dp: minWidth/minHeight
    public int   cellMarginDp   = 1;    // dp: GridLayout 셀 간격

    // ── dot indicator ─────────────────────────────────
    public int   dotColor       = Color.parseColor("#7EDB6A");

    // ─────────────────────────────────────────────────

    /**
     * [권장] Theme의 ?attr 값을 런타임에 읽어 Config를 생성합니다.
     * themes.xml 에서 일괄 변경하면 여기에 자동 반영됩니다.
     */
    public static CalendarConfig createFromTheme(Context context) {
        CalendarConfig c = new CalendarConfig();
        TypedValue tv = new TypedValue();

        // 색상
        c.backgroundColor      = resolveColor(context, R.attr.cal_background_color,    c.backgroundColor);
        c.headerTextColor      = resolveColor(context, R.attr.cal_header_color,         c.headerTextColor);
        c.sundayLabelColor     = resolveColor(context, R.attr.cal_weekday_sun_color,    c.sundayLabelColor);
        c.saturdayLabelColor   = resolveColor(context, R.attr.cal_weekday_sat_color,    c.saturdayLabelColor);
        c.weekdayLabelColor    = resolveColor(context, R.attr.cal_weekday_default_color,c.weekdayLabelColor);
        c.defaultDayColor      = resolveColor(context, R.attr.cal_day_text_color,       c.defaultDayColor);
        c.sundayColor          = resolveColor(context, R.attr.cal_day_sun_color,        c.sundayColor);
        c.saturdayColor        = resolveColor(context, R.attr.cal_day_sat_color,        c.saturdayColor);
        c.todayStrokeColor     = resolveColor(context, R.attr.cal_today_stroke_color,   c.todayStrokeColor);
        c.todayFillColor       = resolveColor(context, R.attr.cal_today_fill_color,     c.todayFillColor);
        c.selectedDayFillColor = resolveColor(context, R.attr.cal_selected_fill_color,  c.selectedDayFillColor);
        c.selectedDayTextColor = resolveColor(context, R.attr.cal_selected_text_color,  c.selectedDayTextColor);
        c.dotColor             = resolveColor(context, R.attr.cal_dot_color,            c.dotColor);

        // 텍스트 크기 (sp → float)
        c.dayTextSize          = resolveDimSp(context, R.attr.cal_day_text_size,           c.dayTextSize);
        c.headerTextSize       = resolveDimSp(context, R.attr.cal_header_text_size,        c.headerTextSize);
        c.weekdayLabelTextSize = resolveDimSp(context, R.attr.cal_weekday_label_text_size, c.weekdayLabelTextSize);

        // 셀 최소 크기 (dp → int)
        c.cellMinSizeDp        = resolveDimDp(context, R.attr.cal_cell_min_size, c.cellMinSizeDp);
        c.cellMarginDp         = resolveDimDp(context, R.attr.cal_cell_margin,   c.cellMarginDp);

        // float (alpha)
        if (context.getTheme().resolveAttribute(R.attr.cal_other_month_alpha, tv, true)) {
            c.otherMonthAlpha = tv.getFloat();
        }

        return c;
    }

    /** fallback: 코드 기본값만 사용 */
    public static CalendarConfig createDefault() {
        return new CalendarConfig();
    }

    // ── 내부 헬퍼 ────────────────────────────────────

    private static int resolveColor(Context ctx, int attrId, int fallback) {
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attrId, tv, true)) {
            // tv.type 이 색상 직접값이거나 @color 참조 모두 처리
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            // @color 리소스 참조인 경우
            if (tv.resourceId != 0) {
                return ctx.getResources().getColor(tv.resourceId, ctx.getTheme());
            }
        }
        return fallback;
    }

    /** dimension attr → sp 값으로 변환 (setTextSize()에 전달용) */
    private static float resolveDimSp(Context ctx, int attrId, float fallbackSp) {
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attrId, tv, true)) {
            // getDimension() 은 px 반환 → sp로 재환산
            float px = TypedValue.complexToDimension(tv.data,
                    ctx.getResources().getDisplayMetrics());
            return px / ctx.getResources().getDisplayMetrics().scaledDensity;
        }
        return fallbackSp;
    }

    /** dimension attr → dp 정수값으로 변환 (margin/padding 계산용) */
    private static int resolveDimDp(Context ctx, int attrId, int fallbackDp) {
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attrId, tv, true)) {
            float px = TypedValue.complexToDimension(tv.data,
                    ctx.getResources().getDisplayMetrics());
            return Math.round(px / ctx.getResources().getDisplayMetrics().density);
        }
        return fallbackDp;
    }
}

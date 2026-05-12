package com.acasian.iot.Calendar.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import com.acasian.iot.Calendar.controller.CalendarController;
import com.acasian.iot.Calendar.model.CalendarConfig;
import com.acasian.iot.Calendar.model.CalendarDate;
import com.acasian.iot.Calendar.model.WorkRecord;
import com.acasian.iot.R;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MonthCalendarView {

    private final GridLayout         gridLayout;
    private final CalendarController controller;
    private final CalendarConfig     config;
    private OnDateClickListener      onDateClickListener;
    private CalendarDate             selectedDate;

    // 작업 데이터 (외부에서 setRecords()로 주입, 없으면 빈 리스트)
    private List<WorkRecord> records = new ArrayList<>();

    public interface OnDateClickListener {
        void onDateClick(CalendarDate date);
    }

    public MonthCalendarView(GridLayout gridLayout) {
        this(gridLayout, CalendarConfig.createFromTheme(gridLayout.getContext()));
    }

    public MonthCalendarView(GridLayout gridLayout, CalendarConfig config) {
        this.gridLayout = gridLayout;
        this.config     = config;
        this.controller = new CalendarController();
    }

    /** 작업 데이터 주입 (DateDetailView와 동일한 records 리스트 공유) */
    public void setRecords(List<WorkRecord> records) {
        this.records = records != null ? records : new ArrayList<>();
    }

    /** 외부에서 선택 날짜 주입 — updateCalendar() 전에 호출하면 하이라이트 반영됨 */
    public void setSelectedDate(LocalDate date) {
        if (selectedDate != null) selectedDate.setSelected(false);
        selectedDate = null;
        if (date == null) return;
        // getDatesInMonth로 CalendarDate 찾아서 selected 설정
        java.time.YearMonth ym = java.time.YearMonth.from(date);
        for (CalendarDate cd : controller.getDatesInMonth(ym)) {
            if (cd.getDate().equals(date)) {
                cd.setSelected(true);
                selectedDate = cd;
                break;
            }
        }
    }

    public void setOnDateClickListener(OnDateClickListener listener) {
        this.onDateClickListener = listener;
    }

    // ── 달력 갱신 ────────────────────────────────────────────────────────
    public void updateCalendar(YearMonth yearMonth) {
        if (gridLayout == null) return;

        gridLayout.removeAllViews();
        gridLayout.setColumnCount(7);
        gridLayout.setBackgroundColor(config.backgroundColor);

        Context        context  = gridLayout.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        float          density  = context.getResources().getDisplayMetrics().density;
        int            marginPx = Math.round(config.cellMarginDp * density);

        List<CalendarDate> dates = controller.getDatesInMonth(yearMonth);

        for (CalendarDate date : dates) {
            View     cellView = inflater.inflate(
                    R.layout.layout_calendar_date_cell, gridLayout, false);
            TextView txtDay      = cellView.findViewById(R.id.txt_day);
            View     dotContainer = cellView.findViewById(R.id.dot_container);
            TextView badge1 = cellView.findViewById(R.id.dot_1);
            TextView badge2 = cellView.findViewById(R.id.dot_2);
            TextView badge3 = cellView.findViewById(R.id.dot_3);

            if (txtDay != null) {
                setupCellDesign(txtDay, dotContainer, badge1, badge2, badge3, date, density);
            }

            cellView.setOnClickListener(v -> {
                if (selectedDate != null) selectedDate.setSelected(false);
                date.setSelected(true);
                selectedDate = date;
                // 전체 재렌더로 이전 셀 선택 해제 UI 반영
                updateCalendar(java.time.YearMonth.from(date.getDate()));
                if (onDateClickListener != null) onDateClickListener.onDateClick(date);
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            );
            params.width  = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            cellView.setLayoutParams(params);

            gridLayout.addView(cellView);
        }
    }

    // ── 셀 스타일 ────────────────────────────────────────────────────────
    private void setupCellDesign(TextView txtDay,
                                  View dotContainer, TextView badge1, TextView badge2, TextView badge3,
                                  CalendarDate date, float density) {

        txtDay.setText(String.valueOf(date.getDayOfMonth()));
        txtDay.setTextSize(config.dayTextSize);

        // 다른 달: 숫자만 흐리게 표시 (숨기지 않음)
        boolean isCurrentMonth = date.isCurrentMonth();
        float alpha = isCurrentMonth ? 1.0f : config.otherMonthAlpha;
        txtDay.setAlpha(alpha);

        // 요일 색상
        int dow = date.getDate().getDayOfWeek().getValue();
        int textColor = (dow == 7) ? config.sundayColor
                      : (dow == 6) ? config.saturdayColor
                      :              config.defaultDayColor;
        txtDay.setTextColor(textColor);
        txtDay.setBackground(null);
        txtDay.setTypeface(null, Typeface.NORMAL);

        // 일요일 셀: 연한 빨강 배경 (현재 달 + 오늘/선택 아닌 경우)
        if (isCurrentMonth && dow == 7 && !date.isToday() && !date.isSelected()) {
            GradientDrawable sunBg = new GradientDrawable();
            sunBg.setShape(GradientDrawable.RECTANGLE);
            sunBg.setColor(android.graphics.Color.parseColor("#1AFF5252"));
            txtDay.setBackground(sunBg);
        }

        // 오늘 / 선택 상태
        if (date.isToday()) {
            txtDay.setBackground(buildCircle(
                    config.todayFillColor, config.todayStrokeColor, density));
            txtDay.setTypeface(null, Typeface.BOLD);
            if (config.todayTextColor != null) txtDay.setTextColor(config.todayTextColor);
        } else if (date.isSelected()) {
            txtDay.setBackground(buildCircle(config.selectedDayFillColor, 0, density));
            txtDay.setTextColor(config.selectedDayTextColor);
            txtDay.setTypeface(null, Typeface.BOLD);
        }

        // ── 컬러 도트 표시 (다른 달은 숨김) ─────────────────────────────
        if (!isCurrentMonth || dotContainer == null) {
            if (dotContainer != null) dotContainer.setVisibility(View.GONE);
            return;
        }

        List<WorkRecord> dayRecords = getRecordsForDate(date.getDate());

        if (dayRecords.isEmpty()) {
            dotContainer.setVisibility(View.GONE);
            return;
        }

        dotContainer.setVisibility(View.VISIBLE);
        applyBadge(badge1, dayRecords.size() >= 1 ? dayRecords.get(0) : null);
        applyBadge(badge2, dayRecords.size() >= 2 ? dayRecords.get(1) : null);
        if (badge3 != null) {
            int extra = dayRecords.size() - 2;
            if (extra > 0) { badge3.setVisibility(View.VISIBLE); badge3.setText("+" + extra + "개"); badge3.setTextColor(android.graphics.Color.parseColor("#A5D6A7")); }
            else badge3.setVisibility(View.GONE);
        }
    }

    private void applyBadge(TextView badge, WorkRecord record) {
        if (badge == null) return;
        if (record == null) { badge.setVisibility(View.GONE); return; }
        badge.setVisibility(View.VISIBLE);
        // 텍스트: HH:mm 유형명
        String time = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                record.getStartTime().getHour(), record.getStartTime().getMinute());
        String name = record.getTaskName() != null ? record.getTaskName() : "";
        if (name.length() > 3) name = name.substring(0, 3);
        badge.setText(time + " " + name);
        // 배경색
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(4 * badge.getResources().getDisplayMetrics().density);
        String n = record.getTaskName() != null ? record.getTaskName() : "";
        int color = n.contains("전체") ? android.graphics.Color.parseColor("#1D9E75")
                  : n.contains("순차") ? android.graphics.Color.parseColor("#EF9F27")
                  : n.contains("개별") ? android.graphics.Color.parseColor("#3B8BD4")
                  : android.graphics.Color.parseColor("#1D9E75");
        bg.setColor(color);
        badge.setBackground(bg);
    }

    // ── 작업 라벨 포맷: "관수 작업 09:00" ────────────────────────────────
    private String buildEventLabel(WorkRecord r) {
        String prefix = r.isPast() ? "" : "예약 ";
        String time   = String.format(Locale.getDefault(),
                "%02d:%02d", r.getStartTime().getHour(), r.getStartTime().getMinute());
        // 작업명이 길면 앞 4글자만
        String task = r.getTaskName();
        if (task.length() > 4) task = task.substring(0, 4);
        return prefix + task + " " + time;
    }

    // ── 상태별 텍스트 색상 ───────────────────────────────────────────────
    private int eventColor(WorkRecord.Status s) {
        switch (s) {
            case DONE:      return Color.parseColor("#7EDB6A"); // 초록
            case SCHEDULED: return Color.parseColor("#64B5F6"); // 파랑
            case FAILED:    return Color.parseColor("#FF5252"); // 빨강
            case RUNNING:   return Color.parseColor("#C8A84B"); // 금색
            default:        return Color.parseColor("#B8D4A0");
        }
    }



    private List<WorkRecord> getRecordsForDate(LocalDate date) {
        List<WorkRecord> result = new ArrayList<>();
        for (WorkRecord r : records) {
            if (r.getDate().isEqual(date)) result.add(r);
        }
        return result;
    }

    private GradientDrawable buildCircle(int fillColor, int strokeColor, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fillColor);
        if (strokeColor != 0) d.setStroke(Math.round(1.5f * density), strokeColor);
        return d;
    }
}

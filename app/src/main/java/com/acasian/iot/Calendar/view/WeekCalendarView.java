package com.acasian.iot.Calendar.view;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.acasian.iot.Calendar.model.CalendarConfig;
import com.acasian.iot.R;

import com.acasian.iot.Calendar.model.WorkRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WeekCalendarView {

    private final CalendarConfig config;
    private final LinearLayout   container;
    private LinearLayout         weekDaysContainer;
    private LocalDate            currentWeekDate;

    public interface OnDateClickListener {
        void onDateClick(LocalDate date);
    }

    private OnDateClickListener listener;
    private List<WorkRecord>     records = new ArrayList<>();

    public WeekCalendarView(LinearLayout container) {
        this(container, CalendarConfig.createFromTheme(container.getContext()));
    }

    public WeekCalendarView(LinearLayout container, CalendarConfig config) {
        this.container = container;
        this.config    = config;
        this.weekDaysContainer = container.findViewById(R.id.week_days_container);
    }

    public void setRecords(List<WorkRecord> records) {
        this.records = records != null ? records : new ArrayList<>();
    }

    public void setOnDateClickListener(OnDateClickListener listener) {
        this.listener = listener;
    }

    public void updateWeek(LocalDate selectedDate) {
        if (weekDaysContainer == null) return;
        weekDaysContainer.removeAllViews();

        Context        context  = weekDaysContainer.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        float          density  = context.getResources().getDisplayMetrics().density;

        // ── 주 시작 계산 (일요일 기준) ──────────────────────────────
        // DayOfWeek: 월=1 화=2 ... 토=6 일=7
        // 일요일(7)을 0으로 환산: (value % 7) → 월=1,화=2,...,토=6,일=0
        int dayOfWeekIdx = selectedDate.getDayOfWeek().getValue() % 7; // 일=0, 월=1 ... 토=6
        LocalDate startOfWeek = selectedDate.minusDays(dayOfWeekIdx);

        for (int i = 0; i < 7; i++) {
            final LocalDate day = startOfWeek.plusDays(i);

            View     cellView = inflater.inflate(
                    R.layout.layout_calendar_date_cell, weekDaysContainer, false);
            TextView txtDay   = cellView.findViewById(R.id.txt_day);
            View     dotView  = cellView.findViewById(R.id.view_dot);

            if (txtDay != null) {
                txtDay.setText(String.valueOf(day.getDayOfMonth()));
                applyDayStyle(txtDay, dotView, cellView, day, selectedDate, density);
            }

            // 균등 분배 레이아웃 파라미터
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cellView.setLayoutParams(params);

            cellView.setOnClickListener(v -> {
                if (listener != null) listener.onDateClick(day);
            });

            weekDaysContainer.addView(cellView);
        }
    }

    // ── 셀 스타일 적용 ──────────────────────────────────────────────────

    private void applyDayStyle(TextView txtDay, View dotView, View cellView,
                               LocalDate day, LocalDate selectedDate, float density) {

        // 텍스트 크기
        txtDay.setTextSize(config.dayTextSize);

        // 요일 색상 (일=0 → 일요일, 토=6)
        int dow = day.getDayOfWeek().getValue() % 7; // 일=0 ... 토=6
        int textColor = (dow == 0) ? config.sundayColor
                      : (dow == 6) ? config.saturdayColor
                      :              config.defaultDayColor;
        txtDay.setTextColor(textColor);
        txtDay.setBackground(null);
        txtDay.setTypeface(null, Typeface.NORMAL);

        // 오늘
        if (day.isEqual(LocalDate.now())) {
            txtDay.setBackground(buildCircle(
                    config.todayFillColor, config.todayStrokeColor, density));
            txtDay.setTypeface(null, Typeface.BOLD);
            if (config.todayTextColor != null) {
                txtDay.setTextColor(config.todayTextColor);
            }
        }
        // 선택된 날짜 강조
        else if (day.isEqual(selectedDate)) {
            txtDay.setBackground(buildCircle(
                    config.selectedDayFillColor, 0, density));
            txtDay.setTextColor(config.selectedDayTextColor);
            txtDay.setTypeface(null, Typeface.BOLD);
        }

        // ── 컬러 도트 ────────────────────────────────────────────────
        android.view.View dotContainer = cellView != null
                ? ((android.view.ViewGroup) cellView).findViewById(R.id.dot_container) : null;
        if (dotContainer == null) {
            if (dotView != null) dotView.setVisibility(View.GONE);
            return;
        }
        List<WorkRecord> dayRecords = getRecordsForDate(day);
        if (dayRecords.isEmpty()) {
            dotContainer.setVisibility(android.view.View.GONE);
        } else {
            dotContainer.setVisibility(android.view.View.VISIBLE);
            applyBadge((TextView)((android.view.ViewGroup) dotContainer).findViewById(R.id.dot_1),
                    dayRecords.size() >= 1 ? dayRecords.get(0) : null);
            applyBadge((TextView)((android.view.ViewGroup) dotContainer).findViewById(R.id.dot_2),
                    dayRecords.size() >= 2 ? dayRecords.get(1) : null);
            TextView badge3w = (TextView)((android.view.ViewGroup) dotContainer).findViewById(R.id.dot_3);
            if (badge3w != null) { int ex = dayRecords.size()-2; if(ex>0){badge3w.setVisibility(android.view.View.VISIBLE);badge3w.setText("+"+ex+"개");}else badge3w.setVisibility(android.view.View.GONE); }
        }
        if (dotView != null) dotView.setVisibility(android.view.View.GONE);
    }

    private void applyBadge(TextView badge, WorkRecord record) {
        if (badge == null) return;
        if (record == null) { badge.setVisibility(android.view.View.GONE); return; }
        badge.setVisibility(android.view.View.VISIBLE);
        String time = String.format(java.util.Locale.getDefault(), "%02d:%02d",
                record.getStartTime().getHour(), record.getStartTime().getMinute());
        String name = record.getTaskName() != null ? record.getTaskName() : "";
        if (name.length() > 3) name = name.substring(0, 3);
        badge.setText(time + " " + name);
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

    private int eventColor(WorkRecord.Status s) {
        switch (s) {
            case DONE:      return android.graphics.Color.parseColor("#7EDB6A");
            case SCHEDULED: return android.graphics.Color.parseColor("#64B5F6");
            case FAILED:    return android.graphics.Color.parseColor("#FF5252");
            case RUNNING:   return android.graphics.Color.parseColor("#C8A84B");
            default:        return android.graphics.Color.parseColor("#B8D4A0");
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
        if (strokeColor != 0) {
            d.setStroke(Math.round(1.5f * density), strokeColor);
        }
        return d;
    }
}

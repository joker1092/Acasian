package com.acasian.iot;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.acasian.iot.Calendar.model.CalendarDate;
import com.acasian.iot.Calendar.view.DateDetailView;
import com.acasian.iot.Calendar.view.MonthCalendarView;
import com.acasian.iot.Calendar.view.WeekCalendarView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private GridLayout        monthGrid;
    private MonthCalendarView monthCalendarView;
    private LinearLayout      weekView;
    private WeekCalendarView  weekCalendarView;
    private View              detailView;
    private DateDetailView    dateDetailView;

    private YearMonth currentMonth;
    private LocalDate currentSelectedDate;
    private TextView  txtYearMonth;
    private View      calendarHeader;
    private View      calendarDayLabels;
    private boolean   isWeekMode = false;

    // 상태바 높이 (insets 콜백에서 저장)
    private int statusBarHeight = 0;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 달력 종료 시 원래 상태바 색상 복원
        setStatusBarColor(false);
    }

    private void setStatusBarColor(boolean calendarMode) {
        if (getActivity() == null) return;
        int color = calendarMode
                ? getResources().getColor(R.color.cal_bg, null)
                : getResources().getColor(R.color.forest_dark, null);
        getActivity().getWindow().setStatusBarColor(color);

        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(
                        getActivity().getWindow(),
                        getActivity().getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
    }

    public static CalendarFragment newInstance() {
        return new CalendarFragment();
    }

    /** 장치 목록 갱신 (ZoneStore 기반) */
    public com.acasian.iot.Calendar.view.DateDetailView getDateDetailView() {
        return dateDetailView;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── 뷰 참조 ──────────────────────────────────────────────────────
        monthGrid         = view.findViewById(R.id.calendar_month);
        weekView          = view.findViewById(R.id.calendar_week);
        detailView        = view.findViewById(R.id.calendar_detail);
        txtYearMonth      = view.findViewById(R.id.txt_year_month);
        calendarHeader    = view.findViewById(R.id.calendar_header);
        calendarDayLabels = view.findViewById(R.id.calendar_day_labels);

        if (monthGrid == null) {
            Log.e("CalendarFragment", "monthGrid not found");
            return;
        }

        // ── 달력 뷰 초기화 ────────────────────────────────────────────────
        monthCalendarView = new MonthCalendarView(monthGrid);
        weekCalendarView  = new WeekCalendarView(weekView);

        // DevMode 여부로 DateDetailView 생성
        // demoMode=true → DemoData.buildRecords() 로드
        // demoMode=false → 빈 목록 ("— 데이터 준비중 —")
        boolean demoMode = AppConfig.getInstance().isDevMode();
        dateDetailView = new DateDetailView(detailView, demoMode);

        currentMonth = YearMonth.now();
        dateDetailView.setMonthCalendarView(monthCalendarView, currentMonth);
        weekCalendarView.setRecords(dateDetailView.getRecords());

        // ZoneStore에서 직접 장치 드롭다운 구성
        injectDevicesFromZoneStore(dateDetailView);

        // ── 월간 이전/다음 버튼 ───────────────────────────────────────────
        ImageButton btnPrev = view.findViewById(R.id.btn_prev_month);
        ImageButton btnNext = view.findViewById(R.id.btn_next_month);

        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            isWeekMode = false;
            updateUI();
        });
        if (btnNext != null) btnNext.setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            isWeekMode = false;
            updateUI();
        });

        // ── 주간 이전/다음 버튼 ───────────────────────────────────────────
        ImageButton btnPrevWeek = view.findViewById(R.id.btn_prev_week);
        ImageButton btnNextWeek = view.findViewById(R.id.btn_next_week);

        if (btnPrevWeek != null) btnPrevWeek.setOnClickListener(v -> {
            if (currentSelectedDate != null) {
                currentSelectedDate = currentSelectedDate.minusWeeks(1);
                currentMonth = YearMonth.from(currentSelectedDate);
                weekCalendarView.updateWeek(currentSelectedDate);
                dateDetailView.updateDate(currentSelectedDate);
                updateWeekMonthLabel();
            }
        });
        if (btnNextWeek != null) btnNextWeek.setOnClickListener(v -> {
            if (currentSelectedDate != null) {
                currentSelectedDate = currentSelectedDate.plusWeeks(1);
                currentMonth = YearMonth.from(currentSelectedDate);
                weekCalendarView.updateWeek(currentSelectedDate);
                dateDetailView.updateDate(currentSelectedDate);
                updateWeekMonthLabel();
            }
        });

        // ── 월간 복귀 버튼 ────────────────────────────────────────────────
        ImageButton btnBackToMonth = view.findViewById(R.id.btn_back_to_month);
        if (btnBackToMonth != null) btnBackToMonth.setOnClickListener(v -> {
            isWeekMode = false;
            updateUI();
        });

        monthCalendarView.setOnDateClickListener(this::onDateSelected);

        // 주간 뷰 날짜 클릭 → detail 갱신
        weekCalendarView.setOnDateClickListener(date -> {
            currentSelectedDate = date;
            dateDetailView.updateDate(date);
            weekCalendarView.updateWeek(date); // 선택 강조 갱신
        });

        // ── Insets 처리 (상태바 + 네비바) ────────────────────────────────
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarH     = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int navHeightPx = (int) getResources().getDimension(R.dimen.nav_height);

            // 전체 프래그먼트 상단 패딩 = 상태바 높이
            v.setPadding(0, statusBarHeight, 0, navHeightPx + navBarH);

            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        // 상태바 색상을 달력 배경색으로 변경
        setStatusBarColor(true);

        updateUI();
    }

    // ── 달력 갱신 ────────────────────────────────────────────────────────
    private void updateUI() {
        if (currentMonth == null || monthCalendarView == null) return;
        if (txtYearMonth != null) {
            txtYearMonth.setText(String.format(Locale.getDefault(),
                    "%d년 %d월", currentMonth.getYear(), currentMonth.getMonthValue()));
        }
        dateDetailView.setCurrentMonth(currentMonth);
        monthCalendarView.updateCalendar(currentMonth);
        if (isWeekMode) setVisibleWeek(); else setVisibleMonth();
    }

    // ── 날짜 선택 ────────────────────────────────────────────────────────
    private void onDateSelected(CalendarDate date) {
        currentSelectedDate = date.getDate();
        if (!isWeekMode) {
            isWeekMode = true;
            setVisibleWeek();
        }
        weekCalendarView.updateWeek(currentSelectedDate);
        dateDetailView.updateDate(currentSelectedDate);
    }

    // ── 주간 뷰 년월 라벨 갱신 ───────────────────────────────────────────
    private void updateWeekMonthLabel() {
        if (weekView == null || currentMonth == null) return;
        TextView tv = weekView.findViewById(R.id.txt_week_year_month);
        if (tv != null) tv.setText(String.format(Locale.getDefault(),
                "%d년 %d월", currentMonth.getYear(), currentMonth.getMonthValue()));
    }

    // ── 뷰 전환 ──────────────────────────────────────────────────────────
    private void setVisibleMonth() {
        if (monthGrid         != null) monthGrid.setVisibility(View.VISIBLE);
        if (weekView          != null) weekView.setVisibility(View.GONE);
        if (detailView        != null) detailView.setVisibility(View.GONE);
        if (calendarHeader    != null) calendarHeader.setVisibility(View.VISIBLE);
        if (calendarDayLabels != null) calendarDayLabels.setVisibility(View.VISIBLE);
    }

    private void setVisibleWeek() {
        if (monthGrid         != null) monthGrid.setVisibility(View.GONE);
        if (weekView          != null) weekView.setVisibility(View.VISIBLE);
        if (detailView        != null) detailView.setVisibility(View.VISIBLE);
        if (calendarHeader    != null) calendarHeader.setVisibility(View.GONE);
        if (calendarDayLabels != null) calendarDayLabels.setVisibility(View.GONE);
        updateWeekMonthLabel();
    }
    /** ZoneStore에서 장치 목록을 읽어 DateDetailView에 주입 */
    private void injectDevicesFromZoneStore(
            com.acasian.iot.Calendar.view.DateDetailView ddv) {
        java.util.List<com.acasian.iot.ZoneStore.ZoneInfo> zones =
                com.acasian.iot.ZoneStore.getInstance().getZones();
        if (zones.isEmpty()) return;

        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<com.acasian.iot.Calendar.model.WorkRecord.DeviceType> types =
                new java.util.ArrayList<>();

        for (com.acasian.iot.ZoneStore.ZoneInfo zone : zones) {
            for (com.acasian.iot.ZoneStore.NodeInfo node : zone.nodes) {
                names.add(node.name);
                types.add(com.acasian.iot.Calendar.model.WorkRecord.DeviceType.PUMP);
            }
        }
        if (!names.isEmpty()) {
            com.acasian.iot.Calendar.model.WorkRecord.DeviceType[] typeArr =
                    types.toArray(new com.acasian.iot.Calendar.model.WorkRecord.DeviceType[0]);
            ddv.setDeviceList(names.toArray(new String[0]), typeArr);
        }
    }

}

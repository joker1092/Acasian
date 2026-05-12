package com.acasian.iot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.acasian.iot.Calendar.model.WorkRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기록 탭 — 7일치 연속 타임라인.
 *
 * ─ 구조 ─────────────────────────────────────────────────────────────────
 *  · 기준 날짜(baseDate) 기준 최근 7일 렌더링 (오늘~6일 전)
 *  · 날짜 구분선(DateHeader) + 기록 아이템 순서로 쌓임 (최신 날짜 위)
 *  · 스크롤 시 화면 최상단에 보이는 날짜 추적 → 헤더 날짜 자동 갱신
 *  · ◀ : baseDate -1 (7일 범위 한 칸씩 과거로)
 *  · ▶ : baseDate +1 (오늘이 baseDate면 비활성)
 *  · 날짜 탭 : DatePicker → 선택 날짜로 이동
 */
public class LogFragment extends Fragment {

    private static final int LOAD_DAYS = 7;

    // endDate: 오늘 (고정, 미래 불가)
    private final LocalDate  endDate    = LocalDate.now();

    // selectDate: 사용자가 선택한 날짜 (◀▶ 이동, 초기=오늘)
    private LocalDate        selectDate  = LocalDate.now();
    private boolean          isExpandingRange = false;  // 범위 확장 중 중복 방지

    // 표시 범위: rangeStart = selectDate-6, rangeEnd = min(selectDate+7, endDate)
    private LocalDate        rangeEnd;
    private LocalDate        rangeStart;

    // 기준 날짜 (= selectDate와 동기화)
    private LocalDate        baseDate   = LocalDate.now();
    private List<WorkRecord> allRecords = new ArrayList<>();

    // 날짜 → 스크롤 Y 위치 캐시
    private final Map<LocalDate, Integer> dateScrollY = new LinkedHashMap<>();

    private TextView         tvDate;
    private TextView         btnPrev;
    private TextView         btnNext;
    private LinearLayout     timelineContainer;
    private LinearLayout     emptyView;
    private NestedScrollView scrollView;

    // ── 생성 ─────────────────────────────────────────────────────────────

    public static LogFragment newInstance() { return new LogFragment(); }

    public void setRecords(List<WorkRecord> records, boolean demoMode) {
        this.allRecords = records != null ? new ArrayList<>(records) : new ArrayList<>();
        if (demoMode) {
            // 이미 demo_ ID가 있으면 중복 추가 방지
            boolean hasDemo = false;
            for (com.acasian.iot.Calendar.model.WorkRecord r : this.allRecords)
                if (r.getId().startsWith("demo_")) { hasDemo = true; break; }
            if (!hasDemo) this.allRecords.addAll(DemoData.buildLogRecords());
        }
        if (timelineContainer != null) {
            renderTimeline();
        }
    }

    public void setRecords(List<WorkRecord> records) { setRecords(records, false); }

    // ── 생명주기 ─────────────────────────────────────────────────────────

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // insets
        View headerFrame = view.findViewById(R.id.logHeaderFrame);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusH     = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarH     = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int navHeightPx = (int) getResources().getDimension(R.dimen.nav_height);
            if (headerFrame != null) headerFrame.setPadding(0, statusH, 0, 0);
            v.setPadding(0, 0, 0, navHeightPx + navBarH);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        if (getActivity() != null)
            getActivity().getWindow().setStatusBarColor(
                    getResources().getColor(R.color.forest_dark, null));

        tvDate           = view.findViewById(R.id.tvLogDate);
        btnPrev          = view.findViewById(R.id.btnLogPrev);
        btnNext          = view.findViewById(R.id.btnLogNext);
        timelineContainer = view.findViewById(R.id.logTimelineContainer);
        emptyView        = view.findViewById(R.id.logEmptyView);
        scrollView       = view.findViewById(R.id.logScrollView);

        // 스크롤 변화 → 헤더 날짜 추적 + 끝 도달 시 콘텐츠 추가
        if (scrollView != null) {
            scrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (sv, scrollX, scrollY, oldX, oldY) -> {
                    updateHeaderDateFromScroll(scrollY);

                    if (isExpandingRange) return;
                    View inner = sv.getChildAt(0);
                    if (inner == null) return;
                    int maxScroll = inner.getHeight() - sv.getHeight();

                    // 맨 위 도달 → 과거 1일 상단에 추가
                    if (scrollY == 0 && oldY > 0) {
                        isExpandingRange = true;
                        LocalDate newDay = rangeStart.minusDays(1);
                        rangeStart = newDay;
                        int addedH = prependDayToTop(newDay);
                        // 추가된 높이만큼 스크롤 위치 보정 (현재 보던 위치 유지)
                        sv.post(() -> {
                            sv.scrollBy(0, addedH);
                            isExpandingRange = false;
                        });
                        updateNextBtn();
                    }

                    // 맨 아래 도달 → 미래 1일 하단에 추가 (endDate 초과 불가)
                    if (maxScroll > 0 && scrollY >= maxScroll - 8
                            && scrollY > oldY && rangeEnd.isBefore(endDate)) {
                        isExpandingRange = true;
                        LocalDate newDay = rangeEnd.plusDays(1);
                        rangeEnd = newDay;
                        appendDayToBottom(newDay);
                        sv.post(() -> isExpandingRange = false);
                        updateNextBtn();
                    }
                });
        }

        applySelectDate(selectDate);  // rangeStart/rangeEnd 초기 계산
        renderTimeline();
        updateHeaderDate(selectDate);
        updateNextBtn();

        // ◀ 하루 전으로 (selectDate -1)
        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            selectDate = selectDate.minusDays(1);
            applySelectDate(selectDate);
            renderTimeline();
            updateHeaderDate(selectDate);
            updateNextBtn();
            scrollToDate(selectDate);
        });

        // ▶ 하루 후로 (selectDate +1, endDate 초과 불가)
        if (btnNext != null) btnNext.setOnClickListener(v -> {
            if (!selectDate.isBefore(endDate)) return;
            selectDate = selectDate.plusDays(1);
            applySelectDate(selectDate);
            renderTimeline();
            updateHeaderDate(selectDate);
            updateNextBtn();
            scrollToDate(selectDate);
        });

        // 날짜 탭 → DatePicker
        if (tvDate != null) tvDate.setOnClickListener(v -> showDatePicker());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null)
            getActivity().getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    }

    // ── 렌더링 ───────────────────────────────────────────────────────────

    /**
     * rangeStart ~ rangeEnd 날짜 범위 타임라인 렌더링.
     * 날짜 오름차순 (과거→최신, 최신이 맨 아래).
     * 타임라인 선은 날짜 구분선을 가로질러 연속.
     * 마지막 아이템(전체)만 아래 선 없음.
     */
    /**
     * 날짜 하나를 timelineContainer 맨 위에 삽입.
     * @return 추가된 뷰들의 총 높이 (스크롤 위치 보정용)
     */
    private int prependDayToTop(LocalDate date) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        // 추가할 뷰들을 임시 리스트로 만든 뒤 index 0부터 삽입
        java.util.List<View> views = buildDayViews(date, inflater);
        for (int i = views.size() - 1; i >= 0; i--) {
            timelineContainer.addView(views.get(i), 0);
        }
        // 높이를 측정해서 반환 (measure → layout 전에는 0이므로 추정값 사용)
        // 날짜 헤더 115px + 기록 없음 텍스트 or 아이템들
        // 정확한 값은 post 이후이므로 여기서는 뷰 측정으로 계산
        int totalH = 0;
        for (View v : views) {
            v.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(timelineContainer.getWidth(), android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            );
            totalH += v.getMeasuredHeight();
        }
        return totalH;
    }

    /** 날짜 하나를 timelineContainer 맨 아래에 추가 */
    private void appendDayToBottom(LocalDate date) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (View v : buildDayViews(date, inflater)) {
            timelineContainer.addView(v);
        }
    }

    /** 날짜 1일치 뷰 목록 생성 (헤더 + 기록 아이템들) */
    private java.util.List<View> buildDayViews(LocalDate date, LayoutInflater inflater) {
        java.util.List<View> views = new ArrayList<>();

        // 날짜 헤더
        View header = inflater.inflate(R.layout.item_log_date_header, timelineContainer, false);
        TextView tvDH = header.findViewById(R.id.tvDateHeader);
        if (tvDH != null) tvDH.setText(formatDateHeader(date));
        header.setTag(date);
        views.add(header);

        // 해당 날짜 기록
        List<WorkRecord> dayList = getRecordsForDate(date);
        if (dayList.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("기록 없음");
            tvEmpty.setTextSize(13f);
            tvEmpty.setTextColor(getResources().getColor(R.color.text_hint, null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(92)); lp.bottomMargin = dp(8);
            tvEmpty.setLayoutParams(lp);
            views.add(tvEmpty);
        } else {
            Collections.sort(dayList, (a, b) -> {
                if (a.getStartTime() == null) return 1;
                if (b.getStartTime() == null) return -1;
                return a.getStartTime().compareTo(b.getStartTime());
            });
            for (int i = 0; i < dayList.size(); i++) {
                View item = inflater.inflate(R.layout.item_log_timeline, timelineContainer, false);
                bindItem(item, dayList.get(i), false); // 연속 선 유지
                views.add(item);
            }
        }
        return views;
    }

    private void renderTimeline() {
        if (timelineContainer == null) return;
        timelineContainer.removeAllViews();
        timelineContainer.setPadding(0, 0, 0, 0);  // 이전 패딩 초기화
        dateScrollY.clear();

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        // 범위 내 전체 기록 수집 + 날짜/시간 오름차순 정렬
        List<WorkRecord> rangeRecords = new ArrayList<>();
        for (WorkRecord r : allRecords) {
            LocalDate d = r.getDate();
            if ((d.isEqual(rangeStart) || d.isAfter(rangeStart))
                    && (d.isEqual(rangeEnd) || d.isBefore(rangeEnd))) {
                rangeRecords.add(r);
            }
        }
        Collections.sort(rangeRecords, (a, b) -> {
            int dc = a.getDate().compareTo(b.getDate());
            if (dc != 0) return dc;
            if (a.getStartTime() == null) return 1;
            if (b.getStartTime() == null) return -1;
            return a.getStartTime().compareTo(b.getStartTime());
        });

        // rangeStart~rangeEnd 모든 날짜를 순서대로 순회 → 구분선 항상 삽입
        // 기록 있는 날: 구분선 + 아이템 / 기록 없는 날: 구분선 + "기록 없음"
        // rangeRecords를 날짜별 맵으로 변환
        java.util.Map<LocalDate, java.util.List<WorkRecord>> byDate = new java.util.LinkedHashMap<>();
        for (WorkRecord r : rangeRecords) {
            byDate.computeIfAbsent(r.getDate(), k -> new ArrayList<>()).add(r);
        }

        // 전체 아이템 수 계산 (마지막 아이템 판별용)
        int totalItems = rangeRecords.size();
        int itemCount  = 0;

        for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
            // 날짜 구분선 (기록 여부 무관하게 항상 삽입)
            View header = inflater.inflate(R.layout.item_log_date_header,
                    timelineContainer, false);
            TextView tvDH = header.findViewById(R.id.tvDateHeader);
            if (tvDH != null) tvDH.setText(formatDateHeader(d));
            header.setTag(d);
            timelineContainer.addView(header);

            java.util.List<WorkRecord> dayList = byDate.get(d);
            if (dayList == null || dayList.isEmpty()) {
                // 기록 없는 날 안내 텍스트
                TextView tvEmpty = new TextView(requireContext());
                tvEmpty.setText("기록 없음");
                tvEmpty.setTextSize(13f);
                tvEmpty.setTextColor(getResources().getColor(R.color.text_hint, null));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart(dp(92)); lp.bottomMargin = dp(8);
                tvEmpty.setLayoutParams(lp);
                timelineContainer.addView(tvEmpty);
            } else {
                for (int i = 0; i < dayList.size(); i++) {
                    itemCount++;
                    boolean isLast = (itemCount == totalItems);
                    View item = inflater.inflate(R.layout.item_log_timeline,
                            timelineContainer, false);
                    bindItem(item, dayList.get(i), isLast);
                    timelineContainer.addView(item);
                }
            }
        }

        // 렌더 완료 후 최신(맨 아래)으로 스크롤
        if (timelineContainer.getChildCount() > 0) {
            scrollToDate(selectDate);
        }
    }

    /**
     * 레이아웃 완료 후 targetDate 날짜 구분선 위치로 스크롤.
     * targetDate의 헤더 뷰를 scrollView 기준 절대 Y로 계산.
     */
    /**
     * selectDate 헤더가 화면 상단에 오도록 하단 패딩 조정.
     *
     * 원리:
     *  1. targetDate 헤더 뷰의 top 좌표를 구함 (= 그 위 콘텐츠 높이)
     *  2. scrollView 가시 높이 - 이후 콘텐츠 높이 = 필요한 하단 패딩
     *  3. rangeEnd == endDate(오늘) 일 때만 패딩 적용, 아닐 때는 패딩 0
     */
    private void scrollToDate(LocalDate targetDate) {
        if (scrollView == null || timelineContainer == null) return;

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // targetDate 헤더 위치 탐색
                    int headerTop = -1;
                    for (int i = 0; i < timelineContainer.getChildCount(); i++) {
                        View child = timelineContainer.getChildAt(i);
                        if (targetDate.equals(child.getTag())) {
                            headerTop = timelineContainer.getTop() + child.getTop();
                            break;
                        }
                    }

                    if (headerTop < 0) {
                        // 해당 날짜 없음 → 패딩 제거 + 상단
                        timelineContainer.setPadding(0, 0, 0, 0);
                        scrollView.scrollTo(0, 0);
                        updateHeaderDate(targetDate);
                        return;
                    }

                    // rangeEnd == endDate(오늘) 일 때만 패딩 적용
                    if (rangeEnd.equals(endDate)) {
                        // scrollView 가시 높이
                        int visibleH = scrollView.getHeight();
                        // headerTop 이후 콘텐츠 높이 (헤더 포함)
                        int totalH   = timelineContainer.getTop()
                                     + timelineContainer.getHeight();
                        int afterH   = totalH - headerTop;

                        // 하단 패딩 = max(0, visibleH - afterH)
                        int bottomPad = Math.max(0, visibleH - afterH);
                        timelineContainer.setPadding(0, 0, 0, bottomPad);
                    } else {
                        // 오늘이 rangeEnd가 아니면 패딩 불필요
                        timelineContainer.setPadding(0, 0, 0, 0);
                    }

                    // 패딩 적용 후 다시 레이아웃 완료를 기다려 스크롤
                    scrollView.post(() -> {
                        int finalTop = -1;
                        for (int i = 0; i < timelineContainer.getChildCount(); i++) {
                            View child = timelineContainer.getChildAt(i);
                            if (targetDate.equals(child.getTag())) {
                                finalTop = timelineContainer.getTop() + child.getTop();
                                break;
                            }
                        }
                        scrollView.scrollTo(0, finalTop >= 0 ? finalTop : 0);
                        updateHeaderDate(targetDate);
                    });
                }
            }
        );
    }

    private List<WorkRecord> getRecordsForDate(LocalDate date) {
        List<WorkRecord> result = new ArrayList<>();
        for (WorkRecord r : allRecords)
            if (r.getDate().equals(date)) result.add(r);
        return result;
    }

    // ── 아이템 바인딩 ─────────────────────────────────────────────────────

    private void bindItem(View item, WorkRecord r, boolean isLastOfDay) {
        TextView tvStart  = item.findViewById(R.id.tvTimelineStart);
        TextView tvEnd    = item.findViewById(R.id.tvTimelineEnd);
        TextView tvStatus = item.findViewById(R.id.tvLogStatus);
        TextView tvName   = item.findViewById(R.id.tvLogTaskName);
        TextView tvZone   = item.findViewById(R.id.tvLogZone);
        TextView tvMemo   = item.findViewById(R.id.tvLogMemo);
        View     dot      = item.findViewById(R.id.timelineDot);
        View     lineBot  = item.findViewById(R.id.timelineLineBottom);

        if (tvStart != null && r.getStartTime() != null)
            tvStart.setText(fmtTime(r.getStartTime()));
        if (tvEnd != null && r.getEndTime() != null)
            tvEnd.setText("~" + fmtTime(r.getEndTime()));
        if (tvName != null) tvName.setText(r.getTaskName());
        if (tvZone != null) {
            String z = r.getZone();
            tvZone.setText((z != null && !z.isEmpty()) ? z : "전체");
        }
        if (tvMemo != null) {
            String m = r.getMemo();
            tvMemo.setText(m != null ? m : "");
        }

        if (tvStatus != null && dot != null) {
            switch (r.getStatus()) {
                case DONE:
                    tvStatus.setText("완료");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_ok);
                    tvStatus.setTextColor(0xFF1B5E20);
                    dot.setBackgroundResource(R.drawable.bg_timeline_dot);
                    break;
                case FAILED:
                    tvStatus.setText("실패");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_error);
                    tvStatus.setTextColor(0xFFC62828);
                    dot.setBackgroundResource(R.drawable.bg_timeline_dot_error);
                    break;
                case RUNNING:
                    tvStatus.setText("진행중");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_ok);
                    tvStatus.setTextColor(0xFF1B5E20);
                    dot.setBackgroundResource(R.drawable.bg_timeline_dot);
                    break;
                case SCHEDULED:
                default:
                    tvStatus.setText("예약");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_scheduled);
                    tvStatus.setTextColor(0xFF1565C0);
                    dot.setBackgroundResource(R.drawable.bg_timeline_dot_scheduled);
                    break;
            }
        }

        // 날짜 마지막 아이템은 아래 선 없음
        if (lineBot != null)
            lineBot.setVisibility(isLastOfDay ? View.INVISIBLE : View.VISIBLE);
    }

    // ── 날짜 헤더 갱신 ───────────────────────────────────────────────────

    /** 헤더 날짜 직접 세팅 */
    private void updateHeaderDate(LocalDate date) {
        if (tvDate == null) return;
        boolean isToday = date.equals(endDate);
        tvDate.setText(String.format(Locale.getDefault(),
                "%d년 %d월 %d일%s",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
                isToday ? "  (오늘)" : ""));
    }

    /** 스크롤 위치 기반으로 현재 보이는 날짜 계산 → 헤더 갱신 */
    private void updateHeaderDateFromScroll(int scrollY) {
        if (timelineContainer == null) return;
        // timelineContainer 자식 중 DateHeader 순회 → 현재 scrollY보다 작은 최대값
        LocalDate visibleDate = rangeEnd;
        for (int i = 0; i < timelineContainer.getChildCount(); i++) {
            View child = timelineContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof LocalDate) {
                int childTop = child.getTop();
                if (childTop <= scrollY + dp(60)) {
                    visibleDate = (LocalDate) tag;
                }
            }
        }
        updateHeaderDate(visibleDate);
    }

    /** selectDate 기준 rangeStart/rangeEnd 계산 */
    private void applySelectDate(LocalDate sel) {
        selectDate = sel;
        baseDate   = sel;
        rangeStart = sel.minusDays(LOAD_DAYS - 1);                         // sel-6
        rangeEnd   = sel.plusDays(LOAD_DAYS).isAfter(endDate)              // sel+7 vs today
                   ? endDate : sel.plusDays(LOAD_DAYS);
    }

    private void updateNextBtn() {
        boolean atEnd = !selectDate.isBefore(endDate);
        if (btnNext != null) {
            btnNext.setAlpha(atEnd ? 0.3f : 1.0f);
            btnNext.setClickable(!atEnd);
        }
    }

    // ── DatePicker ───────────────────────────────────────────────────────

    private void showDatePicker() {
        android.app.DatePickerDialog picker = new android.app.DatePickerDialog(
                requireContext(),
                (dp, y, m, d) -> {
                    LocalDate selected = LocalDate.of(y, m + 1, d);
                    if (selected.isAfter(endDate)) selected = endDate;
                    applySelectDate(selected);
                    renderTimeline();
                    updateHeaderDate(selectDate);
                    updateNextBtn();
                },
                selectDate.getYear(),
                selectDate.getMonthValue() - 1,
                selectDate.getDayOfMonth()
        );
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    // ── 포맷 ─────────────────────────────────────────────────────────────

    private String formatDateHeader(LocalDate date) {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        String suffix = "";
        if (date.equals(today))     suffix = " (오늘)";
        else if (date.equals(yesterday)) suffix = " (어제)";

        return String.format(Locale.getDefault(),
                "%d월 %d일%s",
                date.getMonthValue(), date.getDayOfMonth(), suffix);
    }

    private String fmtTime(java.time.LocalTime t) {
        return String.format(Locale.getDefault(), "%02d:%02d", t.getHour(), t.getMinute());
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

}

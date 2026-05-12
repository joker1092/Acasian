package com.acasian.iot;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.acasian.iot.Calendar.model.CalendarDate;
import com.acasian.iot.Calendar.model.WorkRecord;
import com.acasian.iot.Calendar.view.DateDetailView;
import com.acasian.iot.Calendar.view.MonthCalendarView;
import com.acasian.iot.Calendar.view.WeekCalendarView;
import android.widget.GridLayout;
import android.widget.ImageButton;
import com.acasian.iot.model.IrrigationProfile;
import com.acasian.iot.model.IrrigationProfileManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 예약설정 Activity
 * 안 B: 유형 없음 → BS 오버레이에서 인라인 생성 → 자동 선택 → 예약 흐름 계속
 */
public class ScheduleActivity extends AppCompatActivity {

    private View        header;

    // ── 달력 관련 ──────────────────────────────────────────────────
    private GridLayout        monthGrid;
    private MonthCalendarView monthCalendarView;
    private LinearLayout      weekViewContainer;
    private WeekCalendarView  weekCalendarView;
    private View              detailView;
    private DateDetailView    dateDetailView;
    private View              calendarHeader;
    private View              calendarDayLabels;
    private YearMonth         currentMonth;
    private boolean           isMonthExpanded = true; // 기본 펼침
    private TextView          btnBackToMonth;
    private List<WorkRecord> scheduleList = new ArrayList<>();

    // 새 예약 등록 상태
    private String    selectedTelNo, selectedZoneName;
    private String    selectedProfileId, selectedProfileName;
    private LocalDate selectedDate;
    private LocalTime selectedTime;

    // ── 관수 방식 상태 ──────────────────────────────────────────────
    /** true=자동관수(kind:1) / false=개별관수(kind:2) */
    private boolean   isAutoMode     = true;
    /** true=순차실행(isSeq:Y) / false=동시실행(isSeq:N) */
    private boolean   isSeqMode      = false;
    // 개별관수 직접 입력 값
    private String    indivNodeIds   = "";  // 선택 밸브 콤마구분
    private int       indivStime     = 30;  // 가동시간(분)
    private int       indivDtime     = 0;   // 휴지시간(분)
    private int       indivReCount   = 1;   // 반복횟수

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.forest_dark));
        setContentView(R.layout.activity_schedule);
        // DEV_MODE: ZoneStore 비어있으면 더미 자동 주입
        if (AppConfig.getInstance().isDevMode() && ZoneStore.getInstance().isEmpty()) {
            AppConfig.injectDemoZones();
            DemoData.applyProfiles(this);
        }
        // 상용 모드에서 ZoneStore 비어있으면 → 홈으로 복귀 유도
        if (!AppConfig.getInstance().isDevMode() && ZoneStore.getInstance().isEmpty()) {
            android.widget.Toast.makeText(this,
                    "농장 정보를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.",
                    android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        applyInsets();
        initViews();
        initCalendar();
        loadSchedules();
    }

    private void applyInsets() {
        header = findViewById(R.id.scheduleHeader);
        final int base = header != null ? header.getPaddingTop() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    int sh = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    if (header != null) header.setPadding(
                            header.getPaddingLeft(), base + sh,
                            header.getPaddingRight(), header.getPaddingBottom());
                    return insets;
                });
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnScheduleBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // ── 달력 초기화 ─────────────────────────────────────────────────
    private void initCalendar() {
        monthGrid         = findViewById(R.id.calendar_month);
        weekViewContainer = findViewById(R.id.calendar_week);
        detailView        = findViewById(R.id.calendar_detail);
        calendarHeader    = findViewById(R.id.calendar_header);
        calendarDayLabels = findViewById(R.id.calendar_day_labels);

        if (monthGrid == null) {
            android.util.Log.e("ScheduleActivity", "monthGrid(R.id.calendar_month) not found");
            return;
        }
        if (weekViewContainer == null) {
            android.util.Log.e("ScheduleActivity", "weekViewContainer(R.id.calendar_week) not found");
            return;
        }
        if (detailView == null) {
            android.util.Log.e("ScheduleActivity", "detailView(R.id.calendar_detail) not found");
            return;
        }

        monthCalendarView = new MonthCalendarView(monthGrid);
        weekCalendarView  = new WeekCalendarView(weekViewContainer);

        // 헤더 우상단 월간 보기 버튼
        btnBackToMonth = findViewById(R.id.btnBackToMonth);
        if (btnBackToMonth != null) btnBackToMonth.setOnClickListener(v -> {
            isMonthExpanded = true;
            applyMonthExpanded();
        });

        boolean demoMode = AppConfig.getInstance().isDevMode();
        dateDetailView = new DateDetailView(detailView, demoMode);

        // ── 예약 등록 콜백 → addSchedule API 호출 ───────────────────
        dateDetailView.setOnScheduleAddListener((date, time, profileId, telNo,
                                                  isAuto, isSeqMode, nodeIds,
                                                  stime, dtime, reCount, isRepeat) -> {
            if (demoMode) return; // DEV_MODE: 로컬만 (DateDetailView 내부에서 처리)

            com.acasian.iot.storage.SessionManager session =
                    com.acasian.iot.storage.SessionManager.getInstance(this);
            com.acasian.iot.network.ApiService apiSvc =
                    com.acasian.iot.network.ApiClient.getInstance(this).getService();

            String farmId = com.acasian.iot.ZoneStore.getInstance().getFarmId();
            String yymmdd = com.acasian.iot.network.ApiDateUtil.toYymmdd(date);
            String hhnn   = com.acasian.iot.network.ApiDateUtil.toHhnn(time);
            String isSeqStr = isSeqMode ? "Y" : "N";

            com.acasian.iot.network.ApiService.ScheduleAddRequest req;
            if (isAuto) {
                req = com.acasian.iot.network.ApiService.ScheduleAddRequest.forAutoRepeat(
                        session.getPhoneNumber(), farmId, telNo,
                        profileId, yymmdd, hhnn, isSeqStr, isRepeat);
            } else {
                req = com.acasian.iot.network.ApiService.ScheduleAddRequest.forIndividualRepeat(
                        session.getPhoneNumber(), farmId, telNo,
                        yymmdd, hhnn, isSeqStr,
                        nodeIds, stime, dtime, reCount, isRepeat);
            }

            apiSvc.addSchedule(req).enqueue(
                new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleAddResponse>() {
                    @Override public void onResponse(
                            retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleAddResponse> call,
                            retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleAddResponse> res) {
                        runOnUiThread(() -> {
                            if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) {
                                int schId = res.body().schId;
                                Toast.makeText(ScheduleActivity.this,
                                        "예약이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                loadSchedules();
                                if (schId > 0) genCmdAfterSchedule(String.valueOf(schId));
                            } else {
                                Toast.makeText(ScheduleActivity.this,
                                        "예약 등록 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    @Override public void onFailure(
                            retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleAddResponse> call,
                            Throwable t) {
                        runOnUiThread(() -> Toast.makeText(ScheduleActivity.this,
                                "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
        });

        // ── 예약 삭제 콜백 → delSchedule API 호출 ───────────────────
        dateDetailView.setOnScheduleDeleteListener(schId -> {
            if (demoMode) {
                scheduleList.removeIf(r -> schId.equals(r.getId()));
                updateCalendarUI();
                return;
            }
            com.acasian.iot.network.ApiService apiDel =
                    com.acasian.iot.network.ApiClient.getInstance(this).getService();
            apiDel.delSchedule(new com.acasian.iot.network.ApiService.ScheduleDelRequest(schId))
                  .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                      @Override public void onResponse(
                              retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                              retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> res) {
                          runOnUiThread(() -> {
                              if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) {
                                  Toast.makeText(ScheduleActivity.this,
                                          "예약이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                  loadSchedules();
                              } else {
                                  Toast.makeText(ScheduleActivity.this,
                                          "삭제 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show();
                              }
                          });
                      }
                      @Override public void onFailure(
                              retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                              Throwable t) {
                          runOnUiThread(() -> Toast.makeText(ScheduleActivity.this,
                                  "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                      }
                  });
        });

        currentMonth = YearMonth.now();
        dateDetailView.setMonthCalendarView(monthCalendarView, currentMonth);
        weekCalendarView.setRecords(dateDetailView.getRecords());
        injectDevicesFromZoneStore(dateDetailView);

        // ── 월간 이전/다음 ───────────────────────────────────────────
        ImageButton btnPrev = findViewById(R.id.btn_prev_month);
        ImageButton btnNext = findViewById(R.id.btn_next_month);
        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            updateCalendarUI();
        });
        if (btnNext != null) btnNext.setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            updateCalendarUI();
        });

        // ── 주간 이전/다음 ───────────────────────────────────────────
        ImageButton btnPrevWeek = findViewById(R.id.btn_prev_week);
        ImageButton btnNextWeek = findViewById(R.id.btn_next_week);
        if (btnPrevWeek != null) btnPrevWeek.setOnClickListener(v -> {
            if (selectedDate != null) {
                selectedDate = selectedDate.minusWeeks(1);
                currentMonth = YearMonth.from(selectedDate);
                weekCalendarView.updateWeek(selectedDate);
                dateDetailView.updateDate(selectedDate);
                updateWeekMonthLabel();
            }
        });
        if (btnNextWeek != null) btnNextWeek.setOnClickListener(v -> {
            if (selectedDate != null) {
                selectedDate = selectedDate.plusWeeks(1);
                currentMonth = YearMonth.from(selectedDate);
                weekCalendarView.updateWeek(selectedDate);
                dateDetailView.updateDate(selectedDate);
                updateWeekMonthLabel();
            }
        });

        // ── 월간 날짜 클릭 → 주간 바 + 상세 ────────────────────────
        // 상세 패널 초기 GONE — 날짜 탭 후 주간 뷰 전환 시 VISIBLE
        if (detailView != null) detailView.setVisibility(android.view.View.GONE);

        monthCalendarView.setOnDateClickListener(date -> {
            selectedDate = date.getDate();
            // 주간 바 VISIBLE
            if (weekViewContainer != null) {
                weekViewContainer.setVisibility(android.view.View.VISIBLE);
                updateWeekMonthLabel();
            }
            // 상세 패널 VISIBLE (초기엔 GONE)
            if (detailView != null) detailView.setVisibility(android.view.View.VISIBLE);
            weekCalendarView.updateWeek(selectedDate);
            dateDetailView.updateDate(selectedDate);
            // 월간 달력 접기 (선택 후 더 많은 공간 확보)
            if (isMonthExpanded) {
                isMonthExpanded = false;
                applyMonthExpanded();
            }
        });

        // ── 주간 바 날짜 클릭 → 상세 갱신 ─────────────────────────
        weekCalendarView.setOnDateClickListener(date -> {
            selectedDate = date;
            weekCalendarView.updateWeek(date);
            dateDetailView.updateDate(date);
        });



        updateCalendarUI();
    }

    private void updateCalendarUI() {
        if (currentMonth == null || monthCalendarView == null) return;
        // 서버 예약 목록 → DateDetailView records 동기화
        if (dateDetailView != null && scheduleList != null) {
            dateDetailView.setRecords(scheduleList);
        }
        // 연/월 헤더 텍스트
        TextView tvYM = findViewById(R.id.txt_year_month);
        if (tvYM != null) tvYM.setText(currentMonth.getYear() + "년 "
                + currentMonth.getMonthValue() + "월");
        dateDetailView.setCurrentMonth(currentMonth);
        monthCalendarView.updateCalendar(currentMonth);
        applyMonthExpanded();
    }

    private void applyMonthExpanded() {
        // 월간 달력 영역
        if (calendarHeader    != null) calendarHeader.setVisibility(
                isMonthExpanded ? android.view.View.VISIBLE : android.view.View.GONE);
        if (calendarDayLabels != null) calendarDayLabels.setVisibility(
                isMonthExpanded ? android.view.View.VISIBLE : android.view.View.GONE);
        if (monthGrid         != null) monthGrid.setVisibility(
                isMonthExpanded ? android.view.View.VISIBLE : android.view.View.GONE);

        if (isMonthExpanded) {
            // 월간으로 복귀 → 주간+상세 숨김, 버튼 숨김
            // selectedDate가 있으면 해당 월 달력 유지, 없으면 currentMonth 그대로
            if (weekViewContainer != null)
                weekViewContainer.setVisibility(android.view.View.GONE);
            if (detailView != null)
                detailView.setVisibility(android.view.View.GONE);
            if (btnBackToMonth != null)
                btnBackToMonth.setVisibility(android.view.View.GONE);
            // 선택 날짜가 속한 월로 달력 복원 (updateCalendarUI 직접 호출하면 재귀 → 분리)
            if (selectedDate != null) {
                currentMonth = java.time.YearMonth.from(selectedDate);
                // 헤더 텍스트 갱신
                TextView tvYM = findViewById(R.id.txt_year_month);
                if (tvYM != null) tvYM.setText(currentMonth.getYear() + "년 "
                        + currentMonth.getMonthValue() + "월");
                if (dateDetailView != null) dateDetailView.setCurrentMonth(currentMonth);
                if (monthCalendarView != null) monthCalendarView.updateCalendar(currentMonth);
            }
            selectedDate = null;
        } else {
            // 접힘(주간) → 주간+상세 표시, 버튼 표시
            if (selectedDate != null) {
                if (weekViewContainer != null) {
                    weekViewContainer.setVisibility(android.view.View.VISIBLE);
                    weekCalendarView.updateWeek(selectedDate);
                    updateWeekMonthLabel();
                }
                if (detailView != null)
                    detailView.setVisibility(android.view.View.VISIBLE);
            }
            if (btnBackToMonth != null)
                btnBackToMonth.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void updateWeekMonthLabel() {
        if (weekViewContainer == null || currentMonth == null) return;
        TextView tv = weekViewContainer.findViewById(R.id.txt_week_year_month);
        if (tv != null) tv.setText(currentMonth.getYear() + "년 "
                + currentMonth.getMonthValue() + "월");
    }

    private void injectDevicesFromZoneStore(DateDetailView ddv) {
        java.util.List<ZoneStore.ZoneInfo> zones = ZoneStore.getInstance().getZones();
        if (zones.isEmpty()) return;
        java.util.List<String> names = new ArrayList<>();
        java.util.List<WorkRecord.DeviceType> types = new ArrayList<>();
        for (ZoneStore.ZoneInfo zone : zones)
            for (ZoneStore.NodeInfo node : zone.nodes) {
                names.add(node.name);
                types.add(WorkRecord.DeviceType.PUMP);
            }
        if (!names.isEmpty())
            ddv.setDeviceList(names.toArray(new String[0]),
                    types.toArray(new WorkRecord.DeviceType[0]));
    }

    /** addSchedule 성공 후: 재조회 → 최신 schId로 genScheduleCateGrpCmd 호출 */
    private void loadSchedulesThenGenCmd() {
        com.acasian.iot.storage.SessionManager session =
                com.acasian.iot.storage.SessionManager.getInstance(this);
        com.acasian.iot.network.ApiService api =
                com.acasian.iot.network.ApiClient.getInstance(this).getService();
        api.getSchedule(new com.acasian.iot.network.ApiService.ScheduleGetRequest(
                session.getPhoneNumber()))
           .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleListResponse>() {
               @Override public void onResponse(
                       retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleListResponse> call,
                       retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleListResponse> res) {
                   scheduleList = new java.util.ArrayList<>();
                   if (res.isSuccessful() && res.body() != null
                           && res.body().isSuccess() && res.body().data != null) {
                       int latestSchId = -1;
                       for (com.acasian.iot.network.ApiService.ScheduleListResponse.ScheduleItem item
                               : res.body().data) {
                           java.time.LocalDate date =
                                   com.acasian.iot.network.ApiDateUtil.fromYymmdd(item.yymmdd);
                           java.time.LocalTime time =
                                   com.acasian.iot.network.ApiDateUtil.fromHhnn(item.hhnn);
                           if (date == null || time == null) continue;
                           String schIdStr  = String.valueOf(item.schId);
                           String cateIdStr = String.valueOf(item.cateId);
                           // isSched: Y=실행완료→DONE, N=미실행→날짜기준 판단
                           com.acasian.iot.Calendar.model.WorkRecord.Status recStatus;
                           if ("Y".equalsIgnoreCase(item.isSched)) {
                               recStatus = com.acasian.iot.Calendar.model.WorkRecord.Status.DONE;
                           } else {
                               java.time.LocalDateTime schedDt = java.time.LocalDateTime.of(date, time);
                               recStatus = schedDt.isBefore(java.time.LocalDateTime.now())
                                       ? com.acasian.iot.Calendar.model.WorkRecord.Status.FAILED
                                       : com.acasian.iot.Calendar.model.WorkRecord.Status.SCHEDULED;
                           }
                           com.acasian.iot.Calendar.model.WorkRecord rec =
                                   new com.acasian.iot.Calendar.model.WorkRecord(
                                           schIdStr, date, time, time.plusMinutes(30),
                                           item.cateName != null ? item.cateName : cateIdStr,
                                           com.acasian.iot.Calendar.model.WorkRecord.DeviceType.PUMP,
                                           cateIdStr, item.lteNo,
                                           recStatus, "");
                           rec.setIrrigationProfileId(cateIdStr);
                           scheduleList.add(rec);
                           if (item.schId > latestSchId) latestSchId = item.schId;
                       }
                       final int schId = latestSchId;
                       runOnUiThread(() -> {
                           updateCalendarUI();
                           if (schId > 0) genCmdAfterSchedule(String.valueOf(schId));
                       });
                   } else {
                       runOnUiThread(() -> updateCalendarUI());
                   }
               }
               @Override public void onFailure(
                       retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleListResponse> call,
                       Throwable t) {
                   scheduleList = new java.util.ArrayList<>();
                   runOnUiThread(() -> updateCalendarUI());
               }
           });
    }

    /** addSchedule/updSchedule 성공 후 genScheduleCateGrpCmd 호출 */
    private void genCmdAfterSchedule(String schId) {
        if (schId == null || schId.isEmpty() || schId.startsWith("r_")) return;
        com.acasian.iot.network.ApiService apiSvc =
                com.acasian.iot.network.ApiClient.getInstance(this).getService();
        apiSvc.genScheduleCateGrpCmd(
                new com.acasian.iot.network.ApiService.SchIdRequest(schId))
              .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                  @Override public void onResponse(
                          retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                          retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> res) {
                      android.util.Log.d("ScheduleActivity",
                              "genCmd schId=" + schId + " → " + (res.isSuccessful() ? "OK" : res.code()));
                  }
                  @Override public void onFailure(
                          retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                          Throwable t) {
                      android.util.Log.e("ScheduleActivity", "genCmd 실패: " + t.getMessage());
                  }
              });
    }

    private void loadSchedules() {
        if (AppConfig.getInstance().isDevMode()) {
            // DEV_MODE: 더미 데이터 사용
            scheduleList = DemoData.buildRecords();
            return;
        }

        com.acasian.iot.storage.SessionManager session =
                com.acasian.iot.storage.SessionManager.getInstance(this);
        com.acasian.iot.network.ApiService api =
                com.acasian.iot.network.ApiClient.getInstance(ScheduleActivity.this).getService();

        api.getSchedule(new com.acasian.iot.network.ApiService.ScheduleGetRequest(
                session.getPhoneNumber()))
           .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleListResponse>() {
               @Override
               public void onResponse(
                       retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleListResponse> call,
                       retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleListResponse> res) {
                   scheduleList = new java.util.ArrayList<>();
                   if (res.isSuccessful() && res.body() != null && res.body().isSuccess()
                           && res.body().data != null) {
                       for (com.acasian.iot.network.ApiService.ScheduleListResponse.ScheduleItem item
                               : res.body().data) {
                           java.time.LocalDate date =
                                   com.acasian.iot.network.ApiDateUtil.fromYymmdd(item.yymmdd);
                           java.time.LocalTime time =
                                   com.acasian.iot.network.ApiDateUtil.fromHhnn(item.hhnn);
                           if (date == null || time == null) continue;
                           String schIdStr  = String.valueOf(item.schId);
                           String cateIdStr = String.valueOf(item.cateId);
                           com.acasian.iot.Calendar.model.WorkRecord.Status recStatus2;
                           if ("Y".equalsIgnoreCase(item.isSched)) {
                               recStatus2 = com.acasian.iot.Calendar.model.WorkRecord.Status.DONE;
                           } else {
                               java.time.LocalDateTime schedDt2 = java.time.LocalDateTime.of(date, time);
                               recStatus2 = schedDt2.isBefore(java.time.LocalDateTime.now())
                                       ? com.acasian.iot.Calendar.model.WorkRecord.Status.FAILED
                                       : com.acasian.iot.Calendar.model.WorkRecord.Status.SCHEDULED;
                           }
                           com.acasian.iot.Calendar.model.WorkRecord rec =
                                   new com.acasian.iot.Calendar.model.WorkRecord(
                                           schIdStr, date, time, time.plusMinutes(30),
                                           item.cateName != null ? item.cateName : cateIdStr,
                                           com.acasian.iot.Calendar.model.WorkRecord.DeviceType.PUMP,
                                           cateIdStr, item.lteNo,
                                           recStatus2, "");
                           rec.setIrrigationProfileId(cateIdStr);
                           scheduleList.add(rec);
                       }
                   } else if (!res.isSuccessful()) {
                       runOnUiThread(() -> Toast.makeText(ScheduleActivity.this,
                               "예약 목록 로드 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show());
                   }
                   runOnUiThread(() -> updateCalendarUI());
               }
               @Override
               public void onFailure(
                       retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleListResponse> call,
                       Throwable t) {
                   scheduleList = new java.util.ArrayList<>();
                   android.util.Log.e("ScheduleActivity", "getSchedule failed: " + t.getMessage(), t);
                   runOnUiThread(() -> updateCalendarUI());
               }
           });
    }



    private void showDetail(WorkRecord rec) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        String msg = "유형: " + rec.getTaskName()
                + "\n대상: " + rec.getZone()
                + "\n일시: " + rec.getDate().atTime(rec.getStartTime()).format(fmt)
                + "\n메모: " + rec.getMemo();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("예약 상세").setMessage(msg)
                .setNegativeButton("삭제", (d, w) -> confirmDelete(rec))
                .setNeutralButton("닫기", null).show();
    }

    private void confirmDelete(WorkRecord rec) {
        float dp = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        int p = Math.round(20 * dp);
        root.setPadding(p, p, p, Math.round(8 * dp));

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("예약 삭제");
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF1B2E1B);
        tvTitle.setPadding(0, 0, 0, Math.round(10 * dp));
        root.addView(tvTitle);

        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(0xFFE0E0E0);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * dp)));
        root.addView(div);

        android.widget.TextView tvMsg = new android.widget.TextView(this);
        tvMsg.setText("[" + rec.getTaskName() + "]\n예약을 삭제하시겠습니까?");
        tvMsg.setTextSize(17f);
        tvMsg.setTextColor(0xFF555555);
        tvMsg.setLineSpacing(0, 1.3f);
        tvMsg.setPadding(0, Math.round(14 * dp), 0, Math.round(4 * dp));
        root.addView(tvMsg);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setGravity(android.view.Gravity.END);
        btnRow.setPadding(0, Math.round(16 * dp), 0, 0);

        androidx.appcompat.app.AlertDialog[] ref = {null};
        android.widget.Button btnCancel = new android.widget.Button(this);
        btnCancel.setText("취소");
        btnCancel.setTextSize(17f);
        btnCancel.setTextColor(0xFF9E9E9E);
        btnCancel.setBackground(null);
        btnCancel.setPadding(Math.round(8*dp),0,Math.round(8*dp),0);
        btnCancel.setOnClickListener(v -> { if(ref[0]!=null) ref[0].dismiss(); });

        android.widget.Button btnDel = new android.widget.Button(this);
        btnDel.setText("삭제");
        btnDel.setTextSize(17f);
        btnDel.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDel.setTextColor(0xFFC62828);
        btnDel.setBackground(null);
        btnDel.setPadding(Math.round(12*dp),0,Math.round(4*dp),0);
        btnDel.setOnClickListener(v -> {
            if(ref[0]!=null) ref[0].dismiss();
            // 삭제 실행
            if (AppConfig.getInstance().isDevMode()) {
                        // DEV_MODE: 로컬 목록에서만 제거
                        scheduleList.remove(rec);
                        Toast.makeText(this, "예약이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    com.acasian.iot.network.ApiService apiDel =
                            com.acasian.iot.network.ApiClient.getInstance(ScheduleActivity.this).getService();
                    apiDel.delSchedule(new com.acasian.iot.network.ApiService.ScheduleDelRequest(
                            rec.getId()))
                       .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                           @Override public void onResponse(
                                   retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                                   retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> res) {
                               runOnUiThread(() -> {
                                   if (res.isSuccessful() && res.body() != null) {
                                       scheduleList.remove(rec);
                                       Toast.makeText(ScheduleActivity.this,
                                               "예약이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                       updateCalendarUI();
                                   } else {
                                       Toast.makeText(ScheduleActivity.this,
                                               "삭제 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show();
                                   }
                               });
                           }
                           @Override public void onFailure(
                                   retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                                   Throwable t) {
                               runOnUiThread(() ->
                                   Toast.makeText(ScheduleActivity.this,
                                           "삭제 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                           }
                       });
        });
        btnRow.addView(btnCancel);
        btnRow.addView(btnDel);
        root.addView(btnRow);

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(root).create();
        if (dlg.getWindow() != null) {
            android.graphics.drawable.GradientDrawable wbg = new android.graphics.drawable.GradientDrawable();
            wbg.setColor(0xFFFFFFFF); wbg.setCornerRadius(16 * dp);
            dlg.getWindow().setBackgroundDrawable(wbg);
        }
        ref[0] = dlg;
        dlg.show();
    }

    // ── 새 예약 등록 STEP 흐름 ───────────────────────────────────────

    private void step1_pickGateway() {
        // DevMode: DemoData / 상용: ZoneStore
        String[] names  = AppConfig.getInstance().isDevMode()
                ? DemoData.getZoneNames()
                : ZoneStore.getInstance().getZoneNames();
        String[] telNos = AppConfig.getInstance().isDevMode()
                ? DemoData.getZoneTelNos()
                : ZoneStore.getInstance().getZoneTelNos();
        final int[] chosen = {0};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("게이트웨이 선택")
                .setSingleChoiceItems(names, 0, (d, w) -> chosen[0] = w)
                .setPositiveButton("선택", (d, w) -> {
                    selectedTelNo    = telNos[chosen[0]];
                    selectedZoneName = names[chosen[0]];
                    step2_pickProfile();
                })
                .setNegativeButton("취소", null).show();
    }

    // ── 안 B: BS 인라인 — 유형 없음·+ 추가 모두 bsPanelForm 패널 전환 ──────────
    private void step2_pickProfile() {
        IrrigationProfileManager mgr      = IrrigationProfileManager.getInstance(this);
        List<IrrigationProfile>  all      = mgr.getAll();
        List<IrrigationProfile>  filtered = new ArrayList<>();
        for (IrrigationProfile p : all)
            if (selectedTelNo != null && selectedTelNo.equals(p.getZoneId()))
                filtered.add(p);

        BottomSheetDialog bs   = new BottomSheetDialog(this);
        View              root = LayoutInflater.from(this)
                .inflate(R.layout.bottom_sheet_irrigation_type, null);
        bs.setContentView(root);

        // ── 뷰 참조 ──────────────────────────────────────────────
        TextView     bsTitle         = root.findViewById(R.id.bsTitle);
        TextView     bsZoneName      = root.findViewById(R.id.bsZoneName);
        TextView     bsBtnBack       = root.findViewById(R.id.bsBtnBack);
        LinearLayout bsPanelList     = root.findViewById(R.id.bsPanelList);
        LinearLayout bsPanelForm     = root.findViewById(R.id.bsPanelForm);
        LinearLayout bsCardContainer = root.findViewById(R.id.bsCardContainer);
        TextView     bsBtnAddType    = root.findViewById(R.id.bsBtnAddType);
        Button       bsBtnCancel     = root.findViewById(R.id.bsBtnCancel);
        Button       bsBtnConfirm    = root.findViewById(R.id.bsBtnConfirm);
        Button       bsFormBtnCancel = root.findViewById(R.id.bsFormBtnCancel);
        Button       bsFormBtnSave   = root.findViewById(R.id.bsFormBtnSave);
        EditText     bsFormName      = root.findViewById(R.id.bsFormName);
        EditText     bsFormRun       = root.findViewById(R.id.bsFormRun);
        EditText     bsFormRepeat    = root.findViewById(R.id.bsFormRepeat);
        EditText     bsFormRest      = root.findViewById(R.id.bsFormRest);
        LinearLayout bsFormGroupCont = root.findViewById(R.id.bsFormGroupContainer);

        if (bsZoneName != null) bsZoneName.setText(selectedZoneName);

        // 항상 그룹 구성
        final List<List<String>> groupNodesList = new ArrayList<>();
        groupNodesList.add(new ArrayList<>()); // 초기 그룹 1개

        // ── 그룹 목록 재빌드 ──────────────────────────────────────
        Runnable[] rebuildRef = {null};
        Runnable rebuildGroups = () -> {
            if (bsFormGroupCont == null) return;
            bsFormGroupCont.removeAllViews();
            for (int i = 0; i < groupNodesList.size(); i++) {
                final int idx   = i;
                List<String> nids = groupNodesList.get(i);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = bsDp(8);
                row.setLayoutParams(rowLp);

                // 순위 배지
                TextView tvPri = new TextView(this);
                tvPri.setText(String.valueOf(i + 1));
                tvPri.setTextSize(13f);
                tvPri.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPri.setTextColor(getResources().getColor(R.color.white, null));
                tvPri.setBackgroundResource(R.drawable.bg_btn_zone_start);
                tvPri.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams priLp = new LinearLayout.LayoutParams(bsDp(28), bsDp(28));
                priLp.rightMargin = bsDp(8);
                tvPri.setLayoutParams(priLp);
                row.addView(tvPri);

                // 밸브 레이블 (클릭 → 노드 피커)
                TextView tvLabel = new TextView(this);
                String lbl = bsBuildNodeLabel(nids, selectedTelNo);
                tvLabel.setText(lbl.isEmpty() ? "밸브 없음 (탭하여 선택)" : lbl);
                tvLabel.setTextSize(13f);
                tvLabel.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                        nids.isEmpty() ? R.color.text_hint : R.color.text_primary));
                tvLabel.setBackground(androidx.core.content.ContextCompat.getDrawable(this,
                        R.drawable.bg_input_normal));
                tvLabel.setPadding(bsDp(10), bsDp(8), bsDp(10), bsDp(8));
                tvLabel.setClickable(true); tvLabel.setFocusable(true);
                LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lblLp.rightMargin = bsDp(4);
                tvLabel.setLayoutParams(lblLp);
                tvLabel.setOnClickListener(v2 -> {
                    List<String> usedElsewhere = new ArrayList<>();
                    for (int j = 0; j < groupNodesList.size(); j++)
                        if (j != idx) usedElsewhere.addAll(groupNodesList.get(j));
                    bsShowNodePicker(selectedTelNo, nids, usedElsewhere, picked -> {
                        groupNodesList.set(idx, picked);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                });
                row.addView(tvLabel);

                // ↑ 위로
                if (i > 0) {
                    TextView btnUp = bsMakeIconBtn("↑");
                    btnUp.setOnClickListener(v2 -> {
                        List<String> tmp = groupNodesList.remove(idx);
                        groupNodesList.add(idx - 1, tmp);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                    row.addView(btnUp);
                }
                // ↓ 아래로
                if (i < groupNodesList.size() - 1) {
                    TextView btnDown = bsMakeIconBtn("↓");
                    btnDown.setOnClickListener(v2 -> {
                        List<String> tmp = groupNodesList.remove(idx);
                        groupNodesList.add(idx + 1, tmp);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                    row.addView(btnDown);
                }
                // ✕ 삭제
                if (groupNodesList.size() > 1) {
                    TextView btnDel = bsMakeIconBtn("✕");
                    btnDel.setTextColor(getResources().getColor(R.color.device_accent_error, null));
                    btnDel.setOnClickListener(v2 -> {
                        groupNodesList.remove(idx);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                    row.addView(btnDel);
                }
                bsFormGroupCont.addView(row);
            }

            // + 그룹 추가 버튼
            TextView btnAddGrp = new TextView(this);
            btnAddGrp.setText("+ 그룹 추가");
            btnAddGrp.setTextSize(13f);
            btnAddGrp.setTypeface(null, android.graphics.Typeface.BOLD);
            btnAddGrp.setTextColor(getResources().getColor(R.color.moss, null));
            btnAddGrp.setPadding(bsDp(4), bsDp(6), bsDp(4), bsDp(4));
            btnAddGrp.setClickable(true); btnAddGrp.setFocusable(true);
            btnAddGrp.setOnClickListener(v2 -> {
                List<String> usedAll = new ArrayList<>();
                for (List<String> g : groupNodesList) usedAll.addAll(g);
                bsShowNodePicker(selectedTelNo, new ArrayList<>(), usedAll, picked -> {
                    if (!picked.isEmpty()) {
                        groupNodesList.add(picked);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    }
                });
            });
            bsFormGroupCont.addView(btnAddGrp);
        };
        rebuildRef[0] = rebuildGroups;

        // 그룹 컨테이너 항상 표시
        if (bsFormGroupCont != null) bsFormGroupCont.setVisibility(android.view.View.VISIBLE);
        rebuildGroups.run();

        // ── 패널 전환 ─────────────────────────────────────────────
        Runnable showList = () -> {
            if (bsPanelList != null) bsPanelList.setVisibility(android.view.View.VISIBLE);
            if (bsPanelForm != null) bsPanelForm.setVisibility(android.view.View.GONE);
            if (bsBtnBack   != null) bsBtnBack.setVisibility(android.view.View.GONE);
            if (bsTitle     != null) bsTitle.setText("관수 유형 선택");
        };
        Runnable showForm = () -> {
            if (bsPanelList != null) bsPanelList.setVisibility(android.view.View.GONE);
            if (bsPanelForm != null) bsPanelForm.setVisibility(android.view.View.VISIBLE);
            if (bsBtnBack   != null) bsBtnBack.setVisibility(android.view.View.VISIBLE);
            if (bsTitle     != null) bsTitle.setText("새 유형 만들기");
            if (bsFormName  != null) bsFormName.requestFocus();
        };

        // ── 유형 목록 or 유형 없음 바로 폼 ───────────────────────
        if (filtered.isEmpty()) {
            showForm.run();
        } else {
            showList.run();
            final IrrigationProfile[] selectedHolder = {filtered.get(0)};

            // ① 카드: item_irrigation_type_card.xml (match_parent 전체폭)
            for (IrrigationProfile p : filtered) {
                View card = LayoutInflater.from(this)
                        .inflate(R.layout.item_irrigation_type_card, bsCardContainer, false);
                // 편집/삭제 버튼 숨김 (예약 선택 모드)
                View btnEdit = card.findViewById(R.id.btnEditType);
                View btnDel  = card.findViewById(R.id.btnDeleteType);
                if (btnEdit != null) btnEdit.setVisibility(android.view.View.GONE);
                if (btnDel  != null) btnDel.setVisibility(android.view.View.GONE);
                TextView tvName = card.findViewById(R.id.tvTypeName);
                TextView tvRun  = card.findViewById(R.id.tvRunMinutes);
                TextView tvRest = card.findViewById(R.id.tvRestMinutes);
                TextView tvRep  = card.findViewById(R.id.tvRepeatCount);
                if (tvName != null) tvName.setText(p.getName());
                if (tvRun  != null) tvRun.setText(p.getRunMinutes() + "분");
                if (tvRest != null) tvRest.setText(p.getRestMinutes() > 0 ? p.getRestMinutes() + "분" : "없음");
                if (tvRep  != null) tvRep.setText(p.getRepeatCount() + "회");
                card.setClickable(true); card.setFocusable(true);
                card.setTag("card_" + p.getId());
                card.setOnClickListener(v -> {
                    selectedHolder[0] = p;
                    // 모든 카드 테두리 초기화
                    for (int i = 0; i < bsCardContainer.getChildCount(); i++) {
                        android.view.View c = bsCardContainer.getChildAt(i);
                        c.setBackgroundResource(R.drawable.bg_irrigation_card);
                    }
                    // 선택 카드 강조 (초록 테두리 + 연초록 배경)
                    card.setBackgroundResource(R.drawable.bg_irrigation_card_selected);
                });
                bsCardContainer.addView(card);
                // 첫 번째 카드 기본 선택
                if (p == filtered.get(0)) {
                    card.setBackgroundResource(R.drawable.bg_irrigation_card_selected);
                }
            }
            if (bsBtnConfirm != null) bsBtnConfirm.setOnClickListener(v -> {
                selectedProfileId   = selectedHolder[0].getId();
                selectedProfileName = selectedHolder[0].getName();
                isAutoMode = true;   // 유형 선택 = 자동관수
                bs.dismiss();
                step3_pickDate();
            });
            // ② + 새 관수 유형 추가 → BS 안에서 폼으로 전환 (이탈 없음)
            if (bsBtnAddType != null) bsBtnAddType.setOnClickListener(v -> showForm.run());
        }

        // ── 뒤로 ─────────────────────────────────────────────────
        if (bsBtnBack != null) bsBtnBack.setOnClickListener(v -> {
            if (filtered.isEmpty()) bs.dismiss();
            else showList.run();
        });
        if (bsBtnCancel     != null) bsBtnCancel.setOnClickListener(v -> bs.dismiss());
        if (bsFormBtnCancel != null) bsFormBtnCancel.setOnClickListener(v -> {
            if (filtered.isEmpty()) bs.dismiss();
            else showList.run();
        });

        // ── 저장 후 선택 → step3 자동 진행 ──────────────────────
        if (bsFormBtnSave != null) bsFormBtnSave.setOnClickListener(v -> {
            String name = bsFormName != null ? bsFormName.getText().toString().trim() : "";
            if (android.text.TextUtils.isEmpty(name)) {
                Toast.makeText(this, "유형 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (groupNodesList.stream().allMatch(List::isEmpty)) {
                Toast.makeText(this, "그룹에 밸브를 1개 이상 추가해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            IrrigationProfile newP = new IrrigationProfile();
            newP.setId(java.util.UUID.randomUUID().toString());
            newP.setName(name);
            newP.setZoneId(selectedTelNo);
            newP.setZoneName(selectedZoneName != null ? selectedZoneName : "");
            newP.setProfileType(IrrigationProfile.ProfileType.AUTO);
            newP.setTargetType(IrrigationProfile.TargetType.ZONE_ALL);
            newP.setRunMinutes(parseIntSafe(bsFormRun, 30));
            newP.setRepeatCount(parseIntSafe(bsFormRepeat, 1));
            newP.setRestMinutes(parseIntSafe(bsFormRest, 0));
            List<IrrigationProfile.Group> groups = new ArrayList<>();
            for (int i = 0; i < groupNodesList.size(); i++) {
                IrrigationProfile.Group g = new IrrigationProfile.Group();
                g.setNodeIds(groupNodesList.get(i));
                g.setPriority(i + 1);
                groups.add(g);
            }
            newP.setGroups(groups);
            IrrigationProfileManager.getInstance(this).save(newP);
            selectedProfileId   = newP.getId();
            selectedProfileName = newP.getName();
            isAutoMode = true;   // 유형 생성 후 선택 = 자동관수
            bs.dismiss();
            Toast.makeText(this, name + " 유형이 선택됐어요.", Toast.LENGTH_SHORT).show();
            step3_pickDate();
        });

        bs.show();
    }

    // ── BS 폼용 헬퍼 메서드 ──────────────────────────────────────────────

    private void bsShowNodePicker(String telNo, List<String> current,
                                   List<String> usedInOtherGroups,
                                   java.util.function.Consumer<List<String>> onPicked) {
        // DEV_MODE: 노드 없으면 더미 자동 주입
        if (AppConfig.getInstance().isDevMode()
                && ZoneStore.getInstance().getNodeIds(telNo).length == 0) {
            AppConfig.injectDemoZones();
        }
        String[] allIds   = ZoneStore.getInstance().getNodeIds(telNo);
        String[] allNames = ZoneStore.getInstance().getNodeNames(telNo);
        if (allIds == null || allIds.length == 0) {
            if (AppConfig.getInstance().isDevMode())
                Toast.makeText(this, "DEV_MODE: 노드 정보 없음 — 재시작 필요", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, "이 메인함의 노드 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] displayNames = new String[allNames.length];
        boolean[] enabled     = new boolean[allNames.length];
        for (int i = 0; i < allNames.length; i++) {
            if (usedInOtherGroups.contains(allIds[i])) {
                displayNames[i] = allNames[i] + "  (다른 그룹 사용 중)";
                enabled[i]      = false;
            } else {
                displayNames[i] = allNames[i];
                enabled[i]      = true;
            }
        }
        boolean[] checked = new boolean[allIds.length];
        for (int i = 0; i < allIds.length; i++)
            checked[i] = current.contains(allIds[i]);
        List<String> temp = new ArrayList<>(current);
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("밸브 선택")
            .setMultiChoiceItems(displayNames, checked, (d, which, isChecked) -> {
                if (!enabled[which]) {
                    ((androidx.appcompat.app.AlertDialog) d).getListView().setItemChecked(which, false);
                    Toast.makeText(this, allNames[which] + "은(는) 이미 다른 그룹에 속해 있습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isChecked) { if (!temp.contains(allIds[which])) temp.add(allIds[which]); }
                else temp.remove(allIds[which]);
            })
            .setPositiveButton("확인", (d, w) -> onPicked.accept(temp))
            .setNegativeButton("취소", null)
            .show();
    }

    private String bsBuildNodeLabel(List<String> nodeIds, String telNo) {
        if (nodeIds == null || nodeIds.isEmpty()) return "";
        List<ZoneStore.NodeInfo> nodes = ZoneStore.getInstance().getNodesByTelNo(telNo);
        java.util.Map<String, String> idToName = new java.util.HashMap<>();
        for (ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);
        StringBuilder sb = new StringBuilder();
        for (String nid : nodeIds) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(idToName.containsKey(nid) ? idToName.get(nid) : nid);
        }
        return sb.toString();
    }

    private TextView bsMakeIconBtn(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_hint));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(bsDp(32), bsDp(32)));
        tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    private int bsDp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
    private int parseIntSafe(EditText et, int fallback) {
        if (et == null) return fallback;
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private void step3_pickDate() {
        LocalDate today = LocalDate.now();
        android.view.View v = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_date_picker, null);

        android.widget.TextView tvPreview     = v.findViewById(R.id.tvDatePreview);
        android.widget.TextView tvYearMonth   = v.findViewById(R.id.tvDateYearMonth);
        android.widget.TextView btnPrev       = v.findViewById(R.id.btnDatePrevMonth);
        android.widget.TextView btnNext       = v.findViewById(R.id.btnDateNextMonth);
        android.widget.GridLayout grid        = v.findViewById(R.id.gridDatePicker);
        android.widget.Button btnCancel       = v.findViewById(R.id.btnDateCancel);
        android.widget.Button btnConfirm      = v.findViewById(R.id.btnDateConfirm);

        java.time.YearMonth[] curMonth = { java.time.YearMonth.of(today.getYear(), today.getMonthValue()) };
        LocalDate[] picked = { null };

        String[] KO_MONTH = {"1월","2월","3월","4월","5월","6월",
                              "7월","8월","9월","10월","11월","12월"};

        Runnable[] buildRef = { null };
        Runnable build = () -> {
            java.time.YearMonth ym = curMonth[0];
            tvYearMonth.setText(ym.getYear() + "년  " + KO_MONTH[ym.getMonthValue() - 1]);
            grid.removeAllViews();

            // 1일 요일 (일=1, 월=2, ... 토=7 → 인덱스 0~6)
            int firstDow = ym.atDay(1).getDayOfWeek().getValue() % 7; // 일=0
            int days = ym.lengthOfMonth();
            float dp = getResources().getDisplayMetrics().density;
            int cellH = Math.round(52 * dp);

            // 빈 셀
            for (int i = 0; i < firstDow; i++) {
                android.view.View empty = new android.view.View(this);
                android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams(
                        android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, 1f),
                        android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, 1f));
                lp.width = 0; lp.height = cellH;
                empty.setLayoutParams(lp);
                grid.addView(empty);
            }

            for (int day = 1; day <= days; day++) {
                final LocalDate date = LocalDate.of(ym.getYear(), ym.getMonthValue(), day);
                boolean isPast = date.isBefore(today);
                boolean isToday = date.equals(today);
                boolean isSelected = date.equals(picked[0]);

                android.widget.TextView cell = new android.widget.TextView(this);
                cell.setText(String.valueOf(day));
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setTextSize(18f);
                cell.setTypeface(null, isToday || isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

                // 색상
                int col = (firstDow + day - 1) % 7; // 0=일, 6=토
                if (isPast)        cell.setTextColor(0xFFBBBBBB);
                else if (isSelected) cell.setTextColor(0xFF2C5F2D);
                else if (col == 0)   cell.setTextColor(0xFFE53935);
                else if (col == 6)   cell.setTextColor(0xFF1565C0);
                else                 cell.setTextColor(0xFF222222);

                // 배경: 오늘=테두리, 선택=초록 원
                if (isSelected) {
                    cell.setBackgroundResource(R.drawable.bg_btn_zone_start);
                    cell.setTextColor(0xFFFFFFFF);
                } else if (isToday) {
                    cell.setBackground(androidx.core.content.ContextCompat
                            .getDrawable(this, R.drawable.bg_timeline_dot_scheduled));
                }

                if (isPast) {
                    cell.setAlpha(0.35f);
                    cell.setEnabled(false);
                } else {
                    cell.setClickable(true); cell.setFocusable(true);
                    if (cell.getBackground() == null)
                        cell.setBackground(androidx.core.content.ContextCompat.getDrawable(
                                this, android.R.drawable.list_selector_background));
                    cell.setOnClickListener(cv -> {
                        picked[0] = date;
                        java.time.format.DateTimeFormatter f =
                                java.time.format.DateTimeFormatter.ofPattern("M월 d일 (E)");
                        String label = date.format(f);
                        // 한글 요일
                        String[] KO_DOW = {"월","화","수","목","금","토","일"};
                        String dow = KO_DOW[date.getDayOfWeek().getValue() - 1];
                        tvPreview.setText(ym.getYear() + "년  " + date.getMonthValue() + "월 "
                                + date.getDayOfMonth() + "일 (" + dow + ")");
                        if (buildRef[0] != null) buildRef[0].run();
                    });
                }

                android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams(
                        android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, 1f),
                        android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1, 1f));
                lp.width = 0; lp.height = cellH;
                lp.setMargins(2, 2, 2, 2);
                cell.setLayoutParams(lp);
                grid.addView(cell);
            }
        };
        buildRef[0] = build;
        build.run();

        if (btnPrev != null) btnPrev.setOnClickListener(pv -> {
            if (curMonth[0].isAfter(java.time.YearMonth.from(today))) {
                curMonth[0] = curMonth[0].minusMonths(1);
                build.run();
            }
        });
        if (btnNext != null) btnNext.setOnClickListener(nv -> {
            curMonth[0] = curMonth[0].plusMonths(1);
            build.run();
        });

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(v).create();

        if (btnCancel  != null) btnCancel.setOnClickListener(cv -> dlg.dismiss());
        if (btnConfirm != null) btnConfirm.setOnClickListener(cv -> {
            if (picked[0] == null) {
                Toast.makeText(this, "날짜를 선택해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedDate = picked[0];
            dlg.dismiss();
            step4_pickTime();
        });
        dlg.show();
    }

    private void step4_pickTime() {
        android.view.View tv = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_time_picker, null);

        com.acasian.iot.Calendar.view.WheelTimePickerView pickerH =
                tv.findViewById(R.id.pickerHour);
        com.acasian.iot.Calendar.view.WheelTimePickerView pickerM =
                tv.findViewById(R.id.pickerMinute);
        android.widget.TextView tvPreview = tv.findViewById(R.id.tvPickerPreview);
        android.widget.Button btnCancel   = tv.findViewById(R.id.btnPickerCancel);
        android.widget.Button btnConfirm  = tv.findViewById(R.id.btnPickerConfirm);

        final int[] hVal = {6}, mVal = {0};

        Runnable updatePreview = () -> {
            if (tvPreview != null)
                tvPreview.setText(String.format("%02d : %02d", hVal[0], mVal[0]));
        };

        if (pickerH != null) {
            pickerH.setRange(0, 23);
            pickerH.setWrapSelectorWheel(true);
            pickerH.setValue(6);
            pickerH.setOnValueChangeListener((vv, o, n) -> { hVal[0] = n; updatePreview.run(); });
        }
        if (pickerM != null) {
            pickerM.setRange(0, 59);
            pickerM.setWrapSelectorWheel(true);
            pickerM.setValue(0);
            pickerM.setOnValueChangeListener((vv, o, n) -> { mVal[0] = n; updatePreview.run(); });
        }
        updatePreview.run();

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(tv).create();

        if (btnCancel  != null) btnCancel.setOnClickListener(cv -> dlg.dismiss());
        if (btnConfirm != null) btnConfirm.setOnClickListener(cv -> {
            selectedTime = LocalTime.of(hVal[0], mVal[0]);
            dlg.dismiss();
            step5_confirm();
        });
        dlg.show();
    }

    private void step5_confirm() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
        String[] dowKo = {"월","화","수","목","금","토","일"};
        String dow = dowKo[selectedDate.getDayOfWeek().getValue() - 1];
        String dateStr = selectedDate.format(fmt) + " (" + dow + ")";
        String timeStr = selectedTime.format(DateTimeFormatter.ofPattern("HH:mm"));

        // 큰 글씨 커스텀 확인 다이얼로그
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, Math.round(8 * getResources().getDisplayMetrics().density));

        // 제목
        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("예약 등록 확인");
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF1B5E20);
        tvTitle.setPadding(0, 0, 0, Math.round(16 * getResources().getDisplayMetrics().density));
        root.addView(tvTitle);

        // 구분선
        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(0xFFE0E0E0);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * getResources().getDisplayMetrics().density)));
        root.addView(div);

        int rowPad = Math.round(12 * getResources().getDisplayMetrics().density);

        // 관수 방식 요약 문자열
        String modeLabel = (isAutoMode ? "자동관수" : "개별관수")
                + " · " + (isSeqMode ? "순차 실행" : "동시 실행");
        String profileLabel = isAutoMode
                ? selectedProfileName
                : "직접 입력 · " + indivStime + "분 / 휴지 " + indivDtime + "분 / " + indivReCount + "회";

        // 행 추가 헬퍼
        for (String[] row : new String[][]{
                {"관수 방식",  modeLabel},
                {"유형·설정",  profileLabel},
                {"게이트웨이", selectedZoneName},
                {"날짜",       dateStr},
                {"시간",       timeStr},
        }) {
            android.widget.LinearLayout rowView = new android.widget.LinearLayout(this);
            rowView.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            rowView.setPadding(0, rowPad, 0, rowPad);

            android.widget.TextView label = new android.widget.TextView(this);
            label.setText(row[0]);
            label.setTextSize(15f);
            label.setTextColor(0xFF9E9E9E);
            label.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    Math.round(80 * getResources().getDisplayMetrics().density),
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            rowView.addView(label);

            android.widget.TextView value = new android.widget.TextView(this);
            value.setText(row[1]);
            value.setTextSize(18f);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            value.setTextColor(0xFF1B2E1B);
            rowView.addView(value);

            root.addView(rowView);

            // 구분선
            android.view.View d2 = new android.view.View(this);
            d2.setBackgroundColor(0xFFF0F0F0);
            d2.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * getResources().getDisplayMetrics().density)));
            root.addView(d2);
        }

        // 버튼 행
        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.END);
        btnRow.setPadding(0, Math.round(12 * getResources().getDisplayMetrics().density), 0, 0);

        androidx.appcompat.app.AlertDialog[] dlgRef = {null};

        android.widget.Button btnCancel = new android.widget.Button(this);
        btnCancel.setText("취소");
        btnCancel.setTextSize(17f);
        btnCancel.setTextColor(0xFF9E9E9E);
        btnCancel.setBackground(null);
        btnCancel.setPadding(Math.round(16*getResources().getDisplayMetrics().density), 0,
                Math.round(8*getResources().getDisplayMetrics().density), 0);
        btnCancel.setOnClickListener(v -> { if (dlgRef[0] != null) dlgRef[0].dismiss(); });
        btnRow.addView(btnCancel);

        android.widget.Button btnConfirm = new android.widget.Button(this);
        btnConfirm.setText("예약 확정");
        btnConfirm.setTextSize(17f);
        btnConfirm.setTypeface(null, android.graphics.Typeface.BOLD);
        btnConfirm.setTextColor(0xFF2C5F2D);
        btnConfirm.setBackground(null);
        btnConfirm.setPadding(Math.round(8*getResources().getDisplayMetrics().density), 0,
                Math.round(4*getResources().getDisplayMetrics().density), 0);
        btnConfirm.setOnClickListener(v -> {
            if (dlgRef[0] != null) dlgRef[0].dismiss();
            WorkRecord rec = new WorkRecord(
                    "new_" + System.currentTimeMillis(),
                    selectedDate, selectedTime, selectedTime.plusMinutes(30),
                    selectedProfileName, WorkRecord.DeviceType.PUMP,
                    selectedProfileName, selectedZoneName,
                    WorkRecord.Status.SCHEDULED, "앱에서 등록");
            rec.setIrrigationProfileId(selectedProfileId);

            // DEV_MODE: 로컬 목록에만 추가
            if (AppConfig.getInstance().isDevMode()) {
                scheduleList.add(0, rec);
                Toast.makeText(this, "예약이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                updateCalendarUI();
                return;
            }

            com.acasian.iot.storage.SessionManager session =
                    com.acasian.iot.storage.SessionManager.getInstance(this);
            com.acasian.iot.network.ApiService apiSvc =
                    com.acasian.iot.network.ApiClient.getInstance(this).getService();

            String farmId = com.acasian.iot.ZoneStore.getInstance().getFarmId();
            String yymmdd = com.acasian.iot.network.ApiDateUtil.toYymmdd(selectedDate);
            String hhnn   = com.acasian.iot.network.ApiDateUtil.toHhnn(selectedTime);
            String isSeq  = isSeqMode ? "Y" : "N";

            com.acasian.iot.network.ApiService.ScheduleAddRequest req;
            if (isAutoMode) {
                req = com.acasian.iot.network.ApiService.ScheduleAddRequest.forAuto(
                        session.getPhoneNumber(), farmId, selectedTelNo,
                        selectedProfileId, yymmdd, hhnn, isSeq);
            } else {
                req = com.acasian.iot.network.ApiService.ScheduleAddRequest.forIndividual(
                        session.getPhoneNumber(), farmId, selectedTelNo,
                        yymmdd, hhnn, isSeq,
                        indivNodeIds, indivStime, indivDtime, indivReCount);
            }

            final com.acasian.iot.Calendar.model.WorkRecord finalRec = rec;
            apiSvc.addSchedule(req).enqueue(
                new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleAddResponse>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleAddResponse> call,
                            retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleAddResponse> res) {
                        runOnUiThread(() -> {
                            if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) {
                                int schId = res.body().schId;
                                Toast.makeText(ScheduleActivity.this,
                                        "예약이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                loadSchedules();
                                if (schId > 0) genCmdAfterSchedule(String.valueOf(schId));
                            } else {
                                Toast.makeText(ScheduleActivity.this,
                                        "예약 등록 실패 (" + res.code() + ")", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    @Override
                    public void onFailure(
                            retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleAddResponse> call,
                            Throwable t) {
                        runOnUiThread(() ->
                            Toast.makeText(ScheduleActivity.this,
                                    "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
        });
        btnRow.addView(btnConfirm);
        root.addView(btnRow);

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(root).create();
        dlgRef[0] = dlg;
        dlg.show();
    }

    private void setText(View v, int id, String t) {
        TextView tv = v.findViewById(id);
        if (tv != null) tv.setText(t);
    }

    private void showRecordDetail(com.acasian.iot.Calendar.model.WorkRecord rec) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm");
        float dp = getResources().getDisplayMetrics().density;
        String[] dowKo = {"월","화","수","목","금","토","일"};
        String dow = dowKo[rec.getDate().getDayOfWeek().getValue()-1];

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        int p = Math.round(20*dp);
        root.setPadding(p, p, p, Math.round(8*dp));

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText(rec.getTaskName());
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF1B2E1B);
        tvTitle.setPadding(0,0,0,Math.round(10*dp));
        root.addView(tvTitle);

        android.view.View div = new android.view.View(this);
        div.setBackgroundColor(0xFFE0E0E0);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1*dp)));
        root.addView(div);

        String[][] rows = {
            {"날짜",    rec.getDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일")) + " ("+dow+")"},
            {"시간",    rec.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))},
            {"게이트웨이", rec.getZone() != null ? rec.getZone() : "-"},
            {"메모",    rec.getMemo() != null && !rec.getMemo().isEmpty() ? rec.getMemo() : "-"},
        };
        for (String[] row : rows) {
            android.widget.LinearLayout rowV = new android.widget.LinearLayout(this);
            rowV.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            rowV.setPadding(0, Math.round(9*dp), 0, Math.round(9*dp));
            rowV.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            android.widget.TextView tvLbl = new android.widget.TextView(this);
            tvLbl.setText(row[0]);
            tvLbl.setTextSize(13f);
            tvLbl.setTextColor(0xFF9E9E9E);
            android.widget.LinearLayout.LayoutParams lblLp = new android.widget.LinearLayout.LayoutParams(
                    Math.round(72*dp), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            tvLbl.setLayoutParams(lblLp);
            rowV.addView(tvLbl);

            android.widget.TextView tvVal = new android.widget.TextView(this);
            tvVal.setText(row[1]);
            tvVal.setTextSize(16f);
            tvVal.setTypeface(null, android.graphics.Typeface.BOLD);
            tvVal.setTextColor(0xFF1B2E1B);
            rowV.addView(tvVal);

            root.addView(rowV);

            android.view.View rowDiv = new android.view.View(this);
            rowDiv.setBackgroundColor(0xFFF5F5F5);
            rowDiv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1*dp)));
            root.addView(rowDiv);
        }

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setGravity(android.view.Gravity.END);
        btnRow.setPadding(0, Math.round(14*dp), 0, 0);

        androidx.appcompat.app.AlertDialog[] ref = {null};
        android.widget.Button btnDel = new android.widget.Button(this);
        btnDel.setText("예약 삭제");
        btnDel.setTextSize(15f);
        btnDel.setTextColor(0xFFC62828);
        btnDel.setBackground(null);
        btnDel.setPadding(Math.round(4*dp),0,Math.round(12*dp),0);
        btnDel.setOnClickListener(v -> {
            if(ref[0]!=null) ref[0].dismiss();
            confirmDelete(rec);
        });

        android.widget.Button btnClose = new android.widget.Button(this);
        btnClose.setText("닫기");
        btnClose.setTextSize(15f);
        btnClose.setTypeface(null, android.graphics.Typeface.BOLD);
        btnClose.setTextColor(0xFF2C5F2D);
        btnClose.setBackground(null);
        btnClose.setPadding(Math.round(12*dp),0,Math.round(4*dp),0);
        btnClose.setOnClickListener(v -> { if(ref[0]!=null) ref[0].dismiss(); });

        btnRow.addView(btnDel);
        btnRow.addView(btnClose);
        root.addView(btnRow);

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(root).create();
        if (dlg.getWindow() != null) {
            android.graphics.drawable.GradientDrawable wbg = new android.graphics.drawable.GradientDrawable();
            wbg.setColor(0xFFFFFFFF); wbg.setCornerRadius(16*dp);
            dlg.getWindow().setBackgroundDrawable(wbg);
        }
        ref[0] = dlg;
        dlg.show();
    }
}

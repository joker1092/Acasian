package com.acasian.iot.Calendar.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.acasian.iot.Calendar.model.WorkRecord;
import com.acasian.iot.DemoData;
import com.acasian.iot.IrrigationTypeManagerFragment;
import com.acasian.iot.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 날짜 선택 시 하단 상세 패널 관리.
 * 오늘 이전 → 내역 버전 / 미래 → 예약 버전
 */
public class DateDetailView {


    // 장치 목록 — setDeviceList()로 외부 주입, 없으면 기본 더미 사용
    private String[]               DEVICE_NAMES = {
        "관개 펌프 (A-01)",
        "관개 펌프 (B-02)",
        "환경 센서"
    };
    private WorkRecord.DeviceType[] DEVICE_TYPES = {
        WorkRecord.DeviceType.PUMP,
        WorkRecord.DeviceType.PUMP,
        WorkRecord.DeviceType.SENSOR
    };

    /**
     * 실제 노드 목록 주입 (ZoneStore → CalendarFragment → DateDetailView).
     * farminfo의 nodelist를 기반으로 드롭다운을 구성합니다.
     *
     * @param names  표시 이름 목록 ("토출 밸브 1 (A-01 구역)" 형태)
     * @param types  각 이름에 대응하는 DeviceType
     */
    public void setDeviceList(String[] names, WorkRecord.DeviceType[] types) {
        if (names != null && names.length > 0) {
            DEVICE_NAMES = names;
            DEVICE_TYPES = types != null ? types : new WorkRecord.DeviceType[names.length];
            // null 항목 기본값 처리
            for (int i = 0; i < DEVICE_TYPES.length; i++) {
                if (DEVICE_TYPES[i] == null) DEVICE_TYPES[i] = WorkRecord.DeviceType.PUMP;
            }
        }
    }

    /** 예약 삭제 시 서버 API 호출 위임 콜백 */
    public interface OnScheduleDeleteListener {
        void onScheduleDelete(String schId);
    }

    /** 예약 등록 시 서버 API 호출 위임 콜백
     *  weekFlags: String[7] = {mon,tue,wed,thu,fri,sat,sun}, 각 "Y"/"N"
     *    - isRepeat=2(주간반복)일 때만 의미. isRepeat=0 또는 1이면 null 가능. */
    public interface OnScheduleAddListener {
        void onScheduleAdd(java.time.LocalDate date, java.time.LocalTime time,
                           String profileId, String telNo, boolean isAuto, boolean isSeq,
                           String nodeIds, int stime, int dtime, int reCount,
                           int isRepeat, String[] weekFlags);
    }

    private final View             container;
    private LocalDate              currentDate;
    private MonthCalendarView      monthCalendarView;  // 달력과 records 공유용
    private YearMonth              currentMonth;
    private OnScheduleAddListener    scheduleAddListener;
    private OnScheduleDeleteListener scheduleDeleteListener;

    // 더미 데이터 저장소 (화면당 공유 → 실제는 DB로 교체)
    private final List<WorkRecord> records = new ArrayList<>();

    public DateDetailView(View container) {
        this(container, false);
    }

    public DateDetailView(View container, boolean demoMode) {
        this.container = container;
        if (demoMode) records.addAll(DemoData.buildRecords());
    }

    public void setOnScheduleAddListener(OnScheduleAddListener l) { this.scheduleAddListener = l; }
    public void setOnScheduleDeleteListener(OnScheduleDeleteListener l) { this.scheduleDeleteListener = l; }

    /** 서버에서 받은 예약 목록으로 교체 */
    public void setRecords(java.util.List<WorkRecord> serverRecords) {
        records.clear();
        if (serverRecords != null) records.addAll(serverRecords);
        syncRecordsToCalendar();
        render();
    }

    /** MonthCalendarView 주입 → records 공유, 작업 변경 시 달력 즉시 갱신 */
    /** records 리스트 반환 (WeekCalendarView 등 공유용) */
    public List<WorkRecord> getRecords() { return records; }

    public void setMonthCalendarView(MonthCalendarView view, YearMonth month) {
        this.monthCalendarView = view;
        this.currentMonth      = month;
        syncRecordsToCalendar();
    }

    private void syncRecordsToCalendar() {
        if (monthCalendarView != null) {
            monthCalendarView.setRecords(records);
            if (currentMonth != null) monthCalendarView.updateCalendar(currentMonth);
        }
    }

    // ── 달력 월 갱신 시 호출 ─────────────────────────────────────────────
    public void setCurrentMonth(YearMonth month) {
        this.currentMonth = month;
    }

    // ── 날짜 변경 시 호출 ────────────────────────────────────────────────
    public void updateDate(LocalDate date) {
        this.currentDate = date;
        render();
    }

    // ── 렌더링 ──────────────────────────────────────────────────────────
    private void render() {
        if (currentDate == null || container == null) return;

        boolean isPast = currentDate.isBefore(LocalDate.now()); // 오늘 포함 미래는 예약 가능

        // 날짜 라벨
        TextView txtDate = container.findViewById(R.id.txt_detail_date);
        if (txtDate != null) {
            txtDate.setText(String.format(Locale.getDefault(),
                    "%d월 %d일", currentDate.getMonthValue(), currentDate.getDayOfMonth()));
        }

        // 모드 뱃지
        TextView txtMode = container.findViewById(R.id.txt_detail_mode);
        if (txtMode != null) {
            txtMode.setText(isPast ? "내역" : "예약");
        }

        // FAB (예약 추가 버튼) - 미래 날짜만 표시
        FloatingActionButton fab = container.findViewById(R.id.fab_add_work);
        if (fab != null) {
            fab.setVisibility(isPast ? View.GONE : View.VISIBLE);
            fab.setOnClickListener(v -> showAddDialog());
        }

        // 카드 목록 렌더링
        LinearLayout cardContainer = container.findViewById(R.id.detail_card_container);
        View emptyView = container.findViewById(R.id.detail_empty_view);
        if (cardContainer == null) return;

        cardContainer.removeAllViews();

        List<WorkRecord> dayRecords = getRecordsForDate(currentDate);

        if (dayRecords.isEmpty()) {
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
        } else {
            if (emptyView != null) emptyView.setVisibility(View.GONE);
            for (WorkRecord record : dayRecords) {
                View cardView = buildCard(record);
                cardContainer.addView(cardView);
            }
        }
    }

    // ── 카드 뷰 생성 ────────────────────────────────────────────────────
    private View buildCard(WorkRecord record) {
        Context ctx = container.getContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View card = inflater.inflate(R.layout.item_work_card, null, false);

        // 좌측 컬러 바
        View colorBar = card.findViewById(R.id.card_color_bar);
        if (colorBar != null) colorBar.setBackgroundColor(deviceColor(record.getDeviceType()));

        // 작업명
        TextView tvTask = card.findViewById(R.id.card_task_name);
        if (tvTask != null) tvTask.setText(record.getTaskName());

        // 시간: 시작 시간만 표시 (종료 시간 제거)
        TextView tvTimeZone = card.findViewById(R.id.card_time_zone);
        if (tvTimeZone != null) {
            String startStr = record.getStartTime() != null
                    ? formatTime(record.getStartTime()) : "-";
            String zone = record.getZone();
            String zoneStr = (zone != null && !zone.isEmpty()) ? zone : "전체";
            tvTimeZone.setText(startStr + " · " + zoneStr);
        }

        // 장치명
        TextView tvDevice = card.findViewById(R.id.card_device_name);
        if (tvDevice != null) tvDevice.setText(record.getDeviceName());

        // 상태 뱃지
        TextView tvStatus = card.findViewById(R.id.card_status_badge);
        if (tvStatus != null) {
            tvStatus.setText(statusLabel(record.getStatus()));
            tvStatus.setTextColor(statusColor(record.getStatus(), ctx));
        }

        // 클릭 → 상세 Dialog
        card.setOnClickListener(v -> showDetailDialog(record));

        return card;
    }

    // ── 상세 Dialog ─────────────────────────────────────────────────────
    private void showDetailDialog(WorkRecord record) {
        Context ctx = container.getContext();
        View dlgView = LayoutInflater.from(ctx).inflate(R.layout.dialog_work_detail, null);

        // 작업명
        setTv(dlgView, R.id.dlg_task_name, record.getTaskName());

        // 날짜
        setTv(dlgView, R.id.dlg_date, String.format(Locale.getDefault(),
                "%d년 %d월 %d일",
                record.getDate().getYear(),
                record.getDate().getMonthValue(),
                record.getDate().getDayOfMonth()));

        // 시작 시간만 표시 (종료 시간 제거)
        setTv(dlgView, R.id.dlg_time,
                record.getStartTime() != null ? formatTime(record.getStartTime()) : "-");

        // IrrigationProfile 에서 값 우선 사용, 없으면 memo 파싱 fallback
        String profileName = record.getTaskName();
        String zone        = record.getZone();
        String runText     = "-";
        String restText    = "-";
        String repeatText  = "-";

        String profileId = record.getIrrigationProfileId();
        com.acasian.iot.model.IrrigationProfile profile = null;
        if (profileId != null && !profileId.isEmpty()) {
            profile = com.acasian.iot.model.IrrigationProfileManager
                    .getInstance(ctx).getById(profileId);
        }

        if (profile != null) {
            profileName = profile.getName();
            zone        = profile.getZoneName() + " " +
                    (profile.getTargetType() ==
                     com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL
                     ? "전체" : "개별 " + profile.getDeviceIds().size() + "개");
            runText    = fmtMinDetail(profile.getRunMinutes());
            restText   = profile.getRestMinutes() <= 0 ? "없음"
                        : fmtMinDetail(profile.getRestMinutes());
            repeatText = profile.getRepeatCount() + "회";
        } else {
            // profile 못 찾은 경우 - record에 저장된 데이터로 표시
            profileName = record.getTaskName();
            zone        = record.getZone() != null ? record.getZone() : "전체";
            // memo "30분·휴0분·1회" 파싱
            String memo = record.getMemo();
            if (memo != null && !memo.isEmpty()) {
                try {
                    String[] parts = memo.split("·");
                    if (parts.length >= 1) runText = parts[0].trim();
                    if (parts.length >= 2) {
                        String r = parts[1].replace("휴","").trim();
                        restText = r.equals("0분") ? "없음" : r;
                    }
                    if (parts.length >= 3) repeatText = parts[2].trim();
                } catch (Exception ignored) {}
            }
        }

        setTv(dlgView, R.id.dlg_profile_name, profileName);
        setTv(dlgView, R.id.dlg_zone,   (zone != null && !zone.isEmpty()) ? zone : "전체");
        setTv(dlgView, R.id.dlg_run,    runText);
        setTv(dlgView, R.id.dlg_rest,   restText);
        setTv(dlgView, R.id.dlg_repeat, repeatText);

        // 상태 뱃지
        TextView tvStatus = dlgView.findViewById(R.id.dlg_status_badge);
        if (tvStatus != null) {
            switch (record.getStatus()) {
                case DONE:
                    tvStatus.setText("완료");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_ok); break;
                case FAILED:
                    tvStatus.setText("실패");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_warn); break;
                case RUNNING:
                    tvStatus.setText("진행중");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_ok); break;
                case SCHEDULED: default:
                    tvStatus.setText("예약");
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_neutral); break;
            }
        }

        // 삭제 버튼: 미래 예약만 표시 (오늘 포함, 시작 시간 이전만)
        Button btnCancelRes = dlgView.findViewById(R.id.dlg_btn_cancel_reservation);
        if (btnCancelRes != null) {
            java.time.LocalDateTime schedDt = java.time.LocalDateTime.of(
                    record.getDate(), record.getStartTime());
            boolean isFuture = schedDt.isAfter(java.time.LocalDateTime.now());
            btnCancelRes.setVisibility(isFuture ? View.VISIBLE : View.GONE);
            btnCancelRes.setText("예약 삭제");
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(dlgView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dlgView.findViewById(R.id.dlg_btn_close).setOnClickListener(v -> dialog.dismiss());

        if (btnCancelRes != null) {
            btnCancelRes.setOnClickListener(v -> {
                dialog.dismiss();
                if (scheduleDeleteListener != null && record.getId() != null
                        && !record.getId().startsWith("r_")) {
                    // 서버 API 위임 → 성공 시 ScheduleActivity에서 목록 재로드
                    scheduleDeleteListener.onScheduleDelete(record.getId());
                } else {
                    // 임시 ID(미동기화) → 로컬만 삭제
                    records.remove(record);
                    syncRecordsToCalendar();
                    render();
                }
            });
        }

        dialog.show();
    }

    // ── 예약 추가 Dialog ────────────────────────────────────────────────
    /** 외부에서 날짜 지정 없이 팝업 열기 — 날짜는 팝업 내 선택 */
    public void showAddDialogWithoutDate() {
        currentDate = LocalDate.now(); // 임시값 (팝업에서 DatePicker로 변경)
        showAddDialog();
    }

    public void showAddDialog() {
        if (currentDate == null) return;
        Context ctx = container.getContext();
        View dlgView = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_work, null);

        // ── 선택된 날짜 (반복 시 null, 단건 시 currentDate 초기값) ──────
        final LocalDate[] selectedDate = {currentDate};

        android.view.View rowDate = dlgView.findViewById(R.id.add_row_date);
        TextView tvDate = dlgView.findViewById(R.id.add_txt_date);
        android.view.View btnDate = dlgView.findViewById(R.id.add_btn_date);

        // 날짜 초기값 표시
        if (tvDate != null && currentDate != null) {
            tvDate.setText(String.format(Locale.getDefault(), "%d년 %d월 %d일",
                    currentDate.getYear(), currentDate.getMonthValue(), currentDate.getDayOfMonth()));
            tvDate.setTextColor(ctx.getResources().getColor(R.color.sage, null));
        }

        // 날짜 클릭 → DatePickerDialog
        if (btnDate != null) {
            btnDate.setOnClickListener(vv -> {
                LocalDate base = selectedDate[0] != null ? selectedDate[0] : LocalDate.now();
                android.app.DatePickerDialog dpd = new android.app.DatePickerDialog(ctx,
                        (dp, y, m, d) -> {
                            LocalDate picked = LocalDate.of(y, m + 1, d);
                            selectedDate[0] = picked;
                            if (tvDate != null) {
                                tvDate.setText(String.format(Locale.getDefault(),
                                        "%d년 %d월 %d일", y, m + 1, d));
                                tvDate.setTextColor(ctx.getResources().getColor(R.color.sage, null));
                            }
                        },
                        base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth());
                // 오늘 이후만 선택 가능
                dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
                dpd.show();
            });
        }

        // ── 상태 ──────────────────────────────────────────────────────
        final LocalTime[] selectedStart = {null};
        final com.acasian.iot.model.IrrigationProfile[] selectedProfile = {null};
        final String[] selectedZoneId   = {""};
        final String[] selectedZoneName = {""};
        // 모드: 0=메인함미선택, 1=유형목록, 2=직접입력
        final int[] mode = {0};
        pendingMode[0] = 0;
        // isSeq 기본값: true=순차실행(Y)
        final boolean[] isSeq = {true};

        // ── 뷰 참조 ───────────────────────────────────────────────────
        TextView tvStart     = dlgView.findViewById(R.id.add_txt_start_time);
        TextView tvZone      = dlgView.findViewById(R.id.add_txt_zone);
        TextView tabType     = dlgView.findViewById(R.id.add_tab_type);
        TextView tabDirect   = dlgView.findViewById(R.id.add_tab_direct);
        android.widget.LinearLayout panelList   = dlgView.findViewById(R.id.add_panel_list);
        android.widget.LinearLayout panelDirect = dlgView.findViewById(R.id.add_panel_direct);
        android.widget.LinearLayout panelForm   = dlgView.findViewById(R.id.add_panel_form);
        android.widget.LinearLayout cardContainer = dlgView.findViewById(R.id.add_type_card_container);
        android.widget.LinearLayout cardNoType    = dlgView.findViewById(R.id.add_card_no_type);
        pendingSelectedCardContainer = dlgView.findViewById(R.id.add_selected_card_container);
        android.widget.EditText etDirRun    = dlgView.findViewById(R.id.add_direct_run);
        android.widget.EditText etDirRest   = dlgView.findViewById(R.id.add_direct_rest);
        android.widget.EditText etDirRepeat = dlgView.findViewById(R.id.add_direct_repeat);
        // stime/dtime 선택값 (분 단위) — 픽커 다이얼로그로 설정
        final int[] selectedRunMin  = {30};
        final int[] selectedRestMin = {0};
        android.widget.TextView dirToggleAll = dlgView.findViewById(R.id.add_direct_toggle_all);
        android.widget.TextView dirToggleInd = dlgView.findViewById(R.id.add_direct_toggle_individual);
        android.widget.LinearLayout dirDeviceTags = dlgView.findViewById(R.id.add_direct_device_tags);
        final android.view.View dirTagsScroll = dlgView.findViewById(R.id.add_direct_tags_scroll);
        android.widget.TextView dirDeviceBtn = dlgView.findViewById(R.id.add_direct_device_btn);

        // 선택된 유형 표시 뷰
        android.widget.TextView tvSelectedTypeName   = dlgView.findViewById(R.id.add_selected_type_name);
        android.widget.LinearLayout selectedTypeInfo = dlgView.findViewById(R.id.add_selected_type_info);
        android.widget.TextView tvSelectedTypeRun    = dlgView.findViewById(R.id.add_selected_type_run);
        android.widget.TextView tvSelectedTypeRest   = dlgView.findViewById(R.id.add_selected_type_rest);
        android.widget.TextView tvSelectedTypeRepeat = dlgView.findViewById(R.id.add_selected_type_repeat);
        android.widget.TextView btnChangeType        = dlgView.findViewById(R.id.add_btn_change_type);

        final java.util.List<String> dirSelectedDevIds   = new java.util.ArrayList<>();
        final java.util.Map<String,String> dirDevNameMap = new java.util.LinkedHashMap<>();

        com.acasian.iot.model.IrrigationProfileManager mgr =
                com.acasian.iot.model.IrrigationProfileManager.getInstance(ctx);

        // ── 메인함 0번째 기본 선택 ────────────────────────────────────
        com.acasian.iot.ZoneStore zs0 = com.acasian.iot.ZoneStore.getInstance();
        String[] zNames0  = zs0.getZoneNames();
        String[] zTelNos0 = zs0.getZoneTelNos();
        if (zNames0.length > 0) {
            selectedZoneId[0]   = zTelNos0[0];
            selectedZoneName[0] = zNames0[0];
            if (tvZone != null) {
                tvZone.setText(selectedZoneName[0] + "  ▼");
                tvZone.setTextColor(ctx.getResources().getColor(R.color.text_primary, null));
            }
            mode[0] = 1;
        }

        // 초기: 자동관수 탭 활성 + panelList 표시 (메인함이 있는 경우)
        if (tabType   != null) { tabType.setBackgroundResource(R.drawable.bg_btn_zone_start);
            tabType.setTextColor(ctx.getResources().getColor(R.color.white, null)); }
        if (tabDirect != null) { tabDirect.setBackgroundResource(android.R.color.transparent);
            tabDirect.setTextColor(ctx.getResources().getColor(R.color.moss, null)); }
        if (panelList   != null) panelList.setVisibility(
                zNames0.length > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        if (panelDirect != null) panelDirect.setVisibility(android.view.View.GONE);
        if (panelForm   != null) panelForm.setVisibility(android.view.View.GONE);

        // ── 선택된 유형 정보 표시 ──────────────────────────────────────
        Runnable updateSelectedTypeUI = () -> {
            com.acasian.iot.model.IrrigationProfile p = selectedProfile[0];
            if (p == null) {
                if (tvSelectedTypeName != null) {
                    tvSelectedTypeName.setText("유형 미선택");
                    tvSelectedTypeName.setTextColor(ctx.getResources().getColor(R.color.text_hint, null));
                }
                if (selectedTypeInfo != null) selectedTypeInfo.setVisibility(android.view.View.GONE);
                if (btnChangeType != null) btnChangeType.setText("유형 선택");
            } else {
                if (tvSelectedTypeName != null) {
                    tvSelectedTypeName.setText(p.getName());
                    tvSelectedTypeName.setTextColor(ctx.getResources().getColor(R.color.text_primary, null));
                }
                if (tvSelectedTypeRun    != null) tvSelectedTypeRun.setText("가동 " + p.getRunMinutes() + "분");
                if (tvSelectedTypeRest   != null) tvSelectedTypeRest.setText(
                        p.getRestMinutes() > 0 ? "휴지 " + p.getRestMinutes() + "분" : "휴지 없음");
                if (tvSelectedTypeRepeat != null) tvSelectedTypeRepeat.setText("반복 " + p.getRepeatCount() + "회");
                if (selectedTypeInfo != null) selectedTypeInfo.setVisibility(android.view.View.VISIBLE);
                if (btnChangeType != null) btnChangeType.setText("변경");
            }
        };

        // ── 유형 카드 빌드 (호환용 — 현재는 사용 안함) ─────────────────
        Runnable[] buildCardsRef = {null};
        Runnable buildCards = () -> {
            if (cardContainer == null) return;
            cardContainer.removeAllViews();
            // 선택된 메인함의 유형만 필터
            java.util.List<com.acasian.iot.model.IrrigationProfile> all = mgr.getAll();
            java.util.List<com.acasian.iot.model.IrrigationProfile> filtered = new java.util.ArrayList<>();
            for (com.acasian.iot.model.IrrigationProfile p : all) {
                if (selectedZoneId[0].equals(p.getZoneId())) filtered.add(p);
            }
            if (filtered.isEmpty()) {
                if (cardNoType != null) cardNoType.setVisibility(android.view.View.VISIBLE);
            } else {
                if (cardNoType != null) cardNoType.setVisibility(android.view.View.GONE);
                for (com.acasian.iot.model.IrrigationProfile p : filtered) {
                    View card = LayoutInflater.from(ctx)
                            .inflate(R.layout.item_irrigation_type_card_h, cardContainer, false);
                    bindProfileToCard(card, p);
                    card.setTag(p.getId());
                    card.setOnClickListener(v -> {
                        selectedProfile[0] = p;
                        for (int i = 0; i < cardContainer.getChildCount(); i++) {
                            View c = cardContainer.getChildAt(i);
                            c.setBackgroundResource(R.drawable.bg_irrigation_card);
                            setCardTextColor(c, false, ctx);
                        }
                        card.setBackgroundResource(R.drawable.bg_irrigation_card_selected);
                        setCardTextColor(card, true, ctx);
                    });
                    cardContainer.addView(card);
                }
            }
        };
        buildCardsRef[0] = buildCards;
        // 초기 메인함 기본 선택 후 UI 초기화 (자동 팝업 없음 — 카드 탭 시 열림)
        if (zNames0.length > 0) {
            updateSelectedTypeUI.run();
        }

        // ── 메인함 선택 ───────────────────────────────────────────────
        android.view.View btnZone = dlgView.findViewById(R.id.add_btn_zone);
        if (btnZone != null) btnZone.setOnClickListener(v -> {
            com.acasian.iot.ZoneStore zs = com.acasian.iot.ZoneStore.getInstance();
            String[] zNames  = zs.getZoneNames();
            String[] zTelNos = zs.getZoneTelNos();
            if (zNames.length == 0) {
                android.widget.Toast.makeText(ctx, "홈 탭에서 데이터 로드 후 시도해 주세요.",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            int currentIdx = -1;
            for (int i = 0; i < zTelNos.length; i++)
                if (zTelNos[i].equals(selectedZoneId[0])) { currentIdx = i; break; }
            final int[] picked = {currentIdx};
            new AlertDialog.Builder(ctx)
                .setTitle("메인함 선택")
                .setSingleChoiceItems(zNames, currentIdx, (d, which) -> picked[0] = which)
                .setPositiveButton("확인", (d, w) -> {
                    if (picked[0] >= 0 && picked[0] < zTelNos.length) {
                        selectedZoneId[0]   = zTelNos[picked[0]];
                        selectedZoneName[0] = zNames[picked[0]];
                        if (tvZone != null) {
                            tvZone.setText(selectedZoneName[0] + "  ▼");
                            tvZone.setTextColor(ctx.getResources().getColor(R.color.text_primary, null));
                        }
                        // 메인함 선택 완료 → 자동관수 탭 활성화 (유형 선택창 자동오픈 없음)
                        selectedProfile[0] = null;
                        mode[0] = 1;
                        // 메인함 변경 시 밸브 선택 초기화
                        dirSelectedDevIds.clear();
                        dirDevNameMap.clear();
                        if (dirDeviceTags  != null) dirDeviceTags.removeAllViews();
                        if (dirTagsScroll  != null) dirTagsScroll.setVisibility(android.view.View.GONE);
                        if (dirDeviceBtn   != null) dirDeviceBtn.setText("밸브 선택  ▼");
                        if (panelDirect != null) panelDirect.setVisibility(android.view.View.GONE);
                        if (panelList   != null) panelList.setVisibility(android.view.View.VISIBLE);
                        if (tabType != null) { tabType.setBackgroundResource(R.drawable.bg_btn_zone_start);
                            tabType.setTextColor(ctx.getResources().getColor(R.color.white, null)); }
                        if (tabDirect != null) { tabDirect.setBackgroundResource(android.R.color.transparent);
                            tabDirect.setTextColor(ctx.getResources().getColor(R.color.moss, null)); }
                        updateSelectedTypeUI.run();
                    }
                })
                .setNegativeButton("취소", null).show();
        });

        // ── 탭 전환 ───────────────────────────────────────────────────
        if (tabType != null) tabType.setOnClickListener(v -> {
            if (selectedZoneId[0].isEmpty()) {
                android.widget.Toast.makeText(ctx, "메인함을 먼저 선택해 주세요.",
                        android.widget.Toast.LENGTH_SHORT).show(); return;
            }
            mode[0] = 1;
            tabType.setBackgroundResource(R.drawable.bg_btn_zone_start);
            tabType.setTextColor(ctx.getResources().getColor(R.color.white, null));
            if (tabDirect != null) { tabDirect.setBackgroundResource(android.R.color.transparent);
                tabDirect.setTextColor(ctx.getResources().getColor(R.color.moss, null)); }
            if (panelList   != null) panelList.setVisibility(android.view.View.VISIBLE);
            if (panelDirect != null) panelDirect.setVisibility(android.view.View.GONE);
            updateSelectedTypeUI.run();
        });
        if (tabDirect != null) tabDirect.setOnClickListener(v -> {
            mode[0] = 2;
            tabDirect.setBackgroundResource(R.drawable.bg_btn_zone_start);
            tabDirect.setTextColor(ctx.getResources().getColor(R.color.white, null));
            if (tabType != null) { tabType.setBackgroundResource(android.R.color.transparent);
                tabType.setTextColor(ctx.getResources().getColor(R.color.moss, null)); }
            if (panelDirect != null) panelDirect.setVisibility(android.view.View.VISIBLE);
            if (panelList   != null) panelList.setVisibility(android.view.View.GONE);
            selectedProfile[0] = null;
        });

        // ── 유형 선택/변경 버튼 + 패널 탭 ─────────────────────────────
        Runnable openTypePickerAction = () -> {
            if (selectedZoneId[0].isEmpty()) {
                android.widget.Toast.makeText(ctx, "메인함을 먼저 선택해 주세요.",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            openTypePicker(ctx, selectedZoneId[0], selectedZoneName[0], selectedProfile,
                    updateSelectedTypeUI);
        };
        if (btnChangeType != null) btnChangeType.setOnClickListener(v -> openTypePickerAction.run());
        if (cardNoType    != null) cardNoType.setOnClickListener(v -> openTypePickerAction.run());
        // 패널 전체 탭 → 유형 선택 창 (선택 전 빈 상태에서 카드 누를 때)
        android.widget.LinearLayout panelListClick = dlgView.findViewById(R.id.add_panel_list);
        if (panelListClick != null) {
            panelListClick.setClickable(true);
            panelListClick.setFocusable(true);
            panelListClick.setOnClickListener(v -> {
                // 유형 미선택 상태에서만 탭으로 열기 (선택됐으면 변경 버튼 사용)
                if (selectedProfile[0] == null) openTypePickerAction.run();
            });
        }

        // ── 시작 시간 ─────────────────────────────────────────────────
        android.view.View btnStart = dlgView.findViewById(R.id.add_btn_start_time);
        if (btnStart != null) btnStart.setOnClickListener(v -> {
            LocalTime init = LocalTime.now().plusMinutes(5); // 기본값: 현재 시각 +5분
            showTimePicker(ctx, "시작 시간 선택", init.getHour(), init.getMinute(), (h, m) -> {
                LocalTime picked = LocalTime.of(h, m);
                // 반복 모드(매주/매일)인지 확인 — 반복이면 과거 시각 가드 건너뜀
                com.google.android.material.switchmaterial.SwitchMaterial swRepeatChk =
                        dlgView.findViewById(R.id.switchRepeat);
                boolean isRepeatMode = (swRepeatChk != null && swRepeatChk.isChecked());
                // 단건 + 선택 날짜가 "오늘"일 때만 현재 시각 이후인지 검증
                // (반복은 매주/매일 그 시각에 실행되므로 과거 시각 가드 불필요)
                // (단건 + 미래 날짜도 가드 불필요)
                LocalDate targetDate = selectedDate[0] != null ? selectedDate[0] : currentDate;
                if (!isRepeatMode
                        && targetDate != null
                        && targetDate.isEqual(LocalDate.now())
                        && !picked.isAfter(LocalTime.now())) {
                    android.widget.Toast.makeText(ctx,
                            "현재 시각 이후로 선택해 주세요.", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedStart[0] = picked;
                tvStart.setText(formatTime(selectedStart[0]));
                tvStart.setTextColor(ctx.getResources().getColor(R.color.text_primary, null));
            });
        });

        // ── isSeq 버튼 헬퍼 ──────────────────────────────────────────
        Runnable[] applySeqRef = {null};
        // 자동관수 패널 isSeq
        android.widget.TextView adjustSeqSim = dlgView.findViewById(R.id.add_adjust_seq_sim);
        android.widget.TextView adjustSeqOrd = dlgView.findViewById(R.id.add_adjust_seq_ord);
        // 개별관수 패널 isSeq
        android.widget.TextView directSeqSim = dlgView.findViewById(R.id.add_direct_seq_sim);
        android.widget.TextView directSeqOrd = dlgView.findViewById(R.id.add_direct_seq_ord);

        Runnable applySeq = () -> {
            // 자동관수 패널
            if (adjustSeqSim != null) {
                adjustSeqSim.setBackgroundResource(isSeq[0]
                        ? android.R.color.transparent : R.drawable.bg_btn_zone_start);
                adjustSeqSim.setTextColor(ctx.getResources().getColor(
                        isSeq[0] ? R.color.moss : R.color.white, null));
            }
            if (adjustSeqOrd != null) {
                adjustSeqOrd.setBackgroundResource(isSeq[0]
                        ? R.drawable.bg_btn_zone_start : android.R.color.transparent);
                adjustSeqOrd.setTextColor(ctx.getResources().getColor(
                        isSeq[0] ? R.color.white : R.color.moss, null));
            }
            // 개별관수 패널
            if (directSeqSim != null) {
                directSeqSim.setBackgroundResource(isSeq[0]
                        ? android.R.color.transparent : R.drawable.bg_btn_zone_start);
                directSeqSim.setTextColor(ctx.getResources().getColor(
                        isSeq[0] ? R.color.moss : R.color.white, null));
            }
            if (directSeqOrd != null) {
                directSeqOrd.setBackgroundResource(isSeq[0]
                        ? R.drawable.bg_btn_zone_start : android.R.color.transparent);
                directSeqOrd.setTextColor(ctx.getResources().getColor(
                        isSeq[0] ? R.color.white : R.color.moss, null));
            }
        };
        applySeqRef[0] = applySeq;
        applySeq.run(); // 초기 상태 적용

        if (adjustSeqSim != null) adjustSeqSim.setOnClickListener(v -> { isSeq[0] = false; applySeq.run(); });
        if (adjustSeqOrd != null) adjustSeqOrd.setOnClickListener(v -> { isSeq[0] = true;  applySeq.run(); });
        if (directSeqSim != null) directSeqSim.setOnClickListener(v -> { isSeq[0] = false; applySeq.run(); });
        if (directSeqOrd != null) directSeqOrd.setOnClickListener(v -> { isSeq[0] = true;  applySeq.run(); });

        // ── 직접 입력 제어 대상 ───────────────────────────────────────
        final java.util.List<String> dirTargetType_holder = new java.util.ArrayList<>(); // unused placeholder
        final com.acasian.iot.model.IrrigationProfile.TargetType[] dirTargetType =
                {com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL};
        Runnable dirApplyAll = () -> {
            dirTargetType[0] = com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL;
            dirSelectedDevIds.clear();
            if (dirToggleAll != null) { dirToggleAll.setBackgroundResource(R.drawable.bg_btn_zone_start);
                dirToggleAll.setTextColor(ctx.getResources().getColor(R.color.white, null)); }
            if (dirToggleInd != null) { dirToggleInd.setBackgroundResource(android.R.color.transparent);
                dirToggleInd.setTextColor(ctx.getResources().getColor(R.color.moss, null)); }
            if (dirTagsScroll != null) dirTagsScroll.setVisibility(android.view.View.GONE);
            if (dirDeviceBtn  != null) dirDeviceBtn.setVisibility(android.view.View.GONE);
        };
        Runnable dirApplyInd = () -> {
            dirTargetType[0] = com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_INDIVIDUAL;
            if (dirToggleInd != null) { dirToggleInd.setBackgroundResource(R.drawable.bg_btn_zone_start);
                dirToggleInd.setTextColor(ctx.getResources().getColor(R.color.white, null)); }
            if (dirToggleAll != null) { dirToggleAll.setBackgroundResource(android.R.color.transparent);
                dirToggleAll.setTextColor(ctx.getResources().getColor(R.color.moss, null)); }
            if (dirTagsScroll != null) dirTagsScroll.setVisibility(android.view.View.VISIBLE);
            if (dirDeviceBtn  != null) dirDeviceBtn.setVisibility(android.view.View.VISIBLE);
        };
        if (dirToggleAll != null) dirToggleAll.setOnClickListener(v -> dirApplyAll.run());
        if (dirToggleInd != null) dirToggleInd.setOnClickListener(v -> dirApplyInd.run());
        // ── 가동 시간 픽커 (dialog_valve_timer 재활용) ───────────────
        if (etDirRun != null) {
            etDirRun.setFocusable(false);
            etDirRun.setClickable(true);
            etDirRun.setOnClickListener(v ->
                showDurationPicker(ctx, "가동 시간 설정", selectedRunMin[0], minutes -> {
                    selectedRunMin[0] = minutes;
                    etDirRun.setText(String.valueOf(minutes));
                }));
        }
        // ── 휴지 시간 픽커 ──────────────────────────────────────────
        if (etDirRest != null) {
            etDirRest.setFocusable(false);
            etDirRest.setClickable(true);
            etDirRest.setOnClickListener(v ->
                showDurationPicker(ctx, "휴지 시간 설정", selectedRestMin[0], minutes -> {
                    selectedRestMin[0] = minutes;
                    etDirRest.setText(String.valueOf(minutes));
                }));
        }

        if (dirDeviceBtn != null) dirDeviceBtn.setOnClickListener(v -> {
            // 개별관수는 공통 메인함 사용
            String[] nodeNames = !selectedZoneId[0].isEmpty()
                    ? com.acasian.iot.ZoneStore.getInstance().getNodeNames(selectedZoneId[0])
                    : (DEVICE_NAMES != null ? DEVICE_NAMES : new String[0]);
            String[] nodeIds = !selectedZoneId[0].isEmpty()
                    ? com.acasian.iot.ZoneStore.getInstance().getNodeIds(selectedZoneId[0])
                    : new String[0];
            if (nodeNames.length == 0) {
                android.widget.Toast.makeText(ctx, "메인함을 먼저 선택해 주세요.",
                        android.widget.Toast.LENGTH_SHORT).show(); return;
            }
            boolean[] checked = new boolean[nodeNames.length];
            for (int i = 0; i < nodeIds.length; i++)
                checked[i] = dirSelectedDevIds.contains(nodeIds[i]);
            final String[] finalNodeIds = nodeIds;
            new AlertDialog.Builder(ctx)
                .setTitle("장치 선택")
                .setMultiChoiceItems(nodeNames, checked, (d, which, isChecked) -> {
                    String nid  = which < finalNodeIds.length  ? finalNodeIds[which]  : String.valueOf(which);
                    String name = which < nodeNames.length     ? nodeNames[which]      : nid;
                    if (isChecked) {
                        if (!dirSelectedDevIds.contains(nid)) dirSelectedDevIds.add(nid);
                        dirDevNameMap.put(nid, name);
                    } else {
                        dirSelectedDevIds.remove(nid);
                        dirDevNameMap.remove(nid);
                    }
                })
                .setPositiveButton("확인", (d, w) -> {
                    refreshDeviceTagsNamed(ctx, dirSelectedDevIds, dirDevNameMap, dirDeviceTags);
                    if (dirTagsScroll != null)
                        dirTagsScroll.setVisibility(
                            dirSelectedDevIds.isEmpty()
                            ? android.view.View.GONE : android.view.View.VISIBLE);
                    if (dirDeviceBtn != null)
                        dirDeviceBtn.setText(dirSelectedDevIds.isEmpty()
                            ? "밸브 선택  ▼"
                            : dirSelectedDevIds.size() + "개 선택됨  ▼");
                })
                .setNegativeButton("취소", null).show();
        });

        // ── 폼 패널 토글/장치 ─────────────────────────────────────────
        android.widget.TextView formToggleAll = dlgView.findViewById(R.id.add_form_toggle_all);
        android.widget.TextView formToggleInd = dlgView.findViewById(R.id.add_form_toggle_individual);
        android.widget.LinearLayout formDeviceTags = dlgView.findViewById(R.id.add_form_device_tags);
        android.widget.TextView formDeviceBtn = dlgView.findViewById(R.id.add_form_device_btn);
        final java.util.List<String> formSelectedDevIds = new java.util.ArrayList<>();
        final com.acasian.iot.model.IrrigationProfile.TargetType[] formTargetType =
                {com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL};
        Runnable formApplyAll = () -> {
            formTargetType[0] = com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL;
            formSelectedDevIds.clear();
            if (formToggleAll != null) { formToggleAll.setBackgroundResource(R.drawable.bg_btn_zone_start);
                formToggleAll.setTextColor(ctx.getResources().getColor(R.color.white, null)); }
            if (formToggleInd != null) { formToggleInd.setBackgroundResource(android.R.color.transparent);
                formToggleInd.setTextColor(ctx.getResources().getColor(R.color.text_primary, null)); }
            if (formDeviceTags != null) formDeviceTags.setVisibility(android.view.View.GONE);
            if (formDeviceBtn  != null) formDeviceBtn.setVisibility(android.view.View.GONE);
        };
        Runnable formApplyInd = () -> {
            formTargetType[0] = com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_INDIVIDUAL;
            if (formToggleInd != null) { formToggleInd.setBackgroundResource(R.drawable.bg_btn_zone_start);
                formToggleInd.setTextColor(ctx.getResources().getColor(R.color.white, null)); }
            if (formToggleAll != null) { formToggleAll.setBackgroundResource(android.R.color.transparent);
                formToggleAll.setTextColor(ctx.getResources().getColor(R.color.text_primary, null)); }
            if (formDeviceTags != null) formDeviceTags.setVisibility(android.view.View.VISIBLE);
            if (formDeviceBtn  != null) formDeviceBtn.setVisibility(android.view.View.VISIBLE);
        };
        if (formToggleAll != null) formToggleAll.setOnClickListener(v -> formApplyAll.run());
        if (formToggleInd != null) formToggleInd.setOnClickListener(v -> formApplyInd.run());
        if (formDeviceBtn != null) formDeviceBtn.setOnClickListener(v ->
            showDevicePicker(ctx, formSelectedDevIds, formDeviceTags, null));

        android.widget.EditText etFormName = dlgView.findViewById(R.id.add_form_name);
        android.widget.EditText etFormRun  = dlgView.findViewById(R.id.add_form_run);
        android.widget.EditText etFormRest = dlgView.findViewById(R.id.add_form_rest);
        android.widget.EditText etFormRep  = dlgView.findViewById(R.id.add_form_repeat);
        android.view.View btnFormSave = dlgView.findViewById(R.id.add_form_save);
        if (btnFormSave != null) btnFormSave.setOnClickListener(v -> {
            String name = etFormName != null ? etFormName.getText().toString().trim() : "";
            if (name.isEmpty()) { android.widget.Toast.makeText(ctx, "유형 이름을 입력해 주세요.",
                    android.widget.Toast.LENGTH_SHORT).show(); return; }
            int run = 30, rest = 0, rep = 1;
            try { run  = Math.max(1, Integer.parseInt(etFormRun.getText().toString())); } catch (Exception e) {}
            try { rest = Math.max(0, Integer.parseInt(etFormRest.getText().toString())); } catch (Exception e) {}
            try { rep  = Math.max(1, Integer.parseInt(etFormRep.getText().toString())); } catch (Exception e) {}
            com.acasian.iot.model.IrrigationProfile np = new com.acasian.iot.model.IrrigationProfile();
            np.setName(name);
            np.setTargetType(formTargetType[0]);
            np.setZoneId(selectedZoneId[0]);
            np.setZoneName(selectedZoneName[0]);
            np.setDeviceIds(formTargetType[0] == com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_INDIVIDUAL
                    ? new java.util.ArrayList<>(formSelectedDevIds) : new java.util.ArrayList<>());
            np.setRunMinutes(run); np.setRestMinutes(rest); np.setRepeatCount(rep);
            mgr.save(np);
            buildCards.run();
            if (panelForm != null) panelForm.setVisibility(android.view.View.GONE);
            if (panelList != null) panelList.setVisibility(android.view.View.VISIBLE);
            mode[0] = 1;
            android.widget.Toast.makeText(ctx, "'" + name + "' 유형이 저장되었습니다.",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

        currentAddDialog = new AlertDialog.Builder(ctx).setView(dlgView).create();
        AlertDialog addDialog = currentAddDialog;
        if (addDialog.getWindow() != null)
            addDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // ── switchRepeat 토글 → 날짜 행 숨김/표시 + 요일 칩 박스 ────
        com.google.android.material.switchmaterial.SwitchMaterial swRepeatInit =
                dlgView.findViewById(R.id.switchRepeat);
        android.view.View rowDateRef = dlgView.findViewById(R.id.add_row_date);
        android.view.View rowWeekdaysRef = dlgView.findViewById(R.id.add_row_weekdays);
        TextView tvRepeatDescRef = dlgView.findViewById(R.id.tvRepeatDesc);

        // 요일 칩 7개 (mon~sun 순) + 선택 상태 보관 배열 ("Y"/"N")
        TextView[] chips = new TextView[]{
                dlgView.findViewById(R.id.chip_mon),
                dlgView.findViewById(R.id.chip_tue),
                dlgView.findViewById(R.id.chip_wed),
                dlgView.findViewById(R.id.chip_thu),
                dlgView.findViewById(R.id.chip_fri),
                dlgView.findViewById(R.id.chip_sat),
                dlgView.findViewById(R.id.chip_sun)
        };
        final String[] weekFlags = {"Y","Y","Y","Y","Y","Y","Y"}; // 초기: 전체 ON (매일 반복)
        TextView tvWeekSummary = dlgView.findViewById(R.id.tv_weekday_summary);

        // 칩 시각 갱신 헬퍼
        Runnable refreshChips = () -> {
            for (int i = 0; i < 7; i++) {
                if (chips[i] == null) continue;
                if ("Y".equals(weekFlags[i])) {
                    chips[i].setBackgroundResource(R.drawable.bg_btn_zone_start);
                    chips[i].setTextColor(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.white));
                } else {
                    chips[i].setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    chips[i].setTextColor(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.mist));
                }
            }
            // 요약 갱신
            if (tvWeekSummary != null) {
                int sum = 0;
                for (String v : weekFlags) if ("Y".equals(v)) sum++;
                if (sum == 0) {
                    tvWeekSummary.setText("선택된 요일이 없습니다");
                    tvWeekSummary.setTextColor(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.gold));
                } else if (sum >= 7) {
                    tvWeekSummary.setText("매일 반복");
                    tvWeekSummary.setTextColor(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.gold));
                } else {
                    StringBuilder sb = new StringBuilder("매주 ");
                    String[] labels = {"월","화","수","목","금","토","일"};
                    boolean first = true;
                    for (int i = 0; i < 7; i++) {
                        if ("Y".equals(weekFlags[i])) {
                            if (!first) sb.append("·");
                            sb.append(labels[i]);
                            first = false;
                        }
                    }
                    sb.append(" 반복");
                    tvWeekSummary.setText(sb.toString());
                    tvWeekSummary.setTextColor(
                            androidx.core.content.ContextCompat.getColor(ctx, R.color.gold));
                }
            }
        };

        // 칩 클릭 → 토글 ("Y" ↔ "N")
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            if (chips[idx] != null) {
                chips[idx].setOnClickListener(v -> {
                    weekFlags[idx] = "Y".equals(weekFlags[idx]) ? "N" : "Y";
                    refreshChips.run();
                });
            }
        }
        // 최초 시각 적용
        refreshChips.run();

        if (swRepeatInit != null) {
            swRepeatInit.setOnCheckedChangeListener((btn, checked) -> {
                // 반복 ON → 날짜 행 숨김 + 요일 칩 박스 표시
                // 반복 OFF → 날짜 행 표시 + 요일 칩 박스 숨김
                if (rowDateRef != null)
                    rowDateRef.setVisibility(checked
                            ? android.view.View.GONE : android.view.View.VISIBLE);
                if (rowWeekdaysRef != null)
                    rowWeekdaysRef.setVisibility(checked
                            ? android.view.View.VISIBLE : android.view.View.GONE);
                if (tvRepeatDescRef != null)
                    tvRepeatDescRef.setText(checked
                            ? "매주 반복 — 선택 요일마다 실행"
                            : "반복 안 함 — 단건 예약");
                // 세그먼트 토글 시각 갱신 (왼쪽=반복ON, 오른쪽=단건OFF)
                applyRepeatSegmentStyle(dlgView, checked);
                // 반복 ON 진입 시 칩 전체 ON으로 리셋
                if (checked) {
                    for (int i = 0; i < 7; i++) weekFlags[i] = "Y";
                    refreshChips.run();
                }
            });
        }

        // ── 상단 세그먼트 토글 [매주 반복 | 단건] ────────────────────
        TextView segOn  = dlgView.findViewById(R.id.seg_repeat_on);
        TextView segOff = dlgView.findViewById(R.id.seg_repeat_off);
        if (segOn != null) segOn.setOnClickListener(v -> {
            if (swRepeatInit != null && !swRepeatInit.isChecked()) swRepeatInit.setChecked(true);
            else applyRepeatSegmentStyle(dlgView, true);
        });
        if (segOff != null) segOff.setOnClickListener(v -> {
            if (swRepeatInit != null && swRepeatInit.isChecked()) swRepeatInit.setChecked(false);
            else applyRepeatSegmentStyle(dlgView, false);
        });
        // 초기 표시 (기본: 단건 OFF)
        applyRepeatSegmentStyle(dlgView, swRepeatInit != null && swRepeatInit.isChecked());

        dlgView.findViewById(R.id.add_btn_cancel).setOnClickListener(v -> {
            pendingSelectedCardContainer = null;
            if (mode[0] == 3) { mode[0] = 1;
                if (panelForm != null) panelForm.setVisibility(android.view.View.GONE);
                if (panelList != null) panelList.setVisibility(android.view.View.VISIBLE);
            } else addDialog.dismiss();
        });

        dlgView.findViewById(R.id.add_btn_confirm).setOnClickListener(v -> {
            if (selectedStart[0] == null) {
                android.widget.Toast.makeText(ctx, "시작 시간을 선택해 주세요.",
                        android.widget.Toast.LENGTH_SHORT).show(); return;
            }
            int run, rest, rep;
            String taskName, zone;
            // BottomSheet 콜백으로 설정된 mode 반영
            if (pendingMode[0] == 1 && mode[0] != 2) mode[0] = 1;

            if (mode[0] == 1 && selectedProfile[0] != null) {
                run      = selectedProfile[0].getRunMinutes();
                rest     = selectedProfile[0].getRestMinutes();
                rep      = selectedProfile[0].getRepeatCount();
                taskName = selectedProfile[0].getName();
                zone     = selectedProfile[0].getZoneName() + " " +
                           (selectedProfile[0].getTargetType() ==
                            com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL
                            ? "전체" : "개별 " + selectedProfile[0].getDeviceIds().size() + "개");
            } else if (mode[0] == 2) {
                run  = selectedRunMin[0];
                rest = selectedRestMin[0];
                try { rep  = Math.max(1, Integer.parseInt(etDirRepeat.getText().toString())); } catch (Exception e) { rep  = 1; }
                taskName = "임의 유형";
                zone = selectedZoneName[0].isEmpty() ? "미지정" :
                       selectedZoneName[0] + " 개별 " + dirSelectedDevIds.size() + "개";
            } else {
                android.widget.Toast.makeText(ctx,
                    mode[0] == 1 ? "관수 유형을 선택해 주세요." : "메인함을 선택해 주세요.",
                    android.widget.Toast.LENGTH_SHORT).show(); return;
            }

            int totalMin = run * rep + rest * Math.max(0, rep - 1);
            LocalTime endTime = selectedStart[0].plusMinutes(Math.max(totalMin, 1));
            String seqLabel = isSeq[0] ? "·순차" : "·동시";
            String summary = run + "분·휴" + rest + "분·" + rep + "회" + seqLabel;

            WorkRecord newRecord = new WorkRecord(
                    "r_" + System.currentTimeMillis(),
                    currentDate, selectedStart[0], endTime,
                    taskName, WorkRecord.DeviceType.PUMP, taskName, zone,
                    WorkRecord.Status.SCHEDULED, summary);
            if (selectedProfile[0] != null)
                newRecord.setIrrigationProfileId(selectedProfile[0].getId());

            // 반복 설정 읽기 + 주간반복 처리 (v1.9)
            com.google.android.material.switchmaterial.SwitchMaterial swRepeat =
                    dlgView.findViewById(R.id.switchRepeat);
            boolean repeatOn = (swRepeat != null && swRepeat.isChecked());
            int isRepeat = 0;
            String[] weekFlagsForSend = null;
            if (repeatOn) {
                int sum = 0;
                for (String wf : weekFlags) if ("Y".equals(wf)) sum++;
                if (sum == 0) {
                    // 가드: 0개 선택 시 등록 차단
                    android.widget.Toast.makeText(ctx,
                            "선택된 요일이 없습니다. 최소 1개 요일을 선택해 주세요.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (sum >= 7) {
                    // 전체 선택 → 매일 반복 (v1.9: isRepeat=1, mon~sun="N")
                    isRepeat = 1;
                    weekFlagsForSend = null;
                } else {
                    // 일부 선택 → 주간 반복 (v1.9: isRepeat=2, mon~sun "Y"/"N" 지정)
                    isRepeat = 2;
                    weekFlagsForSend = weekFlags.clone();
                }
                selectedDate[0] = null; // 반복이면 selectedDate 무시
            }

            // 서버 API 호출 위임
            if (scheduleAddListener != null) {
                String nodeIds = mode[0] == 2
                        ? String.join(",", dirSelectedDevIds) : null;
                scheduleAddListener.onScheduleAdd(
                        repeatOn ? currentDate : (selectedDate[0] != null ? selectedDate[0] : currentDate),
                        selectedStart[0],
                        selectedProfile[0] != null ? selectedProfile[0].getId() : null,
                        selectedZoneId[0],
                        mode[0] == 1,
                        isSeq[0],
                        nodeIds,
                        selectedRunMin[0],
                        selectedRestMin[0],
                        mode[0] == 2 ? Math.max(1, tryParseInt(etDirRepeat, 1)) : 1,
                        isRepeat, weekFlagsForSend);
            }
            records.add(newRecord);
            addDialog.dismiss();
            syncRecordsToCalendar();
            render();
        });

        addDialog.show();
    }


    /** 관수 유형 선택 Dialog 열기 */
    private void openTypePicker(Context ctx,
                                 String zoneId, String zoneName,
                                 com.acasian.iot.model.IrrigationProfile[] selectedProfile) {
        openTypePicker(ctx, zoneId, zoneName, selectedProfile, null);
    }

    private void openTypePicker(Context ctx,
                                 String zoneId, String zoneName,
                                 com.acasian.iot.model.IrrigationProfile[] selectedProfile,
                                 Runnable onDone) {
        if (!(ctx instanceof androidx.fragment.app.FragmentActivity)) return;
        androidx.fragment.app.FragmentActivity act =
                (androidx.fragment.app.FragmentActivity) ctx;
        com.acasian.iot.IrrigationTypePickerSheet sheet =
                com.acasian.iot.IrrigationTypePickerSheet.newInstance(zoneName, zoneId);

        // 유형 선택 완료
        sheet.setOnProfileSelectedListener(profile -> {
            selectedProfile[0] = profile;
            pendingMode[0] = 1;
            if (onDone != null) onDone.run();
        });

        sheet.show(act.getSupportFragmentManager(), "type_picker");
    }

    // 현재 열린 예약 다이얼로그의 뷰 참조 (BottomSheet 콜백에서 접근용)
    private android.widget.LinearLayout pendingSelectedCardContainer;
    private AlertDialog currentAddDialog;
    private final int[] pendingMode = {0};  // BottomSheet 콜백에서 mode 업데이트용

    /** 선택된 유형 카드를 예약 다이얼로그에 표시 */
    private void updateSelectedCard(Context ctx,
                                     com.acasian.iot.model.IrrigationProfile profile,
                                     android.widget.LinearLayout container) {
        if (container == null) return;
        container.removeAllViews();
        View card = LayoutInflater.from(ctx)
                .inflate(R.layout.item_irrigation_type_card, container, false);
        View btnE = card.findViewById(R.id.btnEditType);
        View btnD = card.findViewById(R.id.btnDeleteType);
        if (btnE != null) btnE.setVisibility(View.GONE);
        if (btnD != null) btnD.setVisibility(View.GONE);
        bindProfileToCard(card, profile);
        card.setBackgroundResource(R.drawable.bg_irrigation_card_selected);
        setCardTextColor(card, true, ctx);
        container.addView(card);
        container.setVisibility(View.VISIBLE);
    }

    /** 카드 뷰에 프로필 데이터 바인딩 (공용) */
    private void bindProfileToCard(View card, com.acasian.iot.model.IrrigationProfile p) {
        android.widget.TextView tvName  = card.findViewById(R.id.tvTypeName);
        android.widget.TextView tvRun   = card.findViewById(R.id.tvRunMinutes);
        android.widget.TextView tvRest  = card.findViewById(R.id.tvRestMinutes);
        android.widget.TextView tvRpt   = card.findViewById(R.id.tvRepeatCount);
        android.widget.TextView tvBadge = card.findViewById(R.id.tvTargetBadge);
        if (tvName != null) tvName.setText(p.getName());
        if (tvRun  != null) tvRun.setText(fmtMin(p.getRunMinutes()));
        if (tvRest != null) tvRest.setText(p.getRestMinutes() <= 0 ? "없음" : fmtMin(p.getRestMinutes()));
        if (tvRpt  != null) tvRpt.setText(p.getRepeatCount() + "회");
        if (tvBadge != null) {
            String zn = p.getZoneName() != null ? p.getZoneName() : "";
            if (p.getTargetType() == com.acasian.iot.model.IrrigationProfile.TargetType.ZONE_ALL) {
                tvBadge.setText(zn + " 전체");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_ok);
            } else {
                tvBadge.setText(zn + " 노드 " + p.getDeviceIds().size() + "개");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
            }
        }
    }

    /** 카드 내 텍스트 색상 전환 (선택=흰색, 비선택=원색) */
    private void setCardTextColor(View card, boolean selected, android.content.Context ctx) {
        // 선택 배경(#EAF5EA 연초록) → 진한 초록 텍스트, 미선택 → 기본 색상
        int nameColor  = selected ? ctx.getResources().getColor(R.color.forest_dark, null)
                                  : ctx.getResources().getColor(R.color.text_primary, null);
        int valueColor = ctx.getResources().getColor(R.color.forest_dark, null);
        int labelColor = ctx.getResources().getColor(R.color.text_hint, null);
        android.widget.TextView tvName   = card.findViewById(R.id.tvTypeName);
        android.widget.TextView tvRun    = card.findViewById(R.id.tvRunMinutes);
        android.widget.TextView tvRest   = card.findViewById(R.id.tvRestMinutes);
        android.widget.TextView tvRepeat = card.findViewById(R.id.tvRepeatCount);
        if (tvName   != null) tvName.setTextColor(nameColor);
        if (tvRun    != null) tvRun.setTextColor(valueColor);
        if (tvRest   != null) tvRest.setTextColor(valueColor);
        if (tvRepeat != null) tvRepeat.setTextColor(valueColor);
        findLabelViews(card, new String[]{"가동","휴지","반복"}, labelColor);
    }

    private void findLabelViews(View root, String[] texts, int color) {
        if (root instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) root;
            for (String t : texts) {
                if (t.equals(tv.getText() != null ? tv.getText().toString() : "")) {
                    tv.setTextColor(color); break;
                }
            }
        } else if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++)
                findLabelViews(vg.getChildAt(i), texts, color);
        }
    }

    private String fmtMinDetail(int min) {
        if (min <= 0)      return "없음";
        if (min < 60)      return min + "분";
        if (min % 60 == 0) return (min / 60) + "시간";
        return (min / 60) + "시간 " + (min % 60) + "분";
    }

    private String fmtMin(int min) {
        if (min <= 0)      return "없음";
        if (min < 60)      return min + "분";
        if (min % 60 == 0) return (min / 60) + "시간";
        return (min / 60) + "시간 " + (min % 60) + "분";
    }

    /**
     * 장치 선택 다이얼로그 + 태그 컨테이너 갱신.
     */
    private void showDevicePicker(Context ctx,
                                   java.util.List<String> selectedIds,
                                   android.widget.LinearLayout tagContainer,
                                   Runnable onChanged) {
        if (DEVICE_NAMES == null || DEVICE_NAMES.length == 0) {
            android.widget.Toast.makeText(ctx, "장치 정보를 불러올 수 없습니다.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        boolean[] checked = new boolean[DEVICE_NAMES.length];
        for (int i = 0; i < DEVICE_NAMES.length; i++)
            checked[i] = selectedIds.contains(String.valueOf(i));
        new AlertDialog.Builder(ctx)
            .setTitle("장치 선택")
            .setMultiChoiceItems(DEVICE_NAMES, checked, (d, which, isChecked) -> {
                String id = String.valueOf(which);
                if (isChecked) { if (!selectedIds.contains(id)) selectedIds.add(id); }
                else selectedIds.remove(id);
            })
            .setPositiveButton("확인", (d, w) -> {
                refreshDeviceTags(ctx, selectedIds, tagContainer);
                if (onChanged != null) onChanged.run();
            })
            .setNegativeButton("취소", null)
            .show();
    }

    /** 이름 맵을 사용한 태그 갱신 — nodeId→name 직접 표시 */
    private void refreshDeviceTagsNamed(Context ctx,
                                         java.util.List<String> selectedIds,
                                         java.util.Map<String,String> nameMap,
                                         android.widget.LinearLayout tagContainer) {
        if (tagContainer == null) return;
        tagContainer.removeAllViews();
        for (String id : selectedIds) {
            String name = nameMap.containsKey(id) ? nameMap.get(id) : id;
            android.widget.LinearLayout tag = new android.widget.LinearLayout(ctx);
            tag.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            tag.setGravity(android.view.Gravity.CENTER_VERTICAL);
            tag.setBackgroundResource(R.drawable.bg_badge_neutral);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp8(), dp8());
            tag.setLayoutParams(lp);
            tag.setPadding(dp8() + 4, dp8() / 2, dp8() / 2, dp8() / 2);
            android.widget.TextView tvName = new android.widget.TextView(ctx);
            tvName.setText(name);
            tvName.setTextSize(13f);
            tvName.setTextColor(ctx.getResources().getColor(R.color.text_primary, null));
            tag.addView(tvName);
            android.widget.TextView tvDel = new android.widget.TextView(ctx);
            tvDel.setText("  ✕");
            tvDel.setTextSize(13f);
            tvDel.setTextColor(ctx.getResources().getColor(R.color.device_accent_error, null));
            tvDel.setPadding(0, 0, dp8() / 2, 0);
            final String removeId = id;
            tvDel.setOnClickListener(v -> {
                selectedIds.remove(removeId);
                nameMap.remove(removeId);
                refreshDeviceTagsNamed(ctx, selectedIds, nameMap, tagContainer);
                if (tagContainer.getChildCount() == 0) {
                    android.view.View scroll = (android.view.View) tagContainer.getParent();
                    if (scroll != null) scroll.setVisibility(android.view.View.GONE);
                }
            });
            tag.addView(tvDel);
            tagContainer.addView(tag);
        }
    }

    private void refreshDeviceTags(Context ctx,
                                    java.util.List<String> selectedIds,
                                    android.widget.LinearLayout tagContainer) {
        if (tagContainer == null) return;
        tagContainer.removeAllViews();
        for (String id : selectedIds) {
            int idx;
            try { idx = Integer.parseInt(id); } catch (Exception e) { continue; }
            if (idx < 0 || idx >= DEVICE_NAMES.length) continue;
            android.widget.LinearLayout tag = new android.widget.LinearLayout(ctx);
            tag.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            tag.setGravity(android.view.Gravity.CENTER_VERTICAL);
            tag.setBackgroundResource(R.drawable.bg_badge_neutral);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp8(), dp8());
            tag.setLayoutParams(lp);
            tag.setPadding(dp8() + 4, dp8() / 2, dp8() / 2, dp8() / 2);
            android.widget.TextView tvName = new android.widget.TextView(ctx);
            tvName.setText(DEVICE_NAMES[idx]);
            tvName.setTextSize(13f);
            tvName.setTextColor(ctx.getResources().getColor(R.color.text_primary, null));
            tag.addView(tvName);
            android.widget.TextView tvDel = new android.widget.TextView(ctx);
            tvDel.setText("  ✕");
            tvDel.setTextSize(13f);
            tvDel.setTextColor(ctx.getResources().getColor(R.color.device_accent_error, null));
            tvDel.setPadding(0, 0, dp8() / 2, 0);
            final String removeId = id;
            tvDel.setOnClickListener(v -> {
                selectedIds.remove(removeId);
                refreshDeviceTags(ctx, selectedIds, tagContainer);
            });
            tag.addView(tvDel);
            tagContainer.addView(tag);
        }
    }

    private int tryParseInt(android.widget.EditText et, int def) {
        if (et == null) return def;
        try { return Integer.parseInt(et.getText().toString()); } catch (Exception e) { return def; }
    }

    private int dp8() {
        return Math.round(8 * container.getResources().getDisplayMetrics().density);
    }

    // ── 유틸 메서드들 ────────────────────────────────────────────────────

    /** interface: 시간 선택 콜백 */
    private interface TimePickCallback { void onTimePicked(int hour, int minute); }

    /** 특정 날짜의 레코드 필터 */
    private java.util.List<WorkRecord> getRecordsForDate(java.time.LocalDate date) {
        java.util.List<WorkRecord> result = new java.util.ArrayList<>();
        if (date == null) return result;
        for (WorkRecord r : records)
            if (date.equals(r.getDate())) result.add(r);
        return result;
    }

    /** 장치 타입 → 색상 */
    private int deviceColor(WorkRecord.DeviceType type) {
        if (type == null) return android.graphics.Color.parseColor("#4CAF50");
        switch (type) {
            case PUMP:   return android.graphics.Color.parseColor("#2196F3");
            case SENSOR: return android.graphics.Color.parseColor("#FF9800");
            case SHADE:  return android.graphics.Color.parseColor("#9C27B0");
            case LED:    return android.graphics.Color.parseColor("#FFEB3B");
            default:     return android.graphics.Color.parseColor("#4CAF50");
        }
    }

    /** 시간 포맷 HH:mm */
    private String formatTime(java.time.LocalTime t) {
        if (t == null) return "--:--";
        return String.format(Locale.getDefault(), "%02d:%02d", t.getHour(), t.getMinute());
    }

    /** 상태 → 라벨 */
    private String statusLabel(WorkRecord.Status s) {
        if (s == null) return "예정";
        switch (s) {
            case DONE:      return "완료";
            case FAILED:    return "실패";
            case RUNNING:   return "진행중";
            case SCHEDULED: return "예정";
            default:        return "예정";
        }
    }

    /** 상태 → 색상 */
    private int statusColor(WorkRecord.Status s, Context ctx) {
        if (s == null) return ctx.getResources().getColor(R.color.text_hint, null);
        switch (s) {
            case DONE:    return ctx.getResources().getColor(R.color.forest_mid, null);
            case FAILED:  return ctx.getResources().getColor(R.color.device_accent_error, null);
            case RUNNING: return ctx.getResources().getColor(R.color.active_green, null);
            default:      return ctx.getResources().getColor(R.color.text_hint, null);
        }
    }

    /** TextView에 텍스트 설정 (null-safe) */
    private void setTv(View parent, int id, String text) {
        TextView tv = parent.findViewById(id);
        if (tv != null && text != null) tv.setText(text);
    }

    /** 시간 선택 다이얼로그 */
    /** 가동/휴지 시간 설정 — dialog_valve_timer 레이아웃 재활용 */
    private void showDurationPicker(Context ctx, String title,
                                     int initialMinutes, java.util.function.IntConsumer onConfirm) {
        android.view.View tv = android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_valve_timer, null);

        // 헤더 레이블
        android.widget.TextView tvLabel = tv.findViewById(R.id.tvTimerTargetLabel);
        if (tvLabel != null) tvLabel.setText(title);

        // WheelPicker
        com.acasian.iot.Calendar.view.WheelTimePickerView pickerHour   = tv.findViewById(R.id.pickerTimerHour);
        com.acasian.iot.Calendar.view.WheelTimePickerView pickerMinute = tv.findViewById(R.id.pickerTimerMinute);
        android.widget.TextView tvSummary = tv.findViewById(R.id.tvTimerSummary);

        final int initH = initialMinutes / 60;
        final int initM = initialMinutes % 60;
        final int[] selH = {initH};
        final int[] selM = {initM};

        // 시간: 0~5시간, 분: 0~59분
        String[] hourLabels = new String[6];
        for (int i = 0; i <= 5; i++) hourLabels[i] = String.valueOf(i);
        String[] minLabels = new String[60];
        for (int i = 0; i < 60; i++) minLabels[i] = String.format("%02d", i);

        if (pickerHour != null) {
            pickerHour.setRange(0, 5);
            pickerHour.setDisplayedValues(hourLabels);
            pickerHour.setWrapSelectorWheel(false);
            pickerHour.setValue(initH);
        }
        if (pickerMinute != null) {
            pickerMinute.setRange(0, 59);
            pickerMinute.setDisplayedValues(minLabels);
            pickerMinute.setWrapSelectorWheel(true);
            pickerMinute.setValue(initM);
        }

        Runnable updateSummary = () -> {
            int total = selH[0] * 60 + selM[0];
            if (tvSummary != null) tvSummary.setText(
                    selH[0] > 0 ? selH[0] + "시간 " + selM[0] + "분 (" + total + "분)"
                                : selM[0] + "분");
        };
        updateSummary.run();

        if (pickerHour != null) pickerHour.setOnValueChangeListener((p, o, n) -> {
            selH[0] = n; updateSummary.run(); });
        if (pickerMinute != null) pickerMinute.setOnValueChangeListener((p, o, n) -> {
            selM[0] = n; updateSummary.run(); });

        // 프리셋 버튼
        int[] presets = {10, 20, 30, 60, 90, 120};
        int[] presetIds = {R.id.btnPreset10, R.id.btnPreset20, R.id.btnPreset30,
                           R.id.btnPreset60, R.id.btnPreset90, R.id.btnPreset120};
        for (int i = 0; i < presets.length; i++) {
            android.widget.TextView btn = tv.findViewById(presetIds[i]);
            final int min = presets[i];
            if (btn != null) btn.setOnClickListener(v -> {
                selH[0] = min / 60; selM[0] = min % 60;
                if (pickerHour   != null) pickerHour.setValue(selH[0]);
                if (pickerMinute != null) pickerMinute.setValue(selM[0]);
                updateSummary.run();
            });
        }

        // 확인/취소 버튼
        android.widget.Button btnConfirm = tv.findViewById(R.id.btnTimerConfirm);
        android.widget.Button btnCancel  = tv.findViewById(R.id.btnTimerCancel);
        if (btnConfirm != null) btnConfirm.setText("확인");

        androidx.appcompat.app.AlertDialog dlg = new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setView(tv).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));

        if (btnCancel  != null) btnCancel.setOnClickListener(v  -> dlg.dismiss());
        if (btnConfirm != null) btnConfirm.setOnClickListener(v -> {
            onConfirm.accept(selH[0] * 60 + selM[0]);
            dlg.dismiss();
        });
        dlg.show();
    }

    private void showTimePicker(Context ctx, String title,
                                int initHour, int initMinute,
                                TimePickCallback callback) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_time_picker, null);
        TextView tvTitle = view.findViewById(R.id.tvPickerTitle);
        if (tvTitle != null) tvTitle.setText(title);
        TextView tvPreview = view.findViewById(R.id.tvPickerPreview);
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.getDefault(), "%02d", i);
        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) minutes[i] = String.format(Locale.getDefault(), "%02d", i);
        WheelTimePickerView pickerHour   = view.findViewById(R.id.pickerHour);
        WheelTimePickerView pickerMinute = view.findViewById(R.id.pickerMinute);
        pickerHour.setRange(0, 23);
        pickerHour.setDisplayedValues(hours);
        pickerHour.setValue(Math.max(0, Math.min(initHour, 23)));
        pickerHour.setWrapSelectorWheel(true);
        pickerMinute.setRange(0, 59);
        pickerMinute.setDisplayedValues(minutes);
        pickerMinute.setValue(Math.max(0, Math.min(initMinute, 59)));
        pickerMinute.setWrapSelectorWheel(true);
        updatePreview(tvPreview, pickerHour.getValue(), pickerMinute.getValue());
        WheelTimePickerView.OnValueChangeListener listener = (picker, oldVal, newVal) ->
                updatePreview(tvPreview, pickerHour.getValue(), pickerMinute.getValue());
        pickerHour.setOnValueChangeListener(listener);
        pickerMinute.setOnValueChangeListener(listener);
        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(view).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        view.findViewById(R.id.btnPickerCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnPickerConfirm).setOnClickListener(v -> {
            callback.onTimePicked(pickerHour.getValue(), pickerMinute.getValue());
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updatePreview(TextView tv, int hour, int minute) {
        if (tv == null) return;
        tv.setText(String.format(Locale.getDefault(), "%02d : %02d", hour, minute));
    }

    /**
     * 상단 [매일 반복 | 단건] 세그먼트 토글의 시각 상태 갱신.
     * @param checked true = 매일 반복(왼쪽 활성), false = 단건(오른쪽 활성)
     */
    private void applyRepeatSegmentStyle(android.view.View dlgView, boolean checked) {
        TextView segOn  = dlgView.findViewById(R.id.seg_repeat_on);
        TextView segOff = dlgView.findViewById(R.id.seg_repeat_off);
        if (segOn == null || segOff == null) return;
        Context ctx = dlgView.getContext();
        int colorActive   = ContextCompat.getColor(ctx, R.color.white);
        int colorInactive = ContextCompat.getColor(ctx, R.color.moss);
        if (checked) {
            // 반복 ON: 왼쪽(매일 반복) 활성
            segOn.setBackgroundResource(R.drawable.bg_btn_zone_start);
            segOn.setTextColor(colorActive);
            segOff.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            segOff.setTextColor(colorInactive);
        } else {
            // 단건 OFF: 오른쪽(단건) 활성
            segOn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            segOn.setTextColor(colorInactive);
            segOff.setBackgroundResource(R.drawable.bg_btn_zone_start);
            segOff.setTextColor(colorActive);
        }
    }

}
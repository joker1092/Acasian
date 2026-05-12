package com.acasian.iot;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.acasian.iot.network.ApiClient;
import com.acasian.iot.network.ApiService;
import com.acasian.iot.network.ApiDateUtil;
import com.acasian.iot.storage.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 예약설정 리스트 화면
 * 달력 뷰 대신 카드 리스트 형태로 예약 목록 표시
 * - 반복(isRepeat=1): 시간 크게 + 매일 반복 + 토글
 * - 단건(isRepeat=0): 시간 크게 + 날짜 + 토글
 */
public class ScheduleListActivity extends AppCompatActivity {

    // ── 탭 필터 ───────────────────────────────────
    private static final int TAB_ALL    = 0;
    private static final int TAB_REPEAT = 1;
    private static final int TAB_ONCE   = 2;

    private int currentTab = TAB_ALL;

    private TextView tabAll, tabRepeat, tabOnce;
    private View     tabIndicator;
    private RecyclerView rvScheduleList;
    private LinearLayout layoutEmpty;
    private FloatingActionButton fabAdd;

    private SessionManager session;
    private ApiService      apiSvc;
    private com.acasian.iot.Calendar.view.DateDetailView addDialogHelper;

    private final List<ApiService.ScheduleListResponse.ScheduleItem> allItems  = new ArrayList<>();
    private final List<ApiService.ScheduleListResponse.ScheduleItem> showItems = new ArrayList<>();
    private ScheduleListAdapter adapter;

    // ── 생명주기 ──────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        setContentView(R.layout.activity_schedule_list);
        applyInsets();

        session = SessionManager.getInstance(this);
        apiSvc  = ApiClient.getInstance(this).getService();

        initViews();
        setupTabs();
        setupFab();
        loadSchedules();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSchedules();
    }

    // ── 초기화 ────────────────────────────────────

    private void applyInsets() {
        View header = findViewById(R.id.scheduleListHeader);
        final int baseTop = header != null ? header.getPaddingTop() : 0;
        // FAB 및 리스트 하단 여백용
        View fab = findViewById(R.id.fabAddSchedule);
        final int baseFabBottom = fab != null
                ? ((android.view.ViewGroup.MarginLayoutParams) fab.getLayoutParams()).bottomMargin : 20;

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    int top    = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

                    // 헤더 상단 패딩
                    if (header != null)
                        header.setPadding(header.getPaddingLeft(), baseTop + top,
                                header.getPaddingRight(), header.getPaddingBottom());

                    // FAB 하단 마진
                    if (fab != null) {
                        android.view.ViewGroup.MarginLayoutParams lp =
                                (android.view.ViewGroup.MarginLayoutParams) fab.getLayoutParams();
                        lp.bottomMargin = baseFabBottom + bottom;
                        fab.setLayoutParams(lp);
                    }

                    // RecyclerView 하단 패딩 (FAB 뒤에 가리지 않도록)
                    View rv = findViewById(R.id.rvScheduleList);
                    if (rv != null) rv.setPadding(
                            rv.getPaddingLeft(), rv.getPaddingTop(),
                            rv.getPaddingRight(), 80 + bottom);

                    return insets;
                });
    }

    private void initViews() {
        findViewById(R.id.btnScheduleListBack).setOnClickListener(v -> finish());

        tabAll          = findViewById(R.id.tabAll);
        tabRepeat       = findViewById(R.id.tabRepeat);
        tabOnce         = findViewById(R.id.tabOnce);
        tabIndicator    = findViewById(R.id.tabIndicator);
        rvScheduleList  = findViewById(R.id.rvScheduleList);
        layoutEmpty     = findViewById(R.id.layoutEmpty);
        fabAdd          = findViewById(R.id.fabAddSchedule);

        adapter = new ScheduleListAdapter();
        rvScheduleList.setLayoutManager(new LinearLayoutManager(this));
        rvScheduleList.setAdapter(adapter);

        // 예약 추가 팝업용 DateDetailView (뷰 없이 다이얼로그만 사용)
        addDialogHelper = new com.acasian.iot.Calendar.view.DateDetailView(
                findViewById(android.R.id.content),
                AppConfig.getInstance().isDevMode());
        addDialogHelper.setOnScheduleAddListener(
                (date, time, profileId, telNo, isAuto, isSeq, nodeIds, stime, dtime, reCount, isRepeat) -> {
                    if (AppConfig.getInstance().isDevMode()) {
                        Toast.makeText(this, "DEV: 예약 등록됨", Toast.LENGTH_SHORT).show();
                        loadSchedules();
                        return;
                    }
                    String farmId   = com.acasian.iot.ZoneStore.getInstance().getFarmId();
                    String yymmdd   = com.acasian.iot.network.ApiDateUtil.toYymmdd(date);
                    String hhnn     = com.acasian.iot.network.ApiDateUtil.toHhnn(time);
                    String isSeqStr = isSeq ? "Y" : "N";
                    com.acasian.iot.network.ApiService.ScheduleAddRequest req;
                    if (isAuto) {
                        req = com.acasian.iot.network.ApiService.ScheduleAddRequest.forAutoRepeat(
                                session.getPhoneNumber(), farmId, telNo,
                                profileId, yymmdd, hhnn, isSeqStr, isRepeat);
                    } else {
                        req = com.acasian.iot.network.ApiService.ScheduleAddRequest.forIndividualRepeat(
                                session.getPhoneNumber(), farmId, telNo,
                                yymmdd, hhnn, isSeqStr, nodeIds, stime, dtime, reCount, isRepeat);
                    }
                    apiSvc.addSchedule(req).enqueue(
                            new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleAddResponse>() {
                                @Override public void onResponse(
                                        retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleAddResponse> c,
                                        retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleAddResponse> r) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(ScheduleListActivity.this,
                                                "예약이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                        loadSchedules();
                                    });
                                }
                                @Override public void onFailure(
                                        retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleAddResponse> c,
                                        Throwable t) {
                                    runOnUiThread(() -> Toast.makeText(ScheduleListActivity.this,
                                            "예약 등록 실패", Toast.LENGTH_SHORT).show());
                                }
                            });
                });
    }

    private void setupTabs() {
        tabAll   .setOnClickListener(v -> selectTab(TAB_ALL));
        tabRepeat.setOnClickListener(v -> selectTab(TAB_REPEAT));
        tabOnce  .setOnClickListener(v -> selectTab(TAB_ONCE));
        selectTab(TAB_ALL);
    }

    private void selectTab(int tab) {
        currentTab = tab;
        // 텍스트 색상
        tabAll   .setTextColor(tab == TAB_ALL    ? getColor(R.color.white) : getColor(R.color.mist));
        tabRepeat.setTextColor(tab == TAB_REPEAT ? getColor(R.color.white) : getColor(R.color.mist));
        tabOnce  .setTextColor(tab == TAB_ONCE   ? getColor(R.color.white) : getColor(R.color.mist));
        // 인디케이터 이동 (전체너비 1/3씩)
        // 탭 인디케이터 위치: weight로 1/3씩 이동
        // 탭별 위치는 탭 텍스트 강조로 대체 (인디케이터 별도 이동 생략)
        applyFilter();
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> openAddDialog(null));
    }

    // ── 데이터 로드 ───────────────────────────────

    private void loadSchedules() {
        // DEV_MODE: 더미 데이터 표시
        if (AppConfig.getInstance().isDevMode()) {
            loadDemoSchedules();
            return;
        }
        String userId = session.getPhoneNumber();
        apiSvc.getSchedule(new ApiService.ScheduleGetRequest(userId))
              .enqueue(new retrofit2.Callback<ApiService.ScheduleListResponse>() {
                  @Override
                  public void onResponse(
                          retrofit2.Call<ApiService.ScheduleListResponse> call,
                          retrofit2.Response<ApiService.ScheduleListResponse> res) {
                      runOnUiThread(() -> {
                          allItems.clear();
                          ApiService.ScheduleListResponse body = res.body();
                          if (body != null && body.data != null) {
                              allItems.addAll(body.data);
                          }
                          applyFilter();
                      });
                  }
                  @Override
                  public void onFailure(retrofit2.Call<ApiService.ScheduleListResponse> call,
                                        Throwable t) {
                      runOnUiThread(() ->
                              Toast.makeText(ScheduleListActivity.this,
                                      "예약 목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show());
                  }
              });
    }

    /** nodeIds 콤마구분 문자열 → "01, 02, 03" 또는 "01 외 N개" */
    private static String formatNodeIds(String nodeIds) {
        String[] ids = nodeIds.split(",");
        if (ids.length == 1) return ids[0].trim();
        if (ids.length <= 4) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(ids[i].trim());
            }
            return sb.toString();
        }
        return ids[0].trim() + " 외 " + (ids.length - 1) + "개";
    }

    private static ApiService.ScheduleCateListResponse.NodeItem makeDemoNode(String nodeId) {
        ApiService.ScheduleCateListResponse.NodeItem n = new ApiService.ScheduleCateListResponse.NodeItem();
        n.nodeId   = nodeId;
        n.nodeType = "S";
        return n;
    }

    /** DEV_MODE 전용 더미 예약 목록 */
    private void loadDemoSchedules() {
        allItems.clear();

        // 반복 예약 1 — 매일 오전 6:30 (groupList 더미)
        ApiService.ScheduleListResponse.ScheduleItem r1 = new ApiService.ScheduleListResponse.ScheduleItem();
        r1.schId    = 1;
        r1.cateName = "봄 아침 관수";
        r1.hhnn     = "0630";
        r1.yymmdd   = "260514";
        r1.lteNo    = "컨트롤박스 #1";
        r1.isSched  = "N";
        r1.isRepeat = 1;
        r1.isDel    = 1;
        r1.kind     = 1;
        r1.groupList = new java.util.ArrayList<>();
        ApiService.ScheduleCateListResponse.GroupItem g1a = new ApiService.ScheduleCateListResponse.GroupItem();
        g1a.groupName = "A구역";
        g1a.nodeList  = java.util.Arrays.asList(makeDemoNode("01"), makeDemoNode("02"), makeDemoNode("03"));
        ApiService.ScheduleCateListResponse.GroupItem g1b = new ApiService.ScheduleCateListResponse.GroupItem();
        g1b.groupName = "B구역";
        g1b.nodeList  = java.util.Arrays.asList(makeDemoNode("04"), makeDemoNode("05"));
        r1.groupList.add(g1a);
        r1.groupList.add(g1b);
        allItems.add(r1);

        // 반복 예약 2 — 매일 오후 6:00 (groupList 더미)
        ApiService.ScheduleListResponse.ScheduleItem r2 = new ApiService.ScheduleListResponse.ScheduleItem();
        r2.schId    = 2;
        r2.cateName = "저녁 관수";
        r2.hhnn     = "1800";
        r2.yymmdd   = "260514";
        r2.lteNo    = "컨트롤박스 #1";
        r2.isSched  = "N";
        r2.isRepeat = 1;
        r2.isDel    = 1;
        r2.kind     = 1;
        r2.groupList = new java.util.ArrayList<>();
        ApiService.ScheduleCateListResponse.GroupItem g2a = new ApiService.ScheduleCateListResponse.GroupItem();
        g2a.groupName = "전체";
        g2a.nodeList  = java.util.Arrays.asList(
                makeDemoNode("01"), makeDemoNode("02"), makeDemoNode("03"),
                makeDemoNode("04"), makeDemoNode("05"));
        r2.groupList.add(g2a);
        allItems.add(r2);

        // 단건 예약 — 예정
        ApiService.ScheduleListResponse.ScheduleItem s1 = new ApiService.ScheduleListResponse.ScheduleItem();
        s1.schId    = 3;
        s1.cateName = "주말 집중 관수";
        s1.hhnn     = "0700";
        s1.yymmdd   = "260517";
        s1.lteNo    = "컨트롤박스 #2";
        s1.isSched  = "N";
        s1.isRepeat = 0;
        s1.isDel    = 1;
        allItems.add(s1);

        // 단건 예약 — 완료
        ApiService.ScheduleListResponse.ScheduleItem s2 = new ApiService.ScheduleListResponse.ScheduleItem();
        s2.schId    = 4;
        s2.cateName = "시험 관수";
        s2.hhnn     = "0900";
        s2.yymmdd   = "260510";
        s2.lteNo    = "컨트롤박스 #1";
        s2.isSched  = "Y";  // 완료
        s2.isRepeat = 0;
        s2.isDel    = 0; // 종료
        allItems.add(s2);

        applyFilter();
    }

    private void applyFilter() {
        showItems.clear();
        for (ApiService.ScheduleListResponse.ScheduleItem item : allItems) {
            if (currentTab == TAB_ALL)    { showItems.add(item); }
            else if (currentTab == TAB_REPEAT && item.isRepeat == 1) { showItems.add(item); }
            else if (currentTab == TAB_ONCE   && item.isRepeat == 0) { showItems.add(item); }
        }
        adapter.notifyDataSetChanged();
        boolean empty = showItems.isEmpty();
        rvScheduleList.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmpty   .setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ── 예약 추가/수정 다이얼로그 ─────────────────

    private void openAddDialog(ApiService.ScheduleListResponse.ScheduleItem editItem) {
        // 달력 없이 바로 팝업 표시
        addDialogHelper.showAddDialogWithoutDate();
    }

    // ── 예약 삭제 ────────────────────────────────

    private void confirmDelete(ApiService.ScheduleListResponse.ScheduleItem item) {
        new AlertDialog.Builder(this)
                .setTitle("예약 삭제")
                .setMessage("이 예약을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) -> deleteSchedule(item))
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteSchedule(ApiService.ScheduleListResponse.ScheduleItem item) {
        apiSvc.delSchedule(new ApiService.ScheduleDelRequest(String.valueOf(item.schId)))
              .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                  @Override
                  public void onResponse(
                          retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                          retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> res) {
                      runOnUiThread(() -> {
                          Toast.makeText(ScheduleListActivity.this,
                                  "예약이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                          loadSchedules();
                      });
                  }
                  @Override
                  public void onFailure(retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                                        Throwable t) {
                      runOnUiThread(() ->
                              Toast.makeText(ScheduleListActivity.this,
                                      "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show());
                  }
              });
    }

    /**
     * 예약 카드 밸브 그룹 텍스트
     *
     * kind=1 (자동 유형):
     *   cateId → groupList 기반으로 그룹명(밸브N개) 나열
     *   groupList 없으면 cateName fallback
     *
     * kind=2 (개별):
     *   nodeIds 콤마구분 → "밸브 01, 02, 03" 표시
     */
    private static String buildValveGroupText(ApiService.ScheduleListResponse.ScheduleItem item) {

        if (item.kind == 1) {
            // ── 자동 유형 ─────────────────────────────────────────────
            // groupList 있음 → nodeList의 nodeId 목록 나열
            if (item.groupList != null && !item.groupList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < item.groupList.size(); i++) {
                    ApiService.ScheduleCateListResponse.GroupItem g = item.groupList.get(i);
                    if (g.nodeList == null || g.nodeList.isEmpty()) continue;
                    if (sb.length() > 0) sb.append("  /  ");
                    // nodeList의 nodeId 나열
                    StringBuilder nodes = new StringBuilder();
                    for (int j = 0; j < g.nodeList.size(); j++) {
                        if (j > 0) nodes.append(", ");
                        nodes.append(g.nodeList.get(j).nodeId);
                    }
                    sb.append(nodes);
                }
                if (sb.length() > 0) return sb.toString();
            }
            // groupList 없음 → nodeIds 조회
            if (item.nodeIds != null && !item.nodeIds.isEmpty()) {
                return formatNodeIds(item.nodeIds);
            }
            // 없으면 표기 안 함
            return "";
        }

        // ── 개별 (kind=2): nodeIds → 밸브 번호 나열 ──────────────────
        if (item.nodeIds != null && !item.nodeIds.isEmpty()) {
            return formatNodeIds(item.nodeIds);
        }

        // 없으면 표기 안 함
        return "";
    }

    // ── RecyclerView 어댑터 ───────────────────────

    private class ScheduleListAdapter
            extends RecyclerView.Adapter<ScheduleListAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_schedule_list_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ApiService.ScheduleListResponse.ScheduleItem item = showItems.get(pos);
            h.bind(item);
        }

        @Override
        public int getItemCount() { return showItems.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView        tvCardTime, tvCardTypeName, tvCardStatus, tvCardDate, tvCardZone;
            TextView        tvRepeatIcon, tvValveGroup;
            SwitchMaterial  switchActive;

            VH(@NonNull View v) {
                super(v);
                tvCardTime      = v.findViewById(R.id.tvCardTime);
                tvCardTypeName  = v.findViewById(R.id.tvCardTypeName);
                tvCardStatus    = v.findViewById(R.id.tvCardStatus);
                tvCardDate      = v.findViewById(R.id.tvCardDate);
                tvCardZone      = v.findViewById(R.id.tvCardZone);
                tvRepeatIcon    = v.findViewById(R.id.tvRepeatIcon);
                tvValveGroup    = v.findViewById(R.id.tvValveGroup);
                switchActive    = v.findViewById(R.id.switchCardActive);
            }

            void bind(ApiService.ScheduleListResponse.ScheduleItem item) {
                // ── 시간 표시 ──────────────────────────────
                LocalTime time = ApiDateUtil.fromHhnn(item.hhnn);
                if (time != null) {
                    String amPm = time.getHour() < 12 ? "오전" : "오후";
                    int    h    = time.getHour() % 12;
                    if (h == 0) h = 12;
                    tvCardTime.setText(String.format(Locale.getDefault(),
                            "%s %d:%02d", amPm, h, time.getMinute()));
                } else {
                    tvCardTime.setText(item.hhnn != null ? item.hhnn : "--:--");
                }

                // ── 유형명 ────────────────────────────────
                String name = item.cateName != null ? item.cateName : "직접 설정";
                tvCardTypeName.setText(name);

                // ── 메인함 ────────────────────────────────
                tvCardZone.setText(item.lteNo != null ? item.lteNo : "");

                // ── 반복 / 단건 분기 ───────────────────────
                boolean isRepeat = (item.isRepeat == 1);

                tvRepeatIcon .setVisibility(isRepeat ? View.VISIBLE : View.GONE);
                tvValveGroup .setVisibility(isRepeat ? View.VISIBLE : View.GONE);
                tvCardDate   .setVisibility(isRepeat ? View.GONE    : View.VISIBLE);

                // 토글: 반복만 표시, 단건은 숨김
                switchActive.setVisibility(isRepeat ? View.VISIBLE : View.GONE);

                if (isRepeat) {
                    // 반복: 밸브 그룹 텍스트 표시
                    boolean active = item.isActive(); // isDel==1
                    int activeColor = getColor(R.color.moss);
                    int dimColor    = getColor(R.color.inactive_gray);

                    // 밸브 그룹 텍스트 구성
                    if (tvValveGroup != null) {
                        String groupText = buildValveGroupText(item);
                        if (groupText.isEmpty()) {
                            tvValveGroup.setVisibility(View.GONE);
                        } else {
                            tvValveGroup.setVisibility(View.VISIBLE);
                            tvValveGroup.setText(groupText);
                            tvValveGroup.setTextColor(active ? activeColor : dimColor);
                        }
                    }
                    tvCardTime.setTextColor(active
                            ? getColor(R.color.text_primary)
                            : getColor(R.color.inactive_gray));
                    switchActive.setOnCheckedChangeListener(null); // 중복 방지
                    switchActive.setChecked(active);
                    switchActive.setOnCheckedChangeListener((btn, checked) -> {
                        // stopSchedule API 호출
                        // checked=true → isDel=1(진행중), checked=false → isDel=0(종료)
                        int newIsDel = checked ? 1 : 0;
                        tvCardTime.setTextColor(checked
                                ? getColor(R.color.text_primary)
                                : getColor(R.color.inactive_gray));
                        if (tvValveGroup != null)
                            tvValveGroup.setTextColor(checked ? activeColor : dimColor);

                        if (AppConfig.getInstance().isDevMode()) {
                            item.isDel = newIsDel;
                            Toast.makeText(ScheduleListActivity.this,
                                    checked ? "반복 재개" : "반복 정지", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        apiSvc.stopSchedule(
                                checked ? ApiService.StopScheduleRequest.resume(item.schId)
                                        : ApiService.StopScheduleRequest.stop(item.schId))
                              .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                                  @Override public void onResponse(
                                          retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> c,
                                          retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> r) {
                                      runOnUiThread(() -> {
                                          item.isDel = newIsDel;
                                          Toast.makeText(ScheduleListActivity.this,
                                                  checked ? "반복 재개됨" : "반복 정지됨",
                                                  Toast.LENGTH_SHORT).show();
                                      });
                                  }
                                  @Override public void onFailure(
                                          retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> c,
                                          Throwable t) {
                                      runOnUiThread(() -> {
                                          // 실패 시 토글 원복
                                          switchActive.setOnCheckedChangeListener(null);
                                          switchActive.setChecked(!checked);
                                          Toast.makeText(ScheduleListActivity.this,
                                                  "변경 실패", Toast.LENGTH_SHORT).show();
                                      });
                                  }
                              });
                    });
                } else {
                    // 단건: 날짜 표시
                    LocalDate date = ApiDateUtil.fromYymmdd(item.yymmdd);
                    if (date != null) {
                        String dayOfWeek = new String[]{"일","월","화","수","목","금","토"}
                                [date.getDayOfWeek().getValue() % 7];
                        tvCardDate.setText(String.format(Locale.getDefault(),
                                "%d월 %d일 (%s)", date.getMonthValue(), date.getDayOfMonth(),
                                dayOfWeek));
                    } else {
                        tvCardDate.setText(item.yymmdd != null ? item.yymmdd : "");
                    }
                    // 완료 여부에 따라 색상
                    boolean done = "Y".equalsIgnoreCase(item.isSched);
                    tvCardTime.setTextColor(done
                            ? getColor(R.color.inactive_gray)
                            : getColor(R.color.text_primary));
                }

                // ── 상태 배지 ─────────────────────────────
                applyStatusBadge(item);

                // ── 클릭: 상세/삭제 ───────────────────────
                itemView.setOnClickListener(v -> showDetailPopup(item));
                itemView.setOnLongClickListener(v -> {
                    confirmDelete(item);
                    return true;
                });
            }

            private void applyStatusBadge(ApiService.ScheduleListResponse.ScheduleItem item) {
                if (item.isRepeat == 1) {
                    tvCardStatus.setText("매일 반복");
                    tvCardStatus.setBackgroundResource(R.drawable.bg_badge_ok);
                    tvCardStatus.setTextColor(getColor(R.color.forest_dark));
                    return;
                }
                // 단건: isSched 기반
                boolean done = "Y".equalsIgnoreCase(item.isSched);
                LocalDate date = ApiDateUtil.fromYymmdd(item.yymmdd);
                LocalTime time = ApiDateUtil.fromHhnn(item.hhnn);
                boolean isPast = date != null && (
                        date.isBefore(LocalDate.now()) ||
                        (date.isEqual(LocalDate.now()) && time != null
                                && time.isBefore(LocalTime.now())));

                if (done) {
                    tvCardStatus.setText("완료");
                    tvCardStatus.setBackgroundResource(R.drawable.bg_badge_neutral);
                    tvCardStatus.setTextColor(getColor(R.color.inactive_gray));
                } else if (isPast) {
                    tvCardStatus.setText("미실행");
                    tvCardStatus.setBackgroundResource(R.drawable.bg_badge_warn);
                    tvCardStatus.setTextColor(getColor(R.color.device_accent_warn));
                } else {
                    tvCardStatus.setText("예약");
                    tvCardStatus.setBackgroundResource(R.drawable.bg_badge_ok);
                    tvCardStatus.setTextColor(getColor(R.color.forest_dark));
                }
            }

            /** 예약 상세 팝업 — 수정/삭제 */
            private void showDetailPopup(ApiService.ScheduleListResponse.ScheduleItem item) {
                String[] options = item.isRepeat == 1
                        ? new String[]{"수정", "삭제"}
                        : new String[]{"삭제"};

                new AlertDialog.Builder(ScheduleListActivity.this)
                        .setTitle(item.cateName != null ? item.cateName : "예약 상세")
                        .setItems(options, (d, w) -> {
                            if (item.isRepeat == 1) {
                                if (w == 0) openAddDialog(item);
                                else        confirmDelete(item);
                            } else {
                                confirmDelete(item);
                            }
                        })
                        .show();
            }
        }
    }
}

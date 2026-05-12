package com.acasian.iot;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 홈 대시보드 — 앱의 허브
 * 스플래시 → 로그인 → 이 화면
 * 큰 버튼 4개로 관수작업/예약설정/정보조회/설정 진입
 */
public class HomeDashboardActivity extends AppCompatActivity {

    public enum DashStatus { IDLE, RUNNING, WARNING, ERROR }
    private DashStatus currentStatus = DashStatus.IDLE;

    // ── 1분 주기 상태 갱신 ──────────────────────────────────────
    private static final long POLL_INTERVAL_MS = 60_000L;
    private final android.os.Handler pollHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!AppConfig.getInstance().isDevMode()) {
                refreshStatus();
            }
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private com.acasian.iot.storage.SessionManager session;
    private android.widget.TextView tvFarmName;

    private View     dashHeader;
    private CardView cardStatus;
    private TextView tvStatusBadge, tvStatusMain, tvStatusSub;
    private View     dashProgressDivider, dashProgressRow;
    private ProgressBar dashProgressBar;
    private TextView tvProgressTime;
    private TextView tvSensorSoil, tvSensorTemp, tvSensorEC, tvSensorPH;
    private View     sensorCellSoil;
    private CardView btnIrrigation, btnSchedule, btnInfo, btnSettings;
    private CardView cardAlert;
    private TextView tvAlertMsg;
    private CardView btnEmergencyStop;
    private View     dashEmergencyDivider;

    @Override
    protected void onResume() {
        super.onResume();
        // 복귀 시 즉시 1회 갱신 + 1분 폴링 시작
        if (!AppConfig.getInstance().isDevMode()) {
            refreshStatus();
        }
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_home_dashboard);
        session = com.acasian.iot.storage.SessionManager.getInstance(this);
        applyInsets();
        initViews();
        setupButtons();
        // DEV_MODE는 LoginActivity에서 AppConfig에 이미 세팅됨
        if (AppConfig.getInstance().isDevMode()) loadDemoData();
        else                                     loadRealData();
    }

    private void applyInsets() {
        View header = findViewById(R.id.dashHeader);
        final int base = header != null ? header.getPaddingTop() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    int sh = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    if (header != null)
                        header.setPadding(header.getPaddingLeft(), base + sh,
                                header.getPaddingRight(), header.getPaddingBottom());
                    return insets;
                });
    }

    private void initViews() {
        dashHeader          = findViewById(R.id.dashHeader);
        cardStatus          = findViewById(R.id.cardDashStatus);
        tvFarmName          = findViewById(R.id.tvDashFarmName);
        // 세션에 저장된 농장명 표시 (로그인 시 서버 nickName 저장)
        if (tvFarmName != null) {
            String name = session.getUserName();
            tvFarmName.setText((name != null && !name.isEmpty()) ? name + " 🌿" : "내 농장 🌿");
        }
        tvStatusBadge       = findViewById(R.id.tvDashStatusBadge);
        tvStatusMain        = findViewById(R.id.tvDashStatusMain);
        tvStatusSub         = findViewById(R.id.tvDashStatusSub);
        dashProgressDivider = findViewById(R.id.dashProgressDivider);
        dashProgressRow     = findViewById(R.id.dashProgressRow);
        dashProgressBar     = findViewById(R.id.dashProgressBar);
        tvProgressTime      = findViewById(R.id.tvDashProgressTime);
        tvSensorSoil  = findViewById(R.id.tvSensorSoil);
        tvSensorTemp  = findViewById(R.id.tvSensorTemp);
        tvSensorEC    = findViewById(R.id.tvSensorEC);
        tvSensorPH    = findViewById(R.id.tvSensorPH);
        sensorCellSoil = findViewById(R.id.sensorCellSoil);
        btnIrrigation    = findViewById(R.id.btnDashIrrigation);
        btnSchedule      = findViewById(R.id.btnDashSchedule);
        btnInfo          = findViewById(R.id.btnDashInfo);
        btnSettings      = findViewById(R.id.btnDashSettings);
        cardAlert        = findViewById(R.id.cardDashAlert);
        tvAlertMsg       = findViewById(R.id.tvDashAlertMsg);
        btnEmergencyStop      = findViewById(R.id.btnDashEmergencyStop);
        dashEmergencyDivider  = findViewById(R.id.dashEmergencyDivider);
    }

    private void setupButtons() {
        // DEV_MODE는 AppConfig에서 전역 관리 — Intent extra 불필요
        if (btnIrrigation != null) btnIrrigation.setOnClickListener(v ->
            startActivity(new Intent(this, IrrigationActivity.class)));
        if (btnSchedule != null) btnSchedule.setOnClickListener(v ->
            startActivity(new Intent(this, ScheduleListActivity.class)));
        if (btnInfo != null) btnInfo.setOnClickListener(v ->
            startActivity(new Intent(this, SensorInfoActivity.class)));
        if (btnSettings != null) btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        if (cardAlert != null) cardAlert.setOnClickListener(v -> {
            Intent i = new Intent(this, SensorInfoActivity.class);
            startActivity(i);
        });
        if (btnEmergencyStop != null) btnEmergencyStop.setOnClickListener(v ->
                showDashEmergencyDialog());
    }

    // ── 홈 긴급중지 커스텀 다이얼로그 ─────────────────────────────────
    private void showDashEmergencyDialog() {
        float dp = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setBackgroundColor(0xFFB71C1C);
        int hp = Math.round(20 * dp);
        header.setPadding(hp, Math.round(18 * dp), hp, Math.round(18 * dp));
        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("⚠  긴급 중지");
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFFFFFFFF);
        header.addView(tvTitle);
        root.addView(header);

        android.widget.LinearLayout body = new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.setBackgroundColor(0xFFFFFFFF);
        int bp = Math.round(20 * dp);
        body.setPadding(bp, bp, bp, Math.round(8 * dp));
        android.widget.TextView tvMsg = new android.widget.TextView(this);
        tvMsg.setText("전체 관수를 즉시\n중단하시겠습니까?");
        tvMsg.setTextSize(18f);
        tvMsg.setTextColor(0xFF1B2E1B);
        tvMsg.setLineSpacing(0, 1.35f);
        body.addView(tvMsg);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setGravity(android.view.Gravity.END);
        btnRow.setPadding(0, Math.round(20 * dp), 0, Math.round(4 * dp));

        androidx.appcompat.app.AlertDialog[] ref = {null};
        android.widget.Button btnCancel = new android.widget.Button(this);
        btnCancel.setText("취소");
        btnCancel.setTextSize(17f);
        btnCancel.setTextColor(0xFF9E9E9E);
        btnCancel.setBackground(null);
        btnCancel.setPadding(Math.round(8*dp),0,Math.round(8*dp),0);
        btnCancel.setOnClickListener(v -> { if(ref[0]!=null) ref[0].dismiss(); });

        android.widget.Button btnStop = new android.widget.Button(this);
        btnStop.setText("지금 중지");
        btnStop.setTextSize(17f);
        btnStop.setTypeface(null, android.graphics.Typeface.BOLD);
        btnStop.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFB71C1C); bg.setCornerRadius(8*dp);
        btnStop.setBackground(bg);
        btnStop.setPadding(Math.round(18*dp),Math.round(10*dp),Math.round(18*dp),Math.round(10*dp));
        btnStop.setOnClickListener(v -> {
            if(ref[0]!=null) ref[0].dismiss();
            // TODO_API: stopAll() — 전체 GW 긴급중지
            applyStatus(DashStatus.IDLE, "관수가 중지되었습니다", "긴급 중지됨");
            applyProgress(0, "");
            applyEmergencyStyle(false);
            showAlert(false, null);
        });
        btnRow.addView(btnCancel);
        btnRow.addView(btnStop);
        body.addView(btnRow);
        root.addView(body);

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

    private void loadDemoData() {
        // TODO_API: 실제 상태는 서버 폴링/WebSocket으로 교체
        applySensorValues("32%", "21°C", "1.8", "6.5");
        applyStatus(DashStatus.RUNNING, "관수 중 — 컨트롤박스 #1", "그룹 1 진행중 · 12분 남음");
        applyProgress(55, "12분 남음");
        showAlert(true, "⚠  구역 3 습도 낮음 (08:42)");
        applyEmergencyStyle(true);
    }

    /**
     * 1분 주기 상태 갱신 — valveStatus만 재조회하여 상태카드 업데이트.
     * 농장명·farmId 등 초기 설정은 건드리지 않음.
     */
    private void refreshStatus() {
        com.acasian.iot.repository.FarmRepository farmRepo =
                new com.acasian.iot.repository.FarmRepository(this);
        farmRepo.fetchFarmInfo(new com.acasian.iot.repository.FarmRepository.FarmInfoCallback() {
            @Override
            public void onSuccess(com.acasian.iot.model.response.FarmInfoResponse response) {
                runOnUiThread(() -> {
                    boolean anyRunning = false;
                    if (response.getFarmInfoList() != null) {
                        outer:
                        for (com.acasian.iot.model.response.FarmInfoResponse.FarmInfo f
                                : response.getFarmInfoList()) {
                            if (f.getMainList() == null) continue;
                            for (com.acasian.iot.model.response.FarmInfoResponse.MainInfo m
                                    : f.getMainList()) {
                                if (m.getNodeList() == null) continue;
                                for (com.acasian.iot.model.response.FarmInfoResponse.NodeInfo n
                                        : m.getNodeList()) {
                                    if (n.getValveStatus() == 1) {
                                        anyRunning = true;
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                    if (anyRunning) {
                        applyStatus(DashStatus.RUNNING, "관수 진행 중", "");
                    } else {
                        applyStatus(DashStatus.IDLE, "관수 대기 중", "");
                    }
                });
            }
            @Override
            public void onFailure(String errorMessage) {
                // 갱신 실패 시 기존 상태 유지 (에러 표시 안 함)
            }
        });
    }

    private void loadRealData() {
        // 로딩 중 초기 상태 표시
        applyStatus(DashStatus.IDLE, "농장 정보 불러오는 중...", "");
        applySensorValues("--", "--", "--", "--");
        applyEmergencyStyle(false);

        // farminfo API → ZoneStore 채우기
        com.acasian.iot.repository.FarmRepository farmRepo =
                new com.acasian.iot.repository.FarmRepository(this);

        farmRepo.fetchFarmInfo(new com.acasian.iot.repository.FarmRepository.FarmInfoCallback() {
            @Override
            public void onSuccess(com.acasian.iot.model.response.FarmInfoResponse response) {
                // ZoneStore는 FarmRepository 내부에서 자동 갱신됨
                runOnUiThread(() -> {
                    // 농장명 + farmId 갱신 — farminfo 응답 우선 적용
                    if (response.getFarmInfoList() != null
                            && !response.getFarmInfoList().isEmpty()) {
                        com.acasian.iot.model.response.FarmInfoResponse.FarmInfo farm =
                                response.getFarmInfoList().get(0);
                        String farmName = farm.getFarmName();
                        String farmId   = farm.getFarmId();
                        if (farmName != null && !farmName.isEmpty()) {
                            session.saveLogin(session.getPhoneNumberFormatted(), farmName);
                            if (tvFarmName != null)
                                tvFarmName.setText(farmName + " 🌿");
                        }
                        if (farmId != null && !farmId.isEmpty()) {
                            session.saveFarmId(farmId);
                            com.acasian.iot.ZoneStore.getInstance().setFarmId(farmId);
                        }
                    }
                    // farminfo nodelist의 valveStatus로 현재 상태 판단
                    boolean anyRunning = false;
                    if (response.getFarmInfoList() != null) {
                        outer:
                        for (com.acasian.iot.model.response.FarmInfoResponse.FarmInfo f
                                : response.getFarmInfoList()) {
                            if (f.getMainList() == null) continue;
                            for (com.acasian.iot.model.response.FarmInfoResponse.MainInfo m
                                    : f.getMainList()) {
                                if (m.getNodeList() == null) continue;
                                for (com.acasian.iot.model.response.FarmInfoResponse.NodeInfo n
                                        : m.getNodeList()) {
                                    if (n.getValveStatus() == 1) { // 1=관수중
                                        anyRunning = true;
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                    if (anyRunning) {
                        applyStatus(DashStatus.RUNNING, "관수 진행 중", "");
                    } else {
                        applyStatus(DashStatus.IDLE, "관수 대기 중", "");
                    }
                });
            }
            @Override
            public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    applyStatus(DashStatus.ERROR, "농장 정보 불러오기 실패", errorMessage);
                    showAlert(true, "농장 정보를 불러오지 못했습니다.\n네트워크 상태를 확인해 주세요.");
                });
            }
        });
    }

    public void applyStatus(DashStatus status, String main, String sub) {
        currentStatus = status;
        if (tvStatusMain != null) tvStatusMain.setText(main);
        if (tvStatusSub  != null) tvStatusSub.setText(sub);
        // 긴급 중지 버튼: 관수 중일 때만 표시
        boolean isRunning = (status == DashStatus.RUNNING);
        if (btnEmergencyStop     != null)
            btnEmergencyStop.setVisibility(isRunning ? android.view.View.VISIBLE : android.view.View.GONE);
        if (dashEmergencyDivider != null)
            dashEmergencyDivider.setVisibility(isRunning ? android.view.View.VISIBLE : android.view.View.GONE);
        switch (status) {
            case RUNNING:
                if (cardStatus != null) cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.device_bg_running));
                if (tvStatusBadge != null) {
                    tvStatusBadge.setText("관수 중");
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_ok);
                    tvStatusBadge.setTextColor(0xFF1B5E20);
                }
                break;
            case WARNING:
                if (cardStatus != null) cardStatus.setCardBackgroundColor(0xFFFFF8E1);
                if (tvStatusBadge != null) {
                    tvStatusBadge.setText("주의");
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warn);
                    tvStatusBadge.setTextColor(0xFFF57F17);
                }
                break;
            case ERROR:
                if (cardStatus != null) cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.device_bg_error));
                if (tvStatusBadge != null) {
                    tvStatusBadge.setText("오류");
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_error);
                    tvStatusBadge.setTextColor(0xFFC62828);
                }
                break;
            default:
                if (cardStatus != null) cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.card_bg));
                if (tvStatusBadge != null) {
                    tvStatusBadge.setText("대기 중");
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                    tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                }
                break;
        }
    }

    public void applyProgress(int pct, String label) {
        boolean show = pct > 0;
        if (dashProgressDivider != null) dashProgressDivider.setVisibility(show ? View.VISIBLE : View.GONE);
        if (dashProgressRow     != null) dashProgressRow.setVisibility(show ? View.VISIBLE : View.GONE);
        if (dashProgressBar     != null) dashProgressBar.setProgress(pct);
        if (tvProgressTime      != null) tvProgressTime.setText(label);
    }

    public void applySensorValues(String soil, String temp, String ec, String ph) {
        if (tvSensorSoil != null) tvSensorSoil.setText(soil);
        if (tvSensorTemp != null) tvSensorTemp.setText(temp);
        if (tvSensorEC   != null) tvSensorEC.setText(ec);
        if (tvSensorPH   != null) tvSensorPH.setText(ph);
        // 지습 이상 강조
        if (sensorCellSoil != null) {
            try {
                double v = Double.parseDouble(soil.replaceAll("[^0-9.]", ""));
                sensorCellSoil.setBackgroundResource(
                        (v < 20 || v > 40) ? R.drawable.bg_device_card_error : R.drawable.bg_white_card);
            } catch (Exception ignored) {}
        }
    }

    public void showAlert(boolean show, String msg) {
        if (cardAlert == null) return;
        cardAlert.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && tvAlertMsg != null && msg != null) tvAlertMsg.setText(msg);
    }

    private void applyEmergencyStyle(boolean active) {
        // 색상 고정 (항상 error 색) — 표시 여부는 applyStatus에서 제어
        if (btnEmergencyStop == null) return;
        btnEmergencyStop.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.device_bg_error));
    }

    @Override
    public void onBackPressed() {
        float _dp = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout _r = new android.widget.LinearLayout(this);
        _r.setOrientation(android.widget.LinearLayout.VERTICAL);
        _r.setBackgroundColor(0xFFFFFFFF);
        _r.setPadding(Math.round(20*_dp), Math.round(20*_dp), Math.round(20*_dp), Math.round(8*_dp));
        android.widget.TextView _t = new android.widget.TextView(this);
        _t.setText("앱 종료"); _t.setTextSize(20f);
        _t.setTypeface(null, android.graphics.Typeface.BOLD); _t.setTextColor(0xFF1B2E1B);
        _t.setPadding(0,0,0,Math.round(10*_dp)); _r.addView(_t);
        android.view.View _d = new android.view.View(this);
        _d.setBackgroundColor(0xFFE0E0E0);
        _d.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1*_dp)));
        _r.addView(_d);
        android.widget.TextView _m = new android.widget.TextView(this);
        _m.setText("앱을 종료하시겠습니까?"); _m.setTextSize(17f); _m.setTextColor(0xFF555555);
        _m.setPadding(0, Math.round(14*_dp), 0, Math.round(4*_dp)); _r.addView(_m);
        android.widget.LinearLayout _br = new android.widget.LinearLayout(this);
        _br.setGravity(android.view.Gravity.END);
        _br.setPadding(0, Math.round(16*_dp), 0, 0);
        androidx.appcompat.app.AlertDialog[] _ref = {null};
        android.widget.Button _bc = new android.widget.Button(this);
        _bc.setText("취소"); _bc.setTextSize(17f); _bc.setTextColor(0xFF9E9E9E);
        _bc.setBackground(null); _bc.setPadding(Math.round(8*_dp),0,Math.round(8*_dp),0);
        _bc.setOnClickListener(vv -> { if(_ref[0]!=null) _ref[0].dismiss(); });
        android.widget.Button _bx = new android.widget.Button(this);
        _bx.setText("종료"); _bx.setTextSize(17f);
        _bx.setTypeface(null, android.graphics.Typeface.BOLD); _bx.setTextColor(0xFFC62828);
        _bx.setBackground(null); _bx.setPadding(Math.round(12*_dp),0,Math.round(4*_dp),0);
        _bx.setOnClickListener(vv -> finish());
        _br.addView(_bc); _br.addView(_bx); _r.addView(_br);
        androidx.appcompat.app.AlertDialog _dlg =
            new androidx.appcompat.app.AlertDialog.Builder(this).setView(_r).create();
        android.graphics.drawable.GradientDrawable _wbg = new android.graphics.drawable.GradientDrawable();
        _wbg.setColor(0xFFFFFFFF); _wbg.setCornerRadius(16*_dp);
        if (_dlg.getWindow() != null) _dlg.getWindow().setBackgroundDrawable(_wbg);
        _ref[0] = _dlg; _dlg.show();
    }
}

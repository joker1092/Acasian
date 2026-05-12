package com.acasian.iot;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

/**
 * ══════════════════════════════════════════════════════════════════
 *  정보조회 Activity
 *
 *  탭 구성: 센서측정값 / 변화값분석 / 관수주기 / 기기현황
 *
 *  API 연동 준비:
 *    TODO_API 주석 위치에서 실제 API/DB 로드로 교체
 * ══════════════════════════════════════════════════════════════════
 */
public class SensorInfoActivity extends AppCompatActivity {

    // ── 탭 인덱스 ───────────────────────────────────────────────────
    private static final int TAB_SENSOR    = 0;
    private static final int TAB_ANALYSIS  = 1;
    private static final int TAB_CYCLE     = 2;
    private static final int TAB_DEVICE    = 3;

    private int currentTab = TAB_SENSOR;

    // ── 뷰 ─────────────────────────────────────────────────────────
    private View header;
    private TextView[] tabViews = new TextView[4];
    private LinearLayout contentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(
                ContextCompat.getColor(this, R.color.forest_dark));
        setContentView(R.layout.activity_sensor_info);

        applyInsets();
        initViews();
        selectTab(TAB_SENSOR);
    }

    // ── 인셋 ────────────────────────────────────────────────────────
    private void applyInsets() {
        header = findViewById(R.id.infoHeader);
        final int baseTop = header != null ? header.getPaddingTop() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    int sh = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    if (header != null) header.setPadding(
                            header.getPaddingLeft(), baseTop + sh,
                            header.getPaddingRight(), header.getPaddingBottom());
                    return insets;
                });
    }

    // ── 뷰 초기화 ───────────────────────────────────────────────────
    private void initViews() {
        contentContainer = findViewById(R.id.infoContentContainer);

        View btnBack = findViewById(R.id.btnInfoBack);
        if (btnBack instanceof android.widget.ImageView)
            ((android.widget.ImageView) btnBack).setColorFilter(android.graphics.Color.WHITE);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tabViews[0] = findViewById(R.id.tabSensor);
        tabViews[1] = findViewById(R.id.tabAnalysis);
        tabViews[2] = findViewById(R.id.tabCycle);
        tabViews[3] = findViewById(R.id.tabDevice);

        for (int i = 0; i < tabViews.length; i++) {
            final int idx = i;
            if (tabViews[i] != null)
                tabViews[i].setOnClickListener(v -> selectTab(idx));
        }
    }

    // ── 탭 전환 ─────────────────────────────────────────────────────
    private void selectTab(int tab) {
        currentTab = tab;

        // 탭 스타일 적용
        for (int i = 0; i < tabViews.length; i++) {
            if (tabViews[i] == null) continue;
            if (i == tab) {
                tabViews[i].setBackgroundResource(R.drawable.bg_nav_selected);
                tabViews[i].setTextColor(ContextCompat.getColor(this, R.color.moss));
            } else {
                tabViews[i].setBackgroundResource(0);
                tabViews[i].setTextColor(ContextCompat.getColor(this, R.color.inactive_gray));
            }
        }

        // 콘텐츠 교체 (FRAG 역할을 동적 뷰로 구현)
        if (contentContainer != null) {
            contentContainer.removeAllViews();
            switch (tab) {
                case TAB_SENSOR:   renderSensorTab();   break;
                case TAB_ANALYSIS: renderAnalysisTab(); break;
                case TAB_CYCLE:    renderCycleTab();    break;
                case TAB_DEVICE:   renderDeviceTab();   break;
            }
        }
    }

    // ── 센서 측정값 탭 ──────────────────────────────────────────────
    private void renderSensorTab() {
        if (AppConfig.getInstance().isDevMode()) {
            // DEV_MODE: 더미 데이터 표시
            // TODO_API: 실제 연동 시 API 호출로 교체 (문서에 없음 → 서버팀 확인 필요)
            double[][] demoSoil = {{32},{28},{35},{30},{25},{38},{32}};
            renderSensorCard("지습 (토양수분)", "%", demoSoil, 20.0, 40.0, "최근 7일");
            double[][] demoTemp = {{21},{22},{20},{23},{21},{19},{22}};
            renderSensorCard("지온 (토양온도)", "°C", demoTemp, 10.0, 45.0, "최근 7일");
            double[][] demoEC = {{1.8},{2.0},{1.7},{2.1},{1.9},{1.6},{1.8}};
            renderSensorCard("EC (전기전도도)", "dS/m", demoEC, 0.0, 3.0, "최근 7일");
            double[][] demoPH = {{6.5},{6.3},{6.7},{6.4},{6.6},{6.5},{6.4}};
            renderSensorCard("pH (산도)", "", demoPH, 6.0, 7.0, "최근 7일");
        } else {
            // 상용 모드: API 연동 전 — 데이터 준비중
            addPlaceholderCard("— 데이터 준비중 —");
        }
    }

    private void renderSensorCard(String title, String unit,
                                   double[][] values, double min, double max, String period) {
        View card = getLayoutInflater().inflate(
                R.layout.item_sensor_data_card, contentContainer, false);

        TextView tvTitle  = card.findViewById(R.id.tvSensorCardTitle);
        TextView tvPeriod = card.findViewById(R.id.tvSensorCardPeriod);
        TextView tvRange  = card.findViewById(R.id.tvSensorCardRange);
        TextView tvCurrent= card.findViewById(R.id.tvSensorCardCurrent);
        TextView tvStatus = card.findViewById(R.id.tvSensorCardStatus);

        if (tvTitle  != null) tvTitle.setText(title);
        if (tvPeriod != null) tvPeriod.setText(period);
        if (tvRange  != null) tvRange.setText(
                String.format(Locale.getDefault(), "관리범위: %.0f~%.0f%s", min, max, unit));

        double latest = values[values.length - 1][0];
        if (tvCurrent != null)
            tvCurrent.setText(String.format(Locale.getDefault(), "%.1f%s", latest, unit));

        boolean warn = latest < min || latest > max;
        if (tvStatus != null) {
            tvStatus.setText(warn ? "범위 이탈" : "정상");
            tvStatus.setBackgroundResource(warn
                    ? R.drawable.bg_badge_warn : R.drawable.bg_badge_ok);
            tvStatus.setTextColor(ContextCompat.getColor(this,
                    warn ? R.color.device_accent_error : R.color.device_accent_running));
        }

        contentContainer.addView(card);
    }

    // ── 변화값 분석 탭 ──────────────────────────────────────────────
    private void renderAnalysisTab() {
        if (AppConfig.getInstance().isDevMode()) {
            // DEV_MODE: 더미 텍스트 표시
            // TODO_API: 실제 연동 시 API 호출로 교체
            View card = buildInfoCard("변화값 분석",
                    "관수 전후 환경지수 변화를 분석합니다.\n\n"
                    + "• 지습: +8% (관수 후 평균 증가)\n"
                    + "• 지온: -2°C (관수 후 평균 감소)\n"
                    + "• EC: -0.3 dS/m (관수 후 평균 감소)\n"
                    + "• pH: +0.2 (관수 후 평균 증가)");
            contentContainer.addView(card);
        } else {
            addPlaceholderCard("— 데이터 준비중 —");
        }
    }

    // ── 관수주기 탭 ──────────────────────────────────────────────────
    private void renderCycleTab() {
        if (AppConfig.getInstance().isDevMode()) {
            // DEV_MODE: 더미 텍스트 표시
            // TODO_API: getSchedule API 연동 (기록 탭 참고)
            View card = buildInfoCard("사용자 관수주기 데이터",
                    "이번 달 관수 현황\n\n"
                    + "• 총 관수 횟수: 14회\n"
                    + "• 총 관수 시간: 7시간 20분\n"
                    + "• 평균 관수 시간: 31분/회\n\n"
                    + "최근 관수 기록은 '기록' 탭에서 확인하세요.");
            contentContainer.addView(card);
        } else {
            addPlaceholderCard("— 데이터 준비중 —");
        }
    }

    // ── 기기현황 탭 ──────────────────────────────────────────────────
    private void renderDeviceTab() {
        if (AppConfig.getInstance().isDevMode()) {
            // DEV_MODE: 더미 데이터 표시
            // TODO_API: reqNodeStatus API로 교체 (이미 ApiService에 구현됨)
            String[][] devices = {
                {"컨트롤박스 #1", "밸브 10개", "ON", "정상"},
                {"컨트롤박스 #2", "밸브 3개", "ON", "정상"},
                {"컨트롤박스 #3", "밸브 2개", "OFF", "점검 필요"},
            };
            for (String[] d : devices) {
                View row = getLayoutInflater().inflate(
                        R.layout.item_device_status_row, contentContainer, false);
                TextView tvName   = row.findViewById(R.id.tvDeviceStatusName);
                TextView tvDesc   = row.findViewById(R.id.tvDeviceStatusDesc);
                TextView tvPower  = row.findViewById(R.id.tvDeviceStatusPower);
                TextView tvStatus = row.findViewById(R.id.tvDeviceStatusBadge);
                if (tvName   != null) tvName.setText(d[0]);
                if (tvDesc   != null) tvDesc.setText(d[1]);
                if (tvPower  != null) tvPower.setText(d[2]);
                if (tvStatus != null) {
                    tvStatus.setText(d[3]);
                    boolean err = "점검 필요".equals(d[3]);
                    tvStatus.setBackgroundResource(err
                            ? R.drawable.bg_badge_warn : R.drawable.bg_badge_ok);
                    tvStatus.setTextColor(ContextCompat.getColor(this,
                            err ? R.color.device_accent_error : R.color.device_accent_running));
                }
                contentContainer.addView(row);
            }
        } else {
            // 상용 모드: ZoneStore에서 실제 기기 목록 표시
            // TODO_API: reqNodeStatus API 호출 후 실제 상태 반영
            java.util.List<com.acasian.iot.ZoneStore.ZoneInfo> zones =
                    com.acasian.iot.ZoneStore.getInstance().getZones();
            if (zones.isEmpty()) {
                addPlaceholderCard("— 데이터 준비중 —");
                return;
            }
             for (com.acasian.iot.ZoneStore.ZoneInfo zone : zones) {
                 View row = getLayoutInflater().inflate(
                         R.layout.item_device_status_row, contentContainer, false);
                 TextView tvName   = row.findViewById(R.id.tvDeviceStatusName);
                 TextView tvDesc   = row.findViewById(R.id.tvDeviceStatusDesc);
                 TextView tvPower  = row.findViewById(R.id.tvDeviceStatusPower);
                 TextView tvStatus = row.findViewById(R.id.tvDeviceStatusBadge);
                 if (tvName != null) tvName.setText(zone.name);
                 if (tvDesc != null) tvDesc.setText("밸브 " + zone.nodes.size() + "개");

                 // farminfo의 initialValveStatus 기준 상태 집계
                 // 1=관수중, 2=멈춤, 3=OFF, 9=점검
                 boolean anyRunning = false, anyCheck = false;
                 for (com.acasian.iot.ZoneStore.NodeInfo node : zone.nodes) {
                     if (node.initialValveStatus == 9) anyCheck  = true;
                     if (node.initialValveStatus == 1) anyRunning = true;
                 }
                 if (tvPower != null) tvPower.setText(anyRunning ? "ON" : "OFF");
                 if (tvStatus != null) {
                     // TODO_API: reqNodeStatus 호출 후 실시간 상태로 교체
                     if (anyCheck) {
                         tvStatus.setText("점검 필요");
                         tvStatus.setBackgroundResource(R.drawable.bg_badge_warn);
                         tvStatus.setTextColor(ContextCompat.getColor(this, R.color.device_accent_error));
                     } else if (anyRunning) {
                         tvStatus.setText("관수중");
                         tvStatus.setBackgroundResource(R.drawable.bg_badge_ok);
                         tvStatus.setTextColor(ContextCompat.getColor(this, R.color.device_accent_running));
                     } else {
                         tvStatus.setText("정상");
                         tvStatus.setBackgroundResource(R.drawable.bg_badge_ok);
                         tvStatus.setTextColor(ContextCompat.getColor(this, R.color.device_accent_running));
                     }
                 }
                 contentContainer.addView(row);
             }
        }
    }

    // ── 헬퍼: 준비중/정보없음 카드 ─────────────────────────────────
    private void addPlaceholderCard(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(32, 64, 32, 64);
        contentContainer.addView(tv);
    }

    // ── 헬퍼: 정보 카드 생성 ────────────────────────────────────────
    private View buildInfoCard(String title, String content) {
        View card = getLayoutInflater().inflate(
                R.layout.item_sensor_data_card, contentContainer, false);
        TextView tvTitle = card.findViewById(R.id.tvSensorCardTitle);
        TextView tvRange = card.findViewById(R.id.tvSensorCardRange);
        View statusView  = card.findViewById(R.id.tvSensorCardStatus);
        View currentView = card.findViewById(R.id.tvSensorCardCurrent);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvRange != null) tvRange.setText(content);
        if (statusView  != null) statusView.setVisibility(View.GONE);
        if (currentView != null) currentView.setVisibility(View.GONE);
        return card;
    }
}

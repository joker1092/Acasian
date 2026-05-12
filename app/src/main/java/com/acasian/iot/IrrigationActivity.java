package com.acasian.iot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.acasian.iot.network.ApiService;
import com.acasian.iot.repository.ValveRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 관수작업 Activity v3 — 일시정지/재개 포함
 *
 * 밸브 상태 3종:
 *   IDLE    → 대기      : ▶ 시작
 *   RUNNING → 관수중    : ⏸ 일시정지 | ■ 중지
 *   PAUSED  → 일시정지  : ▶ 재개     | ■ 중지
 *
 * API command: 1=시작/재개  2=일시정지  3=종료
 */
public class IrrigationActivity extends AppCompatActivity {

    enum ValveState {
        IDLE,           // 대기
        RUNNING,        // 관수중
        PAUSED,         // 일시정지
        PENDING_START,  // 시작 명령 전송 중 (서버 응답 대기)
        PENDING_PAUSE,  // 일시정지 명령 전송 중
        PENDING_OFF,    // 종료 명령 전송 중
        ERROR           // 재시도 후에도 실패 — 장치 오류 상태
    }

    private static class GwData {
        String telNo, name;
        List<ValveData> valves;
        GwData(String t, String n, List<ValveData> v) { telNo=t; name=n; valves=v; }
    }
    private static class ValveData {
        String id, name, zone, nodeId;
        ValveState state;
        ValveState prevState;  // 실패 시 복원용
        int        retryCount; // 재시도 횟수 (최대 1회)
        ValveData(String i, String n, String z, ValveState s) {
            id=i; name=n; zone=z; state=s; prevState=s; nodeId=i; retryCount=0;
        }
    }

    private List<GwData> gatewayList = new ArrayList<>();
    private int selectedGwIndex = 0;
    private ValveRepository valveRepo;

    // ── 1분 주기 상태 갱신 ──────────────────────────────────────
    private static final long POLL_INTERVAL_MS = 60_000L;
    private final android.os.Handler pollHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!AppConfig.getInstance().isDevMode()) {
                refreshAllValveStatus();
            }
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private View         irrigHeader;
    private TextView     tvGwName, tvAllStatus, tvRunningStatus;
    private LinearLayout valveContainer;
    private View         sectionProgress;
    private CardView     btnAllStart, btnAllPause, btnAllResume, btnAllStop, btnEmergency;

    @Override
    protected void onResume() {
        super.onResume();
        // 백그라운드 복귀 시 즉시 1회 갱신 후 폴링 시작
        if (!AppConfig.getInstance().isDevMode()) {
            refreshAllValveStatus();
        }
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 백그라운드 진입 시 폴링 중단
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
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.forest_dark));
        setContentView(R.layout.activity_irrigation);
        // 개발 모드: 더미 데이터 주입
        // 상용 모드: ZoneStore는 HomeDashboard에서 farminfo API로 채워짐
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
        valveRepo = new ValveRepository(this);
        applyInsets(); initGatewayList(); initViews(); setupGlobalButtons(); renderValveList();

        // 실시간 밸브 상태 갱신은 onResume에서 처리 (1분 폴링)
    }

    private void applyInsets() {
        irrigHeader = findViewById(R.id.irrigHeader);
        final int baseTop = irrigHeader != null ? irrigHeader.getPaddingTop() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    int sh  = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                    if (irrigHeader != null)
                        irrigHeader.setPadding(irrigHeader.getPaddingLeft(), baseTop+sh,
                                irrigHeader.getPaddingRight(), irrigHeader.getPaddingBottom());
                    View root = findViewById(android.R.id.content);
                    if (root instanceof android.view.ViewGroup) {
                        android.view.ViewGroup vg = (android.view.ViewGroup) root;
                        if (vg.getChildCount()>0 && vg.getChildAt(0) instanceof android.view.ViewGroup) {
                            android.view.ViewGroup mv = (android.view.ViewGroup) vg.getChildAt(0);
                            int last = mv.getChildCount()-1;
                            if (last>=0) {
                                View ba = mv.getChildAt(last);
                                int bp = Math.round(24*getResources().getDisplayMetrics().density);
                                ba.setPadding(ba.getPaddingLeft(),ba.getPaddingTop(),ba.getPaddingRight(),bp+nav);
                            }
                        }
                    }
                    return insets;
                });
    }

    /**
     * 게이트웨이 목록 초기화
     * - DevMode : 더미 데이터 (컨트롤박스 2개, 밸브 10/3개)
     * - 상용 모드: ZoneStore(farminfo 응답)에서 읽어옴
     *              valveStatus: 1=관수중, 2=멈춤, 3=OFF, 0=미설치 → IDLE
     */
    private void initGatewayList() {
        gatewayList.clear();

        if (AppConfig.getInstance().isDevMode()) {
            // ── 더미 데이터 ──────────────────────────────────────────
            List<ValveData> gw1 = new ArrayList<>();
            for (int i=1; i<=10; i++) {
                ValveState s = i==1 ? ValveState.RUNNING : i==2 ? ValveState.PAUSED : ValveState.IDLE;
                gw1.add(new ValveData("a_v"+i,"토출 밸브 "+i,"A-"+String.format("%02d",i)+" 구역",s));
            }
            List<ValveData> gw2 = new ArrayList<>();
            gw2.add(new ValveData("b_v1","토출 밸브 1","B-01 구역",ValveState.IDLE));
            gw2.add(new ValveData("b_v2","토출 밸브 2","B-02 구역",ValveState.RUNNING));
            gw2.add(new ValveData("b_v3","토출 밸브 3","B-03 구역",ValveState.IDLE));
            gatewayList.add(new GwData(DemoData.ZONE_TEL_1,"컨트롤박스 #1",gw1));
            gatewayList.add(new GwData(DemoData.ZONE_TEL_2,"컨트롤박스 #2",gw2));
            return;
        }

        // ── 상용 모드: ZoneStore → GwData 변환 ──────────────────────
        for (ZoneStore.ZoneInfo zone : ZoneStore.getInstance().getZones()) {
            List<ValveData> valves = new ArrayList<>();
            for (ZoneStore.NodeInfo node : zone.nodes) {
                // farminfo 응답의 valveStatus → ValveState 변환
                // 1=관수중, 2=멈춤(일시정지), 3=OFF, 0/기타=IDLE
                ValveState initState;
                switch (node.initialValveStatus) {
                    case 1:  initState = ValveState.RUNNING; break;
                    case 2:  initState = ValveState.PAUSED;  break;
                    default: initState = ValveState.IDLE;    break;
                }
                valves.add(new ValveData(
                        node.nodeId,
                        node.name,
                        "",   // 구역명 없음 — 서버 미제공, tvValveZone 숨김 처리
                        initState));
            }
            gatewayList.add(new GwData(zone.telNo, zone.name, valves));
        }
    }

    private void initViews() {
        tvGwName        = findViewById(R.id.tvIrrigGwName);
        tvAllStatus     = findViewById(R.id.tvIrrigAllStatus);
        valveContainer  = findViewById(R.id.irrigValveContainer);
        sectionProgress = findViewById(R.id.irrigProgressSection);
        tvRunningStatus = findViewById(R.id.tvIrrigRunningStatus);
        btnAllStart     = findViewById(R.id.btnIrrigAllStart);
        btnAllPause     = findViewById(R.id.btnIrrigAllPause);
        btnAllResume    = findViewById(R.id.btnIrrigAllResume);
        btnAllStop      = findViewById(R.id.btnIrrigAllStop);
        btnEmergency    = findViewById(R.id.btnIrrigEmergency);
        View btnBack = findViewById(R.id.btnIrrigBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        View btnGw = findViewById(R.id.btnIrrigGwSelect);
        if (btnGw != null) btnGw.setOnClickListener(v -> showGatewayPicker());
    }

    private void setupGlobalButtons() {
        // ▶ 전체 시작 — 시간 선택 후 Z(메인함) 한 번으로 서버에 전달
        if (btnAllStart != null) btnAllStart.setOnClickListener(v -> {
            GwData gw = gatewayList.get(selectedGwIndex);
            List<ValveData> idle = new ArrayList<>();
            for (ValveData vd : gw.valves) if (vd.state==ValveState.IDLE) idle.add(vd);
            if (idle.isEmpty()) { Toast.makeText(this,"대기 중인 밸브가 없습니다.",Toast.LENGTH_SHORT).show(); return; }
            // 전체 시작: 장비경고 확인 → 시간 선택 → Z 한 번 전송 (서버가 각 노드에 전달)
            checkAndStartMain("전체 밸브 ("+idle.size()+"개)", gw);
        });
        // ⏸ 전체 일시정지 — Z(메인함) CMD_PAUSE
        if (btnAllPause != null) btnAllPause.setOnClickListener(v -> {
            GwData gw = gatewayList.get(selectedGwIndex);
            sendMainCommand(gw, ApiService.DeviceRequest.CMD_PAUSE, 0);
        });
        // ▶ 전체 재개 — 시간 선택 후 Z(메인함) 한 번으로 서버에 전달
        if (btnAllResume != null) btnAllResume.setOnClickListener(v -> {
            GwData gw = gatewayList.get(selectedGwIndex);
            boolean anyPaused = false;
            for (ValveData vd : gw.valves) if (vd.state==ValveState.PAUSED) { anyPaused=true; break; }
            if (!anyPaused) return;
            // 전체 재개: 시간 선택 → Z 한 번 전송
            checkAndStartMain("전체 재개", gw);
        });
        // ■ 전체 중지 — Z(메인함) CMD_OFF
        if (btnAllStop != null) btnAllStop.setOnClickListener(v ->
            showStopConfirm("전체 밸브를 중지하시겠습니까?", () -> {
                GwData gw = gatewayList.get(selectedGwIndex);
                sendMainCommand(gw, ApiService.DeviceRequest.CMD_OFF, 0);
            })
        );
        // ■ 긴급 중지
        if (btnEmergency != null) btnEmergency.setOnClickListener(v -> showEmergencyStopDialog());
    }

    /**
     * 전체 제어용 — Z(메인함) nodeId로 명령 전송.
     * 성공 시 gw.valves 전체 상태 일괄 갱신.
     */
    private void sendMainCommand(GwData gw, String cmd, int vtimeMin) {
        ValveRepository.ValveCallback cb = new ValveRepository.ValveCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    ValveState next = cmd.equals(ApiService.DeviceRequest.CMD_PAUSE)
                            ? ValveState.PAUSED
                            : cmd.equals(ApiService.DeviceRequest.CMD_OFF)
                                ? ValveState.IDLE
                                : ValveState.RUNNING;
                    for (ValveData vd : gw.valves) vd.state = next;
                    renderValveList();
                });
            }
            @Override public void onFailure(String e) {
                runOnUiThread(() -> Toast.makeText(
                        IrrigationActivity.this, "명령 전송 실패", Toast.LENGTH_SHORT).show());
            }
        };
        if (cmd.equals(ApiService.DeviceRequest.CMD_START))
            valveRepo.startValve(gw.telNo, "Z", ApiService.DeviceRequest.TYPE_MAIN, vtimeMin, cb);
        else if (cmd.equals(ApiService.DeviceRequest.CMD_PAUSE))
            valveRepo.pauseValve(gw.telNo, "Z", ApiService.DeviceRequest.TYPE_MAIN, cb);
        else
            valveRepo.stopValve(gw.telNo, "Z", ApiService.DeviceRequest.TYPE_MAIN, cb);
    }

    private void showGatewayPicker() {
        String[] names = new String[gatewayList.size()];
        for (int i=0; i<gatewayList.size(); i++) names[i] = gatewayList.get(i).name;
        final int[] chosen = {selectedGwIndex};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("게이트웨이 선택")
                .setSingleChoiceItems(names, selectedGwIndex, (d,w)->chosen[0]=w)
                .setPositiveButton("선택",(d,w)->{ selectedGwIndex=chosen[0]; renderValveList(); })
                .setNegativeButton("취소",null).show();
    }

    private void renderValveList() {
        if (valveContainer==null || gatewayList.isEmpty()) return;
        GwData gw = gatewayList.get(selectedGwIndex);
        if (tvGwName!=null) tvGwName.setText(gw.name);

        boolean anyRunning=false, anyPaused=false, anyActive=false;
        for (ValveData vd : gw.valves) {
            if (vd.state==ValveState.RUNNING)       { anyRunning=true; anyActive=true; }
            if (vd.state==ValveState.PAUSED)        { anyPaused=true;  anyActive=true; }
            // PENDING / ERROR 상태도 active로 집계 (전체 중지 버튼 노출 유지)
            if (vd.state==ValveState.PENDING_START
             || vd.state==ValveState.PENDING_PAUSE
             || vd.state==ValveState.PENDING_OFF
             || vd.state==ValveState.ERROR)         { anyActive=true; }
        }

        sv(btnAllStart,  !anyRunning && !anyPaused);
        sv(btnAllPause,  anyRunning);
        sv(btnAllResume, anyPaused && !anyRunning);
        sv(btnAllStop,   anyActive);

        if (tvAllStatus!=null) {
            if (anyRunning && anyPaused) { tvAllStatus.setText("관수중 · 일시정지 혼합"); tvAllStatus.setTextColor(0xFF854F0B); }
            else if (anyRunning)         { tvAllStatus.setText("관수 진행 중");          tvAllStatus.setTextColor(0xFF1B5E20); }
            else if (anyPaused)          { tvAllStatus.setText("⏸ 일시정지 중");         tvAllStatus.setTextColor(0xFF854F0B); }
            else                         { tvAllStatus.setText("대기 중");               tvAllStatus.setTextColor(0xFF5A7A5A); }
        }
        if (sectionProgress!=null) sectionProgress.setVisibility(anyActive?View.VISIBLE:View.GONE);
        if (tvRunningStatus!=null) tvRunningStatus.setText(anyRunning ? "관수 진행 중" : "⏸ 일시정지 중");

        valveContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (ValveData vd : gw.valves) {
            View row = inf.inflate(R.layout.item_irrigation_valve_row, valveContainer, false);
            bindValveRow(row, vd, gw);
            valveContainer.addView(row);
        }
    }

    private void bindValveRow(View row, ValveData vd, GwData gw) {
        TextView tvName   = row.findViewById(R.id.tvValveName);
        TextView tvZone   = row.findViewById(R.id.tvValveZone);
        TextView tvState  = row.findViewById(R.id.tvValveState);
        View     dot      = row.findViewById(R.id.dotValve);
        TextView bStart   = row.findViewById(R.id.btnValveStart);
        TextView bPause   = row.findViewById(R.id.btnValvePause);
        TextView bResume  = row.findViewById(R.id.btnValveResume);
        TextView bStop    = row.findViewById(R.id.btnValveStop);
        android.widget.ProgressBar pbPending = row.findViewById(R.id.spinnerValvePending);

        if (tvName!=null) tvName.setText(vd.name);
        if (tvZone!=null) {
            if (vd.zone == null || vd.zone.isEmpty()) {
                tvZone.setVisibility(android.view.View.GONE);
            } else {
                tvZone.setVisibility(android.view.View.VISIBLE);
                tvZone.setText(vd.zone);
            }
        }

        switch (vd.state) {
            case ERROR:
                // 오류 상태: 빨간 배경 + 오류 배지 + 재시도/점검 버튼
                row.setBackgroundColor(0xFFFFF0F0);
                dot(dot, 0xFFC62828);
                sv(bPause,  false);
                sv(bResume, false);
                sv(pbPending, false);
                badge(tvState, "장치 오류", R.drawable.bg_badge_error, 0xFFC62828);

                // ↻ 재시도 버튼 — bStart 재활용
                if (bStart != null) {
                    bStart.setVisibility(android.view.View.VISIBLE);
                    bStart.setText("↻");
                    bStart.setTextColor(0xFFC62828);
                    bStart.setBackgroundResource(R.drawable.bg_badge_error);
                    bStart.setOnClickListener(v -> {
                        String retryCmd = vd.prevState == ValveState.IDLE
                                ? ApiService.DeviceRequest.CMD_OFF
                                : vd.prevState == ValveState.RUNNING
                                        ? ApiService.DeviceRequest.CMD_START
                                        : ApiService.DeviceRequest.CMD_PAUSE;
                        sendValveCommand(gw, vd, retryCmd, 0);
                    });
                }

                // 점검 버튼 — bStop 재활용 (서버에서 실제 상태 재조회)
                if (bStop != null) {
                    bStop.setVisibility(android.view.View.VISIBLE);
                    bStop.setText("점검");
                    bStop.setTextSize(9f);
                    bStop.setTextColor(0xFF5A5A5A);
                    bStop.setBackgroundResource(R.drawable.bg_badge_neutral);
                    bStop.setOnClickListener(v -> checkValveStatus(gw, vd));
                }
                break;
            case PENDING_START:
            case PENDING_PAUSE:
            case PENDING_OFF:
                // 처리중: 주황 배경 + 스피너 + 처리중 배지, 모든 버튼 숨김
                row.setBackgroundColor(0xFFFFF8E1);
                dot(dot, 0xFFEF9F27);
                sv(bStart,  false);
                sv(bPause,  false);
                sv(bResume, false);
                sv(bStop,   false);
                sv(pbPending, true);
                String pendingLabel = vd.state==ValveState.PENDING_START ? "시작 중..."
                                    : vd.state==ValveState.PENDING_PAUSE ? "일시정지 중..."
                                    : "종료 중...";
                badge(tvState, pendingLabel, R.drawable.bg_badge_warn, 0xFF854F0B);
                break;
            case RUNNING:
                badge(tvState,"관수중",R.drawable.bg_badge_ok,0xFF1B5E20);
                dot(dot,0xFF4A8C4F); row.setBackgroundColor(0xFFF6FBF6);
                sv(bStart,false); sv(bPause,true); sv(bResume,false); sv(bStop,true); sv(pbPending,false);
                if (bPause!=null) bPause.setOnClickListener(v->
                    sendValveCommand(gw,vd,ApiService.DeviceRequest.CMD_PAUSE,0));
                if (bStop!=null)  bStop.setOnClickListener(v->
                    showStopConfirm(vd.name+"을(를) 중지하시겠습니까?",()->
                        sendValveCommand(gw,vd,ApiService.DeviceRequest.CMD_OFF,0)));
                break;
            case PAUSED:
                badge(tvState,"일시정지",R.drawable.bg_badge_warn,0xFF854F0B);
                dot(dot,0xFFEF9F27); row.setBackgroundColor(0xFFFFFDF7);
                sv(bStart,false); sv(bPause,false); sv(bResume,true); sv(bStop,true); sv(pbPending,false);
                if (bResume!=null) bResume.setOnClickListener(v->
                    // 재개: 시간 설정 다이얼로그 표시 후 CMD_START 전송
                    checkAndStart(vd.name+" 재개",
                        java.util.Collections.singletonList(vd), ApiService.DeviceRequest.CMD_START));
                if (bStop!=null)   bStop.setOnClickListener(v->
                    showStopConfirm(vd.name+"을(를) 중지하시겠습니까?",()->
                        sendValveCommand(gw,vd,ApiService.DeviceRequest.CMD_OFF,0)));
                break;
            default:
                badge(tvState,"대기",R.drawable.bg_badge_neutral,0xFF616161);
                dot(dot,0xFFBDBDBD); row.setBackgroundColor(0xFFFFFFFF);
                sv(bStart,true); sv(bPause,false); sv(bResume,false); sv(bStop,false); sv(pbPending,false);
                if (bStart!=null) bStart.setOnClickListener(v->
                    checkAndStart(vd.name,
                        java.util.Collections.singletonList(vd), ApiService.DeviceRequest.CMD_START));
        }
    }

    /** 외부 호출 진입점 — PENDING 상태 세팅 + retryCount 초기화 후 내부 전송 */
    private void sendValveCommand(GwData gw, ValveData vd, String cmd, int vtimeMin) {
        vd.prevState   = vd.state;
        vd.retryCount  = 0;
        if      (cmd.equals(ApiService.DeviceRequest.CMD_START)) vd.state = ValveState.PENDING_START;
        else if (cmd.equals(ApiService.DeviceRequest.CMD_PAUSE)) vd.state = ValveState.PENDING_PAUSE;
        else                                                      vd.state = ValveState.PENDING_OFF;
        renderValveList();
        sendValveCommandInternal(gw, vd, cmd, vtimeMin);
    }

    /** 실제 API 전송 — 재시도 시에도 이 메서드를 호출 */
    private void sendValveCommandInternal(GwData gw, ValveData vd, String cmd, int vtimeMin) {

        ValveRepository.ValveCallback cb = new ValveRepository.ValveCallback() {
            @Override public void onSuccess() {
                runOnUiThread(()->{
                    // 서버 OK → 실제 상태로 확정
                    if      (cmd.equals(ApiService.DeviceRequest.CMD_PAUSE)) vd.state = ValveState.PAUSED;
                    else if (cmd.equals(ApiService.DeviceRequest.CMD_OFF))   vd.state = ValveState.IDLE;
                    else                                                       vd.state = ValveState.RUNNING;
                    renderValveList();
                });
            }
            @Override public void onFailure(String e) {
                runOnUiThread(()->{
                    if (vd.retryCount < 1) {
                        // 1회 자동 재시도
                        vd.retryCount++;
                        // PENDING 상태 유지 (스피너 계속 표시)
                        sendValveCommandInternal(gw, vd, cmd, vtimeMin);
                    } else {
                        // 재시도도 실패 → ERROR 상태로 확정
                        vd.retryCount = 0;
                        vd.state = ValveState.ERROR;
                        renderValveList();
                    }
                });
            }
        };
        if (cmd.equals(ApiService.DeviceRequest.CMD_START))
            valveRepo.startValve(gw.telNo, vd.nodeId, ApiService.DeviceRequest.TYPE_SERVO, vtimeMin, cb);
        else if (cmd.equals(ApiService.DeviceRequest.CMD_PAUSE))
            valveRepo.pauseValve(gw.telNo, vd.nodeId, ApiService.DeviceRequest.TYPE_SERVO, cb);
        else
            valveRepo.stopValve(gw.telNo, vd.nodeId, ApiService.DeviceRequest.TYPE_SERVO, cb);
    }

    // ── 점검 버튼 — 서버에서 실제 밸브 상태 재조회 후 ERROR 해제 ──
    /** 진입 시 모든 게이트웨이의 밸브 상태를 reqNodeStatus로 갱신 */
    private void refreshAllValveStatus() {
        for (GwData gw : gatewayList) {
            valveRepo.requestNodeStatus(gw.telNo,
                new com.acasian.iot.repository.ValveRepository.NodeStatusCallback() {
                    @Override public void onSuccess(ApiService.NodeStatusResponse response) {
                        runOnUiThread(() -> {
                            for (ValveData vd : gw.valves) {
                                Integer status = findNodeStatus(response, vd.nodeId);
                                if (status == null) continue;
                                switch (status) {
                                    case 1:  vd.state = ValveState.RUNNING; break;
                                    case 2:  vd.state = ValveState.PAUSED;  break;
                                    case 9:  vd.state = ValveState.ERROR;   break;
                                    default: vd.state = ValveState.IDLE;    break;
                                }
                            }
                            renderValveList();
                        });
                    }
                    @Override public void onFailure(String errorMsg) {
                        android.util.Log.w("IrrigationActivity",
                                "상태 갱신 실패 [" + gw.telNo + "]: " + errorMsg);
                    }
                });
        }
    }

    /**
     * reqNodeStatus API 호출 → 응답의 nodeStatus로 ERROR 밸브 상태 복원
     *
     * nodeStatus 값:
     *   1 = 관수중  → RUNNING
     *   2 = 멈춤    → PAUSED
     *   3 = OFF     → IDLE
     *   4 = 벤트중  → IDLE (벤트 완료로 처리)
     *   9 = 점검    → ERROR 유지 (장치가 아직 점검 상태)
     *
     * TODO: 점검 전용 API(예: 장치 리셋/점검 완료 신호) 가 추가되면
     *       해당 API를 먼저 호출 후 reqNodeStatus로 결과 확인하는 방식으로 교체
     */
    private void checkValveStatus(GwData gw, ValveData vd) {
        // 점검 중 표시 — PENDING_OFF 재활용 (스피너 + "점검 중..." 배지)
        vd.state = ValveState.PENDING_OFF;
        renderValveList();

        // TODO_API: DevMode 시 더미 응답으로 IDLE 복원
        if (AppConfig.getInstance().isDevMode()) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> {
                        vd.state    = ValveState.IDLE;
                        vd.retryCount = 0;
                        renderValveList();
                        android.widget.Toast.makeText(this,
                                vd.name + " 점검 완료 — 대기 상태로 복원됐습니다.",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }, 1200);
            return;
        }

        // 실제 API 호출
        valveRepo.requestNodeStatus(gw.telNo,
                new com.acasian.iot.repository.ValveRepository.NodeStatusCallback() {

                    @Override
                    public void onSuccess(ApiService.NodeStatusResponse response) {
                        runOnUiThread(() -> {
                            // 응답에서 해당 밸브 nodeId 찾기
                            Integer nodeStatus = findNodeStatus(response, vd.nodeId);

                            if (nodeStatus == null) {
                                // 응답에 해당 노드 없음 → ERROR 유지
                                vd.state = ValveState.ERROR;
                                renderValveList();
                                android.widget.Toast.makeText(IrrigationActivity.this,
                                        vd.name + " 상태 확인 실패: 노드를 찾을 수 없습니다.",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                return;
                            }

                            switch (nodeStatus) {
                                case 1:  vd.state = ValveState.RUNNING; break; // 관수중
                                case 2:  vd.state = ValveState.PAUSED;  break; // 멈춤
                                case 3:  // OFF
                                case 4:  vd.state = ValveState.IDLE;    break; // 벤트중→IDLE
                                case 9:  // 점검 — 장치가 아직 점검 상태
                                    vd.state = ValveState.ERROR;
                                    android.widget.Toast.makeText(IrrigationActivity.this,
                                            vd.name + " 장치가 점검(9) 상태입니다.\n장치를 확인해 주세요.",
                                            android.widget.Toast.LENGTH_LONG).show();
                                    renderValveList();
                                    return;
                                default: vd.state = ValveState.IDLE;    break;
                            }
                            vd.retryCount = 0;
                            renderValveList();
                            if (vd.state != ValveState.ERROR) {
                                android.widget.Toast.makeText(IrrigationActivity.this,
                                        vd.name + " 점검 완료 — " + stateLabel(vd.state) + " 상태로 복원됐습니다.",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        runOnUiThread(() -> {
                            // 상태 조회 자체가 실패 → ERROR 유지
                            vd.state = ValveState.ERROR;
                            renderValveList();
                            android.widget.Toast.makeText(IrrigationActivity.this,
                                    vd.name + " 상태 조회 실패: " + errorMsg,
                                    android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    /** reqNodeStatus 응답에서 특정 nodeId의 valveStatus 추출 */
    private Integer findNodeStatus(ApiService.NodeStatusResponse response, String targetNodeId) {
        if (response == null || response.data == null
                || response.data.nodelist == null) return null;
        for (ApiService.NodeStatusResponse.NodeStatusDetail detail : response.data.nodelist) {
            if (targetNodeId.equals(detail.nodeId)) return detail.valveStatus;
        }
        return null;
    }

    /** ValveState → 사용자 표시 문자열 */
    private String stateLabel(ValveState state) {
        switch (state) {
            case RUNNING: return "관수중";
            case PAUSED:  return "일시정지";
            case IDLE:    return "대기";
            default:      return "알 수 없음";
        }
    }

    // ── 밸브 명령 실패 오류 다이얼로그 ────────────────────────────
    private void showValveErrorDialog(String valveName, String action,
                                      String errorMsg, Runnable onRetry) {
        float dp = getResources().getDisplayMetrics().density;

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);

        // 헤더 (적색)
        android.widget.LinearLayout hdr = new android.widget.LinearLayout(this);
        hdr.setBackgroundColor(0xFFB71C1C);
        hdr.setPadding(Math.round(20*dp), Math.round(16*dp), Math.round(20*dp), Math.round(16*dp));
        hdr.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("명령 전송 실패");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFFFFFFFF);
        hdr.addView(tvTitle);

        android.widget.TextView tvSub = new android.widget.TextView(this);
        tvSub.setText(valveName + " — " + action);
        tvSub.setTextSize(13f);
        tvSub.setTextColor(0xFFEF9A9A);
        tvSub.setPadding(0, Math.round(4*dp), 0, 0);
        hdr.addView(tvSub);
        root.addView(hdr);

        // 본문
        android.widget.LinearLayout body = new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.setPadding(Math.round(20*dp), Math.round(18*dp), Math.round(20*dp), Math.round(8*dp));

        // 오류 원인
        android.widget.TextView tvMsg = new android.widget.TextView(this);
        tvMsg.setText("서버와 통신에 실패했습니다.\n잠시 후 다시 시도해 주세요.");
        tvMsg.setTextSize(16f);
        tvMsg.setTextColor(0xFF1A2030);
        tvMsg.setLineSpacing(0, 1.4f);
        body.addView(tvMsg);

        // 오류 상세 (개발용 — 작게 표시)
        if (errorMsg != null && !errorMsg.isEmpty()) {
            android.widget.TextView tvErr = new android.widget.TextView(this);
            tvErr.setText("오류 내용: " + errorMsg);
            tvErr.setTextSize(11f);
            tvErr.setTextColor(0xFF9E9E9E);
            tvErr.setPadding(0, Math.round(10*dp), 0, 0);
            tvErr.setLineSpacing(0, 1.3f);
            body.addView(tvErr);
        }

        root.addView(body);

        // 버튼 행
        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setGravity(android.view.Gravity.END);
        btnRow.setPadding(Math.round(12*dp), Math.round(8*dp), Math.round(12*dp), Math.round(12*dp));

        androidx.appcompat.app.AlertDialog[] ref = {null};

        // 닫기 버튼
        android.widget.Button btnClose = new android.widget.Button(this);
        btnClose.setText("닫기");
        btnClose.setTextSize(15f);
        btnClose.setTextColor(0xFF9E9E9E);
        btnClose.setBackground(null);
        btnClose.setPadding(Math.round(12*dp), 0, Math.round(12*dp), 0);
        btnClose.setOnClickListener(v -> { if (ref[0] != null) ref[0].dismiss(); });
        btnRow.addView(btnClose);

        // 재시도 버튼
        android.widget.Button btnRetry = new android.widget.Button(this);
        btnRetry.setText("재시도");
        btnRetry.setTextSize(15f);
        btnRetry.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRetry.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable retryBg =
                new android.graphics.drawable.GradientDrawable();
        retryBg.setColor(0xFFB71C1C);
        retryBg.setCornerRadius(8 * dp);
        btnRetry.setBackground(retryBg);
        btnRetry.setPadding(Math.round(20*dp), Math.round(10*dp), Math.round(20*dp), Math.round(10*dp));
        btnRetry.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
            onRetry.run();
        });
        btnRow.addView(btnRetry);

        root.addView(btnRow);

        // 다이얼로그 생성
        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(root).create();
        if (dlg.getWindow() != null) {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setCornerRadius(16 * dp);
            dlg.getWindow().setBackgroundDrawable(bg);
        }
        ref[0] = dlg;
        dlg.show();
    }

    // ── 장비 경고 체크 → 타이머 다이얼로그 진입 ─────────────────
    private void checkAndStart(String label, List<ValveData> targets, String cmd) {
        java.util.List<EquipmentConfig.EquipWarning> warns =
                EquipmentConfig.checkWarnings(this, targets.size());

        if (warns.isEmpty()) {
            showTimerDialog(label, targets, cmd);
            return;
        }
        showEquipWarningDialog(warns, () -> showTimerDialog(label, targets, cmd));
    }

    /**
     * 전체 제어 전용 — 장비경고 확인 후 시간 선택 다이얼로그 → Z(메인함) 한 번 전송.
     * 서버가 프로토콜에 맞게 각 노드에 전달합니다.
     */
    private void checkAndStartMain(String label, GwData gw) {
        java.util.List<EquipmentConfig.EquipWarning> warns =
                EquipmentConfig.checkWarnings(this, gw.valves.size());

        if (warns.isEmpty()) {
            showTimerDialogMain(label, gw);
            return;
        }
        showEquipWarningDialog(warns, () -> showTimerDialogMain(label, gw));
    }

    /**
     * 전체 제어 전용 시간 선택 다이얼로그 — 확인 시 sendMainCommand(Z) 한 번만 호출.
     */
    private void showTimerDialogMain(String label, GwData gw) {
        View tv = LayoutInflater.from(this).inflate(R.layout.dialog_valve_timer, null);
        com.acasian.iot.Calendar.view.WheelTimePickerView pH = tv.findViewById(R.id.pickerTimerHour);
        com.acasian.iot.Calendar.view.WheelTimePickerView pM = tv.findViewById(R.id.pickerTimerMinute);
        TextView tvSum = tv.findViewById(R.id.tvTimerSummary);
        TextView tvLbl = tv.findViewById(R.id.tvTimerTargetLabel);
        android.widget.Button bCan = tv.findViewById(R.id.btnTimerCancel);
        android.widget.Button bCon = tv.findViewById(R.id.btnTimerConfirm);
        if (tvLbl!=null) tvLbl.setText(label);
        final int[] h={0}, m={30};
        if (pH!=null) { pH.setRange(0,5); pH.setWrapSelectorWheel(false); pH.setValue(0);
            pH.setOnValueChangeListener((v,o,n)->{ h[0]=n; updateSummary(tvSum,h[0],m[0]); }); }
        if (pM!=null) { pM.setRange(0,59); pM.setWrapSelectorWheel(true); pM.setValue(30);
            pM.setOnValueChangeListener((v,o,n)->{ m[0]=n; updateSummary(tvSum,h[0],m[0]); }); }
        updateSummary(tvSum,0,30);
        int[] pIds={R.id.btnPreset10,R.id.btnPreset20,R.id.btnPreset30,R.id.btnPreset60,R.id.btnPreset90,R.id.btnPreset120};
        int[] pMins={10,20,30,60,90,120};
        for (int i=0;i<pIds.length;i++) {
            final int mins=pMins[i]; TextView btn=tv.findViewById(pIds[i]);
            if (btn==null) continue;
            btn.setOnClickListener(v->{ h[0]=mins/60; m[0]=mins%60;
                if(pH!=null)pH.setValue(h[0]); if(pM!=null)pM.setValue(m[0]);
                updateSummary(tvSum,h[0],m[0]); });
        }
        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(tv).create();
        if (bCan!=null) bCan.setOnClickListener(v->dlg.dismiss());
        if (bCon!=null) bCon.setOnClickListener(v->{
            int total=h[0]*60+m[0];
            if (total<=0) { Toast.makeText(this,"1분 이상 설정해 주세요.",Toast.LENGTH_SHORT).show(); return; }
            dlg.dismiss();
            // Z(메인함) 한 번 — 서버가 프로토콜에 맞게 각 노드에 전달
            sendMainCommand(gw, ApiService.DeviceRequest.CMD_START, total);
        });
        dlg.show();
    }

    private void showEquipWarningDialog(
            java.util.List<EquipmentConfig.EquipWarning> warns, Runnable onContinue) {

        android.view.View dv = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_equipment_warning, null);

        // 경고 레벨 결정 (하나라도 RED면 RED)
        boolean hasRed = false;
        for (EquipmentConfig.EquipWarning w : warns)
            if (w.level == EquipmentConfig.WarnLevel.RED) { hasRed = true; break; }

        android.widget.TextView tvTitle = dv.findViewById(R.id.tvWarnTitle);
        android.widget.TextView tvLevel = dv.findViewById(R.id.tvWarnLevel);
        android.widget.TextView tvIcon  = dv.findViewById(R.id.tvWarnIcon);

        if (hasRed) {
            if (tvTitle != null) tvTitle.setText("경고 — 관수 시작 전 확인 필요");
            if (tvLevel != null) { tvLevel.setText("경고"); tvLevel.setTextColor(0xFFC62828); }
            if (tvIcon  != null) tvIcon.setBackgroundResource(R.drawable.bg_badge_error);
        } else {
            if (tvTitle != null) tvTitle.setText("주의 — 관수 시작 전 확인 바람");
            if (tvLevel != null) { tvLevel.setText("주의"); tvLevel.setTextColor(0xFF854F0B); }
        }

        // 경고 메시지 목록 동적 추가
        android.widget.LinearLayout container = dv.findViewById(R.id.warnMessageContainer);
        if (container != null) {
            float dp = getResources().getDisplayMetrics().density;
            for (int i = 0; i < warns.size(); i++) {
                EquipmentConfig.EquipWarning w = warns.get(i);

                // 구분선 (두 번째 이후)
                if (i > 0) {
                    android.view.View div = new android.view.View(this);
                    div.setBackgroundColor(0xFFEEEEEE);
                    android.widget.LinearLayout.LayoutParams lp =
                            new android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    Math.round(dp));
                    lp.setMargins(0, Math.round(8*dp), 0, Math.round(8*dp));
                    div.setLayoutParams(lp);
                    container.addView(div);
                }

                // 경고 메시지 행
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.TOP);

                // 색 점
                android.view.View dot = new android.view.View(this);
                android.graphics.drawable.GradientDrawable dotBg =
                        new android.graphics.drawable.GradientDrawable();
                dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dotBg.setColor(w.level == EquipmentConfig.WarnLevel.RED ? 0xFFC62828 : 0xFFEF9F27);
                dot.setBackground(dotBg);
                int dotSize = Math.round(8*dp);
                android.widget.LinearLayout.LayoutParams dotLp =
                        new android.widget.LinearLayout.LayoutParams(dotSize, dotSize);
                dotLp.setMargins(0, Math.round(5*dp), Math.round(8*dp), 0);
                dot.setLayoutParams(dotLp);
                row.addView(dot);

                // 메시지 텍스트
                android.widget.TextView tvMsg = new android.widget.TextView(this);
                tvMsg.setText(w.message);
                tvMsg.setTextSize(14f);
                tvMsg.setTextColor(0xFF333333);
                tvMsg.setLineSpacing(0, 1.4f);
                tvMsg.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                row.addView(tvMsg);

                container.addView(row);
            }
        }

        androidx.appcompat.app.AlertDialog[] ref = {null};

        android.widget.TextView btnCancel   = dv.findViewById(R.id.btnWarnCancel);
        android.view.View       btnContinue = dv.findViewById(R.id.btnWarnContinue);

        if (btnCancel   != null) btnCancel.setOnClickListener(v -> { if (ref[0]!=null) ref[0].dismiss(); });
        if (btnContinue != null) btnContinue.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
            onContinue.run();
        });

        float dp2 = getResources().getDisplayMetrics().density;
        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(dv).create();
        if (dlg.getWindow() != null) {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setCornerRadius(16 * dp2);
            dlg.getWindow().setBackgroundDrawable(bg);
        }
        ref[0] = dlg;
        dlg.show();
    }

    private void showTimerDialog(String label, List<ValveData> targets, String cmd) {
        View tv = LayoutInflater.from(this).inflate(R.layout.dialog_valve_timer, null);
        com.acasian.iot.Calendar.view.WheelTimePickerView pH = tv.findViewById(R.id.pickerTimerHour);
        com.acasian.iot.Calendar.view.WheelTimePickerView pM = tv.findViewById(R.id.pickerTimerMinute);
        TextView tvSum = tv.findViewById(R.id.tvTimerSummary);
        TextView tvLbl = tv.findViewById(R.id.tvTimerTargetLabel);
        android.widget.Button bCan = tv.findViewById(R.id.btnTimerCancel);
        android.widget.Button bCon = tv.findViewById(R.id.btnTimerConfirm);
        if (tvLbl!=null) tvLbl.setText(label);
        final int[] h={0}, m={30};
        if (pH!=null) { pH.setRange(0,5); pH.setWrapSelectorWheel(false); pH.setValue(0);
            pH.setOnValueChangeListener((v,o,n)->{ h[0]=n; updateSummary(tvSum,h[0],m[0]); }); }
        if (pM!=null) { pM.setRange(0,59); pM.setWrapSelectorWheel(true); pM.setValue(30);
            pM.setOnValueChangeListener((v,o,n)->{ m[0]=n; updateSummary(tvSum,h[0],m[0]); }); }
        updateSummary(tvSum,0,30);
        int[] pIds={R.id.btnPreset10,R.id.btnPreset20,R.id.btnPreset30,R.id.btnPreset60,R.id.btnPreset90,R.id.btnPreset120};
        int[] pMins={10,20,30,60,90,120};
        for (int i=0;i<pIds.length;i++) {
            final int mins=pMins[i]; TextView btn=tv.findViewById(pIds[i]);
            if (btn==null) continue;
            btn.setOnClickListener(v->{ h[0]=mins/60; m[0]=mins%60;
                if(pH!=null)pH.setValue(h[0]); if(pM!=null)pM.setValue(m[0]);
                updateSummary(tvSum,h[0],m[0]); });
        }
        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(tv).create();
        if (bCan!=null) bCan.setOnClickListener(v->dlg.dismiss());
        if (bCon!=null) bCon.setOnClickListener(v->{
            int total=h[0]*60+m[0];
            if (total<=0) { Toast.makeText(this,"1분 이상 설정해 주세요.",Toast.LENGTH_SHORT).show(); return; }
            dlg.dismiss();
            GwData gw = gatewayList.get(selectedGwIndex);
            for (ValveData vd : targets) sendValveCommand(gw, vd, cmd, total);
        });
        dlg.show();
    }

    private void showStopConfirm(String msg, Runnable onConfirm) {
        float dp = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        int p=Math.round(20*dp); root.setPadding(p,p,p,Math.round(8*dp));
        android.widget.TextView tvT = new android.widget.TextView(this);
        tvT.setText("관수 중지"); tvT.setTextSize(20f);
        tvT.setTypeface(null,android.graphics.Typeface.BOLD); tvT.setTextColor(0xFF1B2E1B);
        tvT.setPadding(0,0,0,Math.round(10*dp)); root.addView(tvT);
        android.view.View div = new android.view.View(this); div.setBackgroundColor(0xFFE0E0E0);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,Math.round(1*dp))); root.addView(div);
        android.widget.TextView tvM = new android.widget.TextView(this);
        tvM.setText(msg); tvM.setTextSize(17f); tvM.setTextColor(0xFF555555);
        tvM.setPadding(0,Math.round(14*dp),0,Math.round(4*dp)); root.addView(tvM);
        android.widget.LinearLayout br = new android.widget.LinearLayout(this);
        br.setGravity(android.view.Gravity.END); br.setPadding(0,Math.round(16*dp),0,0);
        androidx.appcompat.app.AlertDialog[] ref={null};
        android.widget.Button bC = new android.widget.Button(this);
        bC.setText("취소"); bC.setTextSize(17f); bC.setTextColor(0xFF9E9E9E); bC.setBackground(null);
        bC.setPadding(Math.round(8*dp),0,Math.round(8*dp),0);
        bC.setOnClickListener(v->{ if(ref[0]!=null) ref[0].dismiss(); });
        android.widget.Button bO = new android.widget.Button(this);
        bO.setText("중지"); bO.setTextSize(17f); bO.setTypeface(null,android.graphics.Typeface.BOLD);
        bO.setTextColor(0xFFC62828); bO.setBackground(null);
        bO.setPadding(Math.round(12*dp),0,Math.round(4*dp),0);
        bO.setOnClickListener(v->{ if(ref[0]!=null) ref[0].dismiss(); onConfirm.run(); });
        br.addView(bC); br.addView(bO); root.addView(br);
        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(root).create();
        if (dlg.getWindow()!=null) {
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFFFFF); bg.setCornerRadius(16*dp);
            dlg.getWindow().setBackgroundDrawable(bg);
        }
        ref[0]=dlg; dlg.show();
    }

    private void showEmergencyStopDialog() {
        float dp = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout hdr = new android.widget.LinearLayout(this);
        hdr.setBackgroundColor(0xFFB71C1C); int hp=Math.round(20*dp);
        hdr.setPadding(hp,Math.round(16*dp),hp,Math.round(16*dp));
        android.widget.TextView tvI = new android.widget.TextView(this);
        tvI.setText("⚠  긴급 중지"); tvI.setTextSize(22f);
        tvI.setTypeface(null,android.graphics.Typeface.BOLD); tvI.setTextColor(0xFFFFFFFF);
        hdr.addView(tvI); root.addView(hdr);
        android.widget.LinearLayout body = new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL); body.setBackgroundColor(0xFFFFFFFF);
        int bp=Math.round(20*dp); body.setPadding(bp,bp,bp,Math.round(8*dp));
        android.widget.TextView tvM = new android.widget.TextView(this);
        tvM.setText("모든 관수를 즉시 중단합니다.\n계속할까요?");
        tvM.setTextSize(18f); tvM.setTextColor(0xFF1B2E1B); tvM.setLineSpacing(0,1.35f); body.addView(tvM);
        android.widget.LinearLayout br = new android.widget.LinearLayout(this);
        br.setGravity(android.view.Gravity.END); br.setPadding(0,Math.round(20*dp),0,Math.round(4*dp));
        androidx.appcompat.app.AlertDialog[] ref={null};
        android.widget.Button bC = new android.widget.Button(this);
        bC.setText("취소"); bC.setTextSize(17f); bC.setTextColor(0xFF9E9E9E); bC.setBackground(null);
        bC.setPadding(Math.round(8*dp),0,Math.round(8*dp),0);
        bC.setOnClickListener(v->{ if(ref[0]!=null) ref[0].dismiss(); });
        android.widget.Button bS = new android.widget.Button(this);
        bS.setText("지금 중지"); bS.setTextSize(17f);
        bS.setTypeface(null,android.graphics.Typeface.BOLD); bS.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable sb = new android.graphics.drawable.GradientDrawable();
        sb.setColor(0xFFB71C1C); sb.setCornerRadius(8*dp); bS.setBackground(sb);
        bS.setPadding(Math.round(18*dp),Math.round(10*dp),Math.round(18*dp),Math.round(10*dp));
        bS.setOnClickListener(v->{
            if(ref[0]!=null) ref[0].dismiss();
            GwData gw = gatewayList.get(selectedGwIndex);
            valveRepo.stopValve(gw.telNo,"0",ApiService.DeviceRequest.TYPE_MAIN,
                new ValveRepository.ValveCallback(){
                    @Override public void onSuccess(){
                        runOnUiThread(()->{ for(ValveData vd:gw.valves)vd.state=ValveState.IDLE;
                            renderValveList(); Toast.makeText(IrrigationActivity.this,"긴급 중지 완료",Toast.LENGTH_SHORT).show(); });
                    }
                    @Override public void onFailure(String e){
                        runOnUiThread(()->{ for(ValveData vd:gw.valves)vd.state=ValveState.IDLE;
                            renderValveList(); Toast.makeText(IrrigationActivity.this,"긴급 중지 완료",Toast.LENGTH_SHORT).show(); });
                    }
                });
        });
        br.addView(bC); br.addView(bS); body.addView(br); root.addView(body);
        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(this).setView(root).create();
        if (dlg.getWindow()!=null) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFFFFF); bg.setCornerRadius(16*dp); dlg.getWindow().setBackgroundDrawable(bg);
        }
        ref[0]=dlg; dlg.show();
    }

    // ── 유틸 ────────────────────────────────────────────────────────
    private void sv(View v, boolean show) {
        if (v!=null) v.setVisibility(show?View.VISIBLE:View.GONE);
    }
    private void badge(TextView tv, String text, int bgRes, int color) {
        if (tv==null) return; tv.setText(text); tv.setBackgroundResource(bgRes); tv.setTextColor(color);
    }
    private void dot(View v, int color) {
        if (v==null) return;
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL); d.setColor(color); v.setBackground(d);
    }
    private void updateSummary(TextView tv, int h, int m) {
        if (tv==null) return;
        tv.setText(h==0 ? "가동 시간: "+m+"분" : m==0 ? "가동 시간: "+h+"시간" : "가동 시간: "+h+"시간 "+m+"분");
    }
}

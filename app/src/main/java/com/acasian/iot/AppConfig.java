package com.acasian.iot;

import java.util.ArrayList;
import java.util.List;

/**
 * 앱 전역 설정 — 싱글톤.
 *
 * ── DEV_MODE ────────────────────────────────────────────────────────
 *  true  : 더미 데이터(DemoData)로 동작. 서버 API 불필요.
 *           로그인 계정: 010-0000-0000 / 0000
 *  false : 실제 서버 API 연동. ZoneStore는 서버 응답으로 채워짐.
 *
 *  ★ 개발/QA 시엔 DEV_MODE = true 로 로그인하면 모든 화면 더미데이터로 동작 ★
 *  ★ 배포 전 실 계정으로 로그인하면 자동으로 false 전환됨               ★
 * ────────────────────────────────────────────────────────────────────
 */
public class AppConfig {

    private static volatile AppConfig INSTANCE;

    public static AppConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (AppConfig.class) {
                if (INSTANCE == null) INSTANCE = new AppConfig();
            }
        }
        return INSTANCE;
    }

    private AppConfig() {}

    // ── DEV_MODE 상태 ─────────────────────────────────────────────
    private boolean devMode = false;

    /** 현재 DEV_MODE 여부 */
    public boolean isDevMode() { return devMode; }

    /**
     * 로그인 시 호출.
     * @param demo true = 데모 계정 로그인, false = 실 계정 로그인
     */
    public void setDevMode(boolean demo) {
        devMode = demo;
    }

    // ── DEV_MODE 시 ZoneStore 더미 주입 ──────────────────────────

    /**
     * DEV_MODE 로그인 시 한 번만 호출.
     * DemoData의 ZONES 정의를 ZoneStore에 주입한다.
     */
    public static void injectDemoZones() {
        List<ZoneStore.ZoneInfo> zones = new ArrayList<>();

        // 컨트롤박스#1 (10개 밸브)
        List<ZoneStore.NodeInfo> nodes1 = new ArrayList<>();
        nodes1.add(new ZoneStore.NodeInfo("a_valve1",  "토출 밸브 1"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve2",  "토출 밸브 2"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve3",  "토출 밸브 3"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve4",  "토출 밸브 4"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve5",  "토출 밸브 5"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve6",  "토출 밸브 6"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve7",  "토출 밸브 7"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve8",  "토출 밸브 8"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve9",  "토출 밸브 9"));
        nodes1.add(new ZoneStore.NodeInfo("a_valve10", "토출 밸브 10"));
        zones.add(new ZoneStore.ZoneInfo(DemoData.ZONE_TEL_1, "컨트롤박스#1", nodes1));

        // 컨트롤박스#2 (3개 밸브)
        List<ZoneStore.NodeInfo> nodes2 = new ArrayList<>();
        nodes2.add(new ZoneStore.NodeInfo("b_valve1", "토출 밸브 1"));
        nodes2.add(new ZoneStore.NodeInfo("b_valve2", "토출 밸브 2"));
        nodes2.add(new ZoneStore.NodeInfo("b_valve3", "토출 밸브 3"));
        zones.add(new ZoneStore.ZoneInfo(DemoData.ZONE_TEL_2, "컨트롤박스#2", nodes2));

        // 컨트롤박스#3 (2개 밸브)
        List<ZoneStore.NodeInfo> nodes3 = new ArrayList<>();
        nodes3.add(new ZoneStore.NodeInfo("c_valve1", "토출 밸브 1"));
        nodes3.add(new ZoneStore.NodeInfo("c_valve2", "토출 밸브 2"));
        zones.add(new ZoneStore.ZoneInfo(DemoData.ZONE_TEL_3, "컨트롤박스#3", nodes3));

        ZoneStore.getInstance().update(zones);
    }
}

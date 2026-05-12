package com.acasian.iot;

import com.acasian.iot.Calendar.model.WorkRecord;
import com.acasian.iot.model.IrrigationProfile;
import com.acasian.iot.model.IrrigationProfileManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════
 *  데모 모드 더미 데이터 설정 파일
 *  로그인: 010-0000-0000 / 0000
 *
 *  ★ 이 파일만 수정하면 데모 화면의 모든 데이터가 바뀝니다 ★
 *
 *  ── 수정 위치 ────────────────────────────────────────────────────
 *  1. 구역/밸브      → ZONES 배열
 *  2. 관수 유형      → applyProfiles()
 *  3. 예약/기록      → buildRecords()
 *  4. 기록 탭        → buildLogRecords()
 * ══════════════════════════════════════════════════════════════════
 */
public class DemoData {

    // ── 메인함 고정 telNo (initDummyZoneData와 applyProfiles에서 공유) ──
    // 더미 관수 유형 고정 ID
    public static final String PROFILE_봄아침   = "demo_profile_001";
    public static final String PROFILE_저녁점적  = "demo_profile_002";
    public static final String PROFILE_기본      = "demo_profile_003";
    public static final String PROFILE_집중      = "demo_profile_004";
    public static final String PROFILE_개별점적  = "demo_profile_005";

    public static final String ZONE_TEL_1 = "demo_tel_001";  // 컨트롤박스#1
    public static final String ZONE_TEL_2 = "demo_tel_002";  // 컨트롤박스#2
    public static final String ZONE_TEL_3 = "demo_tel_003";  // 컨트롤박스#3

    // 더미 관수 유형 고정 ID (앱 재시작 후에도 예약-유형 연결 유지)
    public static final String PROFILE_1 = "demo_profile_001";  // 봄 아침 관수
    public static final String PROFILE_2 = "demo_profile_002";  // 저녁 점적 관수
    public static final String PROFILE_3 = "demo_profile_003";  // 기본 관수
    public static final String PROFILE_4 = "demo_profile_004";  // 집중 관수
    public static final String PROFILE_5 = "demo_profile_005";  // 개별 점적

    // ════════════════════════════════════════════════════════════════
    //  1. 구역 / 밸브
    // ════════════════════════════════════════════════════════════════

    private static final ZoneDef[] ZONES = {

        new ZoneDef(
            "컨트롤박스#1", "경기도 여주시 세종로 1", "정상", "미설치", 10, true,
            new NodeDef[]{
                new NodeDef("a_valve1",  "토출 밸브 1",  "A-01 구역", "RUNNING"),
                new NodeDef("a_valve2",  "토출 밸브 2",  "A-02 구역", "IDLE"),
                new NodeDef("a_valve3",  "토출 밸브 3",  "A-03 구역", "IDLE"),
                new NodeDef("a_valve4",  "토출 밸브 4",  "A-04 구역", "IDLE"),
                new NodeDef("a_valve5",  "토출 밸브 5",  "A-05 구역", "IDLE"),
                new NodeDef("a_valve6",  "토출 밸브 6",  "A-06 구역", "IDLE"),
                new NodeDef("a_valve7",  "토출 밸브 7",  "A-07 구역", "IDLE"),
                new NodeDef("a_valve8",  "토출 밸브 8",  "A-08 구역", "IDLE"),
                new NodeDef("a_valve9",  "토출 밸브 9",  "A-09 구역", "IDLE"),
                new NodeDef("a_valve10", "토출 밸브 10", "A-10 구역", "IDLE"),
            }
        ),

        new ZoneDef(
            "컨트롤박스#2", "경기도 여주시 세종로 2", "정상", "정상", 8, false,
            new NodeDef[]{
                new NodeDef("b_valve1", "토출 밸브 1", "B-01 구역", "IDLE"),
                new NodeDef("b_valve2", "토출 밸브 2", "B-02 구역", "RUNNING"),
                new NodeDef("b_valve3", "토출 밸브 3", "B-03 구역", "IDLE"),
            }
        ),

        new ZoneDef(
            "컨트롤박스#3", "경기도 여주시 세종로 3", "점검", "오류", 4, false,
            new NodeDef[]{
                new NodeDef("c_valve1", "토출 밸브 1", "C-01 구역", "IDLE"),
                new NodeDef("c_valve2", "토출 밸브 2", "C-02 구역", "ERROR"),
            }
        ),
    };

    /** ZONES 순서와 동일한 telNo 배열 반환 */
    public static String[] getZoneTelNos() {
        return new String[]{ ZONE_TEL_1, ZONE_TEL_2, ZONE_TEL_3 };
    }

    /** ZONES 순서와 동일한 메인함명 배열 반환 */
    public static String[] getZoneNames() {
        String[] names = new String[ZONES.length];
        for (int i = 0; i < ZONES.length; i++) names[i] = ZONES[i].zoneName;
        return names;
    }

    // ════════════════════════════════════════════════════════════════
    //  2. 관수 유형 프리셋
    //     ※ zoneId = ZONE_TEL_* 상수와 일치해야 예약/유형관리 연동됨
    // ════════════════════════════════════════════════════════════════

    public static void applyProfiles(android.content.Context ctx) {
        IrrigationProfileManager mgr = IrrigationProfileManager.getInstance(ctx);
        List<IrrigationProfile> list = new ArrayList<>();

        // ── 컨트롤박스#1 유형 ──────────────────────────────────────
        IrrigationProfile p1 = new IrrigationProfile();
        p1.setId("demo_profile_001");
        p1.setName("봄 아침 관수");
        p1.setProfileType(IrrigationProfile.ProfileType.AUTO);
        p1.setTargetType(IrrigationProfile.TargetType.ZONE_ALL);
        p1.setZoneId(ZONE_TEL_1); p1.setZoneName("컨트롤박스#1");
        p1.setRunMinutes(30); p1.setRestMinutes(0); p1.setRepeatCount(1);
        // 그룹: 밸브1~4 / 밸브5~7 / 밸브8~10
        List<String> g1 = java.util.Arrays.asList("a_valve1","a_valve2","a_valve3","a_valve4");
        List<String> g2 = java.util.Arrays.asList("a_valve5","a_valve6","a_valve7");
        List<String> g3 = java.util.Arrays.asList("a_valve8","a_valve9","a_valve10");
        p1.setGroups(java.util.Arrays.asList(
            new IrrigationProfile.Group("demo_g1_1", g1, 1),
            new IrrigationProfile.Group("demo_g1_2", g2, 2),
            new IrrigationProfile.Group("demo_g1_3", g3, 3)
        ));
        list.add(p1);

        IrrigationProfile p2 = new IrrigationProfile();
        p2.setId("demo_profile_002");
        p2.setName("저녁 점적 관수");
        p2.setProfileType(IrrigationProfile.ProfileType.AUTO);
        p2.setTargetType(IrrigationProfile.TargetType.ZONE_ALL);
        p2.setZoneId(ZONE_TEL_1); p2.setZoneName("컨트롤박스#1");
        p2.setRunMinutes(30); p2.setRestMinutes(10); p2.setRepeatCount(2);
        List<String> g2_1 = java.util.Arrays.asList("a_valve1","a_valve2","a_valve3","a_valve4","a_valve5");
        List<String> g2_2 = java.util.Arrays.asList("a_valve6","a_valve7","a_valve8","a_valve9","a_valve10");
        p2.setGroups(java.util.Arrays.asList(
            new IrrigationProfile.Group("demo_g2_1", g2_1, 1),
            new IrrigationProfile.Group("demo_g2_2", g2_2, 2)
        ));
        list.add(p2);

        IrrigationProfile p3 = new IrrigationProfile();
        p3.setId("demo_profile_003");
        p3.setName("기본 관수");
        p3.setProfileType(IrrigationProfile.ProfileType.INDIVIDUAL);
        p3.setTargetType(IrrigationProfile.TargetType.ZONE_INDIVIDUAL);
        p3.setZoneId(ZONE_TEL_1); p3.setZoneName("컨트롤박스#1");
        p3.setRunMinutes(40); p3.setRestMinutes(0); p3.setRepeatCount(1);
        p3.setDeviceIds(java.util.Arrays.asList("a_valve1","a_valve3","a_valve5"));
        list.add(p3);

        // ── 컨트롤박스#2 유형 ──────────────────────────────────────
        IrrigationProfile p4 = new IrrigationProfile();
        p4.setId("demo_profile_004");
        p4.setName("집중 관수");
        p4.setProfileType(IrrigationProfile.ProfileType.AUTO);
        p4.setTargetType(IrrigationProfile.TargetType.ZONE_ALL);
        p4.setZoneId(ZONE_TEL_2); p4.setZoneName("컨트롤박스#2");
        p4.setRunMinutes(60); p4.setRestMinutes(0); p4.setRepeatCount(1);
        List<String> g4_1 = java.util.Arrays.asList("b_valve1","b_valve2");
        List<String> g4_2 = java.util.Arrays.asList("b_valve3");
        p4.setGroups(java.util.Arrays.asList(
            new IrrigationProfile.Group("demo_g4_1", g4_1, 1),
            new IrrigationProfile.Group("demo_g4_2", g4_2, 2)
        ));
        list.add(p4);

        IrrigationProfile p5 = new IrrigationProfile();
        p5.setId("demo_profile_005");
        p5.setName("개별 점적");
        p5.setProfileType(IrrigationProfile.ProfileType.INDIVIDUAL);
        p5.setTargetType(IrrigationProfile.TargetType.ZONE_INDIVIDUAL);
        p5.setZoneId(ZONE_TEL_2); p5.setZoneName("컨트롤박스#2");
        p5.setDeviceIds(java.util.Arrays.asList("b_valve1","b_valve2"));
        p5.setRunMinutes(20); p5.setRestMinutes(5); p5.setRepeatCount(3);
        list.add(p5);

        mgr.saveAll(list);
    }

    // ════════════════════════════════════════════════════════════════
    //  3. 예약 / 기록 (CalendarFragment)
    // ════════════════════════════════════════════════════════════════

    public static List<WorkRecord> buildRecords() {
        List<WorkRecord> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 과거 기록
        list.add(r("r1", today.minusDays(1), 6, 0,
                "봄 아침 관수", "컨트롤박스#1 전체", "30분·휴0분·1회", WorkRecord.Status.DONE, PROFILE_1));
        list.add(r("r2", today.minusDays(1), 17, 30,
                "저녁 점적 관수", "컨트롤박스#1 전체", "30분·휴10분·2회", WorkRecord.Status.FAILED, PROFILE_2));
        list.add(r("r3", today, 8, 0,
                "기본 관수", "컨트롤박스#1 전체", "40분·휴0분·1회", WorkRecord.Status.DONE, PROFILE_3));

        // 미래 예약
        list.add(r("r4", today.plusDays(1), 6, 0,
                "봄 아침 관수", "컨트롤박스#1 전체", "30분·휴0분·1회", WorkRecord.Status.SCHEDULED, PROFILE_1));
        list.add(r("r5", today.plusDays(1), 18, 0,
                "저녁 점적 관수", "컨트롤박스#1 전체", "30분·휴10분·2회", WorkRecord.Status.SCHEDULED, PROFILE_2));
        list.add(r("r6", today.plusDays(2), 7, 0,
                "집중 관수", "컨트롤박스#2 전체", "60분·휴0분·1회", WorkRecord.Status.SCHEDULED, PROFILE_4));

        return list;
    }

    // ════════════════════════════════════════════════════════════════
    //  4. 기록 탭 타임라인 (LogFragment)
    // ════════════════════════════════════════════════════════════════

    public static List<WorkRecord> buildLogRecords() {
        List<WorkRecord> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        list.add(r("demo_1", today, 6, 30,
                "봄 아침 관수", "컨트롤박스#1 전체", "30분·휴0분·1회", WorkRecord.Status.DONE, PROFILE_1));
        list.add(r("demo_2", today, 9, 0,
                "기본 관수", "컨트롤박스#1 전체", "40분·휴0분·1회", WorkRecord.Status.DONE, PROFILE_3));
        list.add(r("demo_3", today, 18, 0,
                "저녁 점적 관수", "컨트롤박스#1 전체", "30분·휴10분·2회", WorkRecord.Status.SCHEDULED, PROFILE_2));
        list.add(r("demo_4", today.minusDays(1), 6, 0,
                "봄 아침 관수", "컨트롤박스#1 전체", "30분·휴0분·1회", WorkRecord.Status.DONE, PROFILE_1));
        list.add(r("demo_5", today.minusDays(1), 17, 30,
                "저녁 점적 관수", "컨트롤박스#1 전체", "30분·휴10분·2회", WorkRecord.Status.FAILED, PROFILE_2));
        list.add(r("demo_6", today.minusDays(2), 7, 0,
                "집중 관수", "컨트롤박스#2 전체", "60분·휴0분·1회", WorkRecord.Status.DONE));
        list.add(r("demo_7", today.minusDays(2), 16, 0,
                "컨트롤박스#3 관수", "컨트롤박스#3 전체", "25분·휴0분·1회", WorkRecord.Status.DONE));
        list.add(r("demo_8", today.minusDays(3), 6, 30,
                "봄 아침 관수", "컨트롤박스#1 전체", "30분·휴0분·1회", WorkRecord.Status.DONE, PROFILE_1));
        list.add(r("demo_9", today.minusDays(5), 8, 0,
                "개별 점적", "컨트롤박스#2 노드2개", "20분·휴5분·3회", WorkRecord.Status.DONE));

        return list;
    }

    // ════════════════════════════════════════════════════════════════
    //  이하 코드는 수정하지 않아도 됩니다
    // ════════════════════════════════════════════════════════════════

    public static List<Object[]> build() {
        List<Object[]> result = new ArrayList<>();
        for (ZoneDef z : ZONES) {
            List<DeviceModel> devices = new ArrayList<>();
            for (NodeDef n : z.nodes) {
                DeviceModel d = new DeviceModel(
                        n.vid, n.nodeName, n.description,
                        DeviceModel.DeviceType.PUMP,
                        "RUNNING".equalsIgnoreCase(n.nodeStatus));
                d.setStatus(parseStatus(n.nodeStatus));
                devices.add(d);
            }
            result.add(new Object[]{
                z.zoneName, z.address, z.mainStatus,
                z.sensorStatus, z.childCount, devices, z.expanded
            });
        }
        return result;
    }

    private static WorkRecord r(String id, LocalDate date, int h, int m,
                                 String name, String zone, String memo,
                                 WorkRecord.Status status) {
        return r(id, date, h, m, name, zone, memo, status, null);
    }

    private static WorkRecord r(String id, LocalDate date, int h, int m,
                                 String name, String zone, String memo,
                                 WorkRecord.Status status, String profileId) {
        int runMin = 30;
        try { runMin = Integer.parseInt(memo.split("분")[0].trim()); }
        catch (Exception ignored) {}
        LocalTime start = LocalTime.of(h, m);
        LocalTime end   = start.plusMinutes(runMin);
        WorkRecord rec = new WorkRecord(id, date, start, end,
                name, WorkRecord.DeviceType.PUMP, name, zone, status, memo);
        if (profileId != null) rec.setIrrigationProfileId(profileId);
        return rec;
    }

    private static DeviceModel.DeviceStatus parseStatus(String s) {
        if (s == null) return DeviceModel.DeviceStatus.IDLE;
        switch (s.toUpperCase()) {
            case "RUNNING": return DeviceModel.DeviceStatus.RUNNING;
            case "ERROR":   return DeviceModel.DeviceStatus.ERROR;
            case "STANDBY": return DeviceModel.DeviceStatus.STANDBY;
            default:        return DeviceModel.DeviceStatus.IDLE;
        }
    }

    private static class ZoneDef {
        final String zoneName, address, mainStatus, sensorStatus;
        final int childCount;
        final boolean expanded;
        final NodeDef[] nodes;
        ZoneDef(String zoneName, String address, String mainStatus,
                String sensorStatus, int childCount, boolean expanded, NodeDef[] nodes) {
            this.zoneName = zoneName; this.address = address;
            this.mainStatus = mainStatus; this.sensorStatus = sensorStatus;
            this.childCount = childCount; this.expanded = expanded; this.nodes = nodes;
        }
    }

    private static class NodeDef {
        final String vid, nodeName, description, nodeStatus;
        NodeDef(String vid, String nodeName, String description, String nodeStatus) {
            this.vid = vid; this.nodeName = nodeName;
            this.description = description; this.nodeStatus = nodeStatus;
        }
    }
}

package com.acasian.iot;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 설치 장비 정보 — SharedPreferences 저장/불러오기
 *
 * 저장 항목:
 *   tankInstalled  : 물탱크 설치 여부
 *   sensorInstalled: 수위 센서 설치 여부
 *   pumpHp         : 펌프 마력수 (HP)
 *   mainPipeMm     : 주관 직경 (mm)
 *   outPipeMm      : 토출관 직경 (mm)
 *
 * 경고 판단 (관수 시작 전 호출):
 *   checkWarnings() → List<EquipWarning>
 *   EquipWarning.level : YELLOW(주의) / RED(경고)
 *   EquipWarning.message : 표시할 메시지
 *
 * ─────────────────────────────────────────
 * TODO: 압력 계산 연동 — 아래 주석 블록 참고
 *   현재는 단순 조건 비교만 수행
 *   추후 IrrigationActivity 시작 시 현재 가동 밸브 수를 넘겨받아
 *   수압 계산 기반 경고로 교체 예정
 * ─────────────────────────────────────────
 */
public class EquipmentConfig {

    private static final String PREF_NAME   = "equipment_config";
    private static final String KEY_TANK    = "tankInstalled";
    private static final String KEY_SENSOR  = "sensorInstalled";
    private static final String KEY_PUMP_HP = "pumpHp";
    private static final String KEY_MAIN_MM = "mainPipeMm";
    private static final String KEY_OUT_MM  = "outPipeMm";

    // 기본값
    private static final boolean DEFAULT_TANK   = false;
    private static final boolean DEFAULT_SENSOR = false;
    private static final float   DEFAULT_HP     = 2.0f;
    private static final int     DEFAULT_MAIN   = 50;   // mm (2인치)
    private static final int     DEFAULT_OUT    = 25;   // mm (1인치)

    // ── 경고 레벨 ──────────────────────────────────────────────────
    public enum WarnLevel { YELLOW, RED }

    public static class EquipWarning {
        public final WarnLevel level;
        public final String    message;
        public EquipWarning(WarnLevel l, String m) { level = l; message = m; }
    }

    // ── 저장 ───────────────────────────────────────────────────────
    public static void save(Context ctx,
                            boolean tank, boolean sensor,
                            float hp, int mainMm, int outMm) {
        prefs(ctx).edit()
                .putBoolean(KEY_TANK,    tank)
                .putBoolean(KEY_SENSOR,  sensor)
                .putFloat  (KEY_PUMP_HP, hp)
                .putInt    (KEY_MAIN_MM, mainMm)
                .putInt    (KEY_OUT_MM,  outMm)
                .apply();
    }

    // ── 불러오기 ───────────────────────────────────────────────────
    public static boolean isTankInstalled  (Context ctx) { return prefs(ctx).getBoolean(KEY_TANK,    DEFAULT_TANK);   }
    public static boolean isSensorInstalled(Context ctx) { return prefs(ctx).getBoolean(KEY_SENSOR,  DEFAULT_SENSOR); }
    public static float   getPumpHp        (Context ctx) { return prefs(ctx).getFloat  (KEY_PUMP_HP, DEFAULT_HP);     }
    public static int     getMainPipeMm    (Context ctx) { return prefs(ctx).getInt    (KEY_MAIN_MM, DEFAULT_MAIN);   }
    public static int     getOutPipeMm     (Context ctx) { return prefs(ctx).getInt    (KEY_OUT_MM,  DEFAULT_OUT);    }

    public static boolean isConfigSaved(Context ctx) {
        return prefs(ctx).contains(KEY_PUMP_HP);
    }

    // ── 경고 판단 ──────────────────────────────────────────────────
    /**
     * 관수 시작 전 경고 조건 검사
     *
     * @param ctx        Context
     * @param valveCount 현재 동시 가동 예정 밸브 수
     * @return 경고 목록 (비어있으면 정상)
     */
    public static List<EquipWarning> checkWarnings(Context ctx, int valveCount) {
        List<EquipWarning> warns = new ArrayList<>();

        boolean tank   = isTankInstalled(ctx);
        boolean sensor = isSensorInstalled(ctx);
        float   hp     = getPumpHp(ctx);
        int     mainMm = getMainPipeMm(ctx);
        int     outMm  = getOutPipeMm(ctx);

        // ── 경고 1: 탱크 있는데 수위 센서 없음 ────────────────────
        if (tank && !sensor) {
            warns.add(new EquipWarning(WarnLevel.YELLOW,
                    "물탱크를 사용 중이나 수위 센서가 없어 잔수량을 확인할 수 없습니다.\n" +
                    "관수 도중 수원이 부족할 수 있습니다."));
        }

        // ── 경고 2: 토출관/주관 직경 비율 불균형 ──────────────────
        // 토출관 합산 단면적이 주관 단면적 초과 시 주관이 병목
        // 단면적 = π(d/2)²  → 비율만 비교하므로 π 생략
        if (mainMm > 0 && outMm > 0 && valveCount > 0) {
            double aMain    = (double) mainMm  * mainMm;
            double aOutSum  = (double) outMm   * outMm  * valveCount;
            if (aOutSum > aMain * 1.2) {
                warns.add(new EquipWarning(WarnLevel.RED,
                        "토출관 합산 단면적이 주관을 초과합니다.\n" +
                        "주관(" + mainMm + "mm) 대비 토출관(" + outMm + "mm)×" + valveCount + "개 구성 확인 바랍니다."));
            }
        }

        // ── 경고 3: 권장 최대 밸브 수 초과 ───────────────────────
        /*
         * TODO: 압력 계산 기반 최대 밸브 수 판단
         *
         * 현재는 단순 경험치 공식 사용 (임시)
         * 추후 EquipmentPressureCalc.java 에서 아래 공식으로 교체 예정:
         *
         *   // 1. Q-H 곡선으로 운전점 탐색 (이분법)
         *   //    H = H0 × (1 - (Q/Q0)²)
         *   //    H_sys = hf_전자밸브 + hf_주관 + hf_토출밸브 + hf_토출관
         *
         *   // 2. 밸브 앞단 압력 계산
         *   //    P_valve = (Hop - hf_main) × 9.81  (kPa)
         *
         *   // 3. 최소 작동압과 비교
         *   //    P_valve >= P_min → 정상
         *   //    P_valve >= P_min × 0.8 → 주의
         *   //    P_valve <  P_min × 0.8 → 경고
         *
         * 현재 임시 로직: HP × 2 = 권장 최대 밸브 수
         */
        int maxValves = (int) (hp * 2);   // TODO: 압력 계산으로 교체
        if (valveCount > maxValves + 2) {
            warns.add(new EquipWarning(WarnLevel.RED,
                    "동시 가동 밸브(" + valveCount + "개)가 펌프(" + hp + "HP) 기준\n" +
                    "권장 최대(" + maxValves + "개)를 크게 초과합니다.\n" +
                    "수압 부족으로 일부 밸브가 정상 동작하지 않을 수 있습니다."));
        } else if (valveCount > maxValves) {
            warns.add(new EquipWarning(WarnLevel.YELLOW,
                    "동시 가동 밸브(" + valveCount + "개)가 펌프(" + hp + "HP) 기준\n" +
                    "권장 최대(" + maxValves + "개)를 초과합니다.\n" +
                    "끝단 밸브의 급수압이 낮아질 수 있습니다."));
        }

        return warns;
    }

    // ── 유틸 ───────────────────────────────────────────────────────
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

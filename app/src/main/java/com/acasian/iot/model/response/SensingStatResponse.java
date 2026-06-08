package com.acasian.iot.model.response;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 센서 통계 응답 (일/주/월 공통) — 실제 서버 포맷 기준.
 *
 * 서버 응답 예:
 * {
 *   "message":"successfully",
 *   "status":"success",
 *   "data":[
 *     { "lteNo":"8988...",
 *       "day1min":"21.4","day1max":"27.5",
 *       "day2min":"22.5","day2max":"26.1", ...
 *       "day31min":"25.1","day31max":"30.4" }
 *   ]
 * }
 *
 * 특징:
 *  - data 는 단일 객체(게이트웨이별) — 버킷 배열이 아님
 *  - 값은 문자열, avg 없이 min/max 만 제공
 *  - "0"/"0" 은 데이터 없음(gap) 으로 간주
 *  - 키 체계가 기간별로 다름:
 *      · 일별 : day1min..day31max        (인덱스)        → toIndexedSeries("day")
 *      · 주별 : monmin/tuemin/.../sunmin (요일 고정키)    → toWeekdaySeries()
 *      · 월별 : mon1min..mon12max        (인덱스 "mon"+N) → toIndexedSeries("mon")
 */
public class SensingStatResponse {

    @SerializedName("status")
    public String status;

    @SerializedName("result")
    public String result;

    @SerializedName("message")
    public String message;

    /** 동적 필드(dayNmin/max 등)를 담기 위해 JsonObject 트리로 수신 */
    @SerializedName("data")
    public List<JsonObject> data;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) || "true".equalsIgnoreCase(result);
    }

    /**
     * 인덱스형 시계열 파싱 — 일별 "day"(day1..day31), 월별 "mon"(mon1..mon12).
     * {prefix}{i}min / {prefix}{i}max 를 i=1부터 키가 없을 때까지 스캔.
     */
    public List<Point> toIndexedSeries(String prefix) {
        List<Point> out = new ArrayList<>();
        if (data == null || data.isEmpty() || data.get(0) == null) return out;
        JsonObject o = data.get(0);
        for (int i = 1; ; i++) {
            String kMin = prefix + i + "min";
            String kMax = prefix + i + "max";
            if (!o.has(kMin) && !o.has(kMax)) break;   // 더 이상 버킷 없음
            double mn = parse(o, kMin);
            double mx = parse(o, kMax);
            boolean hasData = !(mn == 0.0 && mx == 0.0);  // 0/0 = 미측정
            out.add(new Point(prefix + i, mn, mx, hasData));
        }
        return out;
    }

    /** 요일 고정키 (주별) — 서버 키: monmin/tuemin/.../sunmin */
    private static final String[] WEEKDAY_KEYS   = {"mon","tue","wed","thu","fri","sat","sun"};
    private static final String[] WEEKDAY_LABELS = {"월","화","수","목","금","토","일"};

    /**
     * 주별 파싱 — 키가 인덱스가 아니라 요일(mon~sun). 월~일 순서 7개 버킷.
     */
    public List<Point> toWeekdaySeries() {
        List<Point> out = new ArrayList<>();
        if (data == null || data.isEmpty() || data.get(0) == null) return out;
        JsonObject o = data.get(0);
        for (int i = 0; i < WEEKDAY_KEYS.length; i++) {
            String kMin = WEEKDAY_KEYS[i] + "min";
            String kMax = WEEKDAY_KEYS[i] + "max";
            if (!o.has(kMin) && !o.has(kMax)) continue;
            double mn = parse(o, kMin);
            double mx = parse(o, kMax);
            boolean hasData = !(mn == 0.0 && mx == 0.0);
            out.add(new Point(WEEKDAY_LABELS[i], mn, mx, hasData));
        }
        return out;
    }

    private static double parse(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) return 0.0;
            String s = e.getAsString();
            return (s == null || s.isEmpty()) ? 0.0 : Double.parseDouble(s);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    /** 버킷 1개 (하루/한 주/한 달). 서버가 avg 미제공 → 라인은 (min+max)/2 사용 */
    public static class Point {
        public String  label;    // 표시 라벨 (날짜/요일). 파싱 후 호출부에서 보정 가능
        public final double  min;
        public final double  max;
        public final boolean hasData; // false = 미측정(차트에서 제외)

        public Point(String label, double min, double max, boolean hasData) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.hasData = hasData;
        }

        /** 라인용 중앙값 */
        public double mid() { return (min + max) / 2.0; }

        /** 이상값 판정 — max>상한 또는 min<하한 (관리범위 알 때만 의미) */
        public boolean isOutOfRange(double lo, double hi) {
            return max > hi || min < lo;
        }
    }
}

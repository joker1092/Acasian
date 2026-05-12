package com.acasian.iot.network;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 아카시안 API 날짜·시간 형식 변환 유틸.
 *
 * ── 서버 형식 ────────────────────────────────────────────────
 *  날짜: yymmdd  (6자리)  예) 2026년 3월 15일 → "260315"
 *  시간: hhnn   (4자리)  예) 오전 6시 00분   → "0600"
 *
 * ── 사용 예 ──────────────────────────────────────────────────
 *  String yymmdd = ApiDateUtil.toYymmdd(LocalDate.of(2026, 3, 15)); // "260315"
 *  String hhnn   = ApiDateUtil.toHhnn(LocalTime.of(6, 0));          // "0600"
 *
 *  LocalDate date = ApiDateUtil.fromYymmdd("260315"); // 2026-03-15
 *  LocalTime time = ApiDateUtil.fromHhnn("0600");     // 06:00
 * ────────────────────────────────────────────────────────────
 */
public class ApiDateUtil {

    private ApiDateUtil() {}

    // ── LocalDate → yymmdd ───────────────────────────────────────
    /** 서버 DB 형식: "2026-03-15" (yyyy-MM-dd) */
    public static String toYymmdd(LocalDate date) {
        if (date == null) return "";
        return String.format("%04d-%02d-%02d",
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth());
    }

    // ── LocalTime → hhnn ─────────────────────────────────────────
    /** 서버 DB 형식: "09:40" (HH:mm) */
    public static String toHhnn(LocalTime time) {
        if (time == null) return "";
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    // ── yymmdd → LocalDate ───────────────────────────────────────
    /**
     * 서버 날짜 파싱 — 다중 형식 지원:
     *   "260315"     → 6자리 yymmdd (앱 등록)
     *   "2026-03-15" → 10자리 yyyy-MM-dd (기존 DB 데이터)
     *   "20260315"   → 8자리 yyyyMMdd
     */
    public static LocalDate fromYymmdd(String yymmdd) {
        if (yymmdd == null || yymmdd.isEmpty()) return null;
        try {
            // 6자리: yymmdd
            if (yymmdd.length() == 6) {
                int yy = Integer.parseInt(yymmdd.substring(0, 2));
                int mm = Integer.parseInt(yymmdd.substring(2, 4));
                int dd = Integer.parseInt(yymmdd.substring(4, 6));
                return LocalDate.of(2000 + yy, mm, dd);
            }
            // 10자리: yyyy-MM-dd
            if (yymmdd.length() == 10 && yymmdd.charAt(4) == '-') {
                return LocalDate.parse(yymmdd);
            }
            // 8자리: yyyyMMdd
            if (yymmdd.length() == 8) {
                int yyyy = Integer.parseInt(yymmdd.substring(0, 4));
                int mm   = Integer.parseInt(yymmdd.substring(4, 6));
                int dd   = Integer.parseInt(yymmdd.substring(6, 8));
                return LocalDate.of(yyyy, mm, dd);
            }
        } catch (Exception e) { /* fall through */ }
        return null;
    }

    // ── hhnn → LocalTime ─────────────────────────────────────────
    /**
     * 서버 시간 파싱 — 다중 형식 지원:
     *   "0600"  → 4자리 HHmm (앱 등록)
     *   "09:40" → 5자리 HH:mm (기존 DB 데이터)
     */
    public static LocalTime fromHhnn(String hhnn) {
        if (hhnn == null || hhnn.isEmpty()) return null;
        try {
            // 4자리: HHmm
            if (hhnn.length() == 4) {
                int hh = Integer.parseInt(hhnn.substring(0, 2));
                int nn = Integer.parseInt(hhnn.substring(2, 4));
                return LocalTime.of(hh, nn);
            }
            // 5자리: HH:mm
            if (hhnn.length() == 5 && hhnn.charAt(2) == ':') {
                return LocalTime.parse(hhnn);
            }
        } catch (Exception e) { /* fall through */ }
        return null;
    }
}

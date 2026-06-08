package com.acasian.iot.model.request;

import com.google.gson.annotations.SerializedName;

/**
 * 센서 통계 요청 바디 (일/주/월 공통)
 * {
 *   "lteNo"  : "8988228066614774719",
 *   "stTime" : "2026-05-01",
 *   "edTime" : "2026-05-31"
 * }
 *
 * 엔드포인트:
 *   POST v1/stat/getDailySensingStat
 *   POST v1/stat/getWeeklySensingStat
 *   POST v1/stat/getMonthlySensingStat
 */
public class SensingStatRequest {

    @SerializedName("lteNo")
    public final String lteNo;

    /** 조회 시작일 "yyyy-MM-dd" */
    @SerializedName("stTime")
    public final String stTime;

    /** 조회 종료일 "yyyy-MM-dd" */
    @SerializedName("edTime")
    public final String edTime;

    public SensingStatRequest(String lteNo, String stTime, String edTime) {
        this.lteNo  = lteNo;
        this.stTime = stTime;
        this.edTime = edTime;
    }
}

package com.acasian.iot.network;

import com.acasian.iot.model.request.SensingStatRequest;
import com.acasian.iot.model.response.SensingStatResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 센서 통계(정보조회 &gt; 변화값 분석) 전용 Retrofit 인터페이스.
 *
 * 기존 ApiService.java(1500줄)를 수정하지 않고 분리한 별도 서비스.
 * ApiClient 의 동일한 Retrofit(같은 BASE_URL · OkHttp · Auth · Gson)으로 생성:
 *
 *   StatApiService stat = ApiClient.getInstance(context)
 *           .getRetrofit().create(StatApiService.class);
 *
 * 엔드포인트 / 요청·응답 규약은 기존 v1/* 와 동일.
 */
public interface StatApiService {

    /** 일별 센서 통계 — /aca/v1/stat/getDailySensingStat */
    @POST("v1/stat/getDailySensingStat")
    Call<SensingStatResponse> getDailySensingStat(@Body SensingStatRequest request);

    /** 주별 센서 통계 — /aca/v1/stat/getWeeklySensingStat */
    @POST("v1/stat/getWeeklySensingStat")
    Call<SensingStatResponse> getWeeklySensingStat(@Body SensingStatRequest request);

    /** 월별 센서 통계 — /aca/v1/stat/getMonthlySensingStat */
    @POST("v1/stat/getMonthlySensingStat")
    Call<SensingStatResponse> getMonthlySensingStat(@Body SensingStatRequest request);
}

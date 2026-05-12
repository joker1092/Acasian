package com.acasian.iot.network;

import android.content.Context;
import android.util.Log;

import com.acasian.iot.BuildConfig;
import com.acasian.iot.storage.TokenManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit 싱글톤 클라이언트.
 *
 * ┌─ 서버 주소 변경 방법 ────────────────────────────────────────────────┐
 * │  app/build.gradle > buildTypes > debug / release 의                  │
 * │  buildConfigField "String", "BASE_URL", '"https://..."'  수정        │
 * │  → 빌드 후 BuildConfig.BASE_URL 에 자동 반영                         │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * 사용:
 *   ApiService api = ApiClient.getInstance(context).getService();
 *   api.login(...).enqueue(...);
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    // 타임아웃 설정 (초)
    private static final int TIMEOUT_CONNECT = 10;
    private static final int TIMEOUT_READ    = 30;
    private static final int TIMEOUT_WRITE   = 30;

    private static volatile ApiClient instance;

    private final Retrofit    retrofit;
    private final ApiService  apiService;

    // ── 생성자 ──────────────────────────────────────────────────────────
    private ApiClient(Context context) {
        TokenManager tokenManager = TokenManager.getInstance(context);

        // 1. 로그 인터셉터 (DEBUG 빌드만 활성화)
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(
                message -> Log.d(TAG, message));
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        // 2. OkHttpClient 조립
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_CONNECT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_READ,    TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_WRITE,  TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(tokenManager))  // JWT 자동 삽입
                .addInterceptor(logging)                            // 요청/응답 로그
                .build();

        // 3. Gson (날짜 포맷 등 커스텀)
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .create();

        // 4. Retrofit 조립
        // BASE_URL: BuildConfig.BASE_URL (build.gradle buildTypes에서 정의)
        retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        apiService = retrofit.create(ApiService.class);

        Log.d(TAG, "ApiClient initialized. BASE_URL = " + BuildConfig.BASE_URL);
    }

    // ── 싱글톤 접근 ─────────────────────────────────────────────────────
    public static ApiClient getInstance(Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) instance = new ApiClient(context.getApplicationContext());
            }
        }
        return instance;
    }

    /** ApiService 반환 */
    public ApiService getService() { return apiService; }

    /** Retrofit 원본 반환 (필요 시 다른 서비스 생성용) */
    public Retrofit getRetrofit() { return retrofit; }
}

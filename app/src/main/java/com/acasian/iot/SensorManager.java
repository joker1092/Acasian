package com.acasian.iot;

import com.acasian.iot.AppConfig;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.acasian.iot.model.SensorData;

/**
 * 환경 센서 데이터 관리.
 *
 * 현재: 더미 데이터 + 의도적 오류 시뮬레이션
 * 추후: ApiService.getLatestSensors() 실제 연동으로 교체
 *
 * 사용:
 *   SensorManager.fetch(context, new SensorManager.Callback() {
 *       public void onSuccess(SensorData data) { ... }
 *       public void onError(String message)    { ... }
 *   });
 */
public class SensorManager {

    // 테스트용 플래그: true = 오류 시뮬레이션
    private static final boolean SIMULATE_ERROR = false;

    // 로딩 딜레이 (ms) - 실제 API 연동 시 제거
    private static final int FAKE_DELAY_MS = 1200;

    public interface Callback {
        void onSuccess(SensorData data);
        void onError(String message);
    }

    /**
     * 센서 데이터 요청.
     * UI 스레드에서 호출, 콜백도 메인 스레드로 전달.
     */
    public static void fetch(Context context, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // 백그라운드 스레드에서 API 호출 (현재는 더미)
        new Thread(() -> {
            try {
                Thread.sleep(FAKE_DELAY_MS);
            } catch (InterruptedException ignored) {}

            mainHandler.post(() -> {
                if (SIMULATE_ERROR) {
                    callback.onError("센서 정보를 읽어 올 수 없습니다.");
                    return;
                }

                // DEV_MODE: 더미 데이터
                // TODO: 상용 모드 — ApiClient.getInstance(context).getService()
                //         .getLatestSensors().enqueue(...) 으로 교체
                if (AppConfig.getInstance().isDevMode()) {
                    SensorData data = new SensorData(24.5f, 68, 3200, "정상");
                    callback.onSuccess(data);
                } else {
                    // 상용 모드: API 연동 전 — 데이터 없음으로 처리
                    callback.onError("— 데이터 준비중 —");
                }
            });
        }).start();
    }
}

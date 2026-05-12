package com.acasian.iot.repository;

import android.content.Context;
import android.util.Log;

import com.acasian.iot.network.ApiClient;
import com.acasian.iot.network.ApiService;
import com.acasian.iot.storage.TokenManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 밸브 제어 / 상태 조회 API 전담 Repository.
 *
 * ─ 제어 (updNodeValve) ───────────────────────────────────────────────────
 *   repo.startValve(lteNo, nodeId, nodeType, minutes, callback)
 *   repo.stopValve (lteNo, nodeId, nodeType, callback)
 *
 * ─ 상태 조회 (reqNodeStatus) ────────────────────────────────────────────
 *   repo.requestNodeStatus(lteNo, callback)   ← 사용처는 차후 연동
 */
public class ValveRepository {

    private static final String TAG = "ValveRepo";

    private final ApiService   apiService;
    private final TokenManager tokenManager;

    public ValveRepository(Context context) {
        this.apiService   = ApiClient.getInstance(context).getService();
        this.tokenManager = TokenManager.getInstance(context);
    }

    // ── 콜백 ─────────────────────────────────────────────────────────────

    public interface ValveCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface NodeStatusCallback {
        void onSuccess(ApiService.NodeStatusResponse response);
        void onFailure(String errorMessage);
    }

    // ── 관수 시작 (command=1) ─────────────────────────────────────────────

    /**
     * @param lteNo        컨트롤박스 LTE 번호
     * @param nodeId       밸브 번호 (0~F)
     * @param nodeType     "S"=서보모터, "P"=probe센서, "Z"=메인함
     * @param vtimeMinutes 관수 시간(분)
     */
    public void startValve(String lteNo, String nodeId, String nodeType,
                           int vtimeMinutes, ValveCallback callback) {
        call(lteNo, nodeId, nodeType, ApiService.DeviceRequest.CMD_START, vtimeMinutes, callback);
    }

    // ── 일시 멈춤 (command=2) ─────────────────────────────────────────────

    public void pauseValve(String lteNo, String nodeId, String nodeType,
                           ValveCallback callback) {
        call(lteNo, nodeId, nodeType, ApiService.DeviceRequest.CMD_PAUSE, 0, callback);
    }

    // ── 종료 (command=3) ─────────────────────────────────────────────────

    public void stopValve(String lteNo, String nodeId, String nodeType,
                          ValveCallback callback) {
        call(lteNo, nodeId, nodeType, ApiService.DeviceRequest.CMD_OFF, 0, callback);
    }

    // ── 관수 상태 조회 (reqNodeStatus) ────────────────────────────────────
    // 사용처는 차후 연동 예정

    public void requestNodeStatus(String lteNo, NodeStatusCallback callback) {
        String userId = tokenManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("로그인 정보가 없습니다."); return;
        }
        if (lteNo == null || lteNo.isEmpty()) {
            callback.onFailure("LTE 번호가 없습니다."); return;
        }

        ApiService.NodeStatusRequest req = new ApiService.NodeStatusRequest(userId, lteNo);
        Log.d(TAG, "reqNodeStatus → lteNo=" + lteNo);

        apiService.reqNodeStatus(req).enqueue(
                new Callback<ApiService.NodeStatusResponse>() {
            @Override
            public void onResponse(Call<ApiService.NodeStatusResponse> call,
                                   Response<ApiService.NodeStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure("상태 조회 실패 (" + response.code() + ")");
                }
            }
            @Override
            public void onFailure(Call<ApiService.NodeStatusResponse> call, Throwable t) {
                Log.e(TAG, "reqNodeStatus 오류", t);
                callback.onFailure("네트워크 오류: " + t.getMessage());
            }
        });
    }

    // ── 공통 호출 ────────────────────────────────────────────────────────

    private void call(String lteNo, String nodeId, String nodeType,
                      String command, int vtimeMinutes, ValveCallback callback) {

        String userId = tokenManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("로그인 정보가 없습니다."); return;
        }
        if (lteNo == null || lteNo.isEmpty()) {
            // lteNo 없으면 (데모 모드 등) API 스킵 → 성공 처리
            Log.d(TAG, "[DEMO] lteNo 없음 → API 스킵 nodeId=" + nodeId + " cmd=" + command);
            callback.onSuccess();
            return;
        }

        ApiService.DeviceRequest req = new ApiService.DeviceRequest(
                userId, lteNo, nodeId, nodeType, command, vtimeMinutes);

        Log.d(TAG, "updNodeValve → lteNo=" + lteNo
                + " nodeId=" + nodeId + " nodeType=" + nodeType
                + " cmd=" + command + " vtime=" + vtimeMinutes);

        apiService.updNodeValve(req).enqueue(new Callback<ApiService.ValveResponse>() {
            @Override
            public void onResponse(Call<ApiService.ValveResponse> call,
                                   Response<ApiService.ValveResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    Log.d(TAG, "updNodeValve 성공");
                    callback.onSuccess();
                } else {
                    callback.onFailure("밸브 제어에 실패했습니다.");
                }
            }
            @Override
            public void onFailure(Call<ApiService.ValveResponse> call, Throwable t) {
                Log.e(TAG, "updNodeValve 네트워크 오류", t);
                callback.onFailure("네트워크 오류: " + t.getMessage());
            }
        });
    }
}

package com.acasian.iot.repository;

import android.content.Context;

import com.acasian.iot.ZoneStore;
import com.acasian.iot.model.response.FarmInfoResponse;
import com.acasian.iot.network.ApiClient;
import com.acasian.iot.network.ApiService;
import com.acasian.iot.storage.TokenManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 농장 정보 관련 비즈니스 로직.
 *
 * 사용:
 *   FarmRepository repo = new FarmRepository(context);
 *   repo.fetchFarmInfo(new FarmRepository.FarmInfoCallback() {
 *       public void onSuccess(FarmInfoResponse res) { ... }
 *       public void onFailure(String msg)           { ... }
 *   });
 */
public class FarmRepository {

    private final ApiService   apiService;
    private final TokenManager tokenManager;

    public FarmRepository(Context context) {
        this.apiService   = ApiClient.getInstance(context).getService();
        this.tokenManager = TokenManager.getInstance(context);
    }

    // ── 콜백 인터페이스 ──────────────────────────────────────────────────

    public interface FarmInfoCallback {
        void onSuccess(FarmInfoResponse response);
        void onFailure(String errorMessage);
    }

    // ── 농장 정보 조회 ───────────────────────────────────────────────────

    /**
     * POST v1/main/farminfo → ZoneStore 자동 갱신
     * Body: { "userId": "01012341234" }
     */
    public void fetchFarmInfo(FarmInfoCallback callback) {
        String userId = tokenManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("로그인 정보가 없습니다. 다시 로그인해 주세요.");
            return;
        }

        ApiService.FarmInfoRequest request = new ApiService.FarmInfoRequest(userId);

        apiService.getFarmInfo(request).enqueue(new Callback<FarmInfoResponse>() {

            @Override
            public void onResponse(Call<FarmInfoResponse> call,
                                   Response<FarmInfoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FarmInfoResponse body = response.body();
                    if (body.isSuccess()) {
                        // ── 응답 → ZoneStore 자동 갱신 ──────────────
                        applyToZoneStore(body);
                        callback.onSuccess(body);
                    } else {
                        String msg = body.getMessage();
                        callback.onFailure(msg != null && !msg.isEmpty()
                                ? msg : "농장 정보를 불러오지 못했습니다.");
                    }
                } else {
                    callback.onFailure(parseHttpError(response.code()));
                }
            }

            @Override
            public void onFailure(Call<FarmInfoResponse> call, Throwable t) {
                callback.onFailure("네트워크 연결을 확인해 주세요.\n(" + t.getMessage() + ")");
            }
        });
    }

    // ── FarmInfoResponse → ZoneStore 변환 ───────────────────────────────

    /**
     * farminfo 응답의 mainlist(컨트롤박스) + nodelist(밸브)를
     * ZoneStore.ZoneInfo / NodeInfo 구조로 변환하여 ZoneStore에 저장.
     *
     * farminfo.mainlist:
     *   lteNo      → ZoneInfo.telNo  (밸브 제어 시 telNo로 사용)
     *   mainName   → ZoneInfo.name
     *   nodelist:
     *     nodeId   → NodeInfo.nodeId
     *     nodeName → NodeInfo.name
     *     nodeType = "S" (서보모터 밸브)만 포함 — 센서(P) 제외
     */
    public static void applyToZoneStore(FarmInfoResponse response) {
        if (response == null || response.getFarmInfoList() == null) return;

        List<ZoneStore.ZoneInfo> zones = new ArrayList<>();

        for (FarmInfoResponse.FarmInfo farm : response.getFarmInfoList()) {
            if (farm.getMainList() == null) continue;

            for (FarmInfoResponse.MainInfo main : farm.getMainList()) {
                String telNo = main.getLteNo();
                String name  = main.getMainName();
                if (telNo == null || telNo.isEmpty()) continue;

                List<ZoneStore.NodeInfo> nodes = new ArrayList<>();
                if (main.getNodeList() != null) {
                    for (FarmInfoResponse.NodeInfo node : main.getNodeList()) {
                        // 서보모터 밸브(S)만 포함, 센서(P)·메인함(Z) 제외
                        if (!"S".equals(node.getNodeType())) continue;
                        nodes.add(new ZoneStore.NodeInfo(
                                node.getNodeId(),
                                node.getNodeName() != null ? node.getNodeName()
                                        : "밸브 " + node.getNodeId(),
                                node.getValveStatus()));  // 초기 상태값 전달
                    }
                }
                zones.add(new ZoneStore.ZoneInfo(telNo, name, nodes));
            }
        }

        ZoneStore.getInstance().update(zones);
    }

    // ── HTTP 에러 ────────────────────────────────────────────────────────
    private String parseHttpError(int code) {
        switch (code) {
            case 400: return "요청 형식이 올바르지 않습니다.";
            case 401: return "인증이 만료되었습니다. 다시 로그인해 주세요.";
            case 403: return "접근 권한이 없습니다.";
            case 404: return "서비스를 찾을 수 없습니다.";
            case 500: return "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            default:  return "오류가 발생했습니다. (코드: " + code + ")";
        }
    }
}


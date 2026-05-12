package com.acasian.iot.repository;

import android.content.Context;

import com.acasian.iot.model.request.LoginRequest;
import com.acasian.iot.model.response.ApiResponse;
import com.acasian.iot.model.response.LoginResponse;
import com.acasian.iot.network.ApiClient;
import com.acasian.iot.network.ApiService;
import com.acasian.iot.storage.TokenManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 인증 관련 비즈니스 로직 집약.
 * Activity/Fragment 는 이 Repository 만 호출 → 네트워크 코드 분리.
 *
 * 사용:
 *   UserRepository repo = new UserRepository(context);
 *   repo.login("hong", "1234", new UserRepository.LoginCallback() {
 *       public void onSuccess(LoginResponse res) { ... }
 *       public void onFailure(String errorMsg)   { ... }
 *   });
 */
public class UserRepository {

    private final ApiService    apiService;
    private final TokenManager  tokenManager;

    public UserRepository(Context context) {
        this.apiService   = ApiClient.getInstance(context).getService();
        this.tokenManager = TokenManager.getInstance(context);
    }

    // ── 콜백 인터페이스 ──────────────────────────────────────────────────

    public interface LoginCallback {
        void onSuccess(LoginResponse response);
        void onFailure(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    // ── 로그인 ───────────────────────────────────────────────────────────
    public void login(String userId, String password, LoginCallback callback) {
        LoginRequest request = new LoginRequest(userId, password);

        apiService.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse body = response.body();
                    if (body.isSuccess()) {
                        // 토큰 없음 → userId, 농장명(nickName)만 저장
                        tokenManager.saveUserId(body.getUserId());
                        // 농장명은 SessionManager에 저장 (있을 경우)
                        callback.onSuccess(body);
                    } else {
                        // result == "false" → 서버 메시지 전달
                        String msg = body.getMessage();
                        callback.onFailure(msg != null && !msg.isEmpty()
                                ? msg : "아이디 또는 비밀번호가 올바르지 않습니다.");
                    }
                } else {
                    // HTTP 4xx / 5xx
                    callback.onFailure(parseHttpError(response.code()));
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                // 네트워크 오류
                callback.onFailure("네트워크 연결을 확인해 주세요.\n(" + t.getMessage() + ")");
            }
        });
    }

    // ── 로그아웃 ─────────────────────────────────────────────────────────
    public void logout(SimpleCallback callback) {
        apiService.logout().enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call,
                                   Response<ApiResponse<Void>> response) {
                tokenManager.clear(); // 로컬 토큰 무조건 삭제
                callback.onSuccess();
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                tokenManager.clear(); // 실패해도 로컬 삭제
                callback.onSuccess();
            }
        });
    }

    // ── 토큰 갱신 ────────────────────────────────────────────────────────
    public void refreshToken(SimpleCallback callback) {
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null) {
            callback.onFailure("저장된 토큰이 없습니다.");
            return;
        }

       /* ApiService.RefreshRequest req = new ApiService.RefreshRequest(refreshToken);
        apiService.refreshToken(req).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse body = response.body();
                    tokenManager.saveTokens(body.getAccessToken(), body.getRefreshToken());
                    callback.onSuccess();
                } else {
                    tokenManager.clear(); // 갱신 실패 → 강제 로그아웃
                    callback.onFailure("세션이 만료되었습니다. 다시 로그인해 주세요.");
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                callback.onFailure("토큰 갱신 실패: " + t.getMessage());
            }
        });*/
    }    // ── 로그인 상태 확인 ─────────────────────────────────────────────────
    public boolean isLoggedIn() { return tokenManager.hasToken(); }

    // ── HTTP 에러 코드 → 한국어 메시지 ──────────────────────────────────
    private String parseHttpError(int code) {
        switch (code) {
            case 400: return "요청 형식이 올바르지 않습니다.";
            case 401: return "아이디 또는 비밀번호가 올바르지 않습니다.";
            case 403: return "접근 권한이 없습니다.";
            case 404: return "서비스를 찾을 수 없습니다.";
            case 500: return "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            default:  return "오류가 발생했습니다. (코드: " + code + ")";
        }
    }
}

package com.acasian.iot.network;

import androidx.annotation.NonNull;

import com.acasian.iot.storage.TokenManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 모든 요청에 JWT Authorization 헤더를 자동 삽입하는 OkHttp Interceptor.
 *
 * 삽입 형태:
 *   Authorization: Bearer eyJhbGci...
 *
 * 토큰이 없으면 헤더 없이 그대로 통과 (로그인 API 등).
 */
public class AuthInterceptor implements Interceptor {

    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String  token    = tokenManager.getAccessToken();

        // 토큰 없으면 헤더 미삽입 (로그인/회원가입 등 인증 불필요 API)
        if (token == null || token.isEmpty()) {
            return chain.proceed(original);
        }

        Request authenticated = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        return chain.proceed(authenticated);
    }
}

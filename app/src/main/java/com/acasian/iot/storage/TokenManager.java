package com.acasian.iot.storage;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * JWT Access/Refresh 토큰 로컬 저장소.
 * SharedPreferences 기반 (추후 EncryptedSharedPreferences 교체 가능).
 *
 * 사용:
 *   TokenManager tm = TokenManager.getInstance(context);
 *   tm.saveTokens(accessToken, refreshToken);
 *   String token = tm.getAccessToken();
 */
public class TokenManager {

    private static final String PREF_NAME     = "aaa_iot_auth";
    private static final String KEY_ACCESS    = "access_token";
    private static final String KEY_REFRESH   = "refresh_token";
    private static final String KEY_USER_ID   = "user_id";

    private static volatile TokenManager instance;
    private final SharedPreferences prefs;

    private TokenManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static TokenManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TokenManager.class) {
                if (instance == null) instance = new TokenManager(context);
            }
        }
        return instance;
    }

    // ── 토큰 저장 ────────────────────────────────────────────────────────
    public void saveTokens(String accessToken, String refreshToken) {
        prefs.edit()
             .putString(KEY_ACCESS,  accessToken)
             .putString(KEY_REFRESH, refreshToken)
             .apply();
    }

    // ── 조회 ─────────────────────────────────────────────────────────────
    public String getAccessToken()  { return prefs.getString(KEY_ACCESS,  null); }
    public String getRefreshToken() { return prefs.getString(KEY_REFRESH, null); }
    public boolean hasToken()       { return getAccessToken() != null; }

    // ── 사용자 ID 저장 ───────────────────────────────────────────────────
    public void saveUserId(String userId) { prefs.edit().putString(KEY_USER_ID, userId).apply(); }
    public String getUserId()             { return prefs.getString(KEY_USER_ID, null); }

    // ── 로그아웃: 전체 초기화 ────────────────────────────────────────────
    public void clear() { prefs.edit().clear().apply(); }
}

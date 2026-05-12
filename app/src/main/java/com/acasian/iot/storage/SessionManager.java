package com.acasian.iot.storage;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 로그인 세션 관리 (토큰 없이 SharedPreferences 방식)
 *
 * 저장 항목:
 *   - is_logged_in : 로그인 여부
 *   - phone_number : 전화번호 (화면 표시용)
 *   - user_name    : 사용자 이름 (서버에서 받은 경우)
 *   - farm_id      : farmInfo API 응답의 farmId
 */
public class SessionManager {

    private static final String PREF_NAME    = "aaa_session";
    private static final String KEY_LOGGED   = "is_logged_in";
    private static final String KEY_PHONE    = "phone_number";
    private static final String KEY_NAME     = "user_name";
    private static final String KEY_FARM_ID  = "farm_id";

    private static volatile SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) instance = new SessionManager(context);
            }
        }
        return instance;
    }

    /** 로그인 성공 시 저장 */
    public void saveLogin(String phoneNumber, String userName) {
        prefs.edit()
             .putBoolean(KEY_LOGGED, true)
             .putString(KEY_PHONE, phoneNumber)
             .putString(KEY_NAME, userName != null ? userName : "")
             .apply();
    }

    /** farmInfo API 응답 후 farmId 저장 */
    public void saveFarmId(String farmId) {
        prefs.edit().putString(KEY_FARM_ID, farmId != null ? farmId : "").apply();
    }

    /** farmId 조회 */
    public String getFarmId() { return prefs.getString(KEY_FARM_ID, ""); }

    /** 로그인 여부 */
    public boolean isLoggedIn() { return prefs.getBoolean(KEY_LOGGED, false); }

    /** 전화번호 반환 — 숫자만 (서버 userId 형식) */
    public String getPhoneNumber() {
        String phone = prefs.getString(KEY_PHONE, "");
        return phone.replaceAll("[^0-9]", "");
    }

    /** 전화번호 원본 반환 (화면 표시용) */
    public String getPhoneNumberFormatted() {
        return prefs.getString(KEY_PHONE, "");
    }

    /** 사용자 이름 조회 */
    public String getUserName() { return prefs.getString(KEY_NAME, ""); }

    /** 로그아웃: 세션 초기화 */
    public void logout() { prefs.edit().clear().apply(); }
}

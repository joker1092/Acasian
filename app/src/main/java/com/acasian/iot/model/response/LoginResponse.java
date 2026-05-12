package com.acasian.iot.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * POST v1/login 응답
 * {
 *   "result"   : "true",
 *   "message"  : "success",
 *   "userinfo" : {
 *       "userId"   : "01012341234",
 *       "password" : null,
 *       "nickName" : "여주농장",
 *       "phone"    : null,
 *       "enroll"   : null
 *   }
 * }
 *
 * ※ result가 boolean이 아닌 String "true"/"false" 임에 주의
 */
public class LoginResponse {

    @SerializedName("result")
    private String result;      // "true" or "false"

    @SerializedName("message")
    private String message;

    @SerializedName("userinfo")
    private UserInfo userInfo;

    // ── 편의 메서드 ──────────────────────────────────────────────────────
    /** 서버 result == "true" 일 때 성공으로 판단 */
    public boolean isSuccess() {
        return "true".equalsIgnoreCase(result);
    }

    public String  getResult()   { return result; }
    public String  getMessage()  { return message; }
    public UserInfo getUserInfo(){ return userInfo; }

    // ── 하위 호환 getter (UserRepository에서 사용) ───────────────────────
    /** 로그인한 사용자 ID (전화번호) */
    public String getUserId() {
        return userInfo != null ? userInfo.getUserId() : null;
    }

    /** 농장명 (닉네임) */
    public String getFarmName() {
        return userInfo != null ? userInfo.getNickName() : null;
    }

    // access_token / refresh_token 없음 → null 반환 (TokenManager 호환)
    public String getAccessToken()  { return null; }
    public String getRefreshToken() { return null; }

    // ── 중첩 userinfo 객체 ───────────────────────────────────────────────
    public static class UserInfo {

        @SerializedName("userId")
        private String userId;

        @SerializedName("password")
        private String password;   // 항상 null로 내려옴

        @SerializedName("nickName")
        private String nickName;

        @SerializedName("phone")
        private String phone;

        @SerializedName("enroll")
        private String enroll;

        public String getUserId()  { return userId; }
        public String getNickName(){ return nickName; }
        public String getPhone()   { return phone; }
        public String getEnroll()  { return enroll; }
    }
}

package com.acasian.iot.model.request;

import com.google.gson.annotations.SerializedName;

/**
 * POST v1/login 요청 바디
 * {
 *   "userId"   : "01012341234",
 *   "password" : "1234"
 * }
 */
public class LoginRequest {

    @SerializedName("userId")
    private final String userId;

    @SerializedName("password")
    private final String password;

    public LoginRequest(String userId, String password) {
        this.userId   = userId;
        this.password = password;
    }

    public String getUserId()  { return userId; }
    public String getPassword(){ return password; }
}

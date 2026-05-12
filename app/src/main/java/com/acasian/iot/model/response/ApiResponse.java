package com.acasian.iot.model.response;

import com.google.gson.annotations.SerializedName;

/**
 * 서버 공통 응답 래퍼.
 * 대부분의 API가 아래 형태를 공유:
 * {
 *   "success" : true,
 *   "message" : "처리 완료",
 *   "data"    : { ... }
 * }
 *
 * 사용 예: ApiResponse<UserProfile>
 */
public class ApiResponse<T> {

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private T data;

    public boolean isSuccess() { return success; }
    public String  getMessage(){ return message; }
    public T       getData()   { return data; }
}

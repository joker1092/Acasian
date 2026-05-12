package com.acasian.iot.network;

import com.acasian.iot.model.request.LoginRequest;
import com.acasian.iot.model.response.ApiResponse;
import com.acasian.iot.model.response.FarmInfoResponse;
import com.acasian.iot.model.response.LoginResponse;

import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit API 인터페이스.
 *
 * BASE_URL 은 BuildConfig.BASE_URL (build.gradle buildTypes 에서 정의).
 *
 * ─ 엔드포인트 규칙 ───────────────────────────────────────────────────────
 *  POST   /auth/login          → 로그인
 *  POST   /auth/logout         → 로그아웃
 *  POST   /auth/refresh        → 토큰 갱신
 *
 *  GET    /devices             → 전체 기기 목록
 *  GET    /devices/{id}        → 기기 상세
 *  PUT    /devices/{id}/toggle → 기기 ON/OFF
 *
 *  GET    /zones               → 구역 목록
 *  PUT    /zones/{id}/toggle   → 구역 전체 ON/OFF
 *
 *  GET    /schedules?date=     → 날짜별 예약 목록
 *  POST   /schedules           → 예약 등록
 *  DELETE /schedules/{id}      → 예약 취소
 *
 *  GET    /sensors/latest      → 최신 센서 데이터
 * ────────────────────────────────────────────────────────────────────────
 */
public interface ApiService {

    // ── 인증 ─────────────────────────────────────────────────────────────

/** 로그인 */

    @POST("v1/login")
    Call<LoginResponse> login(@Body LoginRequest request);


/** 로그아웃 (서버 토큰 무효화) */

    @POST("v1/logout")
    Call<ApiResponse<Void>> logout();


/** Access Token 갱신 */
/*

    @POST("auth/refresh")
    Call<LoginResponse> refreshToken(@Body RefreshRequest request);

*/


    // ════════════════════════════════════════════════════════════════
    // ── 스케줄 유형 (관수 유형) ──────────────────────────────────
    // ════════════════════════════════════════════════════════════════

    /** GET 스케줄 유형 조회 — /aca/v1/setting/getScheduleCate */
    @POST("v1/setting/getScheduleCate")
    Call<ScheduleCateListResponse> getScheduleCate(@Body ScheduleCateRequest request);

    /** POST 스케줄 유형 입력 — /aca/v1/setting/addScheduleCate */
    @POST("v1/setting/addScheduleCate")
    Call<CateAddResponse> addScheduleCate(@Body ScheduleCateAddRequest request);

    /** POST 스케줄 유형 수정 — /aca/v1/setting/updScheduleCate */
    @POST("v1/setting/updScheduleCate")
    Call<ApiResponse<Void>> updScheduleCate(@Body ScheduleCateUpdRequest request);

    /** POST 스케줄 유형 삭제 — /aca/v1/setting/delScheduleCate */
    @POST("v1/setting/delScheduleCate")
    Call<ApiResponse<Void>> delScheduleCate(@Body ScheduleCateDelRequest request);

    // ════════════════════════════════════════════════════════════════
    // ── 스케줄 유형 상세 / 그룹 (v1.5 신규) ─────────────────────
    // ════════════════════════════════════════════════════════════════

    /** 스케줄 유형 상세 조회 (그룹·노드 포함) */
    @POST("v1/setting/getScheduleCateDetail")
    Call<ScheduleCateDetailResponse> getScheduleCateDetail(@Body CateIdRequest request);

    /** 유형별 그룹 조회 */
    @POST("v1/setting/getScheduleGroup")
    Call<ScheduleGroupListResponse> getScheduleGroup(@Body ScheduleGroupRequest request);

    /** 유형별 그룹 추가 */
    @POST("v1/setting/addScheduleGroup")
    Call<GroupAddResponse> addScheduleGroup(@Body ScheduleGroupAddRequest request);

    /** 유형별 그룹 수정 */
    @POST("v1/setting/updScheduleGroup")
    Call<ApiResponse<Void>> updScheduleGroup(@Body ScheduleGroupUpdRequest request);

    /** 유형별 그룹 삭제 */
    @POST("v1/setting/delScheduleGroup")
    Call<ApiResponse<Void>> delScheduleGroup(@Body GroupIdRequest request);

    /** 그룹 상세(노드) 조회 */
    @POST("v1/setting/getScheduleGroupDetail")
    Call<ScheduleGroupDetailResponse> getScheduleGroupDetail(@Body GroupIdRequest request);

    /** 그룹 상세(노드) 추가/수정 — jsonArray */
    @POST("v1/setting/addScheduleGroupDetail")
    Call<ApiResponse<Void>> addScheduleGroupDetail(@Body java.util.List<ScheduleGroupDetailItem> request);

    /** 그룹 상세(노드) 삭제 */
    @POST("v1/setting/delScheduleGroupDetail")
    Call<ApiResponse<Void>> delScheduleGroupDetail(@Body GroupIdRequest request);

    /** 유형 그룹별 On/Off command 조회 */
    @POST("v1/setting/getScheduleCateGrpCmd")
    Call<ScheduleCateGrpCmdResponse> getScheduleCateGrpCmd(@Body CateIdRequest request);

    /** 유형 그룹별 On/Off command 추가/수정 — jsonArray */
    @POST("v1/setting/addScheduleCateGrpCmd")
    Call<ApiResponse<Void>> addScheduleCateGrpCmd(@Body java.util.List<ScheduleCateGrpCmdItem> request);

    /** 유형 그룹별 On/Off command 전체 삭제 */
    @POST("v1/setting/delScheduleCateGrpCmd")
    Call<ApiResponse<Void>> delScheduleCateGrpCmd(@Body CateIdRequest request);

    /** 유형 그룹별 On/Off command 수동 생성 */
    @POST("v1/setting/genScheduleCateGrpCmd")
    Call<ApiResponse<Void>> genScheduleCateGrpCmd(@Body SchIdRequest request);

    // ════════════════════════════════════════════════════════════════
    // ── 스케줄 (예약) ────────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════

    /** POST 스케줄 조회 — /aca/v1/setting/getSchedule */
    @POST("v1/setting/getSchedule")
    Call<ScheduleListResponse> getSchedule(@Body ScheduleGetRequest request);

    /** POST 스케줄 단건 조회 — /aca/v1/setting/getScheduleById */
    @POST("v1/setting/getScheduleById")
    Call<ScheduleSingleResponse> getScheduleById(@Body SchIdRequest request);

    /** POST 스케줄 입력 — /aca/v1/setting/addSchedule */
    @POST("v1/setting/addSchedule")
    Call<ScheduleAddResponse> addSchedule(@Body ScheduleAddRequest request);

    /** POST 스케줄 수정 — /aca/v1/setting/updSchedule */
    @POST("v1/setting/updSchedule")
    Call<ApiResponse<Void>> updSchedule(@Body ScheduleUpdRequest request);

    /** POST 스케줄 삭제 — /aca/v1/setting/delSchedule */
    @POST("v1/setting/delSchedule")
    Call<ApiResponse<Void>> delSchedule(@Body ScheduleDelRequest request);

    /** POST 스케줄 정지/재개 — /aca/v1/setting/stopSchedule
     *  isDel: 0=스케줄종료(정지), 1=진행중(재개) */
    @POST("v1/setting/stopSchedule")
    Call<ApiResponse<Void>> stopSchedule(@Body StopScheduleRequest request);

    // ════════════════════════════════════════════════════════════════
    // ── 계정 ─────────────────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════

    /** POST 비밀번호 변경 — /aca/v1/changepw */
    @POST("v1/changepw")
    Call<ApiResponse<Void>> changePw(@Body ChangePwRequest request);

    // ── 설치장비 환경 설정 ────────────────────────────────────────────────

    /** POST 설치장비 환경 조회 — /aca/v1/config/getMainEnv */
    @POST("v1/config/getMainEnv")
    Call<MainEnvResponse> getMainEnv(@Body MainEnvRequest request);

    /** POST 설치장비 환경 입력 — /aca/v1/config/addMainEnv */
    @POST("v1/config/addMainEnv")
    Call<com.acasian.iot.model.response.ApiResponse<Void>> addMainEnv(@Body MainEnvSaveRequest request);

    /** POST 설치장비 환경 수정 — /aca/v1/config/updMainEnv */
    @POST("v1/config/updMainEnv")
    Call<com.acasian.iot.model.response.ApiResponse<Void>> updMainEnv(@Body MainEnvSaveRequest request);

    // ── 기기 제어 ─────────────────────────────────────────────────────────

    /** POST v1/device/updNodeValve — 관수 제어 */
    @POST("v1/device/updNodeValve")
    Call<ValveResponse> updNodeValve(@Body DeviceRequest request);

    /** POST v1/device/reqNodeStatus — 관수 상태 조회 */
    @POST("v1/device/reqNodeStatus")
    Call<NodeStatusResponse> reqNodeStatus(@Body NodeStatusRequest request);

    // ── 농장 정보 조회 ────────────────────────────────────────────────────
    /** POST v1/main/farminfo — Body: { "userId": "01012341234" } */
    @POST("v1/main/farminfo")
    Call<FarmInfoResponse> getFarmInfo(@Body FarmInfoRequest request);

/** 전체 기기 목록 *//*

    @GET("devices")
    Call<ApiResponse<java.util.List<DeviceResponse>>> getDevices();

    */
/** 기기 ON/OFF 토글 *//*

    @PUT("devices/{deviceId}/toggle")
    Call<ApiResponse<DeviceResponse>> toggleDevice(
            @Path("deviceId") String deviceId,
            @Body ToggleRequest request);
*/

    // ── 구역 ─────────────────────────────────────────────────────────────
/*

    */
/** 구역 목록 *//*

    @GET("zones")
    Call<ApiResponse<java.util.List<ZoneResponse>>> getZones();

    */
/** 구역 마스터 ON/OFF *//*

    @PUT("zones/{zoneId}/toggle")
    Call<ApiResponse<ZoneResponse>> toggleZone(
            @Path("zoneId") String zoneId,
            @Body ToggleRequest request);
*/

    // ── 예약 일정 ─────────────────────────────────────────────────────────
/*

    */
/**
     * 날짜별 예약 목록
     * @param date "2025-03-06" 형식
     *//*

    @GET("schedules")
    Call<ApiResponse<java.util.List<ScheduleResponse>>> getSchedules(
            @Query("date") String date);

    */
/** 예약 등록 *//*

    @POST("schedules")
    Call<ApiResponse<ScheduleResponse>> addSchedule(@Body ScheduleRequest request);

    */
/** 예약 취소 *//*

    @DELETE("schedules/{scheduleId}")
    Call<ApiResponse<Void>> deleteSchedule(@Path("scheduleId") String scheduleId);

    // ── 센서 ─────────────────────────────────────────────────────────────

    */
/** 최신 센서 데이터 *//*

    @GET("sensors/latest")
    Call<ApiResponse<SensorResponse>> getLatestSensors();
*/

    // ════════════════════════════════════════════════════════════════════
    // 인라인 Request/Response DTO
    // (규모가 커지면 model/request, model/response 패키지로 분리 권장)
    // ════════════════════════════════════════════════════════════════════
    // ── FarmInfo 요청 DTO ────────────────────────────────────────────────
    class FarmInfoRequest {
        @com.google.gson.annotations.SerializedName("userId")
        public final String userId;
        public FarmInfoRequest(String userId) { this.userId = userId; }
    }

    /**
     * POST v1/device/updNodeValve 요청 바디
     * {
     *   "userId"   : "01012341234",
     *   "lteNo"    : "01029943269",  → 컨트롤박스 LTE 번호
     *   "nodeId"   : "0",            → 0~F (밸브 번호)
     *   "nodeType" : "S",            → S=서보모터, P=probe센서, Z=메인함
     *   "command"  : "1",            → 1=관수, 2=멈춤, 3=OFF
     *   "vtime"    : "30"            → 관수 시간(분)
     * }
     */
    class DeviceRequest {
        @com.google.gson.annotations.SerializedName("userId")
        public final String userId;

        @com.google.gson.annotations.SerializedName("lteNo")
        public final String lteNo;

        @com.google.gson.annotations.SerializedName("nodeId")
        public final String nodeId;

        @com.google.gson.annotations.SerializedName("nodeType")
        public final String nodeType;

        @com.google.gson.annotations.SerializedName("command")
        public final String command;

        @com.google.gson.annotations.SerializedName("vtime")
        public final String vtime;

        /** command 상수 */
        public static final String CMD_START = "1";  // 관수 시작
        public static final String CMD_PAUSE = "2";  // 일시 멈춤
        public static final String CMD_OFF   = "3";  // 종료

        /** nodeType 상수 */
        public static final String TYPE_SERVO = "S";  // 서보모터 (밸브)
        public static final String TYPE_PROBE = "P";  // probe 센서
        public static final String TYPE_MAIN  = "Z";  // 메인함

        public DeviceRequest(String userId, String lteNo, String nodeId,
                             String nodeType, String command, int vtimeMinutes) {
            this.userId   = userId;
            this.lteNo    = lteNo;
            this.nodeId   = nodeId;
            this.nodeType = nodeType;
            this.command  = command;
            this.vtime    = String.valueOf(vtimeMinutes);
        }
    }

    // ── NodeStatus 요청 DTO ──────────────────────────────────────────────
    /**
     * POST v1/device/reqNodeStatus 요청 바디
     * { "userId": "01012341234", "lteNo": "01029943269" }
     */
    class NodeStatusRequest {
        @com.google.gson.annotations.SerializedName("userId")
        public final String userId;

        @com.google.gson.annotations.SerializedName("lteNo")
        public final String lteNo;

        public NodeStatusRequest(String userId, String lteNo) {
            this.userId = userId;
            this.lteNo  = lteNo;
        }
    }

    // ── NodeStatus 응답 DTO ──────────────────────────────────────────────
    /**
     * POST v1/device/reqNodeStatus 응답
     * {
     *   "status": "success",
     *   "data": {
     *     "lteNo": "821029943269",
     *     "valveStatus": 3,
     *     "nodelist": [
     *       { "nodeId":"0", "valveStatus":3, "nodeType":"S", ... },
     *       { "nodeId":"1", "valveStatus":1, "nodeType":"S", ... }
     *     ]
     *   }
     * }
     */
    public static class NodeStatusResponse {
        @com.google.gson.annotations.SerializedName("status")
        public String status;
        @com.google.gson.annotations.SerializedName("result")
        public String result;

        @com.google.gson.annotations.SerializedName("data")
        public NodeStatusData data;

        public boolean isSuccess() {
            return "success".equalsIgnoreCase(status) || "true".equalsIgnoreCase(result);
        }

        public static class NodeStatusData {
            @com.google.gson.annotations.SerializedName("lteNo")
            public String lteNo;
            @com.google.gson.annotations.SerializedName("valveStatus")
            public int valveStatus;
            @com.google.gson.annotations.SerializedName("mainStatus")
            public String mainStatus;
            @com.google.gson.annotations.SerializedName("isSchedule")
            public int isSchedule;
            /** 노드 목록 — 서버 키명: nodelist */
            @com.google.gson.annotations.SerializedName("nodelist")
            public java.util.List<NodeStatusDetail> nodelist;
        }

        public static class NodeStatusDetail {
            @com.google.gson.annotations.SerializedName("nodeId")
            public String nodeId;
            @com.google.gson.annotations.SerializedName("lteNo")
            public String lteNo;
            @com.google.gson.annotations.SerializedName("nodeName")
            public String nodeName;
            @com.google.gson.annotations.SerializedName("nodeType")
            public String nodeType;
            /** 밸브 상태: 1=관수중, 2=멈춤, 3=OFF, 4=벤트중, 9=점검 */
            @com.google.gson.annotations.SerializedName("valveStatus")
            public int valveStatus;
            @com.google.gson.annotations.SerializedName("sensorType")
            public Integer sensorType;

            /** valveStatus → DeviceModel.DeviceStatus 변환 */
            public com.acasian.iot.DeviceModel.DeviceStatus toDeviceStatus() {
                switch (valveStatus) {
                    case 1: return com.acasian.iot.DeviceModel.DeviceStatus.RUNNING;
                    case 2: return com.acasian.iot.DeviceModel.DeviceStatus.STANDBY;
                    case 9: return com.acasian.iot.DeviceModel.DeviceStatus.ERROR;
                    default: return com.acasian.iot.DeviceModel.DeviceStatus.IDLE;
                }
            }

            // 하위 호환 — nodeStatus 필드명으로도 접근 가능
            public int getNodeStatus() { return valveStatus; }
        }

        // 하위 호환 — NodeStatusItem/NodeStatusData 구조 유지
        public static class NodeStatusItem {
            public String lteNo;
            public int mainStatus;
            public String sensorStatus;
            public int childCount;
            public java.util.List<NodeStatusDetail> nodeList;
        }
    }
/*

    class RefreshRequest {
        @com.google.gson.annotations.SerializedName("refresh_token")
        public final String refreshToken;
        public RefreshRequest(String t) { refreshToken = t; }
    }

    class ToggleRequest {
        @com.google.gson.annotations.SerializedName("is_on")
        public final boolean isOn;
        public ToggleRequest(boolean on) { isOn = on; }
    }
*/


    // ════════════════════════════════════════════════════════════════
    // ── 스케줄 유형 DTO
    // ════════════════════════════════════════════════════════════════

    class ScheduleCateRequest {
        @com.google.gson.annotations.SerializedName("userId") public final String userId;
        @com.google.gson.annotations.SerializedName("lteNo")  public final String lteNo;
        public ScheduleCateRequest(String userId, String lteNo) { this.userId=userId; this.lteNo=lteNo; }
    }

    /**
     * getScheduleCate / getScheduleCateById 응답
     * 서버: { status:"success", message, data: List<ScheduleCate> }
     * ScheduleCate 내부에 groupList → nodeList 포함
     */
    class ScheduleCateListResponse {
        @com.google.gson.annotations.SerializedName("status")  public String status;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public java.util.List<ScheduleCateItem> data;
        public boolean isSuccess() { return "success".equalsIgnoreCase(status); }

        public static class ScheduleCateItem {
            @com.google.gson.annotations.SerializedName("cateId")    public int cateId;
            @com.google.gson.annotations.SerializedName("cateName")  public String cateName;
            @com.google.gson.annotations.SerializedName("lteNo")     public String lteNo;
            /** 1=자동, 2=개별 */
            @com.google.gson.annotations.SerializedName("kind")      public int kind;
            @com.google.gson.annotations.SerializedName("stime")     public int stime;
            @com.google.gson.annotations.SerializedName("dtime")     public int dtime;
            @com.google.gson.annotations.SerializedName("reCount")   public int reCount;
            @com.google.gson.annotations.SerializedName("userId")    public String userId;
            /** 개별밸브:Y, 전체:N */
            @com.google.gson.annotations.SerializedName("isTemp")    public String isTemp;
            @com.google.gson.annotations.SerializedName("groupList") public java.util.List<GroupItem> groupList;
        }

        public static class GroupItem {
            @com.google.gson.annotations.SerializedName("groupId")     public int groupId;
            @com.google.gson.annotations.SerializedName("groupName")   public String groupName;
            @com.google.gson.annotations.SerializedName("lteNo")       public String lteNo;
            @com.google.gson.annotations.SerializedName("descreption") public String descreption;
            @com.google.gson.annotations.SerializedName("cateId")      public int cateId;
            @com.google.gson.annotations.SerializedName("nodeIds")     public String nodeIds;
            @com.google.gson.annotations.SerializedName("nodeList")    public java.util.List<NodeItem> nodeList;
        }

        public static class NodeItem {
            @com.google.gson.annotations.SerializedName("groupId")  public int groupId;
            @com.google.gson.annotations.SerializedName("nodeId")   public String nodeId;
            @com.google.gson.annotations.SerializedName("lteNo")    public String lteNo;
            @com.google.gson.annotations.SerializedName("nodeType") public String nodeType;
        }
    }

    // ── groupList 요청용 공통 DTO ────────────────────────────────────
    class CateGroupRequest {
        @com.google.gson.annotations.SerializedName("groupName")   public String groupName;
        @com.google.gson.annotations.SerializedName("lteNo")       public String lteNo;
        @com.google.gson.annotations.SerializedName("descreption") public String descreption;
        @com.google.gson.annotations.SerializedName("nodeList")    public java.util.List<CateNodeRequest> nodeList;
        public CateGroupRequest(String groupName, String lteNo, java.util.List<CateNodeRequest> nodeList) {
            this.groupName = groupName; this.lteNo = lteNo; this.nodeList = nodeList;
        }
    }
    class CateNodeRequest {
        @com.google.gson.annotations.SerializedName("nodeId")   public String nodeId;
        @com.google.gson.annotations.SerializedName("lteNo")    public String lteNo;
        @com.google.gson.annotations.SerializedName("nodeType") public String nodeType;
        public CateNodeRequest(String nodeId, String lteNo) {
            this.nodeId = nodeId; this.lteNo = lteNo; this.nodeType = "S";
        }
    }

    class ScheduleCateAddRequest {
        @com.google.gson.annotations.SerializedName("lteNo")      public final String lteNo;
        @com.google.gson.annotations.SerializedName("cateName")   public final String cateName;
        @com.google.gson.annotations.SerializedName("kind")       public final int    kind;
        @com.google.gson.annotations.SerializedName("stime")      public final int    stime;
        @com.google.gson.annotations.SerializedName("dtime")      public final int    dtime;
        @com.google.gson.annotations.SerializedName("reCount")    public final int    reCount;
        @com.google.gson.annotations.SerializedName("userId")     public final String userId;
        @com.google.gson.annotations.SerializedName("isTemp")     public final String isTemp;
        @com.google.gson.annotations.SerializedName("groupList")  public final java.util.List<CateGroupRequest> groupList;
        public ScheduleCateAddRequest(String lteNo, String cateName, int kind,
                                       int stime, int dtime, int reCount, String userId,
                                       java.util.List<CateGroupRequest> groupList) {
            this.lteNo=lteNo; this.cateName=cateName; this.kind=kind;
            this.stime=stime; this.dtime=dtime; this.reCount=reCount; this.userId=userId;
            this.isTemp = "N";
            this.groupList = groupList;
        }
        /** 하위 호환 — groupList 없이 호출 시 빈 리스트 */
        public ScheduleCateAddRequest(String lteNo, String cateName, int kind,
                                       int stime, int dtime, int reCount, String userId) {
            this(lteNo, cateName, kind, stime, dtime, reCount, userId,
                 new java.util.ArrayList<>());
        }
    }

    class ScheduleCateUpdRequest {
        @com.google.gson.annotations.SerializedName("cateId")     public final String cateId;
        @com.google.gson.annotations.SerializedName("lteNo")      public final String lteNo;
        @com.google.gson.annotations.SerializedName("cateName")   public final String cateName;
        @com.google.gson.annotations.SerializedName("kind")       public final int    kind;
        @com.google.gson.annotations.SerializedName("stime")      public final int    stime;
        @com.google.gson.annotations.SerializedName("dtime")      public final int    dtime;
        @com.google.gson.annotations.SerializedName("reCount")    public final int    reCount;
        @com.google.gson.annotations.SerializedName("userId")     public final String userId;
        @com.google.gson.annotations.SerializedName("isTemp")     public final String isTemp;
        @com.google.gson.annotations.SerializedName("groupList")  public final java.util.List<CateGroupRequest> groupList;
        public ScheduleCateUpdRequest(String cateId, String lteNo, String cateName, int kind,
                                       int stime, int dtime, int reCount, String userId,
                                       java.util.List<CateGroupRequest> groupList) {
            this.cateId=cateId; this.lteNo=lteNo; this.cateName=cateName; this.kind=kind;
            this.stime=stime; this.dtime=dtime; this.reCount=reCount; this.userId=userId;
            this.isTemp = "N";
            this.groupList = groupList;
        }
        /** 하위 호환 — groupList 없이 호출 시 빈 리스트 */
        public ScheduleCateUpdRequest(String cateId, String lteNo, String cateName, int kind,
                                       int stime, int dtime, int reCount, String userId) {
            this(cateId, lteNo, cateName, kind, stime, dtime, reCount, userId,
                 new java.util.ArrayList<>());
        }
    }

    class ScheduleCateDelRequest {
        @com.google.gson.annotations.SerializedName("cateId") public final String cateId;
        public ScheduleCateDelRequest(String cateId) { this.cateId = cateId; }
    }

    // ════════════════════════════════════════════════════════════════
    // ── 스케줄 DTO
    // ════════════════════════════════════════════════════════════════

    class ScheduleGetRequest {
        @com.google.gson.annotations.SerializedName("userId")  public final String userId;
        @com.google.gson.annotations.SerializedName("lteNo")   public final String lteNo;
        /** 조회 시작일 yymmdd (nullable — null이면 전체) */
        @com.google.gson.annotations.SerializedName("stDate")  public final String stDate;
        /** 조회 종료일 yymmdd (nullable — null이면 전체) */
        @com.google.gson.annotations.SerializedName("edDate")  public final String edDate;

        /** 기본 생성자 — userId만 (전체 조회) */
        public ScheduleGetRequest(String userId) {
            this(userId, null, null, null);
        }
        /** lteNo + 기간 조회 */
        public ScheduleGetRequest(String userId, String lteNo, String stDate, String edDate) {
            this.userId = userId;
            this.lteNo  = lteNo;
            this.stDate = stDate;
            this.edDate = edDate;
        }
    }

    /**
     * getSchedule 응답
     * 서버: { status:"success", message:"...", data: List<AcaSchedule> }
     * ※ data 가 List 직접 (schinfo 래퍼 없음)
     * ※ schId/cateId 는 서버에서 int 로 반환
     */
    class ScheduleListResponse {
        @com.google.gson.annotations.SerializedName("status")  public String status;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public java.util.List<ScheduleItem> data;
        public boolean isSuccess() { return "success".equalsIgnoreCase(status); }
        public static class ScheduleItem {
            /** 스케줄 ID (서버 int) */
            @com.google.gson.annotations.SerializedName("schId")    public int schId;
            /** 유형 ID (서버 int, 자동관수 시 참조) */
            @com.google.gson.annotations.SerializedName("cateId")   public int cateId;
            @com.google.gson.annotations.SerializedName("cateName") public String cateName;
            @com.google.gson.annotations.SerializedName("yymmdd")   public String yymmdd;
            @com.google.gson.annotations.SerializedName("hhnn")     public String hhnn;
            @com.google.gson.annotations.SerializedName("userId")   public String userId;
            @com.google.gson.annotations.SerializedName("farmId")   public String farmId;
            @com.google.gson.annotations.SerializedName("lteNo")    public String lteNo;
            /** 스케줄 활성 여부 */
            @com.google.gson.annotations.SerializedName("isSched")  public String isSched;
            /** 개별밸브:Y, 전체:N */
            @com.google.gson.annotations.SerializedName("isTemp")   public String isTemp;
            /** 개별밸브 번호 콤마구분 */
            @com.google.gson.annotations.SerializedName("nodeIds")  public String nodeIds;
            /** 순차급수여부: Y=순차, N=동시 */
            @com.google.gson.annotations.SerializedName("isSeq")    public String isSeq;
            /** 1=자동(유형), 2=개별 */
            @com.google.gson.annotations.SerializedName("kind")     public int kind;
            /** 관수시간(분) */
            @com.google.gson.annotations.SerializedName("stime")    public int stime;
            /** 휴지시간(분) */
            @com.google.gson.annotations.SerializedName("dtime")    public int dtime;
            /** 반복횟수 */
            @com.google.gson.annotations.SerializedName("reCount")  public int reCount;
            /** 반복 여부: 0=반복없음(단건), 1=반복(매일 지정시간 실행) */
            @com.google.gson.annotations.SerializedName("isRepeat") public int isRepeat;
            /** 스케줄 진행여부: 0=스케줄종료, 1=진행중 */
            @com.google.gson.annotations.SerializedName("isDel")    public int isDel;
            /** 유형의 그룹 목록 (getScheduleCate groupList 동일 구조) */
            @com.google.gson.annotations.SerializedName("groupList")
            public java.util.List<ScheduleCateListResponse.GroupItem> groupList;
            public boolean isAuto()       { return kind == 1; }
            public boolean isSequential() { return "Y".equalsIgnoreCase(isSeq); }
            public boolean isRepeat()     { return isRepeat == 1; }
            /** 진행 중 여부: isDel=1이면 true */
            public boolean isActive()     { return isDel == 1; }
        }
    }

    /**
     * addSchedule 요청 (v1.5 갱신)
     *
     * ── 자동관수 (kind=1) ───────────────────────────────────────────────
     *   cateId 필수, nodeIds=null, stime/dtime/reCount 서버가 Cate에서 조회
     *   ScheduleAddRequest.forAuto(...) 사용
     *
     * ── 개별관수 (kind=2) ───────────────────────────────────────────────
     *   cateId=null, nodeIds=콤마구분 밸브번호, stime/dtime/reCount 직접 입력
     *   ScheduleAddRequest.forIndividual(...) 사용
     *
     * ── isSeq ──────────────────────────────────────────────────────────
     *   "N" = 동시 실행 (그룹/밸브 동시 가동)
     *   "Y" = 순차 실행 (1번 완료 후 2번 시작)
     *   자동/개별 모두 적용
     */
    class ScheduleAddRequest {
        @com.google.gson.annotations.SerializedName("userId")    public final String userId;
        @com.google.gson.annotations.SerializedName("farmId")    public final String farmId;
        @com.google.gson.annotations.SerializedName("lteNo")     public final String lteNo;
        /** 자동관수 시 유형ID, 개별관수 시 null */
        @com.google.gson.annotations.SerializedName("cateId")    public final String cateId;
        @com.google.gson.annotations.SerializedName("yymmdd")    public final String yymmdd;
        @com.google.gson.annotations.SerializedName("hhnn")      public final String hhnn;
        /** 1=자동(유형), 2=개별 */
        @com.google.gson.annotations.SerializedName("kind")      public final int kind;
        /** "N"=전체, "Y"=개별 */
        @com.google.gson.annotations.SerializedName("isTemp")    public final String isTemp;
        /** 순차급수 여부: "Y"=순차, "N"=동시 */
        @com.google.gson.annotations.SerializedName("isSeq")     public final String isSeq;
        /** 개별밸브 시 콤마구분 nodeId 목록 (자동 시 null) */
        @com.google.gson.annotations.SerializedName("nodeIds")   public final String nodeIds;
        /** 개별관수 시 관수시간(분) — 자동 시 0 전송 (서버 무시) */
        @com.google.gson.annotations.SerializedName("stime")     public final int stime;
        /** 개별관수 시 휴지시간(분) */
        @com.google.gson.annotations.SerializedName("dtime")     public final int dtime;
        /** 개별관수 시 반복횟수 */
        @com.google.gson.annotations.SerializedName("reCount")   public final int reCount;
        /** 반복 여부: 0=반복없음(단건), 1=반복(매일 지정시간 실행) */
        @com.google.gson.annotations.SerializedName("isRepeat")  public final int isRepeat;
        /** 스케줄 진행여부: 0=종료, 1=진행중 — 신규 등록 시 항상 1(진행중) 고정 */
        @com.google.gson.annotations.SerializedName("isDel")     public final int isDel;

        /** 자동관수 생성자 — 단건 */
        public static ScheduleAddRequest forAuto(String userId, String farmId, String lteNo,
                                                  String cateId, String yymmdd, String hhnn,
                                                  String isSeq) {
            return new ScheduleAddRequest(userId, farmId, lteNo, cateId, yymmdd, hhnn,
                    1, "N", isSeq, null, 0, 0, 1, 0);
        }

        /** 자동관수 생성자 — 반복 포함 */
        public static ScheduleAddRequest forAutoRepeat(String userId, String farmId, String lteNo,
                                                        String cateId, String yymmdd, String hhnn,
                                                        String isSeq, int isRepeat) {
            return new ScheduleAddRequest(userId, farmId, lteNo, cateId, yymmdd, hhnn,
                    1, "N", isSeq, null, 0, 0, 1, isRepeat);
        }

        /** 개별관수 생성자 — 단건 */
        public static ScheduleAddRequest forIndividual(String userId, String farmId, String lteNo,
                                                        String yymmdd, String hhnn,
                                                        String isSeq, String nodeIds,
                                                        int stime, int dtime, int reCount) {
            return new ScheduleAddRequest(userId, farmId, lteNo, null, yymmdd, hhnn,
                    2, "Y", isSeq, nodeIds, stime, dtime, reCount, 0);
        }

        /** 개별관수 생성자 — 반복 포함 */
        public static ScheduleAddRequest forIndividualRepeat(String userId, String farmId,
                                                              String lteNo,
                                                              String yymmdd, String hhnn,
                                                              String isSeq, String nodeIds,
                                                              int stime, int dtime, int reCount,
                                                              int isRepeat) {
            return new ScheduleAddRequest(userId, farmId, lteNo, null, yymmdd, hhnn,
                    2, "Y", isSeq, nodeIds, stime, dtime, reCount, isRepeat);
        }

        /** 하위 호환 — 자동관수 기본 (isSeq=N, isRepeat=0) */
        public ScheduleAddRequest(String userId, String farmId, String lteNo,
                                   String cateId, String yymmdd, String hhnn) {
            this(userId, farmId, lteNo, cateId, yymmdd, hhnn, 1, "N", "N", null, 0, 0, 1, 0);
        }

        private ScheduleAddRequest(String userId, String farmId, String lteNo,
                                    String cateId, String yymmdd, String hhnn,
                                    int kind, String isTemp, String isSeq,
                                    String nodeIds, int stime, int dtime, int reCount,
                                    int isRepeat) {
            this.userId=userId; this.farmId=farmId; this.lteNo=lteNo;
            this.cateId=cateId; this.yymmdd=yymmdd; this.hhnn=hhnn;
            this.kind=kind; this.isTemp=isTemp; this.isSeq=isSeq;
            this.nodeIds=nodeIds; this.stime=stime; this.dtime=dtime; this.reCount=reCount;
            this.isRepeat=isRepeat;
            this.isDel = 1; // 신규 등록 시 항상 1(진행중) 고정
        }
    }

    class ScheduleUpdRequest {
        @com.google.gson.annotations.SerializedName("schId")     public final String schId;
        @com.google.gson.annotations.SerializedName("cateId")    public final String cateId;
        @com.google.gson.annotations.SerializedName("yymmdd")    public final String yymmdd;
        @com.google.gson.annotations.SerializedName("hhnn")      public final String hhnn;
        /** 1=자동(유형), 2=개별 */
        @com.google.gson.annotations.SerializedName("kind")      public final int kind;
        /** "N"=전체, "Y"=개별 */
        @com.google.gson.annotations.SerializedName("isTemp")    public final String isTemp;
        /** 순차급수 여부: "Y"=순차, "N"=동시 */
        @com.google.gson.annotations.SerializedName("isSeq")     public final String isSeq;
        /** 개별 시 밸브번호 콤마구분 */
        @com.google.gson.annotations.SerializedName("nodeIds")   public final String nodeIds;
        /** 개별 시 관수시간(분) */
        @com.google.gson.annotations.SerializedName("stime")     public final int stime;
        /** 개별 시 휴지시간(분) */
        @com.google.gson.annotations.SerializedName("dtime")     public final int dtime;
        /** 개별 시 반복횟수 */
        @com.google.gson.annotations.SerializedName("reCount")   public final int reCount;
        /** 반복 여부: 0=반복없음(단건), 1=반복(매일 지정시간 실행) */
        @com.google.gson.annotations.SerializedName("isRepeat")  public final int isRepeat;
        /** 스케줄 진행여부: 0=종료, 1=진행중 */
        @com.google.gson.annotations.SerializedName("isDel")     public final int isDel;

        /** 자동관수 수정 — 단건, isDel=1(진행중) 기본 */
        public ScheduleUpdRequest(String schId, String cateId, String yymmdd, String hhnn,
                                   String isSeq) {
            this(schId, cateId, yymmdd, hhnn, 1, "N", isSeq, null, 0, 0, 1, 0, 1);
        }

        /** 자동관수 수정 — 반복 포함, isDel=1(진행중) 기본 */
        public ScheduleUpdRequest(String schId, String cateId, String yymmdd, String hhnn,
                                   String isSeq, int isRepeat) {
            this(schId, cateId, yymmdd, hhnn, 1, "N", isSeq, null, 0, 0, 1, isRepeat, 1);
        }

        /** 자동관수 수정 — 반복 + isDel 직접 지정 */
        public ScheduleUpdRequest(String schId, String cateId, String yymmdd, String hhnn,
                                   String isSeq, int isRepeat, int isDel) {
            this(schId, cateId, yymmdd, hhnn, 1, "N", isSeq, null, 0, 0, 1, isRepeat, isDel);
        }

        /** 개별관수 수정 — 단건, isDel=1(진행중) 기본 */
        public ScheduleUpdRequest(String schId, String yymmdd, String hhnn,
                                   String isSeq, String nodeIds,
                                   int stime, int dtime, int reCount) {
            this(schId, null, yymmdd, hhnn, 2, "Y", isSeq, nodeIds, stime, dtime, reCount, 0, 1);
        }

        /** 개별관수 수정 — 반복 포함, isDel=1(진행중) 기본 */
        public ScheduleUpdRequest(String schId, String yymmdd, String hhnn,
                                   String isSeq, String nodeIds,
                                   int stime, int dtime, int reCount, int isRepeat) {
            this(schId, null, yymmdd, hhnn, 2, "Y", isSeq, nodeIds, stime, dtime, reCount, isRepeat, 1);
        }

        /** 하위 호환 — 자동관수 기본 (isRepeat=0, isDel=1) */
        public ScheduleUpdRequest(String schId, String cateId, String yymmdd, String hhnn) {
            this(schId, cateId, yymmdd, hhnn, 1, "N", "N", null, 0, 0, 1, 0, 1);
        }

        private ScheduleUpdRequest(String schId, String cateId, String yymmdd, String hhnn,
                                    int kind, String isTemp, String isSeq,
                                    String nodeIds, int stime, int dtime, int reCount,
                                    int isRepeat, int isDel) {
            this.schId=schId; this.cateId=cateId; this.yymmdd=yymmdd; this.hhnn=hhnn;
            this.kind=kind; this.isTemp=isTemp; this.isSeq=isSeq;
            this.nodeIds=nodeIds; this.stime=stime; this.dtime=dtime; this.reCount=reCount;
            this.isRepeat=isRepeat;
            this.isDel=isDel;
        }
    }

    class ScheduleDelRequest {
        @com.google.gson.annotations.SerializedName("schId") public final int schId;
        public ScheduleDelRequest(String schId) {
            int id = 0;
            try { id = Integer.parseInt(schId); } catch (Exception ignored) {}
            this.schId = id;
        }
    }

    /** stopSchedule 요청 — { schId, isDel }
     *  isDel: 0=스케줄종료(정지), 1=진행중(재개) */
    class StopScheduleRequest {
        @com.google.gson.annotations.SerializedName("schId") public final int schId;
        @com.google.gson.annotations.SerializedName("isDel") public final int isDel;
        public StopScheduleRequest(int schId, int isDel) {
            this.schId = schId;
            this.isDel = isDel;
        }
        /** 정지 요청 — isDel=0 */
        public static StopScheduleRequest stop(int schId) {
            return new StopScheduleRequest(schId, 0);
        }
        /** 재개 요청 — isDel=1 */
        public static StopScheduleRequest resume(int schId) {
            return new StopScheduleRequest(schId, 1);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ── v1.5 신규 DTO ────────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════

    /** addScheduleCate 응답 — res: { status:"success", message, cateId } */
    /** addSchedule 응답 — 서버: { status:"success", message, schId } */
    class ScheduleAddResponse {
        @com.google.gson.annotations.SerializedName("status")  public String status;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("schId")   public int schId;
        public boolean isSuccess() { return "success".equalsIgnoreCase(status); }
    }

    /** addScheduleCate 응답 — 서버: { status:"success", message, cateId } */
    class CateAddResponse {
        @com.google.gson.annotations.SerializedName("status")  public String status;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("cateId")  public int cateId;
        public boolean isSuccess() { return "success".equalsIgnoreCase(status); }
    }

    /** 단순 cateId 요청용 */
    class CateIdRequest {
        @com.google.gson.annotations.SerializedName("cateId") public final String cateId;
        public CateIdRequest(String cateId) { this.cateId = cateId; }
    }

    /** 단순 schId 요청용 */
    class SchIdRequest {
        @com.google.gson.annotations.SerializedName("schId") public final String schId;
        public SchIdRequest(String schId) { this.schId = schId; }
    }

    /** 단순 groupId 요청용 */
    class GroupIdRequest {
        @com.google.gson.annotations.SerializedName("groupId") public final String groupId;
        public GroupIdRequest(String groupId) { this.groupId = groupId; }
    }

    /**
     * getScheduleById 응답
     * res: { result, message, data: { schinfo: {...} } }  — 단건이므로 jsonObject
     */
    class ScheduleSingleResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public ScheduleSingleData data;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }
        class ScheduleSingleData {
            // schinfo 필드는 ScheduleListResponse.ScheduleItem과 동일 구조를 인라인으로 정의
            @com.google.gson.annotations.SerializedName("schinfo") public SingleSchInfo schinfo;
        }
        class SingleSchInfo {
            @com.google.gson.annotations.SerializedName("schId")    public String schId;
            /** 유형ID (자동관수 시 유형 참조, 개별관수 시 null) */
            @com.google.gson.annotations.SerializedName("cateId")   public String cateId;
            @com.google.gson.annotations.SerializedName("cateName") public String cateName;
            @com.google.gson.annotations.SerializedName("yymmdd")   public String yymmdd;
            @com.google.gson.annotations.SerializedName("hhnn")     public String hhnn;
            @com.google.gson.annotations.SerializedName("userId")   public String userId;
            @com.google.gson.annotations.SerializedName("farmId")   public String farmId;
            @com.google.gson.annotations.SerializedName("lteNo")    public String lteNo;
            @com.google.gson.annotations.SerializedName("isTemp")   public String isTemp;
            @com.google.gson.annotations.SerializedName("nodeIds")  public String nodeIds;
            /** 1=자동(유형), 2=개별 */
            @com.google.gson.annotations.SerializedName("kind")     public int kind;
            /** 순차급수 여부: Y=순차, N=동시 */
            @com.google.gson.annotations.SerializedName("isSeq")    public String isSeq;
            /** 개별관수 시 관수시간(분) */
            @com.google.gson.annotations.SerializedName("stime")    public int stime;
            /** 개별관수 시 휴지시간(분) */
            @com.google.gson.annotations.SerializedName("dtime")    public int dtime;
            /** 개별관수 시 반복횟수 */
            @com.google.gson.annotations.SerializedName("reCount")  public int reCount;
            public boolean isAuto() { return kind == 1; }
            public boolean isSequential() { return "Y".equalsIgnoreCase(isSeq); }
        }
    }

    /**
     * getScheduleCateDetail 응답
     * schcatedetail[]: sdtId, cateId, nodeGroupId, command, cateName
     * group[]: grName, grId
     * nodelist[]: lteNo, nodeId
     */
    class ScheduleCateDetailResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public CateDetailData data;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }
        class CateDetailData {
            @com.google.gson.annotations.SerializedName("schcatedetail")
            public java.util.List<CateDetailItem> schcatedetail;
        }
        class CateDetailItem {
            @com.google.gson.annotations.SerializedName("sdtId")       public String sdtId;
            @com.google.gson.annotations.SerializedName("cateId")      public String cateId;
            @com.google.gson.annotations.SerializedName("nodeGroupId") public String nodeGroupId;
            @com.google.gson.annotations.SerializedName("command")     public String command;
            @com.google.gson.annotations.SerializedName("cateName")    public String cateName;
            @com.google.gson.annotations.SerializedName("group")       public java.util.List<GroupItem> group;
            @com.google.gson.annotations.SerializedName("nodelist")    public java.util.List<NodeListItem> nodelist;
        }
        class GroupItem {
            @com.google.gson.annotations.SerializedName("grName") public String grName;
            @com.google.gson.annotations.SerializedName("grId")   public String grId;
        }
        class NodeListItem {
            @com.google.gson.annotations.SerializedName("lteNo")  public String lteNo;
            @com.google.gson.annotations.SerializedName("nodeId") public String nodeId;
        }
    }

    /**
     * getScheduleGroup 요청
     * req: { cateId, lteNo }
     */
    class ScheduleGroupRequest {
        @com.google.gson.annotations.SerializedName("cateId") public final String cateId;
        @com.google.gson.annotations.SerializedName("lteNo")  public final String lteNo;
        public ScheduleGroupRequest(String cateId, String lteNo) {
            this.cateId = cateId; this.lteNo = lteNo;
        }
    }

    /**
     * getScheduleGroup 응답
     * schgroup[]: groupId, groupName, lteNo, descreption, cateId, nodeIds
     */
    class ScheduleGroupListResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public GroupListData data;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }
        class GroupListData {
            @com.google.gson.annotations.SerializedName("schgroup")
            public java.util.List<GroupListItem> schgroup;
        }
        class GroupListItem {
            @com.google.gson.annotations.SerializedName("groupId")     public String groupId;
            @com.google.gson.annotations.SerializedName("groupName")   public String groupName;
            @com.google.gson.annotations.SerializedName("lteNo")       public String lteNo;
            @com.google.gson.annotations.SerializedName("descreption") public String description;
            @com.google.gson.annotations.SerializedName("cateId")      public String cateId;
            @com.google.gson.annotations.SerializedName("nodeIds")     public String nodeIds;
        }
    }

    /**
     * addScheduleGroup 요청
     * req: { groupName, lteNo, descreption, cateId }
     */
    class ScheduleGroupAddRequest {
        @com.google.gson.annotations.SerializedName("groupName")   public final String groupName;
        @com.google.gson.annotations.SerializedName("lteNo")       public final String lteNo;
        @com.google.gson.annotations.SerializedName("descreption") public final String description;
        @com.google.gson.annotations.SerializedName("cateId")      public final String cateId;
        public ScheduleGroupAddRequest(String groupName, String lteNo,
                                        String description, String cateId) {
            this.groupName=groupName; this.lteNo=lteNo;
            this.description=description; this.cateId=cateId;
        }
    }

    /** addScheduleGroup 응답 — res: { result, message, groupId } */
    class GroupAddResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("groupId") public String groupId;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }
    }

    /**
     * updScheduleGroup 요청
     * req: { groupId, groupName, descreption }
     */
    class ScheduleGroupUpdRequest {
        @com.google.gson.annotations.SerializedName("groupId")     public final String groupId;
        @com.google.gson.annotations.SerializedName("groupName")   public final String groupName;
        @com.google.gson.annotations.SerializedName("descreption") public final String description;
        public ScheduleGroupUpdRequest(String groupId, String groupName, String description) {
            this.groupId=groupId; this.groupName=groupName; this.description=description;
        }
    }

    /**
     * getScheduleGroupDetail 응답
     * schgroupdetail[]: groupId, nodeId, lteNo, nodeType
     */
    class ScheduleGroupDetailResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public GroupDetailData data;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }
        class GroupDetailData {
            @com.google.gson.annotations.SerializedName("schgroupdetail")
            public java.util.List<GroupDetailItem> schgroupdetail;
        }
        class GroupDetailItem {
            @com.google.gson.annotations.SerializedName("groupId")  public String groupId;
            @com.google.gson.annotations.SerializedName("nodeId")   public String nodeId;
            @com.google.gson.annotations.SerializedName("lteNo")    public String lteNo;
            @com.google.gson.annotations.SerializedName("nodeType") public String nodeType;
        }
    }

    /**
     * addScheduleGroupDetail 요청 아이템 (jsonArray로 전송)
     * req: [ { groupId, nodeId, lteNo, nodeType }, ... ]
     */
    class ScheduleGroupDetailItem {
        @com.google.gson.annotations.SerializedName("groupId")  public final String groupId;
        @com.google.gson.annotations.SerializedName("nodeId")   public final String nodeId;
        @com.google.gson.annotations.SerializedName("lteNo")    public final String lteNo;
        @com.google.gson.annotations.SerializedName("nodeType") public final String nodeType;
        public ScheduleGroupDetailItem(String groupId, String nodeId,
                                        String lteNo, String nodeType) {
            this.groupId=groupId; this.nodeId=nodeId;
            this.lteNo=lteNo; this.nodeType=nodeType;
        }
    }

    /**
     * getScheduleCateGrpCmd 응답
     * schedulecategrpcmd[]: sdtId, cateId, groupId, command, cateName
     */
    class ScheduleCateGrpCmdResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("message") public String message;
        @com.google.gson.annotations.SerializedName("data")    public GrpCmdData data;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }
        class GrpCmdData {
            @com.google.gson.annotations.SerializedName("schedulecategrpcmd")
            public java.util.List<GrpCmdItem> schedulecategrpcmd;
        }
        class GrpCmdItem {
            @com.google.gson.annotations.SerializedName("sdtId")    public String sdtId;
            @com.google.gson.annotations.SerializedName("cateId")   public String cateId;
            @com.google.gson.annotations.SerializedName("groupId")  public String groupId;
            @com.google.gson.annotations.SerializedName("command")  public String command;
            @com.google.gson.annotations.SerializedName("cateName") public String cateName;
        }
    }

    /**
     * addScheduleCateGrpCmd 요청 아이템 (jsonArray로 전송)
     * req: [ { groupId, cateId, command }, ... ]
     */
    class ScheduleCateGrpCmdItem {
        @com.google.gson.annotations.SerializedName("groupId") public final String groupId;
        @com.google.gson.annotations.SerializedName("cateId")  public final String cateId;
        @com.google.gson.annotations.SerializedName("command") public final String command;
        public ScheduleCateGrpCmdItem(String groupId, String cateId, String command) {
            this.groupId=groupId; this.cateId=cateId; this.command=command;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ── 비밀번호 변경 DTO
    // ════════════════════════════════════════════════════════════════

    /** req: { userid, password(현재), chgpassword(변경될) } */
    class ChangePwRequest {
        @com.google.gson.annotations.SerializedName("userid")      public final String userid;
        @com.google.gson.annotations.SerializedName("password")    public final String password;
        @com.google.gson.annotations.SerializedName("chgpassword") public final String chgpassword;
        public ChangePwRequest(String userid, String password, String chgpassword) {
            this.userid=userid; this.password=password; this.chgpassword=chgpassword;
        }
    }

    // ── ValveResponse ─────────────────────────────────────────────────────
    /**
     * POST v1/device/updNodeValve 응답
     * {"message":"successfully","status":"success"}
     * or {"result": true}
     */
    class ValveResponse {
        @com.google.gson.annotations.SerializedName("result")
        public Boolean result;   // Boolean (nullable)

        @com.google.gson.annotations.SerializedName("status")
        public String status;

        @com.google.gson.annotations.SerializedName("message")
        public String message;

        /** result:true 또는 status:"success" 중 하나면 성공 */
        public boolean isSuccess() {
            if (status != null && status.equalsIgnoreCase("success")) return true;
            if (result != null) return result;
            return false;
        }
    }

    // ── Device ───────────────────────────────────────────────────────────
    class DeviceResponse {
        @com.google.gson.annotations.SerializedName("device_id")  public String deviceId;
        @com.google.gson.annotations.SerializedName("name")       public String name;
        @com.google.gson.annotations.SerializedName("zone")       public String zone;
        @com.google.gson.annotations.SerializedName("type")       public String type;
        @com.google.gson.annotations.SerializedName("is_on")      public boolean isOn;
        @com.google.gson.annotations.SerializedName("updated_at") public String updatedAt;
    }

    // ── Zone ─────────────────────────────────────────────────────────────
    class ZoneResponse {
        @com.google.gson.annotations.SerializedName("zone_id")    public String zoneId;
        @com.google.gson.annotations.SerializedName("name")       public String name;
        @com.google.gson.annotations.SerializedName("is_on")      public boolean isOn;
        @com.google.gson.annotations.SerializedName("devices")    public java.util.List<DeviceResponse> devices;
    }

    // ── Schedule ─────────────────────────────────────────────────────────
    class ScheduleRequest {
        @com.google.gson.annotations.SerializedName("date")        public String date;       // "2025-03-06"
        @com.google.gson.annotations.SerializedName("start_time")  public String startTime;  // "06:00"
        @com.google.gson.annotations.SerializedName("end_time")    public String endTime;    // "07:30"
        @com.google.gson.annotations.SerializedName("device_id")   public String deviceId;
        @com.google.gson.annotations.SerializedName("task_name")   public String taskName;
        @com.google.gson.annotations.SerializedName("memo")        public String memo;
    }

    class ScheduleResponse {
        @com.google.gson.annotations.SerializedName("schedule_id") public String scheduleId;
        @com.google.gson.annotations.SerializedName("date")        public String date;
        @com.google.gson.annotations.SerializedName("start_time")  public String startTime;
        @com.google.gson.annotations.SerializedName("end_time")    public String endTime;
        @com.google.gson.annotations.SerializedName("device_id")   public String deviceId;
        @com.google.gson.annotations.SerializedName("device_name") public String deviceName;
        @com.google.gson.annotations.SerializedName("zone")        public String zone;
        @com.google.gson.annotations.SerializedName("task_name")   public String taskName;
        @com.google.gson.annotations.SerializedName("status")      public String status;
        @com.google.gson.annotations.SerializedName("memo")        public String memo;
    }

    // ── Sensor ───────────────────────────────────────────────────────────
    class SensorResponse {
        @com.google.gson.annotations.SerializedName("temperature")    public float temperature;
        @com.google.gson.annotations.SerializedName("humidity")       public float humidity;
        @com.google.gson.annotations.SerializedName("light")          public int   light;
        @com.google.gson.annotations.SerializedName("soil_moisture")  public float soilMoisture;
        @com.google.gson.annotations.SerializedName("measured_at")    public String measuredAt;
    }

    // ── 설치장비 환경 DTO ─────────────────────────────────────────────────

    /** getMainEnv 요청 — { letNo } */
    class MainEnvRequest {
        @com.google.gson.annotations.SerializedName("letNo") public final String letNo;
        public MainEnvRequest(String letNo) { this.letNo = letNo; }
    }

    /** getMainEnv 응답 — { result, messege, data:{irrinfo:{...}} } */
    class MainEnvResponse {
        @com.google.gson.annotations.SerializedName("result")  public String result;
        @com.google.gson.annotations.SerializedName("messege") public String message;
        @com.google.gson.annotations.SerializedName("data")    public MainEnvData data;
        public boolean isSuccess() { return "true".equalsIgnoreCase(result); }

        public static class MainEnvData {
            @com.google.gson.annotations.SerializedName("irrinfo") public IrrInfo irrinfo;
        }

        public static class IrrInfo {
            @com.google.gson.annotations.SerializedName("letNo")      public String letNo;
            @com.google.gson.annotations.SerializedName("farmId")     public String farmId;
            /** 공급펌프 마력 */
            @com.google.gson.annotations.SerializedName("spPump")     public String spPump;
            /** 관수배관직경 (mm) */
            @com.google.gson.annotations.SerializedName("valveDiam")  public String valveDiam;
            /** 토출밸브수 */
            @com.google.gson.annotations.SerializedName("valveCount") public String valveCount;
            /** 주관밸브수 */
            @com.google.gson.annotations.SerializedName("mainValve")  public String mainValve;
            /** 액비장치 여부 */
            @com.google.gson.annotations.SerializedName("fertilYn")   public String fertilYn;
        }
    }

    /** addMainEnv / updMainEnv 요청 */
    class MainEnvSaveRequest {
        @com.google.gson.annotations.SerializedName("letNo")      public final String letNo;
        @com.google.gson.annotations.SerializedName("farmId")     public final String farmId;
        @com.google.gson.annotations.SerializedName("spPump")     public final String spPump;
        @com.google.gson.annotations.SerializedName("valveDiam")  public final String valveDiam;
        @com.google.gson.annotations.SerializedName("valveCount") public final String valveCount;
        @com.google.gson.annotations.SerializedName("mainValve")  public final String mainValve;
        @com.google.gson.annotations.SerializedName("fertilYn")   public final String fertilYn;

        public MainEnvSaveRequest(String letNo, String farmId,
                                   String spPump, String valveDiam,
                                   String valveCount, String mainValve, String fertilYn) {
            this.letNo=letNo; this.farmId=farmId;
            this.spPump=spPump; this.valveDiam=valveDiam;
            this.valveCount=valveCount; this.mainValve=mainValve; this.fertilYn=fertilYn;
        }
    }
}

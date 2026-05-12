package com.acasian.iot.model.response;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * POST v1/main/farminfo 응답
 * {
 *   "result"  : "true",
 *   "message" : "success",
 *   "data"    : [
 *     {
 *       "farmId"   : "20260318163512121",
 *       "farmName" : "여주농장",
 *       "addr"     : "여주시",
 *       "addr2"    : "100",
 *       "mainlist" : [
 *         {
 *           "mainStatus"  : "1",
 *           "valveStatus" : "1",
 *           "descreption" : "주관밸브함체",
 *           "lteNo"       : "01029943269",
 *           "mainName"    : "주관밸브함체",
 *           "childCount"  : null,
 *           "nodelist"    : [
 *             {
 *               "nodeId"     : "0",
 *               "lteNo"      : "01029943269",
 *               "nodeName"   : "1번 밸브",
 *               "descreption": null,
 *               "nodeType"   : "S",
 *               "nodeStatus" : 0
 *             }, ...
 *           ]
 *         }
 *       ]
 *     }
 *   ]
 * }
 */
public class FarmInfoResponse {

    @SerializedName("result")
    private String result;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private List<FarmInfo> farmInfoList;

    public boolean        isSuccess()      { return "true".equalsIgnoreCase(result); }
    public String         getResult()      { return result; }
    public String         getMessage()     { return message; }
    public List<FarmInfo> getFarmInfoList(){ return farmInfoList; }

    // ── 농장 항목 ────────────────────────────────────────────────────────
    public static class FarmInfo {

        @SerializedName("farmId")
        private String farmId;

        @SerializedName("farmName")
        private String farmName;

        @SerializedName("addr")
        private String addr;

        @SerializedName("addr2")
        private String addr2;

        /** 컨트롤박스(주관밸브함체) 목록 */
        @SerializedName("mainlist")
        private List<MainInfo> mainList;

        public String         getFarmId()   { return farmId; }
        public String         getFarmName() { return farmName; }
        public String         getAddr()     { return addr; }
        public String         getAddr2()    { return addr2; }
        public List<MainInfo> getMainList() { return mainList; }

        public String getFullAddress() {
            if (addr == null) return "";
            if (addr2 == null || addr2.isEmpty()) return addr;
            return addr + " " + addr2;
        }
    }

    // ── 컨트롤박스(주관밸브함체) 항목 → ZoneData에 대응 ─────────────────
    public static class MainInfo {

        /** 주관 상태: "1"=정상, "0"=오류 등 */
        @SerializedName("mainStatus")
        private String mainStatus;

        /** 밸브 상태: 1=관수중, 2=멈춤, 3=OFF, 4=벤트중, 9=점검 (서버 int) */
        @SerializedName("valveStatus")
        private int valveStatus;

        @SerializedName("descreption")
        private String description;

        /** 컨트롤박스 LTE 번호 (updNodeValve의 telNo로 사용) */
        @SerializedName("lteNo")
        private String lteNo;

        @SerializedName("mainName")
        private String mainName;

        @SerializedName("swVersion")
        private String swVersion;

        @SerializedName("childCount")
        private Integer childCount;

        /** 센서 상태: 미설치/정상/점검 */
        @SerializedName("sensorStatus")
        private String sensorStatus;

        /** 스케줄 상태: 0=없음, 1=있음 */
        @SerializedName("isSchedule")
        private Integer isSchedule;

        @SerializedName("nodelist")
        private List<NodeInfo> nodeList;

        public String         getMainStatus()    { return mainStatus; }
        public int            getValveStatus()   { return valveStatus; }
        public String         getDescription()   { return description; }
        public String         getLteNo()         { return lteNo; }
        public String         getMainName()      { return mainName != null ? mainName : description; }
        public String         getSwVersion()     { return swVersion; }
        public int            getChildCount()    { return childCount != null ? childCount : (nodeList != null ? nodeList.size() : 0); }
        public String         getSensorStatus()  { return sensorStatus; }
        public int            getIsSchedule()    { return isSchedule != null ? isSchedule : 0; }
        public List<NodeInfo> getNodeList()      { return nodeList; }

        /** mainStatus → 표시용 문자열 */
        public String getMainStatusLabel() {
            if ("1".equals(mainStatus)) return "정상";
            if ("0".equals(mainStatus)) return "오류";
            return "미설치";
        }

        /** valveStatus → 표시용 문자열 (1=관수중, 2=멈춤, 3=OFF, 4=벤트중, 9=점검) */
        public String getValveStatusLabel() {
            switch (valveStatus) {
                case 1: return "관수중";
                case 2: return "멈춤";
                case 3: return "OFF";
                case 4: return "벤트중";
                case 9: return "점검";
                default: return "미설치";
            }
        }
    }

    // ── 밸브 노드 항목 → DeviceModel에 대응 ─────────────────────────────
    public static class NodeInfo {

        /** 밸브 번호 (0~F, Z) → updNodeValve의 vid로 사용 */
        @SerializedName("nodeId")
        private String nodeId;

        /** 노드 LTE 번호 (보통 mainInfo의 lteNo와 동일) */
        @SerializedName("lteNo")
        private String lteNo;

        @SerializedName("nodeName")
        private String nodeName;

        @SerializedName("descreption")
        private String description;

        @SerializedName("nodeType")
        private String nodeType;   // "S"=밸브, 기타=센서 등

        /** 노드 상태 (구버전): 0=대기, 1=작동 중 */
        @SerializedName("nodeStatus")
        private int nodeStatus;

        /** 밸브 상태: 0=미설치, 1=관수중, 2=멈춤, 3=OFF, 4=벤트중, 9=점검 */
        @SerializedName("valveStatus")
        private int valveStatus;

        /** 센서 종류: 1=온도, 2=습도, 3=EC, 4=PH */
        @SerializedName("sensorType")
        private Integer sensorType;

        /** 온도값 (sensorType=1) */
        @SerializedName("tempValue")
        private String tempValue;

        /** 습도값 (sensorType=2) */
        @SerializedName("humValue")
        private String humValue;

        /** EC값 (sensorType=3) */
        @SerializedName("ecValue")
        private String ecValue;

        /** PH값 (sensorType=4) */
        @SerializedName("phValue")
        private String phValue;

        /** 기타 센서값 */
        @SerializedName("etcValue")
        private String etcValue;

        public String  getNodeId()       { return nodeId; }
        public String  getLteNo()        { return lteNo; }
        public String  getNodeName()     { return nodeName; }
        public String  getDescription()  { return description; }
        public String  getNodeType()     { return nodeType; }
        public int     getNodeStatus()   { return nodeStatus; }
        public int     getValveStatus()  { return valveStatus; }
        public Integer getSensorType()   { return sensorType; }
        public String  getTempValue()    { return tempValue; }
        public String  getHumValue()     { return humValue; }
        public String  getEcValue()      { return ecValue; }
        public String  getPhValue()      { return phValue; }
        public String  getEtcValue()     { return etcValue; }

        /** nodeStatus(int) → DeviceModel.DeviceStatus */
        public com.acasian.iot.DeviceModel.DeviceStatus toDeviceStatus() {
            return nodeStatus == 1
                    ? com.acasian.iot.DeviceModel.DeviceStatus.RUNNING
                    : com.acasian.iot.DeviceModel.DeviceStatus.IDLE;
        }
    }
}

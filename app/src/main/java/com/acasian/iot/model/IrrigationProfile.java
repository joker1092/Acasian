package com.acasian.iot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 관수 유형 프로필.
 *
 * 타입:
 *   AUTO       → 그룹 단위 순차 실행 (그룹 내 밸브 동시, 그룹 간 겹침 타이밍)
 *   INDIVIDUAL → 선택한 밸브 전체 동시 실행
 *
 * AUTO 실행 타이밍:
 *   그룹1 가동 시작 → 휴지 시작 = 그룹2 가동 시작 → 휴지 시작 = 그룹3 가동 시작 ...
 */
public class IrrigationProfile {

    // ── 타입 ─────────────────────────────────────────────────────────────
    public enum ProfileType {
        AUTO,        // 자동관수: 그룹 우선순위 순 실행 (kind:1, isTemp:N)
        INDIVIDUAL   // 개별관수: 선택 밸브 실행 (kind:2, isTemp:Y, cateId:null)
    }

    /** 실행 방식 — 자동/개별 모두 적용 */
    public enum ExecMode {
        SIMULTANEOUS,  // 동시 실행 (isSeq:N)
        SEQUENTIAL     // 순차 실행 (isSeq:Y)
    }

    // ── 내부 그룹 (AUTO 전용, 유형에 종속) ───────────────────────────────
    public static class Group {
        private String       id;
        private List<String> nodeIds;   // 그룹에 속한 밸브 nodeId 목록
        private int          priority;  // 실행 순서 (1부터)

        public Group() {
            this.id       = String.valueOf(System.currentTimeMillis());
            this.nodeIds  = new ArrayList<>();
            this.priority = 1;
        }

        public Group(String id, List<String> nodeIds, int priority) {
            this.id       = id;
            this.nodeIds  = nodeIds != null ? nodeIds : new ArrayList<>();
            this.priority = priority;
        }

        public String       getId()       { return id; }
        public List<String> getNodeIds()  { return nodeIds; }
        public int          getPriority() { return priority; }

        public void setId(String id)             { this.id = id; }
        public void setNodeIds(List<String> ids) { this.nodeIds = ids != null ? ids : new ArrayList<>(); }
        public void setPriority(int p)           { this.priority = p; }

        /** 밸브 이름 요약 */
        public String getNodeSummary(com.acasian.iot.ZoneStore store, String telNo) {
            if (nodeIds.isEmpty()) return "밸브 없음";
            List<com.acasian.iot.ZoneStore.NodeInfo> nodes = store.getNodesByTelNo(telNo);
            java.util.Map<String, String> idToName = new java.util.HashMap<>();
            for (com.acasian.iot.ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);
            StringBuilder sb = new StringBuilder();
            for (String nid : nodeIds) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(idToName.containsKey(nid) ? idToName.get(nid) : nid);
            }
            return sb.toString();
        }
    }

    // ── 필드 ─────────────────────────────────────────────────────────────
    private String      id;
    private String      name;
    private ProfileType profileType;   // AUTO / INDIVIDUAL
    private String      zoneId;        // 메인함 telNo
    private String      zoneName;      // 메인함 표시명
    private int         runMinutes;
    private int         restMinutes;
    private int         repeatCount;

    // AUTO 전용
    private List<Group>  groups;       // 우선순위 순 그룹 목록

    // INDIVIDUAL 전용
    private List<String> nodeIds;      // 직접 선택한 밸브
    /** 개별관수 시 관수시간(분) — stime */
    private int indivStime;
    /** 개별관수 시 휴지시간(분) — dtime */
    private int indivDtime;
    /** 개별관수 시 반복횟수 — reCount */
    private int indivReCount;

    // 공통 — 실행 방식
    /** 동시(N) / 순차(Y) — 자동·개별 모두 적용 */
    private ExecMode execMode = ExecMode.SIMULTANEOUS;

    // 하위 호환 (기존 TargetType 유지)
    public enum TargetType { ZONE_ALL, ZONE_INDIVIDUAL }
    private TargetType targetType;

    // ── 생성자 ───────────────────────────────────────────────────────────
    public IrrigationProfile() {
        this.id          = String.valueOf(System.currentTimeMillis());
        this.profileType = ProfileType.AUTO;
        this.targetType  = TargetType.ZONE_ALL;
        this.zoneId      = "";
        this.zoneName    = "";
        this.runMinutes  = 30;
        this.restMinutes = 0;
        this.repeatCount = 1;
        this.groups      = new ArrayList<>();
        this.nodeIds     = new ArrayList<>();
    }

    // 기존 생성자 (하위 호환)
    public IrrigationProfile(String id, String name, TargetType targetType,
                              String zoneId, String zoneName,
                              List<String> deviceIds,
                              int runMinutes, int restMinutes, int repeatCount) {
        this.id          = id;
        this.name        = name;
        this.targetType  = targetType;
        this.profileType = targetType == TargetType.ZONE_ALL
                ? ProfileType.AUTO : ProfileType.INDIVIDUAL;
        this.zoneId      = zoneId    != null ? zoneId    : "";
        this.zoneName    = zoneName  != null ? zoneName  : "";
        this.nodeIds     = deviceIds != null ? deviceIds : new ArrayList<>();
        this.groups      = new ArrayList<>();
        this.runMinutes  = runMinutes;
        this.restMinutes = restMinutes;
        this.repeatCount = repeatCount;
    }

    // ── Getters ──────────────────────────────────────────────────────────
    public String       getId()          { return id; }
    public String       getName()        { return name; }
    public ProfileType  getProfileType() { return profileType != null ? profileType : ProfileType.AUTO; }
    public TargetType   getTargetType()  { return targetType  != null ? targetType  : TargetType.ZONE_ALL; }
    public ExecMode     getExecMode()    { return execMode    != null ? execMode    : ExecMode.SIMULTANEOUS; }
    /** API 전송용 isSeq 문자열 */
    public String       getIsSeq()       { return execMode == ExecMode.SEQUENTIAL ? "Y" : "N"; }
    public boolean      isSequential()   { return execMode == ExecMode.SEQUENTIAL; }

    // 개별관수 시간 필드
    public int  getIndivStime()   { return indivStime; }
    public int  getIndivDtime()   { return indivDtime; }
    public int  getIndivReCount() { return indivReCount > 0 ? indivReCount : 1; }
    public String       getZoneId()      { return zoneId; }
    public String       getZoneName()    { return zoneName; }
    public int          getRunMinutes()  { return runMinutes; }
    public int          getRestMinutes() { return restMinutes; }
    public int          getRepeatCount() { return repeatCount; }
    public List<Group>  getGroups()      { return groups != null ? groups : new ArrayList<>(); }
    /** 하위 호환: INDIVIDUAL 밸브 목록 */
    public List<String> getDeviceIds()   { return nodeIds != null ? nodeIds : new ArrayList<>(); }

    // ── Setters ──────────────────────────────────────────────────────────
    public void setId(String id)               { this.id = id; }
    public void setName(String name)           { this.name = name; }
    public void setProfileType(ProfileType t)  { this.profileType = t; }
    public void setTargetType(TargetType t)    { this.targetType = t; }
    public void setExecMode(ExecMode m)        { this.execMode = m; }
    public void setIndivStime(int v)           { this.indivStime = v; }
    public void setIndivDtime(int v)           { this.indivDtime = v; }
    public void setIndivReCount(int v)         { this.indivReCount = v; }
    public void setZoneId(String id)           { this.zoneId = id != null ? id : ""; }
    public void setZoneName(String name)       { this.zoneName = name != null ? name : ""; }
    public void setRunMinutes(int v)           { this.runMinutes = v; }
    public void setRestMinutes(int v)          { this.restMinutes = v; }
    public void setRepeatCount(int v)          { this.repeatCount = v; }
    public void setGroups(List<Group> g)       { this.groups = g != null ? g : new ArrayList<>(); }
    public void setDeviceIds(List<String> ids) { this.nodeIds = ids != null ? ids : new ArrayList<>(); }

    /** 그룹 추가 (priority 자동 설정) */
    public void addGroup(List<String> nodeIds) {
        if (groups == null) groups = new ArrayList<>();
        Group g = new Group();
        g.setNodeIds(new ArrayList<>(nodeIds));
        g.setPriority(groups.size() + 1);
        groups.add(g);
    }

    /** 그룹 순서 변경 후 priority 재정렬 */
    public void reorderGroups() {
        if (groups == null) return;
        for (int i = 0; i < groups.size(); i++) groups.get(i).setPriority(i + 1);
    }

    public String getSummary() {
        String zn = (zoneName != null && !zoneName.isEmpty()) ? zoneName : "메인함";
        if (profileType == ProfileType.AUTO) {
            int gc = groups != null ? groups.size() : 0;
            return zn + " · 자동 · " + gc + "그룹 · " + runMinutes + "분";
        } else {
            int nc = nodeIds != null ? nodeIds.size() : 0;
            return zn + " · 개별 · " + nc + "개 · " + runMinutes + "분";
        }
    }

    @Override
    public String toString() { return name != null ? name : id; }
}

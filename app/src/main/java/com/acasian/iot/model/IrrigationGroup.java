package com.acasian.iot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 관수 그룹 — 같은 메인함(telNo) 내 밸브(nodeId)를 묶은 단위.
 *
 * 자동 관수 시: priority 순서대로 그룹 단위 실행
 *   - 이전 그룹 휴지 시작 = 다음 그룹 가동 시작 (겹침)
 *   - 그룹 내 밸브는 동시 실행
 *
 * 개별 관수 시: 사용 안 함 (밸브 직접 선택)
 */
public class IrrigationGroup {

    private String       id;
    private String       name;
    private String       telNo;
    private String       zoneName;
    private List<String> nodeIds;

    public IrrigationGroup() {
        this.id      = String.valueOf(System.currentTimeMillis());
        this.nodeIds = new ArrayList<>();
    }

    public IrrigationGroup(String id, String name, String telNo, String zoneName,
                            List<String> nodeIds) {
        this.id       = id;
        this.name     = name;
        this.telNo    = telNo;
        this.zoneName = zoneName;
        this.nodeIds  = nodeIds != null ? nodeIds : new ArrayList<>();
    }

    public String       getId()       { return id; }
    public String       getName()     { return name; }
    public String       getTelNo()    { return telNo; }
    public String       getZoneName() { return zoneName; }
    public List<String> getNodeIds()  { return nodeIds; }

    public void setId(String id)             { this.id = id; }
    public void setName(String name)         { this.name = name; }
    public void setTelNo(String telNo)       { this.telNo = telNo; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public void setNodeIds(List<String> ids) { this.nodeIds = ids != null ? ids : new ArrayList<>(); }

    public String getNodeSummary(com.acasian.iot.ZoneStore store) {
        if (nodeIds.isEmpty()) return "밸브 없음";
        java.util.List<com.acasian.iot.ZoneStore.NodeInfo> nodes = store.getNodesByTelNo(telNo);
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

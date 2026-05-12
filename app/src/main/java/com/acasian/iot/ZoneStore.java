package com.acasian.iot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 메인함(Zone) + 노드(Node) 전역 캐시 — 싱글톤.
 *
 * ── 업데이트 시점 ────────────────────────────────────────────────
 *  · 앱 시작 (로그인 후 initDummyZoneData / loadFarmInfo)
 *  · API 응답 후 (밸브 제어, 예약 등록/취소, 농장 정보 변경 등)
 *  · 포그라운드 복귀 (일정 시간 경과)
 *  · 수동 새로고침
 *
 * ── 사용 ─────────────────────────────────────────────────────────
 *  ZoneStore store = ZoneStore.getInstance();
 *  store.getZones();                        // 전체 메인함
 *  store.getNodesByTelNo("demo_tel_001");   // 특정 메인함 노드
 *  store.addObserver(listener);             // 변경 감지
 */
public class ZoneStore {

    // ── 데이터 모델 ───────────────────────────────────────────────

    public static class ZoneInfo {
        public final String telNo;    // 메인함 식별자 (lteNo)
        public final String name;     // 메인함 표시명
        public final List<NodeInfo> nodes;

        public ZoneInfo(String telNo, String name, List<NodeInfo> nodes) {
            this.telNo = telNo != null ? telNo : "";
            this.name  = name  != null ? name  : "";
            this.nodes = nodes != null
                    ? Collections.unmodifiableList(new ArrayList<>(nodes))
                    : Collections.emptyList();
        }
    }

    public static class NodeInfo {
        public final String nodeId;            // 노드 식별자
        public final String name;              // 노드 표시명
        public final int    initialValveStatus; // farminfo 응답의 valveStatus (1=관수중,2=멈춤,3=OFF,0=미설치)

        public NodeInfo(String nodeId, String name) {
            this(nodeId, name, 3); // 기본값 3=OFF
        }

        public NodeInfo(String nodeId, String name, int initialValveStatus) {
            this.nodeId             = nodeId != null ? nodeId : "";
            this.name               = name   != null ? name   : "";
            this.initialValveStatus = initialValveStatus;
        }
    }

    // ── Observer ─────────────────────────────────────────────────

    public interface Observer {
        void onZoneStoreUpdated(List<ZoneInfo> zones);
    }

    // ── 싱글톤 ───────────────────────────────────────────────────

    private static volatile ZoneStore INSTANCE;

    public static ZoneStore getInstance() {
        if (INSTANCE == null) {
            synchronized (ZoneStore.class) {
                if (INSTANCE == null) INSTANCE = new ZoneStore();
            }
        }
        return INSTANCE;
    }

    private ZoneStore() {}

    // ── 상태 ─────────────────────────────────────────────────────

    private final List<ZoneInfo> zones     = new ArrayList<>();
    private final CopyOnWriteArrayList<Observer> observers =
            new CopyOnWriteArrayList<>();
    private long   lastUpdatedAt = 0L;   // System.currentTimeMillis()
    private String farmId        = "";   // farmInfo 응답의 farmId

    // ── 업데이트 ─────────────────────────────────────────────────

    /**
     * 메인함 목록 전체 교체 후 Observer 알림.
     * 어느 시점이든 호출 가능 (스레드 안전).
     */
    public synchronized void update(List<ZoneInfo> newZones) {
        zones.clear();
        if (newZones != null) zones.addAll(newZones);
        lastUpdatedAt = System.currentTimeMillis();
        notifyObservers();
    }

    /** farmId 저장 — farmInfo 응답 시 호출 */
    public synchronized void setFarmId(String id) {
        this.farmId = id != null ? id : "";
    }

    /** farmId 조회 */
    public synchronized String getFarmId() { return farmId; }

    /** update() 후 마지막 갱신 시각 (ms) */
    public long getLastUpdatedAt() { return lastUpdatedAt; }

    /** 마지막 갱신으로부터 경과 시간 (ms) */
    public long getElapsedSinceUpdate() {
        return System.currentTimeMillis() - lastUpdatedAt;
    }

    // ── 조회 ─────────────────────────────────────────────────────

    public synchronized List<ZoneInfo> getZones() {
        return Collections.unmodifiableList(new ArrayList<>(zones));
    }

    public synchronized boolean isEmpty() { return zones.isEmpty(); }

    /** 첫 번째 메인함의 telNo(lteNo) 반환 — 설정화면 등 단일 GW 접근용 */
    public synchronized String getFirstLteNo() {
        return zones.isEmpty() ? null : zones.get(0).telNo;
    }

    /** telNo로 특정 메인함 조회 */
    public synchronized ZoneInfo getByTelNo(String telNo) {
        if (telNo == null) return null;
        for (ZoneInfo z : zones) if (telNo.equals(z.telNo)) return z;
        return null;
    }

    /** telNo로 노드 목록 조회 */
    public synchronized List<NodeInfo> getNodesByTelNo(String telNo) {
        ZoneInfo z = getByTelNo(telNo);
        return z != null ? z.nodes : Collections.emptyList();
    }

    /** 메인함 이름 배열 (UI 드롭다운용) */
    public synchronized String[] getZoneNames() {
        String[] names = new String[zones.size()];
        for (int i = 0; i < zones.size(); i++) names[i] = zones.get(i).name;
        return names;
    }

    /** 메인함 telNo 배열 (이름 배열과 인덱스 동일) */
    public synchronized String[] getZoneTelNos() {
        String[] telNos = new String[zones.size()];
        for (int i = 0; i < zones.size(); i++) telNos[i] = zones.get(i).telNo;
        return telNos;
    }

    /** 특정 메인함의 노드 이름 배열 */
    public synchronized String[] getNodeNames(String telNo) {
        List<NodeInfo> nodes = getNodesByTelNo(telNo);
        String[] names = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) names[i] = nodes.get(i).name;
        return names;
    }

    /** 특정 메인함의 nodeId 배열 (이름 배열과 인덱스 동일) */
    public synchronized String[] getNodeIds(String telNo) {
        List<NodeInfo> nodes = getNodesByTelNo(telNo);
        String[] ids = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) ids[i] = nodes.get(i).nodeId;
        return ids;
    }

    // ── Observer ─────────────────────────────────────────────────

    public void addObserver(Observer o)    { if (o != null) observers.addIfAbsent(o); }
    public void removeObserver(Observer o) { observers.remove(o); }

    private void notifyObservers() {
        List<ZoneInfo> snapshot = Collections.unmodifiableList(new ArrayList<>(zones));
        // UI 스레드 보장을 위해 메인 핸들러로 전달
        android.os.Handler mainHandler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            for (Observer o : observers) o.onZoneStoreUpdated(snapshot);
        });
    }

    /** 스토어 초기화 (로그아웃 시) */
    public synchronized void clear() {
        zones.clear();
        lastUpdatedAt = 0L;
        notifyObservers();
    }
}

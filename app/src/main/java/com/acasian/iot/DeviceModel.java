package com.acasian.iot;

/**
 * IoT 기기 데이터 모델
 */
public class DeviceModel {

    public enum DeviceType {
        PUMP, SENSOR, SHADE, LED
    }

    /**
     * 기기 운영 상태
     * IDLE    - 휴게 (흰색 배경)
     * RUNNING - 작동 중 (초록 배경)
     * STANDBY - 대기/처리중 (회색 배경)
     * ERROR   - 점검/고장 (빨강 배경)
     */
    public enum DeviceStatus {
        IDLE, RUNNING, STANDBY, ERROR
    }

    private final String     id;
    private final String     name;
    private final String     description;
    private final DeviceType type;
    private       boolean    isOn;
    private       DeviceStatus status;
    private       boolean    isPaused;   // 일시정지 여부 (RUNNING → 일시정지 시 true)

    public DeviceModel(String id, String name, String description,
                       DeviceType type, boolean isOn) {
        this.id     = id;
        this.name   = name;
        this.description = description;
        this.type   = type;
        this.isOn   = isOn;
        // 초기 상태: ON → RUNNING, OFF → IDLE
        this.status = isOn ? DeviceStatus.RUNNING : DeviceStatus.IDLE;
    }

    public String       getId()          { return id; }
    public String       getName()        { return name; }
    public String       getDescription() { return description; }
    public DeviceType   getType()        { return type; }
    public boolean      isOn()           { return isOn; }
    public DeviceStatus getStatus()      { return status; }
    public boolean      isPaused()       { return isPaused; }

    public void setOn(boolean on) {
        this.isOn     = on;
        this.isPaused = false;
        if (status != DeviceStatus.ERROR && status != DeviceStatus.STANDBY) {
            this.status = on ? DeviceStatus.RUNNING : DeviceStatus.IDLE;
        }
    }

    public void setStatus(DeviceStatus status) {
        this.status   = status;
        this.isOn     = (status == DeviceStatus.RUNNING);
        this.isPaused = false;
    }

    /** 일시정지: STANDBY 상태 + isPaused 플래그 */
    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            this.status = DeviceStatus.STANDBY;
            this.isOn   = false;
        }
    }

    public int getIconResId() {
        return switch (type) {
            case PUMP   -> R.drawable.ic_pump;
            case SENSOR -> R.drawable.ic_sensor;
            case SHADE  -> R.drawable.ic_shade;
            case LED    -> R.drawable.ic_led;
        };
    }
}

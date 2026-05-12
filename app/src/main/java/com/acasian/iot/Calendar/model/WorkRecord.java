package com.acasian.iot.Calendar.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 작업 내역 / 예약 데이터 모델
 * isPast() 로 내역/예약 버전 자동 구분
 */
public class WorkRecord {

    public enum Status { DONE, SCHEDULED, FAILED, RUNNING }
    public enum DeviceType { PUMP, SENSOR, SHADE, LED }

    private final String     id;
    private final LocalDate  date;
    private final LocalTime  startTime;
    private final LocalTime  endTime;
    private final String     taskName;
    private final DeviceType deviceType;
    private final String     deviceName;
    private final String     zone;
    private       Status     status;
    private       String     memo;
    private       String     irrigationProfileId;  // 선택된 관수 유형 ID

    public WorkRecord(String id, LocalDate date,
                      LocalTime startTime, LocalTime endTime,
                      String taskName,
                      DeviceType deviceType, String deviceName, String zone,
                      Status status, String memo) {
        this.id         = id;
        this.date       = date;
        this.startTime  = startTime;
        this.endTime    = endTime;
        this.taskName   = taskName;
        this.deviceType = deviceType;
        this.deviceName = deviceName;
        this.zone       = zone;
        this.status     = status;
        this.memo       = memo;
    }

    // 오늘 이전(포함) → 내역 버전
    public boolean isPast() {
        return !date.isAfter(LocalDate.now());
    }

    // Getters
    public String     getId()         { return id; }
    public LocalDate  getDate()       { return date; }
    public LocalTime  getStartTime()  { return startTime; }
    public LocalTime  getEndTime()    { return endTime; }
    public String     getTaskName()   { return taskName; }
    public DeviceType getDeviceType() { return deviceType; }
    public String     getDeviceName() { return deviceName; }
    public String     getZone()       { return zone; }
    public Status     getStatus()     { return status; }
    public String     getMemo()       { return memo; }

    public void setStatus(Status status) { this.status = status; }
    public void setMemo(String memo)     { this.memo = memo; }
    public String getIrrigationProfileId()             { return irrigationProfileId; }
    public void   setIrrigationProfileId(String id)    { this.irrigationProfileId = id; }
}

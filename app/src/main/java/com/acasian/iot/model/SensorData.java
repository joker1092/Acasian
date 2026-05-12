package com.acasian.iot.model;

/**
 * 환경 센서 데이터 모델
 * API 응답 또는 로컬 더미 데이터를 담는 객체
 */
public class SensorData {

    private final float  temperature;   // °C
    private final float  humidity;      // %
    private final int    light;         // lux
    private final String soilStatus;    // "정상" / "건조" / "과습" 등

    public SensorData(float temperature, float humidity,
                      int light, String soilStatus) {
        this.temperature = temperature;
        this.humidity    = humidity;
        this.light       = light;
        this.soilStatus  = soilStatus;
    }

    public float  getTemperature() { return temperature; }
    public float  getHumidity()    { return humidity; }
    public int    getLight()       { return light; }
    public String getSoilStatus()  { return soilStatus; }

    /** lux → 간략 표시 ("3.2k", "850") */
    public String getLightDisplay() {
        if (light >= 1000) {
            return String.format("%.1fk", light / 1000f);
        }
        return String.valueOf(light);
    }
}

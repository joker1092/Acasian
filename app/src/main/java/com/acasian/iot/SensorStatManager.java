package com.acasian.iot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.acasian.iot.model.request.SensingStatRequest;
import com.acasian.iot.model.response.SensingStatResponse;
import com.acasian.iot.network.ApiClient;
import com.acasian.iot.network.StatApiService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
// 주의: retrofit2.Callback/Response 는 내부 Callback 인터페이스와 이름이 겹치므로
//       enqueue 블록에서 정규명(retrofit2.Callback/Response)으로 직접 사용한다.

/**
 * 센서 통계(변화값 분석) 데이터 관리. — SensorManager 와 동일 패턴.
 *
 * 기간(Period) × 센서(SensorType) 조합별로 호출.
 *   기간 → API/키 체계 : 일별 day1.., 주별 mon~sun, 월별 mon1..mon12
 *   센서 → sensorType  : 1 온도(지온) / 2 습도(지습) / 3 EC / 4 pH
 *
 * 서버는 단일 객체에 min/max(문자열)만 내려주므로 SensingStatResponse 의
 * toIndexedSeries / toWeekdaySeries 로 List&lt;Point&gt; 시계열로 변환해 전달한다.
 *
 * 캐싱: 측정이 1시간 주기이므로 같은 '시(時)' 동안 값이 불변.
 *   key(lteNo|period|sensorType) 별 결과 + 시간버킷(yyyyMMddHH) 저장,
 *   같은 시면 네트워크 없이 캐시 반환, 시가 바뀌면 재호출.
 */
public class SensorStatManager {

    /** 조회 단위 — 드롭다운 항목과 1:1 */
    public enum Period {
        DAILY  ("1일"),
        WEEKLY ("1주일"),
        MONTHLY("한달");

        public final String label;
        Period(String label) { this.label = label; }

        public static Period fromPosition(int pos) {
            switch (pos) {
                case 1:  return WEEKLY;
                case 2:  return MONTHLY;
                default: return DAILY;
            }
        }
    }

    /** 센서 종류 — sensorType 코드 + 표시정보 + 환경기준(env_standard) 키/기본값.
     *  prefs 키가 null 이면 그 한계 자체가 없음(지온은 minKey 없음 → 하한 없음).
     *  키 출처: SettingsFragment "환경기준 등록" (SharedPreferences "env_standard"). */
    public enum SensorType {
        TEMP(1, "지온 (토양온도)", "°C",   null,       "temp_max", Double.NaN, 45.0),
        HUMI(2, "지습 (토양수분)", "%",    "soil_min", "soil_max", 20.0,       40.0),
        EC  (3, "EC (전기전도도)", "dS/m", "ec_min",   "ec_max",   0.5,        3.0),
        PH  (4, "pH (산도)",       "",     "ph_min",   "ph_max",   6.0,        7.0);

        public final int    code;
        public final String title;
        public final String unit;
        public final String minKey;  // env_standard 하한 키 (null=하한 없음)
        public final String maxKey;  // env_standard 상한 키 (null=상한 없음)
        public final double defLo;   // prefs 미저장 시 기본 하한 (NaN 가능)
        public final double defHi;   // prefs 미저장 시 기본 상한 (NaN 가능)
        SensorType(int code, String title, String unit,
                   String minKey, String maxKey, double defLo, double defHi) {
            this.code = code; this.title = title; this.unit = unit;
            this.minKey = minKey; this.maxKey = maxKey; this.defLo = defLo; this.defHi = defHi;
        }
    }

    // ── 환경기준(관리범위) 동적 조회 ───────────────────────────────
    //   SettingsFragment 환경기준 등록(prefs "env_standard")에서 읽음.
    //   값을 비우면(한계 제거) NaN, 값 입력 시 그 값 → 차트가 자동 반영.
    private static final String PREF_ENV = "env_standard";

    public static double envLo(Context ctx, SensorType s) { return resolveBound(ctx, s.minKey, s.defLo); }
    public static double envHi(Context ctx, SensorType s) { return resolveBound(ctx, s.maxKey, s.defHi); }

    private static double resolveBound(Context ctx, String key, double def) {
        if (key == null) return Double.NaN;                 // 이 센서엔 해당 한계 없음(지온 하한 등)
        android.content.SharedPreferences p =
                ctx.getSharedPreferences(PREF_ENV, Context.MODE_PRIVATE);
        String v = p.getString(key, null);
        if (v == null) return def;                          // 미저장 → 기본값
        v = v.trim();
        if (v.isEmpty()) return Double.NaN;                 // 사용자가 비움 → 한계 제거
        try { return Double.parseDouble(v); } catch (Exception e) { return Double.NaN; }
    }

    public interface Callback {
        void onSuccess(List<SensingStatResponse.Point> points);
        void onError(String message);
    }

    private static final int FAKE_DELAY_MS = 600;   // DevMode 더미 로딩 딜레이

    // ── 메모리 캐시 (스레드 안전) ──────────────────────────────────
    private static final java.util.Map<String, CacheEntry> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CacheEntry {
        final List<SensingStatResponse.Point> data;
        final String hourStamp;
        CacheEntry(List<SensingStatResponse.Point> data, String hourStamp) {
            this.data = data; this.hourStamp = hourStamp;
        }
    }

    private static String hourStamp() {
        return new SimpleDateFormat("yyyyMMddHH", Locale.getDefault()).format(new java.util.Date());
    }

    private static String cacheKey(String lteNo, Period period, SensorType sensor) {
        return (lteNo == null ? "dev" : lteNo) + "|" + period.name() + "|" + sensor.code;
    }

    /** 캐시 전체 비우기 (수동 새로고침 등) */
    public static void clearCache() { CACHE.clear(); }

    private SensorStatManager() {}

    /** 센서 통계 요청 (캐시 사용). */
    public static void fetch(Context context, Period period, SensorType sensor, Callback callback) {
        fetch(context, period, sensor, false, callback);
    }

    /** @param forceRefresh true 면 캐시 무시하고 재호출 */
    public static void fetch(Context context, final Period period, final SensorType sensor,
                             boolean forceRefresh, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());

        final String lteNo = ZoneStore.getInstance().getFirstLteNo();
        final String key = cacheKey(lteNo, period, sensor);
        final String nowHour = hourStamp();

        // ── 캐시 적중: 같은 시(時)면 네트워크 없이 즉시 반환 ──
        if (!forceRefresh) {
            CacheEntry hit = CACHE.get(key);
            if (hit != null && hit.hourStamp.equals(nowHour)) {
                main.post(() -> callback.onSuccess(hit.data));
                return;
            }
        }

        String[] range = buildDateRange(period);
        final String stTime = range[0];
        final String edTime = range[1];

        // ── DevMode: 더미 ──
        if (AppConfig.getInstance().isDevMode()) {
            new Thread(() -> {
                try { Thread.sleep(FAKE_DELAY_MS); } catch (InterruptedException ignored) {}
                final List<SensingStatResponse.Point> dummy = buildDummy(period, sensor);
                applyLabels(dummy, period, edTime);
                CACHE.put(key, new CacheEntry(dummy, nowHour));
                main.post(() -> callback.onSuccess(dummy));
            }).start();
            return;
        }

        // ── 상용 모드: 실제 API ──
        if (lteNo == null || lteNo.isEmpty()) {
            callback.onError("게이트웨이 정보가 없습니다.");
            return;
        }

        StatApiService api = ApiClient.getInstance(context)
                .getRetrofit().create(StatApiService.class);
        SensingStatRequest req = new SensingStatRequest(lteNo, stTime, edTime, sensor.code);

        Call<SensingStatResponse> call;
        switch (period) {
            case WEEKLY:  call = api.getWeeklySensingStat(req);  break;
            case MONTHLY: call = api.getMonthlySensingStat(req); break;
            case DAILY:
            default:      call = api.getDailySensingStat(req);   break;
        }

        call.enqueue(new retrofit2.Callback<SensingStatResponse>() {
            @Override
            public void onResponse(Call<SensingStatResponse> c,
                                   retrofit2.Response<SensingStatResponse> res) {
                SensingStatResponse body = res.body();
                if (res.isSuccessful() && body != null && body.isSuccess()) {
                    List<SensingStatResponse.Point> series;
                    switch (period) {
                        case WEEKLY:  series = body.toWeekdaySeries();      break;
                        case MONTHLY: series = body.toIndexedSeries("mon"); break;
                        case DAILY:
                        default:      series = body.toIndexedSeries("day"); break;
                    }
                    applyLabels(series, period, edTime);
                    CACHE.put(key, new CacheEntry(series, nowHour));
                    callback.onSuccess(series);
                } else {
                    String msg = (body != null && body.message != null)
                            ? body.message : "통계 데이터를 불러오지 못했습니다.";
                    callback.onError(msg);
                }
            }

            @Override
            public void onFailure(Call<SensingStatResponse> c, Throwable t) {
                callback.onError("네트워크 오류: " + t.getMessage());
            }
        });
    }

    // ── 라벨 보정: 가장 최근 버킷을 edTime(오늘)에 맞추고 뒤로 거슬러 부여 ──
    //   일별 MM/dd, 월별 yy.MM. 주별은 요일 라벨 유지.
    //   ※ 서버가 day1..day31(31칸)을 주므로 stTime 기준 앞으로 세면 마지막이
    //     오늘+1로 밀린다 → 마지막 인덱스를 edTime 에 고정하고 역산.
    private static void applyLabels(List<SensingStatResponse.Point> series,
                                    Period period, String edTime) {
        if (period == Period.WEEKLY || series == null || series.isEmpty()) return;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar base = Calendar.getInstance();
            base.setTime(in.parse(edTime));
            int field = (period == Period.MONTHLY) ? Calendar.MONTH : Calendar.DAY_OF_YEAR;
            SimpleDateFormat out = new SimpleDateFormat(
                    period == Period.MONTHLY ? "yy.MM" : "MM/dd", Locale.getDefault());
            int n = series.size();
            for (int i = 0; i < n; i++) {
                Calendar c = (Calendar) base.clone();
                c.add(field, -(n - 1 - i));      // 마지막 인덱스 → edTime(오늘), 이전은 과거
                series.get(i).label = out.format(c.getTime());
            }
        } catch (Exception ignored) { }
    }

    // ── 조회 기간 계산 ──────────────────────────────────────────────
    private static String[] buildDateRange(Period period) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar ed = Calendar.getInstance();
        Calendar st = Calendar.getInstance();
        switch (period) {
            case WEEKLY:  st.add(Calendar.WEEK_OF_YEAR, -11); break;
            case MONTHLY: st.add(Calendar.MONTH, -11);        break;
            case DAILY:
            default:      st.add(Calendar.DAY_OF_YEAR, -29);  break;
        }
        return new String[]{ fmt.format(st.getTime()), fmt.format(ed.getTime()) };
    }

    // ── DevMode 더미 (센서 관리범위 안에서 생성, 일부 미측정) ───────
    private static List<SensingStatResponse.Point> buildDummy(Period period, SensorType sensor) {
        String[] weekdays = {"월","화","수","목","금","토","일"};
        int count;
        switch (period) {
            case WEEKLY:  count = 7;  break;
            case MONTHLY: count = 12; break;
            case DAILY:
            default:      count = 30; break;
        }
        // 한계가 NaN(한쪽 없음)일 수 있으므로 중심/진폭을 방어적으로 계산
        double lo = sensor.defLo, hi = sensor.defHi;
        double mid, amp;
        if (!Double.isNaN(lo) && !Double.isNaN(hi)) { mid = (lo + hi) / 2.0; amp = (hi - lo) / 4.0; }
        else if (!Double.isNaN(hi))                 { mid = hi * 0.6; amp = Math.max(1.0, hi * 0.08); }
        else if (!Double.isNaN(lo))                 { mid = lo * 1.4; amp = Math.max(1.0, lo * 0.1); }
        else                                        { mid = 25; amp = 5; }
        List<SensingStatResponse.Point> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String label = (period == Period.WEEKLY) ? weekdays[i - 1]
                    : (period == Period.MONTHLY) ? ("mon" + i) : ("day" + i);
            boolean missing = (period != Period.WEEKLY) && (i % 9 == 0);
            if (missing) { list.add(new SensingStatResponse.Point(label, 0, 0, false)); continue; }
            double base = mid + Math.sin(i * 0.5) * amp;
            // 데모: 일부 지점 한계 살짝 초과 → 이상값 마커 확인
            double spike = (i % 7 == 0) ? amp * 1.2 : 0;
            double mn = round(base - amp * 0.6);
            double mx = round(base + amp * 0.6 + spike);
            list.add(new SensingStatResponse.Point(label, mn, mx, true));
        }
        return list;
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }
}

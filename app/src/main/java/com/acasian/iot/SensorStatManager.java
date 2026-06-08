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
 * 기간 선택 → 호출 API / 응답 접두어:
 *   DAILY   (1일)   → getDailySensingStat   · "day"   · 최근 30일
 *   WEEKLY  (1주일) → getWeeklySensingStat  · "week"  · 최근 12주
 *   MONTHLY (한달)  → getMonthlySensingStat · "month" · 최근 12개월
 *
 * 서버는 단일 객체에 min/max(문자열)만 내려주며 키 체계가 기간별로 다르다.
 * (일별 day1.., 주별 mon~sun, 월별 mon1..mon12) → SensingStatResponse 의
 * toIndexedSeries / toWeekdaySeries 로 List&lt;Point&gt; 시계열로 변환해 전달한다.
 */
public class SensorStatManager {

    /** 조회 단위 — 드롭다운 항목과 1:1 */
    public enum Period {
        DAILY  ("1일"),
        WEEKLY ("1주일"),
        MONTHLY("한달");

        public final String label;
        Period(String label) { this.label = label; }

        /** 스피너 position → Period */
        public static Period fromPosition(int pos) {
            switch (pos) {
                case 1:  return WEEKLY;
                case 2:  return MONTHLY;
                default: return DAILY;
            }
        }
    }

    public interface Callback {
        void onSuccess(List<SensingStatResponse.Point> points);
        void onError(String message);
    }

    // 로딩 딜레이 (ms) — DevMode 더미용. 실제 API 경로에는 미적용.
    private static final int FAKE_DELAY_MS = 600;

    // ── 메모리 캐시 ────────────────────────────────────────────────
    //   센서 측정이 1시간 주기이므로 같은 '시(時)' 안에서는 값이 바뀌지 않는다.
    //   → key(lteNo|period) 별로 결과 + 시간버킷(yyyyMMddHH)을 저장하고,
    //     같은 버킷이면 네트워크 호출 없이 캐시 반환, 시가 바뀌면 재호출.
    //   (TTL 방식으로 바꾸려면 now-fetchedAt < 60분 비교로 교체)
    private static final java.util.Map<String, CacheEntry> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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

    private static String cacheKey(String lteNo, Period period) {
        return (lteNo == null ? "dev" : lteNo) + "|" + period.name();
    }

    /** 캐시 전체 비우기 (수동 새로고침 등에서 호출) */
    public static void clearCache() { CACHE.clear(); }

    private SensorStatManager() {}

    /** 센서 통계 요청 (캐시 사용). UI 스레드에서 호출, 콜백도 메인 스레드로 전달. */
    public static void fetch(Context context, final Period period, final Callback callback) {
        fetch(context, period, false, callback);
    }

    /**
     * @param forceRefresh true 면 캐시 무시하고 재호출 (수동 새로고침용)
     */
    public static void fetch(Context context, final Period period,
                             boolean forceRefresh, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());

        // 게이트웨이(메인함) 식별자 — 단일 GW 기준 첫 번째 lteNo
        final String lteNo = ZoneStore.getInstance().getFirstLteNo();
        final String key = cacheKey(lteNo, period);
        final String nowHour = hourStamp();

        // ── 캐시 적중: 같은 시(時) 버킷이면 네트워크 없이 즉시 반환 ──
        if (!forceRefresh) {
            CacheEntry hit = CACHE.get(key);
            if (hit != null && hit.hourStamp.equals(nowHour)) {
                main.post(() -> callback.onSuccess(hit.data));
                return;
            }
        }

        // 조회 기간 계산 (edTime = 오늘)
        String[] range = buildDateRange(period);
        final String stTime = range[0];
        final String edTime = range[1];

        // ── DevMode: 더미 데이터 ──
        if (AppConfig.getInstance().isDevMode()) {
            new Thread(() -> {
                try { Thread.sleep(FAKE_DELAY_MS); } catch (InterruptedException ignored) {}
                final List<SensingStatResponse.Point> dummy = buildDummy(period);
                applyLabels(dummy, period, stTime);
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
        SensingStatRequest req = new SensingStatRequest(lteNo, stTime, edTime);

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
                        case WEEKLY:  series = body.toWeekdaySeries();      break; // mon~sun
                        case MONTHLY: series = body.toIndexedSeries("mon"); break; // mon1..mon12
                        case DAILY:
                        default:      series = body.toIndexedSeries("day"); break; // day1..day31
                    }
                    applyLabels(series, period, stTime);
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

    // ── 라벨 보정: 서버의 day1/mon1 인덱스를 stTime 기준 날짜로 변환 ──
    //   일별 → MM/dd, 월별 → yy.MM (stTime 부터 순차 가정). 주별은 요일 라벨 유지.
    //   ※ 서버 인덱싱이 순차가 아니면 이 부분만 조정.
    private static void applyLabels(List<SensingStatResponse.Point> series,
                                    Period period, String stTime) {
        if (period == Period.WEEKLY || series == null || series.isEmpty()) return;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar base = Calendar.getInstance();
            base.setTime(in.parse(stTime));
            int field = (period == Period.MONTHLY) ? Calendar.MONTH : Calendar.DAY_OF_YEAR;
            SimpleDateFormat out = new SimpleDateFormat(
                    period == Period.MONTHLY ? "yy.MM" : "MM/dd", Locale.getDefault());
            for (int i = 0; i < series.size(); i++) {
                Calendar c = (Calendar) base.clone();
                c.add(field, i);
                series.get(i).label = out.format(c.getTime());
            }
        } catch (Exception ignored) { /* 라벨 보정 실패 시 원본 라벨 유지 */ }
    }

    // ── 조회 기간 계산 ──────────────────────────────────────────────
    /** @return [stTime, edTime] "yyyy-MM-dd" */
    private static String[] buildDateRange(Period period) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar ed = Calendar.getInstance();
        Calendar st = Calendar.getInstance();
        switch (period) {
            case WEEKLY:  st.add(Calendar.WEEK_OF_YEAR, -11); break; // 최근 12주
            case MONTHLY: st.add(Calendar.MONTH, -11);        break; // 최근 12개월
            case DAILY:
            default:      st.add(Calendar.DAY_OF_YEAR, -29);  break; // 최근 30일
        }
        return new String[]{ fmt.format(st.getTime()), fmt.format(ed.getTime()) };
    }

    // ── DevMode 더미 생성 (min/max 만, 일부는 미측정) ───────────────
    private static List<SensingStatResponse.Point> buildDummy(Period period) {
        String[] weekdays = {"월","화","수","목","금","토","일"};
        int count;
        switch (period) {
            case WEEKLY:  count = 7;  break;  // 월~일
            case MONTHLY: count = 12; break;  // 12개월
            case DAILY:
            default:      count = 30; break;  // 30일
        }
        List<SensingStatResponse.Point> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String label = (period == Period.WEEKLY) ? weekdays[i - 1]
                    : (period == Period.MONTHLY) ? ("mon" + i) : ("day" + i);
            // 데모: 일부 구간은 미측정(0/0)으로 비워 gap 처리 확인 (주별은 전부 측정)
            boolean missing = (period != Period.WEEKLY) && (i % 9 == 0);
            if (missing) {
                list.add(new SensingStatResponse.Point(label, 0, 0, false));
                continue;
            }
            double base = 24 + Math.sin(i * 0.5) * 3;      // 지온풍 값
            double mn = round(base - 2.0 - (i % 3) * 0.3);
            double mx = round(base + 2.5 + (i % 4) * 0.4);
            list.add(new SensingStatResponse.Point(label, mn, mx, true));
        }
        return list;
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }
}

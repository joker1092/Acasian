package com.acasian.iot.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 관수 유형 프로필 저장/로드.
 * SharedPreferences JSON 직렬화 방식 (Gson 불필요).
 *
 * 사용:
 *   IrrigationProfileManager mgr = IrrigationProfileManager.getInstance(context);
 *   mgr.save(profile);
 *   List<IrrigationProfile> list = mgr.getAll();
 */
public class IrrigationProfileManager {

    private static final String PREF_NAME = "irrigation_profiles";
    private static final String KEY_LIST  = "profile_list";

    private static volatile IrrigationProfileManager instance;
    private final SharedPreferences prefs;

    private IrrigationProfileManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static IrrigationProfileManager getInstance(Context context) {
        if (instance == null) {
            synchronized (IrrigationProfileManager.class) {
                if (instance == null) instance = new IrrigationProfileManager(context);
            }
        }
        return instance;
    }

    // ── 전체 조회 ────────────────────────────────────────────────────────
    public List<IrrigationProfile> getAll() {
        List<IrrigationProfile> list = new ArrayList<>();
        String json = prefs.getString(KEY_LIST, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                IrrigationProfile p = fromJson(arr.getJSONObject(i));
                if (p != null) list.add(p);
            }
        } catch (JSONException ignored) {}
        return list;
    }

    // ── 단건 조회 ────────────────────────────────────────────────────────
    public IrrigationProfile getById(String id) {
        for (IrrigationProfile p : getAll()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    // ── 저장 (추가 또는 업데이트) ─────────────────────────────────────────
    public void save(IrrigationProfile profile) {
        List<IrrigationProfile> list = getAll();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(profile.getId())) {
                list.set(i, profile); found = true; break;
            }
        }
        if (!found) list.add(profile);
        persist(list);
    }

    // ── 삭제 ─────────────────────────────────────────────────────────────
    public void delete(String id) {
        List<IrrigationProfile> list = getAll();
        list.removeIf(p -> p.getId().equals(id));
        persist(list);
    }

    // ── 전체 저장 (순서 변경 등) ─────────────────────────────────────────
    public void saveAll(List<IrrigationProfile> list) { persist(list); }

    // ── JSON 직렬화 ──────────────────────────────────────────────────────
    private void persist(List<IrrigationProfile> list) {
        try {
            JSONArray arr = new JSONArray();
            for (IrrigationProfile p : list) arr.put(toJson(p));
            prefs.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private JSONObject toJson(IrrigationProfile p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id",          p.getId());
        o.put("name",        p.getName() != null ? p.getName() : "");
        o.put("profileType", p.getProfileType().name());
        o.put("targetType",  p.getTargetType().name());
        o.put("zoneId",      p.getZoneId());
        o.put("zoneName",    p.getZoneName());
        o.put("runMinutes",  p.getRunMinutes());
        o.put("restMinutes", p.getRestMinutes());
        o.put("repeatCount", p.getRepeatCount());
        // AUTO 그룹
        JSONArray groups = new JSONArray();
        for (IrrigationProfile.Group g : p.getGroups()) {
            JSONObject go = new JSONObject();
            go.put("id",       g.getId());
            go.put("priority", g.getPriority());
            JSONArray nids = new JSONArray();
            for (String nid : g.getNodeIds()) nids.put(nid);
            go.put("nodeIds", nids);
            groups.put(go);
        }
        o.put("groups", groups);
        // INDIVIDUAL 밸브
        JSONArray ids = new JSONArray();
        for (String d : p.getDeviceIds()) ids.put(d);
        o.put("deviceIds", ids);
        return o;
    }

    private IrrigationProfile fromJson(JSONObject o) {
        try {
            String id       = o.getString("id");
            String name     = o.optString("name", "");
            String zoneId   = o.optString("zoneId",   "");
            String zoneName = o.optString("zoneName", "");
            int runMin      = o.optInt("runMinutes",  30);
            int restMin     = o.optInt("restMinutes", 0);
            int repeatCnt   = o.optInt("repeatCount", 1);

            // ProfileType
            String ptStr = o.optString("profileType", "AUTO");
            IrrigationProfile.ProfileType profileType;
            try { profileType = IrrigationProfile.ProfileType.valueOf(ptStr); }
            catch (Exception e) { profileType = IrrigationProfile.ProfileType.AUTO; }

            // TargetType (하위 호환)
            String ttStr = o.optString("targetType", "ZONE_ALL");
            IrrigationProfile.TargetType targetType;
            try { targetType = IrrigationProfile.TargetType.valueOf(ttStr); }
            catch (Exception e) { targetType = IrrigationProfile.TargetType.ZONE_ALL; }

            // INDIVIDUAL 밸브
            List<String> deviceIds = new ArrayList<>();
            JSONArray dArr = o.optJSONArray("deviceIds");
            if (dArr != null) for (int i = 0; i < dArr.length(); i++) deviceIds.add(dArr.getString(i));

            // AUTO 그룹
            List<IrrigationProfile.Group> groups = new ArrayList<>();
            JSONArray gArr = o.optJSONArray("groups");
            if (gArr != null) {
                for (int i = 0; i < gArr.length(); i++) {
                    JSONObject go = gArr.getJSONObject(i);
                    String gid = go.optString("id", String.valueOf(i));
                    int priority = go.optInt("priority", i + 1);
                    List<String> nids = new ArrayList<>();
                    JSONArray nArr = go.optJSONArray("nodeIds");
                    if (nArr != null) for (int j = 0; j < nArr.length(); j++) nids.add(nArr.getString(j));
                    groups.add(new IrrigationProfile.Group(gid, nids, priority));
                }
            }

            IrrigationProfile p = new IrrigationProfile(id, name, targetType,
                    zoneId, zoneName, deviceIds, runMin, restMin, repeatCnt);
            p.setProfileType(profileType);
            p.setGroups(groups);
            return p;
        } catch (JSONException e) { return null; }
    }
}

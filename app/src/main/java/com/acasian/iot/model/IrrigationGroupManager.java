package com.acasian.iot.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 관수 그룹 저장/로드 — SharedPreferences JSON 방식.
 */
public class IrrigationGroupManager {

    private static final String PREF_NAME = "irrigation_groups";
    private static final String KEY_LIST  = "group_list";

    private static volatile IrrigationGroupManager instance;
    private final SharedPreferences prefs;

    private IrrigationGroupManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static IrrigationGroupManager getInstance(Context context) {
        if (instance == null) {
            synchronized (IrrigationGroupManager.class) {
                if (instance == null) instance = new IrrigationGroupManager(context);
            }
        }
        return instance;
    }

    // ── 전체 조회 (priority 오름차순) ────────────────────────────────────
    public List<IrrigationGroup> getAll() {
        List<IrrigationGroup> list = new ArrayList<>();
        String json = prefs.getString(KEY_LIST, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                IrrigationGroup g = fromJson(arr.getJSONObject(i));
                if (g != null) list.add(g);
            }
        } catch (JSONException ignored) {}
        return list;
    }

    /** 특정 메인함의 그룹만 조회 (priority 오름차순) */
    public List<IrrigationGroup> getByTelNo(String telNo) {
        List<IrrigationGroup> result = new ArrayList<>();
        if (telNo == null) return result;
        for (IrrigationGroup g : getAll())
            if (telNo.equals(g.getTelNo())) result.add(g);
        return result;
    }

    public IrrigationGroup getById(String id) {
        for (IrrigationGroup g : getAll())
            if (g.getId().equals(id)) return g;
        return null;
    }

    // ── 저장 (추가 또는 업데이트) ─────────────────────────────────────────
    public void save(IrrigationGroup group) {
        List<IrrigationGroup> list = getAll();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(group.getId())) {
                list.set(i, group); found = true; break;
            }
        }
        if (!found) list.add(group);
        persist(list);
    }

    public void delete(String id) {
        List<IrrigationGroup> list = getAll();
        list.removeIf(g -> g.getId().equals(id));
        persist(list);
    }

    public void saveAll(List<IrrigationGroup> list) { persist(list); }

    // ── JSON ─────────────────────────────────────────────────────────────
    private void persist(List<IrrigationGroup> list) {
        try {
            JSONArray arr = new JSONArray();
            for (IrrigationGroup g : list) arr.put(toJson(g));
            prefs.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private JSONObject toJson(IrrigationGroup g) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id",       g.getId());
        o.put("name",     g.getName() != null ? g.getName() : "");
        o.put("telNo",    g.getTelNo() != null ? g.getTelNo() : "");
        o.put("zoneName", g.getZoneName() != null ? g.getZoneName() : "");
        JSONArray ids = new JSONArray();
        for (String nid : g.getNodeIds()) ids.put(nid);
        o.put("nodeIds", ids);
        return o;
    }

    private IrrigationGroup fromJson(JSONObject o) {
        try {
            String id       = o.getString("id");
            String name     = o.optString("name", "");
            String telNo    = o.optString("telNo", "");
            String zoneName = o.optString("zoneName", "");
            List<String> nodeIds = new ArrayList<>();
            JSONArray arr = o.optJSONArray("nodeIds");
            if (arr != null)
                for (int i = 0; i < arr.length(); i++) nodeIds.add(arr.getString(i));
            return new IrrigationGroup(id, name, telNo, zoneName, nodeIds);
        } catch (JSONException e) { return null; }
    }
}

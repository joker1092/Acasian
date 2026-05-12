package com.acasian.iot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.acasian.iot.model.IrrigationProfile;
import com.acasian.iot.model.IrrigationProfileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 관수 유형 관리 화면 (독립 Fragment).
 *
 * 어디서든 열 수 있음:
 *   fm.beginTransaction()
 *     .replace(R.id.fragmentContainer, IrrigationTypeManagerFragment.newInstance(), "itm")
 *     .addToBackStack(null)
 *     .commit();
 */
public class IrrigationTypeManagerFragment extends Fragment {

    private IrrigationProfileManager mgr;
    private LinearLayout cardContainer;

    // ZoneStore Observer
    private ZoneStore.Observer zoneObserver;

    public static IrrigationTypeManagerFragment newInstance() {
        return new IrrigationTypeManagerFragment();
    }

    /** 예약 흐름에서 호출 — 유형 저장 후 콜백으로 프로필 ID/이름 전달 */
    public interface OnProfilePickedCallback {
        void onProfilePicked(String profileId, String profileName);
    }

    private OnProfilePickedCallback pickCallback = null;

    public static IrrigationTypeManagerFragment newInstanceForPick(OnProfilePickedCallback cb) {
        IrrigationTypeManagerFragment f = new IrrigationTypeManagerFragment();
        f.pickCallback = cb;
        return f;
    }



    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_irrigation_type_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mgr = IrrigationProfileManager.getInstance(requireContext());

        // insets
        View headerFrame = view.findViewById(R.id.itmHeaderFrame);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusH     = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarH     = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int navHeightPx = (int) getResources().getDimension(R.dimen.nav_height);
            if (headerFrame != null) headerFrame.setPadding(0, statusH, 0, 0);
            v.setPadding(0, 0, 0, navHeightPx + navBarH);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        if (getActivity() != null)
            getActivity().getWindow().setStatusBarColor(
                    getResources().getColor(R.color.forest_dark, null));

        cardContainer = view.findViewById(R.id.itmCardContainer);

        // ← 뒤로
        View btnBack = view.findViewById(R.id.btnItmBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        // + 추가
        View btnAdd = view.findViewById(R.id.btnItmAdd);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showEditDialog(null));

        // getScheduleCate로 서버에서 유형 목록 로드
        loadCatesFromServer();

        // ZoneStore 변경 감지
        zoneObserver = zones -> rebuildCards();
        ZoneStore.getInstance().addObserver(zoneObserver);
    }

    @Override
    public void onDestroyView() {
        if (zoneObserver != null) ZoneStore.getInstance().removeObserver(zoneObserver);
        super.onDestroyView();
        if (getActivity() != null)
            getActivity().getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    }

    // ── 카드 목록 ─────────────────────────────────────────────────────

    private void showDeleteTypeDialog(com.acasian.iot.model.IrrigationProfile p) {
        if (getContext() == null) return;
        float dp = requireContext().getResources().getDisplayMetrics().density;
        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        int pad = Math.round(20 * dp);
        root.setPadding(pad, pad, pad, Math.round(8 * dp));

        android.widget.TextView tvTitle = new android.widget.TextView(requireContext());
        tvTitle.setText("삭제 확인");
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF1B2E1B);
        tvTitle.setPadding(0, 0, 0, Math.round(10 * dp));
        root.addView(tvTitle);

        android.view.View div = new android.view.View(requireContext());
        div.setBackgroundColor(0xFFE0E0E0);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * dp)));
        root.addView(div);

        android.widget.TextView tvMsg = new android.widget.TextView(requireContext());
        tvMsg.setText("'" + p.getName() + "'\n유형을 삭제하시겠습니까?");
        tvMsg.setTextSize(17f);
        tvMsg.setTextColor(0xFF555555);
        tvMsg.setLineSpacing(0, 1.3f);
        tvMsg.setPadding(0, Math.round(14 * dp), 0, Math.round(4 * dp));
        root.addView(tvMsg);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(requireContext());
        btnRow.setGravity(android.view.Gravity.END);
        btnRow.setPadding(0, Math.round(16 * dp), 0, 0);

        androidx.appcompat.app.AlertDialog[] ref = {null};
        android.widget.Button btnCancel = new android.widget.Button(requireContext());
        btnCancel.setText("취소");
        btnCancel.setTextSize(17f);
        btnCancel.setTextColor(0xFF9E9E9E);
        btnCancel.setBackground(null);
        btnCancel.setPadding(Math.round(8*dp),0,Math.round(8*dp),0);
        btnCancel.setOnClickListener(v -> { if(ref[0]!=null) ref[0].dismiss(); });

        android.widget.Button btnDel = new android.widget.Button(requireContext());
        btnDel.setText("삭제");
        btnDel.setTextSize(17f);
        btnDel.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDel.setTextColor(0xFFC62828);
        btnDel.setBackground(null);
        btnDel.setPadding(Math.round(12*dp),0,Math.round(4*dp),0);
        btnDel.setOnClickListener(v -> {
            if(ref[0]!=null) ref[0].dismiss();
            mgr.delete(p.getId());
            // DEV_MODE: 로컬 삭제만
            if (com.acasian.iot.AppConfig.getInstance().isDevMode()) {
                rebuildCards();
                return;
            }
            com.acasian.iot.network.ApiService apiDel =
                    com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
            apiDel.delScheduleCate(new com.acasian.iot.network.ApiService.ScheduleCateDelRequest(p.getId()))
                  .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                      @Override public void onResponse(
                              retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                              retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> res) {
                          requireActivity().runOnUiThread(() -> rebuildCards());
                      }
                      @Override public void onFailure(
                              retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                              Throwable t) {
                          requireActivity().runOnUiThread(() ->
                              toast("유형 삭제 실패: " + t.getMessage()));
                      }
                  });
            rebuildCards();
        });
        btnRow.addView(btnCancel);
        btnRow.addView(btnDel);
        root.addView(btnRow);

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setView(root).create();
        if (dlg.getWindow() != null) {
            android.graphics.drawable.GradientDrawable wbg = new android.graphics.drawable.GradientDrawable();
            wbg.setColor(0xFFFFFFFF); wbg.setCornerRadius(16 * dp);
            dlg.getWindow().setBackgroundDrawable(wbg);
        }
        ref[0] = dlg;
        dlg.show();
    }

    /** 서버에서 관수 유형(ScheduleCate) 목록 로드 → IrrigationProfileManager에 저장 */
    private void loadCatesFromServer() {
        if (getContext() == null) return;
        // DEV_MODE: 서버 호출 없이 로컬 데이터 사용
        if (com.acasian.iot.AppConfig.getInstance().isDevMode()) {
            rebuildCards();
            return;
        }
        com.acasian.iot.storage.SessionManager sess =
                com.acasian.iot.storage.SessionManager.getInstance(requireContext());
        com.acasian.iot.network.ApiService api =
                com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
        // ZoneStore의 모든 메인함 lteNo에 대해 유형 조회
        java.util.List<com.acasian.iot.ZoneStore.ZoneInfo> zones =
                com.acasian.iot.ZoneStore.getInstance().getZones();
        if (zones.isEmpty()) { rebuildCards(); return; }

        // 서버 데이터로 교체 — 기존 로컬 캐시 클리어
        mgr.saveAll(new java.util.ArrayList<>());

        for (com.acasian.iot.ZoneStore.ZoneInfo zone : zones) {
            api.getScheduleCate(new com.acasian.iot.network.ApiService.ScheduleCateRequest(
                    sess.getPhoneNumber(), zone.telNo))
               .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleCateListResponse>() {
                   @Override public void onResponse(
                           retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleCateListResponse> call,
                           retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleCateListResponse> res) {
                       if (res.isSuccessful() && res.body() != null
                               && res.body().isSuccess()
                               && res.body().data != null) {
                           for (com.acasian.iot.network.ApiService.ScheduleCateListResponse.ScheduleCateItem item
                                   : res.body().data) {
                               com.acasian.iot.model.IrrigationProfile p =
                                       new com.acasian.iot.model.IrrigationProfile();
                               p.setId(String.valueOf(item.cateId));
                               p.setName(item.cateName);
                               p.setZoneId(zone.telNo);
                               p.setRunMinutes(item.stime);
                               p.setRestMinutes(item.dtime);
                               p.setRepeatCount(item.reCount);
                               p.setProfileType(item.kind == 1
                                       ? com.acasian.iot.model.IrrigationProfile.ProfileType.AUTO
                                       : com.acasian.iot.model.IrrigationProfile.ProfileType.INDIVIDUAL);
                               // groupList → IrrigationProfile.Group 변환
                               if (item.groupList != null && !item.groupList.isEmpty()) {
                                   java.util.List<com.acasian.iot.model.IrrigationProfile.Group> groups =
                                           new java.util.ArrayList<>();
                                   for (int gi = 0; gi < item.groupList.size(); gi++) {
                                       com.acasian.iot.network.ApiService.ScheduleCateListResponse.GroupItem gi_item =
                                               item.groupList.get(gi);
                                       java.util.List<String> nodeIds = new java.util.ArrayList<>();
                                       if (gi_item.nodeList != null) {
                                           for (com.acasian.iot.network.ApiService.ScheduleCateListResponse.NodeItem ni
                                                   : gi_item.nodeList) {
                                               nodeIds.add(ni.nodeId);
                                           }
                                       }
                                       com.acasian.iot.model.IrrigationProfile.Group g =
                                               new com.acasian.iot.model.IrrigationProfile.Group(
                                                       String.valueOf(gi_item.groupId),
                                                       nodeIds, gi + 1);
                                       groups.add(g);
                                   }
                                   p.setGroups(groups);
                               }
                               mgr.save(p);
                           }
                       }
                       if (getActivity() != null)
                           requireActivity().runOnUiThread(() -> rebuildCards());
                   }
                   @Override public void onFailure(
                           retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleCateListResponse> call,
                           Throwable t) {
                       android.util.Log.e("IrrigationTypeMgr", "getScheduleCate 실패: " + t.getMessage(), t);
                       if (getActivity() != null)
                           requireActivity().runOnUiThread(() -> rebuildCards());
                   }
               });
        }
    }

    private void rebuildCards() {
        if (cardContainer == null) return;
        cardContainer.removeAllViews();
        List<IrrigationProfile> list = mgr.getAll();

        if (list.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText("등록된 유형이 없습니다\n+ 추가 버튼으로 추가해 주세요");
            tv.setTextSize(15f);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, dp(40), 0, 0);
            cardContainer.addView(tv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (IrrigationProfile p : list) {
            View card = inflater.inflate(R.layout.item_irrigation_type_card,
                    cardContainer, false);

            // 내용 바인딩
            bindCard(card, p);

            // ✏ 편집
            View btnEdit = card.findViewById(R.id.btnEditType);
            if (btnEdit != null) btnEdit.setOnClickListener(v -> showEditDialog(p));

            // ✕ 삭제
            View btnDel = card.findViewById(R.id.btnDeleteType);
            if (btnDel != null) btnDel.setOnClickListener(v ->
                showDeleteTypeDialog(p));

            cardContainer.addView(card);
        }
    }

    private void bindCard(View card, IrrigationProfile p) {
        setText(card, R.id.tvTypeName, p.getName());
        setText(card, R.id.tvRunMinutes,  fmtMin(p.getRunMinutes()));
        setText(card, R.id.tvRestMinutes, p.getRestMinutes() <= 0 ? "없음" : fmtMin(p.getRestMinutes()));
        setText(card, R.id.tvRepeatCount, p.getRepeatCount() + "회");

        TextView tvBadge = card.findViewById(R.id.tvTargetBadge);
        android.widget.LinearLayout nodeBadgeContainer =
                card.findViewById(R.id.tvNodeBadgeContainer);

        if (tvBadge != null) {
            String zn = (p.getZoneName() != null && !p.getZoneName().isEmpty())
                    ? p.getZoneName() : "메인함";

            // 그룹 구성 — groups가 있으면 그룹 표시, 없으면 전체/개별 뱃지
            java.util.List<IrrigationProfile.Group> groups = p.getGroups();
            boolean hasGroups = groups != null && !groups.isEmpty();

            if (hasGroups) {
                // 그룹 구성 wrap 표시
                if (tvBadge != null) tvBadge.setVisibility(android.view.View.GONE);
                if (nodeBadgeContainer != null) {
                    nodeBadgeContainer.removeAllViews();
                    nodeBadgeContainer.setOrientation(android.widget.LinearLayout.VERTICAL);

                    java.util.List<ZoneStore.NodeInfo> zNodes =
                            ZoneStore.getInstance().getNodesByTelNo(p.getZoneId());
                    java.util.Map<String, String> idToName = new java.util.HashMap<>();
                    for (ZoneStore.NodeInfo n : zNodes) idToName.put(n.nodeId, n.name);

                    // 구분선 (가동/반복 수치와 그룹 사이)
                    android.view.View divider = new android.view.View(requireContext());
                    divider.setBackgroundColor(0xFFE0E0E0);
                    android.widget.LinearLayout.LayoutParams divLp =
                            new android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1);
                    divLp.setMargins(0, dp(6), 0, dp(8));
                    divider.setLayoutParams(divLp);
                    nodeBadgeContainer.addView(divider);

                    // 그룹 뱃지 wrap 배치
                    android.widget.LinearLayout currentRow = null;
                    int rowUsed = 0;
                    int maxW = requireContext().getResources().getDisplayMetrics().widthPixels - dp(64);

                    for (int gi = 0; gi < groups.size(); gi++) {
                        com.acasian.iot.model.IrrigationProfile.Group g = groups.get(gi);
                        StringBuilder sb = new StringBuilder();
                        sb.append(gi + 1).append(". ");
                        boolean first = true;
                        for (String nid : g.getNodeIds()) {
                            if (!first) sb.append(", ");
                            sb.append(idToName.containsKey(nid) ? idToName.get(nid) : nid);
                            first = false;
                        }
                        android.widget.TextView badge = new android.widget.TextView(requireContext());
                        badge.setText(sb.toString());
                        badge.setTextSize(13f);
                        badge.setTextColor(0xFF1B5E20);
                        badge.setBackgroundResource(R.drawable.bg_badge_ok);
                        android.widget.LinearLayout.LayoutParams bp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        bp.setMargins(0, 0, dp(6), dp(6));
                        badge.setLayoutParams(bp);
                        badge.setPadding(dp(10), dp(4), dp(10), dp(4));

                        badge.measure(
                                android.view.View.MeasureSpec.makeMeasureSpec(
                                        maxW, android.view.View.MeasureSpec.AT_MOST),
                                android.view.View.MeasureSpec.makeMeasureSpec(
                                        0, android.view.View.MeasureSpec.UNSPECIFIED));
                        int bw = badge.getMeasuredWidth() + dp(6);

                        if (currentRow == null || rowUsed + bw > maxW) {
                            currentRow = new android.widget.LinearLayout(requireContext());
                            currentRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                            android.widget.LinearLayout.LayoutParams rowLp =
                                    new android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                            currentRow.setLayoutParams(rowLp);
                            nodeBadgeContainer.addView(currentRow);
                            rowUsed = 0;
                        }
                        currentRow.addView(badge);
                        rowUsed += bw;
                    }
                    nodeBadgeContainer.setVisibility(android.view.View.VISIBLE);
                }} else {
                tvBadge.setVisibility(android.view.View.VISIBLE);
                tvBadge.setText(zn);
                tvBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                if (nodeBadgeContainer != null) {
                    nodeBadgeContainer.removeAllViews();
                    nodeBadgeContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                    java.util.List<ZoneStore.NodeInfo> nodes =
                            ZoneStore.getInstance().getNodesByTelNo(p.getZoneId());
                    java.util.Map<String, String> idToName = new java.util.HashMap<>();
                    for (ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);
                    for (String nodeId : p.getDeviceIds()) {
                        String nodeName = idToName.containsKey(nodeId)
                                ? idToName.get(nodeId) : nodeId;
                        TextView badge = new TextView(requireContext());
                        badge.setText(nodeName);
                        badge.setTextSize(11f);
                        badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                        badge.setBackgroundResource(R.drawable.bg_irrigation_card);
                        android.widget.LinearLayout.LayoutParams lp =
                                new android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.setMargins(0, 0, dp(6), dp(4));
                        badge.setLayoutParams(lp);
                        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
                        nodeBadgeContainer.addView(badge);
                    }
                    nodeBadgeContainer.setVisibility(
                            p.getDeviceIds().isEmpty()
                            ? android.view.View.GONE : android.view.View.VISIBLE);
                }
            }
        }
    }

    // ── 편집/추가 다이얼로그 ──────────────────────────────────────────

    private void showEditDialog(IrrigationProfile existing) {
        boolean isNew = (existing == null);
        IrrigationProfile profile = isNew ? new IrrigationProfile() : existing;

        android.widget.ScrollView sv = new android.widget.ScrollView(requireContext());
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(8));
        sv.addView(form);

        // 유형 이름
        form.addView(makeLabel("유형 이름"));
        EditText etName = makeInput(isNew ? "" : profile.getName(), "예: 봄 아침 관수", false);
        form.addView(etName);
        form.addView(spacer(14));

        // 가동 / 반복
        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout col1 = makeCol();
        col1.addView(makeLabel("가동 시간 (분)"));
        EditText etRun = makeInput(String.valueOf(profile.getRunMinutes()), "30", true);
        col1.addView(etRun);
        LinearLayout col2 = makeCol();
        col2.addView(makeLabel("반복 횟수"));
        EditText etRepeat = makeInput(String.valueOf(profile.getRepeatCount()), "1", true);
        col2.addView(etRepeat);
        ((LinearLayout.LayoutParams) col1.getLayoutParams()).rightMargin = dp(8);
        row1.addView(col1); row1.addView(col2);
        form.addView(row1);
        form.addView(spacer(14));

        // 휴지 시간
        form.addView(makeLabel("휴지 시간 (분)"));
        EditText etRest = makeInput(String.valueOf(profile.getRestMinutes()), "0", true);
        form.addView(etRest);
        form.addView(spacer(14));

        // 메인함 선택
        form.addView(makeLabel("메인함"));
        final String[] selectedZoneId   = {profile.getZoneId()};
        final String[] selectedZoneName = {profile.getZoneName()};
        TextView tvZoneBtn = new TextView(requireContext());
        tvZoneBtn.setPadding(dp(14), dp(14), dp(14), dp(14));
        tvZoneBtn.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_normal));
        tvZoneBtn.setTextSize(16f);
        tvZoneBtn.setClickable(true); tvZoneBtn.setFocusable(true);
        updateZoneBtn(tvZoneBtn, selectedZoneName[0]);
        form.addView(tvZoneBtn);
        form.addView(spacer(14));

        // 관수 방식 토글
        form.addView(makeLabel("관수 방식"));
        LinearLayout typeToggle = new LinearLayout(requireContext());
        typeToggle.setOrientation(LinearLayout.HORIZONTAL);
        typeToggle.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        typeToggle.setBackgroundResource(R.drawable.bg_irrigation_card);
        TextView tvAuto = makeToggleBtn("자동 관수");
        TextView tvInd  = makeToggleBtn("개별 관수");
        typeToggle.addView(tvAuto);
        typeToggle.addView(tvInd);
        form.addView(typeToggle);
        form.addView(spacer(12));

        // ── 자동관수 그룹 영역 ──────────────────────────────────────
        LinearLayout autoSection = new LinearLayout(requireContext());
        autoSection.setOrientation(LinearLayout.VERTICAL);
        autoSection.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        form.addView(autoSection);

        // 그룹 목록 컨테이너
        LinearLayout groupListContainer = new LinearLayout(requireContext());
        groupListContainer.setOrientation(LinearLayout.VERTICAL);
        groupListContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        autoSection.addView(groupListContainer);

        final List<List<String>> groupNodesList = new ArrayList<>();
        for (IrrigationProfile.Group g : profile.getGroups())
            groupNodesList.add(new ArrayList<>(g.getNodeIds()));

        Runnable[] rebuildGroupsRef = {null};
        Runnable rebuildGroups = () -> {
            groupListContainer.removeAllViews();
            for (int i = 0; i < groupNodesList.size(); i++) {
                final int idx = i;
                List<String> nids = groupNodesList.get(i);

                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(8);
                row.setLayoutParams(rowLp);

                // 순위 배지
                TextView tvPri = new TextView(requireContext());
                tvPri.setText(String.valueOf(i + 1));
                tvPri.setTextSize(13f);
                tvPri.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPri.setTextColor(getResources().getColor(R.color.white, null));
                tvPri.setBackgroundResource(R.drawable.bg_btn_zone_start);
                tvPri.setGravity(android.view.Gravity.CENTER);
                tvPri.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
                ((LinearLayout.LayoutParams)tvPri.getLayoutParams()).rightMargin = dp(8);
                row.addView(tvPri);

                // 밸브 이름 (클릭하면 수정)
                TextView tvNodeLabel = new TextView(requireContext());
                String nodeText = buildNodeLabel(nids, selectedZoneId[0]);
                tvNodeLabel.setText(nodeText.isEmpty() ? "밸브 없음" : nodeText);
                tvNodeLabel.setTextSize(14f);
                tvNodeLabel.setTextColor(ContextCompat.getColor(requireContext(),
                        nids.isEmpty() ? R.color.text_hint : R.color.text_primary));
                tvNodeLabel.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_input_normal));
                tvNodeLabel.setPadding(dp(10), dp(8), dp(10), dp(8));
                tvNodeLabel.setClickable(true); tvNodeLabel.setFocusable(true);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                labelLp.rightMargin = dp(4);
                tvNodeLabel.setLayoutParams(labelLp);
                // 밸브 수정 클릭 - 다른 그룹에 속한 밸브 제외
                tvNodeLabel.setOnClickListener(v2 -> {
                    List<String> usedElsewhere = new ArrayList<>();
                    for (int j = 0; j < groupNodesList.size(); j++)
                        if (j != idx) usedElsewhere.addAll(groupNodesList.get(j));
                    showNodePicker(selectedZoneId[0], nids, usedElsewhere, picked -> {
                        groupNodesList.set(idx, picked);
                        if (rebuildGroupsRef[0] != null) rebuildGroupsRef[0].run();
                    });
                });
                row.addView(tvNodeLabel);

                // ↑ 위로
                if (i > 0) {
                    TextView btnUp = makeIconBtn("↑");
                    btnUp.setOnClickListener(v2 -> {
                        List<String> tmp = groupNodesList.remove(idx);
                        groupNodesList.add(idx - 1, tmp);
                        if (rebuildGroupsRef[0] != null) rebuildGroupsRef[0].run();
                    });
                    row.addView(btnUp);
                }

                // ↓ 아래로
                if (i < groupNodesList.size() - 1) {
                    TextView btnDown = makeIconBtn("↓");
                    btnDown.setOnClickListener(v2 -> {
                        List<String> tmp = groupNodesList.remove(idx);
                        groupNodesList.add(idx + 1, tmp);
                        if (rebuildGroupsRef[0] != null) rebuildGroupsRef[0].run();
                    });
                    row.addView(btnDown);
                }

                // ✕ 삭제
                TextView btnDel = makeIconBtn("✕");
                btnDel.setTextColor(ContextCompat.getColor(requireContext(),
                        R.color.device_accent_error));
                btnDel.setOnClickListener(v2 -> {
                    groupNodesList.remove(idx);
                    if (rebuildGroupsRef[0] != null) rebuildGroupsRef[0].run();
                });
                row.addView(btnDel);

                groupListContainer.addView(row);
            }

            // + 그룹 추가 버튼
            TextView btnAddGroup = new TextView(requireContext());
            btnAddGroup.setText("+ 그룹 추가");
            btnAddGroup.setTextSize(14f);
            btnAddGroup.setTypeface(null, android.graphics.Typeface.BOLD);
            btnAddGroup.setTextColor(ContextCompat.getColor(requireContext(), R.color.moss));
            btnAddGroup.setPadding(dp(4), dp(8), dp(4), dp(4));
            btnAddGroup.setClickable(true); btnAddGroup.setFocusable(true);
            btnAddGroup.setBackground(null);
            btnAddGroup.setOnClickListener(v2 -> {
                if (selectedZoneId[0].isEmpty()) {
                    toast("메인함을 먼저 선택해 주세요."); return;
                }
                // 이미 다른 그룹에 속한 밸브 제외
                List<String> usedAll = new ArrayList<>();
                for (List<String> g : groupNodesList) usedAll.addAll(g);
                showNodePicker(selectedZoneId[0], new ArrayList<>(), usedAll, picked -> {
                    if (!picked.isEmpty()) {
                        groupNodesList.add(picked);
                        if (rebuildGroupsRef[0] != null) rebuildGroupsRef[0].run();
                    }
                });
            });
            groupListContainer.addView(btnAddGroup);
        };
        rebuildGroupsRef[0] = rebuildGroups;
        rebuildGroups.run();

        // ── 개별관수 노드 영역 ──────────────────────────────────────
        LinearLayout indSection = new LinearLayout(requireContext());
        indSection.setOrientation(LinearLayout.VERTICAL);
        form.addView(indSection);

        final List<String> selectedDevIds = new ArrayList<>(profile.getDeviceIds());
        TextView tvIndNodeBtn = new TextView(requireContext());
        tvIndNodeBtn.setPadding(dp(14), dp(14), dp(14), dp(14));
        tvIndNodeBtn.setBackground(ContextCompat.getDrawable(requireContext(),
                R.drawable.bg_input_normal));
        tvIndNodeBtn.setTextSize(15f);
        tvIndNodeBtn.setClickable(true); tvIndNodeBtn.setFocusable(true);
        updateNodeBtn(tvIndNodeBtn, selectedDevIds);
        indSection.addView(makeLabel("밸브 선택"));
        indSection.addView(tvIndNodeBtn);

        tvIndNodeBtn.setOnClickListener(v2 ->
            showNodePicker(selectedZoneId[0], selectedDevIds, picked -> {
                selectedDevIds.clear();
                selectedDevIds.addAll(picked);
                updateNodeBtn(tvIndNodeBtn, selectedDevIds);
            }));

        // 방식 토글 상태 & 전환
        final IrrigationProfile.ProfileType[] profileType =
                {profile.getProfileType()};

        Runnable applyTypeStyle = () -> {
            boolean isAuto = profileType[0] == IrrigationProfile.ProfileType.AUTO;
            tvAuto.setBackgroundResource(isAuto
                    ? R.drawable.bg_btn_zone_start : android.R.color.transparent);
            tvAuto.setTextColor(isAuto
                    ? getResources().getColor(R.color.white, null)
                    : ContextCompat.getColor(requireContext(), R.color.moss));
            tvInd.setBackgroundResource(!isAuto
                    ? R.drawable.bg_btn_zone_start : android.R.color.transparent);
            tvInd.setTextColor(!isAuto
                    ? getResources().getColor(R.color.white, null)
                    : ContextCompat.getColor(requireContext(), R.color.moss));
            autoSection.setVisibility(isAuto ? android.view.View.VISIBLE : android.view.View.GONE);
            indSection.setVisibility(!isAuto  ? android.view.View.VISIBLE : android.view.View.GONE);
        };
        applyTypeStyle.run();

        tvAuto.setOnClickListener(v2 -> {
            profileType[0] = IrrigationProfile.ProfileType.AUTO;
            applyTypeStyle.run();
        });
        tvInd.setOnClickListener(v2 -> {
            profileType[0] = IrrigationProfile.ProfileType.INDIVIDUAL;
            applyTypeStyle.run();
        });

        // 메인함 변경 시 그룹/노드 초기화
        tvZoneBtn.setOnClickListener(v2 -> {
            // DEV_MODE: ZoneStore 비어있으면 더미 자동 주입
            if (ZoneStore.getInstance().isEmpty()
                    && AppConfig.getInstance().isDevMode()) {
                AppConfig.injectDemoZones();
            }
            String[] zNames  = ZoneStore.getInstance().getZoneNames();
            String[] zTelNos = ZoneStore.getInstance().getZoneTelNos();
            if (zNames.length == 0) {
                if (AppConfig.getInstance().isDevMode())
                    toast("더미 데이터 주입 실패 — 앱을 재시작해 주세요.");
                else
                    toast("홈 탭에서 데이터 로드 후 시도해 주세요.");
                return;
            }
            int cur = -1;
            for (int i = 0; i < zTelNos.length; i++)
                if (zTelNos[i].equals(selectedZoneId[0])) { cur = i; break; }
            final int[] picked = {cur};
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("메인함 선택")
                .setSingleChoiceItems(zNames, cur, (d, which) -> picked[0] = which)
                .setPositiveButton("확인", (d, w) -> {
                    if (picked[0] >= 0 && picked[0] < zTelNos.length) {
                        boolean changed = !zTelNos[picked[0]].equals(selectedZoneId[0]);
                        selectedZoneId[0]   = zTelNos[picked[0]];
                        selectedZoneName[0] = zNames[picked[0]];
                        updateZoneBtn(tvZoneBtn, selectedZoneName[0]);
                        if (changed) {
                            groupNodesList.clear();
                            selectedDevIds.clear();
                            rebuildGroups.run();
                            updateNodeBtn(tvIndNodeBtn, selectedDevIds);
                        }
                    }
                })
                .setNegativeButton("취소", null).show();
        });

        // 저장
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(isNew ? "관수 유형 추가" : "관수 유형 편집")
            .setView(sv)
            .setPositiveButton("저장", (d, w) -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) { toast("이름을 입력해 주세요."); return; }
                if (selectedZoneId[0].isEmpty()) { toast("메인함을 선택해 주세요."); return; }
                final boolean isAuto = profileType[0] == IrrigationProfile.ProfileType.AUTO;
                if (isAuto && groupNodesList.isEmpty()) {
                    toast("그룹을 1개 이상 추가해 주세요."); return;
                }
                if (!isAuto && selectedDevIds.isEmpty()) {
                    toast("밸브를 선택해 주세요."); return;
                }
                profile.setName(name);
                profile.setProfileType(profileType[0]);
                profile.setTargetType(isAuto
                        ? IrrigationProfile.TargetType.ZONE_ALL
                        : IrrigationProfile.TargetType.ZONE_INDIVIDUAL);
                profile.setZoneId(selectedZoneId[0]);
                profile.setZoneName(selectedZoneName[0]);
                // AUTO: 그룹 저장
                List<IrrigationProfile.Group> groups = new ArrayList<>();
                for (int i = 0; i < groupNodesList.size(); i++) {
                    IrrigationProfile.Group g = new IrrigationProfile.Group();
                    g.setNodeIds(groupNodesList.get(i));
                    g.setPriority(i + 1);
                    groups.add(g);
                }
                profile.setGroups(groups);
                // INDIVIDUAL: 밸브 저장
                profile.setDeviceIds(isAuto ? new ArrayList<>()
                        : new ArrayList<>(selectedDevIds));
                try { profile.setRunMinutes(Math.max(1,
                        Integer.parseInt(etRun.getText().toString()))); }
                catch (Exception e) { profile.setRunMinutes(30); }
                try { profile.setRestMinutes(Math.max(0,
                        Integer.parseInt(etRest.getText().toString()))); }
                catch (Exception e) { profile.setRestMinutes(0); }
                try { profile.setRepeatCount(Math.max(1,
                        Integer.parseInt(etRepeat.getText().toString()))); }
                catch (Exception e) { profile.setRepeatCount(1); }
                // DEV_MODE: 로컬 저장만
                if (com.acasian.iot.AppConfig.getInstance().isDevMode()) {
                    rebuildCards();
                    if (pickCallback != null) {
                        pickCallback.onProfilePicked(profile.getId(), profile.getName());
                        if (getActivity() != null)
                            getActivity().getSupportFragmentManager().popBackStack();
                    }
                    return;
                }
                com.acasian.iot.storage.SessionManager sess =
                        com.acasian.iot.storage.SessionManager.getInstance(requireContext());
                com.acasian.iot.network.ApiService api =
                        com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
                // isAuto 는 위에서 이미 선언됨
                retrofit2.Callback<com.acasian.iot.network.ApiService.CateAddResponse> cateCb =
                    new retrofit2.Callback<com.acasian.iot.network.ApiService.CateAddResponse>() {
                        @Override public void onResponse(
                                retrofit2.Call<com.acasian.iot.network.ApiService.CateAddResponse> call,
                                retrofit2.Response<com.acasian.iot.network.ApiService.CateAddResponse> res) {
                            if (res.isSuccessful() && res.body() != null)
                                requireActivity().runOnUiThread(() -> rebuildCards());
                            else
                                requireActivity().runOnUiThread(() ->
                                    toast("유형 저장 실패 (" + res.code() + ")"));
                        }
                        @Override public void onFailure(
                                retrofit2.Call<com.acasian.iot.network.ApiService.CateAddResponse> call,
                                Throwable t) {
                            requireActivity().runOnUiThread(() ->
                                toast("네트워크 오류: " + t.getMessage()));
                        }
                    };
                // groupList 빌드 — IrrigationProfile.Group → CateGroupRequest
                java.util.List<com.acasian.iot.network.ApiService.CateGroupRequest> groupReqs =
                        new java.util.ArrayList<>();
                if (profile.getGroups() != null) {
                    for (com.acasian.iot.model.IrrigationProfile.Group g : profile.getGroups()) {
                        java.util.List<com.acasian.iot.network.ApiService.CateNodeRequest> nodeReqs =
                                new java.util.ArrayList<>();
                        if (g.getNodeIds() != null) {
                            for (String nid : g.getNodeIds()) {
                                nodeReqs.add(new com.acasian.iot.network.ApiService.CateNodeRequest(
                                        nid, profile.getZoneId()));
                            }
                        }
                        groupReqs.add(new com.acasian.iot.network.ApiService.CateGroupRequest(
                                "그룹" + (profile.getGroups().indexOf(g) + 1),
                                profile.getZoneId(), nodeReqs));
                    }
                }
                if (isNew) {
                    // 신규: 서버에서 생성된 cateId 받아 profile에 반영 후 콜백
                    api.addScheduleCate(new com.acasian.iot.network.ApiService.ScheduleCateAddRequest(
                            profile.getZoneId(), profile.getName(),
                            isAuto ? 1 : 2,
                            profile.getRunMinutes(), profile.getRestMinutes(),
                            profile.getRepeatCount(), sess.getPhoneNumber(), groupReqs))
                       .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.CateAddResponse>() {
                           @Override public void onResponse(
                                   retrofit2.Call<com.acasian.iot.network.ApiService.CateAddResponse> call,
                                   retrofit2.Response<com.acasian.iot.network.ApiService.CateAddResponse> res) {
                               requireActivity().runOnUiThread(() -> {
                                   if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) {
                                       // 서버 발급 cateId 반영
                                       String serverCateId = String.valueOf(res.body().cateId);
                                       profile.setId(serverCateId);
                                       mgr.save(profile);
                                       rebuildCards();
                                       if (pickCallback != null) {
                                           pickCallback.onProfilePicked(serverCateId, profile.getName());
                                           if (getActivity() != null)
                                               getActivity().getSupportFragmentManager().popBackStack();
                                       }
                                   } else {
                                       toast("유형 저장 실패 (" + (res.body() != null ? res.body().message : res.code()) + ")");
                                   }
                               });
                           }
                           @Override public void onFailure(
                                   retrofit2.Call<com.acasian.iot.network.ApiService.CateAddResponse> call,
                                   Throwable t) {
                               requireActivity().runOnUiThread(() ->
                                   toast("네트워크 오류: " + t.getMessage()));
                           }
                       });
                } else {
                    // 수정: cateId 그대로 유지
                    api.updScheduleCate(new com.acasian.iot.network.ApiService.ScheduleCateUpdRequest(
                            profile.getId(), profile.getZoneId(), profile.getName(),
                            isAuto ? 1 : 2,
                            profile.getRunMinutes(), profile.getRestMinutes(),
                            profile.getRepeatCount(), sess.getPhoneNumber(), groupReqs))
                       .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                           @Override public void onResponse(
                                   retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> c,
                                   retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> r) {
                               requireActivity().runOnUiThread(() -> {
                                   rebuildCards();
                                   if (pickCallback != null) {
                                       pickCallback.onProfilePicked(profile.getId(), profile.getName());
                                       if (getActivity() != null)
                                           getActivity().getSupportFragmentManager().popBackStack();
                                   }
                               });
                           }
                           @Override public void onFailure(
                                   retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> c,
                                   Throwable t) {
                               requireActivity().runOnUiThread(() ->
                                   toast("유형 수정 실패: " + t.getMessage()));
                           }
                       });
                }
            })
            .setNegativeButton("취소", null)
            .show();
    }

    /** 노드 다중선택 콜백 다이얼로그 — 다른 그룹에 속한 밸브 제외 */
    private void showNodePicker(String telNo, List<String> current,
                                 java.util.function.Consumer<List<String>> onPicked) {
        showNodePicker(telNo, current, java.util.Collections.emptyList(), onPicked);
    }

    /** 노드 다중선택 — usedInOtherGroups: 다른 그룹에 이미 속한 nodeId 목록 (선택 불가) */
    private void showNodePicker(String telNo, List<String> current,
                                 List<String> usedInOtherGroups,
                                 java.util.function.Consumer<List<String>> onPicked) {
        // DEV_MODE: 노드 없으면 더미 자동 주입
        if (ZoneStore.getInstance().getNodeIds(telNo).length == 0
                && AppConfig.getInstance().isDevMode()) {
            AppConfig.injectDemoZones();
        }
        String[] allNames = getNodeNamesForZone(telNo);
        String[] allIds   = getNodeIdsForZone(telNo);
        if (allNames.length == 0) {
            if (AppConfig.getInstance().isDevMode())
                toast("DEV_MODE: 노드 정보 없음 — AppConfig.injectDemoZones() 확인 필요");
            else
                toast("이 메인함의 노드 정보가 없습니다.");
            return;
        }

        // 다른 그룹에 속한 밸브는 이름에 표시
        String[] displayNames = new String[allNames.length];
        boolean[] enabled     = new boolean[allNames.length];
        for (int i = 0; i < allNames.length; i++) {
            if (usedInOtherGroups.contains(allIds[i])) {
                displayNames[i] = allNames[i] + "  (다른 그룹 사용 중)";
                enabled[i]      = false;
            } else {
                displayNames[i] = allNames[i];
                enabled[i]      = true;
            }
        }

        boolean[] checked = new boolean[allNames.length];
        for (int i = 0; i < allIds.length; i++)
            checked[i] = current.contains(allIds[i]);

        List<String> temp = new ArrayList<>(current);
        androidx.appcompat.app.AlertDialog dialog =
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("밸브 선택")
                .setMultiChoiceItems(displayNames, checked, (d, which, isChecked) -> {
                    if (!enabled[which]) {
                        // 비활성 항목 선택 시 즉시 복원
                        ((android.app.AlertDialog) d).getListView()
                                .setItemChecked(which, false);
                        toast(displayNames[which].replace("  (다른 그룹 사용 중)", "")
                                + "은(는) 이미 다른 그룹에 속해 있습니다.");
                        return;
                    }
                    String nid = allIds[which];
                    if (isChecked) { if (!temp.contains(nid)) temp.add(nid); }
                    else temp.remove(nid);
                })
                .setPositiveButton("확인", (d, w) -> onPicked.accept(temp))
                .setNegativeButton("취소", null)
                .create();
        dialog.show();
    }

    /** nodeIds → 이름 문자열 */
    private String buildNodeLabel(List<String> nodeIds, String telNo) {
        if (nodeIds.isEmpty()) return "";
        List<ZoneStore.NodeInfo> nodes = ZoneStore.getInstance().getNodesByTelNo(telNo);
        java.util.Map<String, String> idToName = new java.util.HashMap<>();
        for (ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);
        StringBuilder sb = new StringBuilder();
        for (String nid : nodeIds) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(idToName.containsKey(nid) ? idToName.get(nid) : nid);
        }
        return sb.toString();
    }

    private LinearLayout makeCol() {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return col;
    }

    private TextView makeIconBtn(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        tv.setClickable(true); tv.setFocusable(true);
        tv.setBackground(ContextCompat.getDrawable(requireContext(),
                android.R.drawable.list_selector_background));
        return tv;
    }
    // ── 유틸 ─────────────────────────────────────────────────────────

    private void setText(View parent, int id, String text) {
        TextView tv = parent.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText makeInput(String value, String hint, boolean numeric) {
        EditText et = new EditText(requireContext());
        et.setText(value);
        et.setHint(hint);
        et.setTextSize(17f);
        et.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        et.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        et.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_normal));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setSingleLine(true);
        if (numeric) {
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setGravity(android.view.Gravity.CENTER);
            et.setTextSize(20f);
            et.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        return et;
    }

    private View spacer(int dpVal) {
        View v = new View(requireContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
        return v;
    }

    private void updateZoneBtn(TextView tv, String zoneName) {
        boolean empty = (zoneName == null || zoneName.isEmpty());
        tv.setText(empty ? "메인함 선택  ▼" : zoneName + "  ▼");
        tv.setTextColor(ContextCompat.getColor(requireContext(),
                empty ? R.color.text_hint : R.color.text_primary));
    }

    private void updateNodeBtn(TextView tv, List<String> ids) {
        boolean empty = ids.isEmpty();
        tv.setText(empty ? "노드 선택" : ids.size() + "개 선택됨");
        tv.setTextColor(ContextCompat.getColor(requireContext(),
                empty ? R.color.text_hint : R.color.text_primary));
    }

    private String[] getNodeNamesForZone(String zoneId) {
        return ZoneStore.getInstance().getNodeNames(zoneId);
    }

    private String[] getNodeIdsForZone(String zoneId) {
        return ZoneStore.getInstance().getNodeIds(zoneId);
    }

    private TextView makeToggleBtn(String label) {
        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextSize(15f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        tv.setClickable(true); tv.setFocusable(true);
        return tv;
    }

    private String fmtMin(int min) {
        if (min <= 0)      return "없음";
        if (min < 60)      return min + "분";
        if (min % 60 == 0) return (min / 60) + "시간";
        return (min / 60) + "시간 " + (min % 60) + "분";
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(requireContext(), msg,
                android.widget.Toast.LENGTH_SHORT).show();
    }
}

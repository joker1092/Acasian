package com.acasian.iot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.acasian.iot.model.IrrigationProfile;
import com.acasian.iot.model.IrrigationProfileManager;
import androidx.fragment.app.DialogFragment;

import java.util.List;

/**
 * 관수 유형 선택 Dialog.
 * - 패널 A: 유형 목록 선택
 * - 패널 B: 새 유형 추가 폼 (인라인 전환)
 * BottomSheet 대신 DialogFragment 사용 — 스크롤/드래그 충돌 없음
 */
public class IrrigationTypePickerSheet extends DialogFragment {

    private static final String ARG_ZONE_NAME = "zone_name";
    private static final String ARG_ZONE_ID   = "zone_id";

    public interface OnProfileSelectedListener {
        void onSelected(IrrigationProfile profile);
    }

    private OnProfileSelectedListener listener;
    private IrrigationProfile selectedProfile;

    public static IrrigationTypePickerSheet newInstance(String zoneName, String zoneId) {
        IrrigationTypePickerSheet sheet = new IrrigationTypePickerSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ZONE_NAME, zoneName);
        args.putString(ARG_ZONE_ID,   zoneId);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnProfileSelectedListener(OnProfileSelectedListener l) {
        this.listener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // STYLE_NO_TITLE: 타이틀바 없는 전체화면 다이얼로그
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_irrigation_type, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String zoneName = getArguments() != null ? getArguments().getString(ARG_ZONE_NAME, "") : "";
        String zoneId   = getArguments() != null ? getArguments().getString(ARG_ZONE_ID,   "") : "";

        // 다이얼로그 윈도우 설정 — 전체화면, 키보드 올라올 때 resize
        if (getDialog() != null && getDialog().getWindow() != null) {
            android.view.Window w = getDialog().getWindow();
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            // Edge-to-edge: API 29 호환 방식
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
        }

        // 하단 네비게이션 바 insets 적용 — 버튼이 가리지 않도록
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int navBottom = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            int statusTop = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, statusTop, 0, navBottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(view);

        // ── 뷰 참조 ──────────────────────────────────────────────
        TextView tvTitle      = view.findViewById(R.id.bsTitle);
        TextView tvZoneName   = view.findViewById(R.id.bsZoneName);
        TextView btnBack      = view.findViewById(R.id.bsBtnBack);
        LinearLayout panelList = view.findViewById(R.id.bsPanelList);
        LinearLayout panelForm = view.findViewById(R.id.bsPanelForm);
        LinearLayout cardContainer = view.findViewById(R.id.bsCardContainer);

        if (tvZoneName != null) tvZoneName.setText(zoneName);

        // ── 패널 전환 헬퍼 ────────────────────────────────────────
        Runnable showList = () -> {
            if (panelList != null) panelList.setVisibility(View.VISIBLE);
            if (panelForm != null) panelForm.setVisibility(View.GONE);
            if (tvTitle   != null) tvTitle.setText("관수 유형 선택");
            if (tvZoneName!= null) tvZoneName.setVisibility(View.VISIBLE);
            if (btnBack   != null) btnBack.setVisibility(View.GONE);
        };
        Runnable showForm = () -> {
            if (panelList != null) panelList.setVisibility(View.GONE);
            if (panelForm != null) panelForm.setVisibility(View.VISIBLE);
            if (tvTitle   != null) tvTitle.setText("새 관수 유형 추가");
            if (btnBack   != null) btnBack.setVisibility(View.VISIBLE);
            // bsZoneName은 어떤 메인함인지 표시하므로 폼에서도 유지
        };

        // ── 카드 목록 빌드 ── 캐시 먼저 표시 후 서버 갱신 ──────────
        buildCards(cardContainer, zoneId);
        refreshCatesFromServer(zoneId, cardContainer);

        // ── 뒤로 버튼 ─────────────────────────────────────────────
        if (btnBack != null) btnBack.setOnClickListener(v -> showList.run());

        // ── + 새 유형 추가 버튼 ───────────────────────────────────
        View bsBtnAddType = view.findViewById(R.id.bsBtnAddType);
        if (bsBtnAddType != null) bsBtnAddType.setOnClickListener(v -> showForm.run());

        // ── 목록 패널 버튼 ────────────────────────────────────────
        Button btnCancel  = view.findViewById(R.id.bsBtnCancel);
        Button btnConfirm = view.findViewById(R.id.bsBtnConfirm);
        if (btnCancel  != null) btnCancel.setOnClickListener(v -> dismiss());
        if (btnConfirm != null) btnConfirm.setOnClickListener(v -> {
            if (selectedProfile == null) {
                android.widget.Toast.makeText(requireContext(),
                        "유형을 선택해 주세요.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) listener.onSelected(selectedProfile);
            dismiss();
        });

        // ── 추가 폼 ───────────────────────────────────────────────
        setupAddForm(view, zoneId, zoneName, cardContainer, showList);
    }

    // ── 서버에서 해당 메인함 유형 최신화 ────────────────────────────────
    private void refreshCatesFromServer(String zoneId, LinearLayout cardContainer) {
        if (getContext() == null || AppConfig.getInstance().isDevMode()) return;
        if (zoneId == null || zoneId.isEmpty()) return;

        com.acasian.iot.storage.SessionManager sess =
                com.acasian.iot.storage.SessionManager.getInstance(requireContext());
        com.acasian.iot.network.ApiService api =
                com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
        com.acasian.iot.model.IrrigationProfileManager mgr =
                com.acasian.iot.model.IrrigationProfileManager.getInstance(requireContext());

        api.getScheduleCate(new com.acasian.iot.network.ApiService.ScheduleCateRequest(
                sess.getPhoneNumber(), zoneId))
           .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.ScheduleCateListResponse>() {
               @Override public void onResponse(
                       retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleCateListResponse> call,
                       retrofit2.Response<com.acasian.iot.network.ApiService.ScheduleCateListResponse> res) {
                   if (!isAdded() || getActivity() == null) return;
                   if (res.isSuccessful() && res.body() != null
                           && res.body().isSuccess() && res.body().data != null) {
                       // 해당 zoneId 유형만 교체
                       java.util.List<com.acasian.iot.model.IrrigationProfile> all = mgr.getAll();
                       all.removeIf(p -> zoneId.equals(p.getZoneId()));
                       for (com.acasian.iot.network.ApiService.ScheduleCateListResponse.ScheduleCateItem item
                               : res.body().data) {
                           com.acasian.iot.model.IrrigationProfile p =
                                   new com.acasian.iot.model.IrrigationProfile();
                           p.setId(String.valueOf(item.cateId));
                           p.setName(item.cateName);
                           p.setZoneId(zoneId);
                           p.setRunMinutes(item.stime);
                           p.setRestMinutes(item.dtime);
                           p.setRepeatCount(item.reCount);
                           p.setProfileType(item.kind == 1
                                   ? com.acasian.iot.model.IrrigationProfile.ProfileType.AUTO
                                   : com.acasian.iot.model.IrrigationProfile.ProfileType.INDIVIDUAL);
                           if (item.groupList != null) {
                               java.util.List<com.acasian.iot.model.IrrigationProfile.Group> groups =
                                       new java.util.ArrayList<>();
                               for (int gi = 0; gi < item.groupList.size(); gi++) {
                                   com.acasian.iot.network.ApiService.ScheduleCateListResponse.GroupItem gi_item =
                                           item.groupList.get(gi);
                                   java.util.List<String> nodeIds = new java.util.ArrayList<>();
                                   if (gi_item.nodeList != null)
                                       for (com.acasian.iot.network.ApiService.ScheduleCateListResponse.NodeItem ni
                                               : gi_item.nodeList)
                                           nodeIds.add(ni.nodeId);
                                   groups.add(new com.acasian.iot.model.IrrigationProfile.Group(
                                           String.valueOf(gi_item.groupId), nodeIds, gi + 1));
                               }
                               p.setGroups(groups);
                           }
                           all.add(p);
                       }
                       mgr.saveAll(all);
                       requireActivity().runOnUiThread(() -> buildCards(cardContainer, zoneId));
                   }
               }
               @Override public void onFailure(
                       retrofit2.Call<com.acasian.iot.network.ApiService.ScheduleCateListResponse> call,
                       Throwable t) {
                   android.util.Log.w("PickerSheet", "getScheduleCate 실패: " + t.getMessage());
               }
           });
    }

    // ── 유형 카드 목록 빌드 ──────────────────────────────────────────

    private void buildCards(LinearLayout container, String zoneId) {
        if (container == null) return;
        container.removeAllViews();

        IrrigationProfileManager mgr = IrrigationProfileManager.getInstance(requireContext());
        List<IrrigationProfile> filtered = new java.util.ArrayList<>();
        for (IrrigationProfile p : mgr.getAll())
            if (zoneId.equals(p.getZoneId())) filtered.add(p);

        if (filtered.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("등록된 유형이 없습니다.\n아래 + 버튼으로 추가해 주세요.");
            empty.setTextSize(15f);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(16));
            container.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (IrrigationProfile p : filtered) {
            View card = inflater.inflate(R.layout.item_irrigation_type_card, container, false);
            bindCard(card, p);
            View btnE = card.findViewById(R.id.btnEditType);
            View btnD = card.findViewById(R.id.btnDeleteType);
            if (btnE != null) btnE.setVisibility(View.GONE);
            if (btnD != null) btnD.setVisibility(View.GONE);
            card.setOnClickListener(v -> {
                selectedProfile = p;
                for (int i = 0; i < container.getChildCount(); i++)
                    container.getChildAt(i).setBackgroundResource(R.drawable.bg_irrigation_card);
                card.setBackgroundResource(R.drawable.bg_irrigation_card_selected);
                updateCardTextColor(card, true);
                for (int i = 0; i < container.getChildCount(); i++) {
                    View c = container.getChildAt(i);
                    if (c != card) updateCardTextColor(c, false);
                }
            });
            container.addView(card);
        }
    }

    // ── 추가 폼 설정 ─────────────────────────────────────────────────

    private void setupAddForm(View view, String zoneId, String zoneName,
                               LinearLayout cardContainer, Runnable showList) {
        EditText etName    = view.findViewById(R.id.bsFormName);
        EditText etRun     = view.findViewById(R.id.bsFormRun);
        EditText etRest    = view.findViewById(R.id.bsFormRest);
        EditText etRepeat  = view.findViewById(R.id.bsFormRepeat);
        android.widget.LinearLayout groupCont = view.findViewById(R.id.bsFormGroupContainer);

        // 항상 그룹 구성 — 그룹별 밸브 묶음
        final java.util.List<java.util.List<String>> groupNodesList = new java.util.ArrayList<>();
        groupNodesList.add(new java.util.ArrayList<>());  // 기본 그룹 1개

        // ── 항상 VISIBLE (토글 없음) ──────────────────────────────────
        Runnable applyToggle = () -> {
            if (groupCont != null) groupCont.setVisibility(View.VISIBLE);
        };

        // ── 그룹 목록 재빌드 ──────────────────────────────────────────
        Runnable[] rebuildRef = {null};
        Runnable rebuildGroups = () -> {
            if (groupCont == null) return;
            groupCont.removeAllViews();
            for (int i = 0; i < groupNodesList.size(); i++) {
                final int idx = i;
                java.util.List<String> nids = groupNodesList.get(i);

                android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                android.widget.LinearLayout.LayoutParams rowLp =
                        new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(8);
                row.setLayoutParams(rowLp);

                // 순서 배지
                android.widget.TextView tvPri = new android.widget.TextView(requireContext());
                tvPri.setText(String.valueOf(i + 1));
                tvPri.setTextSize(13f);
                tvPri.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPri.setTextColor(getResources().getColor(R.color.white, null));
                tvPri.setBackgroundResource(R.drawable.bg_btn_zone_start);
                tvPri.setGravity(android.view.Gravity.CENTER);
                android.widget.LinearLayout.LayoutParams priLp =
                        new android.widget.LinearLayout.LayoutParams(dp(28), dp(28));
                priLp.rightMargin = dp(8);
                tvPri.setLayoutParams(priLp);
                row.addView(tvPri);

                // 밸브 레이블 (탭 → 노드 피커)
                android.widget.TextView tvLabel = new android.widget.TextView(requireContext());
                String lbl = buildNodeLabel(nids, zoneId);
                tvLabel.setText(lbl.isEmpty() ? "밸브 없음 (탭하여 선택)" : lbl);
                tvLabel.setTextSize(13f);
                tvLabel.setTextColor(ContextCompat.getColor(requireContext(),
                        nids.isEmpty() ? R.color.text_hint : R.color.text_primary));
                tvLabel.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_input_normal));
                tvLabel.setPadding(dp(10), dp(8), dp(10), dp(8));
                tvLabel.setClickable(true);
                tvLabel.setFocusable(true);
                android.widget.LinearLayout.LayoutParams lblLp =
                        new android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lblLp.rightMargin = dp(4);
                tvLabel.setLayoutParams(lblLp);
                tvLabel.setOnClickListener(v2 -> {
                    java.util.List<String> usedElsewhere = new java.util.ArrayList<>();
                    for (int j = 0; j < groupNodesList.size(); j++)
                        if (j != idx) usedElsewhere.addAll(groupNodesList.get(j));
                    showNodePicker(zoneId, nids, usedElsewhere, picked -> {
                        groupNodesList.set(idx, picked);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                });
                row.addView(tvLabel);

                // ↑ 위로
                if (i > 0) {
                    android.widget.TextView btnUp = makeIconBtn("↑");
                    btnUp.setOnClickListener(v2 -> {
                        java.util.List<String> tmp = groupNodesList.remove(idx);
                        groupNodesList.add(idx - 1, tmp);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                    row.addView(btnUp);
                }
                // ↓ 아래로
                if (i < groupNodesList.size() - 1) {
                    android.widget.TextView btnDown = makeIconBtn("↓");
                    btnDown.setOnClickListener(v2 -> {
                        java.util.List<String> tmp = groupNodesList.remove(idx);
                        groupNodesList.add(idx + 1, tmp);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                    row.addView(btnDown);
                }
                // ✕ 삭제
                if (groupNodesList.size() > 1) {
                    android.widget.TextView btnDel = makeIconBtn("✕");
                    btnDel.setTextColor(getResources().getColor(R.color.device_accent_error, null));
                    btnDel.setOnClickListener(v2 -> {
                        groupNodesList.remove(idx);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    });
                    row.addView(btnDel);
                }
                groupCont.addView(row);
            }

            // + 그룹 추가 버튼
            android.widget.TextView btnAddGrp = new android.widget.TextView(requireContext());
            btnAddGrp.setText("+ 그룹 추가");
            btnAddGrp.setTextSize(13f);
            btnAddGrp.setTypeface(null, android.graphics.Typeface.BOLD);
            btnAddGrp.setTextColor(ContextCompat.getColor(requireContext(), R.color.moss));
            btnAddGrp.setPadding(dp(4), dp(6), dp(4), dp(4));
            btnAddGrp.setClickable(true);
            btnAddGrp.setFocusable(true);
            btnAddGrp.setOnClickListener(v2 -> {
                java.util.List<String> usedAll = new java.util.ArrayList<>();
                for (java.util.List<String> g : groupNodesList) usedAll.addAll(g);
                showNodePicker(zoneId, new java.util.ArrayList<>(), usedAll, picked -> {
                    if (!picked.isEmpty()) {
                        groupNodesList.add(picked);
                        if (rebuildRef[0] != null) rebuildRef[0].run();
                    }
                });
            });
            groupCont.addView(btnAddGrp);
        };
        rebuildRef[0] = rebuildGroups;

        applyToggle.run();
        rebuildGroups.run();

        // ── 폼 초기화 헬퍼 ───────────────────────────────────────────
        Runnable resetForm = () -> {
            if (etName   != null) etName.setText("");
            if (etRun    != null) etRun.setText("30");
            if (etRest   != null) etRest.setText("0");
            if (etRepeat != null) etRepeat.setText("1");
            groupNodesList.clear();
            groupNodesList.add(new java.util.ArrayList<>());
            rebuildGroups.run();
        };

        // 취소
        Button btnFormCancel = view.findViewById(R.id.bsFormBtnCancel);
        if (btnFormCancel != null) btnFormCancel.setOnClickListener(v -> {
            resetForm.run();
            showList.run();
        });

        // 저장 후 선택
        Button btnFormSave = view.findViewById(R.id.bsFormBtnSave);
        if (btnFormSave != null) btnFormSave.setOnClickListener(v -> {
            String name = etName != null ? etName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                android.widget.Toast.makeText(requireContext(),
                        "유형 이름을 입력해 주세요.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (groupNodesList.stream().allMatch(java.util.List::isEmpty)) {
                android.widget.Toast.makeText(requireContext(),
                        "그룹에 밸브를 1개 이상 추가해 주세요.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            int run = 30, rest = 0, rep = 1;
            try { run  = Math.max(1, Integer.parseInt(etRun.getText().toString())); }
            catch (Exception ignored) {}
            try { rest = Math.max(0, Integer.parseInt(etRest.getText().toString())); }
            catch (Exception ignored) {}
            try { rep  = Math.max(1, Integer.parseInt(etRepeat.getText().toString())); }
            catch (Exception ignored) {}

            IrrigationProfile np = new IrrigationProfile();
            np.setId(java.util.UUID.randomUUID().toString());
            np.setName(name);
            np.setZoneId(zoneId);
            np.setZoneName(zoneName);
            np.setRunMinutes(run);
            np.setRestMinutes(rest);
            np.setRepeatCount(rep);

            // 항상 그룹 구성으로 저장
            np.setProfileType(IrrigationProfile.ProfileType.AUTO);
            np.setTargetType(IrrigationProfile.TargetType.ZONE_ALL);
            java.util.List<IrrigationProfile.Group> groups = new java.util.ArrayList<>();
            for (int i = 0; i < groupNodesList.size(); i++) {
                IrrigationProfile.Group g = new IrrigationProfile.Group();
                g.setNodeIds(groupNodesList.get(i));
                g.setPriority(i + 1);
                groups.add(g);
            }
            np.setGroups(groups);

            IrrigationProfileManager mgr = IrrigationProfileManager.getInstance(requireContext());

            // DEV_MODE: 로컬 저장만
            if (com.acasian.iot.AppConfig.getInstance().isDevMode()) {
                buildCards(cardContainer, zoneId);
                selectedProfile = np;
                highlightSelected(cardContainer, np.getId());
                resetForm.run();
                showList.run();
                android.widget.Toast.makeText(requireContext(),
                        "'" + name + "' 유형이 추가되었습니다.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // 서버에 addScheduleCate 호출 → 응답받은 cateId로 profile 업데이트
            com.acasian.iot.storage.SessionManager sess =
                    com.acasian.iot.storage.SessionManager.getInstance(requireContext());
            com.acasian.iot.network.ApiService api =
                    com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
            // groupList 빌드 — groupNodesList → CateGroupRequest
            java.util.List<com.acasian.iot.network.ApiService.CateGroupRequest> groupReqs =
                    new java.util.ArrayList<>();
            for (int gi = 0; gi < groupNodesList.size(); gi++) {
                java.util.List<String> nodeIds = groupNodesList.get(gi);
                java.util.List<com.acasian.iot.network.ApiService.CateNodeRequest> nodeReqs =
                        new java.util.ArrayList<>();
                for (String nid : nodeIds) {
                    nodeReqs.add(new com.acasian.iot.network.ApiService.CateNodeRequest(nid, zoneId));
                }
                groupReqs.add(new com.acasian.iot.network.ApiService.CateGroupRequest(
                        "그룹" + (gi + 1), zoneId, nodeReqs));
            }
            api.addScheduleCate(new com.acasian.iot.network.ApiService.ScheduleCateAddRequest(
                    zoneId, name, 1, run, rest, rep, sess.getPhoneNumber(), groupReqs))
               .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.CateAddResponse>() {
                   @Override public void onResponse(
                           retrofit2.Call<com.acasian.iot.network.ApiService.CateAddResponse> call,
                           retrofit2.Response<com.acasian.iot.network.ApiService.CateAddResponse> res) {
                       if (!isAdded() || getActivity() == null) return;
                       requireActivity().runOnUiThread(() -> {
                           if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) {
                               // 서버 발급 cateId 반영
                               String serverCateId = String.valueOf(res.body().cateId);
                               np.setId(serverCateId);
                               mgr.save(np);
                               selectedProfile = np;
                               buildCards(cardContainer, zoneId);
                               highlightSelected(cardContainer, serverCateId);
                               resetForm.run();
                               showList.run();
                               android.widget.Toast.makeText(requireContext(),
                                       "'" + name + "' 유형이 추가되었습니다.", android.widget.Toast.LENGTH_SHORT).show();
                           } else {
                               android.widget.Toast.makeText(requireContext(),
                                       "유형 저장 실패 (" + res.code() + ")", android.widget.Toast.LENGTH_SHORT).show();
                           }
                       });
                   }
                   @Override public void onFailure(
                           retrofit2.Call<com.acasian.iot.network.ApiService.CateAddResponse> call,
                           Throwable t) {
                       if (!isAdded() || getActivity() == null) return;
                       requireActivity().runOnUiThread(() ->
                           android.widget.Toast.makeText(requireContext(),
                                   "네트워크 오류: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                   }
               });
        });
    }

    // ── 노드 피커 다이얼로그 ─────────────────────────────────────────
    // 다른 그룹 사용 중인 밸브 → isEnabled() 오버라이드로 터치 차단 + 회색 표시
    private void highlightSelected(LinearLayout container, String profileId) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View c = container.getChildAt(i);
            boolean sel = profileId != null && profileId.equals(c.getTag());
            c.setBackgroundResource(sel
                    ? R.drawable.bg_irrigation_card_selected
                    : R.drawable.bg_irrigation_card);
            updateCardTextColor(c, sel);
        }
    }

    private void showNodePicker(String telNo, java.util.List<String> current,
                                 java.util.List<String> usedElsewhere,
                                 java.util.function.Consumer<java.util.List<String>> onPicked) {
        String[] allIds   = ZoneStore.getInstance().getNodeIds(telNo);
        String[] allNames = ZoneStore.getInstance().getNodeNames(telNo);
        if (allIds == null || allIds.length == 0) {
            android.widget.Toast.makeText(requireContext(),
                    "이 메인함의 노드 정보가 없습니다.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        final int count = allIds.length;
        final String[] displayNames = new String[count];
        final boolean[] enabled     = new boolean[count];
        final boolean[] checked     = new boolean[count];
        for (int i = 0; i < count; i++) {
            boolean usedOther = usedElsewhere.contains(allIds[i]);
            displayNames[i] = usedOther
                    ? allNames[i] + "  (다른 그룹 사용 중)"
                    : allNames[i];
            enabled[i] = !usedOther;
            checked[i] = current.contains(allIds[i]);
        }

        final java.util.List<String> temp = new java.util.ArrayList<>(current);

        // isEnabled() 오버라이드 — 비활성 항목은 터치 이벤트 자체를 받지 않음
        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<String>(
                        requireContext(),
                        android.R.layout.simple_list_item_multiple_choice,
                        displayNames) {
                    @Override
                    public boolean isEnabled(int position) {
                        return enabled[position];
                    }

                    @Override
                    public android.view.View getView(int position,
                            android.view.View convertView,
                            android.view.ViewGroup parent) {
                        android.view.View v = super.getView(position, convertView, parent);
                        // 비활성 항목 회색 처리
                        v.setAlpha(enabled[position] ? 1f : 0.38f);
                        return v;
                    }
                };

        // 체크 상태 관리용 SparseBooleanArray
        final android.util.SparseBooleanArray checkedMap = new android.util.SparseBooleanArray();
        for (int i = 0; i < count; i++) if (checked[i]) checkedMap.put(i, true);

        androidx.appcompat.app.AlertDialog dialog =
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("밸브 선택")
                .setAdapter(adapter, null)
                .setPositiveButton("확인", (d, w) -> onPicked.accept(temp))
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            android.widget.ListView lv =
                    ((androidx.appcompat.app.AlertDialog) dlg).getListView();
            lv.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
            // 초기 체크 상태 복원
            for (int i = 0; i < count; i++) lv.setItemChecked(i, checked[i]);
            // 항목 탭 리스너 — isEnabled()=false 항목은 이 리스너 자체가 호출 안 됨
            lv.setOnItemClickListener((parent, view, pos, id) -> {
                if (!enabled[pos]) return;
                boolean nowChecked = lv.isItemChecked(pos);
                if (nowChecked) { if (!temp.contains(allIds[pos])) temp.add(allIds[pos]); }
                else temp.remove(allIds[pos]);
            });
        });

        dialog.show();
    }

    // ── 노드 이름 레이블 생성 ────────────────────────────────────────
    private String buildNodeLabel(java.util.List<String> nodeIds, String telNo) {
        if (nodeIds == null || nodeIds.isEmpty()) return "";
        java.util.List<ZoneStore.NodeInfo> nodes = ZoneStore.getInstance().getNodesByTelNo(telNo);
        java.util.Map<String, String> idToName = new java.util.HashMap<>();
        for (ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);
        StringBuilder sb = new StringBuilder();
        for (String nid : nodeIds) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(idToName.containsKey(nid) ? idToName.get(nid) : nid);
        }
        return sb.toString();
    }

    // ── 아이콘 버튼 생성 ─────────────────────────────────────────────
    private android.widget.TextView makeIconBtn(String text) {
        android.widget.TextView tv = new android.widget.TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(32), dp(32)));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    // ── 카드 바인딩 ──────────────────────────────────────────────────

    private void bindCard(View card, IrrigationProfile p) {
        card.setTag(p.getId());
        setText(card, R.id.tvTypeName,    p.getName());
        setText(card, R.id.tvRunMinutes,  fmtMin(p.getRunMinutes()));
        setText(card, R.id.tvRestMinutes, p.getRestMinutes() <= 0 ? "없음" : fmtMin(p.getRestMinutes()));
        setText(card, R.id.tvRepeatCount, p.getRepeatCount() + "회");
        TextView tvBadge = card.findViewById(R.id.tvTargetBadge);
        LinearLayout nodeCont = card.findViewById(R.id.tvNodeBadgeContainer);

        java.util.List<com.acasian.iot.model.IrrigationProfile.Group> groups = p.getGroups();
        boolean hasGroups = groups != null && !groups.isEmpty();

        if (hasGroups) {
            // 그룹 구성 wrap 표시
            if (tvBadge != null) tvBadge.setVisibility(View.GONE);
            if (nodeCont != null) {
                nodeCont.removeAllViews();
                nodeCont.setOrientation(LinearLayout.VERTICAL);
                List<ZoneStore.NodeInfo> zNodes =
                        ZoneStore.getInstance().getNodesByTelNo(p.getZoneId());
                java.util.Map<String, String> idToName = new java.util.HashMap<>();
                for (ZoneStore.NodeInfo n : zNodes) idToName.put(n.nodeId, n.name);

                // 구분선 (수치 영역과 그룹 뱃지 사이)
                android.view.View divider = new android.view.View(requireContext());
                divider.setBackgroundColor(0xFFE0E0E0);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.setMargins(0, dp(6), 0, dp(8));
                divider.setLayoutParams(divLp);
                nodeCont.addView(divider);

                // 그룹 뱃지 wrap 배치
                LinearLayout currentRow = null;
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
                    TextView badge = new TextView(requireContext());
                    badge.setText(sb.toString());
                    badge.setTextSize(13f);
                    badge.setTextColor(0xFF1B5E20);
                    badge.setBackgroundResource(R.drawable.bg_badge_ok);
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
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
                        currentRow = new LinearLayout(requireContext());
                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        currentRow.setLayoutParams(rowLp);
                        nodeCont.addView(currentRow);
                        rowUsed = 0;
                    }
                    currentRow.addView(badge);
                    rowUsed += bw;
                }
                nodeCont.setVisibility(View.VISIBLE);
            }
        } else if (tvBadge != null) {
            tvBadge.setVisibility(View.VISIBLE);
            String zn = p.getZoneName() != null ? p.getZoneName() : "";
            if (p.getTargetType() == IrrigationProfile.TargetType.ZONE_ALL) {
                tvBadge.setText("전체");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_ok);
                if (nodeCont != null) nodeCont.setVisibility(View.GONE);
            } else {
                tvBadge.setText(zn);
                tvBadge.setBackgroundResource(R.drawable.bg_badge_neutral);
                if (nodeCont != null) nodeCont.setVisibility(View.GONE);
            }
        }
    }

    private void updateCardTextColor(View card, boolean selected) {
        // 선택 배경(#EAF5EA 연초록)에는 진한 초록, 미선택에는 기본 색상
        int nameColor  = ContextCompat.getColor(requireContext(),
                selected ? R.color.forest_dark : R.color.text_primary);
        int valueColor = ContextCompat.getColor(requireContext(), R.color.forest_dark);
        setText(card, R.id.tvTypeName,    null, nameColor);
        setText(card, R.id.tvRunMinutes,  null, valueColor);
        setText(card, R.id.tvRestMinutes, null, valueColor);
        setText(card, R.id.tvRepeatCount, null, valueColor);
    }

    private void setText(View p, int id, String text) {
        TextView tv = p.findViewById(id);
        if (tv != null && text != null) tv.setText(text);
    }
    private void setText(View p, int id, String text, int color) {
        TextView tv = p.findViewById(id);
        if (tv != null) { if (text != null) tv.setText(text); tv.setTextColor(color); }
    }
    private String fmtMin(int min) {
        if (min < 60) return min + "분";
        if (min % 60 == 0) return (min / 60) + "시간";
        return (min / 60) + "시간 " + (min % 60) + "분";
    }
    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

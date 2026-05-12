package com.acasian.iot;

import android.app.AlertDialog;
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
import androidx.fragment.app.Fragment;

import com.acasian.iot.model.IrrigationGroup;
import com.acasian.iot.model.IrrigationGroupManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 그룹 관리 화면.
 * - 메인함 탭으로 필터
 * - 그룹 카드: 우선순위 배지 + 밸브 뱃지 + 수정/삭제
 * - 추가/수정 다이얼로그: 그룹명 + 밸브 다중선택 + 우선순위
 */
public class GroupManagerFragment extends Fragment {

    private String         selectedTelNo = "";
    private ZoneStore.Observer zoneObserver;

    public static GroupManagerFragment newInstance() {
        return new GroupManagerFragment();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 뒤로가기
        view.findViewById(R.id.btnGroupBack).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        // + 추가 버튼
        view.findViewById(R.id.btnGroupAdd).setOnClickListener(v ->
            showGroupDialog(null));

        // 메인함 탭 빌드
        buildZoneTabs(view);

        // 그룹 목록
        rebuildCards(view);

        // ZoneStore 변경 감지
        zoneObserver = zones -> {
            if (isAdded()) {
                buildZoneTabs(view);
                rebuildCards(view);
            }
        };
        ZoneStore.getInstance().addObserver(zoneObserver);
    }

    @Override
    public void onDestroyView() {
        ZoneStore.getInstance().removeObserver(zoneObserver);
        super.onDestroyView();
    }

    // ── 메인함 탭 ────────────────────────────────────────────────────────

    private void buildZoneTabs(View root) {
        LinearLayout container = root.findViewById(R.id.groupZoneTabContainer);
        if (container == null) return;
        container.removeAllViews();

        List<ZoneStore.ZoneInfo> zones = ZoneStore.getInstance().getZones();
        if (zones.isEmpty()) return;

        // 선택된 탭이 없으면 첫 번째 메인함 선택
        if (selectedTelNo.isEmpty()) selectedTelNo = zones.get(0).telNo;

        for (ZoneStore.ZoneInfo zone : zones) {
            TextView tab = new TextView(requireContext());
            tab.setText(zone.name);
            tab.setTextSize(14f);
            tab.setTypeface(null, android.graphics.Typeface.BOLD);
            boolean selected = zone.telNo.equals(selectedTelNo);
            tab.setTextColor(selected
                    ? getResources().getColor(R.color.white, null)
                    : ContextCompat.getColor(requireContext(), R.color.moss));
            tab.setBackgroundResource(selected
                    ? R.drawable.bg_btn_zone_start
                    : android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            lp.setMargins(0, 8, dp(12), 8);
            tab.setLayoutParams(lp);
            tab.setPadding(dp(16), 0, dp(16), 0);
            tab.setGravity(android.view.Gravity.CENTER);
            tab.setClickable(true);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> {
                selectedTelNo = zone.telNo;
                buildZoneTabs(root);
                rebuildCards(root);
            });
            container.addView(tab);
        }
    }

    // ── 그룹 카드 목록 ───────────────────────────────────────────────────

    private void rebuildCards(View root) {
        LinearLayout container = root.findViewById(R.id.groupListContainer);
        if (container == null) return;
        container.removeAllViews();

        if (selectedTelNo.isEmpty()) return;

        List<IrrigationGroup> groups =
                IrrigationGroupManager.getInstance(requireContext())
                        .getByTelNo(selectedTelNo);

        if (groups.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("등록된 그룹이 없습니다.\n+ 추가 버튼으로 그룹을 만들어 주세요.");
            empty.setTextSize(15f);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(48), 0, 0);
            container.addView(empty);
            return;
        }

        for (IrrigationGroup group : groups) {
            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_group_card, container, false);
            bindCard(card, group, root);
            container.addView(card);
        }
    }

    private void bindCard(View card, IrrigationGroup group, View root) {
        // 그룹명
        TextView tvName = card.findViewById(R.id.tvGroupName);
        if (tvName != null) tvName.setText(group.getName());

        // 밸브 뱃지
        LinearLayout badgeContainer = card.findViewById(R.id.groupNodeBadgeContainer);
        if (badgeContainer != null) {
            badgeContainer.removeAllViews();
            List<ZoneStore.NodeInfo> nodes =
                    ZoneStore.getInstance().getNodesByTelNo(group.getTelNo());
            java.util.Map<String, String> idToName = new java.util.HashMap<>();
            for (ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);

            if (group.getNodeIds().isEmpty()) {
                TextView empty = new TextView(requireContext());
                empty.setText("밸브 없음");
                empty.setTextSize(12f);
                empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
                badgeContainer.addView(empty);
            } else {
                for (String nid : group.getNodeIds()) {
                    String name = idToName.containsKey(nid) ? idToName.get(nid) : nid;
                    TextView badge = makeBadge(name);
                    badgeContainer.addView(badge);
                }
            }
        }

        // 수정
        card.findViewById(R.id.btnGroupEdit).setOnClickListener(v ->
                showGroupDialog(group));

        // 삭제
        card.findViewById(R.id.btnGroupDelete).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                    .setTitle("그룹 삭제")
                    .setMessage("'" + group.getName() + "' 그룹을 삭제하시겠습니까?")
                    .setPositiveButton("삭제", (d, w) -> {
                        IrrigationGroupManager.getInstance(requireContext())
                                .delete(group.getId());
                        rebuildCards(root);
                    })
                    .setNegativeButton("취소", null).show());
    }

    // ── 추가/수정 다이얼로그 ─────────────────────────────────────────────

    private void showGroupDialog(@Nullable IrrigationGroup existing) {
        boolean isEdit = existing != null;
        View dlg = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_group_edit, null);

        EditText etName  = dlg.findViewById(R.id.groupEditName);
        TextView tvNodes = dlg.findViewById(R.id.groupEditNodeBtn);

        // 초기값 채우기
        final List<String> selectedNodeIds = new ArrayList<>();
        if (isEdit) {
            etName.setText(existing.getName());
            selectedNodeIds.addAll(existing.getNodeIds());
        }
        updateNodeBtnText(tvNodes, selectedNodeIds);

        // 밸브 선택 버튼
        tvNodes.setOnClickListener(v -> {
            String[] nodeNames = ZoneStore.getInstance().getNodeNames(selectedTelNo);
            String[] nodeIds   = ZoneStore.getInstance().getNodeIds(selectedTelNo);
            if (nodeNames.length == 0) {
                toast("이 메인함의 노드 정보가 없습니다."); return;
            }
            boolean[] checked = new boolean[nodeNames.length];
            for (int i = 0; i < nodeIds.length; i++)
                checked[i] = selectedNodeIds.contains(nodeIds[i]);
            final String[] fIds = nodeIds;
            new AlertDialog.Builder(requireContext())
                .setTitle("밸브 선택")
                .setMultiChoiceItems(nodeNames, checked, (d, which, isChecked) -> {
                    String nid = fIds[which];
                    if (isChecked) { if (!selectedNodeIds.contains(nid)) selectedNodeIds.add(nid); }
                    else selectedNodeIds.remove(nid);
                })
                .setPositiveButton("확인", (d, w) -> updateNodeBtnText(tvNodes, selectedNodeIds))
                .setNegativeButton("취소", null).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle(isEdit ? "그룹 수정" : "그룹 추가")
            .setView(dlg)
            .setPositiveButton("저장", null)  // null로 설정 후 직접 처리
            .setNegativeButton("취소", null)
            .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) { toast("그룹명을 입력해 주세요."); return; }
                if (selectedNodeIds.isEmpty()) { toast("밸브를 1개 이상 선택해 주세요."); return; }

                IrrigationGroup group = isEdit ? existing : new IrrigationGroup();
                group.setName(name);
                group.setTelNo(selectedTelNo);
                group.setZoneName(ZoneStore.getInstance().getByTelNo(selectedTelNo) != null
                        ? ZoneStore.getInstance().getByTelNo(selectedTelNo).name : "");
                group.setNodeIds(new ArrayList<>(selectedNodeIds));

                IrrigationGroupManager.getInstance(requireContext()).save(group);
                dialog.dismiss();
                rebuildCards(requireView());
            });
        });
        dialog.show();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private void updateNodeBtnText(TextView tv, List<String> nodeIds) {
        if (tv == null) return;
        if (nodeIds.isEmpty()) {
            tv.setText("밸브 선택 (필수)");
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        } else {
            List<ZoneStore.NodeInfo> nodes =
                    ZoneStore.getInstance().getNodesByTelNo(selectedTelNo);
            java.util.Map<String, String> idToName = new java.util.HashMap<>();
            for (ZoneStore.NodeInfo n : nodes) idToName.put(n.nodeId, n.name);
            StringBuilder sb = new StringBuilder();
            for (String nid : nodeIds) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(idToName.containsKey(nid) ? idToName.get(nid) : nid);
            }
            tv.setText(sb.toString());
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        }
    }

    private TextView makeBadge(String text) {
        TextView badge = new TextView(requireContext());
        badge.setText(text);
        badge.setTextSize(11f);
        badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        badge.setBackgroundResource(R.drawable.bg_badge_neutral);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), dp(4));
        badge.setLayoutParams(lp);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        return badge;
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(requireContext(), msg,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

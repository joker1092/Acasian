package com.acasian.iot;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.acasian.iot.storage.SessionManager;
import com.acasian.iot.ZoneStore;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private static final String PREF_ENV = "env_standard";

    private SessionManager session;

    // ── 비밀번호 변경 ────────────────────────────────────────────
    private LinearLayout menuInfoChange;
    private LinearLayout panelInfoChange;
    private ImageView    icInfoChangeArrow;
    private boolean      infoChangeOpen = false;

    private LinearLayout containerCurrentPw, containerNewPw, containerConfirmPw;
    private EditText     etCurrentPw, etNewPw, etConfirmPw;
    private ImageView    btnToggleCurrent, btnToggleNew, btnToggleConfirm;
    private boolean visibleCurrent = false, visibleNew = false, visibleConfirm = false;

    // ── 환경기준 ─────────────────────────────────────────────────
    private LinearLayout menuEnvStandard;
    private LinearLayout panelEnvStandard;
    private TextView     icEnvArrow;
    private boolean      envOpen = false;

    private EditText etSoilMin, etSoilMax, etTempMax, etEcMin, etEcMax, etPhMin, etPhMax;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setStatusBarColor(false);
    }

    private void setStatusBarColor(boolean on) {
        if (getActivity() == null) return;
        int color = on
                ? getResources().getColor(R.color.forest_dark, null)
                : android.graphics.Color.TRANSPARENT;
        getActivity().getWindow().setStatusBarColor(color);
        androidx.core.view.WindowInsetsControllerCompat ctrl =
                androidx.core.view.WindowCompat.getInsetsController(
                        getActivity().getWindow(),
                        getActivity().getWindow().getDecorView());
        ctrl.setAppearanceLightStatusBars(false);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        session = SessionManager.getInstance(requireContext());

        // insets
        View headerFrame = view.findViewById(R.id.settingsHeaderFrame);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusH  = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarH  = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int navHeightPx = (int) getResources().getDimension(R.dimen.nav_height);
            if (headerFrame != null) headerFrame.setPadding(0, statusH, 0, 0);
            v.setPadding(0, 0, 0, navHeightPx + navBarH);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        setStatusBarColor(true);

        // 전화번호 표시
        TextView tvPhone = view.findViewById(R.id.tvSettingsPhone);
        if (tvPhone != null) {
            String phone = session.getPhoneNumberFormatted();
            tvPhone.setText(phone.isEmpty() ? "-" : phone);
        }

        // ── 홈 아이콘 버튼 — 다른 화면(관수작업 등)과 동일하게 finish() ──
        View btnHome = view.findViewById(R.id.btnSettingsHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                if (getActivity() != null) getActivity().finish();
            });
        }

        initIrrigationTypeMenu(view);
        initEnvStandard(view);
        initEquipment(view);
        initPasswordChange(view);
        initLogout(view);
    }

    // ── 설치 장비 정보 카드 ─────────────────────────────────────
    private void initEquipment(View view) {
        SwitchMaterial swTank   = view.findViewById(R.id.switchTank);
        SwitchMaterial swSensor = view.findViewById(R.id.switchSensor);
        View           rowSensor= view.findViewById(R.id.rowSensorSwitch);
        EditText       etHp     = view.findViewById(R.id.etPumpHp);
        EditText       etMain   = view.findViewById(R.id.etMainPipe);
        EditText       etOut    = view.findViewById(R.id.etOutPipe);
        View           btnSave  = view.findViewById(R.id.btnEquipSave);

        if (swTank == null || etHp == null) return;

        // 탱크 토글 → 센서 행 활성/비활성
        updateSensorRow(swTank.isChecked(), swSensor, rowSensor);
        swTank.setOnCheckedChangeListener((btn, checked) ->
                updateSensorRow(checked, swSensor, rowSensor));

        // ── 서버에서 설치장비 환경 조회 ──────────────────────────
        String letNo = ZoneStore.getInstance().getFirstLteNo();
        if (letNo != null && !letNo.isEmpty()) {
            com.acasian.iot.network.ApiService api =
                    com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
            api.getMainEnv(new com.acasian.iot.network.ApiService.MainEnvRequest(letNo))
                    .enqueue(new retrofit2.Callback<com.acasian.iot.network.ApiService.MainEnvResponse>() {
                        @Override
                        public void onResponse(
                                retrofit2.Call<com.acasian.iot.network.ApiService.MainEnvResponse> call,
                                retrofit2.Response<com.acasian.iot.network.ApiService.MainEnvResponse> res) {
                            if (!isAdded()) return;
                            com.acasian.iot.network.ApiService.MainEnvResponse body = res.body();
                            if (body != null && body.isSuccess()
                                    && body.data != null && body.data.irrinfo != null) {
                                com.acasian.iot.network.ApiService.MainEnvResponse.IrrInfo info =
                                        body.data.irrinfo;
                                // 서버 값 → UI 반영
                                if (info.spPump    != null) etHp  .setText(info.spPump);
                                if (info.valveDiam != null) etMain.setText(info.valveDiam);
                                if (info.valveCount!= null) etOut .setText(info.valveCount);
                                if (info.fertilYn  != null)
                                    swTank.setChecked("Y".equalsIgnoreCase(info.fertilYn));
                            } else {
                                // 서버 데이터 없으면 로컬 캐시 사용
                                loadEquipmentFromLocal(swTank, swSensor, etHp, etMain, etOut);
                            }
                        }
                        @Override
                        public void onFailure(
                                retrofit2.Call<com.acasian.iot.network.ApiService.MainEnvResponse> call,
                                Throwable t) {
                            if (!isAdded()) return;
                            // 네트워크 오류 시 로컬 캐시 사용
                            loadEquipmentFromLocal(swTank, swSensor, etHp, etMain, etOut);
                        }
                    });
        } else {
            // letNo 없으면 로컬 캐시 사용
            loadEquipmentFromLocal(swTank, swSensor, etHp, etMain, etOut);
        }

        // ── 저장 버튼 ─────────────────────────────────────────────
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String hp   = etHp  .getText().toString().trim();
                String main = etMain.getText().toString().trim();
                String out  = etOut .getText().toString().trim();
                if (hp.isEmpty() || main.isEmpty() || out.isEmpty()) {
                    toast("펌프 마력수·관 직경을 올바르게 입력해 주세요."); return;
                }
                String lteNoSave = ZoneStore.getInstance().getFirstLteNo();
                String farmId    = session.getFarmId();
                String fertilYn  = swTank.isChecked() ? "Y" : "N";

                // 로컬 캐시 저장
                EquipmentConfig.save(requireContext(),
                        swTank.isChecked(), swSensor.isChecked(),
                        safeFloat(hp, 2.0f), safeInt(main, 50), safeInt(out, 25));

                if (lteNoSave == null || lteNoSave.isEmpty() || farmId == null) {
                    toast("설치 장비 정보가 저장되었습니다."); return;
                }

                com.acasian.iot.network.ApiService api =
                        com.acasian.iot.network.ApiClient.getInstance(requireContext()).getService();
                com.acasian.iot.network.ApiService.MainEnvSaveRequest req =
                        new com.acasian.iot.network.ApiService.MainEnvSaveRequest(
                                lteNoSave, farmId, hp, main, out, main, fertilYn);

                // 기존 데이터 있으면 upd, 없으면 add
                boolean hasExisting = EquipmentConfig.isConfigSaved(requireContext());
                retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call =
                        hasExisting ? api.updMainEnv(req) : api.addMainEnv(req);
                call.enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> c,
                            retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> r) {
                        if (!isAdded()) return;
                        toast("설치 장비 정보가 저장되었습니다.");
                    }
                    @Override
                    public void onFailure(
                            retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> c,
                            Throwable t) {
                        if (!isAdded()) return;
                        toast("저장 중 오류가 발생했습니다.");
                    }
                });
            });
        }
    }

    /** 로컬 캐시에서 설치장비 값 UI에 반영 */
    private void loadEquipmentFromLocal(SwitchMaterial swTank, SwitchMaterial swSensor,
                                        EditText etHp, EditText etMain, EditText etOut) {
        if (!EquipmentConfig.isConfigSaved(requireContext())) return;
        swTank .setChecked(EquipmentConfig.isTankInstalled  (requireContext()));
        swSensor.setChecked(EquipmentConfig.isSensorInstalled(requireContext()));
        etHp  .setText(String.valueOf(EquipmentConfig.getPumpHp    (requireContext())));
        etMain.setText(String.valueOf(EquipmentConfig.getMainPipeMm(requireContext())));
        etOut .setText(String.valueOf(EquipmentConfig.getOutPipeMm (requireContext())));
    }

    private float safeFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void updateSensorRow(boolean tankOn, SwitchMaterial swSensor, View rowSensor) {
        if (rowSensor == null) return;
        rowSensor.setAlpha(tankOn ? 1.0f : 0.4f);
        swSensor.setEnabled(tankOn);
        if (!tankOn) swSensor.setChecked(false);
    }

    // ── 관수 유형 관리 → overlay ─────────────────────────────────
    private void initIrrigationTypeMenu(View view) {
        LinearLayout menuIrrigationType = view.findViewById(R.id.menuIrrigationType);
        if (menuIrrigationType != null) {
            menuIrrigationType.setOnClickListener(v -> {
                if (getActivity() instanceof SettingsActivity) {
                    ((SettingsActivity) getActivity()).openOverlayFragment(
                            IrrigationTypeManagerFragment.newInstance(), "itm");
                }
            });
        }
    }

    // ── 환경기준 등록 ─────────────────────────────────────────────
    private void initEnvStandard(View view) {
        menuEnvStandard  = view.findViewById(R.id.menuEnvStandard);
        panelEnvStandard = view.findViewById(R.id.panelEnvStandard);
        icEnvArrow       = view.findViewById(R.id.icEnvArrow);

        etSoilMin = view.findViewById(R.id.etSoilMin);
        etSoilMax = view.findViewById(R.id.etSoilMax);
        etTempMax = view.findViewById(R.id.etTempMax);
        etEcMin   = view.findViewById(R.id.etEcMin);
        etEcMax   = view.findViewById(R.id.etEcMax);
        etPhMin   = view.findViewById(R.id.etPhMin);
        etPhMax   = view.findViewById(R.id.etPhMax);

        // 저장된 값 불러오기
        loadEnvValues();

        if (menuEnvStandard != null) {
            menuEnvStandard.setOnClickListener(v -> {
                envOpen = !envOpen;
                if (panelEnvStandard != null)
                    panelEnvStandard.setVisibility(envOpen ? View.VISIBLE : View.GONE);
                if (icEnvArrow != null)
                    icEnvArrow.setRotation(envOpen ? 90f : 0f);
                // 다른 패널 닫기
                if (envOpen && infoChangeOpen) {
                    infoChangeOpen = false;
                    if (panelInfoChange != null) panelInfoChange.setVisibility(View.GONE);
                    if (icInfoChangeArrow != null) icInfoChangeArrow.setRotation(0f);
                }
            });
        }

        View btnSave = view.findViewById(R.id.btnSaveEnv);
        if (btnSave != null) btnSave.setOnClickListener(v -> saveEnvValues());
    }

    private void loadEnvValues() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_ENV, android.content.Context.MODE_PRIVATE);
        set(etSoilMin, prefs.getString("soil_min", "20"));
        set(etSoilMax, prefs.getString("soil_max", "40"));
        set(etTempMax, prefs.getString("temp_max", "45"));
        set(etEcMin,   prefs.getString("ec_min",   "0.5"));
        set(etEcMax,   prefs.getString("ec_max",   "3.0"));
        set(etPhMin,   prefs.getString("ph_min",   "6.0"));
        set(etPhMax,   prefs.getString("ph_max",   "7.0"));
    }

    private void saveEnvValues() {
        // DEV_MODE/실 연동 모두: 현재 SharedPreferences 로컬 저장
        // TODO_API: 서버팀에 환경기준 저장/조회 API 요청 후 연동 필요 (API 문서에 없음)
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_ENV, android.content.Context.MODE_PRIVATE);
        prefs.edit()
                .putString("soil_min", get(etSoilMin))
                .putString("soil_max", get(etSoilMax))
                .putString("temp_max", get(etTempMax))
                .putString("ec_min",   get(etEcMin))
                .putString("ec_max",   get(etEcMax))
                .putString("ph_min",   get(etPhMin))
                .putString("ph_max",   get(etPhMax))
                .apply();
        toast("환경기준이 저장되었습니다.");
    }

    private void set(EditText et, String val) { if (et != null) et.setText(val); }
    private String get(EditText et) { return et != null ? et.getText().toString().trim() : ""; }

    // ── 비밀번호 변경 ─────────────────────────────────────────────
    private void initPasswordChange(View view) {
        menuInfoChange    = view.findViewById(R.id.menuInfoChange);
        panelInfoChange   = view.findViewById(R.id.panelInfoChange);
        icInfoChangeArrow = view.findViewById(R.id.icInfoChangeArrow);

        containerCurrentPw = view.findViewById(R.id.containerCurrentPw);
        containerNewPw     = view.findViewById(R.id.containerNewPw);
        containerConfirmPw = view.findViewById(R.id.containerConfirmPw);
        etCurrentPw        = view.findViewById(R.id.etCurrentPw);
        etNewPw            = view.findViewById(R.id.etNewPw);
        etConfirmPw        = view.findViewById(R.id.etConfirmPw);
        btnToggleCurrent   = view.findViewById(R.id.btnToggleCurrent);
        btnToggleNew       = view.findViewById(R.id.btnToggleNew);
        btnToggleConfirm   = view.findViewById(R.id.btnToggleConfirm);

        Drawable bgNormal  = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_normal);
        Drawable bgFocused = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_focused);

        if (etCurrentPw != null && containerCurrentPw != null)
            etCurrentPw.setOnFocusChangeListener((v, f) ->
                    containerCurrentPw.setBackground(f ? bgFocused : bgNormal));
        if (etNewPw != null && containerNewPw != null)
            etNewPw.setOnFocusChangeListener((v, f) ->
                    containerNewPw.setBackground(f ? bgFocused : bgNormal));
        if (etConfirmPw != null && containerConfirmPw != null)
            etConfirmPw.setOnFocusChangeListener((v, f) ->
                    containerConfirmPw.setBackground(f ? bgFocused : bgNormal));

        setupToggle(btnToggleCurrent, etCurrentPw, () -> { visibleCurrent = !visibleCurrent; return visibleCurrent; });
        setupToggle(btnToggleNew,     etNewPw,     () -> { visibleNew     = !visibleNew;     return visibleNew;     });
        setupToggle(btnToggleConfirm, etConfirmPw, () -> { visibleConfirm = !visibleConfirm; return visibleConfirm; });

        if (menuInfoChange != null) {
            menuInfoChange.setOnClickListener(v -> {
                infoChangeOpen = !infoChangeOpen;
                if (panelInfoChange   != null) panelInfoChange.setVisibility(infoChangeOpen ? View.VISIBLE : View.GONE);
                if (icInfoChangeArrow != null) icInfoChangeArrow.setRotation(infoChangeOpen ? 180f : 0f);
                // 다른 패널 닫기
                if (infoChangeOpen && envOpen) {
                    envOpen = false;
                    if (panelEnvStandard != null) panelEnvStandard.setVisibility(View.GONE);
                    if (icEnvArrow != null) icEnvArrow.setRotation(0f);
                }
            });
        }

        View btnChange = view.findViewById(R.id.btnChangePassword);
        if (btnChange != null) btnChange.setOnClickListener(v -> attemptChangePassword());
    }

    private interface BoolSupplier { boolean get(); }

    private void setupToggle(ImageView icon, EditText et, BoolSupplier toggle) {
        if (icon == null || et == null) return;
        icon.setOnClickListener(v -> {
            boolean visible = toggle.get();
            et.setTransformationMethod(visible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            icon.setImageResource(visible ? R.drawable.ic_eye_on : R.drawable.ic_eye_off);
            et.setSelection(et.getText().length());
        });
    }

    private void attemptChangePassword() {
        String current = etCurrentPw != null ? etCurrentPw.getText().toString() : "";
        String newPw   = etNewPw     != null ? etNewPw.getText().toString()     : "";
        String confirm = etConfirmPw != null ? etConfirmPw.getText().toString() : "";

        Drawable bgNormal = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_normal);
        Drawable bgError  = ContextCompat.getDrawable(requireContext(), R.drawable.bg_input_error);

        if (containerCurrentPw != null) containerCurrentPw.setBackground(bgNormal);
        if (containerNewPw     != null) containerNewPw.setBackground(bgNormal);
        if (containerConfirmPw != null) containerConfirmPw.setBackground(bgNormal);

        if (current.isEmpty()) {
            if (containerCurrentPw != null) containerCurrentPw.setBackground(bgError);
            toast("현재 비밀번호를 입력해 주세요."); return;
        }
        if (newPw.length() < 4) {
            if (containerNewPw != null) containerNewPw.setBackground(bgError);
            toast("비밀번호는 4자리 이상이어야 합니다."); return;
        }
        if (!newPw.equals(confirm)) {
            if (containerConfirmPw != null) containerConfirmPw.setBackground(bgError);
            toast("새 비밀번호가 일치하지 않습니다."); return;
        }

        if (AppConfig.getInstance().isDevMode()) {
            // DEV_MODE: 실제 API 호출 없이 성공 처리
            toast("비밀번호가 변경되었습니다. (DEV_MODE)");
        } else {
            /* TODO_API: 실제 서버 연동 시 아래 주석 해제
            String userId = session.getPhoneNumber();
            com.acasian.iot.network.ApiService api =
                    com.acasian.iot.network.ApiClient.getInstance().create(
                            com.acasian.iot.network.ApiService.class);
            api.changePw(new com.acasian.iot.network.ApiService.ChangePwRequest(
                    userId, currentPw, newPw))
               .enqueue(new retrofit2.Callback<com.acasian.iot.model.response.ApiResponse<Void>>() {
                   @Override public void onResponse(
                           retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                           retrofit2.Response<com.acasian.iot.model.response.ApiResponse<Void>> res) {
                       if (res.isSuccessful() && res.body() != null) {
                           requireActivity().runOnUiThread(() ->
                               toast("비밀번호가 변경되었습니다."));
                       } else {
                           requireActivity().runOnUiThread(() ->
                               toast("비밀번호 변경에 실패했습니다."));
                       }
                   }
                   @Override public void onFailure(
                           retrofit2.Call<com.acasian.iot.model.response.ApiResponse<Void>> call,
                           Throwable t) {
                       requireActivity().runOnUiThread(() ->
                           toast("네트워크 오류: " + t.getMessage()));
                   }
               });
            */
            // 실 연동 전: 로컬 성공 처리
            toast("비밀번호가 변경되었습니다.");
        }
        if (etCurrentPw != null) etCurrentPw.setText("");
        if (etNewPw     != null) etNewPw.setText("");
        if (etConfirmPw != null) etConfirmPw.setText("");
    }

    // ── 로그아웃 ─────────────────────────────────────────────────
    private void initLogout(View view) {
        View btnLogout = view.findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> showLogoutDialog());
        }
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;
        float dp = requireContext().getResources().getDisplayMetrics().density;
        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        int p = Math.round(20 * dp);
        root.setPadding(p, p, p, Math.round(8 * dp));

        android.widget.TextView tvTitle = new android.widget.TextView(requireContext());
        tvTitle.setText("로그아웃");
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
        tvMsg.setText("로그아웃 하시겠습니까?");
        tvMsg.setTextSize(17f);
        tvMsg.setTextColor(0xFF555555);
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

        android.widget.Button btnOut = new android.widget.Button(requireContext());
        btnOut.setText("로그아웃");
        btnOut.setTextSize(17f);
        btnOut.setTypeface(null, android.graphics.Typeface.BOLD);
        btnOut.setTextColor(0xFFC62828);
        btnOut.setBackground(null);
        btnOut.setPadding(Math.round(12*dp),0,Math.round(4*dp),0);
        btnOut.setOnClickListener(v -> {
            if(ref[0]!=null) ref[0].dismiss();
            doLogout();
        });
        btnRow.addView(btnCancel);
        btnRow.addView(btnOut);
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

    private void doLogout() {
        com.acasian.iot.model.IrrigationProfileManager.getInstance(requireContext())
                .saveAll(new java.util.ArrayList<>());
        ZoneStore.getInstance().clear();
        session.logout();
        android.content.Intent intent = new android.content.Intent(
                requireContext(), LoginActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null)
            getActivity().overridePendingTransition(
                    android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
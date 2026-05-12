package com.acasian.iot;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * 설정 단독 Activity.
 * HomeDashboardActivity 설정 버튼 → 이 Activity → SettingsFragment
 * IrrigationTypeManagerFragment 도 overlay 로 표시.
 */
public class SettingsActivity extends AppCompatActivity {

    private View overlayContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.forest_dark));
        setContentView(R.layout.activity_settings_wrapper);

        overlayContainer = findViewById(R.id.settingsOverlayContainer);

        // DEV_MODE: ZoneStore 비어있으면 더미 자동 주입
        if (AppConfig.getInstance().isDevMode()
                && ZoneStore.getInstance().isEmpty()) {
            AppConfig.injectDemoZones();
            // 개발 모드 더미 주입 완료
            DemoData.applyProfiles(this);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settingsFragmentContainer,
                            SettingsFragment.newInstance(), "settings")
                    .commit();
        }
    }

    /** SettingsFragment → IrrigationTypeManagerFragment 오버레이 */
    public void openOverlayFragment(Fragment frag, String tag) {
        if (overlayContainer == null) return;
        overlayContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settingsOverlayContainer, frag, tag)
                .addToBackStack("overlay")
                .commit();

        getSupportFragmentManager().addOnBackStackChangedListener(
                new FragmentManager.OnBackStackChangedListener() {
                    @Override public void onBackStackChanged() {
                        if (getSupportFragmentManager().findFragmentByTag(tag) == null) {
                            if (overlayContainer != null)
                                overlayContainer.setVisibility(View.GONE);
                            getSupportFragmentManager().removeOnBackStackChangedListener(this);
                        }
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }
}

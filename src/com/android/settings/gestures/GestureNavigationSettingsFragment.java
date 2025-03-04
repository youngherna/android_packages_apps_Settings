/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.gestures;

import static android.os.UserHandle.USER_CURRENT;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.LabeledSeekBarPreference;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.search.SearchIndexable;

/**
 * A fragment to include all the settings related to Gesture Navigation mode.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class GestureNavigationSettingsFragment extends DashboardFragment {

    public static final String TAG = "GestureNavigationSettingsFragment";

    public static final String GESTURE_NAVIGATION_SETTINGS =
            "com.android.settings.GESTURE_NAVIGATION_SETTINGS";
    public static final String IMMERSIVE_NAVIGATION_SETTINGS =
            "immersive_navigation";

    private static final String LEFT_EDGE_SEEKBAR_KEY = "gesture_left_back_sensitivity";
    private static final String RIGHT_EDGE_SEEKBAR_KEY = "gesture_right_back_sensitivity";
    private static final String IMMERSIVE_NAV_KEY = "immersive_navigation";
    private static final String NAV_MODE_IMMERSIVE_OVERLAY =
            "mx.xperience.overlay.systemui.immnav.gestural";

    private IOverlayManager mOverlayService;

    private static final String GESTURE_NAVBAR_LENGTH_KEY = "gesture_navbar_length_preference";

    private WindowManager mWindowManager;
    private BackGestureIndicatorView mIndicatorView;

    private float[] mBackGestureInsetScales;
    private float mDefaultBackGestureInset;

    public GestureNavigationSettingsFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIndicatorView = new BackGestureIndicatorView(getActivity());
        mWindowManager = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        mOverlayService = IOverlayManager.Stub
                .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        final Resources res = getActivity().getResources();
        mDefaultBackGestureInset = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_backGestureInset);
        mBackGestureInsetScales = getFloatArray(res.obtainTypedArray(
                com.android.internal.R.array.config_backGestureInsetScales));

        initSeekBarPreference(LEFT_EDGE_SEEKBAR_KEY);
        initSeekBarPreference(RIGHT_EDGE_SEEKBAR_KEY);
        initImmersiveSwitchPreference();
        initGestureBarLengthPreference();
    }

    @Override
    public void onResume() {
        super.onResume();

        mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                getActivity().getWindow().getAttributes()));
    }

    @Override
    public void onPause() {
        super.onPause();

        mWindowManager.removeView(mIndicatorView);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.gesture_navigation_settings;
    }

    @Override
    public int getHelpResource() {
        // TODO(b/146001201): Replace with gesture navigation help page when ready.
        return R.string.help_uri_default;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURE_NAV_BACK_SENSITIVITY_DLG;
    }

    private void initSeekBarPreference(final String key) {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(key);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);

        final String settingsKey = key == LEFT_EDGE_SEEKBAR_KEY
                ? Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT
                : Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT;
        final float initScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), settingsKey, 1.0f);

        // Find the closest value to initScale
        float minDistance = Float.MAX_VALUE;
        int minDistanceIndex = -1;
        for (int i = 0; i < mBackGestureInsetScales.length; i++) {
            float d = Math.abs(mBackGestureInsetScales[i] - initScale);
            if (d < minDistance) {
                minDistance = d;
                minDistanceIndex = i;
            }
        }
        pref.setProgress(minDistanceIndex);

        pref.setOnPreferenceChangeListener((p, v) -> {
            final int width = (int) (mDefaultBackGestureInset * mBackGestureInsetScales[(int) v]);
            mIndicatorView.setIndicatorWidth(width, key == LEFT_EDGE_SEEKBAR_KEY);
            return true;
        });

        pref.setOnPreferenceChangeStopListener((p, v) -> {
            mIndicatorView.setIndicatorWidth(0, key == LEFT_EDGE_SEEKBAR_KEY);
            final float scale = mBackGestureInsetScales[(int) v];
            Settings.Secure.putFloat(getContext().getContentResolver(), settingsKey, scale);
            return true;
        });
    }

    private void initImmersiveSwitchPreference() {
        SwitchPreference prefImmersiveNav = getPreferenceScreen().findPreference(IMMERSIVE_NAV_KEY);
        prefImmersiveNav.setOnPreferenceChangeListener((preference, o) -> {
            final boolean isEnabled = (Boolean) o;
            final LabeledSeekBarPreference prefLength =
                    getPreferenceScreen().findPreference(GESTURE_NAVBAR_LENGTH_KEY);
            if (isEnabled) {
                try {
                    mOverlayService.setEnabledExclusiveInCategory(NAV_MODE_IMMERSIVE_OVERLAY,
                            USER_CURRENT);
                } catch (RemoteException re) {
                    throw re.rethrowFromSystemServer();
                }
            } else {
                try {
                    mOverlayService.setEnabledExclusiveInCategory(NAV_BAR_MODE_GESTURAL_OVERLAY,
                            USER_CURRENT);
                    mOverlayService.setEnabled(NAV_MODE_IMMERSIVE_OVERLAY, false, USER_CURRENT);
                } catch (RemoteException re) {
                    throw re.rethrowFromSystemServer();
                }
            }
            Settings.Secure.putInt(getContext().getContentResolver(),
                    IMMERSIVE_NAVIGATION_SETTINGS, isEnabled ? 1 : 0);
            prefLength.setEnabled(!isEnabled);
            return true;
        });
        prefImmersiveNav.setChecked(Settings.Secure.getInt(getContext().getContentResolver(),
                IMMERSIVE_NAVIGATION_SETTINGS, 0) != 0);
    }

    private void initGestureBarLengthPreference() {
        final LabeledSeekBarPreference pref =
                getPreferenceScreen().findPreference(GESTURE_NAVBAR_LENGTH_KEY);
        pref.setContinuousUpdates(true);
        pref.setProgress(Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.GESTURE_NAVBAR_LENGTH, 0));
        pref.setOnPreferenceChangeListener((p, v) ->
            Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.GESTURE_NAVBAR_LENGTH, (Integer) v));
        pref.setEnabled(Settings.Secure.getInt(getContext().getContentResolver(),
            IMMERSIVE_NAVIGATION_SETTINGS, 0) == 0);
    }

    private static float[] getFloatArray(TypedArray array) {
        int length = array.length();
        float[] floatArray = new float[length];
        for (int i = 0; i < length; i++) {
            floatArray[i] = array.getFloat(i, 1.0f);
        }
        array.recycle();
        return floatArray;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.gesture_navigation_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return SystemNavigationPreferenceController.isGestureAvailable(context);
                }
            };

}

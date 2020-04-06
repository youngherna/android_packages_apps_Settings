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

package com.android.settings.media;

import static android.media.AudioManager.STREAM_DEVICES_CHANGED_ACTION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.settings.bluetooth.Utils;
import com.android.settings.slices.SliceBackgroundWorker;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.LocalMediaManager;
import com.android.settingslib.media.MediaDevice;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listener for background change from {@code BluetoothCallback} to update media output indicator.
 */
public class MediaOutputIndicatorWorker extends SliceBackgroundWorker implements BluetoothCallback,
        LocalMediaManager.DeviceCallback {

    private static final String TAG = "MediaOutputIndWorker";

    private final DevicesChangedBroadcastReceiver mReceiver;
    private final Context mContext;
    private final Collection<MediaDevice> mMediaDevices = new CopyOnWriteArrayList<>();

    private LocalBluetoothManager mLocalBluetoothManager;

    @VisibleForTesting
    LocalMediaManager mLocalMediaManager;

    public MediaOutputIndicatorWorker(Context context, Uri uri) {
        super(context, uri);
        mReceiver = new DevicesChangedBroadcastReceiver();
        mContext = context;
    }

    @Override
    protected void onSlicePinned() {
        mMediaDevices.clear();
        mLocalBluetoothManager = Utils.getLocalBtManager(getContext());
        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        final IntentFilter intentFilter = new IntentFilter(STREAM_DEVICES_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, intentFilter);
        mLocalBluetoothManager.getEventManager().registerCallback(this);

        if (mLocalMediaManager == null) {
            final MediaController controller = getActiveLocalMediaController();
            String packageName = null;
            if (controller != null) {
                packageName = controller.getPackageName();
            }
            mLocalMediaManager = new LocalMediaManager(mContext, packageName, null);
        }

        mLocalMediaManager.registerCallback(this);
        mLocalMediaManager.startScan();
    }

    @Override
    protected void onSliceUnpinned() {
        mLocalMediaManager.unregisterCallback(this);
        mLocalMediaManager.stopScan();

        if (mLocalBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            return;
        }
        mLocalBluetoothManager.getEventManager().unregisterCallback(this);
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void close() {
        mLocalBluetoothManager = null;
        mLocalMediaManager = null;
    }

    @Override
    public void onAudioModeChanged() {
        notifySliceChange();
    }

    @Nullable
    MediaController getActiveLocalMediaController() {
        final MediaSessionManager mMediaSessionManager = mContext.getSystemService(
                MediaSessionManager.class);

        for (MediaController controller : mMediaSessionManager.getActiveSessions(null)) {
            final MediaController.PlaybackInfo pi = controller.getPlaybackInfo();
            if (pi == null) {
                return null;
            }
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState == null) {
                return null;
            }
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
                    && playbackState.getState() == PlaybackState.STATE_PLAYING) {
                return controller;
            }
        }
        return null;
    }

    @Override
    public void onDeviceListUpdate(List<MediaDevice> devices) {
        buildMediaDevices(devices);
        notifySliceChange();
    }

    private void buildMediaDevices(List<MediaDevice> devices) {
        mMediaDevices.clear();
        mMediaDevices.addAll(devices);
    }

    @Override
    public void onSelectedDeviceStateChanged(MediaDevice device, int state) {
        notifySliceChange();
    }

    @Override
    public void onDeviceAttributesChanged() {
        notifySliceChange();
    }

    Collection<MediaDevice> getMediaDevices() {
        return mMediaDevices;
    }

    MediaDevice getCurrentConnectedMediaDevice() {
        return mLocalMediaManager.getCurrentConnectedDevice();
    }

    private class DevicesChangedBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TextUtils.equals(AudioManager.STREAM_DEVICES_CHANGED_ACTION, action)) {
                notifySliceChange();
            }
        }
    }
}

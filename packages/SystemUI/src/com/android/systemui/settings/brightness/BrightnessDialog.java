/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.settings.brightness;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.provider.Settings;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import javax.inject.Inject;

/** A dialog that provides controls for adjusting the screen brightness. */
public class BrightnessDialog extends Activity implements Tunable {

    private BrightnessController mBrightnessController;
    private final BrightnessSliderController.Factory mToggleSliderFactory;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final Handler mBackgroundHandler;

    private ImageView mAutoBrightnessIcon;

    @Inject
    public BrightnessDialog(
            BroadcastDispatcher broadcastDispatcher,
            BrightnessSliderController.Factory factory,
            @Background Handler bgHandler) {
        mBroadcastDispatcher = broadcastDispatcher;
        mToggleSliderFactory = factory;
        mBackgroundHandler = bgHandler;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();

        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        // Calling this creates the decor View, so setLayout takes proper effect
        // (see Dialog#onWindowAttributesChanged)
        window.getDecorView();
        window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        setContentView(R.layout.brightness_mirror_container);
        FrameLayout frame = findViewById(R.id.brightness_mirror_container);
        // The brightness mirror container is INVISIBLE by default.
        frame.setVisibility(View.VISIBLE);

        BrightnessSliderController controller = mToggleSliderFactory.create(this, frame);
        controller.init();
        frame.addView(controller.getRootView(), MATCH_PARENT, WRAP_CONTENT);

        mAutoBrightnessIcon = controller.getIconView();
        mBrightnessController = new BrightnessController(this, mAutoBrightnessIcon,
                controller, mBroadcastDispatcher, mBackgroundHandler);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBrightnessController.registerCallbacks();
        MetricsLogger.visible(this, MetricsEvent.BRIGHTNESS_DIALOG);
        Dependency.get(TunerService.class).addTunable(this, Settings.Secure.QS_SHOW_AUTO_BRIGHTNESS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MetricsLogger.hidden(this, MetricsEvent.BRIGHTNESS_DIALOG);
        mBrightnessController.unregisterCallbacks();
        Dependency.get(TunerService.class).removeTunable(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            finish();
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // If the BrightnessDialog loses focus, dismiss it.
        if (!hasFocus) {
            finish();
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case Settings.Secure.QS_SHOW_AUTO_BRIGHTNESS:
                boolean show =
                        TunerService.parseIntegerSwitch(newValue, true);
                if (mAutoBrightnessIcon != null) {
                    mAutoBrightnessIcon.setVisibility(
                            show ? View.VISIBLE : View.GONE);
                }
                break;
            default:
                break;
         }
    }
}

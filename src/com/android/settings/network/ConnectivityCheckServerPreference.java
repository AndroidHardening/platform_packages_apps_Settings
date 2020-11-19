/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.settings.network;

import static android.net.ConnectivityManager.CAPTIVE_PORTAL_MODE_GOOGLE;
import static android.net.ConnectivityManager.CAPTIVE_PORTAL_MODE_GRAPHENE;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import android.app.settings.SettingsEnums;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.NetworkUtils;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.utils.AnnotationSpan;
import com.android.settingslib.CustomDialogPreferenceCompat;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;

import java.util.HashMap;
import java.util.Map;

/**
 * Dialog to set the Private DNS
 */
public class ConnectivityCheckServerPreference extends CustomDialogPreferenceCompat implements
        DialogInterface.OnClickListener, RadioGroup.OnCheckedChangeListener, TextWatcher {

    private static final String TAG = "ConnectivityCheckServerPrefs";
    private static final Map<String, Integer> CONNECTIVITY_CHECK_MAP;

    static {
        CONNECTIVITY_CHECK_MAP = new HashMap<>();
        CONNECTIVITY_CHECK_MAP.put(CAPTIVE_PORTAL_MODE_GOOGLE, R.id.select_connectivity_check_google);
        CONNECTIVITY_CHECK_MAP.put(CAPTIVE_PORTAL_MODE_GRAPHENE, R.id.select_connectivity_check_graphene);
    }

    static final String MODE_KEY = Settings.Global.CAPTIVE_PORTAL_HTTPS_URL;

    public static String getModeFromSettings(ContentResolver cr) {
        String mode = Settings.Global.getString(cr, MODE_KEY);
        if (!CONNECTIVITY_CHECK_MAP.containsKey(mode)) {
            mode = Settings.Global.getString(cr, Settings.Global.PRIVATE_DNS_DEFAULT_MODE);
        }
        return CONNECTIVITY_CHECK_MAP.containsKey(mode) ? mode : PRIVATE_DNS_DEFAULT_MODE_FALLBACK;
    }

    public static String getHostnameFromSettings(ContentResolver cr) {
        return Settings.Global.getString(cr, HOSTNAME_KEY);
    }

    @VisibleForTesting
    EditText mEditText;
    @VisibleForTesting
    RadioGroup mRadioGroup;
    @VisibleForTesting
    String mMode;

    public PrivateDnsModeDialogPreference(Context context) {
        super(context);
        initialize();
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    private final AnnotationSpan.LinkInfo mUrlLinkInfo = new AnnotationSpan.LinkInfo(
            ANNOTATION_URL, (widget) -> {
        final Context context = widget.getContext();
        final Intent intent = HelpUtils.getHelpIntent(context,
                context.getString(R.string.help_uri_private_dns),
                context.getClass().getName());
        if (intent != null) {
            try {
                widget.startActivityForResult(intent, 0);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Activity was not found for intent, " + intent.toString());
            }
        }
    });

    @Override
    protected void onBindDialogView(View view) {
        final Context context = getContext();
        final ContentResolver contentResolver = context.getContentResolver();

        mMode = getModeFromSettings(context.getContentResolver());

        mEditText = view.findViewById(R.id.private_dns_mode_provider_hostname);
        mEditText.addTextChangedListener(this);
        mEditText.setText(getHostnameFromSettings(contentResolver));

        mRadioGroup = view.findViewById(R.id.private_dns_radio_group);
        mRadioGroup.setOnCheckedChangeListener(this);
        mRadioGroup.check(CONNECTIVITY_CHECK_MAP.getOrDefault(mMode, R.id.private_dns_mode_opportunistic));

        // Initial radio button text
        final RadioButton offRadioButton = view.findViewById(R.id.private_dns_mode_off);
        offRadioButton.setText(R.string.private_dns_mode_off);
        final RadioButton opportunisticRadioButton =
                view.findViewById(R.id.private_dns_mode_opportunistic);
        opportunisticRadioButton.setText(R.string.private_dns_mode_opportunistic);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            final Context context = getContext();
            if (mMode.equals(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME)) {
                // Only clickable if hostname is valid, so we could save it safely
                Settings.Global.putString(context.getContentResolver(), HOSTNAME_KEY,
                        mEditText.getText().toString());
            }

            FeatureFactory.getFactory(context).getMetricsFeatureProvider().action(context,
                    SettingsEnums.ACTION_PRIVATE_DNS_MODE, mMode);
            Settings.Global.putString(context.getContentResolver(), MODE_KEY, mMode);
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.private_dns_mode_off) {
            mMode = PRIVATE_DNS_MODE_OFF;
        } else if (checkedId == R.id.private_dns_mode_opportunistic) {
            mMode = PRIVATE_DNS_MODE_OPPORTUNISTIC;
        } else if (checkedId == R.id.private_dns_mode_provider) {
            mMode = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {}

    private Button getSaveButton() {
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return null;
        }
        return dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    }
}

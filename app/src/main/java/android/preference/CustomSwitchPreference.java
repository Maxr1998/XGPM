/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.preference;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class CustomSwitchPreference extends TwoStatePreference {
    private final Listener mListener = new Listener();

    // Switch text for on and off states
    private CharSequence mSwitchOn;
    private CharSequence mSwitchOff;

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context      The Context that will style this preference
     * @param attrs        Style attributes that differ from the default
     * @param defStyleAttr An attribute in the current theme that contains a
     *                     reference to a style resource that supplies default values for
     *                     the view. Can be 0 to not look for defaults.
     */
    public CustomSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        int[] switchPreference = {0};
        try {
            Field field = Class.forName("com.android.internal.R$styleable").getDeclaredField("SwitchPreference");
            field.setAccessible(true);
            switchPreference = (int[]) field.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        TypedArray a = context.obtainStyledAttributes(attrs, switchPreference, defStyleAttr, 0);
        setSummaryOn(a.getString(getSystemId("styleable", "SwitchPreference_summaryOn")));
        setSummaryOff(a.getString(getSystemId("styleable", "SwitchPreference_summaryOff")));
        setSwitchTextOn(a.getString(
                getSystemId("styleable", "SwitchPreference_switchTextOn")));
        setSwitchTextOff(a.getString(
                getSystemId("styleable", "SwitchPreference_switchTextOff")));
        setDisableDependentsState(a.getBoolean(
                getSystemId("styleable", "SwitchPreference_disableDependentsState"), false));
        a.recycle();
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context The Context that will style this preference
     * @param attrs   Style attributes that differ from the default
     */
    public CustomSwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchPreferenceStyle);
    }

    /**
     * Construct a new SwitchPreference with default style options.
     *
     * @param context The Context that will style this preference
     */
    public CustomSwitchPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        View checkableView = view.findViewById(getSystemId("id", "switchWidget"));
        if (checkableView != null && checkableView instanceof Checkable) {
            if (checkableView instanceof Switch) {
                final Switch switchView = (Switch) checkableView;
                switchView.setOnCheckedChangeListener(null);
            }

            try {
                Field mChecked = getClass().getSuperclass().getDeclaredField("mChecked");
                mChecked.setAccessible(true);
                ((Checkable) checkableView).setChecked(mChecked.getBoolean(this));
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (checkableView instanceof Switch) {
                final Switch switchView = (Switch) checkableView;
                switchView.setTextOn(mSwitchOn);
                switchView.setTextOff(mSwitchOff);
                switchView.setOnCheckedChangeListener(mListener);
            }
        }

        try {
            Method syncSummaryView = getClass().getSuperclass().getDeclaredMethod("syncSummaryView", View.class);
            syncSummaryView.setAccessible(true);
            syncSummaryView.invoke(this, view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Set the text displayed on the switch widget in the on state.
     * This should be a very short string; one word if possible.
     *
     * @param onText Text to display in the on state
     */
    public void setSwitchTextOn(CharSequence onText) {
        mSwitchOn = onText;
        notifyChanged();
    }

    /**
     * Set the text displayed on the switch widget in the off state.
     * This should be a very short string; one word if possible.
     *
     * @param offText Text to display in the off state
     */
    public void setSwitchTextOff(CharSequence offText) {
        mSwitchOff = offText;
        notifyChanged();
    }

    /**
     * @return The text that will be displayed on the switch widget in the on state
     */
    public CharSequence getSwitchTextOn() {
        return mSwitchOn;
    }

    /**
     * Set the text displayed on the switch widget in the on state.
     * This should be a very short string; one word if possible.
     *
     * @param resId The text as a string resource ID
     */
    public void setSwitchTextOn(@StringRes int resId) {
        setSwitchTextOn(getContext().getString(resId));
    }

    /**
     * @return The text that will be displayed on the switch widget in the off state
     */
    public CharSequence getSwitchTextOff() {
        return mSwitchOff;
    }

    /**
     * Set the text displayed on the switch widget in the off state.
     * This should be a very short string; one word if possible.
     *
     * @param resId The text as a string resource ID
     */
    public void setSwitchTextOff(@StringRes int resId) {
        setSwitchTextOff(getContext().getString(resId));
    }

    private int getSystemId(String type, String value) {
        return Resources.getSystem().getIdentifier(value, type, "android");
    }

    private class Listener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!callChangeListener(isChecked)) {
                // Listener didn't like it, change it back.
                // CompoundButton will make sure we don't recurse.
                buttonView.setChecked(!isChecked);
                return;
            }

            CustomSwitchPreference.this.setChecked(isChecked);
        }
    }
}
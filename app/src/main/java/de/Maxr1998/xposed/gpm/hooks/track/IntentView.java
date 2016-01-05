package de.Maxr1998.xposed.gpm.hooks.track;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ListView;

import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.CURRENT_PLAYING_POSITION_EXTRA;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.REPLY_INTENT_EXTRA;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.TRACK_INFO_EXTRA;

public class IntentView extends FrameLayout {

    private ListView list;

    public IntentView(Context context) {
        super(context);
    }

    public IntentView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IntentView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public IntentView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setChildList(ListView l) {
        list = l;
    }

    @SuppressWarnings("unused")
    public void resolveIntent(Intent intent) {
        if (list != null) {
            Bundle extras = intent.getExtras();
            extras.setClassLoader(TrackItem.class.getClassLoader());
            list.setAdapter(new CustomTrackAdapter(getContext(),
                    extras.getInt(CURRENT_PLAYING_POSITION_EXTRA),
                    extras.<TrackItem>getParcelableArrayList(TRACK_INFO_EXTRA),
                    (PendingIntent) extras.getParcelable(REPLY_INTENT_EXTRA)));
        }
    }
}

package de.Maxr1998.xposed.gpm.hooks.track;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class TrackItem implements Parcelable {
    public static Parcelable.Creator<TrackItem> CREATOR = new Creator<TrackItem>() {
        @Override
        public TrackItem createFromParcel(Parcel source) {
            return new TrackItem()
                    .setArt((Bitmap) source.readParcelable(Bitmap.class.getClassLoader()))
                    .setTitle(source.readString())
                    .setArtist(source.readString())
                    .setDuration(source.readString());
        }

        @Override
        public TrackItem[] newArray(int size) {
            return new TrackItem[0];
        }
    };

    public long albumId = 0;

    private Bitmap art = null;
    private String title = "";
    private String artist = "";
    private String duration = "";

    public TrackItem setArt(Bitmap b) {
        art = b;
        return this;
    }

    public TrackItem setTitle(String t) {
        title = t;
        return this;
    }

    public TrackItem setArtist(String a) {
        artist = a;
        return this;
    }

    public TrackItem setDuration(String l) {
        duration = l;
        return this;
    }

    public Bitmap getArt() {
        return art;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getDuration() {
        return duration;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(art, 0);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(duration);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
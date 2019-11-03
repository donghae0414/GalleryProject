package com.example.galleryproject.Model;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

public class UnitImageGroup extends ImageGroup {
    public UnitImageGroup() {
        super();
    }

    public UnitImageGroup(List<Image> images) {
        super(images);
    }

    public UnitImageGroup(List<Image> images, String memo) {
        super(images, memo);
    }


    /**
     * Implements Parcelable
     */
    public UnitImageGroup(Parcel in) {
        super();

        List<Image> images = new ArrayList<>();
        in.readList(images, Image.class.getClassLoader());
        this.setImages(images);

        String memo = in.readString();
        this.setMemo(memo);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeList(images);
        parcel.writeString(memo);
    }

    public static final Creator<UnitImageGroup> CREATOR = new Creator<UnitImageGroup>() {
        @Override
        public UnitImageGroup createFromParcel(Parcel in) {
            return new UnitImageGroup(in);
        }

        @Override
        public UnitImageGroup[] newArray(int size) {
            return new UnitImageGroup[size];
        }
    };
}

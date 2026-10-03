package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Hidden API 编译桩：android.hardware.display.DisplayTopology。
 *
 * 真实类仅存在于设备 bootclasspath（Android 16+ 为 IDisplayManagerCallback
 * 新增 onTopologyChanged 引入）。本桩仅用于编译期让 AIDL 生成的接口可被实现，
 * 不会打进 APK（hiddenapi:stub 为 compileOnly），运行时解析到系统真实类。
 */
public class DisplayTopology implements Parcelable {
    public static final Parcelable.Creator<DisplayTopology> CREATOR =
            new Parcelable.Creator<DisplayTopology>() {
                @Override
                public DisplayTopology createFromParcel(Parcel source) {
                    return new DisplayTopology();
                }

                @Override
                public DisplayTopology[] newArray(int size) {
                    return new DisplayTopology[size];
                }
            };

    public void readFromParcel(Parcel source) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }
}

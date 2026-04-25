package android.app;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;

public class ActivityTaskManager {

    public static class RootTaskInfo extends TaskInfo implements Parcelable {
        public Rect bounds;
        public int[] childTaskIds;
        public String[] childTaskNames;
        public Rect[] childTaskBounds;
        public int[] childTaskUserIds;
        public boolean visible;
        public int position;

        protected RootTaskInfo(Parcel in) {
        }

        public static final Creator<RootTaskInfo> CREATOR = new Creator<RootTaskInfo>() {
            @Override
            public RootTaskInfo createFromParcel(Parcel in) {
                return new RootTaskInfo(in);
            }

            @Override
            public RootTaskInfo[] newArray(int size) {
                return new RootTaskInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
    }
}

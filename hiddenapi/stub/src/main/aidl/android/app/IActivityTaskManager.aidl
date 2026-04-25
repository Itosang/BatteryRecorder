package android.app;

import android.app.ITaskStackListener;
import android.app.ActivityTaskManager;
import android.graphics.Rect;

interface IActivityTaskManager {
    void registerTaskStackListener(in ITaskStackListener listener);
    void unregisterTaskStackListener(in ITaskStackListener listener);
    ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo();
    Rect getTaskBounds(int taskId);
}
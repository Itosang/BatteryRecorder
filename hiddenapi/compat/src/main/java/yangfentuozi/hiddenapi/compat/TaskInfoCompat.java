package yangfentuozi.hiddenapi.compat;

import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.TaskInfo;
import android.graphics.Rect;
import android.os.RemoteException;

import androidx.annotation.Nullable;

public final class TaskInfoCompat {
    private TaskInfoCompat() {
    }

    /**
     * 读取任务当前窗口边界；任务为空时返回空。
     *
     * @param activityTaskManager ActivityTaskManager Binder 接口，用于按任务 ID 读取普通任务边界。
     * @param taskInfo 任务信息。
     * @param focusedRootTaskInfo 当前聚焦 RootTask 信息；仅在 RootTaskInfo 缺少子任务边界时作为后备。
     * @return 成功返回窗口当前边界，任务为空时返回 `null`。
     */
    @Nullable
    public static Rect getBoundsOrNull(
            IActivityTaskManager activityTaskManager,
            @Nullable TaskInfo taskInfo,
            @Nullable ActivityTaskManager.RootTaskInfo focusedRootTaskInfo
    ) throws RemoteException {
        if (taskInfo == null) {
            return null;
        }
        if (taskInfo instanceof ActivityTaskManager.RootTaskInfo) {
            return getTopChildBoundsOrRootBounds(
                    (ActivityTaskManager.RootTaskInfo) taskInfo,
                    focusedRootTaskInfo
            );
        }
        return activityTaskManager.getTaskBounds(taskInfo.taskId);
    }

    /**
     * 读取任务可达到的最大窗口边界；任务为空时返回空。
     *
     * @param focusedRootTaskInfo 当前聚焦 RootTask 信息，用于提供最大窗口边界。
     * @return 成功返回窗口最大边界，任务为空时返回 `null`。
     */
    @Nullable
    public static Rect getMaxBoundsOrNull(@Nullable ActivityTaskManager.RootTaskInfo focusedRootTaskInfo) {
        if (focusedRootTaskInfo == null) {
            return null;
        }
        return focusedRootTaskInfo.bounds;
    }

    /**
     * 从 RootTaskInfo 的顶部子任务读取当前任务边界。
     *
     * @param rootTaskInfo 当前任务对应的 RootTask 信息。
     * @param fallbackRootTaskInfo 后备 RootTask 信息，仅在当前 RootTask 缺少边界时使用。
     * @return 优先返回顶部子任务边界；缺失时返回 RootTask 自身边界。
     */
    @Nullable
    private static Rect getTopChildBoundsOrRootBounds(
            ActivityTaskManager.RootTaskInfo rootTaskInfo,
            @Nullable ActivityTaskManager.RootTaskInfo fallbackRootTaskInfo
    ) {
        final Rect[] childTaskBounds = rootTaskInfo.childTaskBounds;
        if (childTaskBounds != null && childTaskBounds.length > 0) {
            return childTaskBounds[childTaskBounds.length - 1];
        }
        if (rootTaskInfo.bounds != null) {
            return rootTaskInfo.bounds;
        }
        if (fallbackRootTaskInfo == null) {
            return null;
        }
        final Rect[] fallbackChildTaskBounds = fallbackRootTaskInfo.childTaskBounds;
        if (fallbackChildTaskBounds != null && fallbackChildTaskBounds.length > 0) {
            return fallbackChildTaskBounds[fallbackChildTaskBounds.length - 1];
        }
        return fallbackRootTaskInfo.bounds;
    }
}

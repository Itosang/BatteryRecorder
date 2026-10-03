package android.hardware.display;

import android.hardware.display.DisplayTopology;

/**
 * Hidden API 编译桩（Android 17 / API 37 真实接口方法表）：
 *
 *   onDisplayEvent(int displayId, int event)            → (II)V     事务码 1
 *   onDisplaySnapshot(int[] displayIds, int[] states)   → ([I[I)V   事务码 2（Android 16+ 新增）
 *   onTopologyChanged(DisplayTopology topology)         → 事务码 3（Android 16+ 新增）
 *
 * 注意：隐藏接口在编译期走 compileOnly 桩、运行期由 bootclasspath 解析到系统真实
 * 类（含以上全部方法）。因此本地 AIDL 必须与方法表保持"方法集一致"——真实接口
 * 中存在的抽象回调在本桩缺失时，运行期事务派发会抛 AbstractMethodError
 * （"onDisplaySnapshot" 崩溃即由此而来）。
 */
interface IDisplayManagerCallback {
    oneway void onDisplayEvent(int displayId, int event);
    oneway void onDisplaySnapshot(in int[] displayIds, in int[] displayStates);
    oneway void onTopologyChanged(in DisplayTopology topology);
}

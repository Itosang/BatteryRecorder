# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# androidx.window 会通过反射探测设备厂商扩展实现；这些类在编译期并不会随 APK 一起打包。
# R8 在 release shrink 时会把这类可选厂商扩展当成缺失类，需要显式忽略 warning。
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**

# Compose 会把部分保存状态对象直接写进 Bundle，包括 runtime/foundation 内部 Parcelable。
# Release 混淆后若这些类的 CREATOR 被裁剪，进程在后台被杀后恢复界面会抛 BadParcelableException。
-keepclassmembers class androidx.compose.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

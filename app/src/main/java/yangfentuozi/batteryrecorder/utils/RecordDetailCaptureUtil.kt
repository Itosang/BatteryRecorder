package yangfentuozi.batteryrecorder.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.shared.data.RecordsFile

/**
 * 在记录详情长截图顶部追加应用和设备信息头。
 *
 * @param context 应用上下文；用于读取 i18n 文案。
 * @param sourceBitmap 原始长截图位图。
 * @param backgroundColorArgb 头部背景色。
 * @param textColorArgb 头部文字颜色。
 * @return 追加头部后的新位图。
 */
fun appendRecordDetailScreenshotHeader(
    context: Context,
    sourceBitmap: Bitmap,
    backgroundColorArgb: Int,
    textColorArgb: Int
): Bitmap {
    val density = context.resources.displayMetrics
    val horizontalPadding = density.dpToPx(20f)
    val topPadding = density.dpToPx(20f)
    val titleSubtitleSpacing = density.dpToPx(8f)
    val subtitleLineSpacing = density.dpToPx(4f)
    val bottomPadding = density.dpToPx(10f)
    val textWidth = (sourceBitmap.width - horizontalPadding * 2).coerceAtLeast(1)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColorArgb
        textSize = density.spToPx(22f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColorArgb
        textSize = density.spToPx(14f)
    }
    val titleLayout = buildStaticLayout(
        text = context.getString(R.string.capture_header_title),
        textPaint = titlePaint,
        width = textWidth
    )
    val subtitleLayout = buildStaticLayout(
        text = context.getString(R.string.capture_header_device, Build.MODEL),
        textPaint = subtitlePaint,
        width = textWidth
    )
    val versionLayout = buildStaticLayout(
        text = context.getString(R.string.capture_header_version, readVersionName(context)),
        textPaint = subtitlePaint,
        width = textWidth
    )
    val headerHeight = topPadding +
        titleLayout.height +
        titleSubtitleSpacing +
        subtitleLayout.height +
        subtitleLineSpacing +
        versionLayout.height +
        bottomPadding
    val resultBitmap = createBitmap(sourceBitmap.width, sourceBitmap.height + headerHeight)
    val canvas = AndroidCanvas(resultBitmap)
    canvas.drawColor(backgroundColorArgb)
    canvas.withTranslation(horizontalPadding.toFloat(), topPadding.toFloat()) {
        titleLayout.draw(this)
        translate(0f, (titleLayout.height + titleSubtitleSpacing).toFloat())
        subtitleLayout.draw(this)
        translate(0f, (subtitleLayout.height + subtitleLineSpacing).toFloat())
        versionLayout.draw(this)
    }
    canvas.drawBitmap(sourceBitmap, 0f, headerHeight.toFloat(), null)
    return resultBitmap
}

/**
 * 构建记录详情页长截图的默认文件名。
 *
 * @param recordsFile 当前详情页对应的逻辑记录文件。
 * @return 包含 `.png` 后缀的文件名。
 */
fun buildRecordDetailScreenshotFileName(recordsFile: RecordsFile): String {
    val recordName = recordsFile.name.removeSuffix(".txt")
    return "batteryrecorder-${recordsFile.type.dataDirName}-$recordName.png"
}

/**
 * 构建截图头部使用的静态文本布局。
 *
 * @param text 需要绘制的文本。
 * @param textPaint 文本画笔。
 * @param width 文本可用宽度。
 * @return 可直接绘制到 Canvas 的文本布局。
 */
private fun buildStaticLayout(
    text: CharSequence,
    textPaint: TextPaint,
    width: Int
): StaticLayout {
    return StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()
}

/**
 * 将 dp 转为像素。
 *
 * @param value 目标 dp 值。
 * @return 对应像素值。
 */
private fun android.util.DisplayMetrics.dpToPx(value: Float): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, this).toInt()
}

/**
 * 将 sp 转为像素。
 *
 * @param value 目标 sp 值。
 * @return 对应像素值。
 */
private fun android.util.DisplayMetrics.spToPx(value: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, this)
}

/**
 * 读取当前应用的版本号文本。
 *
 * @param context 应用上下文。
 * @return 当前安装包的 `versionName`。
 */
private fun readVersionName(context: Context): String {
    val packageInfo = if (SDK_INT >= VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return requireNotNull(packageInfo.versionName) { "versionName 为空" }
}

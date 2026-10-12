package com.calorietracker.app.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拍照落盘。照片存到 app 私有目录，不入媒体库（用户没要求把饭菜照相册）。
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    /** 生成一个新的拍照输出文件 */
    fun newPhotoFile(): File {
        val name = "meal_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg"
        return File(dir, name)
    }

    /** File -> content:// Uri（FileProvider），Coil 与 ContentResolver 都能直接用 */
    fun toUri(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    /**
     * 把相册选中的图片复制进 app 私有目录。
     * 系统相册给的 content:// 授权可能只在本次会话有效，直接存它的字符串会出现
     * 「今天能看，明天记录里的图打不开」的问题。
     */
    fun importFrom(uri: Uri): File? = runCatching {
        val target = newPhotoFile()
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    /**
     * 清理没有任何记录引用的照片文件，返回删掉的个数。
     *
     * 为什么需要：照片是**只写不读**的（`meal_entries.photo_path` 全工程没有读取方），
     * 而且删记录只删数据库行、不删文件 —— 私有目录会随着使用一直涨（每张 1~3MB）。
     * 启动时按「记录里引用到的文件名」扫一遍即可同时清掉历史残留与今后产生的孤儿。
     *
     * 两个细节：
     *  - 记录里存的是 FileProvider 的 `content://` Uri 字符串（不是文件路径），
     *    所以按**最后一段文件名**比对；
     *  - 跳过最近 1 小时内修改过的文件：万一清理与正在进行的拍摄撞上，
     *    也不能把刚拍好、还没落库的那张删掉。
     */
    fun pruneOrphans(referenced: Collection<String>): Int {
        val keep = referenced
            .mapNotNull { it.substringAfterLast('/').takeIf { name -> name.isNotEmpty() } }
            .toSet()
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS
        return runCatching {
            dir.listFiles()
                ?.count { it.isFile && it.lastModified() < cutoff && it.name !in keep && it.delete() }
                ?: 0
        }.getOrDefault(0)
    }

    private companion object {
        /** 孤儿清理的宽限期：这段时间内新建的文件一律不动（防误删在途拍摄） */
        const val ORPHAN_GRACE_MS = 60 * 60 * 1000L
    }
}

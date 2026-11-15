package com.zhuangxiu.ledger.data.repository

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * 凭证图片的文件存储。所有图片都放在 App 私有目录 [dir] 下，
 * 数据库里只保存文件名。
 */
class ImageStore(val dir: File) {

    init {
        dir.mkdirs()
    }

    /** 生成一个新的空文件（拍照时作为输出文件） */
    fun newFile(extension: String = "jpg"): File {
        dir.mkdirs()
        return File(dir, "IMG_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension")
    }

    fun file(fileName: String): File = File(dir, fileName)

    /** 把输入流里的图片保存到私有目录，返回文件名 */
    fun importFrom(input: InputStream, extension: String = "jpg"): String {
        val target = newFile(extension)
        target.outputStream().use { output -> input.copyTo(output) }
        return target.name
    }

    /** 从相册选图后，把图片复制进私有目录；失败返回 null */
    fun importFromUri(resolver: ContentResolver, uri: Uri): String? = try {
        val extension = when (resolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        resolver.openInputStream(uri)?.use { importFrom(it, extension) }
    } catch (e: Exception) {
        null
    }

    fun delete(fileName: String) {
        file(fileName).delete()
    }

    /** 清空全部图片（恢复备份前使用） */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}

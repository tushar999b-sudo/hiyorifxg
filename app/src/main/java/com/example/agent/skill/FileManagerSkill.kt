package com.example.agent.skill

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileManagerSkill(private val context: Context) {

    private fun resolveTargetFile(rawPath: String): File {
        val trimmed = rawPath.trim()
        val root = Environment.getExternalStorageDirectory()
        return if (trimmed.startsWith("/")) {
            File(trimmed)
        } else {
            val baseDir = File(root, "Hiyori").apply { if (!exists()) mkdirs() }
            File(baseDir, trimmed)
        }
    }

    suspend fun createOrWriteFile(path: String, content: String, append: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveTargetFile(path)
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                if (append && file.exists()) {
                    file.appendText(content)
                } else {
                    file.writeText(content)
                }
                "Saved ${file.length()} bytes to ${file.absolutePath}"
            }
        }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveTargetFile(path)
            if (!file.exists()) error("File not found: ${file.absolutePath}")
            val text = file.readText()
            "File: ${file.absolutePath} (${file.length()} bytes)\n\n${text.take(4000)}"
        }
    }

    suspend fun moveOrRename(sourcePath: String, destPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = resolveTargetFile(sourcePath)
                if (!src.exists()) error("Source not found: ${src.absolutePath}")
                val dst = resolveTargetFile(destPath)
                dst.parentFile?.let { if (!it.exists()) it.mkdirs() }
                if (!src.renameTo(dst)) {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                }
                "Moved to ${dst.absolutePath}"
            }
        }

    suspend fun deleteFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveTargetFile(path)
            if (!file.exists()) error("File not found: ${file.absolutePath}")
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (!deleted) error("Could not delete ${file.absolutePath}")
            "Deleted ${file.absolutePath}"
        }
    }

    suspend fun searchFiles(query: String, searchContent: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = Environment.getExternalStorageDirectory()
                val q = query.trim().lowercase()
                val matches = mutableListOf<String>()
                val skipDirs = setOf("Android", ".thumbnails", ".cache")

                fun walk(dir: File, depth: Int) {
                    if (depth > 4 || matches.size >= 25) return
                    val children = dir.listFiles() ?: return
                    for (child in children) {
                        if (matches.size >= 25) break
                        if (child.isDirectory) {
                            if (child.name !in skipDirs && !child.name.startsWith(".")) {
                                walk(child, depth + 1)
                            }
                        } else {
                            val nameMatch = child.name.lowercase().contains(q)
                            val contentMatch = if (!nameMatch && searchContent && child.length() < 200_000L) {
                                runCatching { child.readText().lowercase().contains(q) }.getOrDefault(false)
                            } else false
                            if (nameMatch || contentMatch) {
                                matches.add("${child.absolutePath} (${child.length()} B)")
                            }
                        }
                    }
                }

                walk(root, 0)
                if (matches.isEmpty()) {
                    "No files matching '$query' found."
                } else {
                    "Found ${matches.size} file(s):\n" + matches.joinToString("\n")
                }
            }
        }

    suspend fun createDocument(fileName: String, format: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ext = format.lowercase().trim().trimStart('.')
                val cleanName = if (fileName.endsWith(".$ext", ignoreCase = true)) fileName else "$fileName.$ext"
                val file = resolveTargetFile(cleanName)
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }

                when (ext) {
                    "pdf" -> {
                        val doc = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                        val page = doc.startPage(pageInfo)
                        val paint = Paint().apply {
                            textSize = 12f
                            isAntiAlias = true
                        }
                        var y = 48f
                        for (line in content.lines()) {
                            if (y > 800f) break
                            page.canvas.drawText(line.take(90), 40f, y, paint)
                            y += 18f
                        }
                        doc.finishPage(page)
                        FileOutputStream(file).use { out -> doc.writeTo(out) }
                        doc.close()
                    }
                    "csv", "md", "txt" -> {
                        file.writeText(content)
                    }
                    else -> {
                        file.writeText(content)
                    }
                }
                "Created $ext document at ${file.absolutePath}"
            }
        }
}

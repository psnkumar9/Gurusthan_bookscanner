package org.gurusthan.bookscanner
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
class BookStore(context: Context) {
    private val root = File(context.filesDir, "books").apply { mkdirs() }
    fun newBook(): String = "book_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
    fun bookDir(id: String): File = File(root, id).apply { mkdirs() }
    fun pageCount(id: String): Int = File(bookDir(id), "pages.tsv").takeIf { it.exists() }?.readLines()?.size ?: 0
    fun saveImage(id: String, name: String, bitmap: Bitmap): File {
        val file = File(bookDir(id), name)
        FileOutputStream(file).use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
        return file
    }
    @Synchronized fun appendPair(id: String, left: String, right: String): Int {
        val dir = bookDir(id)
        val index = File(dir, "pages.tsv")
        val next = pageCount(id) + 1
        val pairId = "%05d".format(next)
        val leftFile = File(dir, "${pairId}_left.txt")
        val rightFile = File(dir, "${pairId}_right.txt")
        leftFile.writeText(left, Charsets.UTF_8)
        rightFile.writeText(right, Charsets.UTF_8)
        index.appendText("$pairId\t${leftFile.name}\t${rightFile.name}\n", Charsets.UTF_8)
        rebuildMaster(id)
        return next
    }
    @Synchronized fun rebuildMaster(id: String): File {
        val dir = bookDir(id)
        val index = File(dir, "pages.tsv")
        val target = File(dir, "master.txt")
        val temp = File(dir, "master.txt.tmp")
        temp.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { out ->
            if (index.exists()) index.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 3) {
                    out.write("\n\n===== SCAN ${p[0]} / LEFT =====\n\n")
                    out.write(File(dir, p[1]).readText())
                    out.write("\n\n===== SCAN ${p[0]} / RIGHT =====\n\n")
                    out.write(File(dir, p[2]).readText())
                }
            }
        }
        if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
        return target
    }
}

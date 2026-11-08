package com.savoj.volumpdf.ui.reader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/** Бінарний формат незбережених об'єктів (DataOutputStream; перший байт — версія). Без Android і без бібліотек. */
object PendingCodec {
    const val VERSION: Int = 1
    private const val MAX_ITEMS = 100_000
    private const val MAX_FLOATS = 8_000_000
    private const val MAX_TEXT_BYTES = 4_000_000

    class Decoded(val documentKey: String, val items: List<PendingMarkup>)

    fun encode(documentKey: String, items: List<PendingMarkup>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            writeString(out, documentKey)
            out.writeInt(items.size)
            for (p in items) {
                writeString(out, p.tool.name)
                writeString(out, p.id)
                out.writeInt(p.pageIndex)
                out.writeFloat(p.x)
                out.writeFloat(p.y)
                writeString(out, p.text)
                out.writeFloat(p.sizePt)
                out.writeInt(p.color)
                out.writeFloat(p.aspect)
                val xy = p.ink?.xy
                if (xy == null) out.writeInt(-1) else {
                    out.writeInt(xy.size)
                    for (v in xy) out.writeFloat(v)
                }
            }
        }
        return bytes.toByteArray()
    }

    /** null — невідома версія, пошкоджені чи обрізані дані (такий файл ігнорується, а не валить застосунок). */
    fun decode(data: ByteArray): Decoded? = try {
        DataInputStream(ByteArrayInputStream(data)).use { inp ->
            if (inp.readUnsignedByte() != VERSION) return null
            val key = readString(inp)
            val count = inp.readInt()
            if (count < 0 || count > MAX_ITEMS) return null
            val items = ArrayList<PendingMarkup>(count)
            repeat(count) {
                val tool = PlaceTool.valueOf(readString(inp))
                val id = readString(inp)
                val page = inp.readInt()
                val x = inp.readFloat()
                val y = inp.readFloat()
                val text = readString(inp)
                val size = inp.readFloat()
                val color = inp.readInt()
                val aspect = inp.readFloat()
                val n = inp.readInt()
                val ink = when {
                    n == -1 -> null
                    n < 0 || n > MAX_FLOATS || n % 2 != 0 -> return null
                    else -> InkPoints(FloatArray(n) { inp.readFloat() })
                }
                items += PendingMarkup(id, tool, page, x, y, text, size, color, ink, aspect)
            }
            if (inp.read() != -1) return null // зайві байти: файл не наш
            Decoded(key, items)
        }
    } catch (_: Exception) {
        null
    }

    private fun writeString(out: DataOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        out.writeInt(b.size)
        out.write(b)
    }

    private fun readString(inp: DataInputStream): String {
        val n = inp.readInt()
        if (n < 0 || n > MAX_TEXT_BYTES) throw java.io.IOException("bad length")
        val b = ByteArray(n)
        inp.readFully(b)
        return String(b, Charsets.UTF_8)
    }
}

/**
 * Сховище незбереженого у приватному каталозі застосунку: `<root>/<хеш URI документа>/state.bin` і поруч
 * `<id>.png` для кожного підпису. Дані (штрихи, підпис) не покидають пристрій. Запис — через тимчасовий файл.
 */
class PendingStore(private val root: File) {

    fun dirFor(documentKey: String): File = File(root, hash(documentKey))

    private fun stateFile(documentKey: String) = File(dirFor(documentKey), STATE_NAME)

    fun imageFile(documentKey: String, itemId: String): File = File(dirFor(documentKey), safe(itemId) + ".png")

    /**
     * Зберігає список. Порожній список = видалення запису. Для кожного підпису без PNG викликається [writeImage]
     * (файл для запису); зайві PNG видаляються.
     */
    fun save(documentKey: String, items: List<PendingMarkup>, writeImage: (itemId: String, target: File) -> Unit) {
        if (items.isEmpty()) {
            clear(documentKey)
            return
        }
        val dir = dirFor(documentKey)
        dir.mkdirs()
        val wanted = HashSet<String>()
        for (p in items) {
            if (p.tool != PlaceTool.SIGNATURE) continue
            val f = imageFile(documentKey, p.id)
            wanted += f.name
            if (!f.exists()) {
                val tmp = File(dir, f.name + ".tmp")
                writeImage(p.id, tmp)
                if (tmp.exists() && !tmp.renameTo(f)) {
                    tmp.copyTo(f, overwrite = true)
                    tmp.delete()
                }
            }
        }
        val tmp = File(dir, STATE_NAME + ".tmp")
        tmp.writeBytes(PendingCodec.encode(documentKey, items))
        val target = stateFile(documentKey)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".png") && f.name !in wanted) f.delete()
        }
    }

    /** null — запису немає, або він пошкоджений, або належить іншому документові (колізія хеша). */
    fun load(documentKey: String): List<PendingMarkup>? {
        val f = stateFile(documentKey)
        if (!f.isFile) return null
        val decoded = try { PendingCodec.decode(f.readBytes()) } catch (_: Exception) { null } ?: return null
        return if (decoded.documentKey == documentKey) decoded.items else null
    }

    fun clear(documentKey: String) {
        dirFor(documentKey).deleteRecursively()
    }

    /** Видаляє записи, до яких не торкалися довше за [maxAgeMs]. Повертає скільки видалено. */
    fun purgeOlderThan(nowMs: Long, maxAgeMs: Long): Int {
        var removed = 0
        root.listFiles()?.forEach { dir ->
            val newest = (dir.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L).coerceAtLeast(dir.lastModified())
            if (nowMs - newest > maxAgeMs && dir.deleteRecursively()) removed++
        }
        return removed
    }

    companion object {
        const val STATE_NAME = "state.bin"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        fun hash(documentKey: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(documentKey.toByteArray(Charsets.UTF_8))
            return d.take(16).joinToString("") { "%02x".format(it) }
        }

        private fun safe(id: String) = id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
    }
}

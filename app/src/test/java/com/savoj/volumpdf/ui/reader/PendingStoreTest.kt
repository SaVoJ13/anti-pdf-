package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val red = 0xFFD32F2F.toInt()

    private fun sample(): List<PendingMarkup> = listOf(
        PendingMarkup("t-1", PlaceTool.TEXT, 119, 12.345f, -3.5f, "рядок 1\nрядок\t2 \\ кінець\r\n✓?", 14f, red),
        PendingMarkup("m-1", PlaceTool.CROSS, 0, 0f, 0f, "", 96f, 0xFF000000.toInt()),
        PendingMarkup("i-1", PlaceTool.INK, 2, 1f, 2f, "", 5f, 0x66112233,
            ink = InkPoints(floatArrayOf(1f, 2f, 3.5f, -4.25f, 1e6f, 7f))),
        PendingMarkup("g-1", PlaceTool.SIGNATURE, 1, 20f, 30f, "", 120f, 0, aspect = 0.4f)
    )

    @Test
    fun codecRoundTripKeepsEveryField() {
        val bytes = PendingCodec.encode("content://doc/1", sample())
        assertEquals(PendingCodec.VERSION, bytes[0].toInt())
        val back = PendingCodec.decode(bytes)!!
        assertEquals("content://doc/1", back.documentKey)
        assertEquals(sample(), back.items)
    }

    @Test
    fun codecRoundTripWithManyPointsAndEmptyList() {
        val big = FloatArray(200_000) { it * 0.37f }
        val item = PendingMarkup("i", PlaceTool.INK, 0, big[0], big[1], "", 2f, red, ink = InkPoints(big))
        assertEquals(listOf(item), PendingCodec.decode(PendingCodec.encode("k", listOf(item)))!!.items)
        assertTrue(PendingCodec.decode(PendingCodec.encode("k", emptyList()))!!.items.isEmpty())
    }

    @Test
    fun codecRejectsGarbageTruncatedAndUnknownVersion() {
        val good = PendingCodec.encode("k", sample())
        assertNull(PendingCodec.decode(ByteArray(0)))
        assertNull(PendingCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(PendingCodec.decode(good.copyOf(good.size - 3)))
        assertNull(PendingCodec.decode(good + byteArrayOf(0)))
        val other = good.copyOf().also { it[0] = 99 }
        assertNull(PendingCodec.decode(other))
    }

    @Test
    fun storeSavesLoadsAndClears() {
        val store = PendingStore(File(tmp.root, "pending"))
        val written = mutableListOf<String>()
        store.save("doc-A", sample()) { id, f -> written += id; f.writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(listOf("g-1"), written)
        assertTrue(store.imageFile("doc-A", "g-1").isFile)
        assertEquals(sample(), store.load("doc-A"))
        assertNull(store.load("doc-B"))
        // повторний запис не перезаписує наявний PNG
        store.save("doc-A", sample()) { id, f -> written += id; f.writeBytes(byteArrayOf(9)) }
        assertEquals(1, written.size)
        // підпис видалено — PNG зникає
        store.save("doc-A", sample().filter { it.tool != PlaceTool.SIGNATURE }) { _, _ -> }
        assertFalse(store.imageFile("doc-A", "g-1").exists())
        // порожній список = видалення
        store.save("doc-A", emptyList()) { _, _ -> }
        assertFalse(store.dirFor("doc-A").exists())
        assertNull(store.load("doc-A"))
    }

    @Test
    fun corruptStateFileIsIgnored() {
        val store = PendingStore(File(tmp.root, "pending"))
        store.save("doc", sample()) { _, f -> f.writeBytes(byteArrayOf(1)) }
        File(store.dirFor("doc"), PendingStore.STATE_NAME).writeBytes(byteArrayOf(1, 0, 0))
        assertNull(store.load("doc"))
    }

    @Test
    fun purgeRemovesOnlyOldRecords() {
        val store = PendingStore(File(tmp.root, "pending"))
        store.save("old", sample()) { _, f -> f.writeBytes(byteArrayOf(1)) }
        store.save("fresh", sample()) { _, f -> f.writeBytes(byteArrayOf(1)) }
        val now = System.currentTimeMillis()
        val tenDaysAgo = now - 10L * 24 * 60 * 60 * 1000
        store.dirFor("old").listFiles()!!.forEach { it.setLastModified(tenDaysAgo) }
        store.dirFor("old").setLastModified(tenDaysAgo)
        assertEquals(1, store.purgeOlderThan(now, PendingStore.MAX_AGE_MS))
        assertNull(store.load("old"))
        assertNotNull(store.load("fresh"))
    }

    @Test
    fun hashIsStableAndFilesystemSafe() {
        val h = PendingStore.hash("content://com.android.providers.media.documents/document/document%3A1")
        assertEquals(h, PendingStore.hash("content://com.android.providers.media.documents/document/document%3A1"))
        assertTrue(h.matches(Regex("[0-9a-f]{32}")))
        assertTrue(h != PendingStore.hash("file:///a.pdf"))
    }
}

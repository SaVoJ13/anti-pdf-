package com.savoj.volumpdf.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingWaitTest {

    @Test fun returnsTrueImmediatelyWhenAlreadyLoaded() = runBlocking {
        assertTrue(PendingWait.awaitLoaded(MutableStateFlow(true), timeoutMs = 50))
    }

    @Test fun returnsFalseAfterTimeoutWhenNeverLoaded() = runBlocking {
        assertFalse("без завантаження має спрацювати таймаут, а не зависання", PendingWait.awaitLoaded(MutableStateFlow(false), timeoutMs = 50))
    }

    @Test fun returnsTrueWhenLoadedBeforeTimeout() = runBlocking {
        val flow = MutableStateFlow(false)
        val t = Thread { Thread.sleep(30); flow.value = true }
        t.start()
        assertTrue(PendingWait.awaitLoaded(flow, timeoutMs = 5_000))
        t.join()
    }

    @Test fun timeoutIsAboutTenSeconds() {
        assertEquals(10_000L, PendingWait.TIMEOUT_MS)
    }
}

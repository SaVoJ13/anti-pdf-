package com.savoj.volumpdf.ui.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PendingLoadGuardTest {

    @Test fun marksLoadedAfterSuccess() = runBlocking {
        var loaded = 0; var failures = 0
        PendingLoadGuard.run({ failures++ }, { loaded++ }) { }
        assertEquals(1, loaded); assertEquals(0, failures)
    }

    @Test fun marksLoadedAndDoesNotThrowWhenBlockFails() = runBlocking {
        var loaded = 0; var failure: Throwable? = null
        PendingLoadGuard.run({ failure = it }, { loaded++ }) { throw IllegalStateException("boom") }
        assertEquals("прапорець має стати true попри виняток", 1, loaded)
        assertTrue(failure is IllegalStateException)
    }

    @Test fun cancellationIsRethrownButStillMarksLoaded() = runBlocking {
        var loaded = 0; var failures = 0
        try {
            PendingLoadGuard.run({ failures++ }, { loaded++ }) { throw CancellationException("cancel") }
            fail("скасування має пройти далі")
        } catch (_: CancellationException) {
        }
        assertEquals(1, loaded); assertEquals(0, failures)
    }
}

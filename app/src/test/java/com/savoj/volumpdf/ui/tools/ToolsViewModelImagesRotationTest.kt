package com.savoj.volumpdf.ui.tools

import android.app.Application
import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.PageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.lang.reflect.Proxy

/** Кути повороту зображень у ToolsViewModel: передача в рушій, rotateFileAt, видалення. */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolsViewModelImagesRotationTest {

    private val calls = mutableListOf<List<Int>>()
    private val uris = List(3) { mock(Uri::class.java) }

    /** Підроблений рушій: лише записує кути з imagesToPdf; решта методів не потрібна. */
    private val fakeEngine: PageOperationsEngine = Proxy.newProxyInstance(
        PageOperationsEngine::class.java.classLoader,
        arrayOf(PageOperationsEngine::class.java)
    ) { _, method, args ->
        if (method.name == "imagesToPdf") {
            @Suppress("UNCHECKED_CAST")
            calls += (args[3] as List<Int>)
            PdfOperationResult.Progress(1, 1)
        } else {
            throw UnsupportedOperationException(method.name)
        }
    } as PageOperationsEngine

    private lateinit var vm: ToolsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ToolsViewModel(mock(Application::class.java), fakeEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun image(i: Int) = SelectedFileItem(uris[i], "img$i", isImage = true)

    @Test
    fun executeImagesToPdf_passesRotationsInOrder() {
        vm.setSelectedFiles(listOf(image(0), image(1), image(2)))
        vm.rotateFileAt(0)
        vm.rotateFileAt(2); vm.rotateFileAt(2)
        vm.executeImagesToPdf(mock(Context::class.java), uris[0])
        assertEquals(listOf(listOf(90, 0, 180)), calls)
    }

    @Test
    fun rotateFileAt_fourPresses_backToZero() {
        vm.setSelectedFiles(listOf(image(0)))
        repeat(3) { vm.rotateFileAt(0) }
        assertEquals(270, vm.selectedFiles.value[0].rotationDeg)
        vm.rotateFileAt(0)
        assertEquals(0, vm.selectedFiles.value[0].rotationDeg)
    }

    @Test
    fun rotateFileAt_ignoresNonImages_andBadPosition() {
        vm.setSelectedFiles(listOf(SelectedFileItem(uris[0], "a.pdf", isImage = false)))
        vm.rotateFileAt(0)
        vm.rotateFileAt(5)
        assertEquals(0, vm.selectedFiles.value[0].rotationDeg)
    }

    @Test
    fun removeFileAt_afterRotations_angleTravelsWithItem() {
        vm.setSelectedFiles(listOf(image(0), image(1), image(2)))
        vm.rotateFileAt(1)
        vm.rotateFileAt(2); vm.rotateFileAt(2); vm.rotateFileAt(2)
        vm.removeFileAt(0)
        assertEquals(listOf(90, 270), vm.selectedFiles.value.map { it.rotationDeg })
        vm.executeImagesToPdf(mock(Context::class.java), uris[0])
        assertEquals(listOf(listOf(90, 270)), calls)
    }
}

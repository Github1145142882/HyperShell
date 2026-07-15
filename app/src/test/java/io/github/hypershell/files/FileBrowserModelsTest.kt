package io.github.hypershell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserModelsTest {
    @Test
    fun defaultsBothPanesToInternalStorage() {
        val browser = FileBrowserState()

        assertEquals("/storage/emulated/0", browser.left.path)
        assertEquals("/storage/emulated/0", browser.right.path)
        assertEquals(FilePaneId.Left, browser.activePane)
    }

    @Test
    fun panesKeepIndependentNavigationAndSelection() {
        val browser = FileBrowserState()
            .updatePane(FilePaneId.Left) { it.copy(path = "/system", backStack = listOf("/"), selectedPaths = setOf("/system/build.prop")) }
            .updatePane(FilePaneId.Right) { it.copy(path = "/data", searchQuery = "apk") }

        assertEquals("/system", browser.left.path)
        assertEquals(setOf("/system/build.prop"), browser.left.selectedPaths)
        assertEquals("/data", browser.right.path)
        assertEquals("apk", browser.right.searchQuery)
    }

    @Test
    fun dangerousDeleteClassificationDoesNotOvermatchPrefixes() {
        assertTrue(isCriticalRootPath("/data/adb/modules"))
        assertTrue(isCriticalRootPath("/system"))
        assertFalse(isCriticalRootPath("/system_ext"))
        assertFalse(isCriticalRootPath("/data/administrator"))
    }
}

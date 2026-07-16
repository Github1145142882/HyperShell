package io.github.hypershell.ui

import io.github.hypershell.files.FileKind
import io.github.hypershell.files.RootFileEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FileVisualKindTest {
    @Test
    fun matchesKindsAndExtensionsWithoutCaseSensitivity() {
        assertEquals(FileVisualKind.Folder, classifyFileVisual(entry("Photos", FileKind.Directory)))
        assertEquals(FileVisualKind.SymbolicLink, classifyFileVisual(entry("latest", FileKind.SymbolicLink)))
        assertEquals(FileVisualKind.Image, classifyFileVisual(entry("PHOTO.HEIC")))
        assertEquals(FileVisualKind.Audio, classifyFileVisual(entry("track.FLAC")))
        assertEquals(FileVisualKind.Video, classifyFileVisual(entry("clip.MKV")))
        assertEquals(FileVisualKind.Archive, classifyFileVisual(entry("backup.TAR.GZ")))
        assertEquals(FileVisualKind.Package, classifyFileVisual(entry("bundle.APKS")))
        assertEquals(FileVisualKind.Text, classifyFileVisual(entry("install.SH")))
        assertEquals(FileVisualKind.Generic, classifyFileVisual(entry("binary.bin")))
    }

    private fun entry(name: String, kind: FileKind = FileKind.Regular) = RootFileEntry(
        path = "/tmp/$name",
        name = name,
        kind = kind,
        size = 0,
        modifiedAt = Instant.EPOCH,
        mode = "",
        owner = "",
        group = "",
    )
}

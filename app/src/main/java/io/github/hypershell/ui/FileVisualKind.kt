package io.github.hypershell.ui

import io.github.hypershell.files.FileKind
import io.github.hypershell.files.RootFileEntry

internal enum class FileVisualKind {
    Folder,
    SymbolicLink,
    Image,
    Audio,
    Video,
    Archive,
    Package,
    Text,
    Generic,
}

internal fun classifyFileVisual(entry: RootFileEntry): FileVisualKind {
    if (entry.kind == FileKind.Directory) return FileVisualKind.Folder
    if (entry.kind == FileKind.SymbolicLink) return FileVisualKind.SymbolicLink

    val name = entry.name.lowercase()
    return when {
        name.endsWithAny(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".svg", ".heic", ".heif", ".avif") -> FileVisualKind.Image
        name.endsWithAny(".mp3", ".flac", ".wav", ".aac", ".m4a", ".ogg", ".opus", ".amr") -> FileVisualKind.Audio
        name.endsWithAny(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".3gp", ".ts") -> FileVisualKind.Video
        name.endsWithAny(
            ".zip", ".tar", ".tgz", ".tar.gz", ".tbz", ".tbz2", ".tar.bz2",
            ".txz", ".tar.xz", ".gz", ".bz2", ".xz", ".zst", ".7z", ".rar",
        ) -> FileVisualKind.Archive
        name.endsWithAny(".apk", ".apks", ".xapk", ".apkm", ".aab") -> FileVisualKind.Package
        name.endsWithAny(
            ".txt", ".md", ".log", ".conf", ".cfg", ".ini", ".properties", ".json",
            ".xml", ".yaml", ".yml", ".toml", ".sh", ".bash", ".zsh", ".fish",
            ".py", ".js", ".ts", ".kt", ".kts", ".java", ".c", ".cc", ".cpp",
            ".h", ".hpp", ".rs", ".go", ".html", ".css", ".sql",
        ) -> FileVisualKind.Text
        else -> FileVisualKind.Generic
    }
}

private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)

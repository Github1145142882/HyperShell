package io.github.hypershell.files

enum class FilePaneId { Left, Right }
enum class FileClipboardMode { Copy, Move }
enum class ConflictPolicy { Ask, Skip, Overwrite, AutoRename }

data class FilePaneState(
    val id: FilePaneId,
    val path: String = "/",
    val entries: List<RootFileEntry> = emptyList(),
    val searchQuery: String = "",
    val backStack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val loading: Boolean = false,
    val generation: Long = 0L,
)

data class FileBrowserState(
    val left: FilePaneState = FilePaneState(FilePaneId.Left, path = "/storage/emulated/0"),
    val right: FilePaneState = FilePaneState(FilePaneId.Right, path = "/storage/emulated/0"),
    val activePane: FilePaneId = FilePaneId.Left,
    val splitFraction: Float = 0.5f,
    val clipboard: FileClipboard? = null,
    val operation: FileOperationProgress? = null,
) {
    fun pane(id: FilePaneId): FilePaneState = if (id == FilePaneId.Left) left else right
    fun updatePane(id: FilePaneId, transform: (FilePaneState) -> FilePaneState): FileBrowserState =
        if (id == FilePaneId.Left) copy(left = transform(left)) else copy(right = transform(right))
}

data class FileClipboard(
    val paths: List<String>,
    val mode: FileClipboardMode,
)

data class FileOperationProgress(
    val title: String,
    val completed: Int,
    val total: Int,
    val currentPath: String? = null,
    val errors: List<String> = emptyList(),
)

data class FileConflict(
    val source: String,
    val destination: String,
)

internal fun isCriticalRootPath(path: String): Boolean =
    listOf("/system", "/vendor", "/product", "/data/adb", "/data/system").any { critical ->
        path == critical || path.startsWith("$critical/")
    }

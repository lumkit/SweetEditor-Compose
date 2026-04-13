package com.qiplat.compose.sweeteditor.core

import java.io.*

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utility class for extracting native library files.
 */
object NativeLibraryExtractor {

    private const val NATIVE_RESOURCE_ROOT = "/native/"

    /**
     * Extract native library from resource path to temporary directory.
     *
     * @param resourcePath Resource path (relative to classpath)
     * @param targetDir Target directory, if null uses system temp directory
     * @return Path to the extracted library file
     */
    fun extractLibrary(resourcePath: String, targetDir: File?): File {
        val actualTargetDir = targetDir ?: createTempDirectory("sweet-editor-compose").toFile()
        val resourceName = extractFileNameFromPath(resourcePath)
        val targetFile = File(actualTargetDir, resourceName)

        // If target file already exists and size > 0, return directly
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        // Ensure target directory exists
        if (!actualTargetDir.exists()) {
            actualTargetDir.mkdirs()
        }

        // Read from resource and write to target file
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath.substring(1))
            ?: throw IOException("Resource not found: $resourcePath")

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        return targetFile
    }

    /**
     * Extract native library for current platform to specified directory and set `sweeteditor.lib.path`.
     * <p>
     * If a native library file with the same size already exists, extraction is skipped.
     *
     * @param targetDir Target directory for extraction (created automatically if it doesn't exist)
     * @return Path to the extracted native library file
     * @throws IOException If extraction fails or native library not found in JAR
     */
    fun extract(targetDir: Path): Path {
        val libName = System.mapLibraryName("sweeteditor")
        val resourcePath = resolveNativeResourcePath(libName)

        targetDir.createDirectories()
        val targetFile = targetDir.resolve(libName)

        // Check if already extracted (file exists and size matches)
        if (!needsExtraction(targetFile, resourcePath)) {
            // Size matches, skip extraction, only register path
            registerLibraryPath(targetDir)
            return targetFile
        }

        // Perform extraction
        val inputStream = NativeLibraryExtractor::class.java.classLoader.getResourceAsStream(resourcePath.substring(1))
            ?: throw FileNotFoundException(
                "Native library not found in JAR: $resourcePath " +
                "(libName=$libName)"
            )

        inputStream.use { input ->
            targetFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        // Register path
        registerLibraryPath(targetDir)
        return targetFile
    }

    /**
     * Extract native library for current platform to default directory and set `sweeteditor.lib.path`.
     * <p>
     * Default directories vary by operating system:
     * <ul>
     *   <li><b>Windows</b>: {@code %LOCALAPPDATA%\SweetEditor\native\} (e.g., {@code C:\Users\xxx\AppData\Local\SweetEditor\native\})</li>
     *   <li><b>macOS</b>: {@code ~/Library/Application Support/SweetEditor/native/}</li>
     *   <li><b>Linux</b>: {@code $XDG_DATA_HOME/sweeteditor/native/} (defaults to {@code ~/.local/share/sweeteditor/native/})</li>
     * </ul>
     *
     * @return Path to the extracted native library file
     * @throws IOException If extraction fails
     */
    fun extractToDefaultDir(): Path {
        val defaultDir = getDefaultNativeDir()
        return extract(defaultDir)
    }

    /**
     * Check if native library for current platform already exists in specified directory.
     *
     * @param targetDir Target directory
     * @return {@code true} if native library already exists
     */
    fun isExtracted(targetDir: Path): Boolean {
        val libName = System.mapLibraryName("sweeteditor")
        return targetDir.resolve(libName).exists()
    }

    /**
     * Extract file name from path.
     */
    private fun extractFileNameFromPath(path: String): String {
        val lastSeparatorIndex = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (lastSeparatorIndex >= 0) {
            path.substring(lastSeparatorIndex + 1)
        } else {
            path
        }
    }

    /**
     * Get default native library storage directory for current platform.
     */
    private fun getDefaultNativeDir(): Path {
        val os = System.getProperty("os.name", "").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            os.contains("win") -> {
                // Windows: Prefer LOCALAPPDATA environment variable
                val localAppData = System.getenv("LOCALAPPDATA")
                if (!localAppData.isNullOrEmpty()) {
                    Path.of(localAppData).resolve("SweetEditor").resolve("native")
                } else {
                    Path.of(userHome, "AppData", "Local", "SweetEditor", "native")
                }
            }
            os.contains("mac") || os.contains("darwin") -> {
                // macOS: ~/Library/Application Support/SweetEditor/native/
                Path.of(userHome, "Library", "Application Support", "SweetEditor", "native")
            }
            else -> {
                // Linux: Follow XDG Base Directory Specification
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                if (!xdgDataHome.isNullOrEmpty()) {
                    Path.of(xdgDataHome, "sweeteditor", "native")
                } else {
                    Path.of(userHome, ".local", "share", "sweeteditor", "native")
                }
            }
        }
    }

    /**
     * Determine whether re-extraction is needed.
     * If target file doesn't exist, or its size doesn't match JAR resource, extraction is needed.
     */
    private fun needsExtraction(targetFile: Path, resourcePath: String): Boolean {
        if (!targetFile.exists()) {
            return true
        }

        // Get JAR resource size
        val resourceSize = getResourceSize(resourcePath)
        if (resourceSize < 0) {
            // Can't get resource size (resource doesn't exist), extraction will report error
            return true
        }

        // Compare sizes
        val existingSize = targetFile.fileSize()
        return existingSize != resourceSize
    }

    /**
     * Get JAR resource size in bytes.
     * @return Resource size, returns -1 if resource doesn't exist
     */
    private fun getResourceSize(resourcePath: String): Long {
        val inputStream = NativeLibraryExtractor::class.java.classLoader.getResourceAsStream(resourcePath.substring(1))
        return inputStream?.use { input ->
            input.readAllBytes().size.toLong()
        } ?: -1
    }

    /**
     * Set target directory as system property `sweeteditor.lib.path`,
     * so that `EditorNative` static initialization can prioritize this path to load native library.
     */
    private fun registerLibraryPath(targetDir: Path) {
        System.setProperty("sweeteditor.lib.path", targetDir.toAbsolutePath().toString())
    }

    /**
     * Resolve native library resource path based on current OS/arch.
     * Matches project resource layout: native/<os>/<arch>/<libName>.
     */
    private fun resolveNativeResourcePath(libName: String): String {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()

        val osDir = when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "osx"
            os.contains("linux") -> "linux"
            else -> throw UnsupportedOperationException("Unsupported OS for SweetEditor native library: $os")
        }

        val archDir = when (osDir) {
            "windows" -> when {
                arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> "x64"
                else -> throw UnsupportedOperationException("Unsupported Windows arch for SweetEditor: $arch")
            }
            "osx" -> when {
                arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
                arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
                else -> throw UnsupportedOperationException("Unsupported macOS arch for SweetEditor: $arch")
            }
            "linux" -> when {
                arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
                else -> throw UnsupportedOperationException("Unsupported Linux arch for SweetEditor: $arch")
            }
            else -> throw UnsupportedOperationException("Unsupported OS dir for SweetEditor: $osDir")
        }

        return "$NATIVE_RESOURCE_ROOT$osDir/$archDir/$libName"
    }

    /**
     * Extract native library from resource path to default temp directory.
     *
     * @param resourcePath Resource path (relative to classpath)
     * @return Path to the extracted library file
     */
    fun extractLibrary(resourcePath: String): File {
        return extractLibrary(resourcePath, null)
    }

    /**
     * Batch extract native libraries from resource paths.
     *
     * @param resourcePaths List of resource paths
     * @param targetDir Target directory, if null uses system temp directory
     * @return List of extracted library file paths
     */
    fun extractLibraries(resourcePaths: List<String>, targetDir: File?): List<File> {
        return resourcePaths.map { extractLibrary(it, targetDir) }
    }

    /**
     * Batch extract native libraries from resource paths to default temp directory.
     *
     * @param resourcePaths List of resource paths
     * @return List of extracted library file paths
     */
    fun extractLibraries(resourcePaths: List<String>): List<File> {
        return extractLibraries(resourcePaths, null)
    }

    /**
     * Write input stream to Path.
     */
    private fun Path.writeStream(inputStream: InputStream) {
        outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }
}

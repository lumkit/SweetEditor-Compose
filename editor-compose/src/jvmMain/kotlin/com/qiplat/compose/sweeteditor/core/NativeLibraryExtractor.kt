package com.qiplat.compose.sweeteditor.core

import java.io.*

import java.nio.file.Path
import kotlin.io.path.*

/**
 * 提取本地库文件的工具类
 */
object NativeLibraryExtractor {

    private const val NATIVE_RESOURCE_ROOT = "/native/"

    /**
     * 从资源路径提取本地库到临时目录
     *
     * @param resourcePath 资源路径（相对于 classpath）
     * @param targetDir 目标目录，如果为 null，则使用系统临时目录
     * @return 提取后的库文件路径
     */
    fun extractLibrary(resourcePath: String, targetDir: File?): File {
        val actualTargetDir = targetDir ?: createTempDirectory("sweet-editor-compose").toFile()
        val resourceName = extractFileNameFromPath(resourcePath)
        val targetFile = File(actualTargetDir, resourceName)

        // 如果目标文件已存在且大小不为0，直接返回
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        // 确保目标目录存在
        if (!actualTargetDir.exists()) {
            actualTargetDir.mkdirs()
        }

        // 从资源中读取并写入目标文件
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath.substring(1))
            ?: throw IOException("无法找到资源: $resourcePath")

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        return targetFile
    }

    /**
     * 从资源路径提取当前平台的本地库到指定目录，并自动设置 `sweeteditor.lib.path`。
     * <p>
     * 如果目标目录中已存在相同大小的本地库文件，则跳过提取，仅设置系统属性。
     *
     * @param targetDir 提取目标目录（如果不存在会自动创建）
     * @return 提取的本地库文件路径
     * @throws IOException 如果提取失败或在 JAR 中找不到当前平台的本地库
     */
    fun extract(targetDir: Path): Path {
        val libName = System.mapLibraryName("sweeteditor")
        val platform = detectPlatform()
        val resourcePath = "$NATIVE_RESOURCE_ROOT$platform/$libName"

        targetDir.createDirectories()
        val targetFile = targetDir.resolve(libName)

        // 检查是否已经提取（文件存在且大小匹配）
        if (!needsExtraction(targetFile, resourcePath)) {
            // 大小匹配，跳过提取，仅注册路径
            registerLibraryPath(targetDir)
            return targetFile
        }

        // 执行提取
        val inputStream = NativeLibraryExtractor::class.java.classLoader.getResourceAsStream(resourcePath.substring(1))
            ?: throw FileNotFoundException(
                "在 JAR 中找不到当前平台的本地库: $resourcePath " +
                "(platform=$platform, libName=$libName)"
            )

        inputStream.use { input ->
            targetFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        // 注册路径
        registerLibraryPath(targetDir)
        return targetFile
    }

    /**
     * 提取当前平台的本地库到默认目录，并自动设置 `sweeteditor.lib.path`。
     * <p>
     * 默认目录因操作系统而异：
     * <ul>
     *   <li><b>Windows</b>: {@code %LOCALAPPDATA%\SweetEditor\native\} (例如 {@code C:\Users\xxx\AppData\Local\SweetEditor\native\})</li>
     *   <li><b>macOS</b>: {@code ~/Library/Application Support/SweetEditor/native/}</li>
     *   <li><b>Linux</b>: {@code $XDG_DATA_HOME/sweeteditor/native/} (默认 {@code ~/.local/share/sweeteditor/native/})</li>
     * </ul>
     *
     * @return 提取的本地库文件路径
     * @throws IOException 如果提取失败
     */
    fun extractToDefaultDir(): Path {
        val defaultDir = getDefaultNativeDir()
        return extract(defaultDir)
    }

    /**
     * 检查指定目录中是否已存在当前平台的本地库。
     *
     * @param targetDir 目标目录
     * @return 如果本地库已存在则返回 {@code true}
     */
    fun isExtracted(targetDir: Path): Boolean {
        val libName = System.mapLibraryName("sweeteditor")
        return targetDir.resolve(libName).exists()
    }

    /**
     * 从路径中提取文件名
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
     * 获取当前平台的默认本地库存储目录。
     */
    private fun getDefaultNativeDir(): Path {
        val os = System.getProperty("os.name", "").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            os.contains("win") -> {
                // Windows: 优先使用 LOCALAPPDATA 环境变量
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
                // Linux: 遵循 XDG 基目录规范
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
     * 确定是否需要重新提取。
     * 如果目标文件不存在，或者其大小与 JAR 资源不匹配，则需要提取。
     */
    private fun needsExtraction(targetFile: Path, resourcePath: String): Boolean {
        if (!targetFile.exists()) {
            return true
        }

        // 获取 JAR 资源的大小
        val resourceSize = getResourceSize(resourcePath)
        if (resourceSize < 0) {
            // 无法获取资源大小（资源不存在），提取时会报告错误
            return true
        }

        // 比较大小
        val existingSize = targetFile.fileSize()
        return existingSize != resourceSize
    }

    /**
     * 获取 JAR 资源的大小（以字节为单位）。
     * @return 资源大小，如果资源不存在则返回 -1
     */
    private fun getResourceSize(resourcePath: String): Long {
        val inputStream = NativeLibraryExtractor::class.java.classLoader.getResourceAsStream(resourcePath.substring(1))
        return if (inputStream != null) {
            inputStream.use { input ->
                input.readAllBytes().size.toLong()
            }
        } else {
            -1
        }
    }

    /**
     * 将目标目录设置为系统属性 `sweeteditor.lib.path`，
     * 以便 `EditorNative` 静态初始化时优先使用此路径加载本地库。
     */
    private fun registerLibraryPath(targetDir: Path) {
        System.setProperty("sweeteditor.lib.path", targetDir.toAbsolutePath().toString())
    }

    /**
     * 自动检测当前平台，返回资源子目录名称。
     * 格式为 `<os>-<arch>`，例如 `macos-aarch64`, `windows-x86_64`。
     */
    private fun detectPlatform(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()

        val osName = when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            else -> "linux"
        }

        val archName = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> "x86_64"
            else -> arch // 后备，使用原始值
        }

        return "${osName}-${archName}"
    }

    /**
     * 从资源路径提取本地库到默认临时目录
     *
     * @param resourcePath 资源路径（相对于 classpath）
     * @return 提取后的库文件路径
     */
    fun extractLibrary(resourcePath: String): File {
        return extractLibrary(resourcePath, null)
    }

    /**
     * 从资源路径批量提取本地库
     *
     * @param resourcePaths 资源路径列表
     * @param targetDir 目标目录，如果为 null，则使用系统临时目录
     * @return 提取后的库文件路径列表
     */
    fun extractLibraries(resourcePaths: List<String>, targetDir: File?): List<File> {
        return resourcePaths.map { extractLibrary(it, targetDir) }
    }

    /**
     * 从资源路径批量提取本地库到默认临时目录
     *
     * @param resourcePaths 资源路径列表
     * @return 提取后的库文件路径列表
     */
    fun extractLibraries(resourcePaths: List<String>): List<File> {
        return extractLibraries(resourcePaths, null)
    }

    /**
     * 将输入流写入 Path
     */
    private fun Path.writeStream(inputStream: InputStream) {
        outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }
}

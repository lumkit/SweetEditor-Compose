cmake_minimum_required(VERSION 3.22)

project(sweeteditor_desktop_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED SWEETEDITOR_ARCH_DIR)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(SWEETEDITOR_ARCH_DIR "arm64")
    elseif(WIN32)
        set(SWEETEDITOR_ARCH_DIR "x64")
    else()
        set(SWEETEDITOR_ARCH_DIR "x86_64")
    endif()
endif()

if(NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME is required")
endif()

# Convert JAVA_HOME to CMake path to handle spaces
file(TO_CMAKE_PATH "${JAVA_HOME}" JAVA_HOME)

if(APPLE)
    set(SWEETEDITOR_PLATFORM_DIR "osx")
    set(SWEETEDITOR_CORE_LIBRARY_NAME "libsweeteditor.dylib")
    set(JNI_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/darwin")
elseif(UNIX)
    set(SWEETEDITOR_PLATFORM_DIR "linux")
    set(SWEETEDITOR_CORE_LIBRARY_NAME "libsweeteditor.so")
    set(JNI_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/linux")
elseif(WIN32)
    set(SWEETEDITOR_PLATFORM_DIR "windows")
    set(SWEETEDITOR_CORE_LIBRARY_NAME "sweeteditor.dll")
    set(SWEETEDITOR_IMPORT_LIBRARY_NAME "sweeteditor.lib")
    set(JNI_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/win32")
else()
    message(FATAL_ERROR "Unsupported desktop platform")
endif()

if(WIN32)
    add_library(sweeteditor SHARED IMPORTED)
    set_target_properties(
        sweeteditor
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../resources/native/${SWEETEDITOR_PLATFORM_DIR}/${SWEETEDITOR_ARCH_DIR}/${SWEETEDITOR_CORE_LIBRARY_NAME}
        IMPORTED_IMPLIB
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../editor-core/${SWEETEDITOR_PLATFORM_DIR}/${SWEETEDITOR_ARCH_DIR}/${SWEETEDITOR_IMPORT_LIBRARY_NAME}
    )
else()
    add_library(sweeteditor SHARED IMPORTED)
    set_target_properties(
        sweeteditor
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../resources/native/${SWEETEDITOR_PLATFORM_DIR}/${SWEETEDITOR_ARCH_DIR}/${SWEETEDITOR_CORE_LIBRARY_NAME}
    )
endif()

add_library(
    sweeteditor_desktop_bridge
    SHARED
    desktop_bridge.cpp
)

# Set output directory for all configurations on Windows
if(WIN32 AND DEFINED CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    set_target_properties(sweeteditor_desktop_bridge PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
    )
    # Also set for all configurations (Debug, Release, etc.)
    set_target_properties(sweeteditor_desktop_bridge PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        LIBRARY_OUTPUT_DIRECTORY_DEBUG ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        LIBRARY_OUTPUT_DIRECTORY_RELEASE ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
    )
endif()

target_include_directories(
    sweeteditor_desktop_bridge
    PRIVATE
    ${JAVA_HOME}/include
    ${JNI_PLATFORM_INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../editor-core
)

target_link_libraries(
    sweeteditor_desktop_bridge
    sweeteditor
)

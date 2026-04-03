cmake_minimum_required(VERSION 3.22)

project(sweeteditor_desktop_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED SWEETEDITOR_ARCH_DIR)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(SWEETEDITOR_ARCH_DIR "arm64")
    else()
        set(SWEETEDITOR_ARCH_DIR "x86_64")
    endif()
endif()

if(NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME is required")
endif()

if(APPLE)
    set(SWEETEDITOR_PLATFORM_DIR "osx")
    set(SWEETEDITOR_CORE_LIBRARY_NAME "libsweeteditor.dylib")
elseif(UNIX)
    set(SWEETEDITOR_PLATFORM_DIR "linux")
    set(SWEETEDITOR_CORE_LIBRARY_NAME "libsweeteditor.so")
elseif(WIN32)
    set(SWEETEDITOR_PLATFORM_DIR "windows")
    set(SWEETEDITOR_CORE_LIBRARY_NAME "sweeteditor.dll")
else()
    message(FATAL_ERROR "Unsupported desktop platform")
endif()

add_library(sweeteditor SHARED IMPORTED)
set_target_properties(
    sweeteditor
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../resources/native/${SWEETEDITOR_PLATFORM_DIR}/${SWEETEDITOR_ARCH_DIR}/${SWEETEDITOR_CORE_LIBRARY_NAME}
)

add_library(
    sweeteditor_desktop_bridge
    SHARED
    desktop_bridge.cpp
)

target_include_directories(
    sweeteditor_desktop_bridge
    PRIVATE
    ${JAVA_HOME}/include
    ${JAVA_HOME}/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../editor-core
)

target_link_libraries(
    sweeteditor_desktop_bridge
    sweeteditor
)

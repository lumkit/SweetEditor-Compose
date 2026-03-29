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

add_library(sweeteditor SHARED IMPORTED)
set_target_properties(
    sweeteditor
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../editor-core/include/mac_os/${SWEETEDITOR_ARCH_DIR}/lib/libsweeteditor.dylib
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

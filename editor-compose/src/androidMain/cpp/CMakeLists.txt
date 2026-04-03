cmake_minimum_required(VERSION 3.22.1)

project(sweeteditor_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(sweeteditor SHARED IMPORTED)
set_target_properties(
    sweeteditor
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libsweeteditor.so
)

add_library(
    sweeteditor_bridge
    SHARED
    android_bridge.cpp
)

target_include_directories(
    sweeteditor_bridge
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../editor-core
)

target_link_libraries(
    sweeteditor_bridge
    sweeteditor
    log
)

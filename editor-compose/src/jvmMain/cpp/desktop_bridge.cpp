#include <jni.h>
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#ifndef __stdcall
#define __stdcall
#endif

using U16Char = char16_t;

#include "c_api.h"

namespace {
JavaVM* g_java_vm = nullptr;
std::mutex g_measurer_mutex;
std::unordered_map<intptr_t, jobject> g_measurers;
thread_local intptr_t g_current_editor_handle = 0;

JNIEnv* get_env(bool* did_attach) {
    *did_attach = false;
    if (g_java_vm == nullptr) {
        return nullptr;
    }
    JNIEnv* env = nullptr;
    if (g_java_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) == JNI_OK) {
        return env;
    }
    if (g_java_vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) == JNI_OK) {
        *did_attach = true;
        return env;
    }
    return nullptr;
}

void release_env(bool did_attach) {
    if (did_attach && g_java_vm != nullptr) {
        g_java_vm->DetachCurrentThread();
    }
}

jobject get_current_measurer() {
    std::lock_guard<std::mutex> lock(g_measurer_mutex);
    const auto iterator = g_measurers.find(g_current_editor_handle);
    if (iterator == g_measurers.end()) {
        return nullptr;
    }
    return iterator->second;
}

struct ScopedCurrentEditorHandle {
    explicit ScopedCurrentEditorHandle(intptr_t handle) : previous(g_current_editor_handle) {
        g_current_editor_handle = handle;
    }

    ~ScopedCurrentEditorHandle() {
        g_current_editor_handle = previous;
    }

    intptr_t previous;
};

float measure_text_width_callback(const U16Char* text, int32_t font_style) {
    bool did_attach = false;
    JNIEnv* env = get_env(&did_attach);
    jobject measurer = get_current_measurer();
    if (env == nullptr || measurer == nullptr || text == nullptr) {
        release_env(did_attach);
        return 0.0f;
    }
    jclass clazz = env->GetObjectClass(measurer);
    jmethodID method = env->GetMethodID(clazz, "measureTextWidth", "(Ljava/lang/String;I)F");
    const auto length = static_cast<jsize>(std::char_traits<U16Char>::length(text));
    jstring java_text = env->NewString(reinterpret_cast<const jchar*>(text), length);
    const float result = env->CallFloatMethod(measurer, method, java_text, static_cast<jint>(font_style));
    env->DeleteLocalRef(java_text);
    env->DeleteLocalRef(clazz);
    release_env(did_attach);
    return result;
}

float measure_inlay_hint_width_callback(const U16Char* text) {
    bool did_attach = false;
    JNIEnv* env = get_env(&did_attach);
    jobject measurer = get_current_measurer();
    if (env == nullptr || measurer == nullptr || text == nullptr) {
        release_env(did_attach);
        return 0.0f;
    }
    jclass clazz = env->GetObjectClass(measurer);
    jmethodID method = env->GetMethodID(clazz, "measureInlayHintWidth", "(Ljava/lang/String;)F");
    const auto length = static_cast<jsize>(std::char_traits<U16Char>::length(text));
    jstring java_text = env->NewString(reinterpret_cast<const jchar*>(text), length);
    const float result = env->CallFloatMethod(measurer, method, java_text);
    env->DeleteLocalRef(java_text);
    env->DeleteLocalRef(clazz);
    release_env(did_attach);
    return result;
}

float measure_icon_width_callback(int32_t icon_id) {
    bool did_attach = false;
    JNIEnv* env = get_env(&did_attach);
    jobject measurer = get_current_measurer();
    if (env == nullptr || measurer == nullptr) {
        release_env(did_attach);
        return 0.0f;
    }
    jclass clazz = env->GetObjectClass(measurer);
    jmethodID method = env->GetMethodID(clazz, "measureIconWidth", "(I)F");
    const float result = env->CallFloatMethod(measurer, method, static_cast<jint>(icon_id));
    env->DeleteLocalRef(clazz);
    release_env(did_attach);
    return result;
}

void get_font_metrics_callback(float* values, size_t length) {
    bool did_attach = false;
    JNIEnv* env = get_env(&did_attach);
    jobject measurer = get_current_measurer();
    if (values == nullptr || length == 0) {
        release_env(did_attach);
        return;
    }
    std::fill(values, values + length, 0.0f);
    if (env == nullptr || measurer == nullptr) {
        release_env(did_attach);
        return;
    }
    jclass clazz = env->GetObjectClass(measurer);
    jmethodID method = env->GetMethodID(clazz, "getFontMetrics", "()[F");
    jfloatArray metrics = static_cast<jfloatArray>(env->CallObjectMethod(measurer, method));
    if (metrics != nullptr) {
        const jsize array_length = env->GetArrayLength(metrics);
        const jsize copy_length = std::min<jsize>(static_cast<jsize>(length), array_length);
        env->GetFloatArrayRegion(metrics, 0, copy_length, values);
        env->DeleteLocalRef(metrics);
    }
    env->DeleteLocalRef(clazz);
    release_env(did_attach);
}

text_measurer_t create_text_measurer_callbacks() {
    text_measurer_t measurer {};
    measurer.measure_text_width = measure_text_width_callback;
    measurer.measure_inlay_hint_width = measure_inlay_hint_width_callback;
    measurer.measure_icon_width = measure_icon_width_callback;
    measurer.get_font_metrics = get_font_metrics_callback;
    return measurer;
}

void store_measurer(JNIEnv* env, intptr_t editor_handle, jobject measurer) {
    std::lock_guard<std::mutex> lock(g_measurer_mutex);
    g_measurers[editor_handle] = env->NewGlobalRef(measurer);
}

void release_measurer(JNIEnv* env, intptr_t editor_handle) {
    std::lock_guard<std::mutex> lock(g_measurer_mutex);
    const auto iterator = g_measurers.find(editor_handle);
    if (iterator == g_measurers.end()) {
        return;
    }
    env->DeleteGlobalRef(iterator->second);
    g_measurers.erase(iterator);
}

jbyteArray copy_binary_payload(JNIEnv* env, const uint8_t* data, size_t size) {
    if (data == nullptr || size == 0) {
        if (data != nullptr) {
            free_binary_data(reinterpret_cast<intptr_t>(data));
        }
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(size),
        reinterpret_cast<const jbyte*>(data)
    );
    free_binary_data(reinterpret_cast<intptr_t>(data));
    return result;
}

std::vector<uint8_t> read_byte_array(JNIEnv* env, jbyteArray data) {
    if (data == nullptr) {
        return {};
    }
    const jsize size = env->GetArrayLength(data);
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    if (size > 0) {
        env->GetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
    }
    return bytes;
}
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    g_java_vm = vm;
    return JNI_VERSION_1_8;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCreateDocumentFromUtf16(
    JNIEnv* env,
    jclass,
    jstring text
) {
    if (text == nullptr) {
        return 0;
    }
    const jchar* chars = env->GetStringChars(text, nullptr);
    const intptr_t handle = create_document_from_utf16(reinterpret_cast<const U16Char*>(chars));
    env->ReleaseStringChars(text, chars);
    return static_cast<jlong>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCreateDocumentFromFile(
    JNIEnv* env,
    jclass,
    jstring path
) {
    if (path == nullptr) {
        return 0;
    }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    const intptr_t handle = create_document_from_file(chars);
    env->ReleaseStringUTFChars(path, chars);
    return static_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeFreeDocument(
    JNIEnv*,
    jclass,
    jlong handle
) {
    if (handle != 0) {
        free_document(static_cast<intptr_t>(handle));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetDocumentLineCount(
    JNIEnv*,
    jclass,
    jlong handle
) {
    return static_cast<jint>(get_document_line_count(static_cast<intptr_t>(handle)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetDocumentLineText(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint line
) {
    const U16Char* text = get_document_line_text(
        static_cast<intptr_t>(handle),
        static_cast<size_t>(line)
    );
    if (text == nullptr) {
        return env->NewString(nullptr, 0);
    }
    size_t length = 0;
    while (text[length] != 0) {
        length++;
    }
    return env->NewString(reinterpret_cast<const jchar*>(text), static_cast<jsize>(length));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCreateEditor(
    JNIEnv* env,
    jclass,
    jobject text_measurer,
    jbyteArray options_data
) {
    std::vector<uint8_t> bytes = read_byte_array(env, options_data);
    text_measurer_t measurer = create_text_measurer_callbacks();
    const intptr_t editor_handle = create_editor(
        measurer,
        bytes.empty() ? nullptr : bytes.data(),
        bytes.size()
    );
    if (editor_handle != 0 && text_measurer != nullptr) {
        store_measurer(env, editor_handle, text_measurer);
    }
    return static_cast<jlong>(editor_handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeFreeEditor(
    JNIEnv* env,
    jclass,
    jlong handle
) {
    if (handle == 0) {
        return;
    }
    release_measurer(env, static_cast<intptr_t>(handle));
    free_editor(static_cast<intptr_t>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetEditorDocument(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jlong document_handle
) {
    set_editor_document(static_cast<intptr_t>(editor_handle), static_cast<intptr_t>(document_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetEditorViewport(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint width,
    jint height
) {
    set_editor_viewport(static_cast<intptr_t>(editor_handle), static_cast<int16_t>(width), static_cast<int16_t>(height));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeOnFontMetricsChanged(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_on_font_metrics_changed(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetFoldArrowMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    editor_set_fold_arrow_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetWrapMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_wrap_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetTabSize(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint tab_size
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_tab_size(static_cast<intptr_t>(editor_handle), tab_size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetScale(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jfloat scale
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_scale(static_cast<intptr_t>(editor_handle), scale);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetLineSpacing(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jfloat add,
    jfloat mult
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_line_spacing(static_cast<intptr_t>(editor_handle), add, mult);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetShowSplitLine(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean show
) {
    editor_set_show_split_line(static_cast<intptr_t>(editor_handle), show ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetCurrentLineRenderMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    editor_set_current_line_render_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetGutterSticky(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean sticky
) {
    editor_set_gutter_sticky(static_cast<intptr_t>(editor_handle), sticky ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetGutterVisible(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean visible
) {
    editor_set_gutter_visible(static_cast<intptr_t>(editor_handle), visible ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetReadOnly(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean read_only
) {
    editor_set_read_only(static_cast<intptr_t>(editor_handle), read_only ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeIsReadOnly(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_is_read_only(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetCompositionEnabled(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean enabled
) {
    editor_set_composition_enabled(static_cast<intptr_t>(editor_handle), enabled ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeIsCompositionEnabled(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_is_composition_enabled(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetAutoIndentMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    editor_set_auto_indent_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetAutoIndentMode(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return static_cast<jint>(editor_get_auto_indent_mode(static_cast<intptr_t>(editor_handle)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetCursorPosition(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint line,
    jint column
) {
    editor_set_cursor_position(static_cast<intptr_t>(editor_handle), static_cast<size_t>(line), static_cast<size_t>(column));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetSelection(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint start_line,
    jint start_column,
    jint end_line,
    jint end_column
) {
    editor_set_selection(
        static_cast<intptr_t>(editor_handle),
        static_cast<size_t>(start_line),
        static_cast<size_t>(start_column),
        static_cast<size_t>(end_line),
        static_cast<size_t>(end_column)
    );
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetCursorPosition(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t line = 0;
    size_t column = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_get_cursor_position(static_cast<intptr_t>(editor_handle), &line, &column);
    jint values[2] = {
        static_cast<jint>(line),
        static_cast<jint>(column),
    };
    jintArray result = env->NewIntArray(2);
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetSelection(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t start_line = 0;
    size_t start_column = 0;
    size_t end_line = 0;
    size_t end_column = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const int has_selection = editor_get_selection(
        static_cast<intptr_t>(editor_handle),
        &start_line,
        &start_column,
        &end_line,
        &end_column
    );
    if (has_selection == 0) {
        return nullptr;
    }
    jint values[4] = {
        static_cast<jint>(start_line),
        static_cast<jint>(start_column),
        static_cast<jint>(end_line),
        static_cast<jint>(end_column),
    };
    jintArray result = env->NewIntArray(4);
    env->SetIntArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeBuildRenderModel(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = build_editor_render_model(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetScrollMetrics(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_get_scroll_metrics(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeHandleGesture(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jint type,
    jfloatArray points,
    jint modifiers,
    jfloat wheel_delta_x,
    jfloat wheel_delta_y,
    jfloat direct_scale
) {
    std::vector<float> point_values;
    if (points != nullptr) {
        const jsize size = env->GetArrayLength(points);
        point_values.resize(static_cast<size_t>(size));
        if (size > 0) {
            env->GetFloatArrayRegion(points, 0, size, point_values.data());
        }
    }
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = handle_editor_gesture_event_ex(
        static_cast<intptr_t>(editor_handle),
        static_cast<uint8_t>(type),
        static_cast<uint8_t>(point_values.size() / 2),
        point_values.empty() ? nullptr : point_values.data(),
        static_cast<uint8_t>(modifiers),
        wheel_delta_x,
        wheel_delta_y,
        direct_scale,
        &out_size
    );
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeTickAnimations(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_tick_animations(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeHandleKeyEvent(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jint key_code,
    jstring text,
    jint modifiers
) {
    const char* chars = text == nullptr ? nullptr : env->GetStringUTFChars(text, nullptr);
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = handle_editor_key_event(
        static_cast<intptr_t>(editor_handle),
        static_cast<uint16_t>(key_code),
        chars,
        static_cast<uint8_t>(modifiers),
        &out_size
    );
    if (text != nullptr) {
        env->ReleaseStringUTFChars(text, chars);
    }
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCompositionStart(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_composition_start(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCompositionUpdate(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jstring text
) {
    const char* chars = text == nullptr ? nullptr : env->GetStringUTFChars(text, nullptr);
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_composition_update(static_cast<intptr_t>(editor_handle), chars);
    if (text != nullptr) {
        env->ReleaseStringUTFChars(text, chars);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCompositionEnd(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jstring committed_text
) {
    const char* chars = committed_text == nullptr ? nullptr : env->GetStringUTFChars(committed_text, nullptr);
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_composition_end(static_cast<intptr_t>(editor_handle), chars, &out_size);
    if (committed_text != nullptr) {
        env->ReleaseStringUTFChars(committed_text, chars);
    }
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCompositionCancel(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_composition_cancel(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeIsComposing(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    return editor_is_composing(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeInsertText(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jstring text
) {
    const char* chars = text == nullptr ? nullptr : env->GetStringUTFChars(text, nullptr);
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_insert_text(static_cast<intptr_t>(editor_handle), chars, &out_size);
    if (text != nullptr) {
        env->ReleaseStringUTFChars(text, chars);
    }
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeReplaceText(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jint start_line,
    jint start_column,
    jint end_line,
    jint end_column,
    jstring text
) {
    const char* chars = text == nullptr ? nullptr : env->GetStringUTFChars(text, nullptr);
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_replace_text(
        static_cast<intptr_t>(editor_handle),
        static_cast<size_t>(start_line),
        static_cast<size_t>(start_column),
        static_cast<size_t>(end_line),
        static_cast<size_t>(end_column),
        chars,
        &out_size
    );
    if (text != nullptr) {
        env->ReleaseStringUTFChars(text, chars);
    }
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeDeleteText(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jint start_line,
    jint start_column,
    jint end_line,
    jint end_column
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_delete_text(
        static_cast<intptr_t>(editor_handle),
        static_cast<size_t>(start_line),
        static_cast<size_t>(start_column),
        static_cast<size_t>(end_line),
        static_cast<size_t>(end_column),
        &out_size
    );
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeBackspace(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_backspace(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeDeleteForward(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_delete_forward(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeInsertSnippet(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jstring template_text
) {
    if (template_text == nullptr) {
        return nullptr;
    }
    const char* chars = env->GetStringUTFChars(template_text, nullptr);
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_insert_snippet(static_cast<intptr_t>(editor_handle), chars, &out_size);
    env->ReleaseStringUTFChars(template_text, chars);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeStartLinkedEditing(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    if (bytes.empty()) {
        return;
    }
    editor_start_linked_editing(
        static_cast<intptr_t>(editor_handle),
        bytes.data(),
        bytes.size()
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeIsInLinkedEditing(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_is_in_linked_editing(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeLinkedEditingNext(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_linked_editing_next(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeLinkedEditingPrev(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_linked_editing_prev(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCancelLinkedEditing(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_cancel_linked_editing(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveLineUp(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_move_line_up(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveLineDown(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_move_line_down(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCopyLineUp(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_copy_line_up(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCopyLineDown(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_copy_line_down(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeDeleteLine(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_delete_line(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeInsertLineAbove(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_insert_line_above(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeInsertLineBelow(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_insert_line_below(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeUndo(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_undo(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeRedo(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t out_size = 0;
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    const uint8_t* data = editor_redo(static_cast<intptr_t>(editor_handle), &out_size);
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCanUndo(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_can_undo(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeCanRedo(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    return editor_can_redo(static_cast<intptr_t>(editor_handle)) != 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSelectAll(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_select_all(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetSelectedText(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    const char* text = editor_get_selected_text(static_cast<intptr_t>(editor_handle));
    return text == nullptr ? nullptr : env->NewStringUTF(text);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetWordRangeAtCursor(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    size_t start_line = 0;
    size_t start_column = 0;
    size_t end_line = 0;
    size_t end_column = 0;
    editor_get_word_range_at_cursor(
        static_cast<intptr_t>(editor_handle),
        &start_line,
        &start_column,
        &end_line,
        &end_column
    );
    jintArray result = env->NewIntArray(4);
    const jint values[4] = {
        static_cast<jint>(start_line),
        static_cast<jint>(start_column),
        static_cast<jint>(end_line),
        static_cast<jint>(end_column)
    };
    env->SetIntArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetWordAtCursor(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    const char* text = editor_get_word_at_cursor(static_cast<intptr_t>(editor_handle));
    return text == nullptr ? nullptr : env->NewStringUTF(text);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveCursorLeft(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean extend_selection
) {
    editor_move_cursor_left(static_cast<intptr_t>(editor_handle), extend_selection ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveCursorRight(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean extend_selection
) {
    editor_move_cursor_right(static_cast<intptr_t>(editor_handle), extend_selection ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveCursorUp(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean extend_selection
) {
    editor_move_cursor_up(static_cast<intptr_t>(editor_handle), extend_selection ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveCursorDown(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean extend_selection
) {
    editor_move_cursor_down(static_cast<intptr_t>(editor_handle), extend_selection ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveCursorToLineStart(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean extend_selection
) {
    editor_move_cursor_to_line_start(static_cast<intptr_t>(editor_handle), extend_selection ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeMoveCursorToLineEnd(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean extend_selection
) {
    editor_move_cursor_to_line_end(static_cast<intptr_t>(editor_handle), extend_selection ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeScrollToLine(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint line,
    jint behavior
) {
    editor_scroll_to_line(
        static_cast<intptr_t>(editor_handle),
        static_cast<size_t>(line),
        static_cast<uint8_t>(behavior)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGotoPosition(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint line,
    jint column
) {
    editor_goto_position(
        static_cast<intptr_t>(editor_handle),
        static_cast<size_t>(line),
        static_cast<size_t>(column)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetScroll(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jfloat scroll_x,
    jfloat scroll_y
) {
    editor_set_scroll(static_cast<intptr_t>(editor_handle), scroll_x, scroll_y);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetPositionRect(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jint line,
    jint column
) {
    float x = 0.0f;
    float y = 0.0f;
    float height = 0.0f;
    editor_get_position_rect(
        static_cast<intptr_t>(editor_handle),
        static_cast<size_t>(line),
        static_cast<size_t>(column),
        &x,
        &y,
        &height
    );
    jfloatArray result = env->NewFloatArray(3);
    const jfloat values[3] = {x, y, height};
    env->SetFloatArrayRegion(result, 0, 3, values);
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeGetCursorRect(
    JNIEnv* env,
    jclass,
    jlong editor_handle
) {
    float x = 0.0f;
    float y = 0.0f;
    float height = 0.0f;
    editor_get_cursor_rect(static_cast<intptr_t>(editor_handle), &x, &y, &height);
    jfloatArray result = env->NewFloatArray(3);
    const jfloat values[3] = {x, y, height};
    env->SetFloatArrayRegion(result, 0, 3, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeRegisterBatchTextStyles(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_register_batch_text_styles(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetBatchLineSpans(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_spans(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetBatchLineInlayHints(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_inlay_hints(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetBatchLinePhantomTexts(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_phantom_texts(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetBatchLineGutterIcons(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_gutter_icons(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetBatchLineDiagnostics(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_diagnostics(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeClearInlayHints(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_clear_inlay_hints(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeClearPhantomTexts(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_clear_phantom_texts(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeClearGutterIcons(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_clear_gutter_icons(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeClearDiagnostics(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_clear_diagnostics(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetIndentGuides(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_indent_guides(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetBracketGuides(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_bracket_guides(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetFlowGuides(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_flow_guides(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetSeparatorGuides(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_separator_guides(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeClearGuides(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_clear_guides(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetFoldRegions(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_fold_regions(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeClearAllDecorations(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    editor_clear_all_decorations(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_DesktopNativeBindings_nativeSetMaxGutterIcons(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint count
) {
    editor_set_max_gutter_icons(static_cast<intptr_t>(editor_handle), static_cast<uint32_t>(count));
}

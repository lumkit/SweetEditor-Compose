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
    if (g_java_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        return env;
    }
    if (g_java_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
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
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeCreateDocumentFromUtf16(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeCreateDocumentFromFile(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeFreeDocument(
    JNIEnv*,
    jclass,
    jlong handle
) {
    if (handle != 0) {
        free_document(static_cast<intptr_t>(handle));
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeCreateEditor(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeFreeEditor(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetEditorDocument(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jlong document_handle
) {
    set_editor_document(static_cast<intptr_t>(editor_handle), static_cast<intptr_t>(document_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetEditorViewport(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint width,
    jint height
) {
    set_editor_viewport(static_cast<intptr_t>(editor_handle), static_cast<int16_t>(width), static_cast<int16_t>(height));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeOnFontMetricsChanged(
    JNIEnv*,
    jclass,
    jlong editor_handle
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_on_font_metrics_changed(static_cast<intptr_t>(editor_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetFoldArrowMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    editor_set_fold_arrow_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetWrapMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_wrap_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetTabSize(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint tab_size
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_tab_size(static_cast<intptr_t>(editor_handle), tab_size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetScale(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jfloat scale
) {
    ScopedCurrentEditorHandle current(static_cast<intptr_t>(editor_handle));
    editor_set_scale(static_cast<intptr_t>(editor_handle), scale);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetLineSpacing(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetShowSplitLine(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean show
) {
    editor_set_show_split_line(static_cast<intptr_t>(editor_handle), show ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetCurrentLineRenderMode(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint mode
) {
    editor_set_current_line_render_mode(static_cast<intptr_t>(editor_handle), mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetGutterSticky(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean sticky
) {
    editor_set_gutter_sticky(static_cast<intptr_t>(editor_handle), sticky ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetGutterVisible(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean visible
) {
    editor_set_gutter_visible(static_cast<intptr_t>(editor_handle), visible ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetReadOnly(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean read_only
) {
    editor_set_read_only(static_cast<intptr_t>(editor_handle), read_only ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetCompositionEnabled(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jboolean enabled
) {
    editor_set_composition_enabled(static_cast<intptr_t>(editor_handle), enabled ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetCursorPosition(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint line,
    jint column
) {
    editor_set_cursor_position(static_cast<intptr_t>(editor_handle), static_cast<size_t>(line), static_cast<size_t>(column));
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetSelection(
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

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeBuildRenderModel(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeGetScrollMetrics(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeHandleGesture(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jint type,
    jfloatArray points
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
    const uint8_t* data = handle_editor_gesture_event(
        static_cast<intptr_t>(editor_handle),
        static_cast<uint8_t>(type),
        static_cast<uint8_t>(point_values.size() / 2),
        point_values.empty() ? nullptr : point_values.data(),
        &out_size
    );
    return copy_binary_payload(env, data, out_size);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeHandleKeyEvent(
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

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeInsertText(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeReplaceText(
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
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeDeleteText(
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

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeRegisterBatchTextStyles(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_register_batch_text_styles(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetBatchLineSpans(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_spans(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetBatchLineInlayHints(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_inlay_hints(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetBatchLinePhantomTexts(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_phantom_texts(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetBatchLineGutterIcons(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_gutter_icons(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetBatchLineDiagnostics(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_batch_line_diagnostics(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetFoldRegions(
    JNIEnv* env,
    jclass,
    jlong editor_handle,
    jbyteArray data
) {
    const std::vector<uint8_t> bytes = read_byte_array(env, data);
    editor_set_fold_regions(static_cast<intptr_t>(editor_handle), bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qiplat_compose_sweeteditor_bridge_AndroidNativeBindings_nativeSetMaxGutterIcons(
    JNIEnv*,
    jclass,
    jlong editor_handle,
    jint count
) {
    editor_set_max_gutter_icons(static_cast<intptr_t>(editor_handle), static_cast<uint32_t>(count));
}

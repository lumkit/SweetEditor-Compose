#include "dll_loader.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

SweetEditorDllLoader& SweetEditorDllLoader::instance() {
    static SweetEditorDllLoader instance;
    return instance;
}

SweetEditorDllLoader::~SweetEditorDllLoader() {
    unload();
}

bool SweetEditorDllLoader::load(const char* dll_path) {
    if (is_loaded()) {
        return true;
    }
    
#ifdef _WIN32
    dll_handle_ = LoadLibraryA(dll_path);
#else
    dll_handle_ = dlopen(dll_path, RTLD_NOW);
#endif
    
    if (!dll_handle_) {
        return false;
    }
    
#define GET_PROC(name) \
    name = reinterpret_cast<decltype(name)>(GetProcAddress(dll_handle_, #name)); \
    if (!name) { \
        unload(); \
        return false; \
    }
    
    // Load all editor functions
    GET_PROC(create_editor);
    GET_PROC(free_editor);
    GET_PROC(set_editor_document);
    GET_PROC(set_editor_viewport);
    GET_PROC(editor_on_font_metrics_changed);
    GET_PROC(editor_set_fold_arrow_mode);
    GET_PROC(editor_set_wrap_mode);
    GET_PROC(editor_set_tab_size);
    GET_PROC(editor_set_scale);
    GET_PROC(editor_set_line_spacing);
    GET_PROC(editor_set_show_split_line);
    GET_PROC(editor_set_current_line_render_mode);
    GET_PROC(editor_set_gutter_sticky);
    GET_PROC(editor_set_gutter_visible);
    GET_PROC(editor_set_read_only);
    GET_PROC(editor_is_read_only);
    GET_PROC(editor_set_composition_enabled);
    GET_PROC(editor_is_composition_enabled);
    GET_PROC(editor_set_auto_indent_mode);
    GET_PROC(editor_get_auto_indent_mode);
    GET_PROC(editor_set_cursor_position);
    GET_PROC(editor_set_selection);
    GET_PROC(editor_get_selection);
    GET_PROC(editor_get_cursor_position);
    GET_PROC(build_editor_render_model);
    GET_PROC(editor_get_scroll_metrics);
    GET_PROC(handle_editor_gesture_event_ex);
    GET_PROC(editor_tick_animations);
    GET_PROC(handle_editor_key_event);
    GET_PROC(editor_composition_start);
    GET_PROC(editor_composition_update);
    GET_PROC(editor_composition_end);
    GET_PROC(editor_composition_cancel);
    GET_PROC(editor_is_composing);
    GET_PROC(editor_insert_text);
    GET_PROC(editor_replace_text);
    GET_PROC(editor_delete_text);
    GET_PROC(editor_backspace);
    GET_PROC(editor_delete_forward);
    GET_PROC(editor_insert_snippet);
    GET_PROC(editor_start_linked_editing);
    GET_PROC(editor_is_in_linked_editing);
    GET_PROC(editor_linked_editing_next);
    GET_PROC(editor_linked_editing_prev);
    GET_PROC(editor_cancel_linked_editing);
    GET_PROC(editor_move_line_up);
    GET_PROC(editor_move_line_down);
    GET_PROC(editor_copy_line_up);
    GET_PROC(editor_copy_line_down);
    GET_PROC(editor_delete_line);
    GET_PROC(editor_insert_line_above);
    GET_PROC(editor_insert_line_below);
    GET_PROC(editor_undo);
    GET_PROC(editor_redo);
    GET_PROC(editor_can_undo);
    GET_PROC(editor_can_redo);
    GET_PROC(editor_select_all);
    GET_PROC(editor_get_selected_text);
    GET_PROC(editor_get_word_range_at_cursor);
    GET_PROC(editor_get_word_at_cursor);
    GET_PROC(editor_move_cursor_left);
    GET_PROC(editor_move_cursor_right);
    GET_PROC(editor_move_cursor_up);
    GET_PROC(editor_move_cursor_down);
    GET_PROC(editor_move_cursor_to_line_start);
    GET_PROC(editor_move_cursor_to_line_end);
    GET_PROC(editor_scroll_to_line);
    GET_PROC(editor_goto_position);
    GET_PROC(editor_set_scroll);
    GET_PROC(editor_get_position_rect);
    GET_PROC(editor_get_cursor_rect);
    GET_PROC(editor_register_batch_text_styles);
    GET_PROC(editor_set_batch_line_spans);
    GET_PROC(editor_set_batch_line_inlay_hints);
    GET_PROC(editor_set_batch_line_phantom_texts);
    GET_PROC(editor_set_batch_line_gutter_icons);
    GET_PROC(editor_set_batch_line_diagnostics);
    GET_PROC(editor_clear_inlay_hints);
    GET_PROC(editor_clear_phantom_texts);
    GET_PROC(editor_clear_gutter_icons);
    GET_PROC(editor_clear_diagnostics);
    GET_PROC(editor_set_indent_guides);
    GET_PROC(editor_set_bracket_guides);
    GET_PROC(editor_set_flow_guides);
    GET_PROC(editor_set_separator_guides);
    GET_PROC(editor_clear_guides);
    GET_PROC(editor_set_fold_regions);
    GET_PROC(editor_clear_all_decorations);
    GET_PROC(editor_set_max_gutter_icons);
    
    // Load document functions
    GET_PROC(create_document_from_utf16);
    GET_PROC(create_document_from_file);
    GET_PROC(free_document);
    GET_PROC(get_document_line_count);
    GET_PROC(get_document_line_utf16);
    GET_PROC(free_binary_data);
    GET_PROC(free_u16_string);
    
#undef GET_PROC
    
    return true;
}

void SweetEditorDllLoader::unload() {
    if (dll_handle_) {
#ifdef _WIN32
        FreeLibrary(dll_handle_);
#else
        dlclose(dll_handle_);
#endif
        dll_handle_ = nullptr;
    }
    
    // Clear all function pointers
    create_editor = nullptr;
    free_editor = nullptr;
    set_editor_document = nullptr;
    set_editor_viewport = nullptr;
    editor_on_font_metrics_changed = nullptr;
    editor_set_fold_arrow_mode = nullptr;
    editor_set_wrap_mode = nullptr;
    editor_set_tab_size = nullptr;
    editor_set_scale = nullptr;
    editor_set_line_spacing = nullptr;
    editor_set_show_split_line = nullptr;
    editor_set_current_line_render_mode = nullptr;
    editor_set_gutter_sticky = nullptr;
    editor_set_gutter_visible = nullptr;
    editor_set_read_only = nullptr;
    editor_is_read_only = nullptr;
    editor_set_composition_enabled = nullptr;
    editor_is_composition_enabled = nullptr;
    editor_set_auto_indent_mode = nullptr;
    editor_get_auto_indent_mode = nullptr;
    editor_set_cursor_position = nullptr;
    editor_set_selection = nullptr;
    editor_get_selection = nullptr;
    editor_get_cursor_position = nullptr;
    build_editor_render_model = nullptr;
    editor_get_scroll_metrics = nullptr;
    handle_editor_gesture_event_ex = nullptr;
    editor_tick_animations = nullptr;
    handle_editor_key_event = nullptr;
    editor_composition_start = nullptr;
    editor_composition_update = nullptr;
    editor_composition_end = nullptr;
    editor_composition_cancel = nullptr;
    editor_is_composing = nullptr;
    editor_insert_text = nullptr;
    editor_replace_text = nullptr;
    editor_delete_text = nullptr;
    editor_backspace = nullptr;
    editor_delete_forward = nullptr;
    editor_insert_snippet = nullptr;
    editor_start_linked_editing = nullptr;
    editor_is_in_linked_editing = nullptr;
    editor_linked_editing_next = nullptr;
    editor_linked_editing_prev = nullptr;
    editor_cancel_linked_editing = nullptr;
    editor_move_line_up = nullptr;
    editor_move_line_down = nullptr;
    editor_copy_line_up = nullptr;
    editor_copy_line_down = nullptr;
    editor_delete_line = nullptr;
    editor_insert_line_above = nullptr;
    editor_insert_line_below = nullptr;
    editor_undo = nullptr;
    editor_redo = nullptr;
    editor_can_undo = nullptr;
    editor_can_redo = nullptr;
    editor_select_all = nullptr;
    editor_get_selected_text = nullptr;
    editor_get_word_range_at_cursor = nullptr;
    editor_get_word_at_cursor = nullptr;
    editor_move_cursor_left = nullptr;
    editor_move_cursor_right = nullptr;
    editor_move_cursor_up = nullptr;
    editor_move_cursor_down = nullptr;
    editor_move_cursor_to_line_start = nullptr;
    editor_move_cursor_to_line_end = nullptr;
    editor_scroll_to_line = nullptr;
    editor_goto_position = nullptr;
    editor_set_scroll = nullptr;
    editor_get_position_rect = nullptr;
    editor_get_cursor_rect = nullptr;
    editor_register_batch_text_styles = nullptr;
    editor_set_batch_line_spans = nullptr;
    editor_set_batch_line_inlay_hints = nullptr;
    editor_set_batch_line_phantom_texts = nullptr;
    editor_set_batch_line_gutter_icons = nullptr;
    editor_set_batch_line_diagnostics = nullptr;
    editor_clear_inlay_hints = nullptr;
    editor_clear_phantom_texts = nullptr;
    editor_clear_gutter_icons = nullptr;
    editor_clear_diagnostics = nullptr;
    editor_set_indent_guides = nullptr;
    editor_set_bracket_guides = nullptr;
    editor_set_flow_guides = nullptr;
    editor_set_separator_guides = nullptr;
    editor_clear_guides = nullptr;
    editor_set_fold_regions = nullptr;
    editor_clear_all_decorations = nullptr;
    editor_set_max_gutter_icons = nullptr;
    create_document_from_utf16 = nullptr;
    create_document_from_file = nullptr;
    free_document = nullptr;
    get_document_line_count = nullptr;
    get_document_line_utf16 = nullptr;
    free_binary_data = nullptr;
    free_u16_string = nullptr;
}

bool SweetEditorDllLoader::is_loaded() const {
    return dll_handle_ != nullptr;
}

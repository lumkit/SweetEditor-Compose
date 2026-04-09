#pragma once

#ifdef _WIN32
#include <windows.h>
#endif

#include <cstdint>
#include "c_api.h"

// Function pointer types for all exported functions
typedef intptr_t (*CreateEditorFunc)(text_measurer_t measurer, const uint8_t* options_data, size_t options_size);
typedef void (*FreeEditorFunc)(intptr_t editor_handle);
typedef void (*SetEditorDocumentFunc)(intptr_t editor_handle, intptr_t document_handle);
typedef void (*SetEditorViewportFunc)(intptr_t editor_handle, int16_t width, int16_t height);
typedef void (*EditorOnFontMetricsChangedFunc)(intptr_t editor_handle);
typedef void (*EditorSetFoldArrowModeFunc)(intptr_t editor_handle, int32_t mode);
typedef void (*EditorSetWrapModeFunc)(intptr_t editor_handle, int32_t mode);
typedef void (*EditorSetTabSizeFunc)(intptr_t editor_handle, int32_t tab_size);
typedef void (*EditorSetScaleFunc)(intptr_t editor_handle, float scale);
typedef void (*EditorSetLineSpacingFunc)(intptr_t editor_handle, float add, float mult);
typedef void (*EditorSetShowSplitLineFunc)(intptr_t editor_handle, int32_t show);
typedef void (*EditorSetCurrentLineRenderModeFunc)(intptr_t editor_handle, int32_t mode);
typedef void (*EditorSetGutterStickyFunc)(intptr_t editor_handle, int32_t sticky);
typedef void (*EditorSetGutterVisibleFunc)(intptr_t editor_handle, int32_t visible);
typedef void (*EditorSetReadOnlyFunc)(intptr_t editor_handle, int32_t read_only);
typedef int32_t (*EditorIsReadOnlyFunc)(intptr_t editor_handle);
typedef void (*EditorSetCompositionEnabledFunc)(intptr_t editor_handle, int32_t enabled);
typedef int32_t (*EditorIsCompositionEnabledFunc)(intptr_t editor_handle);
typedef void (*EditorSetAutoIndentModeFunc)(intptr_t editor_handle, int32_t mode);
typedef int32_t (*EditorGetAutoIndentModeFunc)(intptr_t editor_handle);
typedef void (*EditorSetCursorPositionFunc)(intptr_t editor_handle, size_t line, size_t column);
typedef void (*EditorSetSelectionFunc)(intptr_t editor_handle, size_t start_line, size_t start_column, size_t end_line, size_t end_column);
typedef int32_t (*EditorGetSelectionFunc)(intptr_t editor_handle, size_t* start_line, size_t* start_column, size_t* end_line, size_t* end_column);
typedef void (*EditorGetCursorPositionFunc)(intptr_t editor_handle, size_t* line, size_t* column);
typedef const uint8_t* (*BuildEditorRenderModelFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorGetScrollMetricsFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*HandleEditorGestureEventFunc)(intptr_t editor_handle, uint8_t type, uint8_t point_count, const float* points, uint8_t modifiers, float wheel_delta_x, float wheel_delta_y, float direct_scale, size_t* out_size);
typedef const uint8_t* (*HandleEditorGestureEventExFunc)(intptr_t editor_handle, uint8_t type, uint8_t point_count, const float* points, uint8_t modifiers, float wheel_delta_x, float wheel_delta_y, float direct_scale, size_t* out_size);
typedef const uint8_t* (*EditorTickAnimationsFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*HandleEditorKeyEventFunc)(intptr_t editor_handle, uint16_t key_code, const char* text, uint8_t modifiers, size_t* out_size);
typedef void (*EditorCompositionStartFunc)(intptr_t editor_handle);
typedef void (*EditorCompositionUpdateFunc)(intptr_t editor_handle, const char* text);
typedef const uint8_t* (*EditorCompositionEndFunc)(intptr_t editor_handle, const char* committed_text, size_t* out_size);
typedef void (*EditorCompositionCancelFunc)(intptr_t editor_handle);
typedef int32_t (*EditorIsComposingFunc)(intptr_t editor_handle);
typedef const uint8_t* (*EditorInsertTextFunc)(intptr_t editor_handle, const char* text, size_t* out_size);
typedef const uint8_t* (*EditorReplaceTextFunc)(intptr_t editor_handle, size_t start_line, size_t start_column, size_t end_line, size_t end_column, const char* text, size_t* out_size);
typedef const uint8_t* (*EditorDeleteTextFunc)(intptr_t editor_handle, size_t start_line, size_t start_column, size_t end_line, size_t end_column, size_t* out_size);
typedef const uint8_t* (*EditorBackspaceFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorDeleteForwardFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorInsertSnippetFunc)(intptr_t editor_handle, const char* template_text, size_t* out_size);
typedef void (*EditorStartLinkedEditingFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef int32_t (*EditorIsInLinkedEditingFunc)(intptr_t editor_handle);
typedef int32_t (*EditorLinkedEditingNextFunc)(intptr_t editor_handle);
typedef int32_t (*EditorLinkedEditingPrevFunc)(intptr_t editor_handle);
typedef void (*EditorCancelLinkedEditingFunc)(intptr_t editor_handle);
typedef const uint8_t* (*EditorMoveLineUpFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorMoveLineDownFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorCopyLineUpFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorCopyLineDownFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorDeleteLineFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorInsertLineAboveFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorInsertLineBelowFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorUndoFunc)(intptr_t editor_handle, size_t* out_size);
typedef const uint8_t* (*EditorRedoFunc)(intptr_t editor_handle, size_t* out_size);
typedef int32_t (*EditorCanUndoFunc)(intptr_t editor_handle);
typedef int32_t (*EditorCanRedoFunc)(intptr_t editor_handle);
typedef void (*EditorSelectAllFunc)(intptr_t editor_handle);
typedef const char* (*EditorGetSelectedTextFunc)(intptr_t editor_handle);
typedef void (*EditorGetWordRangeAtCursorFunc)(intptr_t editor_handle, size_t* start_line, size_t* start_column, size_t* end_line, size_t* end_column);
typedef const char* (*EditorGetWordAtCursorFunc)(intptr_t editor_handle);
typedef void (*EditorMoveCursorLeftFunc)(intptr_t editor_handle, int32_t extend_selection);
typedef void (*EditorMoveCursorRightFunc)(intptr_t editor_handle, int32_t extend_selection);
typedef void (*EditorMoveCursorUpFunc)(intptr_t editor_handle, int32_t extend_selection);
typedef void (*EditorMoveCursorDownFunc)(intptr_t editor_handle, int32_t extend_selection);
typedef void (*EditorMoveCursorToLineStartFunc)(intptr_t editor_handle, int32_t extend_selection);
typedef void (*EditorMoveCursorToLineEndFunc)(intptr_t editor_handle, int32_t extend_selection);
typedef void (*EditorScrollToLineFunc)(intptr_t editor_handle, size_t line, int32_t behavior);
typedef void (*EditorGotoPositionFunc)(intptr_t editor_handle, size_t line, size_t column);
typedef void (*EditorSetScrollFunc)(intptr_t editor_handle, float scroll_x, float scroll_y);
typedef void (*EditorGetPositionRectFunc)(intptr_t editor_handle, size_t line, size_t column, float* out_x, float* out_y, float* out_height);
typedef void (*EditorGetCursorRectFunc)(intptr_t editor_handle, float* out_x, float* out_y, float* out_height);
typedef void (*EditorRegisterBatchTextStylesFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetBatchLineSpansFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetBatchLineInlayHintsFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetBatchLinePhantomTextsFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetBatchLineGutterIconsFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetBatchLineDiagnosticsFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorClearInlayHintsFunc)(intptr_t editor_handle);
typedef void (*EditorClearPhantomTextsFunc)(intptr_t editor_handle);
typedef void (*EditorClearGutterIconsFunc)(intptr_t editor_handle);
typedef void (*EditorClearDiagnosticsFunc)(intptr_t editor_handle);
typedef void (*EditorSetIndentGuidesFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetBracketGuidesFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetFlowGuidesFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorSetSeparatorGuidesFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorClearGuidesFunc)(intptr_t editor_handle);
typedef void (*EditorSetFoldRegionsFunc)(intptr_t editor_handle, const uint8_t* data, size_t data_size);
typedef void (*EditorClearAllDecorationsFunc)(intptr_t editor_handle);
typedef void (*EditorSetMaxGutterIconsFunc)(intptr_t editor_handle, int32_t count);

// Document functions
typedef intptr_t (*CreateDocumentFromUtf16Func)(const U16Char* text);
typedef intptr_t (*CreateDocumentFromFileFunc)(const char* path);
typedef void (*FreeDocumentFunc)(intptr_t document_handle);
typedef size_t (*GetDocumentLineCountFunc)(intptr_t document_handle);
typedef const U16Char* (*GetDocumentLineUtf16Func)(intptr_t document_handle, size_t line);
typedef void (*FreeBinaryDataFunc)(intptr_t data);
typedef void (*FreeU16StringFunc)(U16Char* str);

// DLL Loader class
class SweetEditorDllLoader {
public:
    static SweetEditorDllLoader& instance();
    
    bool load(const char* dll_path);
    void unload();
    bool is_loaded() const;
    
    // Function accessors
    CreateEditorFunc create_editor = nullptr;
    FreeEditorFunc free_editor = nullptr;
    SetEditorDocumentFunc set_editor_document = nullptr;
    SetEditorViewportFunc set_editor_viewport = nullptr;
    EditorOnFontMetricsChangedFunc editor_on_font_metrics_changed = nullptr;
    EditorSetFoldArrowModeFunc editor_set_fold_arrow_mode = nullptr;
    EditorSetWrapModeFunc editor_set_wrap_mode = nullptr;
    EditorSetTabSizeFunc editor_set_tab_size = nullptr;
    EditorSetScaleFunc editor_set_scale = nullptr;
    EditorSetLineSpacingFunc editor_set_line_spacing = nullptr;
    EditorSetShowSplitLineFunc editor_set_show_split_line = nullptr;
    EditorSetCurrentLineRenderModeFunc editor_set_current_line_render_mode = nullptr;
    EditorSetGutterStickyFunc editor_set_gutter_sticky = nullptr;
    EditorSetGutterVisibleFunc editor_set_gutter_visible = nullptr;
    EditorSetReadOnlyFunc editor_set_read_only = nullptr;
    EditorIsReadOnlyFunc editor_is_read_only = nullptr;
    EditorSetCompositionEnabledFunc editor_set_composition_enabled = nullptr;
    EditorIsCompositionEnabledFunc editor_is_composition_enabled = nullptr;
    EditorSetAutoIndentModeFunc editor_set_auto_indent_mode = nullptr;
    EditorGetAutoIndentModeFunc editor_get_auto_indent_mode = nullptr;
    EditorSetCursorPositionFunc editor_set_cursor_position = nullptr;
    EditorSetSelectionFunc editor_set_selection = nullptr;
    EditorGetSelectionFunc editor_get_selection = nullptr;
    EditorGetCursorPositionFunc editor_get_cursor_position = nullptr;
    BuildEditorRenderModelFunc build_editor_render_model = nullptr;
    EditorGetScrollMetricsFunc editor_get_scroll_metrics = nullptr;
    HandleEditorGestureEventExFunc handle_editor_gesture_event_ex = nullptr;
    EditorTickAnimationsFunc editor_tick_animations = nullptr;
    HandleEditorKeyEventFunc handle_editor_key_event = nullptr;
    EditorCompositionStartFunc editor_composition_start = nullptr;
    EditorCompositionUpdateFunc editor_composition_update = nullptr;
    EditorCompositionEndFunc editor_composition_end = nullptr;
    EditorCompositionCancelFunc editor_composition_cancel = nullptr;
    EditorIsComposingFunc editor_is_composing = nullptr;
    EditorInsertTextFunc editor_insert_text = nullptr;
    EditorReplaceTextFunc editor_replace_text = nullptr;
    EditorDeleteTextFunc editor_delete_text = nullptr;
    EditorBackspaceFunc editor_backspace = nullptr;
    EditorDeleteForwardFunc editor_delete_forward = nullptr;
    EditorInsertSnippetFunc editor_insert_snippet = nullptr;
    EditorStartLinkedEditingFunc editor_start_linked_editing = nullptr;
    EditorIsInLinkedEditingFunc editor_is_in_linked_editing = nullptr;
    EditorLinkedEditingNextFunc editor_linked_editing_next = nullptr;
    EditorLinkedEditingPrevFunc editor_linked_editing_prev = nullptr;
    EditorCancelLinkedEditingFunc editor_cancel_linked_editing = nullptr;
    EditorMoveLineUpFunc editor_move_line_up = nullptr;
    EditorMoveLineDownFunc editor_move_line_down = nullptr;
    EditorCopyLineUpFunc editor_copy_line_up = nullptr;
    EditorCopyLineDownFunc editor_copy_line_down = nullptr;
    EditorDeleteLineFunc editor_delete_line = nullptr;
    EditorInsertLineAboveFunc editor_insert_line_above = nullptr;
    EditorInsertLineBelowFunc editor_insert_line_below = nullptr;
    EditorUndoFunc editor_undo = nullptr;
    EditorRedoFunc editor_redo = nullptr;
    EditorCanUndoFunc editor_can_undo = nullptr;
    EditorCanRedoFunc editor_can_redo = nullptr;
    EditorSelectAllFunc editor_select_all = nullptr;
    EditorGetSelectedTextFunc editor_get_selected_text = nullptr;
    EditorGetWordRangeAtCursorFunc editor_get_word_range_at_cursor = nullptr;
    EditorGetWordAtCursorFunc editor_get_word_at_cursor = nullptr;
    EditorMoveCursorLeftFunc editor_move_cursor_left = nullptr;
    EditorMoveCursorRightFunc editor_move_cursor_right = nullptr;
    EditorMoveCursorUpFunc editor_move_cursor_up = nullptr;
    EditorMoveCursorDownFunc editor_move_cursor_down = nullptr;
    EditorMoveCursorToLineStartFunc editor_move_cursor_to_line_start = nullptr;
    EditorMoveCursorToLineEndFunc editor_move_cursor_to_line_end = nullptr;
    EditorScrollToLineFunc editor_scroll_to_line = nullptr;
    EditorGotoPositionFunc editor_goto_position = nullptr;
    EditorSetScrollFunc editor_set_scroll = nullptr;
    EditorGetPositionRectFunc editor_get_position_rect = nullptr;
    EditorGetCursorRectFunc editor_get_cursor_rect = nullptr;
    EditorRegisterBatchTextStylesFunc editor_register_batch_text_styles = nullptr;
    EditorSetBatchLineSpansFunc editor_set_batch_line_spans = nullptr;
    EditorSetBatchLineInlayHintsFunc editor_set_batch_line_inlay_hints = nullptr;
    EditorSetBatchLinePhantomTextsFunc editor_set_batch_line_phantom_texts = nullptr;
    EditorSetBatchLineGutterIconsFunc editor_set_batch_line_gutter_icons = nullptr;
    EditorSetBatchLineDiagnosticsFunc editor_set_batch_line_diagnostics = nullptr;
    EditorClearInlayHintsFunc editor_clear_inlay_hints = nullptr;
    EditorClearPhantomTextsFunc editor_clear_phantom_texts = nullptr;
    EditorClearGutterIconsFunc editor_clear_gutter_icons = nullptr;
    EditorClearDiagnosticsFunc editor_clear_diagnostics = nullptr;
    EditorSetIndentGuidesFunc editor_set_indent_guides = nullptr;
    EditorSetBracketGuidesFunc editor_set_bracket_guides = nullptr;
    EditorSetFlowGuidesFunc editor_set_flow_guides = nullptr;
    EditorSetSeparatorGuidesFunc editor_set_separator_guides = nullptr;
    EditorClearGuidesFunc editor_clear_guides = nullptr;
    EditorSetFoldRegionsFunc editor_set_fold_regions = nullptr;
    EditorClearAllDecorationsFunc editor_clear_all_decorations = nullptr;
    EditorSetMaxGutterIconsFunc editor_set_max_gutter_icons = nullptr;
    
    // Document functions
    CreateDocumentFromUtf16Func create_document_from_utf16 = nullptr;
    CreateDocumentFromFileFunc create_document_from_file = nullptr;
    FreeDocumentFunc free_document = nullptr;
    GetDocumentLineCountFunc get_document_line_count = nullptr;
    GetDocumentLineUtf16Func get_document_line_utf16 = nullptr;
    FreeBinaryDataFunc free_binary_data = nullptr;
    FreeU16StringFunc free_u16_string = nullptr;

private:
    SweetEditorDllLoader() = default;
    ~SweetEditorDllLoader();
    
    SweetEditorDllLoader(const SweetEditorDllLoader&) = delete;
    SweetEditorDllLoader& operator=(const SweetEditorDllLoader&) = delete;
    
#ifdef _WIN32
    HMODULE dll_handle_ = nullptr;
#else
    void* dll_handle_ = nullptr;
#endif
};

// Macro to simplify function loading
#define LOAD_FUNC(loader, name) \
    (loader).name = reinterpret_cast<decltype((loader).name)>(GetProcAddress((loader).dll_handle_, #name)); \
    if (!(loader).name) { \
        return false; \
    }

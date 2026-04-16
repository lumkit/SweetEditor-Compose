//
// Created by Scave on 2025/12/2.
//
#ifndef SWEETEDITOR_FOUNDATION_H
#define SWEETEDITOR_FOUNDATION_H

#include <cstdint>
#include "macro.h"
#include "keymap.h"

namespace NS_SWEETEDITOR {
  /// Text position
  struct TextPosition {
    /// Line index, starting from 0
    size_t line {0};
    /// Column index, starting from 0
    size_t column {0};

    bool operator<(const TextPosition& other) const;
    bool operator==(const TextPosition& other) const;
    bool operator!=(const TextPosition& other) const;
    U8String dump() const;
  };

  /// Text range
  struct TextRange {
    TextPosition start;
    TextPosition end;

    bool operator==(const TextRange& other) const;
    bool contains(const TextPosition& pos) const;
    U8String dump() const;
  };

  /// 2D coordinate wrapper
  struct PointF {
    float x {0};
    float y {0};

    float distance(const PointF& other) const;
    U8String dump() const;
  };

  /// Axis-aligned rectangle (origin + size)
  struct Rect {
    PointF origin;
    float width {0};
    float height {0};
  };

  /// Offset rectangle relative to a reference point
  struct OffsetRect {
    float left {0};
    float top {0};
    float right {0};
    float bottom {0};

    bool contains(float dx, float dy) const;
  };

  /// Editor viewport
  struct Viewport {
    /// Editor width
    float width {0};
    /// Editor height
    float height {0};

    bool valid() const;
    U8String dump() const;
  };

  /// Editor view state
  struct ViewState {
    /// Scale factor
    float scale {1};
    /// Horizontal scroll offset
    float scroll_x {0};
    /// Vertical scroll offset
    float scroll_y {0};

    U8String dump() const;
  };

  /// Keyboard event data
  struct KeyEvent {
    /// Key code
    KeyCode key_code {KeyCode::NONE};
    /// Input text (used for regular character input, such as letters, numbers, symbols)
    U8String text;
    /// Modifier key state
    KeyModifier modifiers {KeyModifier::NONE};

    /// Whether this is plain text input (no special key code, text only)
    bool isTextInput() const;
  };

  enum struct ScrollBehavior {
    /// Make the target line visible at the top
    GOTO_TOP,
    /// Scroll the target line to the center
    GOTO_CENTER,
    /// Scroll the target line to the bottom
    GOTO_BOTTOM,
  };

  /// Unified caret state: cursor position + selection
  struct CaretState {
    /// Logical cursor position in text
    TextPosition cursor;
    /// Selection range (start is anchor, end is active end / cursor side)
    TextRange selection;
    /// Whether there is an active selection
    bool has_selection {false};

    void setSelection(const TextRange& range);
    void clearSelection();
    TextRange normalizedSelection() const;
  };

  /// Auto-indent modes
  enum struct AutoIndentMode {
    /// No auto-indent; new line starts at column 0
    NONE = 0,
    /// Keep previous line indent (copy leading whitespace)
    KEEP_INDENT = 1,
  };

  /// Auto-wrap modes
  enum struct WrapMode {
    /// No wrapping
    NONE,
    /// Character-level wrapping
    CHAR_BREAK,
    /// Word-level wrapping
    WORD_BREAK,
  };

  /// Current line render modes
  enum struct CurrentLineRenderMode {
    /// Fill full line background
    BACKGROUND = 0,
    /// Draw line border only
    BORDER = 1,
    /// Disable current-line decoration
    NONE = 2,
  };
}

#endif //SWEETEDITOR_FOUNDATION_H

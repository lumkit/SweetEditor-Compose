-keep class com.qiplat.compose.sweeteditor.bridge.AndroidNativeBindings { *; }
-keep interface com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer
-keepclassmembers class * implements com.qiplat.compose.sweeteditor.bridge.NativeTextMeasurer {
    public float measureTextWidth(java.lang.String, int);
    public float measureInlayHintWidth(java.lang.String);
    public float measureIconWidth(int);
    public float[] getFontMetrics();
}

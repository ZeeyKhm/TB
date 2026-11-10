# Media3 / ExoPlayer is consumed as a library and does not need extra rules.
# Keep line numbers so playback errors remain readable in crash reports.
-keepattributes SourceFile,LineNumberTable

# Keep class names readable; R8 still shrinks and optimises the release build.
-dontobfuscate

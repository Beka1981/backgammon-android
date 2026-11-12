# R8 processes the instrumentation APK against Android, which has no javac model.
# This type is referenced only by Error Prone's compile-time annotation metadata.
# Keep production shrinking checks unchanged.
-dontwarn javax.lang.model.element.Modifier
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keep class ge.gogichaishvili.backgammon.GamePerformanceTest { *; }
-keep class ge.gogichaishvili.backgammon.ProcessPersistenceTest { *; }
-keep class ge.gogichaishvili.backgammon.ProfileDrawingMeasurementTest { *; }

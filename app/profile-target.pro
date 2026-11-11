# Instrumentation runner references this API from the target APK at runtime.
# Keep it in the measurement variant after app shrinking; production release is unchanged.
-keep class androidx.tracing.Trace { *; }
# The in-process measurement harness awaits repository calls; retain this otherwise unused facade.
-keep,allowoptimization class kotlinx.coroutines.BuildersKt { *; }
-keep,allowoptimization class androidx.lifecycle.SavedStateHandle { *; }
-keep,allowoptimization class androidx.lifecycle.ViewModelStore { *; }

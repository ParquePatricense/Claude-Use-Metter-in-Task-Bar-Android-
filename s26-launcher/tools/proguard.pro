# Reglas ProGuard para Inicio S26 (release).
-dontobfuscate
-optimizationpasses 3
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable
-allowaccessmodification
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
-dontnote **

# Puntos de entrada declarados en el manifest.
-keep class com.s26home.launcher.LauncherApp { <init>(); }
-keep class com.s26home.launcher.** extends android.app.Activity { <init>(); }
-keep class com.s26home.launcher.** extends android.app.Service { <init>(); }
-keep class com.s26home.launcher.R* { *; }

# Vistas creadas por el sistema (AppWidgetHost) y métodos llamados por reflexión del framework.
-keepclassmembers class * extends android.view.View { <init>(android.content.Context); }
-keepclassmembers class * extends android.app.Activity { public void *(android.view.View); }

# Kotlin: aserciones de nulabilidad sin costo en tiempo de ejecución.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkNotNullParameter(...);
    static void checkNotNullExpressionValue(...);
    static void checkParameterIsNotNull(...);
    static void checkExpressionValueIsNotNull(...);
}

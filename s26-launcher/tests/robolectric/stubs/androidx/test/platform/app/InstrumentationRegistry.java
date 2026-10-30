package androidx.test.platform.app;
public final class InstrumentationRegistry {
  private static android.app.Instrumentation i; private static android.os.Bundle b;
  public static void registerInstance(android.app.Instrumentation x, android.os.Bundle y) { i = x; b = y; }
  public static android.app.Instrumentation getInstrumentation() { return i; }
  public static android.os.Bundle getArguments() { return b; }
}

package androidx.test.espresso; public final class IdlingRegistry { private static final IdlingRegistry I = new IdlingRegistry();
  public static IdlingRegistry getInstance() { return I; }
  public java.util.Collection<android.os.Looper> getLoopers() { return java.util.Collections.emptyList(); }
  public java.util.Collection<IdlingResource> getResources() { return java.util.Collections.emptyList(); } }

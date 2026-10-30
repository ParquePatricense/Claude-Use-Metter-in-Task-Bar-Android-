package androidx.test.internal.runner.lifecycle;
import androidx.test.runner.lifecycle.*; import android.app.Activity; import java.util.*;
public class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
  private final Map<Activity, Stage> st = new WeakHashMap<>(); private final List<ActivityLifecycleCallback> cbs = new ArrayList<>();
  public void addLifecycleCallback(ActivityLifecycleCallback c) { cbs.add(c); }
  public void removeLifecycleCallback(ActivityLifecycleCallback c) { cbs.remove(c); }
  public Stage getLifecycleStageOf(Activity a) { return st.get(a); }
  public Collection<Activity> getActivitiesInStage(Stage s) { List<Activity> r = new ArrayList<>(); for (Map.Entry<Activity, Stage> e : st.entrySet()) if (e.getValue() == s) r.add(e.getKey()); return r; }
  public void signalLifecycleChange(Stage s, Activity a) { st.put(a, s); for (ActivityLifecycleCallback c : new ArrayList<>(cbs)) c.onActivityLifecycleChanged(a, s); }
}

package s26;

import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import com.s26home.launcher.LauncherApp;
import com.s26home.launcher.data.*;
import com.s26home.launcher.ui.*;
import com.s26home.launcher.settings.SettingsActivity;
import com.s26home.launcher.onboarding.OnboardingActivity;
import com.s26home.launcher.system.Wallpapers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w384dp-h823dp-port-450dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeTest {
  static final String[][] APPS = {
    {"com.android.vending","Play Store","-1"},{"com.sec.android.gallery3d","Galería","3"},{"com.samsung.android.calendar","Calendario","7"},
    {"com.android.settings","Ajustes","-1"},{"com.sec.android.app.myfiles","Mis archivos","7"},{"com.samsung.android.app.notes","Samsung Notes","7"},
    {"com.sec.android.app.voicenote","Grabadora de voz","1"},{"com.samsung.android.spay","Samsung Wallet","-1"},{"com.samsung.android.oneconnect","SmartThings","-1"},
    {"com.google.android.gm","Gmail","7"},{"com.google.android.apps.maps","Maps","6"},{"com.google.android.youtube","YouTube","2"},
    {"com.google.android.apps.docs","Drive","7"},{"com.google.android.googlequicksearchbox","Google","-1"},{"com.whatsapp","WhatsApp","4"},
    {"com.instagram.android","Instagram","4"},{"com.spotify.music","Spotify","1"},{"com.netflix.mediaclient","Netflix","2"},
    {"com.mercadopago.wallet","Mercado Pago","-1"},{"com.mercadolibre","Mercado Libre","-1"},{"com.supercell.clashroyale","Clash Royale","0"},
    {"com.sec.android.app.camera","Cámara","3"},{"com.samsung.android.messaging","Mensajes","4"},{"com.samsung.android.dialer","Teléfono","-1"},
    {"com.sec.android.app.sbrowser","Samsung Internet","-1"},{"com.samsung.android.app.reminder","Recordatorio","7"},{"com.sec.android.app.clockpackage","Reloj","-1"},
    {"com.samsung.android.calculator","Calculadora","-1"},{"com.twitter.android","X","4"},{"com.zhiliaoapp.musically","TikTok","4"},
  };

  Activity cur;

  void pump(long ms) {
    for (int i = 0; i < 12; i++) {
      try { Thread.sleep(25); } catch (InterruptedException e) {}
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms / 12 + 1));
    }
  }

  static Bitmap wall;
  void shot(Activity a, String name) throws Exception {
    pump(200);
    View d = a.getWindow().getDecorView();
    int w = d.getWidth(), h = d.getHeight();
    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    if (a instanceof HomeActivity) {
      if (wall == null) wall = Wallpapers.INSTANCE.render(Wallpapers.INSTANCE.getStyles().get(0), w, h);
      c.drawBitmap(wall, 0, 0, null);
    }
    d.draw(c);
    try (FileOutputStream o = new FileOutputStream(System.getProperty("shots.dir", "/tmp/shots") + "/" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    System.out.println("SHOT " + name + " " + w + "x" + h);
  }

  @Before public void apps() throws Exception {
    Context ctx = RuntimeEnvironment.getApplication();
    LauncherApps la = ctx.getSystemService(LauncherApps.class);
    Class<?> ic = Class.forName("android.content.pm.LauncherActivityInfoInternal");
    Class<?> inc = Class.forName("android.content.pm.IncrementalStatesInfo");
    Constructor<?> ik = ic.getDeclaredConstructor(ActivityInfo.class, inc, UserHandle.class, boolean.class);
    Constructor<LauncherActivityInfo> k = LauncherActivityInfo.class.getDeclaredConstructor(Context.class, ic);
    k.setAccessible(true);
    for (String[] a : APPS) {
      ActivityInfo ai = new ActivityInfo();
      ai.packageName = a[0]; ai.name = a[0] + ".Main"; ai.nonLocalizedLabel = a[1];
      ai.applicationInfo = new ApplicationInfo(); ai.applicationInfo.packageName = a[0];
      ai.applicationInfo.category = Integer.parseInt(a[2]); ai.applicationInfo.nonLocalizedLabel = a[1];
      ai.applicationInfo.flags = a[0].startsWith("com.sec") || a[0].startsWith("com.samsung") || a[0].startsWith("com.android") ? ApplicationInfo.FLAG_SYSTEM : 0;
      shadowOf(la).addActivity(android.os.Process.myUserHandle(), k.newInstance(ctx, ik.newInstance(ai, null, android.os.Process.myUserHandle(), false)));
    }
  }

  HomeActivity home() {
    LauncherApp app = (LauncherApp) RuntimeEnvironment.getApplication();
    app.getSettings().setOnboardingDone(true);
    app.getApps().load(null);
    pump(300);
    ActivityController<HomeActivity> c = Robolectric.buildActivity(HomeActivity.class).setup();
    HomeActivity a = c.get(); cur = a;
    // dispara el listener de insets como en un equipo real
    a.getWindow().getDecorView().requestApplyInsets();
    pump(600);
    return a;
  }

  @BeforeClass public static void dir() { new File(System.getProperty("shots.dir", "/tmp/shots")).mkdirs(); }

  @Test public void homeFlows() throws Exception {
    HomeActivity a = home();
    LauncherApp app = (LauncherApp) a.getApplication();
    System.out.println("apps=" + app.getApps().getAll().size() + " items=" + app.getModel().getItems().size() + " pages=" + a.getWorkspace().getPageCount());
    Assert.assertTrue(app.getModel().getItems().size() > 3);
    shot(a, "01_home");

    a.openDrawer(false); pump(800);
    Assert.assertTrue(a.getDrawer().isOpen());
    shot(a, "02_drawer");
    a.getDrawer().close(true); pump(600);
    Assert.assertFalse(a.getDrawer().isOpen());
    a.openDrawer(true); pump(800);
    EditText_set(a, "ma");
    shot(a, "03_drawer_search");
    a.closeAllOverlays(false); pump(300);

    // carpeta
    FolderIconView f = find(a.getRoot(), FolderIconView.class);
    Assert.assertNotNull("hay carpeta por defecto", f);
    f.performClick(); pump(700);
    Assert.assertNotNull(a.getFolder());
    shot(a, "04_folder");
    a.getFolder().close(true); pump(600);
    Assert.assertNull(a.getFolder());

    // pulsación larga + arrastre a otra celda
    CellLayout page0 = (CellLayout) a.getWorkspace().getChildAt(0);
    IconView icon = null;
    for (int i = 0; i < page0.getChildCount(); i++) { View v = page0.getChildAt(i); if (v.getClass() == IconView.class) { icon = (IconView) v; break; } }
    Assert.assertNotNull(icon);
    HomeItem it = icon.getItem();
    int ox = it.getX(), oy = it.getY();
    float[] p = center(a, icon);
    long t0 = SystemClock.uptimeMillis();
    dispatch(a, MotionEvent.ACTION_DOWN, p[0], p[1], t0);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
    Assert.assertNotNull("popup tras pulsación larga", a.getPopup());
    shot(a, "05_popup");
    Assert.assertTrue(a.getDrag().getActive());
    float tx = a.getRoot().getWidth() * 0.5f, ty = a.getRoot().getHeight() * 0.3f;
    for (int i = 1; i <= 10; i++) { dispatch(a, MotionEvent.ACTION_MOVE, p[0] + (tx - p[0]) * i / 10, p[1] + (ty - p[1]) * i / 10, t0 + 700 + i * 16); pump(20); }
    shot(a, "06_dragging");
    dispatch(a, MotionEvent.ACTION_UP, tx, ty, t0 + 1000); pump(600);
    Assert.assertFalse(a.getDrag().getActive());
    System.out.println("moved from " + ox + "," + oy + " to " + it.getX() + "," + it.getY() + " page " + it.getPage());
    Assert.assertTrue("se movió", it.getX() != ox || it.getY() != oy);

    // modo edición
    a.enterEditMode(); pump(600);
    shot(a, "07_edit");
    a.showWidgetPicker(); pump(600);
    shot(a, "08_widgets");
    a.closeAllOverlays(false); pump(300);
    Assert.assertFalse(a.getEditMode());

    // nueva página y crear carpeta arrastrando un ícono sobre otro
    int before = (int) app.getModel().getItems().stream().filter(x -> x.isFolder()).count();
    CellLayout pg = (CellLayout) a.getWorkspace().getChildAt(a.getWorkspace().getCurrentPage());
    List<IconView> icons = new ArrayList<>();
    for (int i = 0; i < pg.getChildCount(); i++) { View v = pg.getChildAt(i); if (v.getClass() == IconView.class) icons.add((IconView) v); }
    if (icons.size() >= 2) {
      float[] s = center(a, icons.get(0)); float[] d = center(a, icons.get(1));
      long t1 = SystemClock.uptimeMillis();
      dispatch(a, MotionEvent.ACTION_DOWN, s[0], s[1], t1);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
      for (int i = 1; i <= 10; i++) { dispatch(a, MotionEvent.ACTION_MOVE, s[0] + (d[0] - s[0]) * i / 10, s[1] + (d[1] - s[1]) * i / 10, t1 + 700 + i * 16); pump(20); }
      pump(200);
      dispatch(a, MotionEvent.ACTION_UP, d[0], d[1], t1 + 1100); pump(600);
      int after = (int) app.getModel().getItems().stream().filter(x -> x.isFolder()).count();
      System.out.println("folders " + before + " -> " + after);
      Assert.assertEquals(before + 1, after);
      shot(a, "09_new_folder");
    }

    // gesto deslizar hacia arriba abre apps
    long t2 = SystemClock.uptimeMillis();
    float cx = a.getRoot().getWidth() / 2f, cy = a.getRoot().getHeight() * 0.55f;
    dispatch(a, MotionEvent.ACTION_DOWN, cx, cy, t2);
    for (int i = 1; i <= 12; i++) { dispatch(a, MotionEvent.ACTION_MOVE, cx, cy - i * 40, t2 + i * 10); }
    dispatch(a, MotionEvent.ACTION_UP, cx, cy - 480, t2 + 130); pump(800);
    Assert.assertTrue("swipe up abre apps", a.getDrawer().isOpen());
    a.closeAllOverlays(false); pump(200);

    // deslizar entre páginas
    app.getModel().addPage(); pump(300);
    Assert.assertTrue(a.getWorkspace().getPageCount() >= 2);
    long t3 = SystemClock.uptimeMillis();
    float y = a.getRoot().getHeight() * 0.4f;
    dispatch(a, MotionEvent.ACTION_DOWN, 330 * 2.8f, y, t3);
    for (int i = 1; i <= 10; i++) dispatch(a, MotionEvent.ACTION_MOVE, 330 * 2.8f - i * 60, y, t3 + i * 10);
    dispatch(a, MotionEvent.ACTION_UP, 330 * 2.8f - 600, y, t3 + 110); pump(800);
    System.out.println("page after swipe " + a.getWorkspace().getCurrentPage());
    Assert.assertEquals(1, a.getWorkspace().getCurrentPage());

    // persistencia
    pump(800); Thread.sleep(300); pump(100);
    File f2 = new File(a.getFilesDir(), "home.json");
    Assert.assertTrue(f2.exists());
    System.out.println("home.json " + f2.length() + " bytes");

    // cambio de ajustes => reconstrucción en vivo
    app.getSettings().setHomeCols(5); app.getSettings().setHomeRows(6); pump(500);
    Assert.assertEquals(5, ((CellLayout) a.getWorkspace().getChildAt(0)).getCols());
    app.getSettings().setHomeCols(4); app.getSettings().setHomeRows(5); pump(500);
    a.getWorkspace().setCurrentPageImmediate(0);
    shot(a, "10_home_after");
  }

  @Test @Config(qualifiers = "w384dp-h823dp-port-night-450dpi") public void darkMode() throws Exception {
    HomeActivity a = home();
    a.openDrawer(false); pump(800);
    shot(a, "11_drawer_dark");
    a.closeAllOverlays(false); pump(300);
    FolderIconView f = find(a.getRoot(), FolderIconView.class);
    if (f != null) { f.performClick(); pump(700); shot(a, "12_folder_dark"); }
  }

  @Test public void settingsScreens() throws Exception {
    for (String s : new String[]{null, "home", "icons", "drawer", "style", "gestures", "hidden", "about"}) {
      Intent i = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class);
      if (s != null) i.putExtra("section", s);
      Activity a = Robolectric.buildActivity(SettingsActivity.class, i).setup().get();
      pump(200);
      shot(a, "20_settings_" + (s == null ? "main" : s));
    }
  }

  @Test public void onboarding() throws Exception {
    ActivityController<OnboardingActivity> c = Robolectric.buildActivity(OnboardingActivity.class).setup();
    for (int i = 0; i < 6; i++) {
      pump(400);
      shot(c.get(), "30_onboarding_" + i);
      View next = findText(c.get().getWindow().getDecorView(), i == 5 ? "Empezar" : "Siguiente");
      Assert.assertNotNull(next);
      if (i < 5) next.performClick();
    }
  }

  @Test @Config(qualifiers = "w384dp-h823dp-port-night-450dpi") public void settingsDark() throws Exception {
    Activity a = Robolectric.buildActivity(SettingsActivity.class).setup().get();
    shot(a, "21_settings_main_dark");
    Intent i = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class).putExtra("section", "style");
    shot(Robolectric.buildActivity(SettingsActivity.class, i).setup().get(), "21_settings_style_dark");
  }

  // ---------------------------------------------------------------- utilidades
  void EditText_set(HomeActivity a, String q) {
    android.widget.EditText e = find(a.getDrawer(), android.widget.EditText.class);
    e.setText(q); pump(300);
  }
  float[] center(HomeActivity a, IconView v) {
    Rect r = new Rect(); v.iconBounds(r);
    RectF rf = a.getDrag().mapRectToRoot(v, new RectF(r));
    return new float[]{rf.centerX(), rf.centerY()};
  }
  void dispatch(Activity a, int action, float x, float y, long t) {
    MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
    a.getWindow().getDecorView().dispatchTouchEvent(e); e.recycle();
  }
  static <T> T find(View v, Class<T> c) {
    if (c.isInstance(v) && v.getVisibility() == View.VISIBLE) return c.cast(v);
    if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { T r = find(g.getChildAt(i), c); if (r != null) return r; } }
    return null;
  }
  static View findText(View v, String t) {
    if (v instanceof android.widget.TextView && t.contentEquals(((android.widget.TextView) v).getText())) return v;
    if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) { View r = findText(g.getChildAt(i), t); if (r != null) return r; } }
    return null;
  }
}

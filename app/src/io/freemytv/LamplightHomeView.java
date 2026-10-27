package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Data/launch/accessibility adapter for the independently authored Lamplight renderer. */
final class LamplightHomeView extends View implements HomeSurface, HomeData.Listener, Recs.Listener {
    private final Activity activity;
    private final ImpeccableHomeRenderer renderer = new ImpeccableHomeRenderer();
    private final ImpeccableHomeRenderer.Model model = new ImpeccableHomeRenderer.Model();
    private final HomeData data;
    private final Recs recs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Map<String, HomeApps.Item> targets = new HashMap<>();
    private final RectF bounds = new RectF();
    private final NowPlaying.Watch watch;
    private List<HomeApps.Item> apps = new ArrayList<>(), controls = new ArrayList<>(), allApps = new ArrayList<>();
    private List<Recs.Pick> picks = new ArrayList<>();
    private List<Resume.Entry> resume = new ArrayList<>();
    private boolean running, okDown, allOnResume;
    private int generation;
    private long rotation, snapshotAt;
    private final Spoken spoken;

    LamplightHomeView(Activity host) {
        super(host);
        activity = host;
        data = HomeData.get(host);
        recs = Recs.get(host);
        recs.setLight(true);
        watch = new NowPlaying.Watch(host, this::refresh, true);
        setFocusable(true);
        spoken = new Spoken(this, new Spoken.Source() {
            @Override public void items(List<Integer> out) {
                for (int s = 0; s < 4; s++) for (int i = 0; i < row(s).items.size(); i++) {
                    if (renderer.itemBounds(model, s, i, getWidth(), getHeight(), bounds)) out.add(id(s, i));
                }
            }
            @Override public String text(int id) {
                ImpeccableHomeRenderer.Item item = item(id);
                return item == null ? "" : item.title + (item.detail.isEmpty() ? "" : ", " + item.detail);
            }
            @Override public boolean bounds(int id, Rect out) {
                if (!renderer.itemBounds(model, section(id), index(id), getWidth(), getHeight(), bounds)) return false;
                bounds.round(out);
                return true;
            }
            @Override public int focused() {
                return model.focusedItem < 0 ? -1 : id(model.focusedSection, model.focusedItem);
            }
            @Override public void focus(int id) {
                model.focusedSection = section(id);
                model.focusedItem = index(id);
                renderer.normalize(model);
                invalidate();
            }
            @Override public void click(int id) { focus(id); activate(); }
        });
        apps = HomeApps.shelf(host, false);
        controls = controls(host);
        rebuildModel();
    }

    private static int id(int section, int index) { return 1 + section * 1000 + index; }
    private static int section(int id) { return (id - 1) / 1000; }
    private static int index(int id) { return (id - 1) % 1000; }
    private ImpeccableHomeRenderer.Row row(int section) {
        return section == 0 ? model.apps : section == 1 ? model.actions : section == 2 ? model.resume : model.discovery;
    }
    private ImpeccableHomeRenderer.Item item(int id) {
        if (id < 1 || section(id) > 3) return null;
        List<ImpeccableHomeRenderer.Item> items = row(section(id)).items;
        return index(id) < items.size() ? items.get(index(id)) : null;
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return spoken; }

    private static List<HomeApps.Item> controls(Activity host) {
        List<HomeApps.Item> list = new ArrayList<>(HomeApps.drawerShortcuts(host));
        for (HomeApps.Item input : HomeApps.allInputs(host)) list.add(input);
        if (HomeApps.liveTv(host) != null) list.add(HomeApps.action(HomeApps.LIVE_TV, "live", Ui.tr("Live TV"), "tv"));
        return list;
    }
    private void refresh() {
        if (!running) return;
        resume = Resume.items(activity);
        if (picks.isEmpty()) picks = recs.row(null, 12, rotation);
        rebuildModel();
    }
    private void rebuildModel() {
        String focused = renderer.focused(model) == null ? "" : renderer.focused(model).id;
        targets.clear();
        List<ImpeccableHomeRenderer.Item> appRows = new ArrayList<>(), actionRows = new ArrayList<>(), mediaRows = new ArrayList<>(), discoveries = new ArrayList<>();
        for (HomeApps.Item app : apps) {
            String id = "app:" + app.pkg;
            targets.put(id, app);
            appRows.add(new ImpeccableHomeRenderer.Item(id, app.label, "", app.tile != null ? app.tile : app.logo,
                    ImpeccableHomeRenderer.Icon.APPS, true, -1));
        }
        for (HomeApps.Item control : controls) {
            String id = "control:" + control.key;
            targets.put(id, control);
            actionRows.add(new ImpeccableHomeRenderer.Item(id, control.label, "", null,
                    control.kind == HomeApps.SETTINGS || control.kind == HomeApps.ANDROID_SETTINGS ? ImpeccableHomeRenderer.Icon.SETTINGS
                    : control.kind == HomeApps.INPUT ? ImpeccableHomeRenderer.Icon.INPUT : ImpeccableHomeRenderer.Icon.APPS, true, -1));
        }
        for (Resume.Entry entry : resume) {
            String id = "resume:" + entry.pkg + ":" + entry.mediaId + ":" + entry.title;
            HomeApps.Item launch = HomeApps.app(entry.pkg, entry.app, Resume.uri(entry));
            launch.resume = true;
            targets.put(id, launch);
            mediaRows.add(new ImpeccableHomeRenderer.Item(id, Resume.title(entry), entry.app + " · " + Resume.left(entry),
                    Resume.artwork(activity, entry, this::refresh), ImpeccableHomeRenderer.Icon.PLAY, true,
                    entry.dur > 0 ? Math.min(1, (float) entry.pos / entry.dur) : -1));
        }
        for (Recs.Pick pick : picks) {
            String id = "pick:" + pick.kind + ":" + pick.id;
            Backdrop art = recs.backdrop(pick);
            if (pick.pkg != null) targets.put(id, HomeApps.app(pick.pkg, pick.title, pick.link));
            discoveries.add(new ImpeccableHomeRenderer.Item(id, pick.title, pick.meta, art == null ? null : art.wide,
                    ImpeccableHomeRenderer.Icon.PLAY, pick.pkg != null, -1));
        }
        model.apps = new ImpeccableHomeRenderer.Row(Ui.tr("Shelf"), appRows, ImpeccableHomeRenderer.Status.READY, Ui.tr("No apps available."));
        model.actions = new ImpeccableHomeRenderer.Row(Ui.tr("Top Buttons"), actionRows, ImpeccableHomeRenderer.Status.READY, "");
        model.resume = new ImpeccableHomeRenderer.Row(Ui.tr("Continue Watching"), mediaRows, ImpeccableHomeRenderer.Status.READY, Ui.tr("None yet."));
        model.discovery = new ImpeccableHomeRenderer.Row(Ui.tr("For You"), discoveries, ImpeccableHomeRenderer.Status.READY, Ui.tr("None yet."));
        HomeData.Weather wx = data.weather;
        model.weather = !Prefs.weather() || wx == null ? null : new ImpeccableHomeRenderer.Weather(
                HomeData.degrees(wx.temp), HomeData.condition(wx.code, wx.isDay), Prefs.city());
        model.time = android.text.format.DateFormat.getTimeFormat(activity).format(new java.util.Date());
        model.textScale = Math.max(1, Math.min(1.3f, getResources().getConfiguration().fontScale));
        List<ImpeccableHomeRenderer.Item> current = row(model.focusedSection).items;
        for (int i = 0; i < current.size(); i++) if (current.get(i).id.equals(focused)) model.focusedItem = i;
        renderer.normalize(model);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) { renderer.render(canvas, getWidth(), getHeight(), model); }
    @Override public void onData() { refresh(); }
    @Override public void onRecs() { refresh(); }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            data.refreshIfStale();
            recs.refreshIfStale();
            if (android.os.SystemClock.elapsedRealtime() - snapshotAt >= 10 * 60000L) {
                rotation++;
                snapshotAt = android.os.SystemClock.elapsedRealtime();
                picks = recs.row(null, 12, rotation);
            }
            recs.prefetchRows(rotation);
            refresh();
            main.postDelayed(this, 15000);
        }
    };
    @Override public void resume() {
        if (running) return;
        running = true;
        recs.appsChanged();
        data.addListener(this);
        recs.addListener(this);
        rotation = System.currentTimeMillis() / 600000;
        snapshotAt = android.os.SystemClock.elapsedRealtime();
        recs.prefetchRows(rotation);
        picks = recs.row(null, 12, rotation);
        watch.start();
        reset(false);
        refresh();
        int request = ++generation;
        loader.execute(() -> {
            List<HomeApps.Item> loaded = HomeApps.shelf(activity, true), others = HomeApps.others(activity);
            main.post(() -> {
                if (!running || generation != request) return;
                apps = loaded;
                allApps = others;
                rebuildModel();
                if (allOnResume) { allOnResume = false; showAllApps(); }
            });
        });
        main.post(tick);
        spoken.readAgain();
    }
    @Override public void pause() {
        running = false;
        generation++;
        main.removeCallbacks(tick);
        data.removeListener(this);
        recs.removeListener(this);
        watch.stop();
        okDown = false;
    }
    @Override public void destroy() { pause(); loader.shutdown(); }
    @Override public void reset(boolean animated) {
        model.focusedSection = ImpeccableHomeRenderer.APPS;
        model.focusedItem = 0;
        model.scrollY = 0;
        java.util.Arrays.fill(model.scrollX, 0);
        renderer.normalize(model);
        invalidate();
    }
    @Override public void showAllApps() {
        if (allApps.isEmpty()) { allOnResume = true; return; }
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = allApps.get(i).label;
        new AlertDialog.Builder(activity).setTitle(Ui.tr("All Apps")).setItems(labels,
                (dialog, which) -> launch(allApps.get(which))).show();
    }
    private void launch(HomeApps.Item target) {
        if (target.kind == HomeApps.ALL_APPS) { showAllApps(); return; }
        if (target.kind == HomeApps.SLEEP) {
            final int[] minutes = {0,5,10,15,20,30,45,60,90,120,180,240};
            String[] labels = new String[minutes.length];
            for (int i = 0; i < labels.length; i++) labels[i] = minutes[i] == 0 ? Ui.tr("Off") : SleepTimer.duration(minutes[i]);
            new AlertDialog.Builder(activity).setTitle(Ui.tr("Sleep Timer")).setItems(labels,
                    (dialog, which) -> SleepTimer.set(activity, minutes[which])).show();
            return;
        }
        if (!HomeApps.open(activity, target, null)) Toast.makeText(activity, Ui.tr("Not installed"), Toast.LENGTH_SHORT).show();
    }
    private void activate() {
        ImpeccableHomeRenderer.Item selected = renderer.focused(model);
        if (selected == null) return;
        HomeApps.Item target = targets.get(selected.id);
        if (target != null) launch(target);
    }
    @Override public boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) { renderer.move(model, code); invalidate(); spoken.focusMoved(); }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (down && event.getRepeatCount() == 0) okDown = true;
            else if (!down) { if (okDown && !event.isCanceled() && spoken.keyClick()) activate(); okDown = false; }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) { if (!down) reset(true); return true; }
        return false;
    }
}

package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Vibe TV's settings (the app's icon, "All Apps", or the gear on the home screen), as a two-pane menu: on the
 * left a rail of anchors, on the right one continuous settings document. Section headings and natural heights
 * let short groups reveal the following group. The rail jumps to headings; content arrows cross groups. OK moves
 * between the panes and opens what a row offers (a list, a switch); the arrows move the focus and step values; Back
 * goes back a step at a time (list → panel → rail → out). The apps on the shelf and the top buttons are edited like
 * on the home screen: OK adds or removes, holding OK lifts an app and the arrows move it.
 * The switch from the TV's own home screen runs in the main process (HomeMode); this screen only asks for it and
 * follows the state file.
 */
public class SettingsActivity extends Activity {
    private SettingsView view;
    private DreamView preview;   // "Screensaver": the screensaver right here, without changing the system setting

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (openFireHome(getIntent())) return;
        Watchdog.install(this);
        Ui.init(this);
        SleepTimer.listen(this);   // :home may start here, from the launcher icon
        SleepTimer.check(this);
        view = new SettingsView(this);
        setContentView(view);
    }

    /**
     * Fire TV: Amazon keeps the Home button, so the app's icon opens the home screen (FireHomeActivity) directly;
     * the gear on the home screen opens these settings (no launcher category).
     */
    private boolean openFireHome(Intent intent) {
        if (!HomeMode.AMAZON || intent == null || !(intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                || intent.hasCategory(Intent.CATEGORY_LAUNCHER)) || intent.getBooleanExtra("screensaver", false)) {
            return false;
        }
        startActivity(new Intent(this, FireHomeActivity.class));
        finish();
        return true;
    }

    @Override protected void onResume() {
        super.onResume();
        if (view == null) return;
        view.resume();
        // From ADB: am start -n io.freemytv/.SettingsActivity --ez screensaver true (straight to the preview).
        if (preview == null && getIntent().getBooleanExtra("screensaver", false)) {
            getIntent().removeExtra("screensaver");
            view.pause();
            showPreview();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (openFireHome(intent)) return;
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();     // before pausing the view: closing the preview resumes it (view.resume)
        if (view != null) view.pause();
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            // Like the real screensaver: arrows, OK or Back close it (other keys go on to the system); with Watch from the
            // screensaver on, OK opens the title first.
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        boolean ok = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
                        if (ok && Prefs.saverWatch()) preview.watch();
                        closePreview();
                    }
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        /** The sections, top to bottom on the rail; the panel on the right shows the focused one. */
        private static final int SECTIONS = 11;
        private static final int SEC_HOME = 0, SEC_LAYOUT = 1, SEC_SAVER = 2, SEC_GENERAL = 3, SEC_WIDGETS = 4, SEC_SHELF = 5,
                SEC_TOP = 6, SEC_SLEEP = 7, SEC_KEYS = 8, SEC_TV = 9, SEC_ABOUT = 10;
        private static final int ZONE_RAIL = 0, ZONE_PANEL = 1;
        /** Geometry in 1080p units, scaled by k. */
        private static final float RAIL_ITEM_L = 100, RAIL_ITEM_R = 450, RAIL_ITEM_H = 56, RAIL_PITCH = 62, RAIL_TOP = 252;
        private static final float PANEL_L = 510, PANEL_R = 1840;
        private static final float ROW_INSET = 24, TEXT_INSET = 30;
        private static final float ROWS_TOP = 190, ROW_H = 68, ROW_GAP = 8, CHOICE_H = 104;
        private static final int PANEL_VISIBLE = 9;
        private static final long HOLD_MS = 500;
        private static final int AMBER = 0xFFFFC266, INK = 0xFF1C1C1E;
        /** RAIL_SYMBOLS[i] is the symbol of section i (Symbols assets). */
        private static final String[] RAIL_SYMBOLS = {"palette", "view_agenda", "bedtime", "tune", "widgets", "apps", "toolbar", "schedule",
                "settings_remote", "tv_settings", "info"};
        private static final String[] UNITS = {"auto", "C", "F"}, THEMES = {"auto", "light", "dark"};
        /** The list picker: what it was opened for (a key's index + 1, or one of the PICK_… constants below). */
        private static final int PICK_CITY = 0, PICK_LANGUAGE = -1, PICK_SLEEP = -2, PICK_PLACE = -3;
        /** General's rows. */
        private static final int G_LANGUAGE = 0, G_TEMP = 1, G_THEME = 2, G_SOUNDS = 3;
        /** Widgets' rows: media-session switches share the listener, then quotes and its coin rows. */
        private static final int W_WEATHER = 0, W_CITY = 1, W_PLAYING = 2, W_RESUME = 3, W_QUOTES = 4, W_COINS = 5;
        /** Sleep Timer's rows, by what they set: the timer, whether it shows on screen and (where it shows over other
         * apps) where. Values are picked from lists (OK), as on the TV's own settings; ← → step them. */
        private static final int P_SLEEP = 0, P_SHOW = 1, P_WHERE = 2;

        private final SettingsActivity activity;
        private final Handler handler = new Handler(Looper.getMainLooper());
        /** Focus pills: the rail's sections, then the panel's visible rows. */
        private final Spring[] pop = new Spring[SECTIONS + PANEL_VISIBLE];
        private final Spring lift = new Spring(0.28f, 0.85f, 0);   // the lifted row's scale
        private final List<String> appPkgs = new ArrayList<>(), appLabels = new ArrayList<>();
        // The list picker: what it shows, which one is in use, and the row to come back to.
        private final List<String> pickLabels = new ArrayList<>();
        // The editors (shelf and top buttons): what they can hold (selected ones first, in order) and the selection.
        private List<String[]> shelfRows = new ArrayList<>();
        private List<String> shelfOrder = new ArrayList<>();
        private final List<String> liftSaved = new ArrayList<>();
        /** Sleep Timer's pickers (PICK_SLEEP and below): their choices as {value, name}, and the value now. */
        private final List<String[]> choices = new ArrayList<>();
        private String choiceNow = "";
        private final List<String[]> cities = new ArrayList<>();
        private String cityStatus = "";
        private final RectF tmp = new RectF(), rowRect = new RectF(), focusBox = new RectF();
        private JSONObject state = new JSONObject(), recs = new JSONObject();
        private boolean hasKey;
        private int zone = ZONE_RAIL, section = SEC_HOME, focus = SEC_HOME, scroll;
        private boolean picking, lifted, editingTop;
        private int pickFor, pickCurrent, returnRow;   // pickFor: the remote button (index + 1), or a PICK_… constant
        private boolean running, okDown, backDown, holdArmed, wasBusy;
        private StaticLayout errorLayout;   // the last error, wrapped (rebuilt only when it changes)
        private Toast whyToast;   // why the focused option is unavailable
        private String[] whyRows;  // each row's reason, worked out once per screen (null: again on the next frame)
        private boolean whyPicking;
        private int whySection = -1, whyScroll = -1, whyState = -1;
        private int sleepLeft;     // the sleep timer's minutes left, read with the state, not on every frame
        private String errorShown;
        private final Spoken spoken;
        private static final float PAGE_TOP = 80, PAGE_BOTTOM = 1000, SECTION_HEAD = 68, SECTION_GAP = 64;
        private final float[] sectionTops = new float[SECTIONS], sectionHeights = new float[SECTIONS];
        private float pageOffset, pageTarget, documentHeight;
        private int drawingSection = -1, drawingFocusSection, drawingFocusRow;
        private final EditorData[] editors = {new EditorData(), new EditorData()};
        private static final class EditorData {
            final List<String[]> rows = new ArrayList<>();
            final List<String> order = new ArrayList<>();
        }
        private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pageSurface = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final HomeThemeThumbs themeThumbs = new HomeThemeThumbs();
        private List<String> layoutOrder = new ArrayList<>();
        private long startedAt, lastNanos, pendingAt;
        private boolean animateScroll = true;
        private int w, h;
        private float k = 1;
        private Paint title, version, sectionTitle, rowLabel, rowValue, rowSub, header, cellLabel, cellValue, warn,
                spinner, white, shadow, hint;
        private TextPaint notice;

        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                state = HomeMode.state(getContext());
                sleepLeft = SleepTimer.minutesLeft(getContext());
                hasKey = Recs.hasKey(getContext());
                recs = Recs.status(getContext());
                boolean nowBusy = !busyText().isEmpty();
                if (nowBusy) pendingAt = 0;
                // What can change the reasons away from a key: a switch (or the watchdog's revert) and the overlay's and the
                // listener's checks.
                int whyNow = (freeActive() ? 1 : 0) | (Capabilities.refused(getContext(), Capabilities.OVERLAY) ? 2 : 0)
                        | (Capabilities.refused(getContext(), Capabilities.LISTENER) ? 4 : 0);
                if (whyNow != whyState) whyRows = null;
                whyState = whyNow;
                if (zone == ZONE_PANEL && focus >= panelRows()) focus = Math.max(0, panelRows() - 1);
                if (!picking) revealFocusedRow();
                // A switch just ended: TalkBack reads how it went (the newest notice).
                JSONArray notices = state.optJSONArray("notices");
                if (wasBusy && !nowBusy && notices != null && notices.length() > 0) {
                    JSONObject newest = notices.optJSONObject(0);
                    if (newest != null) announceForAccessibility(newest.optString("text"));
                }
                wasBusy = nowBusy;
                invalidate();
                handler.postDelayed(this, busy() ? 500 : 2000);
            }
        };

        private final Runnable holdStart = new Runnable() {
            @Override public void run() {
                if (okDown && holdArmed && !picking && !lifted) startLift();
            }
        };

        SettingsView(SettingsActivity a) {
            super(a);
            activity = a;
            prepareEditors();
            for (String[] app : HomeApps.shelfCandidates(a)) {
                appPkgs.add(app[0]);
                appLabels.add(app[1]);
            }
            for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.28f, 0.85f, 0);
            setFocusable(true);
            setContentDescription("Vibe TV");
            // Stable section/row IDs survive movement through the continuous document.
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) {
                    if (picking) {
                        for (int i = 0; i < panelRows(); i++) out.add(1_000_000 + i);
                        return;
                    }
                    for (int i = 1; i <= SECTIONS; i++) if (sectionPresent(i - 1)) out.add(i);
                    for (int group = 0; group < SECTIONS; group++) {
                        for (int row = 0; row < rowsInSection(group); row++) {
                            if (documentRowBounds(group, row, null)) out.add(contentId(group, row));
                        }
                    }
                }
                @Override public String text(int id) {
                    if (id <= SECTIONS) return railText(id - 1);
                    if (id >= 1_000_000) return rowText(id - 1_000_000);
                    Cursor saved = new Cursor();
                    try {
                        int group = id / 1000 - 1;
                        bindSection(group);
                        lifted = saved.wasLifted && group == saved.group;
                        scroll = 0;
                        return rowText(id % 1000);
                    } finally { saved.restore(); }
                }
                @Override public boolean bounds(int id, Rect out) {
                    if (id <= SECTIONS) {
                        railBox(id - 1).round(out);
                        return !picking;
                    }
                    if (id >= 1_000_000) {
                        if (!picking || id - 1_000_000 >= panelRows()) return false;
                        rowBox(id - 1_000_000).round(out);
                        return true;
                    }
                    return !picking && documentRowBounds(id / 1000 - 1, id % 1000, out);
                }
                @Override public int focused() {
                    return picking ? 1_000_000 + focus : zone == ZONE_RAIL ? focus + 1 : contentId(section, focus);
                }
                @Override public void focus(int id) {
                    if (id <= SECTIONS) previewSection(id - 1);
                    else if (id >= 1_000_000) focus = id - 1_000_000;
                    else {
                        bindSection(id / 1000 - 1);
                        zone = ZONE_PANEL;
                        focus = id % 1000;
                        scroll = 0;
                        whyRows = null;
                        revealFocusedRow();
                    }
                    invalidate();
                }
                @Override public void click(int id) {
                    focus(id);
                    if (id <= SECTIONS) selectSection(id - 1); else activate();
                }
                @Override public boolean clickable(int id) {
                    return id <= SECTIONS || id >= 1_000_000 || id / 1000 - 1 != SEC_ABOUT;
                }
                @Override public boolean holdable(int id) {
                    if (id < 1000 || id >= 1_000_000 || picking || lifted) return false;
                    int group = id / 1000 - 1, row = id % 1000;
                    if (group == SEC_LAYOUT) return row < HomeSections.keys().length;
                    if (group != SEC_SHELF && group != SEC_TOP) return false;
                    EditorData data = editors[group == SEC_TOP ? 1 : 0];
                    return row < data.rows.size() && data.order.contains(data.rows.get(row)[0]);
                }
                @Override public void hold(int id) { focus(id); startLift(); }
            });
        }

        private static int contentId(int group, int row) { return (group + 1) * 1000 + row; }

        /** Reading neighboring accessibility nodes must not move the live editor or remote cursor. */
        private final class Cursor {
            final int group = section, row = focus, from = scroll;
            final boolean top = editingTop, wasLifted = lifted;
            final List<String[]> rows = shelfRows;
            final List<String> order = shelfOrder, layout = layoutOrder;
            void restore() {
                section = group;
                focus = row;
                scroll = from;
                editingTop = top;
                lifted = wasLifted;
                shelfRows = rows;
                shelfOrder = order;
                layoutOrder = layout;
                whyRows = null;
            }
        }

        private boolean documentRowBounds(int group, int row, Rect out) {
            if (group < 0 || group >= SECTIONS || !sectionPresent(group) || row < 0 || row >= rowsInSection(group)) return false;
            float height = rowHeight(group, row);
            float top = PAGE_TOP + sectionTops[group] + rowTop(group, row) - pageOffset;
            if (top < PAGE_TOP || top + height > PAGE_BOTTOM) return false;
            float left = PANEL_L + ROW_INSET, right = PANEL_R - ROW_INSET;
            if (group == SEC_HOME) {
                float width = (right - left - 24) / 2;
                left += (row % 2) * (width + 24);
                right = left + width;
            }
            if (out != null) out.set(Math.round(left * k), Math.round(top * k),
                    Math.round(right * k), Math.round((top + height) * k));
            return true;
        }

        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
            return spoken;
        }

        // MARK: - What the rows say

        /** The sections, on the rail and as the panel's title. */
        private String[] sectionLabels() {
            return new String[] {Ui.tr("Themes"), Ui.tr("Home Layout"), Ui.tr("Screensaver"), Ui.tr("Preferences"), Ui.tr("Widgets"),
                    Ui.tr("Shelf"), Ui.tr("Top Buttons"), Ui.tr("Sleep Timer"), Ui.tr("Remote Buttons"),
                    Ui.tr("TV Settings"), Ui.tr("About this TV")};
        }

        /** Why the section can't be opened here (Capabilities.NEED_… reason), or null. */
        private String railWhy(int section) {
            return section == SEC_KEYS && !RemoteKeys.supported() ? Capabilities.missing(getContext(), Capabilities.NEED_REMOTE_BUTTONS) : null;
        }

        /** A rail item as TalkBack reads it, with why where it's unavailable. */
        private String railText(int i) {
            String label = sectionLabels()[i];
            String why = railWhy(i);
            return why == null ? label : label + ", " + Ui.tr("Unavailable") + ". " + why;
        }

        /** The panel's rows: how many are on screen at once. */
        private int panelRows() {
            if (picking) return Math.min(PANEL_VISIBLE, pickLabels.size());
            switch (section) {
                case SEC_HOME: return 4;
                case SEC_LAYOUT: return layoutOrder.size() + 1;
                case SEC_SAVER: return saverLabels().length;
                case SEC_GENERAL: return 4;
                case SEC_WIDGETS: return widgetsLabels().length;
                case SEC_SHELF: case SEC_TOP: return shelfRows.size();
                case SEC_SLEEP: return powerRows().length;
                case SEC_KEYS: return keyLabels().length;
                case SEC_TV: return 1;
                default: return 5;
            }
        }

        private String[] saverLabels() {
            boolean photos = !Photos.files(getContext()).isEmpty();
            List<String> l = new ArrayList<>();
            l.add(Ui.tr("Preview"));
            l.add(Ui.tr("My Photos"));
            if (photos) l.add(Ui.tr("Remove"));
            l.add(Ui.tr("Watch from the screensaver"));
            return l.toArray(new String[0]);
        }

        private String[] saverValues() {
            String[] labels = saverLabels();
            String[] v = new String[labels.length];
            int photos = Photos.files(getContext()).size();
            v[1] = photos == 0 ? Ui.tr("photos|None")
                    : Prefs.photos() ? Ui.plural("plural|one:{n} photo|other:{n} photos", photos) : Ui.tr("Off");
            if (photos > 0) v[2] = Ui.plural("plural|one:{n} photo|other:{n} photos", photos);
            v[v.length - 1] = onOff(Prefs.saverWatch());
            return v;
        }

        private String[] widgetsLabels() {
            List<String> l = new ArrayList<>();
            l.add(Ui.tr("Weather widget"));
            l.add(Ui.tr("City"));
            l.add(Ui.tr("Now Playing widget"));
            l.add(Ui.tr("Continue Watching"));
            l.add(Ui.tr("Quotes widget"));
            if (Prefs.quotes()) {
                l.add("Bitcoin");
                l.add("Ethereum");
                l.add("Solana");
                if (dollar()) l.add(Ui.tr("Dollar"));
            }
            return l.toArray(new String[0]);
        }

        private String[] widgetsValues() {
            String[] labels = widgetsLabels();
            String[] v = new String[labels.length];
            v[W_WEATHER] = onOff(Prefs.weather());
            v[W_CITY] = !cityStatus.isEmpty() ? cityStatus : Prefs.city().isEmpty() ? "—" : Prefs.city();
            v[W_PLAYING] = onOff(Prefs.nowPlaying());
            v[W_RESUME] = onOff(Prefs.resumeOn());
            v[W_QUOTES] = onOff(Prefs.quotes());
            // No quotes widget, nowhere to show prices: the coins read as off (and stay locked) until it is back on.
            for (int i = W_COINS; i < v.length; i++) v[i] = onOff(Prefs.quotes() && Prefs.coin(i - W_COINS));
            return v;
        }

        private boolean dollar() {
            return "BRL".equals(Place.currency());
        }

        private String[] generalLabels() {
            return new String[] {Ui.tr("Language"), Ui.tr("Temperature"), Ui.tr("Appearance"), Ui.tr("Navigation sounds")};
        }

        private String[] generalValues() {
            String[] v = new String[4];
            String lang = Prefs.language();
            v[G_LANGUAGE] = lang.equals("auto") ? Ui.tr("Automatic") : Ui.languageName(lang);
            String unit = Prefs.temperature();
            v[G_TEMP] = unit.equals("C") ? "°C" : unit.equals("F") ? "°F" : Ui.tr("unit|Automatic");
            String theme = Prefs.theme();
            v[G_THEME] = theme.equals("light") ? Ui.tr("Light") : theme.equals("dark") ? Ui.tr("Dark") : Ui.tr("Automatic");
            v[G_SOUNDS] = onOff(Prefs.sounds());
            return v;
        }

        /** Labels and values for the plain list sections (every one but Home, Shelf and Top Buttons). */
        private String[] rowLabels() {
            switch (section) {
                case SEC_LAYOUT: return layoutLabels();
                case SEC_SAVER: return saverLabels();
                case SEC_GENERAL: return generalLabels();
                case SEC_WIDGETS: return widgetsLabels();
                case SEC_SLEEP: return powerLabels();
                case SEC_KEYS: return keyLabels();
                case SEC_TV: return new String[] {Ui.tr("Settings")};
                default: return new String[] {Ui.tr("Version"), Ui.tr("Model")};
            }
        }

        private String[] rowValues() {
            switch (section) {
                case SEC_LAYOUT: return layoutValues();
                case SEC_SAVER: return saverValues();
                case SEC_GENERAL: return generalValues();
                case SEC_WIDGETS: return widgetsValues();
                case SEC_SLEEP: return powerValues();
                case SEC_KEYS: return keyValues();
                case SEC_TV: return new String[] {""};
                default: return new String[] {versionValue(), tvValue()};
            }
        }

        /** A panel row as TalkBack reads it: its name and its value, as on screen, and why where it's unavailable. */
        private String rowText(int row) {
            if (!picking && section == SEC_ABOUT && row >= 2) {
                if (row == 2) {
                    JSONObject metrics = state.optJSONObject("metrics");
                    JSONObject original = metrics == null ? null : metrics.optJSONObject(HomeMode.ORIGINAL);
                    JSONObject free = metrics == null ? null : metrics.optJSONObject(HomeMode.FREE);
                    return Ui.tr("COMPARISON") + ". " + Ui.tr("Original") + ", " + metric(original, "homeMs")
                            + ". Vibe TV, " + metric(free, "homeMs");
                }
                if (row == 3) return Ui.tr("RECOMMENDATIONS") + ". " + recs.optInt("count");
                JSONArray notices = state.optJSONArray("notices");
                JSONObject latest = notices == null ? null : notices.optJSONObject(0);
                return Ui.tr("NOTICES") + ". " + (latest == null ? Ui.tr("None yet.") : latest.optString("text"));
            }
            String why = unavailable(row);
            String base = rowValueText(row);
            return why == null ? base : base + ", " + Ui.tr("Unavailable") + ". " + why;
        }

        private String rowValueText(int row) {
            if (!picking && section == SEC_ABOUT && row >= 2) return rowText(row);
            if (picking) {
                int at = scroll + row;
                if (at >= pickLabels.size()) return "";
                return pickLabels.get(at) + (at == pickCurrent ? Ui.tr(", selected") : "")
                        + ", " + Ui.trf("{i} of {n}", "i", at + 1, "n", pickLabels.size());
            }
            if (lifted && row == focus && section == SEC_LAYOUT) {
                return layoutLabels()[row] + ", " + Ui.tr("Hold OK to move it.");
            }
            if (lifted && row == focus) {
                String[] app = shelfRows.get(scroll + row);
                int place = shelfOrder.indexOf(app[0]);
                return Ui.trf("Moving {app}, position {n} of {total}", "app", app[1], "n", place + 1, "total", shelfOrder.size());
            }
            if (section == SEC_HOME) {
                return HomeThemeThumbs.NAMES[row] + ", " + themeDescription(row)
                        + (homeChoiceActive(row) ? Ui.tr(". In use") : "");
            }
            if (section == SEC_SHELF || section == SEC_TOP) {
                int at = scroll + row;
                if (at >= shelfRows.size()) return "";
                String[] app = shelfRows.get(at);
                int place = shelfOrder.indexOf(app[0]);
                String where = editingTop ? (place >= 0 ? Ui.trf("in the top buttons, place {n}", "n", place + 1)
                        : Ui.tr("not in the top buttons"))
                        : (place >= 0 ? Ui.trf("on the shelf, place {n}", "n", place + 1) : Ui.tr("not on the shelf"));
                return app[1] + ", " + where;
            }
            String[] labels = rowLabels(), values = rowValues();
            int at = scroll + row;
            if (at >= labels.length) return "";
            String value = values[at];
            return value == null || value.isEmpty() ? labels[at] : labels[at] + ", " + value;
        }

        // MARK: - What each row needs

        /**
         * What the focused section's row `row` needs from the TV (Capabilities.NEED_…), or null. A new option that
         * needs something adds its line here; drawing, keys and TalkBack then treat it as unavailable where the TV
         * lacks it.
         */
        private String need(int row) {
            if (picking) return null;
            switch (section) {
                case SEC_WIDGETS:
                    return (scroll + row == W_PLAYING && !Prefs.nowPlaying())
                            || (scroll + row == W_RESUME && !Prefs.resumeOn()) ? Capabilities.NEED_LISTENER : null;
                case SEC_SLEEP: {
                    int[] rows = powerRows();
                    return row < rows.length && rows[row] == P_WHERE ? Capabilities.NEED_OVERLAY : null;
                }
                case SEC_TOP: {   // only to add it: one already among the buttons can still move or go
                    int at = scroll + row;
                    if (at >= shelfRows.size()) return null;
                    String key = shelfRows.get(at)[0];
                    return HomeApps.TOP_LIVE.equals(key) && !shelfOrder.contains(key) ? Capabilities.NEED_LIVE_TV : null;
                }
                default:
                    return null;
            }
        }

        /** Why row `row` can't be used here, for the person to read; null where it works. */
        private String unavailable(int row) {
            int n = panelRows();
            if (row < 0 || row >= n) return null;
            if (whyRows == null || whyRows.length != n || whyPicking != picking || whySection != section || whyScroll != scroll) {
                whyRows = new String[n];
                whyPicking = picking;
                whySection = section;
                whyScroll = scroll;
                for (int r = 0; r < n; r++) {
                    String need = need(r);
                    whyRows[r] = section == SEC_WIDGETS && !picking && scroll + r >= W_COINS && !Prefs.quotes()
                            ? Ui.tr("Turn on the Quotes widget to pick its coins.")
                            : need == null ? null : Capabilities.missing(getContext(), need);
                }
            }
            return whyRows[row];
        }

        /** OK on an unavailable row: a toast says why; false if it isn't unavailable. */
        private boolean tellWhy() {
            String why = unavailable(focus);
            if (whyToast != null) whyToast.cancel();
            whyToast = why == null ? null : Toast.makeText(getContext(), why, Toast.LENGTH_LONG);
            if (whyToast != null) whyToast.show();
            return why != null;
        }

        // MARK: - The rows' values

        private static String onOff(boolean on) {
            return on ? Ui.tr("On") : Ui.tr("Off");
        }

        /**
         * Sleep Timer's rows: the timer, whether it shows on screen and (where it shows over other apps) where. The
         * Position row is only there while the time left is shown at all.
         */
        private int[] powerRows() {
            return Prefs.sleepShown() ? new int[] {P_SLEEP, P_SHOW, P_WHERE} : new int[] {P_SLEEP, P_SHOW};
        }

        private int powerRow() {
            int[] rows = powerRows();
            return rows[Math.min(focus, rows.length - 1)];
        }

        private int powerFocus(int id) {
            int[] rows = powerRows();
            for (int i = 0; i < rows.length; i++) if (rows[i] == id) return i;
            return 0;
        }

        private String[] powerLabels() {
            int[] rows = powerRows();
            String[] out = new String[rows.length];
            for (int i = 0; i < rows.length; i++) {
                switch (rows[i]) {
                    case P_SLEEP: out[i] = Ui.tr("Sleep Timer"); break;
                    case P_SHOW: out[i] = Ui.tr("Show Time Left"); break;
                    default: out[i] = Ui.tr("Position");
                }
            }
            return out;
        }

        private String[] powerValues() {
            int[] rows = powerRows();
            String[] out = new String[rows.length];
            for (int i = 0; i < rows.length; i++) {
                switch (rows[i]) {
                    case P_SLEEP: out[i] = sleepLeft == 0 ? Ui.tr("Off") : Ui.trf("{time} left", "time", SleepTimer.duration(sleepLeft)); break;
                    case P_SHOW: out[i] = onOff(Prefs.sleepShown()); break;
                    default: out[i] = SleepOverlay.placeName(Prefs.sleepPlace());
                }
            }
            return out;
        }

        private String[] keyLabels() {
            if (RemoteKeys.HISENSE) return new String[] {Ui.tr("Prime Video button"), Ui.tr("YouTube button"),
                    Ui.tr("Disney+ button"), Ui.tr("All Apps button"), Ui.tr("Settings button"), Ui.tr("Profile button")};
            if (RemoteKeys.STEM_NETFLIX) return new String[] {Ui.tr("Netflix button")};
            if (RemoteKeys.KTC) return new String[] {Ui.tr("Tabii button")};
            if (RemoteKeys.XIAOMI) return new String[] {Ui.tr("Mi button"), Ui.tr("YouTube button"), Ui.tr("PatchWall button"),
                    Ui.tr("Okko button"), Ui.tr("VK button"), Ui.tr("Rutube button"), Ui.tr("KION button"),
                    Ui.tr("Wink button"), Ui.tr("IVI button")};
            if (RemoteKeys.SONY) return new String[] {Ui.tr("BRAVIA CORE button"),
                    Ui.tr("Netflix button"), Ui.tr("Disney+ button"),
                    Ui.tr("Prime Video button"), Ui.tr("YouTube button"),
                    Ui.tr("Crunchyroll button"), Ui.tr("Google Play button")};
            return new String[] {Ui.tr("Netflix button"), Ui.tr("YouTube button"),
                    Ui.tr("Prime Video button"), Ui.tr("TCL Channel button")};
        }

        private String[] keyValues() {
            String[] v = new String[Prefs.KEYS.length];
            for (int i = 0; i < v.length; i++) {
                String assigned = Prefs.keyApp(Prefs.KEYS[i]);
                int at = appPkgs.indexOf(assigned);
                v[i] = at >= 0 ? appLabels.get(at) : Ui.tr("Not installed");
            }
            return v;
        }

        /** "auto", then English and every language with a catalogue. */
        private static String[] languageChoices() {
            String[] all = Ui.languages();
            String[] v = new String[all.length + 1];
            v[0] = "auto";
            System.arraycopy(all, 0, v, 1, all.length);
            return v;
        }

        private String versionValue() {
            String v = versionName(getContext());   // "Vibe TV 1.2.3"
            return v.startsWith("Vibe TV ") ? v.substring("Vibe TV ".length()) : v;
        }

        private String tvValue() {
            return Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE;
        }

        // MARK: - Life cycle

        void resume() {
            if (running) return;
            running = true;
            whyRows = null;
            startedAt = SystemClock.uptimeMillis();
            lastNanos = 0;
            animateScroll = Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
            handler.post(poll);
            Choreographer.getInstance().postFrameCallback(this);
            spoken.readAgain();
            Photos.importInbox(getContext(), this::invalidate);   // photos a computer just sent
        }

        void pause() {
            running = false;
            handler.removeCallbacks(poll);
            handler.removeCallbacks(holdStart);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            boolean moving = busy();
            for (Spring s : pop) moving |= !s.settled();
            moving |= !lift.settled() || Math.abs(pageOffset - pageTarget) > 0.5f;
            if (moving) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            k = w / 1920f;
            pageSurface.setShader(new android.graphics.LinearGradient(0, 0, w, h,
                    0xFF161719, 0xFF090A0B, android.graphics.Shader.TileMode.CLAMP));
            Symbols.warm(getContext(), "sparkles", 46 * k);
            Symbols.warm(getContext(), "check", 26 * k);
            Symbols.warm(getContext(), "chevron_right", 24 * k);
            for (String symbol : RAIL_SYMBOLS) Symbols.warm(getContext(), symbol, 28 * k);
            title = Ui.text(Ui.displaySemibold, 32 * k, 0xFFFFFFFF);
            title.setTextAlign(Paint.Align.CENTER);
            version = Ui.text(Ui.regular, 19 * k, 0x66FFFFFF);
            version.setTextAlign(Paint.Align.CENTER);
            sectionTitle = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            rowLabel = Ui.text(Ui.medium, 30 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 28 * k, 0xB3FFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowSub = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            header = Ui.text(Ui.semibold, 21 * k, 0x73FFFFFF);
            header.setLetterSpacing(0.06f);
            cellLabel = Ui.text(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue = Ui.numbers(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue.setTextAlign(Paint.Align.RIGHT);
            warn = Ui.text(Ui.regular, 23 * k, AMBER);
            notice = new TextPaint(Ui.text(Ui.regular, 23 * k, 0xB3FFFFFF));
            hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xFFF5F5F7);
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setMaskFilter(new BlurMaskFilter(26 * k, BlurMaskFilter.Blur.NORMAL));
        }

        // MARK: - State the panel reads

        private boolean busy() {
            return !busyText().isEmpty() || (pendingAt != 0 && SystemClock.uptimeMillis() - pendingAt < 10_000);
        }

        private String busyText() {
            long at = state.optLong("busyAt"), now = SystemClock.elapsedRealtime();
            boolean stale = now < at || now - at > 10 * 60_000L;
            return stale ? "" : state.optString("busy");
        }

        private boolean freeActive() {
            return HomeMode.FREE.equals(state.optString("mode")) && !state.optBoolean("homeReverted");
        }

        private static String versionName(Context c) {
            try {
                return "Vibe TV " + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return "Vibe TV";
            }
        }

        private static String metric(JSONObject m, String key) {
            if (m == null || !m.has(key)) return "—";
            long v = m.optLong(key);
            if (key.equals("homeMs")) return Ui.trf("{n} ms", "n", v);
            if (key.equals("memAvailableMB") && v >= 1024) return Ui.trf("{n} GB", "n", String.format(Ui.locale(), "%.2f", v / 1024.0));
            return Ui.trf("{n} MB", "n", v);
        }

        private static String ago(long at) {
            long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
            if (minutes < 1) return Ui.tr("now");
            if (minutes < 60) return Ui.trf("{n} min ago", "n", minutes);
            if (minutes < 48 * 60) return Ui.trf("{n} h ago", "n", minutes / 60);
            return Ui.plural("plural|one:{n} day ago|other:{n} days ago", minutes / 1440);
        }

        // MARK: - Keys

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    if (!down) return true;
                    int direction = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                    if (lifted) {
                        moveShelfApp(direction);
                    } else if (zone == ZONE_RAIL) {
                        int next = nextRail(focus, direction);
                        if (next != focus) previewSection(next);
                    } else if (picking) {
                        if (direction < 0) {
                            if (focus > 0) focus--;
                            else if (scroll > 0) scroll--;
                        } else if (focus < panelRows() - 1) focus++;
                        else if (scroll + panelRows() < listSize()) scroll++;
                    } else {
                        moveContent(direction);
                    }
                    if (!picking) revealFocusedRow();
                    invalidate();
                    spoken.focusMoved();
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!down || picking) return true;
                    if (lifted) {
                        moveShelfApp(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    } else if (zone == ZONE_RAIL) {
                        if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) selectSection(focus);
                    } else if (section == SEC_HOME && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && focus % 2 == 0) {
                        focus++;
                    } else if (section == SEC_HOME && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && focus % 2 == 1) {
                        focus--;
                    } else if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                        zone = ZONE_RAIL;
                        focus = section;
                    } else if (section == SEC_SLEEP || section == SEC_KEYS || section == SEC_GENERAL || section == SEC_WIDGETS) {
                        adjust(1);
                    }
                    whyRows = null;   // a value changed: what each row needs may have too
                    invalidate();
                    spoken.focusMoved();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (lifted) {
                        // The press that lifted the row is still held: its repeats and release do nothing; a new press drops it.
                        if (down && e.getRepeatCount() == 0) {
                            okDown = false;
                            dropLift();
                        } else if (!down) {
                            okDown = false;
                        }
                        return true;
                    }
                    if (down) {
                        if (e.getRepeatCount() == 0) {
                            okDown = true;
                            if (zone == ZONE_PANEL && !picking && canHold()) {
                                holdArmed = true;
                                handler.removeCallbacks(holdStart);
                                handler.postDelayed(holdStart, HOLD_MS);
                            }
                        } else if (okDown && holdArmed && e.isLongPress()) {
                            startLift();
                        }
                    } else {
                        handler.removeCallbacks(holdStart);
                        holdArmed = false;
                        if (okDown && !e.isCanceled() && spoken.keyClick()) activate();
                        okDown = false;
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                        return true;
                    }
                    if (backDown && !e.isCanceled()) {
                        if (lifted) {
                            cancelLift();
                        } else if (picking) {
                            closePick();
                        } else if (zone == ZONE_PANEL) {
                            zone = ZONE_RAIL;
                            focus = section;
                        } else {
                            backDown = false;
                            activity.finish();  // from the rail, Back leaves the settings
                            return true;
                        }
                    }
                    backDown = false;
                    invalidate();
                    spoken.focusMoved();
                    return true;
                default:
                    return false;
            }
        }

        /** Whether holding OK on the focused row lifts it (the editors, on an app that's already in). */
        private boolean canHold() {
            if (section == SEC_LAYOUT) return focus >= 0 && focus < layoutOrder.size();
            if (section != SEC_SHELF && section != SEC_TOP) return false;
            if (focus >= panelRows() || scroll + focus >= shelfRows.size()) return false;
            return shelfOrder.contains(shelfRows.get(scroll + focus)[0]) && unavailable(focus) == null;
        }

        private int listSize() {
            return picking ? pickLabels.size() : shelfRows.size();
        }

        // MARK: - Activation

        /** OK: the rail opens the section, the panel opens what its row offers. */
        private void activate() {
            Context c = getContext();
            if (zone == ZONE_RAIL && !picking) {
                selectSection(focus);
                return;
            }
            if (tellWhy()) return;
            whyRows = null;
            if (picking) {
                applyPick();
                return;
            }
            if (zone == ZONE_RAIL) {
                selectSection(focus);
                return;
            }
            switch (section) {
                case SEC_HOME: {
                    if (busy()) return;
                    boolean system = focus == 3;
                    if (!system) Prefs.setHomeDesign(HomeThemeThumbs.IDS[focus]);
                    if (system && freeActive()) {
                        HomeMode.request(c, HomeMode.ORIGINAL);
                        pendingAt = SystemClock.uptimeMillis();
                    } else if (!system && !freeActive()) {
                        HomeMode.request(c, HomeMode.FREE);
                        pendingAt = SystemClock.uptimeMillis();
                    }
                    updateDocumentGeometry();
                    invalidate();
                    spoken.focusMoved();
                    return;
                }
                case SEC_LAYOUT:
                    if (focus == layoutOrder.size()) {
                        new AlertDialog.Builder(activity).setTitle(Ui.tr("Reset layout"))
                                .setMessage(Ui.tr("Restore the default section order and visibility?"))
                                .setPositiveButton(Ui.tr("Reset layout"), (dialog, which) -> {
                                    HomeSections.reset(c);
                                    layoutOrder = HomeSections.order(c);
                                    focus = 0;
                                    whyRows = null;
                                    invalidate();
                                }).setNegativeButton(Ui.tr("Cancel"), null).show();
                    } else {
                        String key = layoutOrder.get(focus);
                        if (!HomeSections.setVisible(c, key, !HomeSections.visible(c, key))) {
                            Toast.makeText(c, Ui.tr("Keep at least one section visible."), Toast.LENGTH_SHORT).show();
                        }
                        invalidate();
                    }
                    return;
                case SEC_SAVER: {
                    boolean photos = !Photos.files(getContext()).isEmpty();
                    if (focus == 0) {
                        pause();
                        activity.showPreview();
                        return;
                    }
                    if (focus == 1) {
                        if (photos) Prefs.setPhotos(!Prefs.photos());
                        invalidate();
                        return;
                    }
                    if (focus == 2 && photos) {
                        askRemovePhotos();
                        return;
                    }
                    Prefs.setSaverWatch(!Prefs.saverWatch());
                    invalidate();
                    return;
                }
                case SEC_GENERAL: {
                    if (focus == G_LANGUAGE) {
                        openPicker(PICK_LANGUAGE);
                    } else {
                        adjust(1);
                        invalidate();
                        spoken.focusMoved();
                    }
                    return;
                }
                case SEC_WIDGETS: {
                    int row = focus;
                    switch (row) {
                        case W_CITY:
                            askCity();
                            return;
                        default:
                            adjust(1);
                    }
                    invalidate();
                    spoken.focusMoved();
                    return;
                }
                case SEC_SHELF: case SEC_TOP:
                    toggleShelfApp();
                    invalidate();
                    return;
                case SEC_SLEEP:
                    activatePower();
                    return;
                case SEC_KEYS:
                    openPicker(focus + 1);
                    return;
                case SEC_TV: {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (intent.resolveActivity(c.getPackageManager()) != null) activity.startActivity(intent);
                    return;
                }
                default:
                    return;   // About: Version and This TV are only there to be read
            }
        }

        /** Rail destinations are anchors into the same document, never separate pages. */
        private void selectSection(int to) {
            bindSection(to);
            zone = ZONE_PANEL;
            picking = false;
            lifted = false;
            scroll = 0;
            focus = 0;
            whyRows = null;
            updateDocumentGeometry();
            revealFocusedRow();
            invalidate();
            spoken.focusMoved();
        }

        private void previewSection(int to) {
            bindSection(to);
            zone = ZONE_RAIL;
            picking = false;
            lifted = false;
            scroll = 0;
            focus = to;
            whyRows = null;
            updateDocumentGeometry();
            pageTarget = Math.min(sectionTops[to], maxPageOffset());
        }

        private void moveContent(int direction) {
            int step = section == SEC_HOME ? 2 * direction : direction;
            int next = focus + step;
            if (next >= 0 && next < panelRows()) {
                focus = next;
                return;
            }
            int destination = section + direction;
            while (destination >= 0 && destination < SECTIONS) {
                bindSection(destination);
                if (sectionPresent(destination) && panelRows() > 0) {
                    focus = direction > 0 ? 0 : panelRows() - 1;
                    scroll = 0;
                    whyRows = null;
                    return;
                }
                destination += direction;
            }
            // The document has a real beginning and end; don't wrap or leave the content column.
            bindSection(Math.max(0, Math.min(SECTIONS - 1, section)));
            focus = direction > 0 ? Math.max(0, panelRows() - 1) : 0;
        }

        private boolean sectionPresent(int group) {
            return group != SEC_LAYOUT || "vibe".equals(Prefs.homeDesign());
        }

        private int nextRail(int from, int direction) {
            for (int next = from + direction; next >= 0 && next < SECTIONS; next += direction) {
                if (sectionPresent(next)) return next;
            }
            return from;
        }

        private void bindSection(int to) {
            section = to;
            if (to == SEC_LAYOUT) layoutOrder = HomeSections.order(getContext());
            if (to == SEC_SHELF || to == SEC_TOP) {
                editingTop = to == SEC_TOP;
                EditorData data = editors[editingTop ? 1 : 0];
                shelfRows = data.rows;
                shelfOrder = data.order;
            }
        }

        private int rowsInSection(int at) {
            switch (at) {
                case SEC_HOME: return 4;
                case SEC_LAYOUT: return HomeSections.keys().length + 1;
                case SEC_SAVER: return saverLabels().length;
                case SEC_GENERAL: return 4;
                case SEC_WIDGETS: return widgetsLabels().length;
                case SEC_SHELF: return editors[0].rows.size();
                case SEC_TOP: return editors[1].rows.size();
                case SEC_SLEEP: return powerRows().length;
                case SEC_KEYS: return keyLabels().length;
                case SEC_TV: return 1;
                default: return 5;
            }
        }

        private float rowHeight(int group, int row) {
            if (group == SEC_HOME) return 220;
            if (group == SEC_ABOUT && row >= 2) return row == 2 ? 188 : row == 3 ? 144 : 150;
            return ROW_H;
        }

        private float rowTop(int group, int row) {
            if (group == SEC_HOME) return SECTION_HEAD + (row / 2) * 244;
            float top = SECTION_HEAD;
            for (int i = 0; i < row; i++) top += rowHeight(group, i) + ROW_GAP;
            return top;
        }

        private void updateDocumentGeometry() {
            float top = 0;
            for (int i = 0; i < SECTIONS; i++) {
                sectionTops[i] = top;
                if (!sectionPresent(i)) { sectionHeights[i] = 0; continue; }
                int rows = rowsInSection(i);
                float body = rowTop(i, rows) - SECTION_HEAD;
                float footer = i == SEC_SHELF || i == SEC_TOP || i == SEC_LAYOUT ? 108 : i == SEC_HOME || i == SEC_TV ? 0 : 64;
                if (rows == 0) footer += ROW_H;
                if (i == SEC_HOME) {
                    if (busy()) footer = 100;
                    else if (!state.optString("error").isEmpty()) footer = 220;
                }
                if (i == SEC_ABOUT) footer = 0;
                sectionHeights[i] = SECTION_HEAD + body + footer + SECTION_GAP;
                top += sectionHeights[i];
            }
            documentHeight = top - SECTION_GAP;
            pageTarget = Math.max(0, Math.min(pageTarget, maxPageOffset()));
        }

        private float maxPageOffset() {
            return Math.max(0, documentHeight - (PAGE_BOTTOM - PAGE_TOP));
        }

        private void revealFocusedRow() {
            if (zone == ZONE_RAIL || picking) return;
            updateDocumentGeometry();
            float height = rowHeight(section, focus);
            float top = sectionTops[section] + rowTop(section, focus);
            float bottom = top + height;
            float visible = PAGE_BOTTOM - PAGE_TOP;
            if (top < pageTarget + SECTION_HEAD) pageTarget = Math.max(0, top - SECTION_HEAD);
            else if (bottom > pageTarget + visible - 40) pageTarget = bottom - visible + 40;
            pageTarget = Math.max(0, Math.min(pageTarget, maxPageOffset()));
        }

        /** Changes the focused value one step: left/right adjust it; OK advances toggles and cycles. */
        private void adjust(int dir) {
            if (unavailable(focus) != null) {
                tellWhy();
                return;
            }
            Context c = getContext();
            if (section == SEC_GENERAL) {
                switch (focus) {
                    case G_LANGUAGE: {
                        String[] languages = languageChoices();
                        int at = java.util.Arrays.asList(languages).indexOf(Prefs.language());
                        if (at < 0) at = 0;
                        Prefs.setLanguage(languages[(at + dir + languages.length) % languages.length]);
                        SleepOverlay.update(c);
                        break;
                    }
                    case G_TEMP: {
                        int at = java.util.Arrays.asList(UNITS).indexOf(Prefs.temperature());
                        if (at < 0) at = 0;
                        Prefs.setTemperature(UNITS[(at + dir + UNITS.length) % UNITS.length]);
                        break;
                    }
                    case G_THEME: {
                        int at = java.util.Arrays.asList(THEMES).indexOf(Prefs.theme());
                        if (at < 0) at = 0;
                        Prefs.setTheme(THEMES[(at + dir + THEMES.length) % THEMES.length]);
                        SleepOverlay.update(c);
                        break;
                    }
                    default:
                        Prefs.setSounds(!Prefs.sounds());
                }
                HomeData.get(c).settingsChanged();
                return;
            }
            if (section == SEC_WIDGETS) {
                switch (focus) {
                    case W_CITY:
                        askCity();
                        return;
                    case W_WEATHER:
                        Prefs.setWeather(!Prefs.weather());
                        break;
                    case W_PLAYING:
                        Prefs.setNowPlaying(!Prefs.nowPlaying());
                        HomeMode.nowPlayingAccess(c);
                        break;
                    case W_RESUME:
                        Prefs.setResume(!Prefs.resumeOn());
                        HomeMode.nowPlayingAccess(c);
                        break;
                    case W_QUOTES:
                        Prefs.setQuotes(!Prefs.quotes());
                        break;
                    default:
                        if (focus >= W_COINS) Prefs.setCoin(focus - W_COINS, !Prefs.coin(focus - W_COINS));
                }
                HomeData.get(c).settingsChanged();
                return;
            }
            if (section == SEC_SLEEP) {
                int row = powerRow();
                if (row == P_SLEEP) {
                    SleepTimer.step(getContext(), dir);
                    sleepLeft = SleepTimer.minutesLeft(getContext());
                } else if (row == P_SHOW) {
                    Prefs.setSleepShown(!Prefs.sleepShown());
                    SleepOverlay.update(getContext());
                } else {
                    Prefs.setSleepPlace((Prefs.sleepPlace() + dir + SleepOverlay.PLACES) % SleepOverlay.PLACES);
                    SleepOverlay.update(getContext());
                }
                focus = powerFocus(row);   // rows come and go with the switches above them
                return;
            }
            int n = appPkgs.size();
            if (n == 0) return;
            int at = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[focus]));
            int next = at < 0 ? (dir > 0 ? 0 : n - 1) : (at + dir + n) % n;
            Prefs.setKeyApp(Prefs.KEYS[focus], appPkgs.get(next));
        }

        /** OK on a Sleep Timer row: its list of choices, or (on a switch) the other value. */
        private void activatePower() {
            List<String[]> list = new ArrayList<>();
            switch (powerRow()) {
                case P_SLEEP:
                    list.add(new String[] {"0", Ui.tr("Off")});
                    for (int m : new int[] {5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240}) {
                        list.add(new String[] {String.valueOf(m), SleepTimer.duration(m)});
                    }
                    openChoices(PICK_SLEEP, list, String.valueOf(SleepTimer.minutesLeft(getContext())));
                    return;
                case P_WHERE:
                    for (int i = 0; i < SleepOverlay.PLACES; i++) list.add(new String[] {String.valueOf(i), SleepOverlay.placeName(i)});
                    openChoices(PICK_PLACE, list, String.valueOf(Prefs.sleepPlace()));
                    return;
                default:
                    Prefs.setSleepShown(!Prefs.sleepShown());
                    SleepOverlay.update(getContext());
                    focus = powerFocus(P_SHOW);
                    invalidate();
                    spoken.focusMoved();
            }
        }

        private void openChoices(int which, List<String[]> list, String now) {
            choices.clear();
            choices.addAll(list);
            choiceNow = now;
            openPicker(which);
        }

        // MARK: - The list picker

        private void openPicker(int which) {
            pickFor = which;
            returnRow = focus;
            pickLabels.clear();
            if (which == PICK_CITY) {
                for (String[] city : cities) pickLabels.add(city[0]);
                pickCurrent = -1;
            } else if (which == PICK_LANGUAGE) {
                String[] langs = languageChoices();
                for (String code : langs) {
                    pickLabels.add(code.equals("auto") ? Ui.tr("Automatic") : Ui.languageName(code));
                }
                pickCurrent = java.util.Arrays.asList(langs).indexOf(Prefs.language());
            } else if (which <= PICK_SLEEP) {
                pickCurrent = -1;
                for (int i = 0; i < choices.size(); i++) {
                    pickLabels.add(choices.get(i)[1]);
                    if (choices.get(i)[0].equals(choiceNow)) pickCurrent = i;
                }
            } else {
                pickLabels.addAll(appLabels);
                pickCurrent = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[which - 1]));
            }
            if (pickLabels.isEmpty()) return;
            int visible = Math.min(PANEL_VISIBLE, pickLabels.size());
            int at = Math.max(0, pickCurrent);
            scroll = Math.max(0, Math.min(at - 3, pickLabels.size() - visible));
            focus = at - scroll;
            picking = true;
            whyRows = null;
            invalidate();
            spoken.focusMoved();
        }

        /** OK in the picker: applied, and back to the row that opened it. */
        private void applyPick() {
            Context c = getContext();
            int at = scroll + focus;
            if (at < 0 || at >= pickLabels.size()) {
                closePick();
                return;
            }
            switch (pickFor) {
                case PICK_CITY: {
                    String[] city = cities.get(at);
                    Prefs.setCity(city[1], city[2], city[3]);
                    HomeData.get(c).settingsChanged();
                    closePick();
                    break;
                }
                case PICK_LANGUAGE:
                    Prefs.setLanguage(languageChoices()[at]);
                    SleepOverlay.update(c);   // the pill speaks it too
                    closePick();
                    break;
                case PICK_SLEEP:
                    SleepTimer.set(c, Integer.parseInt(choices.get(at)[0]));
                    sleepLeft = SleepTimer.minutesLeft(c);
                    picking = false;
                    scroll = 0;
                    focus = powerFocus(P_SLEEP);
                    break;
                default:
                    if (pickFor == PICK_PLACE) {
                        Prefs.setSleepPlace(Integer.parseInt(choices.get(at)[0]));
                        SleepOverlay.update(c);
                        picking = false;
                        scroll = 0;
                        focus = powerFocus(P_WHERE);
                    } else {
                        Prefs.setKeyApp(Prefs.KEYS[pickFor - 1], appPkgs.get(at));
                        closePick();
                    }
            }
            invalidate();
            spoken.focusMoved();
        }

        /** Back in the picker: nothing changed, back to the row that opened it. */
        private void closePick() {
            picking = false;
            scroll = 0;
            focus = returnRow;
            whyRows = null;
        }

        private String pickerTitle() {
            return pickFor == PICK_CITY ? Ui.tr("City")
                    : pickFor == PICK_LANGUAGE ? Ui.tr("Language")
                    : pickFor == PICK_SLEEP ? Ui.tr("Sleep Timer") : pickFor == PICK_PLACE ? Ui.tr("Position")
                    : keyLabels()[pickFor - 1];
        }

        // MARK: - The editors (Shelf, Top Buttons)

        private void prepareEditors() {
            for (int i = 0; i < 2; i++) {
                editingTop = i == 1;
                shelfRows = editors[i].rows;
                shelfOrder = editors[i].order;
                if (editingTop) {
                    shelfOrder.addAll(HomeApps.topButtonKeys());
                    rebuildShelfRows(HomeApps.topCandidates());
                } else {
                    PackageManager pm = getContext().getPackageManager();
                    for (String pkg : HomeApps.shelfPackages(getContext())) {
                        if (HomeApps.launchIntent(pm, pkg) != null) shelfOrder.add(pkg);
                    }
                    rebuildShelfRows(HomeApps.shelfCandidates(getContext()));
                }
            }
            bindSection(SEC_HOME);
        }

        /** Selected apps first, in their order, then the rest in the order they came. */
        private void rebuildShelfRows(List<String[]> candidates) {
            shelfRows.clear();
            for (String pkg : shelfOrder) {
                for (String[] cand : candidates) if (cand[0].equals(pkg)) { shelfRows.add(cand); break; }
            }
            for (String[] cand : candidates) if (!shelfOrder.contains(cand[0])) shelfRows.add(cand);
        }

        /** Same rows after a change: keeps the focus on the app that was focused. */
        private void refreshShelfRows(String focusedPkg) {
            List<String[]> all = new ArrayList<>(shelfRows);
            rebuildShelfRows(all);
            for (int i = 0; i < shelfRows.size(); i++) {
                if (!shelfRows.get(i)[0].equals(focusedPkg)) continue;
                scroll = 0;
                focus = i;
                revealFocusedRow();
                break;
            }
            if (editingTop) Prefs.setTopButtons(shelfOrder); else Prefs.setShelf(shelfOrder);
        }

        /** OK: puts the app in (at the end) or takes it off. */
        private void toggleShelfApp() {
            if (scroll + focus < 0 || scroll + focus >= shelfRows.size()) return;
            String pkg = shelfRows.get(scroll + focus)[0];
            if (shelfOrder.contains(pkg)) shelfOrder.remove(pkg);
            else if (editingTop || shelfOrder.size() < HomeApps.maxShelf(getContext())) shelfOrder.add(pkg);   // three buttons: no limit to reach
            else return;
            refreshShelfRows(pkg);
        }

        /** While lifted, ↑ ↓ : moves the app one place earlier or later. */
        private void moveShelfApp(int dir) {
            if (section == SEC_LAYOUT) {
                int to = focus + dir;
                if (to < 0 || to >= layoutOrder.size()) return;
                String key = layoutOrder.remove(focus);
                layoutOrder.add(to, key);
                focus = to;
                HomeSections.setOrder(getContext(), layoutOrder);
                revealFocusedRow();
                invalidate();
                return;
            }
            String pkg = shelfRows.get(scroll + focus)[0];
            int at = shelfOrder.indexOf(pkg), to = at + dir;
            if (at < 0 || to < 0 || to >= shelfOrder.size()) return;
            shelfOrder.remove(at);
            shelfOrder.add(to, pkg);
            refreshShelfRows(pkg);
        }

        /** Hold OK on an app that's in: it is lifted, and the arrows move it until OK drops it (or Back cancels). */
        private void startLift() {
            handler.removeCallbacks(holdStart);
            holdArmed = false;
            if (lifted || picking || !canHold()) return;
            liftSaved.clear();
            liftSaved.addAll(section == SEC_LAYOUT ? layoutOrder : shelfOrder);
            lifted = true;
            lift.target = 1;
            whyRows = null;
            invalidate();
            spoken.focusMoved();
        }

        /** OK while lifted: the app stays where it is (every move was saved as it happened). */
        private void dropLift() {
            lifted = false;
            lift.target = 0;
            whyRows = null;
            invalidate();
            spoken.focusMoved();
        }

        /** Back while lifted: the order goes back to what it was before the lift. */
        private void cancelLift() {
            if (section == SEC_LAYOUT) {
                String key = layoutOrder.get(focus);
                HomeSections.setOrder(getContext(), liftSaved);
                layoutOrder = HomeSections.order(getContext());
                focus = Math.max(0, layoutOrder.indexOf(key));
                revealFocusedRow();
                dropLift();
                return;
            }
            String pkg = shelfRows.get(scroll + focus)[0];
            shelfOrder.clear();
            shelfOrder.addAll(liftSaved);
            refreshShelfRows(pkg);
            dropLift();
        }

        // MARK: - City and photos

        private void askCity() {
            if (Ui.tr("Searching…").equals(cityStatus)) return;
            EditText field = new EditText(activity);
            field.setSingleLine(true);
            field.setHint(Ui.tr("Type the city's name"));
            field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(Ui.tr("City"))
                    .setView(field)
                    .setPositiveButton(Ui.tr("Search"), (d, which) -> searchCity(field.getText().toString()))
                    .setNegativeButton(Ui.tr("Cancel"), null)
                    .create();
            field.setOnEditorActionListener((v, action, event) -> {
                dialog.dismiss();
                searchCity(field.getText().toString());
                return true;
            });
            dialog.show();
        }

        private void searchCity(String name) {
            if (name.trim().isEmpty()) return;
            cityStatus = Ui.tr("Searching…");
            invalidate();
            new Thread(() -> {
                List<String[]> found;
                String error;
                try {
                    found = HomeData.findCity(name);
                    error = found.isEmpty() ? Ui.tr("No city with that name.") : "";
                } catch (Exception e) {
                    found = new ArrayList<>();
                    error = Ui.trf("Couldn't search now: {error}", "error", e.getMessage());
                    AppLog.w("city search: " + e.getMessage());
                }
                List<String[]> result = found;
                String message = error;
                handler.post(() -> {
                    cityStatus = message;
                    if (!result.isEmpty() && zone == ZONE_PANEL && section == SEC_WIDGETS && !picking) {
                        cities.clear();
                        cities.addAll(result);
                        openPicker(PICK_CITY);
                    }
                    invalidate();
                    if (!message.isEmpty()) handler.postDelayed(() -> { cityStatus = ""; invalidate(); }, 4000);
                });
            }).start();
        }

        /** OK on Remove all: asks, then deletes the photos sent from a computer (tools/photos.py). */
        private void askRemovePhotos() {
            int n = Photos.files(getContext()).size();
            if (n == 0) return;
            AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(Ui.plural("plural|one:Remove {n} photo?|other:Remove {n} photos?", n))
                    .setPositiveButton(Ui.tr("Remove"), (d, which) -> {
                        Photos.removeAll(getContext());
                        whyRows = null;
                        invalidate();
                        spoken.focusMoved();
                    })
                    .setNegativeButton(Ui.tr("Cancel"), null)
                    .show();
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();   // one stray OK keeps them
        }

        // MARK: - Drawing

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            for (int i = 0; i < pop.length; i++) {
                boolean on;
                if (i < SECTIONS) {
                    on = zone == ZONE_RAIL && focus == i;
                } else {
                    int row = i - SECTIONS;
                    on = zone == ZONE_PANEL && focus == row && row < panelRows();
                }
                pop[i].target = on ? 1 : 0;
                if (animateScroll) pop[i].step(dt); else pop[i].value = pop[i].target;
            }
            lift.target = lifted ? 1 : 0;
            lift.step(dt);
            long up = SystemClock.uptimeMillis();
            float seconds = (up - startedAt) / 1000f;
            updateDocumentGeometry();
            float delta = pageTarget - pageOffset;
            pageOffset = !animateScroll || Math.abs(delta) < 0.5f ? pageTarget : pageOffset + delta * (1 - (float) Math.exp(-18 * dt));
            c.drawColor(0xFF0C0D0F);
            c.drawRect(0, 0, w, h, pageSurface);
            drawRail(c);
            divider.setColor(0x33FFFFFF);
            divider.setStrokeWidth(Math.max(1, k));
            c.drawLine(478 * k, PAGE_TOP * k, 478 * k, PAGE_BOTTOM * k, divider);
            if (picking) drawPanel(c, seconds);
            else drawDocument(c, seconds);
        }

        /** The rail: the identity on top, then one item per section (the focused one a white pill). */
        private void drawRail(Canvas c) {

            Symbols.draw(getContext(), c, "sparkles", 126 * k, 121 * k, 30 * k, 0xEBFFFFFF);
            title.setTextAlign(Paint.Align.LEFT);
            c.drawText("Vibe TV", 162 * k, 132 * k, title);
            version.setTextAlign(Paint.Align.LEFT);
            c.drawText(Ui.tr("Settings"), 100 * k, 185 * k, version);
            version.setTextAlign(Paint.Align.CENTER);
            String[] labels = sectionLabels();
            int visibleIndex = 0;
            for (int i = 0; i < SECTIONS; i++) {
                if (!sectionPresent(i)) continue;
                float top = RAIL_TOP + visibleIndex++ * RAIL_PITCH;
                rowRect.set(RAIL_ITEM_L * k, top * k, RAIL_ITEM_R * k, (top + RAIL_ITEM_H) * k);
                float f = drawRowBackground(c, i, 0);
                String why = railWhy(i);
                int rest = why != null ? 0x66FFFFFF : i == section ? 0xFFFFFFFF : 0x99FFFFFF;
                int ink = blend(rest, INK, f);
                rowLabel.setColor(ink);
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                Symbols.draw(getContext(), c, RAIL_SYMBOLS[i], 134 * k, rowRect.centerY(), 28 * k, ink);
                c.drawText(TextUtils.ellipsize(labels[i], new TextPaint(rowLabel), 250 * k, TextUtils.TruncateAt.END).toString(),
                        166 * k, base, rowLabel);
            }
        }

        /** The panel: a glass card with the section's title and its rows (or the list picker). */
        private void drawPanel(Canvas c, float seconds) {

            float hx = (PANEL_L + ROW_INSET + TEXT_INSET) * k;
            c.drawText(picking ? pickerTitle() : sectionLabels()[section], hx, (ROWS_TOP - 40) * k, sectionTitle);
            if (picking) drawPicker(c);
            else if (section == SEC_HOME) drawHomePanel(c, hx, seconds);
            else if (section == SEC_SHELF || section == SEC_TOP) drawEditor(c);
            else drawRows(c, hx);
        }

        /** Sections share one clipped document; short groups reveal the following heading. */
        private void drawDocument(Canvas c, float seconds) {
            int activeSection = section, activeFocus = focus, activeScroll = scroll;
            boolean activeTop = editingTop, activeLifted = lifted;
            List<String[]> activeRows = shelfRows;
            List<String> activeOrder = shelfOrder, activeLayout = layoutOrder;
            drawingFocusSection = activeSection;
            drawingFocusRow = activeFocus;
            c.save();
            c.clipRect((PANEL_L - 16) * k, PAGE_TOP * k, (PANEL_R + 16) * k, PAGE_BOTTOM * k);
            try {
                for (int i = 0; i < SECTIONS; i++) {
                    float top = PAGE_TOP + sectionTops[i] - pageOffset;
                    if (!sectionPresent(i) || top + sectionHeights[i] < PAGE_TOP || top > PAGE_BOTTOM) continue;
                    bindSection(i);
                    drawingSection = i;
                    lifted = activeLifted && i == activeSection;
                    focus = i == activeSection ? activeFocus : -1;
                    scroll = 0;
                    whyRows = null;
                    c.save();
                    c.translate(0, (top + SECTION_HEAD - ROWS_TOP) * k);
                    float hx = hx2();
                    if (i > 0) {
                        divider.setColor(0x26FFFFFF);
                        c.drawLine((PANEL_L + ROW_INSET) * k, (ROWS_TOP - SECTION_HEAD - 24) * k,
                                (PANEL_R - ROW_INSET) * k, (ROWS_TOP - SECTION_HEAD - 24) * k, divider);
                    }
                    c.drawText(sectionLabels()[i], hx, (ROWS_TOP - 28) * k, sectionTitle);
                    if (i == SEC_HOME) drawHomePanel(c, hx, seconds);
                    else if (i == SEC_SHELF || i == SEC_TOP) drawEditor(c);
                    else drawRows(c, hx);
                    if (i == SEC_ABOUT) drawAboutDetails(c, hx);
                    c.restore();
                }
            } finally {
                section = activeSection;
                focus = activeFocus;
                scroll = activeScroll;
                editingTop = activeTop;
                lifted = activeLifted;
                shelfRows = activeRows;
                shelfOrder = activeOrder;
                layoutOrder = activeLayout;
                drawingSection = -1;
                whyRows = null;
                c.restore();
            }
        }

        /** List row: no background; focused, white with a shadow (Apple TV's focus). Returns how focused it is. */
        private float drawRowBackground(Canvas c, int i, float liftScale) {
            float f = i < SECTIONS ? Ui.clamp(pop[i].value)
                    : drawingSection >= 0 ? (zone == ZONE_PANEL && drawingSection == drawingFocusSection
                            && i - SECTIONS == drawingFocusRow ? 1 : 0)
                    : (zone == ZONE_PANEL && i - SECTIONS == focus ? 1 : 0);
            if (f > 0.01f) {
                float s = (1 + 0.02f * f) * (1 + liftScale);
                float cx = rowRect.centerX(), cy = rowRect.centerY();
                tmp.set(cx - rowRect.width() * s / 2, cy - rowRect.height() * s / 2, cx + rowRect.width() * s / 2, cy + rowRect.height() * s / 2);
                float r = 18 * k * s;
                tmp.offset(0, 10 * k * f);
                shadow.setColor(Ui.alpha(0xFF000000, 0.4f * f));
                c.drawRoundRect(tmp, r, r, shadow);
                tmp.offset(0, -10 * k * f);
                white.setAlpha(Math.round(255 * f));
                c.drawRoundRect(tmp, r, r, white);
                rowRect.set(tmp);
            }
            return f;
        }

        private void rowRectAt(int i, float height) {
            float pitch = height + ROW_GAP;
            rowRect.set((PANEL_L + ROW_INSET) * k, (ROWS_TOP + i * pitch) * k, (PANEL_R - ROW_INSET) * k, (ROWS_TOP + i * pitch + height) * k);
        }

        /** A long list (the apps, the languages): nine rows at a time, the one in use checked. */
        private void drawPicker(Canvas c) {
            int rows = panelRows();
            for (int i = 0; i < rows; i++) {
                int at = scroll + i;
                rowRectAt(i, ROW_H);
                if (c.quickReject(rowRect, Canvas.EdgeType.AA)) continue;
                float f = drawRowBackground(c, SECTIONS + i, 0);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                c.drawText(pickLabels.get(at), rowRect.left + TEXT_INSET * k, rowRect.centerY() + Ui.halfCap(rowLabel), rowLabel);
                if (at == pickCurrent) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - TEXT_INSET * k - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
            float hy = (ROWS_TOP + rows * (ROW_H + ROW_GAP) + 36) * k;
            c.drawText((scroll + focus + 1) + " / " + pickLabels.size() + "  ·  " + Ui.tr("OK picks, Back cancels."),
                    (PANEL_L + ROW_INSET + TEXT_INSET) * k, hy, hint);
        }

        /** Home Screen: the two picks, then how they compare, the picks' status and the last notices. */
        private boolean homeChoiceActive(int index) {
            return index == 3 ? !freeActive() : freeActive() && HomeThemeThumbs.IDS[index].equals(Prefs.homeDesign());
        }

        private String themeDescription(int index) {
            switch (index) {
                case 0: return Ui.tr("Your home, with customizable sections.");
                case 1: return Ui.tr("The original Simba home layout.");
                case 2: return Ui.tr("An independent Impeccable design.");
                default: return Ui.tr("The TV's factory home screen");
            }
        }

        private void drawHomePanel(Canvas c, float hx, float seconds) {
            float width = (PANEL_R - PANEL_L - 2 * ROW_INSET - 24) / 2;
            for (int i = 0; i < 4; i++) {
                float left = PANEL_L + ROW_INSET + (i % 2) * (width + 24);
                float top = ROWS_TOP + (i / 2) * 244;
                rowRect.set(left * k, top * k, (left + width) * k, (top + 220) * k);
                if (c.quickReject(rowRect, Canvas.EdgeType.AA)) continue;
                float f = drawRowBackground(c, SECTIONS + i, 0);
                RectF art = new RectF((left + 16) * k, (top + 20) * k, (left + 288) * k, (top + 173) * k);
                themeThumbs.draw(c, art, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowSub.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                rowLabel.setTextSize(26 * k);
                c.drawText(HomeThemeThumbs.NAMES[i], (left + 308) * k, (top + 55) * k, rowLabel);
                TextPaint description = new TextPaint(rowSub);
                int textWidth = Math.max(1, Math.round((width - 332) * k));
                StaticLayout text = StaticLayout.Builder.obtain(themeDescription(i), 0, themeDescription(i).length(), description, textWidth)
                        .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build();
                c.save();
                c.translate((left + 308) * k, (top + 78) * k);
                text.draw(c);
                c.restore();
                if (homeChoiceActive(i)) Symbols.draw(getContext(), c, "check", (left + width - 34) * k,
                        (top + 186) * k, 24 * k, blend(0xFFFFFFFF, INK, f));
                rowLabel.setTextSize(30 * k);
            }
            if (busy() || !state.optString("error").isEmpty()) {
                warn.setColor(busy() ? 0xB3FFFFFF : AMBER);
                String status = busy() ? busyText() : state.optString("error");
                c.drawText(TextUtils.ellipsize(status, new TextPaint(warn), (PANEL_R - PANEL_L - 80) * k,
                        TextUtils.TruncateAt.END).toString(), hx, (ROWS_TOP + 530) * k, warn);
            }
        }

        private String sectionName(String key) {
            switch (key) {
                case "actions": return Ui.tr("Top Buttons");
                case "weather": return Ui.tr("Weather widget");
                case "apps": return Ui.tr("Shelf");
                case "continue": return Ui.tr("Continue Watching");
                case "for-you": return Ui.tr("For You");
                case "anime": return Ui.tr("Anime");
                case "movies": return Ui.tr("Movies");
                default: return Ui.tr("TV Shows");
            }
        }

        private String[] layoutLabels() {
            List<String> order = layoutOrder.isEmpty() ? HomeSections.order(getContext()) : layoutOrder;
            String[] labels = new String[order.size() + 1];
            for (int i = 0; i < order.size(); i++) labels[i] = sectionName(order.get(i));
            labels[order.size()] = Ui.tr("Reset layout");
            return labels;
        }

        private String[] layoutValues() {
            String[] values = new String[layoutOrder.size() + 1];
            for (int i = 0; i < layoutOrder.size(); i++) values[i] = "#" + (i + 1) + " · " + onOff(HomeSections.visible(getContext(), layoutOrder.get(i)));
            values[layoutOrder.size()] = "";
            return values;
        }

        private float drawComparison(Canvas c, float hx, float y) {
            float colA = (PANEL_R - ROW_INSET - 220) * k, colB = (PANEL_R - ROW_INSET - TEXT_INSET) * k;
            c.drawText(Ui.tr("COMPARISON"), hx, y, header);
            cellValue.setColor(0x73FFFFFF);
            c.drawText(Ui.tr("Original"), colA, y, cellValue);
            c.drawText("Vibe TV", colB, y, cellValue);
            cellValue.setColor(0xD9FFFFFF);
            JSONObject metrics = state.optJSONObject("metrics");
            JSONObject a = metrics == null ? null : metrics.optJSONObject(HomeMode.ORIGINAL);
            JSONObject b = metrics == null ? null : metrics.optJSONObject(HomeMode.FREE);
            String[][] rows = {{Ui.tr("Home button"), "homeMs"}, {Ui.tr("Free memory"), "memAvailableMB"},
                    {Ui.tr("Home screen memory"), "homePssMB"}};
            for (int i = 0; i < rows.length; i++) {
                float base = y + (46 + 40 * i) * k;
                c.drawText(rows[i][0], hx, base, cellLabel);
                c.drawText(metric(a, rows[i][1]), colA, base, cellValue);
                c.drawText(metric(b, rows[i][1]), colB, base, cellValue);
            }
            return y + (46 + 40 * rows.length) * k;
        }

        private void drawAboutDetails(Canvas c, float hx) {
            for (int row = 2; row < 5; row++) {
                float top = ROWS_TOP + rowTop(SEC_ABOUT, row) - SECTION_HEAD;
                rowRect.set((PANEL_L + ROW_INSET) * k, top * k,
                        (PANEL_R - ROW_INSET) * k, (top + rowHeight(SEC_ABOUT, row)) * k);
                if (zone == ZONE_PANEL && drawingFocusSection == SEC_ABOUT && drawingFocusRow == row) {
                    divider.setColor(0xE6FFFFFF);
                    divider.setStyle(Paint.Style.STROKE);
                    divider.setStrokeWidth(2 * k);
                    c.drawRoundRect(rowRect, 18 * k, 18 * k, divider);
                    divider.setStyle(Paint.Style.FILL);
                }
                float y = (top + 30) * k;
                if (row == 2) drawComparison(c, hx, y);
                else if (row == 3) drawRecs(c, hx, y);
                else drawNotices(c, hx, y);
            }
        }

        /** Home screen picks: status, and the credit TMDB asks for. */
        private float drawRecs(Canvas c, float hx, float y) {
            int width = Math.round((PANEL_R - ROW_INSET - (PANEL_L + ROW_INSET + TEXT_INSET)) * k);
            c.drawText(Ui.tr("RECOMMENDATIONS"), hx, y, header);
            String line;
            String error = recs.optString("error");
            int count = recs.optInt("count");
            if (!hasKey) line = Ui.tr("No TMDB key: see the README (config.properties).");
            else if (error.equals(Recs.NO_KNOWN_APPS)) line = Ui.tr("None of the TV's streaming apps can give picks.");
            else if (error.equals(Recs.NO_APPS)) line = Ui.trf("TMDB has none of the TV's apps for tmdb_region={region}.", "region", Recs.REGION);
            else if (!error.isEmpty()) line = Ui.trf("Error: {error}.", "error", error);
            else if (count > 0) line = Ui.trf("On · {n} titles from the TV's apps.", "n", count);
            else line = Ui.tr("Fetching titles…");
            c.drawText(TextUtils.ellipsize(line, notice, width, TextUtils.TruncateAt.END).toString(), hx, y + 42 * k, notice);
            version.setTextAlign(Paint.Align.LEFT);
            c.drawText(Ui.tr("This product uses the TMDB API but is not endorsed or certified by TMDB."), hx, y + 78 * k, version);
            if (!Config.WATCHMODE_KEY.trim().isEmpty()) {
                c.drawText(Ui.tr("Links to the titles by Watchmode."), hx, y + 108 * k, version);
            }
            version.setTextAlign(Paint.Align.CENTER);
            return y + (Config.WATCHMODE_KEY.trim().isEmpty() ? 78 : 108) * k;
        }

        private void drawNotices(Canvas c, float hx, float y) {
            c.drawText(Ui.tr("NOTICES"), hx, y, header);
            JSONArray notices = state.optJSONArray("notices");
            int shown = 0;
            for (int i = 0; notices != null && i < notices.length() && shown < 2; i++) {
                JSONObject o = notices.optJSONObject(i);
                if (o == null) continue;
                String line = ago(o.optLong("at")) + " · " + o.optString("text");
                c.drawText(TextUtils.ellipsize(line, notice, 1100 * k, TextUtils.TruncateAt.END).toString(), hx, y + (42 + 38 * shown) * k, notice);
                shown++;
            }
            if (shown == 0) c.drawText(Ui.tr("None yet."), hx, y + 42 * k, notice);
        }

        /** The plain list sections: label on the left, value on the right, a hint at the bottom. */
        private void drawRows(Canvas c, float hx) {
            String[] labels = rowLabels(), values = rowValues();
            int rows = section == SEC_ABOUT ? 2 : panelRows(), from = scroll;
            for (int i = 0; i < rows; i++) {
                int at = from + i;
                rowRectAt(i, ROW_H);
                if (c.quickReject(rowRect, Canvas.EdgeType.AA)) continue;
                float f = drawRowBackground(c, SECTIONS + i, 0);
                boolean locked = unavailable(i) != null;
                rowLabel.setColor(blend(locked ? 0x73FFFFFF : 0xF2FFFFFF, INK, f));
                rowValue.setColor(blend(locked ? 0x59FFFFFF : 0xB3FFFFFF, locked ? 0x591C1C1E : 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                String value = locked ? Ui.tr("Unavailable") : values[at];
                float valueWidth = value == null ? 0 : rowValue.measureText(value);
                float labelWidth = Math.max(120 * k, rowRect.width() - 2 * TEXT_INSET * k - valueWidth - 48 * k);
                c.drawText(TextUtils.ellipsize(labels[at], new TextPaint(rowLabel), labelWidth,
                        TextUtils.TruncateAt.END).toString(), rowRect.left + TEXT_INSET * k, base, rowLabel);
                if (section == SEC_TV) {
                    Symbols.draw(getContext(), c, "chevron_right", rowRect.right - TEXT_INSET * k - 12 * k,
                            rowRect.centerY(), 24 * k, rowValue.getColor());
                } else {
                    if (value != null && !value.isEmpty()) {
                        c.drawText(value, rowRect.right - TEXT_INSET * k, base, rowValue);
                    }
                }
            }
            float hy = (ROWS_TOP + rows * (ROW_H + ROW_GAP) + 36) * k;
            switch (section) {
                case SEC_LAYOUT:
                    c.drawText(Ui.tr("OK shows or hides a section."), hx, hy, hint);
                    c.drawText(lifted ? Ui.tr("↑ ↓ to move · OK to drop · Back to cancel") : Ui.tr("Hold OK to move it."), hx, hy + 34 * k, hint);
                    break;
                case SEC_SAVER:
                    if (Photos.files(getContext()).isEmpty()) {
                        c.drawText(Ui.tr("Send them from a computer: see “Your own photos” in the README."), hx, hy, hint);
                    }
                    break;
                case SEC_GENERAL:
                    c.drawText(Ui.tr("OK opens the list. Back returns to sections."), hx, hy, hint);
                    break;
                case SEC_WIDGETS:
                    c.drawText(Ui.tr("OK changes the value. Back returns to sections."), hx, hy, hint);
                    break;
                case SEC_SLEEP:
                    c.drawText(Ui.tr("OK opens the list. Back returns to sections."), hx, hy, hint);
                    break;
                case SEC_KEYS:
                    c.drawText(RemoteKeys.VESTEL ? Ui.tr("YouTube, Prime Video and Google Play: the TV itself controls those buttons.")
                            : RemoteKeys.KTC ? Ui.tr("Netflix, YouTube and Prime Video: the TV itself controls those buttons.")
                            : RemoteKeys.SONY ? Ui.tr("Sony default: the button opens what it always did.")
                            : RemoteKeys.FORMOVIE ? Ui.tr("The other buttons: the TV itself controls them.")
                            : RemoteKeys.XIAOMI ? Ui.tr("Xiaomi default: the button opens what it always did.")
                            : RemoteKeys.HISENSE ? Ui.tr("Netflix: the TV itself controls that button.")
                            : RemoteKeys.supported() ? Ui.tr("Disney and Globoplay: the TV itself controls those buttons.")
                            : Ui.tr("TCL remote buttons: on this TV they work as always."), hx, hy, hint);
                    break;
            }
        }

        /** The editors (Shelf, Top Buttons): what each can hold, checked, in place; holding OK lifts a row to move it. */
        private void drawEditor(Canvas c) {
            int rows = panelRows();
            if (shelfRows.isEmpty()) {
                rowLabel.setColor(0xB3FFFFFF);
                c.drawText(Ui.tr("No apps available."), hx2(), ROWS_TOP * k + Ui.halfCap(rowLabel), rowLabel);
                return;
            }
            for (int i = 0; i < rows; i++) {
                String[] app = shelfRows.get(scroll + i);
                int place = shelfOrder.indexOf(app[0]);
                rowRectAt(i, ROW_H);
                if (c.quickReject(rowRect, Canvas.EdgeType.AA)) continue;
                float liftScale = lifted && i == focus ? 0.04f * lift.value : 0;
                float f = drawRowBackground(c, SECTIONS + i, liftScale);
                boolean off = unavailable(i) != null;
                rowLabel.setColor(off ? blend(0x73FFFFFF, 0x731C1C1E, f)
                        : blend(place >= 0 ? 0xF2FFFFFF : 0x80FFFFFF, place >= 0 ? INK : 0x801C1C1E, f));
                rowValue.setColor(off ? blend(0x59FFFFFF, 0x591C1C1E, f) : blend(0xB3FFFFFF, 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(TextUtils.ellipsize(app[1], new TextPaint(rowLabel), 900 * k, TextUtils.TruncateAt.END).toString(),
                        rowRect.left + TEXT_INSET * k, base, rowLabel);
                float right = rowRect.right - TEXT_INSET * k;
                if (lifted && i == focus) {
                    c.drawText("← →", right, base, rowValue);
                } else {
                    if (place >= 0 && !off) {
                        // In: a check, as in the pickers, before its place.
                        float cw = Symbols.width(getContext(), "check", 26 * k);
                        Symbols.draw(getContext(), c, "check", right - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                        right -= cw + 16 * k;
                    }
                    c.drawText(off ? Ui.tr("Unavailable") : place >= 0 ? "#" + (place + 1) : Ui.tr("shelf|Off"), right, base, rowValue);
                }
            }
            float hy = (ROWS_TOP + rows * (ROW_H + ROW_GAP) + 36) * k;
            if (lifted) {
                c.drawText(Ui.tr("↑ ↓ to move · OK to drop · Back to cancel"), hx2(), hy, hint);
            } else {
                c.drawText(editingTop ? Ui.tr("OK adds or removes a button.")
                        : HomeApps.maxShelf(getContext()) == Integer.MAX_VALUE ? Ui.tr("OK adds or removes an app.")
                        : Ui.trf("OK adds or removes (up to {n}).", "n", HomeApps.maxShelf(getContext())),
                        hx2(), hy, hint);
                c.drawText(Ui.tr("Hold OK to move it."), hx2(), hy + 34 * k, hint);
                if (!editingTop && HomeApps.lowRam(getContext())) {
                    c.drawText(Ui.tr("This TV has little memory, so the shelf holds fewer apps."), hx2(), hy + 68 * k, hint);
                }
            }
        }

        private float hx2() {
            return (PANEL_L + ROW_INSET + TEXT_INSET) * k;
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        // MARK: - TalkBack's bounds

        private RectF railBox(int i) {
            int visibleIndex = 0;
            for (int at = 0; at < i; at++) if (sectionPresent(at)) visibleIndex++;
            float top = RAIL_TOP + visibleIndex * RAIL_PITCH;
            focusBox.set(RAIL_ITEM_L * k, top * k, RAIL_ITEM_R * k, (top + RAIL_ITEM_H) * k);
            return focusBox;
        }

        private RectF rowBox(int row) {
            float height = picking ? ROW_H : rowHeight(section, row);
            float top = picking ? ROWS_TOP + row * (height + ROW_GAP)
                    : PAGE_TOP + sectionTops[section] + rowTop(section, row) - pageOffset;
            float left = PANEL_L + ROW_INSET, right = PANEL_R - ROW_INSET;
            if (!picking && section == SEC_HOME) {
                float width = (right - left - 24) / 2;
                left += (row % 2) * (width + 24);
                right = left + width;
            }
            rowRect.set(left * k, top * k, right * k, (top + height) * k);
            return rowRect;
        }
    }
}

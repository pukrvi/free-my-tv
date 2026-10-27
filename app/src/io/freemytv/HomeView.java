package io.freemytv;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The home screen. Everything stacks from the top:
 *  - the quick-action row first: All Apps, Sleep Timer — which opens its picker right here — Home Settings,
 *    TV Settings, Live TV and the HDMI inputs;
 *  - the weather strip (and with the crypto prices when they're on, so it splits in two);
 *  - the glass app dock (the first tile is focused by default);
 *  - four vertically stacked recommendation rows, with horizontally scrolling cards and a fixed focus;
 *  - the glass reflects the current background; from sunset to sunrise, a night look: dark background, smoked glass and
 *    white text.
 * Measurements are in a 1920×1080 space (k = width / 1920). The screen is split into layers so the TV's weak GPU only
 * redraws what changed.
 */
final class HomeView extends ViewGroup implements HomeSurface, Choreographer.FrameCallback, HomeData.Listener, Recs.Listener {
    // tvOS grid (Human Interface Guidelines): content inside the safe area, 80 px from the sides and 60 from top and
    // bottom; 40 px between items; 7 columns of 217 px (the apps). The shelf glass extends 36 px past the tiles (the
    // same padding on all four sides) and the tile corners are concentric with it (glass radius = radius + padding).
    private static final float MARGIN = 80, CONTENT_R = 1840;
    // Dock tiles have corners concentric with the glass (glass radius = tile radius + padding).
    // Shelf like the tvOS 27 Dock: icons in each brand's color, 48 apart, and the glass bar hugging them with 40 of
    // padding (the focused icon grows 20% without touching its edge). The icons are 16:9, the shape of the apps' TV
    // banners, so those are never stretched: up to 224 × 126, 178 × 100 with eight apps, 133 × 75 with ten
    // (HomeApps.shelfVisible; past that the shelf scrolls).
    private static final float SHELF_PAD = 40, TILE_GAP = 48;
    private static final float TILE_RADIUS = 22;
    private static final float CARD_RADIUS = 30;
    // Tile focus (Apple TV style): it grows from the center and its shadow drops (it "rises" toward the viewer).
    private static final float FOCUS_GROW = 0.2f;
    // A 40-unit rhythm separates the quick buttons, weather, dock, and recommendation rows.
    private static final float GAP = 40;
    private static final float ROW_H = 126, CHIPS_TOP = 24, STRIP_TOP = CHIPS_TOP + ROW_H + GAP, STRIP_H = 220;
    private static final float FORECAST_LINE_GAP = 16;
    private static final float WEATHER_CITY_BASE = 38, WEATHER_SUN_BASE = 176;
    private static final float ROWS_TOP = 696, ROW_PITCH = 384, ROW_BAND_H = 384;
    private static final float ROW_CARD_TOP = 52, ROW_CARD_W = 420, ROW_CARD_H = 236, ROW_CARD_GAP = 28;
    private static final float PAGE_TOP = 150, PAGE_BOTTOM = 1080;
    // "All Apps" sheet.
    private static final int COLUMNS = 5, VISIBLE_ROWS = 3;
    private static final float GRID_W = 300, GRID_H = 169, GRID_GAP = 40, GRID_ROW = 239, GRID_TOP = 196, GRID_RADIUS = 22;
    private static final int SMOKE = 0xFF111113, INK = 0xFF1C1C1E, WHITE = 0xFFF5F5F7;
    private static final int UP_COLOR = 0xFF30D158, DOWN_COLOR = 0xFFFF453A;
    private static final long TOAST_MS = 6000, FADE_MS = 800, ROW_REFRESH_MS = 10 * 60_000L;
    /** Blur radii of the shadow and shadowDeep paints (Shadows needs them to draw the same blur on older TVs). */
    private static final float SHADOW_BLUR = 26, DEEP_BLUR = 30;
    private static final int SKY = 0, STRIP = 1, PLATTER = 4, TILES = 5, DRAWER = 6, SHEET = 7, TOAST = 8,
            ROW0 = 9, ROW1 = 10, ROW2 = 11, ROW3 = 12, RESUME = 13;
    private static final int Z_SHELF = 2, Z_DRAWER = 3, Z_ROW0 = 20, Z_RESUME = 24;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    // The dock layer begins at 430; its 20-unit inset puts the glass at 450..656 and the tiles at 490..616.
    private static final float L_DOCK_TOP = 430, L_PLATTER_TOP = 20, L_TILE_H = 126;
    private static final int L_INK = 0xFF1D1D1F, L_DIM = 0xFF6E6E73, L_FAINT = 0xFF8E8E93;

    private final Activity activity;
    private final Scene scene = new Scene();
    private final HomeData data;
    private final Recs recs;
    private final Click click;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences ui;
    private final TvInputManager tvInputs;
    private final Spoken spoken = new Spoken(this, new Spoken.Source() {
        @Override public void items(List<Integer> out) { a11yItems(out); }
        @Override public String text(int id) { return a11yText(id); }
        @Override public boolean bounds(int id, Rect out) { return a11yBounds(id, out); }
        @Override public int focused() { return a11yFocused(); }
        @Override public void focus(int id) { a11yFocus(id); }
        @Override public void click(int id) { activate(); }
        @Override public boolean clickable(int id) { return true; }
        @Override public boolean holdable(int id) {
            int group = (id - 1) / 1000;
            return group == GRID && canToggleShelf((id - 1) % 1000) || group == Z_RESUME || isRowZone(group);
        }
        @Override public void hold(int id) { a11yFocus(id); if (canManageCard()) openCardMenu(); else a11yHold(); }
    });
    private final RectF a11yRect = new RectF();
    private final Layer sky, strip, platter, tiles, drawerLayer, sheetLayer, toastLayer;
    private final Layer[] rowLayers = new Layer[4];
    private int zone = Z_SHELF;
    private final List<String> sectionOrder;
    private final Map<String, Boolean> sectionEnabled = new HashMap<>();
    private final Map<String, Float> sectionTops = new HashMap<>();
    private float sectionDocumentBottom;

    // Night look (like Apple's "Automatic" appearance): darker from sunset to sunrise, with a 2 s transition.
    private static final long NIGHT_MS = 2000;
    private boolean nightTarget;
    private float night, nightFrom;     // 0 = day, 1 = night
    private long nightAt;
    private final int[] glassNight = new int[Backdrop.GW * Backdrop.GH], frostNight = new int[Backdrop.FW * Backdrop.FH];
    private final int[] neutralGlassPx = new int[Backdrop.GW * Backdrop.GH], neutralFrostPx = new int[Backdrop.FW * Backdrop.FH];
    private boolean nightPair;          // the glass holds both photos (day and night), blended by `night`
    private Shader nightGradient;

    private final List<Recs.Pick>[] rowItems = new List[] {new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    private final Spring[][] rowFocus = new Spring[4][];
    private final RectF[][] rowRects = new RectF[4][];
    private final float[] rowOffset = new float[4];
    private final int[] rowCol = new int[4];
    private final Paint[] rowScrimPaints = new Paint[4];
    private final LinearGradient[] rowScrims = new LinearGradient[4];
    private Paint rowLabel, rowTitle, rowMeta, lQuote;
    private Shader lightGradient;

    // Continue Watching: what other apps were playing (Resume), its own row between the dock and the recommendations.
    private final Layer resumeLayer;
    private final NowPlaying.Watch resumeWatch;   // reads the sessions while the home screen shows, and records them
    private List<Resume.Entry> resumeItems = new ArrayList<>();
    private Spring[] resumeFocus = new Spring[0];
    private RectF[] resumeRects = new RectF[0];
    private float resumeOffset;
    private AlertDialog cardMenu;
    private final Map<String, BitmapShader> resumeArtworkShaders = new HashMap<>();
    private int resumeCol;
    private final Map<String, HomeApps.Item> resumeTiles = new HashMap<>();   // each app's card tile (null = in the making)

    // Shelf
    private List<HomeApps.Item> shelf = new ArrayList<>();
    private Spring[] tileFocus = new Spring[0];
    private RectF[] tileRects = new RectF[0];
    private final Map<String, Bitmap> crispLogos = new HashMap<>();   // logos already sized for the focused tile
    /** Each app's tile like its Apple TV icon: brand colors (top, bottom) and logo (0 = as is, 1 = white, 2 = dark). */
    private static final Map<String, int[]> TILE_STYLE = new HashMap<>();
    static {
        TILE_STYLE.put("netflix", new int[] {0xFF0A0A0A, 0xFF000000, 0});
        TILE_STYLE.put("youtube", new int[] {0xFF1C1C1E, 0xFF09090A, 0});
        TILE_STYLE.put("globoplay", new int[] {0xFFFF8A1F, 0xFFFF2D5A, 1});
        TILE_STYLE.put("prime", new int[] {0xFF2AA8FF, 0xFF0A68D4, 1});
        TILE_STYLE.put("disney", new int[] {0xFF173584, 0xFF08143F, 0});
        TILE_STYLE.put("max", new int[] {0xFF5C30E8, 0xFF221684, 0});
        TILE_STYLE.put("appletv", new int[] {0xFF1C1C1E, 0xFF000000, 0});
    }
    private final android.graphics.PorterDuffColorFilter whiteLogo =
            new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
    private final RectF platterRect = new RectF();
    private int shelfIndex;
    /** The first app the shelf shows: with more apps than fit, it scrolls with the focus. */
    private int shelfFirst;
    private final int shelfVisible;

    // Moving apps on the shelf (as on Apple TV): hold OK on a tile, then ← → move it, OK drops it, Back cancels. The
    // lifted tile wiggles; the others slide to their new places (each slot's x offset is a spring that returns to 0).
    private static final long HOLD_MS = 500, WIGGLE_MS = 300;
    private static final float LIFT_GROW = 0.08f, WIGGLE_DEG = 1.5f;
    private boolean reordering, reorderAnim, okHeld, holdArmed;
    private int liftIndex = -1;
    private long reorderAt;
    private float wiggle;
    private Spring[] tileSlide = new Spring[0];
    private final Spring moveLift = new Spring(0.3f, 0.72f, 0);
    private List<HomeApps.Item> reorderOrigin;
    private final Runnable holdStart = () -> { if (this.okDown && this.holdArmed) hold(); };

    // Recommendation background (the first For You pick) and page scroll.
    private long bgFadeAt;
    private final Spring pageScroll = new Spring(0.3f, 0.8f, 0);
    private Backdrop bgFrom, bgTo;     // null = gradient (no photo)
    private final int[] frostMix = new int[Backdrop.FW * Backdrop.FH];

    // The chips row (inputs and shortcuts; drawn on the old bottom drawer's layer)
    private List<HomeApps.Item> drawerItems = new ArrayList<>();
    private Spring[] drawerFocus = new Spring[0];
    private RectF[] drawerRects = new RectF[0];
    private final RectF weatherCard = new RectF(), quotesCard = new RectF();
    private int drawerIndex;

    // Sleep Timer quick picker (from the Sleep Timer chip): OK applies, Back closes. Off first, then 5 min to 4 h.
    private boolean sleepOpen;
    private int sleepIndex;
    private static final int[] SLEEP_CHOICES = {0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240};

    // "All Apps" sheet
    private List<HomeApps.Item> others = new ArrayList<>();
    private Spring[] gridFocus = new Spring[0];
    private String[] gridLabels;
    private final Spring sheet = new Spring(0.38f, 0.9f, 0);
    private final Spring scroll = new Spring(0.34f, 1f, 0);
    private final RectF focusedCell = new RectF();
    private boolean sheetOpen, shelfEdited;
    private int gridIndex;
    private long toggledAt;

    // Toasts
    private final ArrayDeque<String> toasts = new ArrayDeque<>();
    private StaticLayout toast;
    private long toastAt;

    // Frames
    private boolean running, tilesDirty, drawerDirty, gridDirty, motionDirty = true;
    private boolean idle;
    private long lastNanos, resumedAt, lastStep, rowSnapshotAt;
    private int rowCycle;
    private static final long BURST_MS = 1500;
    private int w, h;
    private float k = 1;
    /** "Off in 42 min" under the shelf, at the right, while the sleep timer runs (Settings > Sleep Timer); "" otherwise. */
    private String sleepText = "";
    private boolean okDown, backDown;

    private Paint labelPaint, buttonPaint;
    private Paint drawerLabel, cardTitle, cardText, cardDim, cardDimRight, bigTemp, hourPaint, hourTemp, dayLow;
    private float forecastLabelCap, forecastLabelDescent, forecastValueAbove, forecastValueBelow;
    private Paint pricePaint, changePaint, sheetTitle;
    private TextPaint gridLabel, toastPaint;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowSoft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowDeep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shadows shadows = new Shadows();
    private final Paint imageFill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF(), cell = new RectF(), screen = new RectF();
    private final TextPaint fitPaint = new TextPaint();
    private final RectF gridCell = new RectF();
    private LinearGradient[] tileShaders = new LinearGradient[0];
    private LinearGradient tileGlow, cardSheen;
    private float tileShaderH, cardSheenH;
    private LinearGradient[] lightSheens = new LinearGradient[0], lightRims = new LinearGradient[0];
    private float[] lightShaderW = new float[0];
    private android.graphics.ColorFilter veilFilter;
    private float veilAmount = -1;
    private int veilColor;
    private HomeData.Weather textWeather;
    private long textPricesAt = -1, textMinute = -1;
    private int textPrefs = -1;
    private String textLanguage;
    private String weatherTitle, nowTemp, highLow, sunriseText, sunsetText, staleText;
    private final String[] hourText = new String[6], hourTempText = new String[6];
    private final String[] dayText = new String[6], dayMaxText = new String[6], dayMinText = new String[6];
    private boolean textDays;   // the daily forecast is in (an offline forecast of an older version only has the hours)
    private final String[] priceText = new String[HomeData.COINS.length], changeText = new String[HomeData.COINS.length];

    /** One layer of the screen. It draws in full-screen coordinates: the glass needs each pixel's real position. */
    private final class Layer extends View {
        private final int kind;

        Layer(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        @Override protected void onDraw(Canvas c) {
            if (labelPaint == null) return;
            c.translate(-getLeft(), -getTop());
            // Layers use screen coordinates while drawing. If a layer moves on screen, glass must sample the
            // background at that translated position too.
            scene.setOffset(0, getTranslationY());
            shadows.dither = dark() > 0.5f;
            switch (kind) {
                case SKY: drawSky(c); break;
                case STRIP: drawStrip(c); break;
                case PLATTER: drawPlatter(c); break;
                case TILES: drawTiles(c); break;
            case DRAWER: drawChips(c); break;
                case ROW0: drawRow(c, 0); break;
                case ROW1: drawRow(c, 1); break;
                case ROW2: drawRow(c, 2); break;
                case ROW3: drawRow(c, 3); break;
                case RESUME: drawResumeRow(c); break;
                case SHEET: drawSheetLayer(c); break;
                default: drawToast(c); break;
            }
            scene.setOffset(0, 0);
        }
    }

    private final Runnable reloadSoon = this::reload;
    private static final long APPS_MAX_AGE_MS = 15 * 60_000;
    private final LauncherApps launcherApps;
    private boolean appsStale = true;
    private int appsVersion, appsGeneration;
    private long appsAt;

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String inputId, int state) { reloadSoon(); }
        @Override public void onInputAdded(String inputId) { reloadSoon(); }
        @Override public void onInputRemoved(String inputId) { reloadSoon(); }
    };

    private void reloadSoon() {
        handler.removeCallbacks(reloadSoon);
        handler.postDelayed(reloadSoon, 500);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            wake();
            data.refreshIfStale();
            recs.refreshIfStale();
            checkNotices();
            updatePalette();
            checkNight(true);
            updatePicks();
            reloadResume();   // the "12 min ago" lines, and entries past their month
            updateSleep();
            handler.postDelayed(this, 15_000);
        }
    };

    HomeView(Activity a) {
        super(a);
        activity = a;
        sectionOrder = HomeSections.order(a);
        for (String key : HomeSections.keys()) sectionEnabled.put(key, HomeSections.visible(a, key));
        data = HomeData.get(a);
        recs = Recs.get(a);
        click = new Click(a);
        shelfVisible = HomeApps.shelfVisible(a);
        ui = a.getSharedPreferences("home-ui", Context.MODE_PRIVATE);
        tvInputs = (TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
        launcherApps = (LauncherApps) a.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (launcherApps != null) launcherApps.registerCallback(packages, handler);
        setFocusable(true);
        sky = new Layer(a, SKY);
        strip = new Layer(a, STRIP);
        platter = new Layer(a, PLATTER);
        tiles = new Layer(a, TILES);
        drawerLayer = new Layer(a, DRAWER);
        resumeLayer = new Layer(a, RESUME);
        resumeWatch = new NowPlaying.Watch(a, this::reloadResume, true);   // records while the home screen shows
        // ROW0 reuses the retired hero layer's position in the old z-order.
        rowLayers[0] = new Layer(a, ROW0);
        rowLayers[1] = new Layer(a, ROW1);
        rowLayers[2] = new Layer(a, ROW2);
        rowLayers[3] = new Layer(a, ROW3);
        sheetLayer = new Layer(a, SHEET);
        toastLayer = new Layer(a, TOAST);
        // Hardware layers on the GPU: only the one that changed is redrawn; moving, fading and scaling stay cheap.
        // Not on Android 8 to 11: on older GPUs (a Mi Box 3's Mali-450) redrawing and compositing a big layer on every
        // frame cost more than drawing straight to the screen (measured: focus on the picks 33 → 12 ms a frame, the
        // drawer 19 → 17 ms).
        for (View v : new View[] {platter, tiles, drawerLayer, strip, sheetLayer}) v.setLayerType(Ui.LEGACY ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        for (Layer row : rowLayers) row.setLayerType(Ui.LEGACY ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        resumeLayer.setLayerType(Ui.LEGACY ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        for (View v : new View[] {sky, strip, platter, tiles, drawerLayer}) {
            v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);   // TalkBack reads the items (Spoken)
            addView(v);
        }
        resumeLayer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(resumeLayer);
        for (Layer row : rowLayers) {
            row.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(row);
        }
        for (View v : new View[] {sheetLayer, toastLayer}) {
            v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(v);
        }
        setContentDescription("Free My TV");
        recs.setLight(true);
        checkNight(false);
        sheetLayer.setVisibility(INVISIBLE);
        scene.setMotion(0.6f, 0f);
        updatePalette();
        // First time: right away (a cold start opens with the apps already there); missing logos come from reload().
        setShelf(HomeApps.shelf(a, false));
        setDrawerItems(drawerList(a));
        updateSectionLayout();
        chooseDefaultFocus();
        pageScroll.snap(pageScroll.target);
    }

    // MARK: - Lifecycle (called by HomeActivity)

    public void resume() {
        if (running) return;
        running = true;
        checkNight(false);
        long now = SystemClock.uptimeMillis();
        // Some remotes (Xiaomi's) send one Home press as a burst of 5 to 8, and each one pauses and resumes the home
        // screen: those after the first change nothing, so the lists are read only once.
        boolean firstResume = resumedAt == 0;
        boolean again = resumedAt != 0 && now - resumedAt < BURST_MS;
        resumedAt = now;
        lastNanos = 0;
        lastStep = 0;
        data.addListener(this);
        recs.addListener(this);
        resumeWatch.start();
        reloadResume();   // what was recorded before, plus what the sessions say right now (just back from an app)
        data.refreshIfStale();
        recs.refreshIfStale();
        Photos.importInbox(getContext(), ownReady);   // photos a computer just sent
        if (tvInputs != null) tvInputs.registerCallback(inputCallback, handler);
        handler.postDelayed(tick, 15_000);
        checkNotices();
        if (!again) reload();
        if (!firstResume && !again && !allAppsOnResume) reset(false);
        updatePalette();
        updatePicks(!firstResume && !again);
        updateSleep();      // a timer just set in Settings shows at once, not at the next tick
        if (allAppsOnResume) {
            allAppsOnResume = false;
            reset(false);
            openSheet();
        } else if (sheetOpen) {
            closeSheet();
        }
        sheet.snap(sheet.target);
        motionDirty = true;
        invalidateAll();
        idle = false;
        Choreographer.getInstance().postFrameCallback(this);
        spoken.readAgain();
    }

    public void pause() {
        if (!running) return;
        running = false;
        cancelReorder(true);
        if (cardMenu != null) cardMenu.dismiss();
        handler.removeCallbacks(holdStart);
        okHeld = holdArmed = false;
        Choreographer.getInstance().removeFrameCallback(this);
        handler.removeCallbacks(tick);
        handler.removeCallbacks(reloadSoon);
        resumeWatch.stop();   // the row records only while the home screen shows
        data.removeListener(this);
        recs.removeListener(this);
        if (tvInputs != null) tvInputs.unregisterCallback(inputCallback);
    }

    public void destroy() {
        pause();
        if (launcherApps != null) launcherApps.unregisterCallback(packages);
        click.release();
    }

    /** Starting state (Home pressed while the home screen is showing): first app focused, chips and sheet closed. */
    public void reset(boolean animated) {
        wake();
        cancelReorder(!animated);
        sheetOpen = false;
        if (animated) {
            sheet.target = 0;
        } else {
            sheet.snap(0);
        }
        pageScroll.target = 0;
        chooseDefaultFocus();
        if (!animated) pageScroll.snap(pageScroll.target);
        retarget(!animated);
        motionDirty = true;
    }

    @Override public void onData() {
        wake();
        updatePalette();
        checkNight(true);
        drawerDirty = true;
        strip.invalidate();
        platter.invalidate();
    }

    @Override public void onRecs() {
        wake();
        updatePicks();
    }

    private void invalidateAll() {
        for (View v : new View[] {sky, strip, platter, tiles, drawerLayer}) v.invalidate();
        for (Layer row : rowLayers) row.invalidate();
        resumeLayer.invalidate();
    }

    // MARK: - Size and layers

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        float kk = width / 1920f;
        measure(sky, width, height);
        // The weather strip: its own layer holds the 190..410 band and its shadows.
        measure(strip, width, Math.round(450 * kk));
        // Sized to what they draw (the glass and its shadow, the dock at 450..656; the grown tiles and their
        // shadows): smaller layers are cheaper for the GPU to composite on their (rare) redraws.
        measure(platter, width, Math.round(280 * kk));
        measure(tiles, width, Math.round(280 * kk));
        // The quick buttons: 24..150, plus the focused tile's growth and its shadow.
        measure(drawerLayer, width, Math.round(190 * kk));
        measure(resumeLayer, width, Math.round(ROW_BAND_H * kk));
        for (Layer row : rowLayers) measure(row, width, Math.round(ROW_BAND_H * kk));
        measure(sheetLayer, width, height);
        measure(toastLayer, Math.round(1000 * kk), Math.round(220 * kk));
        setMeasuredDimension(width, height);
    }

    private static void measure(View v, int width, int height) {
        v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l, height = b - t;
        float kk = width / 1920f;
        sky.layout(0, 0, width, height);
        place(strip, 0, 0);
        place(platter, 0, Math.round(L_DOCK_TOP * kk));   // the dock layer: 430..710
        place(tiles, 0, Math.round(L_DOCK_TOP * kk));   // at the dock's top: the focused tile grows past the glass and must not be clipped
        place(drawerLayer, 0, 0);
        place(resumeLayer, 0, Math.round(ROWS_TOP * kk));
        for (int i = 0; i < rowLayers.length; i++) place(rowLayers[i], 0, Math.round(rowTopUnits(i) * kk));
        sheetLayer.layout(0, 0, width, height);
        sheetLayer.setPivotX(width / 2f);
        sheetLayer.setPivotY(height / 2f);
        place(toastLayer, (width - toastLayer.getMeasuredWidth()) / 2, Math.round(600 * kk));   // toast draws at y≈640, above the rows
    }

    private static void place(View v, int x, int y) {
        v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;
        screen.set(0, 0, w, h);
        scene.setSize(w, h);
        makePaints();
        gridLabels = null;
        Symbols.warm(getContext(), "bell", 26 * k);
        Symbols.warm(getContext(), "bedtime", 21 * k);   // the sleep timer's "Off in", at lQuote's size
        layoutShelf();
        layoutDrawer();
        layoutStrip();
        layoutRows();
        layoutResumeRow();
        updateSectionLayout();
        updatePicks();
        motionDirty = true;
        wake();
    }

    private void makePaints() {
        labelPaint = Ui.text(Ui.medium, 25 * k, 0xC7FFFFFF);
        labelPaint.setLetterSpacing(0.1f);
        for (Paint p : new Paint[] {labelPaint}) p.setShadowLayer(22 * k, 0, 2 * k, 0x59000000);
        rowLabel = Ui.text(Ui.semibold, 26 * k, L_INK);
        rowTitle = Ui.text(Ui.semibold, 22 * k, WHITE);
        rowMeta = Ui.text(Ui.medium, 18 * k, 0xCCFFFFFF);
        for (int i = 0; i < rowScrimPaints.length; i++) rowScrimPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        lQuote = Ui.numbers(Ui.regular, 21 * k, L_FAINT);
        lightGradient = null;
        buttonPaint = Ui.text(Ui.semibold, 29 * k, INK);
        // The cards' shared paints (the glass cards, the weather strip and the sleep picker), keyed off the night look.
        drawerLabel = Ui.text(Ui.medium, 23 * k, 0xEBFFFFFF);
        drawerLabel.setTextAlign(Paint.Align.CENTER);
        cardTitle = Ui.text(Ui.semibold, 25 * k, 0xB3FFFFFF);
        cardText = Ui.text(Ui.medium, 29 * k, 0xF2FFFFFF);
        cardDim = Ui.numbers(Ui.medium, 23 * k, 0xADFFFFFF);
        cardDimRight = Ui.text(Ui.medium, 21 * k, 0x99FFFFFF);
        cardDimRight.setTextAlign(Paint.Align.RIGHT);
        bigTemp = Ui.numbers(Ui.light, 68 * k, 0xFFFFFFFF);
        hourPaint = Ui.numbers(Ui.medium, 22 * k, 0xADFFFFFF);
        hourPaint.setTextAlign(Paint.Align.CENTER);
        hourTemp = Ui.numbers(Ui.medium, 26 * k, 0xF2FFFFFF);
        hourTemp.setTextAlign(Paint.Align.CENTER);
        dayLow = Ui.numbers(Ui.medium, 22 * k, 0xADFFFFFF);
        dayLow.setTextAlign(Paint.Align.CENTER);
        forecastLabelCap = Ui.cap(hourPaint);
        forecastLabelDescent = Math.max(0, hourPaint.descent());
        forecastValueAbove = Math.max(11 * k, Math.max(Ui.halfCap(hourTemp), Ui.halfCap(dayLow)));
        forecastValueBelow = Math.max(11 * k, Math.max(Ui.halfCap(hourTemp) + hourTemp.descent(),
                Ui.halfCap(dayLow) + dayLow.descent()));
        pricePaint = Ui.numbers(Ui.medium, 26 * k, 0xF2FFFFFF);
        pricePaint.setTextAlign(Paint.Align.RIGHT);
        changePaint = Ui.numbers(Ui.semibold, 21 * k, 0xFFFFFFFF);
        changePaint.setTextAlign(Paint.Align.CENTER);
        sheetTitle = Ui.text(Ui.displaySemibold, 40 * k, 0xFFFFFFFF);
        sheetTitle.setTextAlign(Paint.Align.CENTER);
        gridLabel = new TextPaint(Ui.text(Ui.medium, 24 * k, 0x99FFFFFF));
        gridLabel.setTextAlign(Paint.Align.CENTER);
        toastPaint = new TextPaint(Ui.text(Ui.medium, 26 * k, 0xF2FFFFFF));
        shadow.setMaskFilter(new BlurMaskFilter(SHADOW_BLUR * k, BlurMaskFilter.Blur.NORMAL));
        shadowSoft.setMaskFilter(new BlurMaskFilter(12 * k, BlurMaskFilter.Blur.NORMAL));
        shadowDeep.setMaskFilter(new BlurMaskFilter(DEEP_BLUR * k, BlurMaskFilter.Blur.NORMAL));
        halo.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * k);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2.5f * k);
    }

    // MARK: - Frames: 60 per second while something moves; nothing is redrawn when still

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (frameTimeNanos - lastNanos) / 1e9f);
        lastNanos = frameTimeNanos;
        long up = SystemClock.uptimeMillis();

        boolean tilesMoving = step(tileFocus, dt);
        if (reorderAnim) tilesMoving |= stepReorder(dt, up);
        boolean rowsMoving = false;
        for (int i = 0; i < rowFocus.length; i++) {
            if (rowFocus[i] != null && step(rowFocus[i], dt)) {
                rowLayers[i].invalidate();
                rowsMoving = true;
            }
        }
        if (step(resumeFocus, dt)) {
            resumeLayer.invalidate();
            rowsMoving = true;
        }
        boolean drawerItemsMoving = step(drawerFocus, dt);
        boolean gridMoving = step(gridFocus, dt) | scroll.step(dt);
        boolean sheetMoving = sheet.step(dt);
        boolean pageMoving = pageScroll.step(dt);
        boolean toastFading = tickToast(up);
        boolean bgFading = tickBackgroundFade(up);
        boolean nightMoving = tickNight(up);

        if (sheetMoving || pageMoving || motionDirty) updateMotion();
        // A glass layer samples the background when it is drawn. Rebuild these cached layers while scrolling so the
        // glass does not keep showing the pixels from its previous screen position.
        if (pageMoving) {
            strip.invalidate();
            platter.invalidate();
            drawerLayer.invalidate();
        }
        // The All Apps sheet only moves the cached layers: redrawing everything on every frame made the TV drop
        // frames (measured: ~50 ms of GPU per frame). The layers are drawn resting in place; the glass shelf is
        // redrawn once (its glass shows the background around it, which the sheet doesn't change).
        if (tilesMoving || tilesDirty) tiles.invalidate();
        if (drawerItemsMoving || drawerDirty || bgFading) {
            drawerLayer.invalidate();
            strip.invalidate();    // the weather strip reads the same data and the same day-night tone
        }
        if (nightMoving || bgFading) {
            for (Layer row : rowLayers) row.invalidate();
            resumeLayer.invalidate();
        }
        if (gridMoving || gridDirty) sheetLayer.invalidate();
        if (bgFading) sky.invalidate();
        if (bgFading) platter.invalidate();
        tilesDirty = drawerDirty = gridDirty = motionDirty = false;

        // Busy: something is moving, or the sheet or the sleep picker is open.
        boolean busy = tilesMoving || rowsMoving || drawerItemsMoving || gridMoving
                || sheetMoving || pageMoving || toastFading || bgFading || nightMoving || sheetOpen
                || sheet.value > 0.001f || toast != null || !toasts.isEmpty();
        idle = !busy;
        if (busy) Choreographer.getInstance().postFrameCallback(this);
        else Choreographer.getInstance().postFrameCallbackDelayed(this, 1000);
    }

    private void wake() {
        if (!running || !idle) return;
        idle = false;
        lastNanos = 0;
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    private static boolean step(Spring[] springs, float dt) {
        boolean moving = false;
        for (Spring s : springs) moving |= s.step(dt);
        return moving;
    }

    /** The All Apps sheet: the layers' positions and opacity (without redrawing their content). */
    private void updateMotion() {
        float s = Ui.clamp(sheet.value);
        float home = 1 - s, top = 1 - smoothstep(0, 0.32f, s);
        show(strip, sleepOpen ? 0 : top, 0);   // the header: it would collide with the sheet's title
        show(platter, home, 0);
        show(tiles, home, 0);
        show(drawerLayer, home, 0);   // the chips row
        for (Layer row : rowLayers) show(row, home, 0);
        show(resumeLayer, home, 0);
        show(sheetLayer, sleepOpen ? 1 : s, 0);
        applyPageScroll();
        float scale = sleepOpen ? 1 : 1.04f - 0.04f * s;
        sheetLayer.setScaleX(scale);
        sheetLayer.setScaleY(scale);
    }

    private static void show(View v, float alpha, float translationY) {
        v.setAlpha(alpha);
        v.setTranslationY(translationY);
        v.setVisibility(alpha > 0.002f ? VISIBLE : INVISIBLE);
    }

    private void applyPageScroll() {
        updateSectionLayout();
        positionSection(strip, "weather", STRIP_TOP);
        positionSection(platter, "apps", L_DOCK_TOP + L_PLATTER_TOP);
        positionSection(tiles, "apps", L_DOCK_TOP + L_PLATTER_TOP);
        positionSection(drawerLayer, "actions", CHIPS_TOP);
        for (int i = 0; i < rowLayers.length; i++) positionSection(rowLayers[i], rowSection(i), rowTopUnits(i));
        positionSection(resumeLayer, "continue", ROWS_TOP);
    }

    private void positionSection(View layer, String key, float originalTop) {
        boolean shown = sectionShown(key);
        layer.setVisibility(shown && layer.getAlpha() > 0.002f ? VISIBLE : INVISIBLE);
        float top = sectionTops.containsKey(key) ? sectionTops.get(key) : originalTop;
        layer.setTranslationY((top - originalTop - pageScroll.value) * k);
    }

    private static String rowSection(int row) {
        return row == 0 ? "for-you" : row == 1 ? "anime" : row == 2 ? "movies" : "tv-shows";
    }

    private static String sectionForZone(int group) {
        if (group == Z_SHELF) return "apps";
        if (group == Z_DRAWER) return "actions";
        if (group == Z_RESUME) return "continue";
        return isRowZone(group) ? rowSection(group - Z_ROW0) : "";
    }

    private static int zoneForSection(String key) {
        if (key.equals("actions")) return Z_DRAWER;
        if (key.equals("apps")) return Z_SHELF;
        if (key.equals("continue")) return Z_RESUME;
        for (int row = 0; row < 4; row++) if (key.equals(rowSection(row))) return Z_ROW0 + row;
        return -1;
    }

    private boolean configuredSection(String key) { return Boolean.TRUE.equals(sectionEnabled.get(key)); }

    private boolean populatedSection(String key) {
        if (key.equals("actions")) return !drawerItems.isEmpty();
        if (key.equals("apps")) return !shelf.isEmpty();
        if (key.equals("continue")) return !resumeItems.isEmpty();
        for (int row = 0; row < 4; row++) if (key.equals(rowSection(row))) return !rowItems[row].isEmpty();
        return false;
    }

    private boolean sectionShown(String key) {
        if (key.equals("actions") && !configuredSection(key)) {
            // An offline/empty home must still expose Settings and All Apps as its recovery route.
            for (String candidate : sectionOrder) {
                if (!candidate.equals("actions") && configuredSection(candidate) && populatedSection(candidate)) return false;
            }
            return true;
        }
        if (!configuredSection(key)) return false;
        if (key.equals("continue")) return !resumeItems.isEmpty();
        if (key.equals("weather")) return Prefs.weather() || Prefs.quotes() && HomeData.active().length > 0;
        return true;
    }

    private float sectionHeight(String key) {
        if (key.equals("actions")) return ROW_H;
        if (key.equals("weather")) return STRIP_H;
        if (key.equals("apps")) return 206;
        return ROW_BAND_H - GAP;
    }

    private void updateSectionLayout() {
        float top = CHIPS_TOP;
        for (String key : sectionOrder) {
            if (!sectionShown(key)) continue;
            sectionTops.put(key, top);
            top += sectionHeight(key) + GAP;
        }
        sectionDocumentBottom = Math.max(CHIPS_TOP, top - GAP);
    }

    private void ensureSectionVisible(String key) {
        updateSectionLayout();
        if (!sectionShown(key) || !sectionTops.containsKey(key)) return;
        float top = sectionTops.get(key), bottom = top + sectionHeight(key);
        float target = pageScroll.target;
        if (bottom - target > PAGE_BOTTOM - 20) target = bottom - PAGE_BOTTOM + 20;
        if (top - target < CHIPS_TOP) target = top - CHIPS_TOP;
        pageScroll.target = Math.max(0, Math.min(pageScrollMax(), target));
        motionDirty = true;
        wake();
    }

    private void chooseDefaultFocus() {
        if (sectionShown("apps") && !shelf.isEmpty()) {
            zone = Z_SHELF;
            shelfIndex = 0;
        } else {
            zone = Z_DRAWER;
            drawerIndex = 0;
            for (String key : sectionOrder) if (sectionShown(key) && populatedSection(key) && zoneForSection(key) >= 0) {
                zone = zoneForSection(key);
                if (zone == Z_RESUME) resumeCol = 0;
                else if (isRowZone(zone)) rowCol[zone - Z_ROW0] = 0;
                break;
            }
        }
        ensureSectionVisible(sectionForZone(zone));
    }

    private boolean moveVertical(int direction, float x) {
        int current = sectionOrder.indexOf(sectionForZone(zone));
        for (int at = current + direction; at >= 0 && at < sectionOrder.size(); at += direction) {
            String key = sectionOrder.get(at);
            if (!sectionShown(key) || !populatedSection(key)) continue;
            int group = zoneForSection(key);
            if (group < 0) continue; // display-only weather never takes focus
            zone = group;
            if (group == Z_SHELF) shelfIndex = nearest(tileRects, x);
            else if (group == Z_DRAWER) drawerIndex = nearest(drawerRects, x);
            else if (group == Z_RESUME) {
                resumeCol = nearest(resumeRects, x);
                ensureRowVisibleResume(resumeCol);
            } else {
                int row = group - Z_ROW0;
                rowCol[row] = nearestRow(row, x);
                ensureRowVisible(row, rowCol[row]);
            }
            ensureSectionVisible(key);
            return true;
        }
        return false;
    }

    private void updateSleep() {
        boolean here = Prefs.sleepShown() && !android.provider.Settings.canDrawOverlays(getContext());   // else SleepOverlay
        int left = here ? SleepTimer.minutesLeft(getContext()) : 0;
        String text = left == 0 ? "" : Ui.trf("Off in {time}", "time", SleepTimer.duration(left));
        if (text.equals(sleepText)) return;
        sleepText = text;
        platter.invalidate();
    }

    private void updatePalette() {
        HomeData.Weather wx = data.weather;
        Shaders.sky(scene.palette, System.currentTimeMillis(), wx.sunrise, wx.sunset);
        scene.applyPalette();
        platter.invalidate();   // recolors the sleep timer pill with the day-night tone
    }

    // MARK: - Recommendation rows and background

    private void updatePicks() {
        updatePicks(false);
    }

    /** Keeps recommendation positions fixed while Home is visible; a new visit or the ten-minute refresh advances them. */
    private void updatePicks(boolean forceRows) {
        if (labelPaint == null) {
            recs.prefetchRows(rowCycle);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        boolean due = forceRows || rowSnapshotAt == 0 || now - rowSnapshotAt >= ROW_REFRESH_MS;
        boolean empty = true;
        for (List<Recs.Pick> row : rowItems) empty &= row.isEmpty();
        if (due || empty) {
            if (due) rowCycle++;
            List<Recs.Pick> mixed = recs.row(null, 10, rowCycle);
            List<Recs.Pick> anime = recs.row("anime", 10, rowCycle);
            List<Recs.Pick> movies = recs.row("movie", 10, rowCycle);
            List<Recs.Pick> shows = recs.row("tv", 10, rowCycle);
            boolean available = !mixed.isEmpty() || !anime.isEmpty() || !movies.isEmpty() || !shows.isEmpty();
            if (due || (empty && available)) {
                if (!due) rowCycle++;
                if (!due) {
                    // When rows first become available, start at the next stable selection cycle.
                    mixed = recs.row(null, 10, rowCycle);
                    anime = recs.row("anime", 10, rowCycle);
                    movies = recs.row("movie", 10, rowCycle);
                    shows = recs.row("tv", 10, rowCycle);
                }
                updateRow(0, mixed);
                updateRow(1, anime);
                updateRow(2, movies);
                updateRow(3, shows);
            }
            if (due || available) rowSnapshotAt = now;
        }
        // A recommendation callback may mean only that an image finished loading. Redraw the held rows without
        // selecting a different set of titles.
        for (Layer layer : rowLayers) layer.invalidate();
        int activeRow = isRowZone(zone) ? zone - Z_ROW0 : -1;
        float activeX = focusX();
        layoutRows();
        if (isRowZone(zone) && rowItems[zone - Z_ROW0].isEmpty()) {
            int next = closestPopulatedRow(activeRow);
            if (next >= 0) enterRow(next, activeX);
            else {
                chooseDefaultFocus();
                motionDirty = true;
                retarget(false);
            }
        }
        recs.prefetchRows(rowCycle);
        boolean own = rowItems[0].isEmpty() && Photos.on(getContext());
        Backdrop b = own ? Photos.backdrop(getContext(), ownReady)
                : rowItems[0].isEmpty() ? null : recs.backdrop(rowItems[0].get(0));
        if (b != null || (rowItems[0].isEmpty() && !own)) showBackdrop(b);
        spoken.focusMoved();
    }

    /** A user photo is ready for the home background, or newly imported photos are available. */
    private final Runnable ownReady = () -> {
        if (running) updatePicks();
    };

    private static boolean samePicks(List<Recs.Pick> a, List<Recs.Pick> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!Recs.key(a.get(i)).equals(Recs.key(b.get(i)))) return false;
        return true;
    }

    private void updateRow(int row, List<Recs.Pick> next) {
        List<Recs.Pick> old = rowItems[row];
        if (samePicks(old, next)) {
            rowItems[row] = next;
            rowLayers[row].invalidate();
            return;
        }
        String focusedKey = rowCol[row] < old.size() ? Recs.key(old.get(rowCol[row])) : null;
        int col = Math.min(rowCol[row], Math.max(0, next.size() - 1));
        if (focusedKey != null) {
            for (int i = 0; i < next.size(); i++) {
                if (focusedKey.equals(Recs.key(next.get(i)))) { col = i; break; }
            }
        }
        rowItems[row] = next;
        rowCol[row] = col;
        rowFocus[row] = new Spring[next.size()];
        rowRects[row] = new RectF[next.size()];
        for (int i = 0; i < next.size(); i++) {
            rowFocus[row][i] = new Spring(0.32f, 0.78f, 0);
            rowRects[row][i] = new RectF();
        }
        rowOffset[row] = 0;
        layoutRow(row);
        ensureRowVisible(row, col);
        aim(rowFocus[row], zone == Z_ROW0 + row ? col : -1, true);
        rowLayers[row].invalidate();
    }

    private void layoutRows() {
        if (rowLabel == null) return;
        for (int i = 0; i < rowItems.length; i++) {
            if (rowRects[i] == null || rowRects[i].length != rowItems[i].size()) {
                rowRects[i] = new RectF[rowItems[i].size()];
                rowFocus[i] = new Spring[rowItems[i].size()];
                for (int j = 0; j < rowItems[i].size(); j++) {
                    rowRects[i][j] = new RectF();
                    rowFocus[i][j] = new Spring(0.32f, 0.78f, 0);
                }
            }
            layoutRow(i);
        }
    }

    private void layoutRow(int row) {
        float top = rowTopUnits(row) + ROW_CARD_TOP;
        for (int i = 0; i < rowRects[row].length; i++) {
            float left = MARGIN + i * (ROW_CARD_W + ROW_CARD_GAP) - rowOffset[row];
            rowRects[row][i].set(left * k, top * k, (left + ROW_CARD_W) * k, (top + ROW_CARD_H) * k);
        }
        float cardTop = top * k, cardBottom = (top + ROW_CARD_H) * k;
        rowScrims[row] = new LinearGradient(0, cardTop + 105 * k, 0, cardBottom,
                0x00000000, 0xE6000000, Shader.TileMode.CLAMP);
        if (rowScrimPaints[row] != null) rowScrimPaints[row].setShader(rowScrims[row]);
    }

    private void ensureRowVisible(int row, int col) {
        if (rowRects[row] == null || col < 0 || col >= rowRects[row].length) return;
        float minLeft = MARGIN * k, maxLeft = (1920 - MARGIN - ROW_CARD_W) * k;
        RectF r = rowRects[row][col];
        if (r.left < minLeft) rowOffset[row] -= (minLeft - r.left) / k;
        else if (r.left > maxLeft) rowOffset[row] += (r.left - maxLeft) / k;
        float contentRight = MARGIN + Math.max(0, rowRects[row].length - 1) * (ROW_CARD_W + ROW_CARD_GAP) + ROW_CARD_W;
        rowOffset[row] = Math.max(0, Math.min(Math.max(0, contentRight - CONTENT_R), rowOffset[row]));
        layoutRow(row);
    }

    private void ensurePageScroll(int row) {
        ensureSectionVisible(rowSection(row));
    }

    /** Where recommendation row `i` (0..3) sits: the Continue Watching row above takes a pitch when it shows. */
    private float rowTopUnits(int row) {
        return ROWS_TOP + (resumeRowShowing() ? ROW_PITCH : 0) + row * ROW_PITCH;
    }

    private boolean resumeRowShowing() {
        return configuredSection("continue") && !resumeItems.isEmpty();
    }

    /** How far the page may scroll: the last row fully above the bottom safe area, plus the 20 slack it always had. */
    private float pageScrollMax() {
        return Math.max(0, sectionDocumentBottom - PAGE_BOTTOM + 20);
    }

    private int nearestRow(int row, float x) {
        return rowRects[row] == null || rowRects[row].length == 0 ? 0 : nearest(rowRects[row], x);
    }

    private int closestPopulatedRow(int from) {
        for (int distance = 1; distance < rowItems.length; distance++) {
            int above = from - distance;
            if (above >= 0 && sectionShown(rowSection(above)) && !rowItems[above].isEmpty()) return above;
            int below = from + distance;
            if (below < rowItems.length && sectionShown(rowSection(below)) && !rowItems[below].isEmpty()) return below;
        }
        return -1;
    }

    // MARK: - Continue Watching

    /** Reads the entries again: the watch recorded something, the screen came back, or a minute passed. */
    private void reloadResume() {
        List<Resume.Entry> next = Prefs.resumeOn() ? Resume.items(getContext()) : new ArrayList<>();
        boolean was = resumeRowShowing();
        boolean same = next.size() == resumeItems.size();
        for (int i = 0; same && i < next.size(); i++) {
            Resume.Entry a = next.get(i), b = resumeItems.get(i);
            same = a.pkg.equals(b.pkg) && a.app.equals(b.app) && a.title.equals(b.title) && a.sub.equals(b.sub)
                    && a.mediaId.equals(b.mediaId) && a.pos == b.pos && a.dur == b.dur && a.at == b.at;
        }
        resumeItems = next;
        java.util.Set<String> artworkKeys = new java.util.HashSet<>();
        for (Resume.Entry entry : next) artworkKeys.add(Resume.key(entry));
        resumeArtworkShaders.keySet().retainAll(artworkKeys);
        if (same) {
            resumeLayer.invalidate();   // "12 min ago" advances while the home screen stays open
            return;
        }
        if (resumeItems.isEmpty()) {
            if (was) {
                resumeFocus = new Spring[0];
                resumeRects = new RectF[0];
                resumeCol = 0;
                resumeOffset = 0;
                relayoutRows();   // the recommendations move back up; the page range shrinks with them
                if (zone == Z_RESUME) {
                    chooseDefaultFocus();
                    retarget(false);
                }
            }
            resumeLayer.invalidate();
            return;
        }
        if (resumeFocus.length != resumeItems.size()) {
            Spring[] focus = new Spring[resumeItems.size()];
            RectF[] rects = new RectF[resumeItems.size()];
            for (int i = 0; i < resumeItems.size(); i++) {
                focus[i] = new Spring(0.32f, 0.78f, 0);
                rects[i] = new RectF();
            }
            resumeFocus = focus;
            resumeRects = rects;
            resumeCol = Math.min(resumeCol, resumeItems.size() - 1);
            resumeOffset = 0;
        }
        layoutResumeRow();
        ensureRowVisibleResume(resumeCol);
        aim(resumeFocus, zone == Z_RESUME ? resumeCol : -1, false);
        if (was != resumeRowShowing()) relayoutRows();
        resumeLayer.invalidate();
    }

    /** The recommendations moved over (the row appeared or vanished): their rects and the page range again. */
    private void relayoutRows() {
        for (int i = 0; i < rowLayers.length; i++) place(rowLayers[i], 0, Math.round(rowTopUnits(i) * k));
        layoutRows();
        updateSectionLayout();
        pageScroll.target = Math.min(pageScroll.target, pageScrollMax());
        motionDirty = true;
        for (Layer row : rowLayers) row.invalidate();
        wake();
    }

    private void layoutResumeRow() {
        float top = ROWS_TOP + ROW_CARD_TOP;
        for (int i = 0; i < resumeRects.length; i++) {
            float left = MARGIN + i * (ROW_CARD_W + ROW_CARD_GAP) - resumeOffset;
            resumeRects[i].set(left * k, top * k, (left + ROW_CARD_W) * k, (top + ROW_CARD_H) * k);
        }
    }

    private void ensureRowVisibleResume(int col) {
        if (col < 0 || col >= resumeRects.length) return;
        float minLeft = MARGIN * k, maxLeft = (1920 - MARGIN - ROW_CARD_W) * k;
        RectF r = resumeRects[col];
        if (r.left < minLeft) resumeOffset -= (minLeft - r.left) / k;
        else if (r.left > maxLeft) resumeOffset += (r.left - maxLeft) / k;
        float contentRight = MARGIN + Math.max(0, resumeRects.length - 1) * (ROW_CARD_W + ROW_CARD_GAP) + ROW_CARD_W;
        resumeOffset = Math.max(0, Math.min(Math.max(0, contentRight - CONTENT_R), resumeOffset));
        layoutResumeRow();
    }

    /** Focus on the Continue Watching row, at the column nearest where the focus was. */
    private void enterResume(float x) {
        if (resumeItems.isEmpty()) return;
        zone = Z_RESUME;
        resumeCol = nearest(resumeRects, x);
        ensureRowVisibleResume(resumeCol);
        ensureSectionVisible("continue");
        aim(resumeFocus, resumeCol, false);
        resumeLayer.invalidate();
    }

    /** The Continue Watching row: each app's card, with a progress bar and where it stopped under it. */
    private void drawResumeRow(Canvas c) {
        if (resumeItems.isEmpty() || resumeRects.length != resumeItems.size()) return;
        applyTone();
        String title = Ui.tr("Continue Watching");
        c.drawText(title, MARGIN * k - Ui.leftBearing(rowLabel, title), ROWS_TOP * k + 28 * k, rowLabel);
        int focused = -1;
        for (int i = 0; i < resumeItems.size(); i++) {
            if (resumeFocus[i].value > 0.02f && (focused < 0 || resumeFocus[i].value > resumeFocus[focused].value)) focused = i;
        }
        for (int i = 0; i < resumeItems.size(); i++) if (i != focused) drawResumeCard(c, i);
        if (focused >= 0) drawResumeCard(c, focused);
    }

    private void drawResumeCard(Canvas c, int index) {
        Resume.Entry e = resumeItems.get(index);
        RectF base = resumeRects[index];
        float focus = Ui.clamp(resumeFocus[index].value), scale = 1 + 0.045f * focus;
        float cx = base.centerX(), cy = base.centerY() - 5 * k * focus;
        float bw = base.width(), bh = base.height(), radius = 18 * k;
        cell.set(cx - bw * scale / 2, cy - bh * scale / 2, cx + bw * scale / 2, cy + bh * scale / 2);
        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
        tmp.offset(0, (8 + 10 * focus) * k);
        shadowDeep.setColor(Ui.alpha(0xFF000000, 0.34f + 0.18f * focus));
        shadows.draw(c, tmp, radius, DEEP_BLUR * k, shadowDeep);
        c.restore();

        HomeApps.Item tile = resumeTiles.get(e.pkg);
        if (tile == null) requestResumeTile(e.pkg);   // app branding also identifies sessions without a content title
        Bitmap art = Resume.artwork(getContext().getApplicationContext(), e, () -> resumeLayer.invalidate());
        if (art != null) {
            String artKey = Resume.key(e);
            BitmapShader shader = resumeArtworkShaders.get(artKey);
            if (shader == null) {
                shader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                resumeArtworkShaders.put(artKey, shader);
            }
            roundImage(c, art, shader, cell, radius * scale);
        } else if (tile != null && tile.tile != null) {
            roundImage(c, tile.tile, tile.tileShader, cell, radius * scale);
        } else {
            fill.setShader(null);
            fill.setColor(0xFF161618);
            c.drawRoundRect(cell, radius * scale, radius * scale, fill);
            if (e.title.isEmpty()) {
                fitPaint.set(rowTitle);
                fitPaint.setTextSize(40 * k);
                fitPaint.setColor(WHITE);
                fitPaint.setTextAlign(Paint.Align.CENTER);
                String name = TextUtils.ellipsize(e.app, fitPaint, cell.width() - 64 * k, TextUtils.TruncateAt.END).toString();
                c.drawText(name, cell.centerX(), cell.centerY() + Ui.halfCap(fitPaint), fitPaint);
            }
        }
        if (e.dur > 0 && e.pos > 0 && e.pos < e.dur) {   // where it stopped, as on Apple TV's Up Next
            float pad = 16 * k, h = 7 * k, x0 = cell.left + pad, x1 = cell.right - pad, top = cell.bottom - 16 * k;
            fill.setShader(null);
            // Dark backing preserves contrast on white app logos as well as bright thumbnail frames.
            fill.setColor(0xE6101012);
            c.drawRoundRect(x0 - 5 * k, top - 5 * k, x1 + 5 * k, top + h + 5 * k, 7 * k, 7 * k, fill);
            fill.setColor(0xFF626268);
            c.drawRoundRect(x0, top, x1, top + h, h / 2, h / 2, fill);
            float at = Ui.clamp((float) e.pos / e.dur);
            if (at > 0f) {
                fill.setColor(0xE6FFFFFF);
                c.drawRoundRect(x0, top, x0 + Math.max(h, (x1 - x0) * at), top + h, h / 2, h / 2, fill);
            }
        }
        if (focus > 0.01f) {
            rim.setShader(null);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(3 * k);
            rim.setColor(Ui.alpha(WHITE, 0.88f * focus));
            c.drawRoundRect(cell, radius * scale, radius * scale, rim);
        }
        rowTitle.setTextSize(22 * k);
        fitPaint.set(rowTitle);
        String caption = TextUtils.ellipsize(Resume.title(e), fitPaint, bw, TextUtils.TruncateAt.END).toString();
        c.drawText(caption, base.left, base.bottom + 34 * k, rowTitle);
        String meta = resumeMeta(e);
        if (!meta.isEmpty()) {
            rowMeta.setTextSize(18 * k);
            fitPaint.set(rowMeta);
            meta = TextUtils.ellipsize(meta, fitPaint, bw, TextUtils.TruncateAt.END).toString();
            c.drawText(meta, base.left, base.bottom + 62 * k, rowMeta);
        }
    }

    /** The small line under a card: the app, then how far it has to go, then when it was last seen. */
    private static String resumeMeta(Resume.Entry e) {
        StringBuilder s = new StringBuilder(e.app);
        String left = Resume.left(e);
        if (!left.isEmpty()) {
            if (s.length() > 0) s.append(" · ");
            s.append(left);
        }
        String ago = Resume.ago(e);
        if (!ago.isEmpty()) {
            if (s.length() > 0) s.append(" · ");
            s.append(ago);
        }
        return s.toString();
    }

    /** The card's app tile, made off the UI thread (extracting a banner decodes a bitmap); the row redraws when it's in. */
    private void requestResumeTile(final String pkg) {
        if (resumeTiles.containsKey(pkg)) return;   // in the making, or the app has no art to give
        resumeTiles.put(pkg, null);
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            final HomeApps.Item it = HomeApps.tileItem(c, pkg);
            handler.post(() -> {
                resumeTiles.put(pkg, it);
                resumeLayer.invalidate();
            });
        });
    }

    private float focusX() {
        if (zone == Z_RESUME && resumeCol >= 0 && resumeCol < resumeRects.length) {
            return resumeRects[resumeCol].centerX();
        } else if (isRowZone(zone)) {
            int row = zone - Z_ROW0;
            if (rowRects[row] != null && rowCol[row] >= 0 && rowCol[row] < rowRects[row].length) {
                return rowRects[row][rowCol[row]].centerX();
            }
        } else if (zone == Z_DRAWER && drawerIndex < drawerRects.length) return drawerRects[drawerIndex].centerX();
        else if (zone == Z_SHELF && shelfIndex < tileRects.length) return tileRects[shelfIndex].centerX();
        return w / 2f;
    }

    private static boolean isRowZone(int zone) {
        return zone >= Z_ROW0 && zone < Z_ROW0 + 4;
    }

    private void enterRow(int row, float x) {
        if (row < 0 || row >= rowItems.length || rowItems[row].isEmpty()) return;
        zone = Z_ROW0 + row;
        rowCol[row] = nearestRow(row, x);
        ensureRowVisible(row, rowCol[row]);
        aim(rowFocus[row], rowItems[row].isEmpty() ? -1 : rowCol[row], false);
        rowLayers[row].invalidate();
        ensurePageScroll(row);
    }

    /** Swaps the background with a cross-fade (from one image to another, from the gradient to an image, or back). */
    private void showBackdrop(Backdrop b) {
        if (b == bgTo) return;
        long up = SystemClock.uptimeMillis();
        // In the middle of another swap, start from whichever is more visible.
        if (bgFadeAt == 0 || fadeEase(bgFadeAt, up) >= 0.5f) bgFrom = bgTo;
        bgTo = b;
        bgFadeAt = up;
        nightPair = false;
        if (b == null || glassOf(b) == null) return;
        if (bgFrom == null || glassOf(bgFrom) == null) {
            scene.setFrost(frostOf(b));
            scene.setGlassImage(glassOf(b));
        } else {
            scene.setGlassImages(glassOf(bgFrom), glassOf(b));
        }
    }

    /** What the glass shows: the photo lightened (by day), darkened (at night) or blended in between. */
    private int[] glassOf(Backdrop b) {
        if (b.nightGlass == null || night <= 0.001f) return b.lightGlass;
        if (night >= 0.999f) return b.nightGlass;
        // During the transition, the blend (only for the current background; a photo fading in uses the nearer side).
        if (b != bgTo) return night < 0.5f ? b.lightGlass : b.nightGlass;
        for (int i = 0; i < glassNight.length; i++) glassNight[i] = mix(b.lightGlass[i], b.nightGlass[i], night);
        return glassNight;
    }

    /** No photo: the glass reads the background's own gray (not Scene's default aurora, which isn't on screen). */
    private void neutralGlass() {
        if (!scene.shaded()) {   // Android 8 to 12: a gray image would be one more full-area pass for the flat look
            scene.clearFrost();
            scene.clearGlass();
            nightPair = false;
            return;
        }
        int color = blend(0xFFEBEBF0, 0xFF141416, night);
        java.util.Arrays.fill(neutralFrostPx, color);
        java.util.Arrays.fill(neutralGlassPx, color);
        scene.setFrost(neutralFrostPx);
        scene.setGlassImage(neutralGlassPx);
        nightPair = false;
    }

    /** What the frosted glass shows (drawer, toasts): the blurred photo, darkened at night. */
    private int[] frostOf(Backdrop b) {
        if (b.nightFrost == null || night <= 0.001f) return b.frost;
        if (night >= 0.999f) return b.nightFrost;
        if (b != bgTo) return night < 0.5f ? b.frost : b.nightFrost;
        for (int i = 0; i < frostNight.length; i++) frostNight[i] = mix(b.frost[i], b.nightFrost[i], night);
        return frostNight;
    }

    // MARK: - Night look

    /** Day or night right now (from the sun at your location); `animate`: with the transition. */
    private void checkNight(boolean animate) {
        // The dark look always, until a toggle exists in Home Settings (the day-night transition machinery stays).
        boolean want = true;
        if (want == nightTarget) return;
        nightTarget = want;
        if (animate && running) {
            nightFrom = night;
            nightAt = SystemClock.uptimeMillis();
        } else {
            night = want ? 1 : 0;
            nightAt = 0;
            nightChanged();
        }
    }

    /** Advances the day ⇄ night transition; true while it runs. */
    private boolean tickNight(long up) {
        if (nightAt == 0) return false;
        float t = Ui.clamp((up - nightAt) / NIGHT_MS), to = nightTarget ? 1 : 0;
        night = nightFrom + (to - nightFrom) * t * t * (3 - 2 * t);
        if (t >= 1) {
            night = to;
            nightAt = 0;
        }
        nightChanged();
        return true;
    }

    /** Redraws for the current night value (the glass now reads the lightened, blended or darkened photo). */
    private void nightChanged() {
        if (bgFadeAt == 0 && bgTo != null && bgTo.lightGlass != null) {
            scene.setFrost(frostOf(bgTo));
            if (nightAt != 0 && bgTo.nightGlass != null) {
                if (!nightPair) {
                    scene.setGlassImages(bgTo.lightGlass, bgTo.nightGlass);
                    nightPair = true;
                }
                scene.setGlassMix(night);
            } else {
                scene.setGlassImage(glassOf(bgTo));
                nightPair = false;
            }
        } else if (bgFadeAt == 0 && bgTo == null) {
            neutralGlass();
        }
        tilesDirty = drawerDirty = true;
        for (Layer row : rowLayers) row.invalidate();
        resumeLayer.invalidate();
        sky.invalidate();
        platter.invalidate();
    }

    /** How dark it is: 0 = day, 1 = night (in between during the transition). */
    private float dark() {
        return night;
    }

    /** The day color or the night color, depending on the night value (blended during the transition). */
    private int tone(int onLight, int onDark) {
        return blend(onLight, onDark, dark());
    }

    private float tone(float onLight, float onDark) {
        return onLight + (onDark - onLight) * dark();
    }

    /** Text colors for the current night value (dark by day, white at night). */
    private void applyTone() {
        if (rowLabel != null) rowLabel.setColor(tone(L_INK, WHITE));
        if (rowTitle != null) rowTitle.setColor(tone(L_INK, WHITE));
        if (rowMeta != null) rowMeta.setColor(tone(L_DIM, 0xFF98989D));
        if (lQuote != null) lQuote.setColor(tone(L_FAINT, 0xB8FFFFFF));
    }

    private boolean tickBackgroundFade(long up) {
        if (bgFadeAt == 0) return false;
        float e = fadeEase(bgFadeAt, up);
        if (bgTo != null && bgFrom != null && glassOf(bgFrom) != null && glassOf(bgTo) != null) {
            if (drawerLayer.getVisibility() == VISIBLE || toast != null) {
                int[] from = frostOf(bgFrom), to = frostOf(bgTo);
                for (int i = 0; i < frostMix.length; i++) frostMix[i] = mix(from[i], to[i], e);
                scene.setFrost(frostMix);
            }
            scene.setGlassMix(e);
        }
        if (e >= 1) {
            bgFadeAt = 0;
            bgFrom = null;
            if (bgTo == null) {
                neutralGlass();
            } else {
                scene.setFrost(frostOf(bgTo));
                if (glassOf(bgTo) != null) scene.setGlassImage(glassOf(bgTo));
            }
        }
        return true;
    }

    private float fadeEase(long start, long up) {
        return fadeEase(start, up, FADE_MS);
    }

    private static float fadeEase(long start, long up, float duration) {
        float t = Ui.clamp((up - start) / duration);
        return t * t * (3 - 2 * t);
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // MARK: - Background

    private void drawSky(Canvas c) {
        long up = SystemClock.uptimeMillis();
        float e = bgFadeAt == 0 ? 1 : fadeEase(bgFadeAt, up);
        boolean fading = bgFadeAt != 0;
        if (bgTo == null || (fading && bgFrom == null)) {
            if (lightGradient == null) lightGradient = Ui.gradient(h, 0xFFF5F5F7, 0xFFE2E2E7);
            if (nightGradient == null) nightGradient = Ui.gradient(h, 0xFF1C1C1E, 0xFF0B0B0D);
            if (night < 0.999f) {
                fill.setShader(lightGradient);
                fill.setAlpha(Math.round(255 * (1 - night)));
                c.drawRect(screen, fill);
            }
            if (night > 0.001f) {
                fill.setShader(nightGradient);
                fill.setAlpha(Math.round(255 * night));
                c.drawRect(screen, fill);
            }
            fill.setAlpha(255);
            fill.setShader(null);
        }
        if (fading && bgFrom != null) drawLightImage(c, bgFrom, bgTo == null ? 1 - e : 1);
        if (bgTo != null) drawLightImage(c, bgTo, fading ? e : 1);
    }

    private void drawLightImage(Canvas c, Backdrop backdrop, float alpha) {
        if (night < 0.999f && backdrop.lightImage != null) drawImage(c, backdrop.lightImage, alpha * (1 - night));
        if (night > 0.001f && backdrop.nightImage != null) drawImage(c, backdrop.nightImage, alpha * night);
    }

    /** Blends a color on top (the drawer's veil) as part of drawing the image, without another layer. */
    private android.graphics.ColorFilter veil(float amount, int color) {
        if (amount <= 0.002f) return null;
        amount = Math.round(amount * 255) / 255f;
        if (amount == veilAmount && color == veilColor) return veilFilter;
        veilAmount = amount;
        veilColor = color;
        float keep = 1 - amount;
        return veilFilter = new android.graphics.ColorMatrixColorFilter(new float[] {
            keep, 0, 0, 0, ((color >> 16) & 255) * amount,
            0, keep, 0, 0, ((color >> 8) & 255) * amount,
            0, 0, keep, 0, (color & 255) * amount,
            0, 0, 0, 1, 0});
    }

    private void drawImage(Canvas c, Bitmap image, float alpha) {
        if (image == null || alpha <= 0.004f) return;
        imagePaint.setAlpha(Math.round(255 * Math.min(1, alpha)));
        c.drawBitmap(image, null, screen, imagePaint);
    }

    // MARK: - Hero

    private static void text(Canvas c, String s, float x, float y, Paint p, int color, float alpha) {
        if (s == null || s.isEmpty()) return;
        p.setColor(Ui.alpha(color, alpha));
        c.drawText(s, x, y, p);
    }

    // MARK: - Quotes and shelf

    private void setShelf(List<HomeApps.Item> list) {
        wake();
        cancelReorder(true);    // the list changed under the move (an app installed or removed)
        tileShaders = new LinearGradient[0];
        boolean same = list.size() == shelf.size();
        for (int i = 0; same && i < list.size(); i++) same = list.get(i).key.equals(shelf.get(i).key);
        shelf = list;
        if (!same || tileFocus.length != list.size()) {
            tileFocus = new Spring[list.size()];
            tileSlide = new Spring[list.size()];
            tileRects = new RectF[list.size()];
            for (int i = 0; i < tileFocus.length; i++) {
                tileFocus[i] = new Spring(0.34f, 0.74f, 0);
                tileSlide[i] = new Spring(0.32f, 0.86f, 0);
                tileRects[i] = new RectF();
            }
            shelfIndex = Math.min(shelfIndex, Math.max(0, list.size() - 1));
            retarget(true);
            layoutShelf();
        }
        tilesDirty = true;
        platter.invalidate();
    }

    private void layoutShelf() {
        int n = Math.min(shelf.size(), shelfVisible);

        float pad = SHELF_PAD * k, gap = TILE_GAP * k;
        float full = (CONTENT_R - MARGIN) * k - 2 * pad;
        // Tiles in the TV banners' own 16:9, so an app's art is never stretched; never taller than L_TILE_H. The glass
        // keeps its bottom edge, so with more apps it only gets lower.
        float tw = Math.min(L_TILE_H * 16f / 9 * k, (full - Math.max(0, n - 1) * gap) / Math.max(1, n));
        float th = tw * 9f / 16;
        float width = n * tw + Math.max(0, n - 1) * gap, left = (w - width) / 2f;
        float top = (L_DOCK_TOP + L_PLATTER_TOP + L_TILE_H) * k - th;
        platterRect.set(MARGIN * k, top, CONTENT_R * k, top + th + 2 * pad);
        snap(platterRect);

        shelfFirst = Math.max(0, Math.min(shelfFirst, shelf.size() - n));
        float x = left - shelfFirst * (tw + gap), y = top + pad;
        for (int i = 0; i < shelf.size(); i++) {
            tileRects[i].set(x, y, x + tw, y + th);
            snap(tileRects[i]);
            x += tw + gap;
        }
        crispLogos.clear();
    }

    /** Scrolls the shelf, one app at a time, so the focused app is shown: the tiles slide there (unless `snap`). */
    private void revealShelf(boolean snap) {
        int n = Math.min(shelf.size(), shelfVisible);
        int first = Math.max(0, Math.min(Math.max(shelfFirst, shelfIndex - n + 1), Math.min(shelfIndex, shelf.size() - n)));
        if (first == shelfFirst || tileRects.length == 0) return;
        float dx = (shelfFirst - first) * (tileRects[0].width() + TILE_GAP * k);
        for (int i = 0; i < tileRects.length; i++) {
            tileRects[i].offset(dx, 0);
            if (!snap && i < tileSlide.length) tileSlide[i].value -= dx;
        }
        shelfFirst = first;
        tilesDirty = true;
        if (snap) return;
        reorderAnim = true;   // the same slides as moving an app
        wake();
    }

    /** Shelf layer: the glass bar and its shadow (it only changes with the background). */
    private void drawPlatter(Canvas c) {
        applyTone();

        // The sleep timer's countdown floats under the shelf's glass ("Off in 42 min"); before the empty-shelf
        // early-out, so a timer still shows with a bare sheet.
        if (!sleepText.isEmpty()) {
            float right = CONTENT_R * k;
            float cy = (L_DOCK_TOP + L_PLATTER_TOP + L_TILE_H + 2 * SHELF_PAD + 10) * k;
            float tw = lQuote.measureText(sleepText), size = lQuote.getTextSize();
            c.drawText(sleepText, right - tw, cy + Ui.halfCap(lQuote), lQuote);
            Symbols.draw(getContext(), c, "bedtime", right - tw - size * 0.9f, cy, size, lQuote.getColor());
        }
        if (shelf.isEmpty()) return;
        float radius = CARD_RADIUS * k;
        tmp.set(platterRect);
        tmp.offset(0, 18 * k);
        shadow.setColor(tone(0x1F000000, 0x52000000));
        shadows.draw(c, tmp, radius, SHADOW_BLUR * k, shadow);
        // White glass by day; smoked glass at night.
        scene.liquid(c, platterRect, radius, tone(0xFFFFFFFF, SMOKE), tone(0.42f, 0.12f), tone(0.03f, 0.08f),
                tone(30, 34) * k, tone(22, 28) * k, tone(0.65f, 0.95f), 1);
    }

    private void drawTiles(Canvas c) {
        // The focused one last: it grows over its neighbors without being covered by them.
        int top = -1;
        float most = 0.02f;
        for (int i = 0; i < shelf.size(); i++) {
            if (tileFocus[i].value > most) {
                most = tileFocus[i].value;
                top = i;
            }
        }
        if (liftIndex >= 0 && liftIndex < shelf.size() && moveLift.value > 0.02f) top = liftIndex;
        int last = Math.min(shelf.size(), shelfFirst + shelfVisible);
        // Scrolling, tiles slide in and out through the glass's sides (only those that show any part are drawn). Only
        // then: on a shelf that fits, the clip would cut the end tiles' shadows in a straight line.
        boolean scrolls = shelf.size() > shelfVisible;
        if (scrolls) {
            c.save();
            c.clipRect(platterRect.left, 0, platterRect.right, h);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < shelf.size(); i++) {
                if ((pass == 0) == (i == top)) continue;
                float slid = i < tileSlide.length ? tileSlide[i].value : 0;
                if (tileRects[i].right + slid <= platterRect.left || tileRects[i].left + slid >= platterRect.right) continue;
                // Moving apps: the tile slides from where it was and the lifted one wiggles (only then: no extra work
                // otherwise).
                float dx = i < tileSlide.length ? tileSlide[i].value : 0;
                float angle = i == liftIndex ? wiggle * moveLift.value : 0;
                if (dx == 0 && angle == 0) {
                    drawShelfTile(c, i);
                    continue;
                }
                c.save();
                c.translate(dx, 0);
                c.rotate(angle, tileRects[i].centerX(), tileRects[i].centerY());
                drawShelfTile(c, i);
                c.restore();
            }
        }
        if (scrolls) c.restore();
        // More apps than fit: a small arrow inside the glass's edge on each side that has more, where no tile reaches
        // even focused (inside the screen's safe area).
        float cy = platterRect.centerY(), size = 28 * k, inset = 14 * k;
        int ink = tone(0x99000000, 0xCCFFFFFF);
        if (last < shelf.size()) Symbols.draw(getContext(), c, "chevron_right", platterRect.right - inset, cy, size, ink);
        if (shelfFirst > 0) {
            c.save();
            c.scale(-1, 1, platterRect.left + inset, cy);
            Symbols.draw(getContext(), c, "chevron_right", platterRect.left + inset, cy, size, ink);
            c.restore();
        }
    }

    /**
     * App icon like the ones in the tvOS 27 Dock: the brand color edge to edge (a soft gradient) with the logo.
     * Focused: it grows 20% from the center, with a deep shadow, a subtle glow, and a slim white focus rim.
     */
    private void drawShelfTile(Canvas c, int i) {
        HomeApps.Item it = shelf.get(i);
        // Lifted to be moved: a little bigger than focused, with a deeper shadow.
        float l = i == liftIndex ? Math.max(0, moveLift.value) : 0;
        float f = tileFocus[i].value, fc = Ui.clamp(f), s = 1 + FOCUS_GROW * f + LIFT_GROW * l;
        RectF base = tileRects[i];
        float bw = base.width(), bh = base.height(), r0 = TILE_RADIUS * k;
        float cx = base.centerX(), cy = base.centerY();
        cell.set(cx - bw * s / 2, cy - bh * s / 2, cx + bw * s / 2, cy + bh * s / 2);
        float r = r0 * s;
        // Shadow at the resting icon size, enlarged by the transform (so the blur stays cached): short at rest, deep
        // and lower when focused.
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
        tmp.offset(0, (4 + 14 * f + 6 * l) * k);
        shadowDeep.setColor(Ui.alpha(0xFF000000, tone(0.14f, 0.3f) + tone(0.18f, 0.3f) * fc + 0.14f * Ui.clamp(l)));
        shadows.draw(c, tmp, r0, DEEP_BLUR * k, shadowDeep);
        c.restore();
        int[] style = TILE_STYLE.get(it.key);
        if (it.logo != null && style != null) {
            if (tileShaders.length != shelf.size() || tileShaderH != bh) {
                tileShaders = new LinearGradient[shelf.size()];
                tileShaderH = bh;
                tileGlow = new LinearGradient(0, -bh / 2, 0, 0, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            }
            if (tileShaders[i] == null) {
                tileShaders[i] = new LinearGradient(0, -bh / 2, 0, bh / 2, style[0], style[1], Shader.TileMode.CLAMP);
            }
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
            tileFill.setShader(tileShaders[i]);
            tileFill.setAlpha(255);
            c.drawRoundRect(tmp, r0, r0, tileFill);
            if (fc > 0.01f) {
                tileFill.setShader(tileGlow);
                tileFill.setAlpha(Math.round(255 * fc));
                c.drawRoundRect(tmp, r0, r0, tileFill);
                tileFill.setAlpha(255);
            }
            tileFill.setShader(null);
            c.restore();
            drawLogo(c, it, cell, s, style[2]);
        } else if (it.logo != null) {
            tileFill.setShader(null);
            tileFill.setColor(0xFF161618);
            c.drawRoundRect(cell, r, r, tileFill);
            drawLogo(c, it, cell, s, 0);
        } else {
            if (it.tile != null) roundImage(c, it.tile, it.tileShader, cell, r);
        }
        if (fc > 0.01f) {
            rim.setShader(null);
            rim.setColor(Ui.alpha(WHITE, 0.9f * fc));
            rim.setStrokeWidth(2 * k);
            c.drawRoundRect(cell, r, r, rim);
            rim.setStrokeWidth(2.5f * k);
        }
    }

    /**
     * `image` stretched over `cell`, with corners of radius `r`: the shader of an anti-aliased rounded rectangle. A
     * clipPath there isn't anti-aliased on some GPUs (stair-stepped corners on a MediaTek TV), and on Android 8 to 11 it
     * cost a mask on every frame of a focus animation (measured on a Mi Box 3: most of a 49 ms frame). `shader` is the
     * image's own, made with it (HomeApps.Item, Backdrop) and dropped with it; the canvas does the scaling, so drawing
     * changes nothing in it and allocates nothing.
     */
    private void roundImage(Canvas c, Bitmap image, BitmapShader shader, RectF cell, float r) {
        float sx = cell.width() / image.getWidth(), sy = cell.height() / image.getHeight();
        c.save();
        c.translate(cell.left, cell.top);
        c.scale(sx, sy);
        imageFill.setShader(shader);
        c.drawRoundRect(0, 0, image.getWidth(), image.getHeight(), r / sx, r / sy, imageFill);
        imageFill.setShader(null);
        c.restore();
    }

    /**
     * Logo at its "optical" size (all with the same amount of ink, plus each one's own adjustment, never past 82% × 63%
     * of the tile), centered and already filtered down to the focused tile size and to the resting one: focused or at
     * rest it is drawn pixel for pixel, without resampling (on a 720p UI, shrinking at draw time made it look jagged).
     */
    private void drawLogo(Canvas c, HomeApps.Item it, RectF box, float s, int mode) {
        boolean rest = s < 1 + FOCUS_GROW / 2;
        Bitmap logo = crispLogo(it, mode == 2 && it.logoDark != null, rest);
        if (logo == null) return;
        float scale = rest ? s : s / (1 + FOCUS_GROW);
        float dw = logo.getWidth() * scale, dh = logo.getHeight() * scale;
        float left = box.centerX() - dw / 2, top = box.centerY() - dh / 2;
        if (rest ? Math.abs(scale - 1) < 0.002f : scale > 0.998f) {
            left = Math.round(left);
            top = Math.round(top);
        }
        tmp.set(left, top, left + dw, top + dh);
        bitmapPaint.setColorFilter(mode == 1 ? whiteLogo : null);
        c.drawBitmap(logo, null, tmp, bitmapPaint);
        bitmapPaint.setColorFilter(null);
    }

    private Bitmap crispLogo(HomeApps.Item it, boolean dark, boolean rest) {
        Bitmap src = dark ? it.logoDark : it.logo;
        if (tileRects.length == 0) return src;
        String key = it.key + "@" + System.identityHashCode(src) + "@" + Math.round(tileRects[0].width()) + (rest ? "@rest" : "");
        Bitmap cached = crispLogos.get(key);
        if (cached != null) return cached;
        float bw = tileRects[0].width() * (1 + FOCUS_GROW), bh = tileRects[0].height() * (1 + FOCUS_GROW);
        float lw = src.getWidth(), lh = src.getHeight();
        float scale = (float) Math.sqrt(bw * bh * 0.10f / (lw * lh * Math.max(0.08f, it.logoInk))) * it.logoScale;
        scale = Math.min(scale, Math.min(bw * 0.82f / lw, bh * 0.63f / lh));
        if (rest) scale /= 1 + FOCUS_GROW;
        Bitmap out = Ui.downscale(src, Math.round(lw * scale), Math.round(lh * scale));
        crispLogos.put(key, out);
        return out;
    }

    // MARK: - Bottom drawer

    /** The chips row: the shortcuts first (All Apps, Sleep Timer…), then Live TV and the HDMI inputs. */
    private static List<HomeApps.Item> drawerList(Context c) {
        List<HomeApps.Item> list = HomeApps.drawerShortcuts(c);
        list.addAll(HomeApps.allInputs(c));
        return list;
    }

    private void setDrawerItems(List<HomeApps.Item> list) {
        boolean same = list.size() == drawerItems.size();
        for (int i = 0; same && i < list.size(); i++) {
            same = list.get(i).key.equals(drawerItems.get(i).key) && list.get(i).connected == drawerItems.get(i).connected;
        }
        drawerItems = list;
        if (drawerFocus.length != list.size()) {
            drawerFocus = new Spring[list.size()];
            drawerRects = new RectF[list.size()];
            for (int i = 0; i < drawerFocus.length; i++) {
                drawerFocus[i] = new Spring(0.28f, 0.85f, 0);
                drawerRects[i] = new RectF();
            }
            drawerIndex = Math.min(drawerIndex, Math.max(0, list.size() - 1));
            retarget(true);
        }
        layoutDrawer();
        layoutStrip();
        if (!same) drawerDirty = true;
    }

    /** The chips row: tiles only, so nothing else in the drawer layer reads the settings or weather. */

    private void layoutDrawer() {
        int n = drawerItems.size();
        float left = MARGIN * k, right = CONTENT_R * k;
        float gap = 18 * k;
        float tw = Math.min(230 * k, (right - left - Math.max(0, n - 1) * gap) / Math.max(1, n));
        float x = left, top = CHIPS_TOP * k;
        for (int i = 0; i < n; i++) {
            drawerRects[i].set(x, top, x + tw, top + ROW_H * k);
            snap(drawerRects[i]);
            x += tw + gap;
        }
        // The symbols at their sizes at rest and focused: drawing them never scales one.
        for (HomeApps.Item it : drawerItems) {
            Symbols.warm(getContext(), it.symbol, 34 * k);
            Symbols.warm(getContext(), it.symbol, 34 * k * 1.07f);
        }
        drawerDirty = true;
    }

    /** The weather strip (and the crypto next to it, when it's on) in its own band at the top, dock-side margins. */
    private void layoutStrip() {
        float left = MARGIN * k, right = CONTENT_R * k, top = STRIP_TOP * k, split = left + (right - left) * 0.585f;
        boolean wx = Prefs.weather(), qt = Prefs.quotes() && HomeData.active().length > 0;
        // A card that is off gives its room to the other one.
        weatherCard.set(left, top, qt ? split - 16 * k : right, top + STRIP_H * k);
        quotesCard.set(wx ? split + 16 * k : left, top, right, top + STRIP_H * k);
        snap(weatherCard);
        snap(quotesCard);
        // The symbols at their sizes: drawing them never scales one.
        Symbols.warmWeather(getContext(), 40 * k);   // the strip's today symbol (next to the big temperature)
        Symbols.warmWeather(getContext(), 22 * k);   // the forecast columns': the hours' and the days'
        Symbols.warmWeather(getContext(), 20 * k);   // the days' symbols sit next to their lows and highs
        Symbols.warm(getContext(), "sunrise", 22 * k);
        Symbols.warm(getContext(), "sunset", 22 * k);
        strip.invalidate();
    }

    /** Moves focus to All Apps at the top of the screen. */
    private void openChips() {
        if (drawerItems.isEmpty()) return;
        zone = Z_DRAWER;
        drawerIndex = 0;
        retarget(false);
    }

    /** Color for the drawer's text and symbols: dark by day, white at night. */
    private int drawerInk(float alpha) {
        return Ui.alpha(tone(L_INK, 0xFFFFFFFF), alpha);
    }

    /** Glass for a drawer card or button: white by day, smoked at night (both during the transition). */
    private void drawerGlass(Canvas c, RectF r, float radius, float lightTint, int shadowColor, float drop) {
        float dk = dark();
        if (dk < 0.999f) {
            tmp.set(r);
            tmp.offset(0, drop);
            shadow.setColor(Ui.alpha(shadowColor, 1 - dk));
            shadows.draw(c, tmp, radius, SHADOW_BLUR * k, shadow);
            scene.glass(c, r, radius, 0xFFFFFFFF, lightTint, 0, 0, 0.02f, 1, 1 - dk);
        }
        if (dk > 0.001f) scene.glass(c, r, radius, SMOKE, 0.4f, 0, 0, 0.02f, 1, dk);
    }

    private void drawerCard(Canvas c, RectF r) {
        drawerGlass(c, r, CARD_RADIUS * k, 0.72f, 0x1A000000, 10 * k);
    }

    private void drawChips(Canvas c) {
        if (drawerItems.isEmpty()) return;
        int top = -1;
        for (int i = 0; i < drawerItems.size(); i++) if (drawerFocus[i].value > 0.02f && (top < 0 || drawerFocus[i].value > drawerFocus[top].value)) top = i;
        for (int i = 0; i < drawerItems.size(); i++) if (i != top) drawDrawerTile(c, i);
        if (top >= 0) drawDrawerTile(c, top);
    }

    /** The strip at the top: the weather (and next to it, at its side, the crypto when it's on). */
    private void drawStrip(Canvas c) {
        cardTitle.setColor(tone(L_DIM, 0xB3FFFFFF));
        cardText.setColor(drawerInk(0.95f));
        cardDim.setColor(tone(L_DIM, 0xADFFFFFF));
        cardDimRight.setColor(tone(L_FAINT, 0x99FFFFFF));
        bigTemp.setColor(drawerInk(1));
        hourPaint.setColor(tone(L_DIM, 0xADFFFFFF));
        hourTemp.setColor(drawerInk(0.95f));
        dayLow.setColor(tone(L_DIM, 0xADFFFFFF));
        pricePaint.setColor(drawerInk(0.95f));
        if (Prefs.weather()) drawWeatherCard(c, weatherCard);
        if (Prefs.quotes() && HomeData.active().length > 0) drawQuotesCard(c, quotesCard);
    }

    /** Square drawer button: symbol and name; an input with no device is dimmed. Focused: white, with dark text. */
    private void drawDrawerTile(Canvas c, int i) {
        HomeApps.Item it = drawerItems.get(i);
        float f = Ui.clamp(drawerFocus[i].value), s = 1 + 0.07f * f;
        RectF base = drawerRects[i];
        float cx = base.centerX(), cy = base.centerY();
        cell.set(cx - base.width() * s / 2, cy - base.height() * s / 2, cx + base.width() * s / 2, cy + base.height() * s / 2);
        float r = 20 * k * s;
        if (f > 0.01f) {
            tmp.set(cell);
            tmp.offset(0, 12 * k * f);
            shadow.setColor(Ui.alpha(0xFF000000, 0.45f * f));
            shadows.draw(c, tmp, r, SHADOW_BLUR * k, shadow);
        }
        if (f < 0.99f) drawerGlass(c, cell, r, 0.7f, 0x14000000, 6 * k);
        if (f > 0.01f) {
            fill.setShader(null);
            fill.setColor(Ui.alpha(WHITE, f));
            c.drawRoundRect(cell, r, r, fill);
        }
        int ink = blend(tone(0xFF8E8E93, 0x6BFFFFFF), INK, f);
        Symbols.draw(getContext(), c, it.symbol, cx, cell.top + cell.height() * 0.4f, 34 * k * s, ink);
        drawerLabel.setColor(ink);
        drawerLabel.setTextSize(23 * k * s);
        float room = cell.width() - 16 * k, wide = drawerLabel.measureText(it.label);
        // A long name ("Home Settings", an input you renamed) shrinks a little before it gets cut.
        if (wide > room) drawerLabel.setTextSize(Math.max(18 * k * s, 23 * k * s * room / wide));
        fitPaint.set(drawerLabel);
        String label = TextUtils.ellipsize(it.label, fitPaint, room, TextUtils.TruncateAt.END).toString();
        c.drawText(label, cx, cell.top + cell.height() * 0.8f, drawerLabel);
        drawerLabel.setTextSize(23 * k);
    }

    /** Weather for your location: now (large), the next 6 hours and 6 days, and sunrise and sunset. */
    private void drawWeatherCard(Canvas c, RectF r) {
        drawerCard(c, r);
        if (Ui.LEGACY) weatherSnapshot.draw(c, r, drawerCardsKey(), cc -> drawWeather(cc, r));
        else drawWeather(c, r);
    }

    /**
     * Weather for your location: at the left, the today column (the city; the symbol and the temperature; the high and
     * the low; the sunrise and the sunset at the bottom), then a vertical divider, then the forecast in two rows — the
     * next 6 hours, a horizontal hairline, the next 6 days. No header labels: the rows read on their own. A row
     * without data (an older, offline forecast only carried one of them) takes the whole section.
     */
    private void drawWeather(Canvas c, RectF r) {
        refreshDrawerText();
        HomeData.Weather wx = textWeather;
        float x = r.left + 34 * k;
        float verticalScale = STRIP_H / 200f;
        // The city and sunrise/sunset line stay evenly inset as the card grows.
        c.drawText(weatherTitle, x, r.top + WEATHER_CITY_BASE * verticalScale * k, cardTitle);
        if (Double.isNaN(wx.temp)) {
            c.drawText(Ui.tr("No weather data right now."), x, r.top + 80 * verticalScale * k, cardDim);
            return;
        }
        String symbol = Symbols.weather(wx.code, wx.isDay);
        float sw = Symbols.width(getContext(), symbol, 40 * k);
        Symbols.draw(getContext(), c, symbol, x + sw / 2, r.top + 82 * verticalScale * k, 40 * k, drawerInk(0.95f));
        c.drawText(nowTemp, x + sw + 12 * k - Ui.leftBearing(bigTemp, nowTemp),
                r.top + 106 * verticalScale * k, bigTemp);
        c.drawText(highLow, x, r.top + 140 * verticalScale * k, cardDim);
        float sunX = x;
        float sunBase = r.top + WEATHER_SUN_BASE * verticalScale * k;
        if (wx.sunrise != 0) sunX = sunItem(c, "sunrise", sunriseText, sunX, sunBase) + 36 * k;
        if (wx.sunset != 0) sunItem(c, "sunset", sunsetText, sunX, sunBase);
        float split = r.left + 392 * k;
        fill.setShader(null);
        fill.setColor(tone(0x1A000000, 0x24FFFFFF));
        c.drawRoundRect(split - k, r.top + 28 * k, split + k, r.bottom - 28 * k, k, k, fill);
        float hx = split + 30 * k, right = r.right - 30 * k;
        boolean both = !wx.hours.isEmpty() && textDays;
        float lineGap = FORECAST_LINE_GAP * k;
        float groupHeight = forecastLabelCap + forecastLabelDescent + lineGap + forecastValueAbove + forecastValueBelow;
        float forecastMid = r.centerY();
        float dividerHalf = k;
        if (!wx.hours.isEmpty()) {
            float pad = both ? (forecastMid - dividerHalf - r.top - groupHeight) / 2
                    : (r.height() - groupHeight) / 2;
            float label = r.top + pad + forecastLabelCap;
            float value = label + forecastLabelDescent + lineGap + forecastValueAbove;
            forecastHours(c, hx, right, label, value, wx);
        }
        if (both) c.drawRoundRect(hx, forecastMid - dividerHalf, right, forecastMid + dividerHalf, k, k, fill);
        if (textDays) {
            // Treat the divider edges as the inner boundaries so each forecast row has equal top and bottom padding.
            float pad = both ? (r.bottom - forecastMid - dividerHalf - groupHeight) / 2
                    : (r.height() - groupHeight) / 2;
            float dayLabel = (both ? forecastMid + dividerHalf : r.top) + pad + forecastLabelCap;
            float dayValue = dayLabel + forecastLabelDescent + lineGap + forecastValueAbove;
            forecastDays(c, hx, right, dayLabel, dayValue, wx);
        } else if (wx.hours.isEmpty()) {
            c.drawText(Ui.tr("No forecast yet."), hx, forecastMid + Ui.halfCap(cardDim), cardDim);
        }
    }

    /** The hours row: one column per hour — the time, then its symbol and the temperature together. */
    private void forecastHours(Canvas c, float left, float right, float yTime, float yMid, HomeData.Weather wx) {
        float cw = (right - left) / 6, yTemp = yMid + Ui.halfCap(hourTemp);
        for (int i = 0; i < wx.hours.size() && i < 6; i++) {
            float cc = left + cw * (i + 0.5f);
            c.drawText(hourText[i], cc, yTime, hourPaint);
            String s = Symbols.weather(wx.hourCode.get(i), wx.hourDay.get(i));
            float sw = Symbols.width(getContext(), s, 22 * k), gap = 10 * k, tw = sw + gap + hourTemp.measureText(hourTempText[i]);
            float lx = cc - tw / 2;
            float fit = Math.min(1, Math.max(0, cw - 8 * k) / tw);
            c.save();
            c.scale(fit, 1, cc, 0);
            Symbols.draw(getContext(), c, s, lx + sw / 2, yMid, 22 * k, drawerInk(0.9f));
            float tempW = hourTemp.measureText(hourTempText[i]);
            c.drawText(hourTempText[i], lx + sw + gap + tempW / 2, yTemp, hourTemp);
            c.restore();
        }
    }

    /** The days row: one column per day — the weekday, then its icon and high/low together on the second line. */
    private void forecastDays(Canvas c, float left, float right, float yDay, float yMid, HomeData.Weather wx) {
        float cw = (right - left) / 6;
        for (int i = 0; i < wx.days.size() && i < 6; i++) {
            float cc = left + cw * (i + 0.5f);
            c.drawText(dayText[i], cc, yDay, hourPaint);
            String s = Symbols.weather(wx.dayCode.get(i), true);
            float sw = Symbols.width(getContext(), s, 22 * k), symbolGap = 8 * k;
            float maxW = hourTemp.measureText(dayMaxText[i]), minW = dayLow.measureText(dayMinText[i]), gap = 8 * k;
            float groupW = sw + symbolGap + maxW + gap + minW;
            float x = cc - groupW / 2;
            float fit = Math.min(1, Math.max(0, cw - 8 * k) / groupW);
            c.save();
            c.scale(fit, 1, cc, 0);
            Symbols.draw(getContext(), c, s, x + sw / 2, yMid, 22 * k, drawerInk(0.9f));
            c.drawText(dayMaxText[i], x + sw + symbolGap + maxW / 2, yMid + Ui.halfCap(hourTemp), hourTemp);
            c.drawText(dayMinText[i], x + sw + symbolGap + maxW + gap + minW / 2,
                    yMid + Ui.halfCap(dayLow), dayLow);
            c.restore();
        }
    }

    private void refreshDrawerText() {
        HomeData.Weather wx = data.weather;
        boolean settings = Prefs.version != textPrefs || !Ui.language.equals(textLanguage);
        textPrefs = Prefs.version;
        textLanguage = Ui.language;
        if (wx != textWeather || settings) {
            textWeather = wx;
            weatherTitle = Prefs.city().isEmpty() ? Ui.tr("Weather") : Prefs.city();
            nowTemp = HomeData.degrees(wx.temp);
            highLow = HomeData.highLow(wx, "   ");
            textDays = !wx.days.isEmpty();   // the data excludes today, so show exactly the next six dates
            for (int i = 0; textDays && i < wx.days.size() && i < 6; i++) {
                dayText[i] = HomeData.weekday(wx.days.get(i));
                dayMaxText[i] = HomeData.degrees(wx.dayMax.get(i));
                dayMinText[i] = HomeData.degrees(wx.dayMin.get(i));
            }
            for (int i = 0; i < wx.hours.size() && i < 6; i++) {
                hourText[i] = HomeData.hour(wx.hours.get(i));
                hourTempText[i] = HomeData.degrees(wx.hourTemp.get(i));
            }
            // The sunrise and sunset, as bare times (the icons name them); the row is only 358 px wide.
            sunriseText = Ui.time(wx.sunrise);
            sunsetText = Ui.time(wx.sunset);
        }
        long minute = System.currentTimeMillis() / 60_000;
        if (data.pricesAt != textPricesAt || minute != textMinute || settings) {
            textPricesAt = data.pricesAt;
            textMinute = minute;
            String stale = HomeData.staleness(data.pricesAt, 5 * 60_000L);
            staleText = stale.isEmpty() ? Ui.tr("24h change") : Ui.trf("quotes|{ago}", "ago", stale);
            for (int i = 0; i < HomeData.COINS.length; i++) {
                priceText[i] = HomeData.brl(data.prices[i]);
                changeText[i] = HomeData.change(data.changes[i]);
            }
        }
    }

    private float sunItem(Canvas c, String symbol, String label, float x, float base) {
        float size = 22 * k, sw = Symbols.width(getContext(), symbol, size);
        Symbols.draw(getContext(), c, symbol, x + sw / 2, base - Ui.halfCap(cardDim), size, tone(L_DIM, 0xB3FFFFFF));
        c.drawText(label, x + sw + 10 * k, base, cardDim);
        return x + sw + 10 * k + cardDim.measureText(label);
    }

    /** Prices in the configured currency with the 24 h change (green/red, like Apple's Stocks app). */
    private void drawQuotesCard(Canvas c, RectF r) {
        drawerCard(c, r);
        if (Ui.LEGACY) quotesSnapshot.draw(c, r, drawerCardsKey(), cc -> drawPrices(cc, r));
        else drawPrices(c, r);
    }

    /**
     * What the drawer cards show depends on: the data, the settings, the language, day or night, and the minute (the
     * sunrise and sunset, "12 min ago").
     */
    private String drawerCardsKey() {
        return data.weatherAt + "|" + data.pricesAt + "|" + Prefs.version + "|" + Ui.language + "|"
                + Float.floatToIntBits(dark()) + "|" + System.currentTimeMillis() / 60_000;
    }

    /**
     * Android 8 to 11: a drawer card's content (its text and symbols, not its glass) kept as a bitmap. The drawer is
     * redrawn on every frame of its focus animation, and laying that text out again took ~15 ms a frame on a Mi Box 3.
     */
    private static final class Snapshot {
        private Bitmap bitmap;
        private String key = "";

        void draw(Canvas c, RectF r, String key, Consumer<Canvas> content) {
            int left = Math.round(r.left), top = Math.round(r.top);
            int w = Math.max(1, Math.round(r.right) - left), h = Math.max(1, Math.round(r.bottom) - top);
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                this.key = "";
            }
            if (!key.equals(this.key)) {
                bitmap.eraseColor(0);
                Canvas cc = new Canvas(bitmap);
                cc.translate(-left, -top);
                content.accept(cc);
                this.key = key;
            }
            c.drawBitmap(bitmap, left, top, null);
        }
    }

    private final Snapshot weatherSnapshot = new Snapshot(), quotesSnapshot = new Snapshot();

    private void drawPrices(Canvas c, RectF r) {
        float x = r.left + 34 * k, right = r.right - 34 * k;
        refreshDrawerText();
        c.drawText(Ui.tr("Crypto"), x, r.top + 54 * k, cardTitle);
        c.drawText(staleText, right, r.top + 54 * k, cardDimRight);
        float cap = Ui.cap(cardText);
        int[] coins = HomeData.active();
        // Squeezed into the strip: up to three rows; the coins past them count on the last name ("Bitcoin +2").
        for (int j = 0; j < coins.length && j < 3; j++) {
            int i = coins[j];
            float base = r.top + (126 + 48 * j) * k, mid = base - cap / 2;
            String name = HomeData.coinName(i) + (j == 2 && coins.length > 3 ? " +" + (coins.length - 3) : "");
            float gw = Glyphs.width(getContext(), HomeData.COINS[i], cap);
            Glyphs.coin(getContext(), c, HomeData.COINS[i], x + 14 * k, mid, cap, tone(L_DIM, 0xCCFFFFFF));
            c.drawText(name, x + Math.max(gw, 28 * k) / 2 + 30 * k, base, cardText);
            c.drawText(priceText[i], right - 128 * k, base, pricePaint);
            double change = data.changes[i];
            if (Double.isNaN(change)) continue;
            int color = change >= 0 ? tone(0xFF248A3D, UP_COLOR) : tone(0xFFD70015, DOWN_COLOR);
            tmp.set(right - 108 * k, mid - 17 * k, right, mid + 17 * k);
            fill.setShader(null);
            fill.setColor(Ui.alpha(color, 0.2f));
            c.drawRoundRect(tmp, 17 * k, 17 * k, fill);
            changePaint.setColor(color);
            c.drawText(changeText[i], tmp.centerX(), mid + Ui.halfCap(changePaint), changePaint);
        }
    }

    // MARK: - Focus

    /** Sets the springs' targets from the current focus (snap: no animation). */
    private void retarget(boolean snap) {
        String active = sectionForZone(zone);
        if (!sheetOpen && !sleepOpen && (!sectionShown(active) || !populatedSection(active))) chooseDefaultFocus();
        revealShelf(snap);
        boolean focus = !sheetOpen && !sleepOpen;
        boolean tilesChanged = aim(tileFocus, focus && zone == Z_SHELF ? shelfIndex : -1, snap);
        boolean drawerChanged = aim(drawerFocus, focus && zone == Z_DRAWER ? drawerIndex : -1, snap);
        tilesDirty |= snap || tilesChanged;
        drawerDirty |= snap || drawerChanged;
        for (int i = 0; i < rowFocus.length; i++) {
            boolean changed = aim(rowFocus[i], focus && zone == Z_ROW0 + i ? rowCol[i] : -1, snap);
            if (snap || changed) rowLayers[i].invalidate();
        }
        boolean resumeChanged = aim(resumeFocus, focus && zone == Z_RESUME ? resumeCol : -1, snap);
        if (snap || resumeChanged) resumeLayer.invalidate();
        spoken.focusMoved();
    }

    /** Aims a group's springs (only `focused` at 1); returns true if any target changed. */
    private static boolean aim(Spring[] springs, int focused, boolean snap) {
        if (springs == null) return false;
        boolean changed = false;
        for (int i = 0; i < springs.length; i++) {
            float target = i == focused ? 1 : 0;
            changed |= springs[i].target != target;
            springs[i].target = target;
            if (snap) springs[i].snap(target);
        }
        return changed;
    }

    // MARK: - "All Apps" sheet

    private void setOthers(List<HomeApps.Item> list) {
        others = list;
        gridFocus = new Spring[list.size()];
        for (int i = 0; i < gridFocus.length; i++) gridFocus[i] = new Spring(0.3f, 0.82f, 0);
        gridLabels = null;
        gridIndex = Math.min(gridIndex, Math.max(0, list.size() - 1));
        retargetGrid(true);
        gridDirty = true;
    }

    /** All Apps from the remote's button. Coming back from another app, resume() opens it (it closes the sheet). */
    public void showAllApps() {
        if (!running) {
            allAppsOnResume = true;
            return;
        }
        reset(false);
        openSheet();
    }

    private boolean allAppsOnResume;

    private void openSheet() {
        sheetOpen = true;
        gridIndex = 0;
        scroll.snap(0);
        retargetGrid(true);
        retarget(false);
        sheet.target = 1;
        gridDirty = true;
    }

    private void closeSheet() {
        sheetOpen = false;
        sheet.target = 0;
        retarget(false);
        if (shelfEdited) {     // the grid was kept as it was while open: apps that went to the shelf leave it now
            shelfEdited = false;
            reload();
        }
    }

    // MARK: - Sleep timer

    /** The Sleep Timer chip: opens the picker with the running timer preselected (or Off). */
    private void openSleepPicker() {
        wake();
        sleepOpen = true;
        sleepIndex = 0;
        int left = SleepTimer.minutesLeft(getContext());
        if (left > 0) {   // nothing running: Off stays picked (5 min was the first choice, not what runs)
            for (int i = 1; i < SLEEP_CHOICES.length; i++) {   // the smallest choice that ends no earlier than what's left
                if (SLEEP_CHOICES[i] >= left) {
                    sleepIndex = i;
                    break;
                }
            }
        }
        retarget(false);
        motionDirty = true;   // the sheet layer shows the picker (updateMotion sets its visibility)
        sheetLayer.invalidate();
    }

    private void sleepMove(int dx, int dy) {
        int next = Math.max(0, Math.min(SLEEP_CHOICES.length - 1, sleepIndex + (dy != 0 ? dy : dx)));
        if (next == sleepIndex) return;
        sleepIndex = next;
        sheetLayer.invalidate();
        click.move();
    }

    private void closeSleepPicker() {
        sleepOpen = false;
        retarget(false);
        motionDirty = true;
        sheetLayer.invalidate();
        click.move();
    }

    private void pickSleep() {
        sleepOpen = false;
        int m = SLEEP_CHOICES[sleepIndex];
        SleepTimer.set(getContext().getApplicationContext(), m);
        updateSleep();
        retarget(false);
        motionDirty = true;
        sheetLayer.invalidate();
        click.open();
        showToast(m == 0 ? Ui.tr("Sleep timer off") : Ui.trf("Sleep timer: {time}", "time", SleepTimer.duration(m)));
    }

    private void retargetGrid(boolean snap) {
        for (int i = 0; i < gridFocus.length; i++) {
            gridFocus[i].target = sheetOpen && i == gridIndex ? 1 : 0;
            if (snap) gridFocus[i].snap(gridFocus[i].target);
        }
        int rows = Math.max(1, (others.size() + COLUMNS - 1) / COLUMNS);
        int visible = Math.min(rows, VISIBLE_ROWS);
        int r = gridIndex / COLUMNS;
        float target = scroll.target;
        if (r < target) target = r;
        else if (r > target + visible - 1) target = r - visible + 1;
        scroll.target = Math.max(0, Math.min(rows - visible, target));
        if (snap) scroll.snap(scroll.target);
        spoken.focusMoved();
    }

    /** The All Apps sheet: a dimmed page, the title and the groups' tiles and labels in a grid. */
    private void drawSheetLayer(Canvas c) {
        if (sleepOpen) {
            drawSleepPicker(c);
            return;
        }
        c.drawColor(0xA8000000);
        float shift = scroll.value * GRID_ROW * k;
        c.drawText(Ui.tr("All Apps"), w / 2f, 124 * k - shift, sheetTitle);
        int n = others.size();
        if (gridLabels == null || gridLabels.length != n) {
            gridLabels = new String[n];
            for (int i = 0; i < n; i++) {
                gridLabels[i] = TextUtils.ellipsize(others.get(i).label, gridLabel, (GRID_W - 10) * k, TextUtils.TruncateAt.END).toString();
            }
        }
        float gridWidth = COLUMNS * GRID_W + (COLUMNS - 1) * GRID_GAP;
        float left = (1920 - gridWidth) / 2 * k;
        float gridTop = GRID_TOP * k;
        for (int i = 0; i < n; i++) {
            int r = i / COLUMNS, col = i % COLUMNS;
            float y = gridTop + r * GRID_ROW * k - shift;
            if (y > h || y + GRID_ROW * k < 0) continue;
            float x = left + col * (GRID_W + GRID_GAP) * k;
            gridCell.set(x, y, x + GRID_W * k, y + GRID_H * k);
            float f = gridFocus[i].value;
            drawCard(c, others.get(i), gridCell, GRID_RADIUS * k, f, 0.08f);
            if (sheetOpen && i == gridIndex) focusedCell.set(cell);
            gridLabel.setColor(blend(0x99FFFFFF, 0xFFFFFFFF, Ui.clamp(f)));
            c.drawText(gridLabels[i], gridCell.centerX(), gridCell.bottom + (40 + 6 * f) * k, gridLabel);
        }
    }

    /**
     * The sleep picker: a dimmed page with the "Sleep Timer" title and one chip per choice — Off, then the minutes.
     * The running timer is preselected as the smallest choice that ends no earlier than what's left.
     */
    private void drawSleepPicker(Canvas c) {
        c.drawColor(0xD9000000);
        c.drawText(Ui.tr("Sleep Timer"), w / 2f, 124 * k, sheetTitle);
        // Six chips per row, every row centered by its own width (the labels differ: "Off", "15 min", "2 h").
        float left = 120 * k, right = w - 120 * k, gap = 24 * k, ch = 96 * k, rowGap = 28 * k;
        drawerLabel.setTextSize(26 * k);
        float cap = Ui.cap(drawerLabel);
        String[] labels = new String[SLEEP_CHOICES.length];
        float[] widths = new float[SLEEP_CHOICES.length];
        for (int i = 0; i < SLEEP_CHOICES.length; i++) {
            labels[i] = SLEEP_CHOICES[i] == 0 ? Ui.tr("Off") : SleepTimer.duration(SLEEP_CHOICES[i]);
            widths[i] = Math.max(140 * k, drawerLabel.measureText(labels[i]) + 64 * k);
        }
        float top = h * 0.42f - ch;
        for (int i = 0; i < SLEEP_CHOICES.length; i++) {
            int row = i / 6, col = i % 6;
            float rowWidth = 0;
            for (int j = row * 6; j < SLEEP_CHOICES.length && j < row * 6 + 6; j++) rowWidth += widths[j] + (j > row * 6 ? gap : 0);
            float x = left + (right - left - rowWidth) / 2;
            for (int j = row * 6; j < i; j++) x += widths[j] + gap;
            float y = top + row * (ch + rowGap);
            cell.set(x, y, x + widths[i], y + ch);
            boolean focused = i == sleepIndex;
            if (focused) {
                fill.setShader(null);
                fill.setColor(tone(0xFFFFFFFF, 0xFFFFFFFF));
                c.drawRoundRect(cell, 20 * k, 20 * k, fill);
            } else {
                // Solid, not glass: the dock's tiles must not show through the picker's chips.
                fill.setShader(null);
                fill.setColor(tone(0x3A3A3C, 0x1C1C1E));
                c.drawRoundRect(cell, 20 * k, 20 * k, fill);
                rim.setShader(null);
                rim.setColor(tone(0x14000000, 0x1FFFFFFF));
                rim.setStrokeWidth(1.5f * k);
                c.drawRoundRect(cell, 20 * k, 20 * k, rim);
            }
            drawerLabel.setColor(focused ? tone(INK, INK) : drawerInk(0.95f));
            c.drawText(labels[i], cell.centerX(), cell.centerY() + cap / 2, drawerLabel);
        }
        drawerLabel.setTextSize(23 * k);
    }

    /** 16:9 sheet card. Focused (the shelf's subtle focus): it grows, with a shadow, a soft sheen and a thin border. */
    private void drawCard(Canvas c, HomeApps.Item it, RectF base, float radius, float f, float grow) {
        float s = 1 + grow * f;
        float cx = base.centerX(), cy = base.centerY();
        cell.set(cx - base.width() * s / 2, cy - base.height() * s / 2, cx + base.width() * s / 2, cy + base.height() * s / 2);
        float r = radius * s;
        float fc = Ui.clamp(f);
        if (f > 0.01f) {
            tmp.set(cell);
            tmp.offset(0, 16 * k * f);
            shadow.setColor(Ui.alpha(0xFF000000, 0.55f * fc));
            shadows.draw(c, tmp, r, SHADOW_BLUR * k, shadow);
        }
        if (it.tile != null) roundImage(c, it.tile, it.tileShader, cell, r);
        if (f > 0.01f) {
            float bh = base.height();
            if (cardSheen == null || cardSheenH != bh) {
                cardSheen = new LinearGradient(0, -bh / 2, 0, 0, 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
                cardSheenH = bh;
            }
            c.save();
            c.translate(cx, cy);
            c.scale(s, s);
            tmp.set(-base.width() / 2, -bh / 2, base.width() / 2, bh / 2);
            sheen.setShader(cardSheen);
            sheen.setAlpha(Math.round(255 * fc));
            c.drawRoundRect(tmp, radius, radius, sheen);
            sheen.setAlpha(255);
            c.restore();
        }
        if (fc < 0.99f) {
            veil.setColor(Ui.alpha(0xFF000000, 0.08f * (1 - fc)));
            c.drawRoundRect(cell, r, r, veil);
        }
        border.setColor(0x14FFFFFF);
        c.drawRoundRect(cell, r, r, border);
        if (f > 0.01f) {
            rim.setShader(null);
            rim.setColor(Ui.alpha(0xFFFFFFFF, 0.4f * fc));
            rim.setStrokeWidth(1.5f * k);
            c.drawRoundRect(cell, r, r, rim);
            rim.setStrokeWidth(2.5f * k);
        }
    }

    // MARK: - Toasts

    void showToast(String text) {
        toasts.add(text);
        announceForAccessibility(text);
        wake();
    }

    /** Advances the toast queue; returns true while a toast fades in or out (animation). */
    private boolean tickToast(long up) {
        if (toast != null && up - toastAt > TOAST_MS) {
            toast = null;
            toastLayer.invalidate();
        }
        if (toast == null && toastPaint != null) {
            String next = toasts.poll();
            if (next != null) {
                toast = layout(next);
                toastAt = up;
            }
        }
        if (toast == null) return false;
        long age = up - toastAt;
        boolean fading = age < 350 || age > TOAST_MS - 450;
        if (fading) toastLayer.invalidate();
        return fading;
    }

    private void drawToast(Canvas c) {
        if (toast == null) return;
        float age = (SystemClock.uptimeMillis() - toastAt) / 1000f;
        float a = Math.min(Ui.clamp(age / 0.3f), Ui.clamp((TOAST_MS / 1000f - age) / 0.4f));
        if (a <= 0) return;
        float padX = 28 * k, padY = 18 * k, icon = 26 * k, iconW = Symbols.width(getContext(), "bell", icon);
        float tw = toast.getWidth() + iconW + 16 * k, th = toast.getHeight();
        float toastTop = 648 * k - (1 - a) * 16 * k;
        tmp.set((w - tw) / 2f - padX, toastTop, (w + tw) / 2f + padX, toastTop + th + 2 * padY);
        int save = c.saveLayerAlpha(tmp.left - 4, tmp.top - 4, tmp.right + 4, tmp.bottom + 4, Math.round(255 * a));
        scene.glass(c, tmp, Math.min(tmp.height() / 2, 32 * k), SMOKE, 0.55f, 0, 0, 0.02f, 1);
        Symbols.draw(getContext(), c, "bell", tmp.left + padX + iconW / 2, tmp.centerY(), icon, 0xEBFFFFFF);
        c.translate(tmp.left + padX + iconW + 16 * k, tmp.top + padY);
        toast.draw(c);
        c.restoreToCount(save);
    }

    private StaticLayout layout(String text) {
        int max = Math.round(720 * k);
        int width = (int) Math.min(max, Math.ceil(Layout.getDesiredWidth(text, toastPaint)));
        return StaticLayout.Builder.obtain(text, 0, text.length(), toastPaint, Math.max(1, width))
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    /** New notices from the main process (mode switches, the check after the TV boots): those from the last 24 h. */
    private void checkNotices() {
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            JSONArray list = HomeMode.state(c).optJSONArray("notices");
            if (list == null) return;
            long seen = ui.getLong("seenNotice", 0), newest = seen;
            long cutoff = System.currentTimeMillis() - 24 * 3600_000L;
            final List<String> fresh = new ArrayList<>();
            for (int i = list.length() - 1; i >= 0; i--) {   // the list comes newest first
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                long at = o.optLong("at");
                if (at <= seen || at < cutoff) continue;
                fresh.add(o.optString("text"));
                newest = Math.max(newest, at);
            }
            if (newest > seen) ui.edit().putLong("seenNotice", newest).apply();
            if (!fresh.isEmpty()) handler.post(() -> { for (String t : fresh) showToast(t); });
        });
    }

    // MARK: - Lists (in the background, so returning to the home screen isn't delayed)

    private void reload() {
        final Context c = getContext().getApplicationContext();
        // The shelf and All Apps (100 to 500 ms of package queries on a TV) only when they can have changed.
        long up = SystemClock.uptimeMillis();
        final boolean apps = appsStale || Prefs.version != appsVersion || Ui.generation != appsGeneration
                || up - appsAt > APPS_MAX_AGE_MS;
        if (apps) {
            appsStale = false;
            appsVersion = Prefs.version;
            appsGeneration = Ui.generation;
            appsAt = up;
        }
        LOADER.execute(() -> {
            final List<HomeApps.Item> s = apps ? HomeApps.shelf(c, true) : null;
            final List<HomeApps.Item> d = drawerList(c);
            final List<HomeApps.Item> o = apps ? HomeApps.others(c) : null;
            handler.post(() -> {
                if (s != null) setShelf(s);
                setDrawerItems(d);
                if (o != null && !sheetOpen) setOthers(o);
                else if (o != null) appsStale = true;   // All Apps is open: its list comes next time
            });
        });
    }

    /** Apps installed, removed or updated (also while another app is open): the next reload reads them again. */
    private final LauncherApps.Callback packages = new LauncherApps.Callback() {
        @Override public void onPackageAdded(String pkg, UserHandle user) { appsChanged(); }
        @Override public void onPackageRemoved(String pkg, UserHandle user) { appsChanged(); }
        @Override public void onPackageChanged(String pkg, UserHandle user) { appsChanged(); }
        @Override public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) { appsChanged(); }
        @Override public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) { appsChanged(); }
    };

    private void appsChanged() {
        appsStale = true;
        if (running) reloadSoon();
    }

    // MARK: - Keys: only the arrows, OK, Back and Settings (system keys pass through to the system)

    public boolean onKey(KeyEvent e) {
        wake();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (reordering) return reorderKey(e, down);
        int code = e.getKeyCode();
        if (down && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                && code != KeyEvent.KEYCODE_NUMPAD_ENTER) {
            holdArmed = false;      // another key while OK is held: no move mode
            handler.removeCallbacks(holdStart);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Opens on release, and only if the press happened here: the release of an OK pressed inside another
                // app opens nothing.
                // Held (~0.5 s) on a shelf app: move mode; on an app in All Apps: on or off the shelf. A timer, since
                // not every remote repeats a held key, or the long-press repeat, whichever comes first; the release
                // after a hold opens nothing.
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        okDown = true;
                        okHeld = false;
                        handler.removeCallbacks(holdStart);
                        holdArmed = sheetOpen ? canToggleShelf(gridIndex) : canReorder() || canManageCard();
                        if (holdArmed) handler.postDelayed(holdStart, HOLD_MS);
                    } else if (okDown && holdArmed && e.isLongPress()) {
                        hold();
                    }
                } else {
                    holdArmed = false;
                    handler.removeCallbacks(holdStart);
                    if (okDown && !okHeld && !e.isCanceled() && spoken.keyClick()) activate();
                    okDown = okHeld = false;
                }
                return true;
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_NOTIFICATION:
                // The remote's Settings button (TCL's sends NOTIFICATION) opens TV Settings, as Hisense's gear does by
                // default; ours stay on the gear on screen. Hisense's gear is a Remote Buttons choice (RemoteKeys); where
                // the system takes the key first, it never gets here.
                if (RemoteKeys.HISENSE) return false;
                if (down && e.getRepeatCount() == 0) HomeApps.open(activity, HomeApps.tvSettings(), this, w / 2, h / 2, 1, 1);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) {
                    if (e.getRepeatCount() == 0) backDown = true;
                } else {
                    if (backDown && !e.isCanceled()) back();
                    backDown = false;
                }
                return true;
            default:
                return false;
        }
    }

    private void back() {
        if (sleepOpen) {
            closeSleepPicker();
        } else if (sheetOpen) {
            closeSheet();
        } else if (zone != Z_SHELF || !sectionShown("apps")) {
            chooseDefaultFocus();
            motionDirty = true;
            retarget(false);
        } else {
            return;
        }
        click.move();
    }

    private void move(int dx, int dy) {
        if (sleepOpen) {
            sleepMove(dx, dy);
            return;
        }
        if (sheetOpen) {
            moveGrid(dx, dy);
            return;
        }
        float x = focusX();
        boolean moved = false;
        if (dx != 0) {
            if (zone == Z_SHELF && !shelf.isEmpty()) {
                int next = Math.max(0, Math.min(shelf.size() - 1, shelfIndex + dx));
                moved = next != shelfIndex;
                shelfIndex = next;
            } else if (zone == Z_DRAWER && !drawerItems.isEmpty()) {
                int next = Math.max(0, Math.min(drawerItems.size() - 1, drawerIndex + dx));
                moved = next != drawerIndex;
                drawerIndex = next;
            } else if (isRowZone(zone)) {
                int row = zone - Z_ROW0;
                int next = Math.max(0, Math.min(rowItems[row].size() - 1, rowCol[row] + dx));
                moved = next != rowCol[row];
                rowCol[row] = next;
                if (moved) {
                    ensureRowVisible(row, next);
                    ensurePageScroll(row);
                }
            } else if (zone == Z_RESUME && !resumeItems.isEmpty()) {
                int next = Math.max(0, Math.min(resumeItems.size() - 1, resumeCol + dx));
                moved = next != resumeCol;
                resumeCol = next;
                if (moved) {
                    ensureRowVisibleResume(next);
                    ensureSectionVisible("continue");
                }
            }
        } else if (dy != 0) {
            moved = moveVertical(dy, x);
        }
        if (!moved) return;
        retarget(false);
        click.move();
    }

    private int findPopulatedRow(int from, int direction) {
        for (int row = from + direction; row >= 0 && row < rowItems.length; row += direction) {
            if (sectionShown(rowSection(row)) && !rowItems[row].isEmpty()) return row;
        }
        return -1;
    }

    private static int nearest(RectF[] rects, float x) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < rects.length; i++) {
            float d = Math.abs(rects[i].centerX() - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private void moveGrid(int dx, int dy) {
        int n = others.size();
        if (n == 0) return;
        int r = gridIndex / COLUMNS, col = gridIndex % COLUMNS;
        int rows = (n + COLUMNS - 1) / COLUMNS;
        int next = gridIndex;
        if (dx != 0) {
            int candidate = gridIndex + dx;
            if (candidate >= 0 && candidate < n && candidate / COLUMNS == r) next = candidate;
        } else {
            int targetRow = r + dy;
            if (targetRow >= 0 && targetRow < rows) next = Math.min(n - 1, targetRow * COLUMNS + col);
        }
        if (next == gridIndex) return;
        gridIndex = next;
        retargetGrid(false);
        click.move();
        gridDirty = true;
    }

    private void activate() {
        wake();
        if (reordering) {   // TalkBack's click while moving an app: drop it
            dropReorder();
            return;
        }
        HomeApps.Item it;
        RectF r;
        float sourceY = 0;
        Resume.Entry resumeEntry = null;
        if (sleepOpen) {
            pickSleep();
            return;
        }
        if (sheetOpen) {
            if (others.isEmpty()) return;
            it = others.get(gridIndex);
            r = focusedCell;
        } else if (zone == Z_DRAWER) {
            if (drawerItems.isEmpty()) return;
            it = drawerItems.get(drawerIndex);
            r = drawerRects[drawerIndex];
            sourceY = drawerLayer.getTranslationY();
        } else if (isRowZone(zone)) {
            int row = zone - Z_ROW0;
            Recs.Pick p = rowCol[row] < rowItems[row].size() ? rowItems[row].get(rowCol[row]) : null;
            if (p == null || p.pkg == null) {
                showToast(Ui.tr("This title is not ready to open yet."));
                return;
            }
            it = HomeApps.app(p.pkg, Recs.serviceName(p), p.link);
            r = rowRects[row][rowCol[row]];
            sourceY = rowLayers[row].getTranslationY();
        } else if (zone == Z_RESUME) {
            if (resumeCol >= resumeItems.size()) return;
            resumeEntry = resumeItems.get(resumeCol);
            it = HomeApps.app(resumeEntry.pkg, resumeEntry.app.isEmpty() ? resumeEntry.pkg : resumeEntry.app,
                    Resume.uri(resumeEntry));
            it.resume = true;
            r = resumeRects[resumeCol];
            sourceY = resumeLayer.getTranslationY();
        } else {
            if (shelf.isEmpty()) return;
            it = shelf.get(shelfIndex);
            r = tileRects[shelfIndex];
            sourceY = tiles.getTranslationY();
        }
        click.open();
        Runnable resume = resumeEntry == null ? null : resumeWatch.prepareResume(resumeEntry);
        if (it.kind == HomeApps.ALL_APPS) {
            openSheet();
            return;
        }
        if (it.kind == HomeApps.SLEEP) {
            openSleepPicker();
            return;
        }
        RectF launchFrom = new RectF(r);
        launchFrom.offset(0, sourceY);
        boolean opened = HomeApps.open(activity, it, this, Math.round(launchFrom.left), Math.round(launchFrom.top),
                Math.round(launchFrom.width()), Math.round(launchFrom.height()));
        if (opened && resume != null) handler.postDelayed(resume, 700);
        if (!opened) showToast(Ui.trf("Couldn't open {app}.", "app", it.label));
    }

    // MARK: - Holding OK: All Apps puts the app on the shelf or takes it off; on the shelf, the app is lifted to move

    private void hold() {
        if (canManageCard()) openCardMenu(); else if (sheetOpen) toggleShelf(); else startReorder();
    }

    private boolean canManageCard() {
        return !sheetOpen && !sleepOpen && (zone == Z_RESUME && resumeCol < resumeItems.size()
                || isRowZone(zone) && rowCol[zone - Z_ROW0] < rowItems[zone - Z_ROW0].size());
    }

    private void openCardMenu() {
        if (!canManageCard() || cardMenu != null) return;
        okHeld = true;
        holdArmed = false;
        handler.removeCallbacks(holdStart);
        final Resume.Entry entry = zone == Z_RESUME ? resumeItems.get(resumeCol) : null;
        final Recs.Pick pick = entry == null ? rowItems[zone - Z_ROW0].get(rowCol[zone - Z_ROW0]) : null;
        String action = entry != null ? Ui.tr("Remove from Continue Watching") : Ui.tr("Hide from recommendations");
        cardMenu = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(entry != null ? Resume.title(entry) : pick.title)
                .setItems(new String[] {action}, (dialog, which) -> {
                    if (entry != null) {
                        Resume.remove(getContext(), entry);
                        reloadResume();
                    } else {
                        recs.hide(pick);
                        for (int row = 0; row < rowItems.length; row++) {
                            List<Recs.Pick> remaining = new ArrayList<>(rowItems[row]);
                            remaining.removeIf(item -> Recs.key(item).equals(Recs.key(pick)));
                            updateRow(row, remaining);
                        }
                        updatePicks();
                    }
                    retarget(true);
                    wake();
                    spoken.readAgain();
                })
                .setNegativeButton(Ui.tr("Cancel"), null)
                .create();
        // The release belonging to the hold must not activate the first menu action.
        cardMenu.setOnKeyListener((dialog, code, event) -> {
            if (okHeld && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_UP) okDown = okHeld = false;
                return true;
            }
            return false;
        });
        cardMenu.setOnDismissListener(dialog -> {
            cardMenu = null;
            okDown = okHeld = holdArmed = false;
            spoken.readAgain();
        });
        cardMenu.show();
        cardMenu.getWindow().setLayout(Math.round(860 * k), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardMenu.getListView().setSelection(0);
        cardMenu.getListView().requestFocus();
    }

    /** TalkBack's long click on an app in All Apps (once per press, if the remote's hold also arrives). */
    private void a11yHold() {
        if (SystemClock.uptimeMillis() - toggledAt < 1000) return;
        if (okDown) okHeld = true;     // the remote's release must not open the app
        toggleShelf();
    }

    private boolean canToggleShelf(int i) {
        if (!sheetOpen || i >= others.size()) return false;
        HomeApps.Item it = others.get(i);
        return it.kind == HomeApps.APP && it.pkg != null;
    }

    /** Puts the focused app at the end of the shelf, or takes it off if it's there; a toast says which. */
    private void toggleShelf() {
        handler.removeCallbacks(holdStart);
        holdArmed = false;
        if (!canToggleShelf(gridIndex)) return;
        okHeld = true;
        toggledAt = SystemClock.uptimeMillis();
        final HomeApps.Item it = others.get(gridIndex);
        final Context c = getContext().getApplicationContext();
        click.open();
        LOADER.execute(() -> {
            // On the shelf means shown there: installed and within the limit, as in Settings > Shelf.
            List<String> list = new ArrayList<>(HomeApps.shelfPackages(c));
            PackageManager pm = c.getPackageManager();
            int max = HomeApps.maxShelf(c), shown = 0;
            boolean on = false;
            for (String p : list) {
                if (shown >= max) break;
                if (HomeApps.launchIntent(pm, p) == null) continue;
                shown++;
                on |= p.equals(it.pkg);
            }
            final String text;
            list.remove(it.pkg);
            if (on) {
                text = Ui.trf("Removed from the shelf: {app}", "app", it.label);
            } else {
                if (shown >= max) {
                    handler.post(() -> showToast(Ui.tr("The shelf is full. Make room in Free My TV › Shelf.")));
                    return;
                }
                list.add(it.pkg);
                text = Ui.trf("Added to the shelf: {app}", "app", it.label);
            }
            Prefs.setShelf(list);   // here, so the next hold reads the new list
            handler.post(() -> {
                shelfEdited = true;
                showToast(text);
                reload();
            });
        });
    }

    // MARK: - Moving apps on the shelf

    private boolean canReorder() {
        if (sheetOpen || zone != Z_SHELF || shelfIndex >= shelf.size() || shelf.size() < 2) return false;
        HomeApps.Item it = shelf.get(shelfIndex);
        return it.kind == HomeApps.APP && it.pkg != null;
    }

    private void startReorder() {
        handler.removeCallbacks(holdStart);
        holdArmed = false;
        if (reordering || !canReorder()) return;
        wake();
        reordering = reorderAnim = okHeld = true;
        reorderOrigin = new ArrayList<>(shelf);
        liftIndex = shelfIndex;
        reorderAt = SystemClock.uptimeMillis();
        moveLift.target = 1;
        click.open();
        showToast(Ui.tr("← → to move · OK to drop · Back to cancel"));
        retarget(false);    // TalkBack reads "Moving <app>, position i of n"
    }

    /** Keys while moving an app: nothing else on the home screen gets them (system keys still go to the system). */
    private boolean reorderKey(KeyEvent e, boolean down) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) shiftReorder(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // The press that lifted the app is still held: its repeats and release do nothing; a new press drops it.
                if (down && e.getRepeatCount() == 0) {
                    okDown = okHeld = false;
                    dropReorder();
                } else if (!down) {
                    okDown = okHeld = false;
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) {
                    if (e.getRepeatCount() == 0) backDown = true;
                } else {
                    if (backDown && !e.isCanceled()) {
                        cancelReorder(false);
                        click.move();
                    }
                    backDown = false;
                }
                return true;
            default:
                return false;
        }
    }

    /** Swaps the lifted app with its neighbor; both slide from where they are to their new slots. */
    private void shiftReorder(int dx) {
        int i = shelfIndex, j = i + dx;
        if (j < 0 || j >= shelf.size()) return;
        HomeApps.Item it = shelf.get(i);
        shelf.set(i, shelf.get(j));
        shelf.set(j, it);
        Spring f = tileFocus[i];
        tileFocus[i] = tileFocus[j];
        tileFocus[j] = f;
        Spring s = tileSlide[i];
        tileSlide[i] = tileSlide[j];
        tileSlide[j] = s;
        float d = tileRects[i].left - tileRects[j].left;
        tileSlide[j].value += d;
        tileSlide[i].value -= d;
        if (tileShaders.length == shelf.size()) {
            LinearGradient g = tileShaders[i];
            tileShaders[i] = tileShaders[j];
            tileShaders[j] = g;
        }
        shelfIndex = liftIndex = j;
        reorderAnim = true;
        wake();
        retarget(false);
        click.move();
    }

    /** OK: drops the app where it is and saves the shelf's new order. */
    private void dropReorder() {
        if (!reordering) return;
        reordering = false;
        moveLift.target = 0;
        boolean changed = false;
        for (int i = 0; i < shelf.size() && !changed; i++) changed = shelf.get(i) != reorderOrigin.get(i);
        reorderOrigin = null;
        click.open();
        retarget(false);
        if (!changed) return;
        final List<String> order = new ArrayList<>();
        for (HomeApps.Item it : shelf) order.add(it.pkg);
        final Context c = getContext().getApplicationContext();
        LOADER.execute(() -> {
            // The saved list may hold more than the shelf shows (apps not installed, past the limit): the shown ones
            // take their new order in the same slots, the others stay where they were.
            List<String> all = HomeApps.shelfPackages(c), out = new ArrayList<>(all);
            int next = 0;
            for (int s = 0; s < out.size() && next < order.size(); s++) {
                if (order.contains(out.get(s))) out.set(s, order.get(next++));
            }
            if (next < order.size()) {
                out = new ArrayList<>(order);
                for (String p : all) if (!order.contains(p)) out.add(p);
            }
            final List<String> save = out;
            handler.post(() -> Prefs.setShelf(save));
        });
    }

    /** Back, or leaving the home screen: the original order comes back (sliding, unless `snap`). */
    private void cancelReorder(boolean snap) {
        if (!reordering && !reorderAnim) return;
        if (reordering) {
            reordering = false;
            moveLift.target = 0;
            int n = shelf.size();
            if (reorderOrigin != null && reorderOrigin.size() == n) {
                HomeApps.Item lifted = shelf.get(shelfIndex);
                Spring[] focus = new Spring[n], slide = new Spring[n];
                LinearGradient[] shaders = tileShaders.length == n ? new LinearGradient[n] : tileShaders;
                for (int a = 0; a < n; a++) {
                    int b = shelf.indexOf(reorderOrigin.get(a));
                    focus[a] = tileFocus[b];
                    slide[a] = tileSlide[b];
                    slide[a].value += tileRects[b].left - tileRects[a].left;
                    if (shaders != tileShaders) shaders[a] = tileShaders[b];
                }
                shelf = reorderOrigin;
                tileFocus = focus;
                tileSlide = slide;
                tileShaders = shaders;
                shelfIndex = liftIndex = shelf.indexOf(lifted);
            }
            reorderOrigin = null;
            retarget(snap);
        }
        if (snap) {
            moveLift.snap(0);
            for (Spring s : tileSlide) s.snap(0);
            wiggle = 0;
            liftIndex = -1;
            reorderAnim = false;
            tilesDirty = true;
        } else {
            wake();
        }
    }

    /** One frame of the move: the slides, the lift and the wiggle. Returns true while any of them moves. */
    private boolean stepReorder(float dt, long up) {
        boolean moving = step(tileSlide, dt) | moveLift.step(dt);
        if (reordering || moveLift.value > 0.001f) {
            wiggle = WIGGLE_DEG * (float) Math.sin((up - reorderAt) * (2 * Math.PI / WIGGLE_MS));
            moving = true;
        } else {
            wiggle = 0;
        }
        reorderAnim = moving;
        return moving;
    }

    // MARK: - TalkBack: every reachable item as a virtual node (Spoken). Id = group * 1000 + index + 1.

    /** The All Apps sheet's virtual accessibility group. */
    private static final int GRID = 9;

    private static int a11yId(int group, int index) {
        return group * 1000 + index + 1;
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        return spoken;
    }

    private void a11yItems(List<Integer> out) {
        if (sleepOpen) return;   // the sleep picker owns the screen; TalkBack reads the home again when it closes
        if (sheetOpen) {
            for (int i = 0; i < others.size(); i++) out.add(a11yId(GRID, i));
            return;
        }
        for (String key : sectionOrder) {
            if (!sectionShown(key)) continue;
            if (key.equals("apps")) {
                for (int i = shelfFirst; i < Math.min(shelf.size(), shelfFirst + shelfVisible); i++) out.add(a11yId(Z_SHELF, i));
            } else if (key.equals("actions")) {
                for (int i = 0; i < drawerItems.size(); i++) out.add(a11yId(Z_DRAWER, i));
            } else if (key.equals("continue")) {
                for (int i = 0; i < resumeItems.size(); i++) out.add(a11yId(Z_RESUME, i));
            } else {
                int group = zoneForSection(key);
                if (!isRowZone(group)) continue;
                int row = group - Z_ROW0;
                for (int i = 0; i < rowItems[row].size(); i++) out.add(a11yId(group, i));
            }
        }
    }

    private String a11yText(int id) {
        int group = (id - 1) / 1000, i = (id - 1) % 1000;
        if (group == GRID) {
            if (i >= others.size()) return "";
            return label(others, i) + ", " + Ui.trf("{i} of {n}", "i", i + 1, "n", others.size());
        }
        if (isRowZone(group)) {
            int row = group - Z_ROW0;
            return i < rowItems[row].size() ? pickText(rowItems[row].get(i)) : "";
        }
        if (group == Z_RESUME) {
            if (i >= resumeItems.size()) return "";
            Resume.Entry e = resumeItems.get(i);
            StringBuilder s = new StringBuilder(Resume.title(e));
            if (!e.sub.isEmpty()) s.append(", ").append(e.sub);
            if (!e.app.isEmpty()) s.append(". App: ").append(e.app);
            String left = Resume.left(e);
            if (!left.isEmpty()) s.append(", ").append(left);
            return s.toString();
        }
        switch (group) {
            case Z_SHELF:
                if (reordering && i == shelfIndex) {
                    return Ui.trf("Moving {app}, position {n} of {total}",
                            "app", label(shelf, i), "n", i + 1, "total", shelf.size());
                }
                return label(shelf, i);
            case Z_DRAWER: return label(drawerItems, i);
            default: return "";
        }
    }

    /**
     * Where the item is now. Items off screen (the sheet's hidden rows) get a thin strip at that edge, so TalkBack's
     * arrows can still reach them; going there brings them in.
     */
    private boolean a11yBounds(int id, Rect out) {
        int group = (id - 1) / 1000, i = (id - 1) % 1000;
        float dy;
        if (group != GRID && !sectionShown(sectionForZone(group))) return false;
        if (group == GRID) {
            if (!sheetOpen || i >= others.size()) return false;
            float gridWidth = COLUMNS * GRID_W + (COLUMNS - 1) * GRID_GAP;
            float x = ((1920 - gridWidth) / 2 + (i % COLUMNS) * (GRID_W + GRID_GAP)) * k;
            float y = (GRID_TOP + (i / COLUMNS - scroll.target) * GRID_ROW) * k;
            a11yRect.set(x, y, x + GRID_W * k, y + GRID_H * k);
            dy = 0;
        } else if (isRowZone(group)) {
            int row = group - Z_ROW0;
            if (i >= rowRects[row].length) return false;
            a11yRect.set(rowRects[row][i]);
            dy = rowLayers[row].getTranslationY();
        } else if (group == Z_RESUME) {
            if (i >= resumeRects.length) return false;
            a11yRect.set(resumeRects[i]);
            dy = resumeLayer.getTranslationY();
        } else if (group == Z_SHELF) {
            if (i >= tileRects.length) return false;
            a11yRect.set(tileRects[i]);
            dy = tiles.getTranslationY();
        } else if (group == Z_DRAWER) {
            if (i >= drawerRects.length) return false;
            a11yRect.set(drawerRects[i]);
            dy = drawerLayer.getTranslationY();
        } else {
            return false;
        }
        a11yRect.offset(0, dy);
        float edge = 24 * k;
        if (a11yRect.top >= h - edge) a11yRect.set(a11yRect.left, h - edge, a11yRect.right, h);
        else if (a11yRect.bottom <= edge) a11yRect.set(a11yRect.left, 0, a11yRect.right, edge);
        if (a11yRect.left >= w - edge) a11yRect.set(w - edge, a11yRect.top, w, a11yRect.bottom);
        else if (a11yRect.right <= edge) a11yRect.set(0, a11yRect.top, edge, a11yRect.bottom);
        a11yRect.intersect(0, 0, w, h);
        a11yRect.round(out);
        return out.width() > 0 && out.height() > 0;
    }

    private int a11yFocused() {
        if (sheetOpen) return others.isEmpty() ? -1 : a11yId(GRID, gridIndex);
        if (isRowZone(zone)) {
            int row = zone - Z_ROW0;
            return rowCol[row] < rowItems[row].size() ? a11yId(zone, rowCol[row]) : -1;
        }
        if (zone == Z_RESUME) return resumeCol < resumeItems.size() ? a11yId(Z_RESUME, resumeCol) : -1;
        switch (zone) {
            case Z_SHELF: return shelf.isEmpty() ? -1 : a11yId(Z_SHELF, shelfIndex);
            case Z_DRAWER: return drawerItems.isEmpty() ? -1 : a11yId(Z_DRAWER, drawerIndex);
            default: return -1;
        }
    }

    /** A spoken-feedback service (TalkBack) is on. */
    private boolean talkBack() {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled()
                && !am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isEmpty();
    }

    /** TalkBack moved to an item: the remote's focus goes there. */
    private void a11yFocus(int id) {
        wake();
        int group = (id - 1) / 1000, i = (id - 1) % 1000;
        if (group == GRID) {
            if (!sheetOpen || i >= others.size()) return;
            gridIndex = i;
            retargetGrid(false);
            gridDirty = true;
            click.move();
            return;
        }
        if (sheetOpen || reordering || !sectionShown(sectionForZone(group))) return;
        if (isRowZone(group)) {
            int row = group - Z_ROW0;
            if (i >= rowItems[row].size()) return;
            rowCol[row] = i;
            ensureRowVisible(row, i);
            ensurePageScroll(row);
        } else if (group == Z_RESUME) {
            if (i >= resumeItems.size()) return;
            resumeCol = i;
            ensureRowVisibleResume(i);
            ensureSectionVisible("continue");
        } else if (group == Z_DRAWER) {
            if (i >= drawerItems.size()) return;
            drawerIndex = i;
            ensureSectionVisible("actions");
        } else if (group == Z_SHELF) {
            if (i >= shelf.size()) return;
            shelfIndex = i;
            ensureSectionVisible("apps");
        } else {
            return;
        }
        zone = group;
        retarget(false);
        click.move();
    }

    private static String label(List<HomeApps.Item> list, int i) {
        return i >= 0 && i < list.size() && list.get(i).label != null ? list.get(i).label : "";
    }

    /** A recommendation: title, details and the app that plays it. */
    private static String pickText(Recs.Pick p) {
        if (p == null) return "";
        StringBuilder s = new StringBuilder(p.title == null ? "" : p.title);
        if (p.meta != null && !p.meta.isEmpty()) s.append(", ").append(p.meta);
        String app = Recs.serviceName(p);
        if (app != null && !app.isEmpty()) s.append(". App: ").append(app);
        return s.toString();
    }

    /** A recommendation shelf with its own horizontal focus and a stable row title. */
    private void drawRow(Canvas c, int row) {
        applyTone();
        float rowTop = rowTopUnits(row) * k;
        String title;
        switch (row) {
            case 1: title = Ui.tr("Anime"); break;
            case 2: title = Ui.tr("Movies"); break;
            case 3: title = Ui.tr("TV Shows"); break;
            default: title = Ui.tr("For You"); break;
        }
        c.drawText(title, MARGIN * k - Ui.leftBearing(rowLabel, title), rowTop + 28 * k, rowLabel);
        List<Recs.Pick> items = rowItems[row];
        if (items.isEmpty() || rowRects[row] == null) return;
        int focused = -1;
        for (int i = 0; i < items.size(); i++) {
            if (rowFocus[row][i].value > 0.02f && (focused < 0 || rowFocus[row][i].value > rowFocus[row][focused].value)) focused = i;
        }
        for (int i = 0; i < items.size(); i++) if (i != focused) drawRowCard(c, row, i);
        if (focused >= 0) drawRowCard(c, row, focused);
    }

    private void drawRowCard(Canvas c, int row, int index) {
        Recs.Pick pick = rowItems[row].get(index);
        RectF base = rowRects[row][index];
        float focus = Ui.clamp(rowFocus[row][index].value), scale = 1 + 0.045f * focus;
        float cx = base.centerX(), cy = base.centerY() - 5 * k * focus;
        float bw = base.width(), bh = base.height(), radius = 18 * k;
        cell.set(cx - bw * scale / 2, cy - bh * scale / 2, cx + bw * scale / 2, cy + bh * scale / 2);
        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        tmp.set(-bw / 2, -bh / 2, bw / 2, bh / 2);
        tmp.offset(0, (8 + 10 * focus) * k);
        shadowDeep.setColor(Ui.alpha(0xFF000000, 0.34f + 0.18f * focus));
        shadows.draw(c, tmp, radius, DEEP_BLUR * k, shadowDeep);
        c.restore();

        Backdrop backdrop = recs.backdrop(pick);
        Bitmap image = backdrop == null ? null : backdrop.wide;
        if (image != null) {
            roundImage(c, image, backdrop.wideShader, cell, radius * scale);
            if (rowScrimPaints[row] != null) c.drawRoundRect(cell, radius * scale, radius * scale, rowScrimPaints[row]);
        } else {
            fill.setShader(null);
            fill.setColor(tone(0xFF3A3A3C, 0xFF252527));
            c.drawRoundRect(cell, radius * scale, radius * scale, fill);
        }
        if (focus > 0.01f) {
            rim.setShader(null);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(3 * k);
            rim.setColor(Ui.alpha(WHITE, 0.88f * focus));
            c.drawRoundRect(cell, radius * scale, radius * scale, rim);
        }
        if (pick.title == null) return;
        rowTitle.setTextSize(22 * k);
        fitPaint.set(rowTitle);
        String caption = TextUtils.ellipsize(pick.title, fitPaint, bw, TextUtils.TruncateAt.END).toString();
        c.drawText(caption, base.left, base.bottom + 34 * k, rowTitle);
        if (pick.meta != null && !pick.meta.isEmpty()) {
            rowMeta.setTextSize(18 * k);
            fitPaint.set(rowMeta);
            String meta = TextUtils.ellipsize(pick.meta, fitPaint, bw, TextUtils.TruncateAt.END).toString();
            c.drawText(meta, base.left, base.bottom + 62 * k, rowMeta);
        }
    }

    // MARK: - Helpers

    /** Rounds a shape's edges to whole pixels (crisp at rest; a scale animation moves them again). */
    private static void snap(RectF r) {
        r.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = Ui.clamp((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    private static int blend(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}

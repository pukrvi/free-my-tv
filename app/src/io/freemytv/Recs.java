package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capped home recommendations from TMDB, Anime Kitsu and Streaming Catalogs. Regional subscription offers select
 * a service in dock order; unmatched titles open their exact entry in Stremio. TMDB and Watchmode keys are optional
 * and never go to the log. Home owns its stable visit/ten-minute selection; the saver keeps its hourly rotation.
 */
final class Recs {
    static final class Pick {
        int id;
        String source = "TMDB", externalId = "", externalType = "", imdb = "";
        String kind;            // "movie" or "tv"
        String title, backdrop, meta = "", app, pkg;
        String link;            // opens the title in its app (looked up with Watchmode), or null: the app's home
        boolean detailed;
        boolean anime;          // Kitsu provenance, not the broad Animation genre.
        java.util.Set<String> feeds = new java.util.LinkedHashSet<>(), genres = new java.util.HashSet<>(),
                countries = new java.util.HashSet<>(), keywords = new java.util.HashSet<>();
        java.util.Map<String, Double> ranks = new java.util.HashMap<>();
        String releaseDate = "", seasonDate = "", year = "";
        double rating;
        int votes, airingRank = Integer.MAX_VALUE;
        boolean oscarWinner, duneRelated;
        java.io.File photo;     // one of your own photos (Photos): no title, no caption
    }

    interface Listener {
        void onRecs();
        default void onArtwork(String key) { onRecs(); }
    }

    private static final String API = "https://api.themoviedb.org/3";
    /** The backdrops: on a 720p UI, TMDB's 1280-wide copy shows the same and is lighter to download and decode. */
    private static final String IMAGES = "https://image.tmdb.org/t/p/" + (Backdrop.W <= 1280 ? "w1280" : "original");
    /** TMDB's Animation genre (movies and shows: cartoons and anime), left out with hide_animation=true. */
    private static final int ANIMATION = 16;
    private static final long LIST_EVERY = 6 * 3600_000L;
    /** Bump when what is saved for each title changes (the saved list is discarded and fetched again). */
    private static final int CACHE_VERSION = 7;
    private static final int MAX_PICKS = 600;
    final RecommendationPlan plan = new RecommendationPlan();
    private final ExecutorService selection = Executors.newSingleThreadExecutor();
    volatile long revision;
    private static final String STREMIO = "com.stremio.one";
    private static final int MANHA = 0, TARDE = 1, NOITE = 2;
    /** Service name on TMDB (part of the name, lowercase) → TV app (HomeApps key, package). */
    private static final String[][] SERVICES = RecommendationRouting.SERVICES;
    private static final Map<String, String> SERVICE_NAME = new HashMap<>();
    static {
        SERVICE_NAME.put("stremio", "Stremio");
        SERVICE_NAME.put("jio", "JioCinema");
        SERVICE_NAME.put("netflix", "Netflix");
        SERVICE_NAME.put("prime", "Prime Video");
        SERVICE_NAME.put("disney", "Disney+");
        SERVICE_NAME.put("hulu", "Hulu");
        SERVICE_NAME.put("paramount", "Paramount+");
        SERVICE_NAME.put("peacock", "Peacock");
        SERVICE_NAME.put("globoplay", "Globoplay");
        SERVICE_NAME.put("max", "Max");
        SERVICE_NAME.put("appletv", "Apple TV");
        SERVICE_NAME.put("hotstar", "JioHotstar");
        SERVICE_NAME.put("okko", "Okko");
        SERVICE_NAME.put("crunchyroll", "Crunchyroll");
        SERVICE_NAME.put("now", "NOW");
        SERVICE_NAME.put("oneplay", "Oneplay");
        SERVICE_NAME.put("kinopoisk", "Кинопоиск");
        SERVICE_NAME.put("premier", "Premier");
        SERVICE_NAME.put("amediateka", "Амедиатека");
        SERVICE_NAME.put("wink", "Wink");
    }
    /** Country for "what streams where" (config.properties "tmdb_region", e.g. US, GB, BR). */
    static final String REGION = Place.region();
    /** The errors the settings say in words: TMDB lists none of the TV's apps in REGION, or the TV has none we know. */
    static final String NO_APPS = "none of the TV's streaming services are on TMDB";
    static final String NO_KNOWN_APPS = "none of the TV's streaming apps can give picks";

    private static Recs instance;

    static synchronized Recs get(Context c) {
        if (instance == null) instance = new Recs(c.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final AppAvailability appAvailability;
    private final SharedPreferences cache;
    private final java.util.concurrent.LinkedBlockingDeque<Runnable> ioQueue = new java.util.concurrent.LinkedBlockingDeque<>();
    private final java.util.concurrent.ThreadPoolExecutor io = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, ioQueue);
    private final ExecutorService catalogs = Executors.newFixedThreadPool(3);
    private final ExecutorService enrichment = Executors.newSingleThreadExecutor();
    private final StremioCatalogs addons = new StremioCatalogs();
    private volatile String sourceWarnings = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final ArtworkCache<Backdrop> images = new ArtworkCache<>();
    private final Set<String> visibleArtworkKeys = new HashSet<>();
    private final java.util.Set<String> pending = new java.util.HashSet<>();
    private final java.util.Set<Backdrop> pendingBackgrounds = new java.util.HashSet<>();
    private volatile List<Pick> picks = new ArrayList<>();
    private volatile int period = -1;
    private volatile long listAt;
    private volatile boolean fetching;
    private volatile String error = "";
    private volatile boolean light;     // light design: builds only the cards (all three tabs' cards fit in memory)
    private volatile boolean withTall = true;
    private volatile long failedAt, keyStamp, linkRetryAt;
    private volatile String listLanguage = "";   // language used for the saved titles, genres and texts

    private Recs(Context c) {
        context = c;
        appAvailability = new AppAvailability(pkg -> HomeApps.launchIntent(context.getPackageManager(), pkg) != null);
        cache = c.getSharedPreferences("recs", Context.MODE_PRIVATE);
        try(java.io.InputStream in=c.getAssets().open("recommendations/profile.json")){plan.configure(new JSONObject(read(in)));}catch(Exception ignored){}
        if (cache.getInt("version", 0) != CACHE_VERSION) cache.edit().clear().putInt("version", CACHE_VERSION).apply();
        try {
            String saved = cache.getString("picks", null);
            if (saved != null) picks = parse(new JSONArray(saved));
            period = cache.getInt("period", -1);
            listAt = cache.getLong("listAt", 0);
            listLanguage = cache.getString("language", cache.getBoolean("english", false) ? "en" : "pt");
            keyStamp = cache.getLong("keyStamp", 0);
        } catch (Exception ignored) {
        }
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    static boolean hasKey(Context c) {
        return !Config.TMDB_KEY.trim().isEmpty();
    }

    /** Last fetch, for the settings screen: {count, error, at}. Written by the home screen (another process). */
    static JSONObject status(Context c) {
        File f = new File(c.getFilesDir(), "recs-status.json");
        if (!f.exists()) return new JSONObject();
        try (FileInputStream in = new FileInputStream(f)) {
            return new JSONObject(read(in));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void writeStatus() {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), "recs-status.json"))) {
            out.write(new JSONObject().put("count", picks.size()).put("error", error).put("at", System.currentTimeMillis()).put("sources", sourceWarnings)
                    .toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    String error() { return error; }

    static String periodLabel() {
        int p = period(System.currentTimeMillis());
        return p == MANHA ? Ui.tr("THIS MORNING") : p == TARDE ? Ui.tr("THIS AFTERNOON") : Ui.tr("TONIGHT");
    }

    private static int period(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return hour >= 5 && hour < 12 ? MANHA : hour >= 12 && hour < 18 ? TARDE : NOITE;
    }

    /** This hour's picks (the first is the featured one; the arrows switch to the others). */
    List<Pick> now() {
        return now(null);
    }

    /** This hour's 3, movies only ("movie") or shows only ("tv"); null = both (the home screen tabs). */
    List<Pick> now(String kind) {
        List<Pick> list = new ArrayList<>();
        Map<String, Boolean> have = new HashMap<>();   // one look per app, not per title (this runs every 15 s)
        for (Pick p : picks) {
            if ((kind == null || kind.equals(p.kind))
                    && (!p.detailed || (p.pkg != null && have.computeIfAbsent(p.pkg, pkg -> STREMIO.equals(pkg) ? installed(pkg) : preferred(pkg))))) list.add(p);   // app missing: skip
        }
        if (list.isEmpty()) return list;
        long hour = System.currentTimeMillis() / 3600_000L;
        // Three per hour, never two from the same franchise ("Law & Order" and "Law & Order: Special…").
        List<Pick> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < list.size() && out.size() < 3; i++) {
            Pick p = list.get((int) ((hour * 3 + i) % list.size()));
            if (seen.add(franchise(p.title))) out.add(p);
        }
        return out;
    }

    /** "Law & Order: Special Victims Unit" → "law & order" (what comes before ":" or " - "). */
    private static String franchise(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        int cut = t.indexOf(':');
        if (cut < 0) cut = t.indexOf(" - ");
        return (cut > 0 ? t.substring(0, cut) : t).trim();
    }

    /**
     * A Netflix-style row: up to `max` picks, one per franchise, rotating by the supplied selection cycle. `kind` is
     * null (all), "movie", "tv" or "anime" (movies and shows). Picks whose image is already on disk come first. Same
     * eligibility as now(): an app on the TV, or a title whose details aren't known yet.
     */
    List<Pick> row(String kind, int max) {
        long hour = System.currentTimeMillis() / 3600_000L;
        return row(kind, max, hour * Math.max(1, max));
    }

    /** A stable caller-controlled rotation used by Home so refreshes happen on visits, not while focus is moving. */
    List<Pick> row(String kind, int max, long rotation) {
        List<Pick> out = new ArrayList<>();
        if (max <= 0) return out;
        List<Pick> ready = new ArrayList<>(), rest = new ArrayList<>();
        Map<String, Boolean> have = new HashMap<>();   // one look per app, not per title
        for (Pick p : picks) {
            boolean matches = "anime".equals(kind) ? p.anime : (kind == null || kind.equals(p.kind));
            if (!matches || hidden(p)) continue;
            if (p.detailed && !(p.pkg != null && have.computeIfAbsent(p.pkg, pkg -> STREMIO.equals(pkg) ? installed(pkg) : preferred(pkg)))) continue;
            (hasImage(p) ? ready : rest).add(p);
        }
        java.util.Set<String> seen = new java.util.HashSet<>();   // one per franchise
        java.util.Set<String> keys = new java.util.HashSet<>();   // never the same pick twice
        addRow(out, seen, keys, ready, rotation, max);
        addRow(out, seen, keys, rest, rotation, max);
        return out;
    }

    private boolean hidden(Pick p) {
        return context.getSharedPreferences("hidden-picks", Context.MODE_PRIVATE).getBoolean(key(p), false);
    }

    void hide(Pick p) {
        context.getSharedPreferences("hidden-picks", Context.MODE_PRIVATE).edit().putBoolean(key(p), true).apply();
    }

    /** Strict title matching avoids giving a watched episode artwork from an unrelated movie. */
    String resumeArtwork(String title) {
        if (!hasKey(context)) return "";
        String query = title.replaceFirst("(?i)\\s*[-–:]?\\s*S[0-9]+E[0-9]+.*$", "").trim();
        try {
            JSONArray results = get("/search/multi?query=" + URLEncoder.encode(query, "UTF-8")).optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject result = results.optJSONObject(i);
                if (result == null) continue;
                String kind = result.optString("media_type");
                if (!kind.equals("movie") && !kind.equals("tv")) continue;
                String name = result.optString("title", result.optString("name"));
                String original = result.optString("original_title", result.optString("original_name"));
                if (!query.equalsIgnoreCase(name) && !query.equalsIgnoreCase(original)) continue;
                String path = result.optString("backdrop_path");
                if (path.isEmpty() || path.equals("null")) path = result.optString("poster_path");
                if (!path.isEmpty() && !path.equals("null")) return "https://image.tmdb.org/t/p/w780" + path;
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static void addRow(List<Pick> out, java.util.Set<String> seen, java.util.Set<String> keys,
                               List<Pick> list, long rotation, int max) {
        if (list.isEmpty()) return;
        int start = (int) Math.floorMod(rotation, (long) list.size());
        for (int i = 0; i < list.size() && out.size() < max; i++) {
            Pick p = list.get((start + i) % list.size());
            if (keys.add(key(p)) && seen.add(franchise(p.title))) out.add(p);
        }
    }

    /** Name of the service the pick is on ("Netflix"), or empty. */
    static String serviceName(Pick p) {
        return p == null || p.app == null ? "" : SERVICE_NAME.getOrDefault(p.app, "");
    }

    /** The pick's ready backdrop (or null: builds it in the background and notifies when it's ready). */
    Backdrop backdrop(Pick p) {
        if (p == null || p.backdrop == null) return null;
        final String key = p.backdrop;    // the details may swap the image midway
        synchronized (images) {
            Backdrop b = images.get(key);
            if (b != null) return b;
            if (!pending.add(key)) return null;
        }
        io.execute(() -> {
            try {
                // Discard requests left behind by scrolling before doing expensive decode work.
                synchronized (images) {
                    if (light && !withTall && !images.isVisible(key)) return;
                }
                Pick photo = new Pick(); photo.backdrop = key; photo.title = p.title;
                loadImage(photo);
            } finally {
                synchronized (images) { pending.remove(key); }
            }
        });
        return null;
    }

    /** Protect the actual viewport, including the home background, while allowing old rows to be evicted. */
    void keepVisibleArtwork(List<Pick> visible) {
        synchronized (images) {
            visibleArtworkKeys.clear();
            for (Pick p : visible) if (p.backdrop != null) visibleArtworkKeys.add(p.backdrop);
            images.retainVisible(visibleArtworkKeys);
        }
    }

    /** Card-only artwork stays cheap until selected as the page background. Never tint images on the UI thread. */
    Backdrop background(Pick p) {
        final String key = p == null ? null : p.backdrop;
        Backdrop b = backdrop(p);
        if (b == null || b.backgroundReady()) return b;
        synchronized (images) {
            if (!pendingBackgrounds.add(b)) return null;
        }
        // Prioritize the one visible background over queued offscreen artwork, on the same worker.
        io.prestartCoreThread();
        ioQueue.addFirst(() -> {
            boolean ready = false;
            try { b.prepareBackground(); ready = true; }
            catch (RuntimeException | OutOfMemoryError e) { AppLog.w("background preparation unavailable"); }
            finally {
                synchronized (images) { pendingBackgrounds.remove(b); }
                if (ready) notifyArtwork(key);
            }
        });
        return null;
    }

    private boolean hasImage(Pick p) {
        return p.backdrop != null && imageFile(p).exists();
    }

    private File imageFile(Pick p) {
        // A fixed-size hash also handles full addon image URLs without path separators or overlong filenames.
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(p.backdrop.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) name.append(String.format(Locale.ROOT, "%02x", b & 255));
            return new File(new File(context.getCacheDir(), "recs"), name.toString());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Fetches the sources if stale or the time of day (morning/afternoon/night) changed; then the details. */
    void refreshIfStale() {
        if (fetching) return;
        long now = System.currentTimeMillis();
        long stamp = sourceStamp();   // a new key, country, animation choice or installed streaming-app set refetches
        boolean newKey = stamp != keyStamp;
        long sinceFail = now - failedAt;    // negative = the clock went back: don't wait
        if (!newKey && failedAt != 0 && sinceFail >= 0 && sinceFail < 10 * 60_000L) return;
        int p = period(now);
        boolean stale = newKey || now - listAt > 3600_000L || now < listAt || picks.isEmpty()
                || !listLanguage.equals(Ui.language);
        boolean missing = false;
        for (Pick pick : wanted()) missing |= !pick.detailed || !hasImage(pick) || needsLink(pick);
        if (!stale && !missing) return;
        keyStamp = stamp;
        fetching = true;
        enrichment.execute(() -> {
            try {
                if (stale) fetchList(p);
                // One failing title doesn't stop the others; whatever has no app on the TV is not downloaded.
                int failures = 0, tried = 0;
                String last = "";
                for (Pick pick : wanted()) {
                    tried++;
                    try {
                        if (!pick.detailed) fetchDetails(pick);
                        if (pick.pkg != null && !(light && !withTall)) download(pick);
                    } catch (Exception e) {
                        fallback(pick);
                        failures++;
                        last = e.getMessage() == null ? e.toString() : e.getMessage();
                    }
                    findLink(pick);
                }
                save();
                if (tried > 0 && failures == tried && picks.isEmpty()) throw new IOException(last);
                error = "";
                failedAt = 0;
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                failedAt = System.currentTimeMillis();
                AppLog.w("recommendations: " + error);
            } finally {
                fetching = false;
                writeStatus();
                notifyListeners();
            }
        });
    }

    /** Home screen design: the ready images are rebuilt for it (the light design uses cards, not the whole photo). */
    void setLight(boolean value) {
        boolean portraits = !value || !"vibe".equals(Prefs.homeDesign());
        synchronized (images) {
            if (value == light && portraits == withTall) return;
            light = value;
            withTall = portraits;
            images.clear();
            images.retainVisible(java.util.Collections.emptySet());
            images.limit(value ? 24 : 4);
        }
    }

    /**
     * Screensaver: the picks with an app on the TV and an image already on disk, starting with this hour's (the
     * featured one first), never two from the same franchise.
     */
    List<Pick> showcase() {
        List<Pick> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Pick p : wanted()) if (hasImage(p) && seen.add(franchise(p.title))) out.add(p);
        Map<String, Boolean> have = new HashMap<>();
        for (Pick p : picks) {
            if (p.detailed && p.pkg != null && have.computeIfAbsent(p.pkg, pkg -> STREMIO.equals(pkg) ? installed(pkg) : preferred(pkg)) && hasImage(p)
                    && seen.add(franchise(p.title))) out.add(p);
        }
        return out;
    }

    /** The pick's original image if it's already on disk (the screensaver loads it in its own way), or null. */
    File readyImage(Pick p) {
        return hasImage(p) ? imageFile(p) : null;
    }

    /**
     * While the screensaver is showing: details and image for every pick in the list (not just this hour's), one at a
     * time in the background, so it has more photos. Not used otherwise.
     */
    void prefetchAll() {
        if (fetching) return;
        long sinceFail = System.currentTimeMillis() - failedAt;
        if (failedAt != 0 && sinceFail >= 0 && sinceFail < 10 * 60_000L) return;
        List<Pick> todo = new ArrayList<>();
        for (Pick p : picks) if (!p.detailed || (p.pkg != null && !hasImage(p)) || needsLink(p)) todo.add(p);
        if (todo.isEmpty()) return;
        fetching = true;
        io.execute(() -> {
            int failures = 0;
            String last = "";
            try {
                // One failing title (gone from TMDB, an image that won't download) doesn't stop the others.
                for (Pick p : todo) {
                    try {
                        if (!p.detailed) fetchDetails(p);
                        if (p.pkg != null) download(p);
                        findLink(p);
                    } catch (Exception e) {
                        fallback(p);
                        failures++;
                        last = e.getMessage() == null ? e.toString() : e.getMessage();
                    }
                }
                save();
                if (failures == todo.size()) {
                    error = last;
                    failedAt = System.currentTimeMillis();
                    AppLog.w("recommendations (screensaver): " + last);
                }
            } finally {
                fetching = false;
                notifyListeners();
            }
        });
    }

    /**
     * While the home screen shows the rows: details and image for what row() would return, at most 40 titles, skipping
     * the ones already fetched. One at a time in the background; safe to call every refresh.
     */
    void prefetchRows(long rotation) {
        if (fetching) return;
        long sinceFail = System.currentTimeMillis() - failedAt;
        if (failedAt != 0 && sinceFail >= 0 && sinceFail < 10 * 60_000L) return;
        int cap = 40;
        List<Pick> todo = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String kind : new String[] {null, "movie", "tv", "anime"}) {
            for (Pick p : row(kind, cap, rotation)) {
                if (todo.size() >= cap) break;
                if (!seen.add(key(p))) continue;
                if (!p.detailed || (p.pkg != null && !hasImage(p)) || needsLink(p)) todo.add(p);
            }
        }
        if (todo.isEmpty()) return;
        fetching = true;
        io.execute(() -> {
            int failures = 0;
            String last = "";
            try {
                // One failing title (gone from TMDB, an image that won't download) doesn't stop the others.
                for (Pick p : todo) {
                    try {
                        if (!p.detailed) fetchDetails(p);
                        if (p.pkg != null) download(p);
                        findLink(p);
                    } catch (Exception e) {
                        fallback(p);
                        failures++;
                        last = e.getMessage() == null ? e.toString() : e.getMessage();
                    }
                }
                save();
                if (failures == todo.size()) {
                    error = last;
                    failedAt = System.currentTimeMillis();
                    AppLog.w("recommendations (rows): " + last);
                }
            } finally {
                fetching = false;
                notifyListeners();
            }
        });
    }

    /** This hour's picks for the three tabs (Explore, Movies, Shows), without repeats. */
    List<Pick> wanted() {
        List<Pick> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String kind : new String[] {null, "movie", "tv"}) {
            for (Pick p : now(kind)) if (seen.add(key(p))) out.add(p);
        }
        return out;
    }

    /** Movies and shows are numbered separately on TMDB: a pick is identified by type + number. */
    static String key(Pick p) {
        return "TMDB".equals(p.source) ? p.kind + ":" + p.id : p.externalType + ":" + p.externalId;
    }

    interface RowsReady { void ready(List<Pick>[] rows); }
    void rankRows(long cycle,RowsReady callback){
        final List<Pick> snapshot=new ArrayList<>(picks);
        selection.execute(()->{
            List<Pick> available=new ArrayList<>();for(Pick p:snapshot)if(!hidden(p))available.add(p);
            List<Pick>[] rows=plan.rank(available,cycle,java.time.LocalDate.now());
            main.post(()->callback.ready(rows));
        });
    }
    private boolean prefetchingVisible;
    private final Map<String,Long> detailRetries=new java.util.concurrent.ConcurrentHashMap<>();
    void prefetchVisible(List<Pick> wanted){
        if(prefetchingVisible)return;
        List<Pick> todo=new ArrayList<>();Set<String> seen=new HashSet<>();long now=System.currentTimeMillis();
        for(Pick p:wanted)if(seen.add(key(p))&&now>=detailRetries.getOrDefault(key(p),0L)&&(!p.detailed||needsLink(p))){todo.add(p);if(todo.size()>=12)break;}
        if(todo.isEmpty())return;prefetchingVisible=true;
        enrichment.execute(()->{
            for(Pick p:todo){
                try{if(!p.detailed)fetchDetails(p);findLink(p);
                    List<Pick> updated=new ArrayList<>(picks);for(int i=0;i<updated.size();i++)if(key(updated.get(i)).equals(key(p)))updated.set(i,p);picks=updated;
                }catch(Exception ignored){fallback(p);detailRetries.put(key(p),System.currentTimeMillis()+600_000);}
            }
            save();prefetchingVisible=false;notifyListeners();
        });
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onRecs(); });
    }

    private void notifyArtwork(String key) {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onArtwork(key); });
    }

    // MARK: - TMDB

    private void fetchList(int p) throws Exception {
        // Fetch independently: a failed TMDB account or addon must not remove the other sources.
        List<String> services = dockServices();
        if (services.isEmpty() && installed(STREMIO)) services = Arrays.asList("netflix", "prime", "disney");
        final List<String> selected = services;
        java.util.concurrent.Future<List<Pick>> tmdb = catalogs.submit(() -> hasKey(context) ? fetchTmdbList() : new ArrayList<>());
        java.util.concurrent.Future<List<Pick>> kitsu = catalogs.submit(() -> Config.HIDE_ANIMATION ? new ArrayList<>() : cachedAddon("kitsu",12,()->fromAddon(addons.anime())));
        java.util.concurrent.Future<List<Pick>> streaming = catalogs.submit(() -> cachedAddon("streaming",6,()->fromAddon(addons.streaming(selected))));
        long deadline = System.currentTimeMillis() + 45_000;
        List<List<Pick>> sources = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<java.util.concurrent.Future<List<Pick>>> tasks = Arrays.asList(tmdb, kitsu, streaming);
        String[] names = {"TMDB", "Anime Kitsu", "Streaming Catalogs"};
        for (int i = 0; i < tasks.size(); i++) {
            try { sources.add(tasks.get(i).get(Math.max(1, deadline - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS)); }
            catch (Exception e) {
                tasks.get(i).cancel(true);
                warnings.add(names[i]);
                List<Pick> previous = new ArrayList<>();
                // Keep a working source's last cached selection through a temporary outage.
                for (Pick old : picks) if (names[i].equals(old.source)) previous.add(old);
                sources.add(previous);
            }
        }
        sourceWarnings = String.join(", ", warnings);
        List<Pick> list = new ArrayList<>();
        for(List<Pick> source:sources)for(Pick pick:source)if(list.size()<MAX_PICKS)list.add(pick);
        if (list.isEmpty()) throw new IOException("No recommendations available");
        picks = list;
        revision++;
        period = p;
        listAt = System.currentTimeMillis();
        listLanguage = Ui.language;
    }

    static List<Pick> blend(List<List<Pick>> sources) {
        List<Pick> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; list.size() < MAX_PICKS; i++) {
            boolean added = false;
            for (List<Pick> source : sources) if (i < source.size() && list.size() < MAX_PICKS) {
                added = true;
                Pick pick = source.get(i);
                String imdbKey = pick.kind + ":" + pick.imdb, tmdbKey = pick.kind + ":" + pick.id;
                if ((!pick.imdb.isEmpty() && seen.contains(imdbKey)) || (pick.id > 0 && seen.contains(tmdbKey)) || seen.contains(key(pick))) continue;
                seen.add(key(pick));
                if (!pick.imdb.isEmpty()) seen.add(imdbKey);
                if (pick.id > 0) seen.add(tmdbKey);
                list.add(pick);
            }
            if (!added) break;
        }
        return list;
    }

    private List<Pick> cachedAddon(String id,int hours,java.util.concurrent.Callable<List<Pick>> fetch) throws Exception {
        String key="addon:"+id+":"+keyStamp+":"+Ui.language;long now=System.currentTimeMillis(),at=cache.getLong(key+":at",0);String saved=cache.getString(key,"");
        if(!saved.isEmpty()&&now>=at&&now-at<hours*3600_000L)return parse(new JSONArray(saved));
        try{List<Pick> list=fetch.call();JSONArray values=new JSONArray();for(Pick p:list)values.put(encode(p));cache.edit().putString(key,values.toString()).putLong(key+":at",now).apply();return list;}
        catch(Exception e){if(!saved.isEmpty())return parse(new JSONArray(saved));throw e;}
    }

    private List<Pick> fromAddon(List<StremioCatalogs.Entry> entries) {
        List<Pick> out = new ArrayList<>();
        for (StremioCatalogs.Entry e : entries) {
            if (Config.HIDE_ANIMATION && e.anime) continue;
            Pick p = new Pick();
            p.id = e.tmdb;
            p.externalId = e.id;
            p.externalType = e.type;
            p.imdb = e.imdb;
            p.source = e.source;
            p.kind = "movie".equals(e.type) ? "movie" : "tv";
            p.title = e.title;
            p.backdrop = e.image;
            p.anime = e.anime;
            p.meta = e.source;
            p.feeds=new LinkedHashSet<>(e.feeds);p.ranks=new HashMap<>(e.ranks);p.airingRank=e.airingRank;p.year=e.meta;
            if(e.metadata!=null){
                JSONObject metadata=e.metadata;p.genres=strings(metadata.optJSONArray("genres"));
                String released=metadata.optString("released");if(released.length()>=10)p.releaseDate=released.substring(0,10);
                String country=metadata.optString("country");Set<String> countries=new HashSet<>();
                if(country.contains("India"))countries.add("IN");if(country.contains("South Korea"))countries.add("KR");if(country.contains("Japan"))countries.add("JP");p.countries=countries;
            }
            fallback(p);
            // Regional offers replace the provisional Stremio route once metadata has been checked.

            out.add(p);
        }
        return out;
    }

    private List<Pick> fetchTmdbList() throws Exception {
        List<Pick> out=new ArrayList<>();
        String[][] feeds={
            {"tmdb-trending-movie","movie","/trending/movie/week?language="+lang(),"1"},
            {"tmdb-trending-tv","tv","/trending/tv/week?language="+lang(),"1"},
            {"new-movies","movie","/discover/movie?include_adult=false&sort_by=popularity.desc&primary_release_date.gte="+day(-365)+"&primary_release_date.lte="+day(0),"6"},
            {"new-shows","tv","/discover/tv?include_adult=false&sort_by=popularity.desc&first_air_date.gte="+day(-90)+"&first_air_date.lte="+day(0),"6"},
            {"new-seasons","tv","/discover/tv?include_adult=false&sort_by=popularity.desc&air_date.gte="+day(-90)+"&air_date.lte="+day(0),"6"},
            {"indian-movie","movie","/discover/movie?include_adult=false&sort_by=popularity.desc&with_origin_country=IN","24"},
            {"indian-tv","tv","/discover/tv?include_adult=false&sort_by=popularity.desc&with_origin_country=IN","24"},
            {"sci-fi-movie","movie","/discover/movie?include_adult=false&sort_by=popularity.desc&with_genres=878","24"},
            {"sci-fi-tv","tv","/discover/tv?include_adult=false&sort_by=popularity.desc&with_genres=10765","24"},
            {"korean-movie","movie","/discover/movie?include_adult=false&sort_by=popularity.desc&with_origin_country=KR","24"},
            {"korean-tv","tv","/discover/tv?include_adult=false&sort_by=popularity.desc&with_origin_country=KR","24"},
            {"japanese-movie","movie","/discover/movie?include_adult=false&sort_by=popularity.desc&with_origin_country=JP","24"},
            {"japanese-tv","tv","/discover/tv?include_adult=false&sort_by=popularity.desc&with_origin_country=JP","24"},
            {"must-watch-movies","movie","/movie/top_rated?language="+lang(),"168"},
            {"must-watch-tv","tv","/tv/top_rated?language="+lang(),"168"},
            {"dune-related","movie","/movie/438631/recommendations?language="+lang(),"168"}
        };
        for(String[] feed:feeds){
            String cacheKey="feed:"+feed[0]+":"+Ui.language;
            String saved=cache.getString(cacheKey,"");long at=cache.getLong(cacheKey+":at",0);
            try{
                if(!Thread.currentThread().isInterrupted()&&(saved.isEmpty()||System.currentTimeMillis()-at>Long.parseLong(feed[3])*3600_000L)){
                    saved=get(feed[2]).toString();cache.edit().putString(cacheKey,saved).putLong(cacheKey+":at",System.currentTimeMillis()).apply();
                }
                JSONArray results=saved.isEmpty()?null:new JSONObject(saved).optJSONArray("results");
                for(int i=0;results!=null&&i<results.length()&&i<20;i++){
                    List<Pick> one=new ArrayList<>();add(one,results.getJSONObject(i),feed[1]);
                    for(Pick pick:one){
                        pick.feeds.add(feed[0]);pick.ranks.put(feed[0],1-i/(double)Math.max(1,results.length()-1));
                        pick.duneRelated=feed[0].equals("dune-related");
                        if(feed[0].startsWith("indian"))pick.countries.add("IN");
                        if(feed[0].startsWith("korean"))pick.countries.add("KR");
                        if(feed[0].startsWith("japanese"))pick.countries.add("JP");
                        out.add(pick);
                    }
                }
            }catch(Exception ignored){}
        }
        // Award membership is verified locally; release metadata is resolved through TMDB.
        try(java.io.InputStream in=context.getAssets().open("recommendations/oscars.json")){
            JSONArray films=new JSONObject(read(in)).optJSONArray("films");
            for(int i=0;films!=null&&i<films.length();i++){
                JSONObject film=films.getJSONObject(i);int id=film.optInt("tmdbId");
                if(id<=0)continue;
                Pick p=new Pick();p.id=id;p.kind="movie";p.title=film.optString("filmTitle");p.oscarWinner=true;
                p.feeds.add("oscars");p.year=String.valueOf(film.optInt("releaseYear"));out.add(p);
            }
        }catch(Exception ignored){}
        if(out.isEmpty())throw new IOException("TMDB catalogs unavailable");
        return out;
    }

    /** Language of titles and genres (TMDB's "fr-FR", "zh-CN"…); the services come from the region. */
    private static String lang() {
        String lang = Ui.language;
        if (lang.equals("en")) return "en-US";
        if (lang.equals("pt")) return "pt-BR";
        if (lang.equals("es")) return "ES".equals(java.util.Locale.getDefault().getCountry()) ? "es-ES" : "es-MX";
        if (lang.equals("de")) return "de-DE";   // TMDB's de-AT and de-CH have few translations of their own
        if (lang.indexOf('-') > 0) return lang;
        return lang + "-" + Ui.locale().getCountry();
    }

    private static void alternate(List<Pick> out, java.util.Set<String> seen, List<Pick> a, List<Pick> b, int max) {
        for (int i = 0; i < Math.max(a.size(), b.size()) && out.size() < max; i++) {
            if (i < a.size() && seen.add(key(a.get(i)))) out.add(a.get(i));
            if (i < b.size() && out.size() < max && seen.add(key(b.get(i)))) out.add(b.get(i));
        }
    }

    /** The date `offset` days from today, as "2026-10-19" (negative = in the past). */
    private static String day(int offset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offset);
        return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private static void add(List<Pick> list, JSONObject o, String kind) {
        String backdrop = o.optString("backdrop_path", "");
        if (backdrop.isEmpty() || "null".equals(backdrop)) return;
        boolean anime = animated(o);
        if (Config.HIDE_ANIMATION && anime) return;
        Pick p = new Pick();
        p.id = o.optInt("id");
        p.kind = kind;
        p.title = o.optString(kind.equals("movie") ? "title" : "name");
        p.backdrop = backdrop;
        p.anime = false;
        applyMetadata(p,o);
        list.add(p);
    }

    private static void applyMetadata(Pick p,JSONObject o){
        String date=o.optString("movie".equals(p.kind)?"release_date":"first_air_date");
        if(date.length()>=10){p.releaseDate=date.substring(0,10);p.year=date.substring(0,4);}
        p.rating=o.optDouble("vote_average",p.rating);p.votes=o.optInt("vote_count",p.votes);
        Set<String> genres=new HashSet<>(p.genres);JSONArray a=o.optJSONArray("genres");
        for(int i=0;a!=null&&i<a.length();i++){JSONObject g=a.optJSONObject(i);if(g!=null)genres.add(g.optString("name"));}
        JSONArray ids=o.optJSONArray("genre_ids");
        for(int i=0;ids!=null&&i<ids.length();i++){int id=ids.optInt(i);if(id==878)genres.add("Science Fiction");if(id==14)genres.add("Fantasy");if(id==10765)genres.add("Sci-Fi & Fantasy");if(id==12)genres.add("Adventure");}
        p.genres=genres;Set<String> countries=new HashSet<>(p.countries);countries.addAll(strings(o.optJSONArray("origin_country")));
        a=o.optJSONArray("production_countries");for(int i=0;a!=null&&i<a.length();i++){JSONObject country=a.optJSONObject(i);if(country!=null)countries.add(country.optString("iso_3166_1"));}p.countries=countries;
        JSONObject kw=o.optJSONObject("keywords");a=kw==null?null:kw.optJSONArray("keywords");if(a==null&&kw!=null)a=kw.optJSONArray("results");
        Set<String> keywords=new HashSet<>(p.keywords);for(int i=0;a!=null&&i<a.length();i++){JSONObject k=a.optJSONObject(i);if(k!=null)keywords.add(k.optString("name"));}p.keywords=keywords;
        a=o.optJSONArray("seasons");String latest=p.seasonDate,today=java.time.LocalDate.now().toString();
        for(int i=0;a!=null&&i<a.length();i++){JSONObject season=a.optJSONObject(i);if(season==null||season.optInt("season_number")<=0)continue;String air=season.optString("air_date");if(air.length()==10&&air.compareTo(today)<=0&&air.compareTo(latest)>0)latest=air;}p.seasonDate=latest;
    }

    private static boolean animated(JSONObject o) {
        JSONArray genres = o.optJSONArray("genre_ids");
        for (int i = 0; genres != null && i < genres.length(); i++) if (genres.optInt(i) == ANIMATION) return true;
        return false;
    }

    /** Genre, runtime (or seasons) and the service it's on, among the ones the TV has. */
    private void fetchDetails(Pick p) throws Exception {
        if (p.id <= 0 && hasKey(context) && !p.imdb.isEmpty()) {
            JSONObject found = get("/find/" + p.imdb + "?external_source=imdb_id");
            JSONArray matches = found.optJSONArray("movie".equals(p.kind) ? "movie_results" : "tv_results");
            if (matches != null && matches.length() == 1) p.id = matches.getJSONObject(0).optInt("id");
        }
        if (!hasKey(context) || p.id <= 0) {
            fallback(p);
            p.meta = p.source + (p.app == null ? "" : " · " + serviceName(p));
            p.detailed = true;
            return;
        }
        JSONObject o = get("/" + p.kind + "/" + p.id
                + "?language=" + lang() + "&append_to_response=watch/providers,images,external_ids,keywords&include_image_language=null," + Ui.language.replaceAll("-.*", "") + ",en");
        applyMetadata(p,o);
        p.app = p.pkg = p.link = null;
        JSONObject external = o.optJSONObject("external_ids");
        if (external != null && external.optString("imdb_id").matches("tt[0-9]+")) p.imdb = external.optString("imdb_id");
        if (p.externalId.isEmpty() && !p.imdb.isEmpty()) {
            p.externalId = p.imdb;
            p.externalType = "movie".equals(p.kind) ? "movie" : "series";
        }
        p.title = title(p, o);
        String best = bestBackdrop(o.optJSONObject("images"));
        if (best != null) p.backdrop = best;
        if(p.backdrop==null||p.backdrop.isEmpty()){String image=o.optString("backdrop_path");if(image.isEmpty()||"null".equals(image))image=o.optString("poster_path");p.backdrop=image.isEmpty()||"null".equals(image)?null:image;}
        List<String> parts = new ArrayList<>();
        JSONArray genres = o.optJSONArray("genres");
        if (p.kind.equals("tv")) parts.add(Ui.tr("Series"));
        if (genres != null && genres.length() > 0) {
            String name = genres.getJSONObject(0).optString("name");
            parts.add(genre(name));
        }
        if (p.kind.equals("movie")) {
            int minutes = o.optInt("runtime");
            if (minutes > 0) parts.add(minutes >= 60 ? Ui.trf("{h}h {m}m", "h", minutes / 60, "m", minutes % 60) : Ui.trf("{m}m", "m", minutes));
        } else {
            int seasons = o.optInt("number_of_seasons");
            if (seasons > 0) parts.add(Ui.plural("plural|one:{n} season|other:{n} seasons", seasons));
        }
        JSONObject wp = o.optJSONObject("watch/providers");
        JSONObject br = wp == null ? null : wp.optJSONObject("results") == null ? null : wp.optJSONObject("results").optJSONObject(REGION);
        JSONArray flat = br == null ? null : br.optJSONArray("flatrate");
        String[] selected = RecommendationRouting.choose(flat, HomeApps.shelfPackages(context), this::installed);
        if (selected != null) {
            p.app = selected[1];
            p.pkg = selected[2];
        }
        fallback(p);
        if (!"TMDB".equals(p.source)) parts.add(p.source);
        if (p.app != null) parts.add(SERVICE_NAME.get(p.app));
        p.meta = String.join(" · ", parts);
        p.detailed = true;
    }

    /**
     * Without a title in the TV's language TMDB answers with the original one, so a Korean series on a German TV reads
     * "닥터X: 하얀 마피아의 시대". Then the English title, when TMDB has one (one more request, only for those).
     */
    private String title(Pick p, JSONObject o) {
        String field = p.kind.equals("movie") ? "title" : "name";
        String own = o.optString(field, p.title);
        String original = o.optString("original_language");
        String lang = Ui.language.replaceAll("-.*", "");   // TMDB's original_language has no country: "zh", not "zh-CN"
        if (lang.equals("en") || own.isEmpty() || !own.equals(o.optString("original_" + field))
                || lang.equals(original) || "en".equals(original)) return own;
        try {
            String english = get("/" + p.kind + "/" + p.id + "?language=en-US").optString(field);
            return english.isEmpty() ? own : english;
        } catch (Exception e) {
            return own;
        }
    }

    /** TV genres that TMDB returns in English even in other languages (pt-BR, es, de…). */
    private static String genre(String name) {
        switch (name) {
            case "Action & Adventure": return Ui.tr("genre|Action & Adventure");
            case "Sci-Fi & Fantasy": return Ui.tr("genre|Sci-Fi & Fantasy");
            case "War & Politics": return Ui.tr("genre|War & Politics");
            case "Kids": return Ui.tr("genre|Kids");
            case "News": return Ui.tr("genre|News");
            case "Soap": return Ui.tr("genre|Soap");
            case "Talk": return Ui.tr("genre|Talk");
            default: return name;
        }
    }

    /**
     * The title's best backdrop: no text written over it (our own text goes on top of it), the closest to 1920 wide
     * (larger only costs download, disk and decoding) and, on a tie, the highest rated.
     */
    private static String bestBackdrop(JSONObject images) {
        JSONArray list = images == null ? null : images.optJSONArray("backdrops");
        String best = null;
        double bestScore = -1;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject b = list.optJSONObject(i);
            if (b == null || b.optString("file_path", "").isEmpty()) continue;
            boolean textless = b.isNull("iso_639_1");
            int width = b.optInt("width");
            double score = (textless ? 1e7 : 0) + Math.min(width, 1920) * 100 - Math.max(0, width - 1920) + b.optDouble("vote_average", 0);
            if (score > bestScore) {
                bestScore = score;
                best = b.optString("file_path");
            }
        }
        return best;
    }

    /** The SERVICES row for a TMDB name: the installed one first, when a service has more than one package. */
    private String[] service(String name, int providerId) {
        String[] first = null;
        for (String[] s : SERVICES) {
            if (!RecommendationRouting.matches(s, name, providerId)) continue;
            if (preferred(s[2])) return s;
            if (first == null) first = s;
        }
        return first;
    }

    /** Services named in hide_services (config.properties), trimmed and lowercased: never a source of picks. */
    private static final Set<String> HIDDEN = new HashSet<>();
    static {
        for (String s : Config.HIDE_SERVICES.split(",")) {
            String key = s.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) HIDDEN.add(key);
        }
    }

    /** The service's app is on the TV, and the service isn't hidden (an app the TV ships without a subscription, say). */
    private boolean installed(String pkg) {
        if (!HIDDEN.isEmpty()) for (String[] s : SERVICES) if (s[2].equals(pkg) && HIDDEN.contains(s[1])) return false;

        if (STREMIO.equals(pkg) && HIDDEN.contains("stremio")) return false;
        return appAvailability.available(pkg);
    }

    private boolean preferred(String pkg) {
        return HomeApps.shelfPackages(context).contains(pkg) && installed(pkg);
    }

    private List<String> dockServices() {
        List<String> out = new ArrayList<>();
        for (String pkg : HomeApps.shelfPackages(context)) for (String[] service : SERVICES)
            if (pkg.equals(service[2]) && installed(pkg) && !out.contains(service[1])) out.add(service[1]);
        return out;
    }

    private void fallback(Pick p) {
        if (p.pkg != null || !installed(STREMIO)) return;
        String link = StremioCatalogs.detailLink(p.externalType, p.externalId);
        if (link == null) return;
        p.pkg = STREMIO;
        p.app = "stremio";
        p.link = link;
    }

    void appsChanged() { appAvailability.invalidate(); }


    private long sourceStamp() {
        long stamp = (Config.TMDB_KEY.hashCode() * 31L + REGION.hashCode()) * 31L + (Config.HIDE_ANIMATION ? 1 : 0);
        for (String[] service : SERVICES) stamp = stamp * 31L + (installed(service[2]) ? service[2].hashCode() : 0);
        stamp = stamp * 31L + (installed(STREMIO) ? 1 : 0);
        for (String pkg : HomeApps.shelfPackages(context)) stamp = stamp * 31L + pkg.hashCode();
        return stamp;
    }

    // MARK: - Watchmode (the title's own page in its app; the free plan's web addresses are what the apps open)

    private static String linkKey(Pick p) {
        return "wm2:" + p.kind + p.id + p.app;   // wm2: links found before the channel check are looked up again
    }

    private boolean needsLink(Pick p) {
        return p.id > 0 && !"stremio".equals(p.app) && linksOn() && p.pkg != null && p.link == null
                && cache.getString(linkKey(p), null) == null && System.currentTimeMillis() >= linkRetryAt;
    }

    /** A key is set and Watchmode hasn't refused it (a refused key isn't tried again until it changes). */
    private boolean linksOn() {
        String key = Config.WATCHMODE_KEY.trim();
        return !key.isEmpty() && cache.getInt("wm:refused", 0) != key.hashCode();
    }

    /** Sets p.link from what was looked up before, or looks it up (2 requests of the free plan's 2,500 a month). */
    private void findLink(Pick p) {
        if (p.id <= 0 || "stremio".equals(p.app) || !linksOn() || p.pkg == null || p.link != null) return;
        String saved = cache.getString(linkKey(p), null);
        if (saved != null) {
            p.link = saved.isEmpty() ? null : saved;
            return;
        }
        if (System.currentTimeMillis() < linkRetryAt) return;
        try {
            String link = "";
            JSONArray found = new JSONObject(watchmode("/search/?search_field=tmdb_" + p.kind + "_id&search_value=" + p.id))
                    .optJSONArray("title_results");
            if (found != null && found.length() > 0) {
                JSONArray sources = new JSONArray(watchmode("/title/" + found.getJSONObject(0).getInt("id") + "/sources/?regions=" + REGION));
                String[] service = null;
                for (String[] s : SERVICES) if (s[1].equals(p.app)) service = s;
                for (int i = 0; service != null && i < sources.length() && link.isEmpty(); i++) {
                    JSONObject s = sources.getJSONObject(i);
                    String name = s.optString("name").toLowerCase(Locale.ROOT).replace(" ", "");
                    String want = service[0].replace(" ", "");
                    // Not a channel sold inside another app ("AppleTV+ Amazon Channel", "Paramount+ (Via Amazon Prime)"):
                    // that link opens Amazon or Apple's app, not the one the pick is for.
                    boolean resold = name.contains("channel") || name.contains("(via");
                    boolean same = !resold && (want.equals("max") ? name.equals("max") : name.contains(want));
                    if (same && s.optString("type").equals("sub") && s.optString("web_url").startsWith("https://")) link = s.optString("web_url");
                }
            }
            cache.edit().putString(linkKey(p), link).apply();
            p.link = link.isEmpty() ? null : link;
        } catch (Exception e) {
            if ("Watchmode returned HTTP 401".equals(e.getMessage())) {
                cache.edit().putInt("wm:refused", Config.WATCHMODE_KEY.trim().hashCode()).apply();
            }
            linkRetryAt = System.currentTimeMillis() + 10 * 60_000L;   // quota or network: not again right away
            AppLog.w("watchmode: " + e.getMessage());
        }
    }

    private static String watchmode(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.watchmode.com/v1" + path + (path.contains("?") ? "&" : "?")
                + "apiKey=" + URLEncoder.encode(Config.WATCHMODE_KEY.trim(), "UTF-8")).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("Watchmode returned HTTP " + code);
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private JSONObject get(String path) throws IOException, org.json.JSONException {
        String key = readKey();
        if (key == null) throw new IOException("no TMDB key");
        boolean bearer = key.startsWith("eyJ");   // read access token (v4) or API key (v3)
        String url = API + path + (bearer ? "" : (path.contains("?") ? "&" : "?") + "api_key=" + URLEncoder.encode(key, "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + key);
        try {
            int code = c.getResponseCode();
            if (code == 401) throw new IOException("TMDB rejected the key");
            if (code != 200) throw new IOException("TMDB returned HTTP " + code);
            return new JSONObject(read(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    private String readKey() {
        String key = Config.TMDB_KEY.trim();
        return key.isEmpty() ? null : key;
    }

    // MARK: - Images (bounded on disk and in memory; addon URLs or TMDB image paths)

    private void download(Pick p) throws IOException {
        if(p.backdrop==null||p.backdrop.isEmpty())return;
        File f = imageFile(p);
        if (f.exists()) return;
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        File part = new File(f.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(StremioCatalogs.https(p.backdrop) ? p.backdrop : IMAGES + p.backdrop).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (!part.renameTo(f)) throw new IOException("couldn't save the image");
        prune(f.getParentFile());
    }

    /** Reads the photo (≥ the UI's width) and builds the backdrop (Backdrop: cards plus frosted and glass versions). */
    private void loadImage(Pick p) {
        synchronized (images) {
            if (images.containsKey(p.backdrop)) return;
        }
        File f = imageFile(p);
        try {
            download(p);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= Backdrop.W) opts.inSampleSize *= 2;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), opts);
            if (b == null) throw new IOException("unreadable image");
            AppLog.w("backdrop \u201c" + p.title + "\u201d: " + bounds.outWidth + "×" + bounds.outHeight + " → " + Backdrop.W + "×" + Backdrop.H);
            boolean forLight = light;
            boolean portraits = withTall;
            Backdrop backdrop = Backdrop.make(b, forLight, false, portraits);
            synchronized (images) {
                if (forLight != light || portraits != withTall) return;   // the design changed midway
                images.put(p.backdrop, backdrop);
                // Visible entries are pinned; stale queued completions cannot evict them.
            }
            notifyArtwork(p.backdrop);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            AppLog.w("recommendation image: " + e.getMessage());
        }
    }

    void trim() {
        // Explore's picks stay: the home screen opens on that tab, so its cards are never gray when you come back.
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (Pick p : now(null)) if (p.backdrop != null) keep.add(p.backdrop);
        synchronized (images) {
            images.keySet().retainAll(keep);
        }
    }

    private static void prune(File dir) {
        // A download cut off when the system stopped the app leaves its .part behind.
        long stale = System.currentTimeMillis() - 10 * 60_000L;
        File[] parts = dir.listFiles((d, name) -> name.endsWith(".part"));
        for (int i = 0; parts != null && i < parts.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            if (parts[i].lastModified() < stale) parts[i].delete();
        }
        File[] files = dir.listFiles((d, name) -> !name.endsWith(".part"));
        if (files == null || files.length <= 96) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - 96; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    // MARK: - List cache

    private void save() {
        try {
            JSONArray a = new JSONArray();
            for (Pick p : picks) {
                a.put(encode(p));
            }
            SharedPreferences.Editor e = cache.edit().putString("picks", a.toString()).putInt("period", period).putLong("listAt", listAt)
                    .putString("language", listLanguage)
                    .putLong("keyStamp", keyStamp).putInt("version", CACHE_VERSION);
            // Watchmode links of titles no longer in the list go (the refused-key mark stays).
            Set<String> keep = new HashSet<>();
            for (Pick p : picks) keep.add(linkKey(p));
            for (String k : cache.getAll().keySet()) if ((k.startsWith("wm:") || k.startsWith("wm2:")) && !k.equals("wm:refused") && !keep.contains(k)) e.remove(k);
            e.apply();
        } catch (Exception ignored) {
        }
    }

    static JSONObject encode(Pick p) throws org.json.JSONException {
        return new JSONObject().put("id", p.id).put("kind", p.kind).put("title", p.title).put("backdrop", p.backdrop).put("link", p.link == null ? "" : p.link)
                        .put("meta", p.meta).put("app", p.app == null ? "" : p.app).put("pkg", p.pkg == null ? "" : p.pkg)
                        .put("detailed", p.detailed).put("anime", p.anime)
                        .put("source", p.source).put("externalId", p.externalId).put("externalType", p.externalType).put("imdb", p.imdb)
                        .put("feeds",new JSONArray(p.feeds)).put("genres",new JSONArray(p.genres)).put("countries",new JSONArray(p.countries))
                        .put("keywords",new JSONArray(p.keywords)).put("ranks",new JSONObject(p.ranks)).put("releaseDate",p.releaseDate)
                        .put("seasonDate",p.seasonDate).put("year",p.year).put("rating",p.rating).put("votes",p.votes)
                        .put("airingRank",p.airingRank).put("oscarWinner",p.oscarWinner).put("duneRelated",p.duneRelated);
    }

    static List<Pick> parse(JSONArray a) {
        List<Pick> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Pick p = new Pick();
            p.id = o.optInt("id");
            p.source = o.optString("source", "TMDB");
            p.externalId = o.optString("externalId");
            p.externalType = o.optString("externalType");
            p.imdb = o.optString("imdb");
            p.feeds=strings(o.optJSONArray("feeds"));p.genres=strings(o.optJSONArray("genres"));p.countries=strings(o.optJSONArray("countries"));
            p.keywords=strings(o.optJSONArray("keywords"));JSONObject ranks=o.optJSONObject("ranks");
            if(ranks!=null)for(java.util.Iterator<String> keys=ranks.keys();keys.hasNext();){String key=keys.next();p.ranks.put(key,ranks.optDouble(key));}
            p.releaseDate=o.optString("releaseDate");p.seasonDate=o.optString("seasonDate");p.year=o.optString("year");
            p.rating=o.optDouble("rating",0);p.votes=o.optInt("votes");p.airingRank=o.optInt("airingRank",Integer.MAX_VALUE);
            p.oscarWinner=o.optBoolean("oscarWinner");p.duneRelated=o.optBoolean("duneRelated");
            p.kind = o.optString("kind");
            p.title = o.optString("title");
            p.backdrop = o.optString("backdrop");
            p.link = o.optString("link").isEmpty() ? null : o.optString("link");
            p.meta = o.optString("meta");
            p.app = o.optString("app").isEmpty() ? null : o.optString("app");
            p.pkg = o.optString("pkg").isEmpty() ? null : o.optString("pkg");
            p.detailed = o.optBoolean("detailed");
            p.anime = o.optBoolean("anime");
            out.add(p);
        }
        return out;
    }

    private static Set<String> strings(JSONArray a) {
        Set<String> out=new LinkedHashSet<>();for(int i=0;a!=null&&i<a.length();i++){String s=a.optString(i);if(!s.isEmpty()&&!"null".equals(s))out.add(s);}return out;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}

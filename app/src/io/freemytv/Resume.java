package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What was watched in other apps (YouTube, Netflix, Prime Video, Stremio…), for the home screen's Continue Watching row.
 * It comes from YouTube, Netflix, Prime Video and Stremio's media sessions — the ones NowPlaying reads for the widget —
 * while playback is active, including before an app discards its paused session. The notification listener lives in
 * the same process as Home so the private history has one writer and immediately consistent reads. An entry is the app, the title, where playback stopped, and, when the app gives
 * one, the content's id
 * (YouTube's video id, which opens the video itself, not just the app). Kept in its own settings file: recording on
 * every pause must not touch Prefs' version, which makes HomeActivity rebuild the whole screen when it changes.
 */
final class Resume {

    /** One thing to pick up: an app, its title, where it stopped (ms), and when it was last seen. */
    static final class Entry {
        final String pkg, app, title, sub, mediaId;
        final long pos, dur, at;

        Entry(String pkg, String app, String title, String sub, String mediaId, long pos, long dur, long at) {
            this.pkg = pkg;
            this.app = app;
            this.title = title;
            this.sub = sub == null ? "" : sub;
            this.mediaId = mediaId == null ? "" : mediaId;
            this.pos = pos;
            this.dur = dur;
            this.at = at;
        }
    }

    private static final long DAY = 24L * 3600 * 1000;
    private static final long KEEP = 10 * DAY;   // other services expire after ten days
    private static final int MAX = 12;   // the row scrolls, but twelve is already a library
    private static final Pattern YT_ID = Pattern.compile("[\\w-]{11}");

    /** Where the app opens straight on this title, when we know how: YouTube's vnd.youtube:<video id>. */
    static String uri(Entry e) {
        if (e.pkg.startsWith("com.google.android.youtube") && YT_ID.matcher(e.mediaId).matches()) {
            return "vnd.youtube:" + e.mediaId;
        }
        return "";   // Netflix, Prime Video, Stremio: open the app; their schemes change and lie
    }

    /** "45 min left" or "2 h left": how far the title has to go, from where it stopped. "" when the app says no length. */
    static String left(Entry e) {
        if (e.dur <= 0 || e.pos <= 0) return "";
        long min = Math.max(0, (e.dur - e.pos) / 60_000);
        if (min == 0) return "";   // played to the end: nothing left to say
        return min < 60 ? Ui.trf("{n} min left", "n", min) : Ui.trf("{n} h left", "n", Math.max(1, min / 60));
    }

    /** "12 min ago": when it was last seen (staleness, so the row says the same things the weather does). */
    static String ago(Entry e) {
        return HomeData.staleness(e.at, 60_000);   // "" in the first two minutes: nothing needs saying yet
    }

    /**
     * Records a supported app's title; true when the store changed (the row reloads). Paused sessions count too:
     * that's how most arrive when the person comes back from the app.
     */
    static synchronized boolean record(Context c, MediaController mc) {
        return record(c, mc.getPackageName(), mc.getMetadata(), mc.getPlaybackState());
    }

    /** Session parsing seam shared by the live listener and the on-device regression checks. */
    static synchronized boolean record(Context c, String pkg, MediaMetadata m, PlaybackState s) {
        if (pkg.equals(c.getPackageName()) || !supported(pkg)) return false;
        MediaDescription d = m == null ? null : m.getDescription();
        String title = trim(d == null ? null : d.getTitle());
        boolean appOnly = title.isEmpty() || generic(title, pkg);
        if (appOnly && !opaqueProvider(pkg)) return false;
        if (appOnly) title = "";   // explicit app-level resume, not a fabricated content title
        if (s == null || s.getState() == PlaybackState.STATE_NONE || s.getState() == PlaybackState.STATE_ERROR
                || s.getState() == PlaybackState.STATE_STOPPED) return false;
        long dur = m == null ? 0 : Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
        long pos = 0;
        boolean positionKnown = s != null && s.getPosition() != PlaybackState.PLAYBACK_POSITION_UNKNOWN && s.getPosition() >= 0;
        if (positionKnown) {
            pos = s.getPosition();
            long updated = s.getLastPositionUpdateTime();
            if (!appOnly && s.getState() == PlaybackState.STATE_PLAYING && updated > 0) {   // the position stands still while it plays
                long elapsed = SystemClock.elapsedRealtime() - updated;
                if (elapsed > 0) pos += (long) (elapsed * s.getPlaybackSpeed());
            }
        }
        String sub = appOnly ? "" : trim(d == null ? null : d.getSubtitle());
        String id = trim(d == null ? null : d.getMediaId());
        Entry identity = new Entry(pkg, "", title, sub, id, pos, dur, 0);
        long hiddenUntil = sp(c).getLong("hidden:" + key(identity), 0);
        if (hiddenUntil > System.currentTimeMillis() || hiddenUntil > 0 && s.getState() != PlaybackState.STATE_PLAYING) return false;
        if (sp(c).getBoolean("expired:" + key(identity), false) && s.getState() != PlaybackState.STATE_PLAYING) return false;
        if (!appOnly) captureArt(c, identity, m);
        List<Entry> stored = load(c);
        for (Entry existing : stored) {
            if (key(existing).equals(key(identity)) && System.currentTimeMillis() - existing.at > retention(pkg)
                    && (s == null || s.getState() != PlaybackState.STATE_PLAYING)) return false;
        }
        List<Entry> list = items(c);
        if (!appOnly) list.removeIf(entry -> entry.pkg.equals(pkg) && entry.title.isEmpty());
        Entry old = null;
        int oldIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (e.pkg.equals(pkg) && e.title.equals(title) && (sub.isEmpty() || e.sub.equals(sub))) {
                old = e;
                oldIndex = i;
                break;
            }
        }
        // Some TV players retain the episode title while publishing a short advertisement's duration.
        if (old != null && old.dur > 5 * 60_000 && dur > 0 && dur < 90_000) return false;
        // Providers sometimes omit fields on a paused session; keep the last useful resume point and identifiers.
        if (old != null) {
            if (sub.isEmpty()) sub = old.sub;
            if (id.isEmpty()) id = old.mediaId;
            if (dur <= 0) dur = old.dur;
            if (!positionKnown) pos = old.pos;
        }
        if (pos < 0) pos = 0;
        if (dur > 0 && pos > dur) pos = dur;
        String app = app(c, pkg);
        if (app.isEmpty() && old != null) app = old.app;
        // Finished title: remove its stale card instead of leaving a false resume point behind.
        if (dur > 0 && pos >= dur) {
            if (oldIndex < 0) return false;
            list.remove(oldIndex);
            save(c, list);
            return true;
        }
        // The title is close to its last stored point: a paused session must not refresh its age or reorder it.
        if (old != null && old.app.equals(app) && old.mediaId.equals(id) && old.dur == dur
                && Math.abs(old.pos - pos) < 30_000) {
            return false;
        }
        if (oldIndex >= 0) list.remove(oldIndex);
        list.add(0, new Entry(pkg, app, title, sub, id, pos, dur, System.currentTimeMillis()));
        while (list.size() > MAX) list.remove(list.size() - 1);
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            sp(c).edit().remove("expired:" + key(identity)).remove("hidden:" + key(identity)).apply();
        }
        save(c, list);
        return true;
    }

    /** What to pick up, newest first, two days for YouTube and ten for other services. */
    static synchronized List<Entry> items(Context c) {
        List<Entry> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Entry e : load(c)) {
            if (generic(e.title, e.pkg)) continue;
            if (now - e.at > retention(e.pkg)) {
                if (!sp(c).getBoolean("expired:" + key(e), false)) sp(c).edit().putBoolean("expired:" + key(e), true).apply();
                continue;
            }   // sorted, newest first: the rest are older
            out.add(e);
            if (out.size() == MAX) break;
        }
        return out;
    }

    static long retention(String pkg) {
        return pkg.contains("youtube") ? 2 * DAY : KEEP;
    }

    static String key(Entry e) {
        // Title/subtitle remain stable even when an app temporarily omits its media ID.
        return Integer.toHexString((e.pkg + "\n" + e.title).hashCode());
    }

    static synchronized void remove(Context c, Entry e) {
        List<Entry> list = items(c);
        list.removeIf(item -> key(item).equals(key(e)));
        // A still-published paused session must not immediately put the removed card back.
        sp(c).edit().putLong("hidden:" + key(e), System.currentTimeMillis() + retention(e.pkg)).apply();
        save(c, list);
    }

    static boolean opaqueProvider(String pkg) {
        return pkg.equals("com.netflix.ninja") || pkg.equals("com.amazon.amazonvideo.livingroom")
                || pkg.equals("com.amazon.firebat") || pkg.equals("com.amazon.avod");
    }

    static String title(Entry entry) {
        return entry.title.isEmpty() ? Ui.trf("Continue in {app}", "app", entry.app) : entry.title;
    }

    private static boolean generic(String title, String pkg) {
        String text = title.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        return text.equals("primevideo") || text.equals("netflix") || text.equals("youtube")
                || text.equals("hotstar") || text.equals("jiohotstar") || text.equals("stremio");
    }

    private static final ExecutorService ART = Executors.newSingleThreadExecutor();
    private static final Set<String> pendingArt = new HashSet<>();
    private static final java.util.Map<String, Long> artRetry = new java.util.HashMap<>();
    private static final LruCache<String, Bitmap> artwork = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };

    private static File artFile(Context c, Entry e) {
        return new File(new File(c.getCacheDir(), "resume-art"), key(e) + ".jpg");
    }

    /** Local cache only; decoding/downloading happens on ART, never while drawing a frame. */
    static Bitmap artwork(Context c, Entry e, Runnable changed) {
        if (e.title.isEmpty()) return null;
        Bitmap ready = artwork.get(key(e));
        if (ready != null) return ready;
        requestArt(c, e, null, "", changed);
        return null;
    }

    private static void captureArt(Context c, Entry e, MediaMetadata m) {
        if (m == null) return;
        Bitmap bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bitmap == null) bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bitmap == null) bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        String uri = m.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if (uri == null) uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        requestArt(c, e, bitmap, uri == null ? "" : uri, null);
    }

    private static void requestArt(Context c, Entry e, Bitmap source, String uri, Runnable changed) {
        String key = key(e);
        synchronized (pendingArt) {
            if (artwork.get(key) != null || pendingArt.contains(key)) return;
            if (source == null && uri.isEmpty() && System.currentTimeMillis() < artRetry.getOrDefault(key, 0L)) return;
            pendingArt.add(key);
        }
        ART.execute(() -> {
            Bitmap b = null;
            try {
                File f = artFile(c, e);
                if (f.exists()) b = BitmapFactory.decodeFile(f.getPath());
                File legacy = new File(f.getParentFile(), Integer.toHexString((e.pkg + "\n" + e.title + "\n" + e.sub).hashCode()) + ".jpg");
                if (b == null && legacy.exists()) b = BitmapFactory.decodeFile(legacy.getPath());
                if (b == null && source != null && !source.isRecycled()) b = source;
                if (b == null && !uri.isEmpty()) b = readArt(c, uri);
                if (b == null && e.pkg.contains("youtube") && YT_ID.matcher(e.mediaId).matches()) {
                    b = readArt(c, "https://i.ytimg.com/vi/" + e.mediaId + "/hqdefault.jpg");
                }
                if (b == null && !e.pkg.contains("youtube")) {
                    String fallback = Recs.get(c).resumeArtwork(e.title);
                    if (!fallback.isEmpty()) b = readArt(c, fallback);
                }
                if (b != null) {
                    float scale = Math.min(1f, 640f / Math.max(b.getWidth(), b.getHeight()));
                    if (scale < 1) b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * scale)),
                            Math.max(1, Math.round(b.getHeight() * scale)), true);
                    artwork.put(key, b);
                    if (!f.exists()) {
                        f.getParentFile().mkdirs();
                        try (FileOutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 88, out); }
                    }
                }
            } catch (Exception ignored) {
                // Session art may be an inaccessible app-private URI. Keep the app tile as the fallback.
            } finally {
                synchronized (pendingArt) {
                    pendingArt.remove(key);
                    artRetry.put(key, System.currentTimeMillis() + 5 * 60_000);
                }
                if (b != null && changed != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(changed);
            }
        });
    }

    private static Bitmap readArt(Context c, String address) throws Exception {
        Uri uri = Uri.parse(address);
        if ("content".equals(uri.getScheme())) {
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { return decodeArt(in); }
        }
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return null;
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        try {
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream()) { return decodeArt(in); }
        } finally { connection.disconnect(); }
    }

    private static Bitmap decodeArt(InputStream in) throws Exception {
        if (in == null) return null;
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (data.size() + count > 5 * 1024 * 1024) return null;
            data.write(buffer, 0, count);
        }
        byte[] bytes = data.toByteArray();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / opts.inSampleSize > 1280) opts.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }

    private static List<Entry> load(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("entries", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                out.add(new Entry(o.optString("pkg"), o.optString("app"), o.optString("title"), o.optString("sub"),
                        o.optString("id"), o.optLong("pos"), o.optLong("dur"), o.optLong("at")));
            }
        } catch (Exception e) {
            AppLog.w("resume: " + e.getMessage());
        }
        return out;
    }

    private static void save(Context c, List<Entry> list) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("pkg", e.pkg).put("app", e.app).put("title", e.title).put("pos", e.pos).put("at", e.at);
                if (!e.sub.isEmpty()) o.put("sub", e.sub);
                if (!e.mediaId.isEmpty()) o.put("id", e.mediaId);
                if (e.dur > 0) o.put("dur", e.dur);
                a.put(o);
            }
            sp(c).edit().putString("entries", a.toString()).apply();
            Set<String> keep = new HashSet<>();
            for (Entry entry : list) {
                keep.add(key(entry) + ".jpg");
                keep.add(Integer.toHexString((entry.pkg + "\n" + entry.title + "\n" + entry.sub).hashCode()) + ".jpg");
            }
            ART.execute(() -> {
                File[] files = new File(c.getCacheDir(), "resume-art").listFiles();
                if (files != null) for (File file : files) if (!keep.contains(file.getName())) file.delete();
            });
        } catch (Exception e) {
            AppLog.w("resume save: " + e.getMessage());
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("resume", Context.MODE_PRIVATE);
    }

    private static String app(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "";   // hidden from us (Android 11+): the app is still there when the card is picked
        }
    }

    private static String trim(CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }

    /** Only the four services requested for this row; the Now Playing widget itself still sees every session. */
    private static boolean supported(String pkg) {
        return pkg.equals("com.google.android.youtube.tv") || pkg.equals("com.google.android.youtube")
                || pkg.equals("com.amazon.firetv.youtube") || pkg.equals("com.netflix.ninja")
                || pkg.equals("com.amazon.amazonvideo.livingroom") || pkg.equals("com.amazon.firebat")
                || pkg.equals("in.startv.hotstar") || pkg.equals("com.hotstar")
                || pkg.equals("com.amazon.avod") || pkg.equals("com.stremio.one") || pkg.equals("com.stremio");
    }
}

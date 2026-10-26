package io.freemytv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

/** Device regression checks. Uses isolated preferences/cache, never the user's viewing history. */
public final class HistoryChecks extends Instrumentation {
    private boolean inspect;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        inspect = args != null && args.getString("inspect", "false").equals("true");
        start();
    }
    private int checks;
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context base = getTargetContext();
        if (inspect) {
            StringBuilder text = new StringBuilder();
            try {
                for (MediaController mc : base.getSystemService(MediaSessionManager.class).getActiveSessions(NowPlaying.component(base))) {
                    if (!mc.getPackageName().matches(".*(youtube|netflix|amazonvideo|hotstar).*$")) continue;
                    MediaMetadata m = mc.getMetadata();
                    PlaybackState state = mc.getPlaybackState();
                    text.append(mc.getPackageName()).append(" state=").append(state == null ? -1 : state.getState());
                    text.append(" position=").append(state == null ? -1 : state.getPosition());
                    text.append(" keys=").append(m == null ? "none" : new java.util.ArrayList<>(m.keySet())).append("\n");
                    Bundle extras = mc.getExtras();
                    text.append("session extra keys=").append(extras == null ? "none" : new java.util.ArrayList<>(extras.keySet())).append("\n");
                    if (m != null) {
                        for (String key : new String[] {MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                                MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                                MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION}) {
                            String value = m.getString(key);
                            text.append(key).append("=").append(value == null ? "none" : value.substring(0, Math.min(100, value.length()))).append("\n");
                        }
                        text.append("art bitmap=").append(m.getBitmap(MediaMetadata.METADATA_KEY_ART) != null)
                                .append(" album bitmap=").append(m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null)
                                .append(" display bitmap=").append(m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null).append("\n");
                        String id = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                        text.append("mediaId present=").append(id != null && !id.isEmpty());
                        text.append(" duration=").append(m.getLong(MediaMetadata.METADATA_KEY_DURATION)).append("\n");
                        for (String key : new String[] {MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, MediaMetadata.METADATA_KEY_MEDIA_URI}) {
                            String value = m.getString(key);
                            text.append(key).append(" host=").append(value == null ? "none" : android.net.Uri.parse(value).getHost()).append("\n");
                        }
                    }
                }
                result.putString("stream", text.toString());
                finish(-1, result);
            } catch (Exception e) { result.putString("stream", e.toString()); finish(0, result); }
            return;
        }
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences("history-check-" + name, mode);
            }
            @Override public File getCacheDir() { return new File(base.getCacheDir(), "history-check"); }
        };
        try {
            SharedPreferences store = isolated.getSharedPreferences("resume", 0);
            store.edit().clear().commit();
            String yt = "com.google.android.youtube.tv", st = "com.stremio.one";
            MediaMetadata metadata = metadata("History check", 600_000);
            check(Resume.record(isolated, yt, metadata, state(3, 60_000, 20_000)), "playing title must be recorded");
            Resume.Entry entry = Resume.items(isolated).get(0);
            long until = SystemClock.elapsedRealtime() + 3000;
            while (Resume.artwork(isolated, entry, null) == null && SystemClock.elapsedRealtime() < until) Thread.sleep(50);
            check(Resume.artwork(isolated, entry, null) != null, "session artwork must become available from the local cache");
            check(entry.pos >= 80_000 && entry.pos < 85_000, "playing position must include elapsed time");
            check(Resume.record(isolated, yt, metadata("History check", 0), state(2, -1, 0)) == false,
                    "omitted paused fields must preserve the saved point");
            check(Resume.items(isolated).get(0).dur == 600_000, "duration must survive omitted paused metadata");
            Resume.remove(isolated, entry);
            check(Resume.items(isolated).isEmpty(), "remove must clear the card");
            check(!Resume.record(isolated, yt, metadata, state(2, 80_000, 0)), "stale paused session must not resurrect removed card");
            store.edit().clear().commit();
            Resume.record(isolated, st, metadata, state(2, 100_000, 0));
            Resume.record(isolated, st, metadata("History check", 20_000), state(3, 10_000, 0));
            check(Resume.items(isolated).get(0).dur == 600_000, "an advertisement must not replace an episode's saved duration");
            Resume.record(isolated, st, metadata, state(2, 600_000, 0));
            check(Resume.items(isolated).isEmpty(), "finished content must be removed");
            check(Resume.record(isolated, "com.amazon.amazonvideo.livingroom", metadata("PrimeVideo", 0), state(3, 0, 0)),
                    "opaque provider should offer an honest app-level fallback");
            check(Resume.items(isolated).get(0).title.isEmpty(), "generic app title must not be presented as a movie title");
            check(Resume.record(isolated, "com.netflix.ninja", null, state(3, 42_000, 0)),
                    "Netflix's null metadata must produce an app-level fallback");
            check(Resume.record(isolated, "in.startv.hotstar", metadata, state(2, 100_000, 0)), "Hotstar must be supported");
            long now = System.currentTimeMillis(), day = 86_400_000L;
            JSONArray entries = new JSONArray();
            entries.put(json(yt, "expired YouTube", now - 3 * day));
            entries.put(json(st, "retained Stremio", now - 3 * day));
            entries.put(json(st, "expired Stremio", now - 11 * day));
            store.edit().putString("entries", entries.toString()).commit();
            check(Resume.items(isolated).size() == 1 && Resume.items(isolated).get(0).title.equals("retained Stremio"),
                    "expiry must be 2 days for YouTube, 10 for other services");
            check(!Resume.record(isolated, yt, metadata("expired YouTube", 600_000), state(2, 50_000, 0)),
                    "expired paused session must stay expired");
            check(Resume.record(isolated, yt, metadata("expired YouTube", 600_000), state(3, 70_000, 0)),
                    "actual playback must reintroduce an expired title");
            check(Resume.record(isolated, yt, metadata("expired YouTube", 600_000), state(2, 110_000, 0)),
                    "paused progress must update after an expired title is played again");
            java.lang.reflect.Constructor<Recs> constructor = Recs.class.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            Recs recommendations = constructor.newInstance(isolated);
            Recs.Pick pick = new Recs.Pick();
            pick.id = 123; pick.kind = "movie"; pick.title = "History check movie";
            java.lang.reflect.Field field = Recs.class.getDeclaredField("picks"); field.setAccessible(true);
            field.set(recommendations, java.util.Arrays.asList(pick));
            check(recommendations.row(null, 10).size() == 1, "test recommendation must initially be present");
            recommendations.hide(pick);
            check(recommendations.row(null, 10).isEmpty() && recommendations.row("movie", 10).isEmpty(),
                    "hidden recommendation must disappear from all matching shelves");
            result.putString("stream", "PASS: " + checks + " history checks (isolated data).\n");
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + e.toString() + "\n");
            finish(0, result);
        } finally {
            base.deleteSharedPreferences("history-check-resume");
            base.deleteSharedPreferences("history-check-recs");
            base.deleteSharedPreferences("history-check-hidden-picks");
        }
    }
    private static MediaMetadata metadata(String title, long duration) {
        return new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)).build();
    }
    private static PlaybackState state(int state, long position, long elapsed) {
        return new PlaybackState.Builder().setState(state, position, 1f, SystemClock.elapsedRealtime() - elapsed).build();
    }
    private static JSONObject json(String pkg, String title, long at) throws Exception {
        return new JSONObject().put("pkg", pkg).put("title", title).put("app", "Test").put("at", at)
                .put("dur", 600_000).put("pos", 50_000);
    }
}

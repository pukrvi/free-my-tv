package io.freemytv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Device regression checks. Uses isolated preferences/cache, never the user's viewing history. */
public final class HistoryChecks extends Instrumentation {
    private boolean inspect;
    private String sampleTitle = "";
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        inspect = args != null && args.getString("inspect", "false").equals("true");
        sampleTitle = args == null ? "" : args.getString("sample_title", "");
        start();
    }
    private int checks;
    private final List<String> failures = new ArrayList<>();
    private void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context base = getTargetContext();
        if (inspect) {
            StringBuilder text = new StringBuilder();
            if (!sampleTitle.isEmpty()) for (Resume.Entry saved : Resume.items(base)) {
                if (sampleTitle.equals(saved.title)) text.append("requested saved sample: point=").append(saved.pos)
                        .append(" duration=").append(saved.dur).append(" public title link=")
                        .append(!Resume.uri(saved).isEmpty()).append("\n");
            }
            try {
                for (MediaController mc : base.getSystemService(MediaSessionManager.class).getActiveSessions(NowPlaying.component(base))) {
                    if (!mc.getPackageName().matches(".*(youtube|netflix|amazonvideo|hotstar|stremio).*$")) continue;
                    MediaMetadata m = mc.getMetadata();
                    PlaybackState state = mc.getPlaybackState();
                    text.append(mc.getPackageName()).append(" state=").append(state == null ? -1 : state.getState());
                    text.append(" position=").append(state == null ? -1 : state.getPosition());
                    text.append(" actions=").append(state == null ? 0 : state.getActions());
                    text.append(" sessionActivity=").append(mc.getSessionActivity() != null);
                    text.append(" keys=").append(m == null ? "none" : new java.util.ArrayList<>(m.keySet())).append("\n");
                    if (m != null) for (Resume.Entry saved : Resume.items(base)) {
                        if (saved.pkg.equals(mc.getPackageName()) && Resume.matches(saved, m)) {
                            text.append("saved point=").append(saved.pos).append(" duration=").append(saved.dur)
                                    .append(" public title link=").append(!Resume.uri(saved).isEmpty()).append("\n");
                        }
                    }
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
                        if (id != null && (id.matches("[A-Za-z0-9_-]{11}") || id.matches("tt[0-9]+(:[0-9]+:[0-9]+)?"))) {
                            text.append(" publicId=").append(id);
                        }
                        text.append(" duration=").append(m.getLong(MediaMetadata.METADATA_KEY_DURATION)).append("\n");
                        for (String key : new String[] {MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, MediaMetadata.METADATA_KEY_MEDIA_URI}) {
                            String value = m.getString(key);
                            text.append(key).append(" scheme=").append(value == null ? "none" : Uri.parse(value).getScheme()).append(" ");
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
        int resultCode = 0;
        try {
            for (String name : new String[] {"resume", "recs", "hidden-picks"}) {
                if (!isolated.getSharedPreferences(name, 0).edit().clear().commit()) {
                    throw new IllegalStateException("could not clear isolated " + name + " fixture");
                }
            }
            SharedPreferences store = isolated.getSharedPreferences("resume", 0);
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
            boolean recommendationsConfigured = Recs.hasKey(isolated);
            check(recommendationsConfigured && recommendations.row(null, 10).size() == 1,
                    recommendationsConfigured ? "test recommendation must initially be present"
                            : "recommendation row coverage requires an app build configured for recommendations; empty rows do not prove hiding");
            recommendations.hide(pick);
            check(recommendationsConfigured && recommendations.row(null, 10).isEmpty() && recommendations.row("movie", 10).isEmpty(),
                    "hidden recommendation must disappear from all matching shelves");
            resumeTargets(isolated, store, yt, st);
            resolvedLinkHistory(isolated, store, yt);
            stremioLookup(st, yt);
            matchingIdentity(yt);
            staleController(base, isolated);
            cancelPendingResume();
            if (!failures.isEmpty()) throw new AssertionError(failures.size() + "/" + checks
                    + " failed:\n - " + android.text.TextUtils.join("\n - ", failures));
            result.putString("stream", "PASS: " + checks + " history checks (isolated data).\n");
            resultCode = -1;
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + e.toString() + "\n");
        } finally {
            for (String name : new String[] {"resume", "recs", "hidden-picks"}) {
                try {
                    if (!isolated.getSharedPreferences(name, 0).edit().clear().commit()) {
                        throw new IllegalStateException("could not clear isolated " + name + " fixture");
                    }
                    base.deleteSharedPreferences("history-check-" + name);
                } catch (RuntimeException e) {
                    resultCode = 0;
                    result.putString("stream", "FAIL: isolated fixture cleanup: " + e.toString() + "\n");
                }
            }
        }
        // finish() may terminate this process; complete all isolated fixture cleanup before calling it.
        finish(resultCode, result);
    }

    private void resumeTargets(Context isolated, SharedPreferences store, String yt, String st) {
        String video = "aqz-KE-bpKQ";
        store.edit().clear().commit();
        MediaMetadata pausedPoint = new MediaMetadata.Builder(metadata("Exact paused point", 600_000))
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, video).build();
        Resume.record(isolated, yt, pausedPoint, state(3, 100_000, 0));
        Resume.record(isolated, yt, pausedPoint, state(2, 112_345, 0));
        Resume.Entry paused = Resume.items(isolated).get(0);
        check(paused.pos == 112_345 && !Resume.record(isolated, yt, pausedPoint, state(2, 112_345, 0))
                        && Resume.items(isolated).get(0).at == paused.at,
                "final paused point must persist exactly below the playing throttle and repeated pause must keep its age");
        for (String id : new String[] {video, "https://www.youtube.com/watch?v=" + video + "&t=10",
                "https://youtu.be/" + video + "?t=10", "vnd.youtube:" + video}) {
            store.edit().clear().commit();
            MediaMetadata m = new MediaMetadata.Builder(metadata("Linked video", 600_000))
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id).build();
            Resume.record(isolated, yt, m, state(2, 125_000, 0));
            youtubeTarget(Resume.uri(Resume.items(isolated).get(0)), video, 125,
                    "YouTube media ID form must reopen the exact saved video and time: " + id);
        }
        store.edit().clear().commit();
        MediaMetadata linked = new MediaMetadata.Builder(metadata("URI-only video", 600_000))
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, "https://www.youtube.com/watch?v=" + video + "&t=7").build();
        Resume.record(isolated, yt, linked, state(2, 125_000, 0));
        youtubeTarget(Resume.uri(Resume.items(isolated).get(0)), video, 125,
                "MEDIA_URI must survive private history persistence and use the saved point");
        Resume.record(isolated, yt, metadata("URI-only video", 0), state(2, -1, 0));
        youtubeTarget(Resume.uri(Resume.items(isolated).get(0)), video, 125,
                "a pause that omits the URI must preserve the saved content link");
        youtubeTarget(Resume.uri(new Resume.Entry(yt, "YouTube", "Clamp", "", video, -1000, 600_000, 0)), video, 0,
                "a negative saved point must clamp to zero");
        youtubeTarget(Resume.uri(new Resume.Entry(yt, "YouTube", "Clamp", "", video, Long.MAX_VALUE, 600_000, 0)), video, 600,
                "a saved point beyond duration must clamp to duration without overflow");
        store.edit().clear().commit();
        String episode = "stremio:///detail/series/tt0877057/tt0877057:1:1";
        MediaMetadata stremio = new MediaMetadata.Builder(metadata("Episode 1", 1_200_000))
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, episode).build();
        Resume.record(isolated, st, stremio, state(2, 120_000, 0));
        String launch = Resume.uri(Resume.items(isolated).get(0));
        Uri detail = Uri.parse(launch);
        check("stremio".equals(detail.getScheme())
                        && "/detail/series/tt0877057/tt0877057:1:1".equals(detail.getPath())
                        && "true".equals(detail.getQueryParameter("autoPlay")),
                "Stremio must persist the exact episode detail link with autoPlay enabled");
        Resume.record(isolated, st, metadata("Episode 1", 0), state(2, -1, 0));
        check(launch.equals(Resume.uri(Resume.items(isolated).get(0))), "Stremio pause omission must retain its episode link");
        for (String address : new String[] {"https://cdn.example.test/video.m3u8?token=PRIVATE",
                "https://user:secret@www.youtube.com/watch?v=" + video,
                "https://www.youtube.com.evil.test/watch?v=" + video,
                "file:///private/video", "intent://watch#Intent;scheme=youtube;end"}) {
            store.edit().clear().commit();
            MediaMetadata rejected = new MediaMetadata.Builder(metadata("Not a public title link", 600_000))
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, address).build();
            Resume.record(isolated, yt, rejected, state(2, 125_000, 0));
            check(Resume.uri(Resume.items(isolated).get(0)).isEmpty(),
                    "stream, credential, spoofed-host and arbitrary intent URIs must not become launch targets");
        }
    }

    private void youtubeTarget(String address, String id, long seconds, String message) {
        Uri uri = Uri.parse(address);
        check("https".equals(uri.getScheme()) && "www.youtube.com".equals(uri.getHost())
                && "/watch".equals(uri.getPath()) && id.equals(uri.getQueryParameter("v"))
                && Long.toString(seconds).equals(uri.getQueryParameter("t")), message);
    }

    private void resolvedLinkHistory(Context isolated, SharedPreferences store, String yt) {
        store.edit().clear().commit();
        Resume.record(isolated, yt, metadata("Older unresolved title", 600_000), state(2, 125_000, 0));
        Resume.Entry older = Resume.items(isolated).get(0);
        Resume.record(isolated, yt, metadata("Most recent title", 600_000), state(2, 225_000, 0));
        Resume.Entry linked = Resume.withUri(isolated, older, "https://www.youtube.com/watch?v=aqz-KE-bpKQ");
        check("https://www.youtube.com/watch?v=aqz-KE-bpKQ&t=125".equals(Resume.uri(linked))
                        && linked.at == older.at && linked.pos == older.pos && linked.dur == older.dur,
                "resolving a content link must preserve saved age, progress and duration");
        List<Resume.Entry> after = Resume.items(isolated);
        check(after.size() == 2 && after.get(0).title.equals("Most recent title") && after.get(1).title.equals(older.title),
                "resolving an older content link must not reorder the home row");
        Resume.remove(isolated, linked);
        Resume.withUri(isolated, older, "https://www.youtube.com/watch?v=aqz-KE-bpKQ");
        after = Resume.items(isolated);
        check(after.size() == 1 && after.get(0).title.equals("Most recent title"),
                "late lookup completion must not resurrect a card removed by the user");
    }

    private void cancelPendingResume() throws Exception {
        java.lang.reflect.Field pending = NowPlaying.class.getDeclaredField("pendingResume");
        java.lang.reflect.Field handlerField = NowPlaying.class.getDeclaredField("resumeHandler");
        pending.setAccessible(true);
        handlerField.setAccessible(true);
        Handler handler = (Handler) handlerField.get(null);
        AtomicInteger calls = new AtomicInteger();
        Runnable probe = () -> calls.incrementAndGet();
        try {
            pending.set(null, probe);
            handler.postDelayed(probe, 200);
            NowPlaying.cancelResume();
            Thread.sleep(300);
            check(pending.get(null) == null && calls.get() == 0,
                    "canceling resume must clear and remove its queued callback");
        } finally {
            handler.removeCallbacks(probe);
            pending.set(null, null);
        }
    }

    private void stremioLookup(String st, String yt) {
        String id = "tt0877057", other = "tt1234567";
        check(Resume.safeUri(st, id).isEmpty(), "a bare IMDb ID cannot identify whether Stremio content is a movie or series");
        String series = "stremio:///detail/series/" + id;
        String movie = "stremio:///detail/movie/" + id;
        String episode = series + "/" + id + ":2:3?autoPlay=true";
        Resume.Entry show = new Resume.Entry(st, "Stremio", "Death Note - S02E03 - Test episode", "", "", 125_000, 600_000, 0);
        Resume.Entry film = new Resume.Entry(st, "Stremio", "Death Note", "", "", 125_000, 600_000, 0);
        List<ResumeLookup.Candidate> exact = java.util.Arrays.asList(new ResumeLookup.Candidate("Death Note", series));
        check(episode.equals(ResumeLookup.choose(show, exact)), "unique exact Stremio series must map the saved season and episode");
        check((movie + "/" + id + "?autoPlay=true").equals(ResumeLookup.choose(film,
                        java.util.Arrays.asList(new ResumeLookup.Candidate("Death Note", movie)))),
                "unique exact Stremio movie must map the movie ID");
        check(ResumeLookup.choose(show, java.util.Arrays.asList(new ResumeLookup.Candidate("Another Show", series))).isEmpty(),
                "a near or unrelated title must not become a Stremio resume target");
        check(ResumeLookup.choose(show, java.util.Arrays.asList(new ResumeLookup.Candidate("Death Note", movie))).isEmpty(),
                "a movie with the same title must not replace a saved series episode");
        check(ResumeLookup.choose(film, exact).isEmpty(), "a series with the same title must not replace a saved movie");
        check(ResumeLookup.choose(show, java.util.Collections.emptyList()).isEmpty(), "empty provider results must stay at fallback");
        check(episode.equals(ResumeLookup.choose(show, java.util.Arrays.asList(exact.get(0), exact.get(0)))),
                "duplicate results for one canonical ID are not ambiguous");
        check(ResumeLookup.choose(show, java.util.Arrays.asList(exact.get(0),
                        new ResumeLookup.Candidate("Death Note", "stremio:///detail/series/" + other))).isEmpty(),
                "distinct IDs for one exact title are ambiguous and must not be chosen");
        check(episode.equals(ResumeLookup.choose(show, java.util.Arrays.asList(new ResumeLookup.Candidate("  death   NOTE  ", series)))),
                "title case and whitespace normalization must preserve unique exact matching");
        Resume.Entry zero = new Resume.Entry(st, "Stremio", "Death Note - S01E00 - Unknown episode", "", "", 125_000, 600_000, 0);
        check(ResumeLookup.choose(zero, exact).isEmpty(), "episode zero must not become a guessed playable episode");
        check(ResumeLookup.choose(new Resume.Entry(yt, "YouTube", show.title, "", "", 0, 0, 0), exact).isEmpty(),
                "Stremio provider candidates must not resolve another service's history");
        String poster = "https://images.metahub.space/poster/medium/" + id + "/img";
        MediaMetadata art = new MediaMetadata.Builder(metadata(show.title, 600_000))
                .putString(MediaMetadata.METADATA_KEY_ART_URI, poster).build();
        check(episode.equals(ResumeLookup.fromMetadata(st, art, show.title)),
                "Stremio-owned public poster ID plus explicit SxxExx must recover the exact episode");
        MediaMetadata repeated = new MediaMetadata.Builder(art)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://images.metahub.space/background/medium/" + id + "/img").build();
        check(episode.equals(ResumeLookup.fromMetadata(st, repeated, show.title)), "matching artwork IDs must resolve consistently");
        MediaMetadata conflict = new MediaMetadata.Builder(art)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://images.metahub.space/poster/medium/" + other + "/img").build();
        check(ResumeLookup.fromMetadata(st, conflict, show.title).isEmpty(), "conflicting artwork IDs must refuse automatic selection");
        for (String unsafe : new String[] {"https://cdn.example.test/poster/medium/" + id + "/img",
                "https://user:secret@images.metahub.space/poster/medium/" + id + "/img",
                poster + "?token=PRIVATE", "https://images.metahub.space/poster/medium/not-imdb/img",
                "https://images.metahub.space/poster/medium/" + id,
                "https://images.metahub.space.evil.test/poster/medium/" + id + "/img",
                "https://images.metahub.space:444/poster/medium/" + id + "/img"}) {
            MediaMetadata unsafeArt = new MediaMetadata.Builder(metadata(show.title, 600_000))
                    .putString(MediaMetadata.METADATA_KEY_ART_URI, unsafe).build();
            check(ResumeLookup.fromMetadata(st, unsafeArt, show.title).isEmpty(),
                    "only strict public Metahub artwork paths may identify a Stremio episode");
        }
        check(ResumeLookup.fromMetadata(st, art, zero.title).isEmpty(), "artwork cannot turn episode zero into a valid episode");
        check(ResumeLookup.fromMetadata(st, art, film.title).isEmpty(), "a poster alone cannot distinguish a plain movie title from a series");
        check(ResumeLookup.fromMetadata(yt, art, show.title).isEmpty(), "Stremio artwork identity must not be applied to YouTube");
        check(ResumeLookup.fromMetadata(st, null, show.title).isEmpty(), "missing metadata must leave Stremio resolution unavailable");
    }

    private void matchingIdentity(String yt) {
        String id = "aqz-KE-bpKQ";
        Resume.Entry saved = new Resume.Entry(yt, "YouTube", "Saved exact title", "Channel A", id, 125_000, 600_000, 0);
        MediaMetadata match = new MediaMetadata.Builder(metadata(saved.title, 600_000))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Channel A")
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id).build();
        check(Resume.matches(saved, match), "same title, channel, ID and duration must match for resume");
        check(Resume.matches(saved, new MediaMetadata.Builder(metadata(saved.title, 600_000))
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id).build()),
                "omitted pause subtitle must not discard a known matching title");
        check(!Resume.matches(saved, new MediaMetadata.Builder(match).putString(MediaMetadata.METADATA_KEY_ARTIST, "Channel B").build()),
                "conflicting nonempty subtitles must refuse a seek");
        check(!Resume.matches(saved, new MediaMetadata.Builder(match).putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "dQw4w9WgXcQ").build()),
                "canonical content ID disagreement must refuse a seek even with the same title");
        check(!Resume.matches(saved, new MediaMetadata.Builder(match).putLong(MediaMetadata.METADATA_KEY_DURATION, 20_000).build()),
                "an ad retaining the title must never receive the episode's resume point");
        check(Resume.matches(saved, new MediaMetadata.Builder(match).putLong(MediaMetadata.METADATA_KEY_DURATION, 605_000).build()),
                "small duration rounding changes may preserve a known identity");
        check(!Resume.matches(saved, new MediaMetadata.Builder(match).putLong(MediaMetadata.METADATA_KEY_DURATION, 640_000).build()),
                "a materially different cut must not receive a saved seek");
        check(Resume.matches(saved, new MediaMetadata.Builder(match).putLong(MediaMetadata.METADATA_KEY_DURATION, 0).build()),
                "omitted paused duration must not reject matching title and content ID");
        check(Resume.matches(saved, metadata(saved.title, 600_000)), "omitted ID may use exact title, subtitle and duration fallback");
        check(!Resume.matches(saved, metadata("Different title", 600_000)), "different title must never receive saved controls");
        check(!Resume.matches(saved, null), "null metadata must never be considered an identified title");
        check(!Resume.matches(new Resume.Entry(yt, "YouTube", "", "", "", 0, 0, 0), match),
                "an app-only card supplies no content identity for seeking");
    }

    @SuppressWarnings("unchecked")
    private void staleController(Context base, Context isolated) throws Exception {
        MediaSession session = new MediaSession(base, "isolated-history-resume-check");
        AtomicInteger seeks = new AtomicInteger(), plays = new AtomicInteger();
        AtomicLong lastSeek = new AtomicLong(-1);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onSeekTo(long point) { lastSeek.set(point); seeks.incrementAndGet(); }
            @Override public void onPlay() { plays.incrementAndGet(); }
        }, new Handler(Looper.getMainLooper()));
        try {
            session.setMetadata(metadata("Saved title A", 600_000));
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 125_000, 0)
                    .setActions(PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_PLAY).build());
            MediaController controller = session.getController();
            controller.getTransportControls().seekTo(1);
            long deadline = SystemClock.elapsedRealtime() + 2000;
            while (seeks.get() == 0 && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25);
            check(seeks.get() == 1, "isolated MediaSession must receive transport controls before stale-target check");
            seeks.set(0);
            NowPlaying.Watch watch = new NowPlaying.Watch(isolated, () -> {}, false);
            java.lang.reflect.Field controllers = NowPlaying.Watch.class.getDeclaredField("controllers");
            controllers.setAccessible(true);
            ((List<MediaController>) controllers.get(watch)).add(controller);
            Resume.Entry saved = new Resume.Entry(controller.getPackageName(), "Test", "Saved title A", "", "", 125_000, 600_000, 0);
            Runnable resume = watch.prepareResume(saved);
            check(resume != null, "matching live session must prepare resume");
            session.setMetadata(metadata("Different title B", 600_000));
            if (resume != null) resume.run();
            Thread.sleep(750);
            check(seeks.get() == 0 && plays.get() == 0,
                    "a delayed resume must recheck content identity and never seek or play a different title");
            session.setMetadata(metadata(saved.title, 600_000));
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 125_000, 0)
                    .setActions(0).build());
            Runnable fresh = watch.prepareResume(saved);
            check(fresh != null, "matching title may prepare before the player advertises ready actions");
            if (fresh != null) fresh.run();
            Thread.sleep(200);
            check(seeks.get() == 0 && plays.get() == 0, "resume must respect actions read at execution time");
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 125_000, 0)
                    .setActions(PlaybackState.ACTION_SEEK_TO).build());
            if (fresh != null) fresh.run();
            Thread.sleep(200);
            check(seeks.get() == 0 && plays.get() == 0, "paused player without a play action must not receive partial resume controls");
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 125_000, 0)
                    .setActions(PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_PLAY).build());
            if (fresh != null) fresh.run();
            Thread.sleep(200);
            check(seeks.get() == 0 && plays.get() == 0, "stopped player must wait rather than receive stale controls");
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 125_000, 0)
                    .setActions(PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_PLAY).build());
            if (fresh != null) fresh.run();
            deadline = SystemClock.elapsedRealtime() + 2000;
            while ((seeks.get() == 0 || plays.get() == 0) && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25);
            check(seeks.get() == 1 && plays.get() == 1 && lastSeek.get() == 125_000,
                    "a newly ready matching player must use fresh actions to seek the saved point and play once");
            seeks.set(0);
            plays.set(0);
            lastSeek.set(-1);
            Resume.Entry beginning = new Resume.Entry(saved.pkg, "Test", saved.title, "", "", 0, 600_000, 0);
            Runnable start = watch.prepareResume(beginning);
            if (start != null) start.run();
            deadline = SystemClock.elapsedRealtime() + 2000;
            while ((seeks.get() == 0 || plays.get() == 0) && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25);
            check(seeks.get() == 1 && plays.get() == 1 && lastSeek.get() == 0,
                    "an identified title saved at zero must seek to its beginning rather than resume an old offset");
        } finally {
            session.release();
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

package io.freemytv;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recover Stremio's public title identity, never a playable stream or an account token. */
final class ResumeLookup {
    private static final Pattern EPISODE = Pattern.compile("^(.+?)\\s+-\\s+S([0-9]{1,3})E([0-9]{1,4})(?:\\s+-.*)?$", Pattern.CASE_INSENSITIVE);
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean stremio(String pkg) {
        return pkg.equals("com.stremio.one") || pkg.equals("com.stremio");
    }

    /** This provider's own artwork embeds its canonical IMDb ID, while MEDIA_ID is often empty. */
    static String fromMetadata(String pkg, MediaMetadata metadata, String title) {
        if (!stremio(pkg) || metadata == null) return "";
        Matcher episode = EPISODE.matcher(title);
        // A poster alone cannot tell whether a plain title is a movie or a series. Do not guess.
        if (!episode.matches()) return "";
        String found = "";
        for (String key : new String[] {MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String address = metadata.getString(key);
            if (address == null) continue;
            try {
                Uri uri = Uri.parse(address);
                if (!"https".equals(uri.getScheme()) || !"images.metahub.space".equals(uri.getHost())
                        || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null) continue;
                List<String> path = uri.getPathSegments();
                if (path.size() != 4 || !(path.get(0).equals("poster") || path.get(0).equals("background"))
                        || !path.get(2).matches("tt[0-9]{5,12}") || !path.get(3).equals("img")) continue;
                String id = path.get(2);
                if (!found.isEmpty() && !found.equals(id)) return "";
                found = id;
            } catch (RuntimeException ignored) { }
        }
        return found.isEmpty() ? "" : episodeUri(pkg, found, episode);
    }

    static final class Candidate {
        final String title, uri;
        Candidate(String title, String uri) { this.title = title; this.uri = uri; }
    }

    /** Exact unique provider-title match only. Ambiguous titles stay at the app fallback. */
    static String choose(Resume.Entry entry, List<Candidate> candidates) {
        if (!stremio(entry.pkg)) return "";
        Matcher episode = EPISODE.matcher(entry.title);
        boolean series = episode.matches();
        String title = series ? episode.group(1) : entry.title;
        String found = "";
        for (Candidate candidate : candidates) {
            if (!normal(title).equals(normal(candidate.title))) continue;
            String safe = Resume.safeUri(entry.pkg, candidate.uri);
            if (safe.isEmpty()) continue;
            List<String> path = Uri.parse(safe).getPathSegments();
            if (path.size() < 3 || !path.get(0).equals("detail") || !path.get(2).matches("tt[0-9]{5,12}")) continue;
            if (series && !path.get(1).equals("series") || !series && !path.get(1).equals("movie")) continue;
            String link = series ? episodeUri(entry.pkg, path.get(2), episode)
                    : Resume.safeUri(entry.pkg, "stremio:///detail/movie/" + path.get(2) + "/" + path.get(2));
            if (!found.isEmpty() && !found.equals(link)) return "";
            found = link;
        }
        return found;
    }

    private static String normal(String title) {
        return title == null ? "" : title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String episodeUri(String pkg, String id, Matcher episode) {
        int season = Integer.parseInt(episode.group(2)), number = Integer.parseInt(episode.group(3));
        if (number == 0) return "";
        return Resume.safeUri(pkg, "stremio:///detail/series/" + id + "/" + id + ":" + season + ":" + number);
    }

    /** One bounded, cancellable lookup in Stremio's exported TV search provider, off the UI thread. */
    static void resolve(Context context, Resume.Entry entry, Consumer<Resume.Entry> ready) {
        if (!stremio(entry.pkg) || !Resume.uri(entry).isEmpty() || entry.title.isEmpty()) {
            ready.accept(entry);
            return;
        }
        Context app = context.getApplicationContext();
        AtomicBoolean delivered = new AtomicBoolean();
        CancellationSignal cancel = new CancellationSignal();
        Runnable timeout = () -> {
            if (delivered.compareAndSet(false, true)) {
                ready.accept(entry);
                // Cancellation can wait for the provider; never do that work on the UI thread.
                new Thread(cancel::cancel, "resume-lookup-cancel").start();
            }
        };
        MAIN.postDelayed(timeout, 2500);
        WORK.execute(() -> {
            String link = "";
            try {
                Matcher episode = EPISODE.matcher(entry.title);
                String query = episode.matches() ? episode.group(1) : entry.title;
                Uri uri = new Uri.Builder().scheme("content").authority(entry.pkg + ".content")
                        .appendPath("search_suggest_query").appendPath(query).build();
                List<Candidate> candidates = new ArrayList<>();
                try (Cursor cursor = app.getContentResolver().query(uri,
                        new String[] {"suggest_text_1", "suggest_intent_data"}, null, null, null, cancel)) {
                    if (cursor != null) {
                        int name = cursor.getColumnIndex("suggest_text_1"), data = cursor.getColumnIndex("suggest_intent_data");
                        while (name >= 0 && data >= 0 && candidates.size() < 40 && cursor.moveToNext())
                            candidates.add(new Candidate(cursor.getString(name), cursor.getString(data)));
                    }
                }
                link = choose(entry, candidates);
            } catch (RuntimeException ignored) { } // absent/disabled/permission-denied provider: safe app fallback
            final String address = link;
            MAIN.post(() -> {
                if (!delivered.compareAndSet(false, true)) return;
                MAIN.removeCallbacks(timeout);
                ready.accept(address.isEmpty() ? entry : Resume.withUri(app, entry, address));
            });
        });
    }
}

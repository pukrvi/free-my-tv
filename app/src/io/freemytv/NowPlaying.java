package io.freemytv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What any app is playing (title, artist or channel, the app), from the media session each app publishes for the remote's
 * play/pause and the Assistant: one way for music, videos, radio and live streams alike. Reading the sessions needs this
 * notification listener allowed (HomeMode does it while Now Playing or Continue Watching is on); it never reads a
 * notification. While Continue Watching is enabled, it stays bound to capture playback before apps end their sessions.
 */
public class NowPlaying extends NotificationListenerService {

    private Watch history;
    private final Handler historyHandler = new Handler(Looper.getMainLooper());
    private final Runnable sample = new Runnable() {
        @Override public void run() {
            if (!Prefs.resumeOn()) {
                requestUnbind();
                return;
            }
            if (history != null) history.update();
            historyHandler.postDelayed(this, 15_000);
        }
    };

    @Override public void onListenerConnected() {
        Prefs.init(this);
        if (history != null) history.stop();
        if (!Prefs.resumeOn()) {
            requestUnbind();
            return;
        }
        history = new Watch(this, () -> {}, true);
        history.start();
        historyHandler.removeCallbacks(sample);
        historyHandler.postDelayed(sample, 15_000);
    }

    @Override public void onListenerDisconnected() {
        if (history != null) history.stop();
        history = null;
        historyHandler.removeCallbacks(sample);
    }

    @Override public void onDestroy() {
        onListenerDisconnected();
        super.onDestroy();
    }

    private static final float[] STILL = {0.55f, 1, 0.75f}, PERIOD_MS = {170, 230, 200};

    /** The bars' width for text of this cap height (drawBars). */
    static float barsWidth(float cap) {
        return cap * 1.12f;
    }

    /** Three level bars before the title, standing on its baseline, `cap` tall: still, or bouncing like a level meter. */
    static void drawBars(Canvas c, float x, float base, float cap, Paint p, boolean moving, RectF r) {
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < 3; i++) {
            float level = moving ? 0.3f + 0.7f * Math.abs((float) Math.sin(t / PERIOD_MS[i] + i * 1.3f)) : STILL[i];
            float bx = x + i * cap * 0.42f;
            r.set(bx, base - cap * level, bx + cap * 0.28f, base);
            c.drawRoundRect(r, cap * 0.14f, cap * 0.14f, p);
        }
    }

    static ComponentName component(Context c) {
        return new ComponentName(c, NowPlaying.class);
    }

    /** Whether Android lets us read the sessions: our listener is allowed. */
    static boolean allowed(Context c) {
        try {
            c.getSystemService(MediaSessionManager.class).getActiveSessions(component(c));
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static final Handler resumeHandler = new Handler(Looper.getMainLooper());
    private static Runnable pendingResume;

    static void cancelResume() {
        if (pendingResume != null) resumeHandler.removeCallbacks(pendingResume);
        pendingResume = null;
    }

    /** The provider can create a new session after Home pauses, so reacquire until its requested content is ready. */
    static void requestResume(Context c, Resume.Entry entry) {
        requestResume(c, entry, false);
    }

    static void requestResume(Context c, Resume.Entry entry, boolean searchFallback) {
        cancelResume();
        if (entry.title.isEmpty()) return;   // an app launch supplies no identity to seek safely
        Context context = c.getApplicationContext();
        long started = SystemClock.elapsedRealtime();
        long deadline = started + (searchFallback ? 60_000 : 15_000);
        pendingResume = new Runnable() {
            private boolean playbackTransition;
            private MediaController stable;
            private long matchedAt;
            @Override public void run() {
                if (pendingResume != this || !Prefs.resumeOn() || SystemClock.elapsedRealtime() >= deadline) {
                    if (pendingResume == this) pendingResume = null;
                    return;
                }
                MediaController match = null;
                boolean matchingPlayback = false;
                try {
                    for (MediaController mc : context.getSystemService(MediaSessionManager.class).getActiveSessions(component(context))) {
                        if (!entry.pkg.equals(mc.getPackageName()) || !Resume.matches(entry, mc.getMetadata())) continue;
                        PlaybackState state = mc.getPlaybackState();
                        if (state == null || state.getState() != PlaybackState.STATE_PLAYING && state.getState() != PlaybackState.STATE_PAUSED) continue;
                        if (state.getState() == PlaybackState.STATE_PLAYING) matchingPlayback = true;
                        if (searchFallback && (state.getState() != PlaybackState.STATE_PLAYING
                                || !playbackTransition || state.getLastPositionUpdateTime() < started)) {
                            if (state.getState() != PlaybackState.STATE_PLAYING) playbackTransition = true;
                            continue;
                        }
                        match = mc;
                        break;
                    }
                    if (match == null) {
                        if (searchFallback && !matchingPlayback) playbackTransition = true;
                        stable = null;
                        matchedAt = 0;
                    } else if (stable == null || !stable.getSessionToken().equals(match.getSessionToken())) {
                        stable = match;
                        matchedAt = SystemClock.elapsedRealtime();
                    } else if (SystemClock.elapsedRealtime() - matchedAt >= 500 && resumeController(entry, match)) {
                        pendingResume = null;
                        return;
                    }
                } catch (SecurityException ignored) {
                    pendingResume = null;   // permission was revoked: never work around it
                    return;
                }
                resumeHandler.postDelayed(this, 250);
            }
        };
        // Let the launch arrive before interpreting the old session as the requested player.
        resumeHandler.postDelayed(pendingResume, 750);
    }

    private static boolean resumeController(Resume.Entry entry, MediaController controller) {
        try {
            if (!entry.pkg.equals(controller.getPackageName()) || !Resume.matches(entry, controller.getMetadata())) return false;
            PlaybackState state = controller.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING && state.getState() != PlaybackState.STATE_PAUSED) return false;
            long actions = state.getActions();
            if (entry.pos > 0 && (actions & PlaybackState.ACTION_SEEK_TO) == 0) return false;
            if (state.getState() == PlaybackState.STATE_PAUSED && (actions & PlaybackState.ACTION_PLAY) == 0) return false;
            MediaController.TransportControls controls = controller.getTransportControls();
            // Recheck at the last possible point: a callback can have been prepared for an older title.
            if (!Resume.matches(entry, controller.getMetadata())) return false;
            if ((actions & PlaybackState.ACTION_SEEK_TO) != 0) controls.seekTo(Resume.point(entry));
            if (state.getState() == PlaybackState.STATE_PAUSED) controls.play();
            return true;
        } catch (RuntimeException ignored) {
            return false;   // destroyed provider session; a later poll can acquire its replacement
        }
    }

    private static boolean asked;

    /** A screen's watch: start() when it shows, stop() when it's hidden. text is null while nothing plays. */
    static final class Watch implements MediaSessionManager.OnActiveSessionsChangedListener {
        private final Context context;
        private final Runnable changed;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final List<MediaController> controllers = new ArrayList<>();
        private final Map<String, String> labels = new HashMap<>();   // some players report every second
        private final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) { update(); }
            @Override public void onMetadataChanged(MediaMetadata metadata) { update(); }
            @Override public void onSessionDestroyed() { update(); }
        };
        private boolean on;
        private final boolean record;   // remember what played, for the home screen's Continue Watching row
        volatile String text;

        Watch(Context c, Runnable changed, boolean record) {
            context = c;
            this.changed = changed;
            this.record = record;
        }

        void start() {
            // The permission is shared by the Now Playing widget and, on the home screen, Continue Watching: asked
            // while either is on, and the watch only reads sessions when one of them wants to.
            if (on || !(Prefs.nowPlaying() || (record && Prefs.resumeOn()))) return;   // off in Home Settings: nothing watched at all
            if (record && Prefs.resumeOn() && !(context instanceof NowPlaying)) {
                // A previous version unbound the listener; reconnect after an update or force-stop.
                requestRebind(component(context));
            }
            MediaSessionManager m = context.getSystemService(MediaSessionManager.class);
            try {
                m.addOnActiveSessionsChangedListener(this, component(context), handler);
                on = true;
                onActiveSessionsChanged(m.getActiveSessions(component(context)));
            } catch (SecurityException e) {
                // On, but not allowed (it was on when Original or the watchdog took it back): asked once, as Free My TV
                // mode's switch itself never allows it. A TV that refused it shows the row unavailable instead.
                if (!asked && Capabilities.adb(context) && !Capabilities.refused(context, Capabilities.LISTENER)) {
                    asked = true;
                    HomeMode.nowPlayingAccess(context);
                }
            }
        }

        void stop() {
            if (!on) return;
            on = false;
            context.getSystemService(MediaSessionManager.class).removeOnActiveSessionsChangedListener(this);
            onActiveSessionsChanged(null);
        }

        /** A prepared callback still checks live metadata/actions when invoked; the captured session may change. */
        Runnable prepareResume(Resume.Entry entry) {
            for (MediaController mc : controllers) {
                if (entry.pkg.equals(mc.getPackageName()) && Resume.matches(entry, mc.getMetadata())) {
                    return () -> resumeController(entry, mc);
                }
            }
            return null;
        }

        @Override public void onActiveSessionsChanged(List<MediaController> sessions) {
            for (MediaController mc : controllers) mc.unregisterCallback(callback);
            controllers.clear();
            if (sessions != null && on) {
                for (MediaController mc : sessions) {
                    mc.registerCallback(callback, handler);
                    controllers.add(mc);
                }
            }
            update();
        }

        private void update() {
            if (record && Prefs.resumeOn()) {
                boolean wrote = false;
                // Sessions are ordered most-recent first. Resume.record inserts at the front, so record in reverse to
                // keep the current app first when several services still publish paused sessions.
                for (int i = controllers.size() - 1; i >= 0; i--) wrote |= Resume.record(context, controllers.get(i));
                if (wrote) changed.run();   // the row has something new (or a title moved up), not just a position
            }
            String now = null;
            for (MediaController mc : controllers) {   // most recent first
                PlaybackState s = mc.getPlaybackState();
                int state = s == null ? PlaybackState.STATE_NONE : s.getState();
                // A stream that buffers is still playing: the line doesn't blink back to the prices.
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                        || state == PlaybackState.STATE_CONNECTING) {
                    now = describe(mc);
                    break;
                }
            }
            if (Objects.equals(now, text)) return;
            text = now;
            changed.run();
        }

        /** "Title · Artist · App": the parts the app gives, then its name; null when there's none of them. */
        private String describe(MediaController mc) {
            List<String> parts = new ArrayList<>();
            MediaMetadata m = mc.getMetadata();
            MediaDescription d = m == null ? null : m.getDescription();
            if (d != null && !TextUtils.isEmpty(d.getTitle())) parts.add(d.getTitle().toString().trim());
            if (d != null && !TextUtils.isEmpty(d.getSubtitle())) parts.add(d.getSubtitle().toString().trim());
            String app = labels.get(mc.getPackageName());
            if (app == null) {
                try {
                    PackageManager pm = context.getPackageManager();
                    app = pm.getApplicationLabel(pm.getApplicationInfo(mc.getPackageName(), 0)).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    app = "";   // an app without a launcher icon is hidden from us (Android 11+): no name to show
                }
                labels.put(mc.getPackageName(), app);
            }
            if (!app.isEmpty() && !parts.contains(app)) parts.add(app);
            return parts.isEmpty() ? null : TextUtils.join(" · ", parts);
        }
    }
}

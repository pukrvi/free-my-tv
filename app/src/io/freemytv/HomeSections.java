package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Mine's home composition, kept separate from app membership and the other home themes. */
final class HomeSections {
    private HomeSections() {}
    private static final String[] KEYS = {"actions", "weather", "apps", "continue", "for-you", "anime", "movies", "tv-shows"};

    static String[] keys() { return KEYS.clone(); }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("home-sections", Context.MODE_PRIVATE);
    }

    /** Always returns every known section exactly once, preserving valid saved order across upgrades. */
    static List<String> order(Context c) {
        List<String> order = new ArrayList<>();
        for (String key : prefs(c).getString("order", "").split(",")) {
            if (known(key) && !order.contains(key)) order.add(key);
        }
        for (String key : KEYS) if (!order.contains(key)) order.add(key);
        return order;
    }

    static boolean visible(Context c, String key) {
        return known(key) && prefs(c).getBoolean("visible-" + key, true);
    }

    static void setOrder(Context c, List<String> requested) {
        List<String> order = new ArrayList<>();
        if (requested != null) for (String key : requested) if (known(key) && !order.contains(key)) order.add(key);
        for (String key : KEYS) if (!order.contains(key)) order.add(key);
        prefs(c).edit().putString("order", android.text.TextUtils.join(",", order)).apply();
        Prefs.version++;
    }

    /** Weather is informational; at least one navigable section must remain enabled. */
    static boolean setVisible(Context c, String key, boolean show) {
        if (!known(key)) return false;
        if (!show && !key.equals("weather")) {
            boolean another = false;
            for (String other : KEYS) if (!other.equals("weather") && !other.equals(key) && visible(c, other)) another = true;
            if (!another) return false;
        }
        prefs(c).edit().putBoolean("visible-" + key, show).apply();
        Prefs.version++;
        return true;
    }

    static void reset(Context c) {
        prefs(c).edit().clear().apply();
        Prefs.version++;
    }

    private static boolean known(String key) { return Arrays.asList(KEYS).contains(key); }
}

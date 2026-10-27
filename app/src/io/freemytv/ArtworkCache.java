package io.freemytv;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/** Ready artwork. The caller holds this cache's monitor while accessing it. */
final class ArtworkCache<T> extends LinkedHashMap<String, T> {
    private int limit = 24;
    private final Set<String> visible = new HashSet<>();

    ArtworkCache() { super(32, .75f, true); }

    void retainVisible(Set<String> keys) {
        if (visible.equals(keys)) return;
        visible.clear(); visible.addAll(keys); trim();
    }
    boolean isVisible(String key) { return visible.contains(key); }

    void limit(int value) { limit = value; trim(); }

    @Override public T put(String key, T value) {
        T previous = super.put(key, value);
        trim();
        return previous;
    }

    private void trim() {
        Iterator<String> keys = keySet().iterator();
        while (size() > limit && keys.hasNext()) {
            if (!visible.contains(keys.next())) keys.remove();
        }
    }
}

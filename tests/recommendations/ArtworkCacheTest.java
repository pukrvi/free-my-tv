package io.freemytv;

import java.util.*;

public final class ArtworkCacheTest {
    public static void main(String[] args) {
        ArtworkCache<Object> cache = new ArtworkCache<>();
        cache.limit(24);
        Set<String> visible = new LinkedHashSet<>();
        for (int i = 0; i < 26; i++) visible.add("visible-" + i);
        cache.retainVisible(visible);
        // The reusable caller set can change after publication without changing cache protection.
        Set<String> published = new HashSet<>(visible);
        cache.retainVisible(published);
        published.clear();
        for (String key : visible) cache.put(key, new Object());
        // Older requests finish after scrolling. None may replace current visible artwork.
        for (int i = 0; i < 50; i++) cache.put("old-" + i, new Object());
        for (String key : visible) {
            Object image = cache.get(key);
            if (image == null) throw new AssertionError("Visible artwork evicted: " + key);
            for (int i = 0; i < 50; i++) cache.put("late-" + i, new Object());
            if (cache.get(key) != image) throw new AssertionError("Visible artwork changed: " + key);
        }
        if (cache.size() > 26) throw new AssertionError("Cache exceeded protected viewport");
        cache.retainVisible(Collections.emptySet());
        if (cache.size() > 24) throw new AssertionError("Cache did not shrink after scrolling");
        cache.clear();
        cache.limit(2);
        cache.put("recent", new Object()); cache.put("other", new Object());
        cache.get("recent"); cache.put("next", new Object());
        if (!cache.containsKey("recent") || cache.containsKey("other")) throw new AssertionError("Not access ordered");
        System.out.println("Artwork cache: visible images stable across 1,350 late loads; bounded and access ordered");
    }
}

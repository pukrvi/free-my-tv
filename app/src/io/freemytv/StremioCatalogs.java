package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discovery only: exactly two public addons; no account, stream requests or addon installation. */
final class StremioCatalogs {
    static final String KITSU = "https://anime-kitsu.strem.fun";
    static final String STREAMING = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club";
    static final String[][] PROVIDERS = {
        {"netflix", "nfx"}, {"prime", "amp"}, {"hotstar", "jhs"}, {"disney", "dnp"},
        {"max", "hbm"}, {"appletv", "atp"}, {"hulu", "hlu"}, {"paramount", "pmp"},
        {"peacock", "pcp"}, {"crunchyroll", "cru"}, {"globoplay", "gop"}
    };

    static final class Entry {
        String id, imdb, type, title, image, meta, source;
        JSONObject metadata;
        java.util.Set<String> feeds=new java.util.LinkedHashSet<>();
        java.util.Map<String,Double> ranks=new java.util.HashMap<>();
        int airingRank=Integer.MAX_VALUE;
        int tmdb;
        boolean anime;
    }

    interface Transport { JSONObject get(String url) throws Exception; }
    private final Transport transport;
    StremioCatalogs() { this(StremioCatalogs::get); }
    StremioCatalogs(Transport transport) { this.transport = transport; }

    List<Entry> anime() throws Exception {
        return catalogs(KITSU, "community.anime.kitsu", new String[] {"kitsu-anime-trending", "kitsu-anime-airing"}, true);
    }

    List<Entry> streaming(List<String> services) throws Exception {
        List<String> ids = new ArrayList<>();
        for (String service : services) for (String[] provider : PROVIDERS)
            if (provider[0].equals(service) && !ids.contains(provider[1])) ids.add(provider[1]);
        return catalogs(STREAMING, "pw.ers.netflix-catalog", ids.toArray(new String[0]), false);
    }

    private List<Entry> catalogs(String base, String expected, String[] ids, boolean anime) throws Exception {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (ids.length == 0) return new ArrayList<>();
        JSONObject manifest = transport.get(base + "/manifest.json");
        if (!expected.equals(manifest.optString("id"))) throw new IOException("Unexpected catalog manifest");
        JSONArray catalogs = manifest.optJSONArray("catalogs");
        // Some public deployments omit supported services from the default manifest (notably JioHotstar).
        // Request a configured manifest for precisely our provider ids; never assume a feed means regional availability.
        if (!anime) {
            String config = String.join(",", ids) + "::";
            String encoded = java.util.Base64.getEncoder().encodeToString(config.getBytes(StandardCharsets.UTF_8));
            JSONObject configured = transport.get(base + "/" + segment(encoded) + "/manifest.json");
            if (!expected.equals(configured.optString("id"))) throw new IOException("Unexpected catalog manifest");
            catalogs = configured.optJSONArray("catalogs");
            base += "/" + segment(encoded);
        }
        Exception failure = null;
        int succeeded = 0;
        for (String id : ids) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Catalog fetch cancelled");
            for (int i = 0; catalogs != null && i < catalogs.length(); i++) {
                JSONObject catalog = catalogs.optJSONObject(i);
                if (catalog == null || !id.equals(catalog.optString("id")) || requiredExtra(catalog)) continue;
                String type = catalog.optString("type");
                if (!("anime".equals(type) || "series".equals(type) || "movie".equals(type))) continue;
                try {
                    JSONArray metas = transport.get(base + "/catalog/" + segment(type) + "/" + segment(id) + ".json").optJSONArray("metas");
                    if (metas == null) throw new IOException("Catalog returned no metas");
                    succeeded++;
                    int added = 0;
                    for (int j = 0; j < metas.length() && added < (anime ? (id.equals("kitsu-anime-airing") ? 20 : 30) : 40); j++) {
                        Entry entry = parse(metas.optJSONObject(j), anime);
                        if(entry!=null){
                            entry.feeds.add(id);entry.ranks.put(id,1-j/(double)Math.max(1,metas.length()-1));
                            if(id.equals("kitsu-anime-airing"))entry.airingRank=j;
                            Entry previous=out.putIfAbsent(entry.type+":"+entry.id,entry);
                            if(previous!=null){previous.feeds.addAll(entry.feeds);previous.ranks.putAll(entry.ranks);previous.airingRank=Math.min(previous.airingRank,entry.airingRank);}
                            added++;
                        }
                    }
                } catch (Exception e) { failure = e; }
            }
        }
        if (succeeded == 0) throw new IOException(failure == null ? "No supported catalogs" : "Catalog requests failed");
        return new ArrayList<>(out.values());
    }

    private static boolean requiredExtra(JSONObject catalog) {
        JSONArray extras = catalog.optJSONArray("extra");
        for (int i = 0; extras != null && i < extras.length(); i++) {
            JSONObject extra = extras.optJSONObject(i);
            if (extra != null && extra.optBoolean("isRequired")) return true;
        }
        return false;
    }

    static Entry parse(JSONObject o, boolean anime) {
        if (o == null || o.optBoolean("adult")) return null;
        String id = o.optString("id"), type = o.optString("type");
        if (!(id.matches("tt[0-9]+") || (anime && id.matches("kitsu:[0-9]+")))) return null;
        if (!("movie".equals(type) || "series".equals(type))) return null;
        JSONArray genres = o.optJSONArray("genres");
        if (genres == null) genres = o.optJSONArray("genre");
        for (int i = 0; genres != null && i < genres.length(); i++) {
            String genre = genres.optString(i);
            if (genre.matches("(?i)hentai|adult|pornography|erotica")) return null;
        }
        String image = o.optString("background");
        if (!https(image)) image = o.optString("poster");
        if (!https(image) || o.optString("name").trim().isEmpty()) return null;
        Entry e = new Entry();
        e.id = id;
        e.type = type;
        e.imdb = id.startsWith("tt") ? id : o.optString("imdb_id");
        if (!e.imdb.matches("tt[0-9]+")) e.imdb = "";
        e.tmdb = o.optInt("moviedb_id");
        e.title = o.optString("name");
        e.image = image;
        e.anime = anime;
        e.metadata=o;
        e.source = anime ? "Anime Kitsu" : "Streaming Catalogs";
        String year = o.optString("releaseInfo", o.optString("year"));
        e.meta = year.matches("[0-9]{4}.*") ? year.substring(0, 4) : "";
        return e;
    }

    static boolean https(String value) {
        try {
            URL url = new URL(value);
            return "https".equals(url.getProtocol()) && url.getHost().length() > 0 && url.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }

    static String detailLink(String type, String id) {
        if (!("movie".equals(type) || "series".equals(type)) || id == null
                || !(id.matches("tt[0-9]+") || id.matches("kitsu:[0-9]+"))) return null;
        return "stremio:///detail/" + type + "/" + id;
    }

    private static String segment(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("Catalog unavailable");
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    if (out.size() + n > 4 * 1024 * 1024) throw new IOException("Catalog too large");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { c.disconnect(); }
    }
}

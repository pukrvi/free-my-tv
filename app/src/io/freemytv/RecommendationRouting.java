package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Region-filtered subscription offers supplied by TMDB, ranked by the user's dock. */
final class RecommendationRouting {
    static final String[][] SERVICES = {
        {"netflix", "netflix", "com.netflix.ninja"},
        {"prime video", "prime", "com.amazon.amazonvideo.livingroom"},
        {"prime video", "prime", "com.amazon.firebat"},
        {"prime video", "prime", "com.amazon.avod"},
        {"disney", "disney", "com.disney.disneyplus"},
        {"hulu", "hulu", "com.hulu.livingroomplus"},
        {"hulu", "hulu", "com.hulu.plus"},
        {"paramount", "paramount", "com.cbs.ott"},
        {"paramount", "paramount", "com.cbs.ca"},      // Paramount+ outside the US (a Sony in the UK; Canada)
        {"peacock", "peacock", "com.peacocktv.peacockandroid"},
        {"peacock", "peacock", "com.peacock.peacockfiretv"},
        {"globoplay", "globoplay", "com.globo.globotv"},
        {"max", "max", "com.wbd.stream"},
        {"max", "max", "com.hbo.hbonow"},
        {"apple tv", "appletv", "com.apple.atve.androidtv.appletv"},
        {"apple tv", "appletv", "com.apple.atve.sony.appletv"},   // Sony's own build
        {"apple tv", "appletv", "com.apple.atve.amazon.appletv"},
        {"jio cinema", "jio", "com.jio.media.ondemand"},
        {"jiocinema", "jio", "com.jio.media.ondemand"},
        {"hotstar", "hotstar", "in.startv.hotstar"},   // TMDB: "JioHotstar" (earlier "Disney Plus Hotstar")
        {"crunchyroll", "crunchyroll", "com.crunchyroll.crunchyroid"},
        {"now tv", "now", "com.bskyb.nowtv.beta"},     // UK: Sky's NOW (TMDB: "Now TV", "Now TV Cinema")
        {"oneplay", "oneplay", "net.cme.voyo.cz.tvapp"},    // Czechia: Voyo and O2 TV became Oneplay, same app
        // Russia (TMDB's watch providers for RU)
        {"okko", "okko", "ru.more.play"},
        {"kinopoisk", "kinopoisk", "ru.kinopoisk.tv"},
        {"premier", "premier", "gpm.tnt_premier"},
        {"amediateka", "amediateka", "ru.amediateka"},
        {"more tv", "wink", "ru.rt.video.app.tv"},   // TMDB: "More TV" (more.tv is part of Rostelecom's Wink now)
    };

    static String[] choose(JSONArray offers, List<String> dock, Predicate<String> installed) {
        for (String pkg : dock) {
            if (!installed.test(pkg)) continue;
            for (String[] service : SERVICES) {
                if (!pkg.equals(service[2])) continue;
                for (int i = 0; offers != null && i < offers.length(); i++) {
                    JSONObject offer = offers.optJSONObject(i);
                    if (offer != null && matches(service, offer.optString("provider_name"), offer.optInt("provider_id"))) return service;
                }
            }
        }
        return null;
    }

    static boolean matches(String[] service, String name, int providerId) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        // These subscriptions belong to another app. Rental stores are not subscription availability.
        if (n.contains(" channel") || n.contains("(via") || n.contains(" store")) return false;
        if (service[1].equals("appletv") && providerId != 350) return false;
        return service[0].equals("max") ? n.matches(".*\\bmax\\b.*") : n.contains(service[0]);
    }
}

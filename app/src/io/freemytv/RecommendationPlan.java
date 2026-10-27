package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure twelve-row selector and refresh policy. No network, bitmaps, package queries or UI work. */
final class RecommendationPlan {
    static final int COUNT = 12;
    static final String[] IDS = {"for-me", "anime-airing", "new-movies", "new-shows", "trending", "indian", "sci-fi", "korean", "japanese", "oscars", "must-watch-movies", "must-watch-tv"};
    static final class Spec {
        final String id;
        int limit, visitReplace, idleReplace;
        long dataMs, idleMs;
        Spec(int row, int hours, int visit, int minutes, int idle) {
            id = IDS[row]; limit = row == 1 ? 20 : 10;
            dataMs = hours * 3600_000L; visitReplace = visit; idleMs = minutes * 60_000L; idleReplace = idle;
        }
    }
    final Spec[] specs = {
        new Spec(0,6,4,10,2), new Spec(1,12,0,0,0), new Spec(2,6,4,10,2), new Spec(3,6,4,10,2),
        new Spec(4,1,3,15,2), new Spec(5,24,3,30,2), new Spec(6,24,3,30,2), new Spec(7,24,3,30,2),
        new Spec(8,24,3,30,2), new Spec(9,168,2,60,1), new Spec(10,168,3,60,1), new Spec(11,168,3,60,1)
    };
    List<String> priority = Arrays.asList("netflix", "prime", "hotstar");
    int newDays = 90, recentDays = 365;
    long minimumIdleMs = 60_000;

    void configure(JSONObject profile) {
        JSONArray rows = profile.optJSONArray("rows");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            int index = index(row.optString("id")); if (index < 0) continue;
            Spec s = specs[index];
            s.limit = index == 1 ? 20 : Math.max(1, Math.min(20, row.optInt("limit",10)));
            s.visitReplace = Math.max(0,Math.min(s.limit,row.optInt("visitReplace",s.visitReplace)));
            s.idleReplace = Math.max(0,Math.min(s.limit,row.optInt("idleReplace",s.idleReplace)));
            s.idleMs = Math.max(1,row.optLong("idleMinutes",s.idleMs/60_000))*60_000L;
            s.dataMs = Math.max(1,row.optLong("dataHours",s.dataMs/3600_000))*3600_000L;
        }
        JSONArray apps = profile.optJSONArray("providerPriority"); List<String> order = new ArrayList<>();
        for (int i = 0; apps != null && i < apps.length(); i++) {
            String app=apps.optString(i); if (!app.isEmpty() && !order.contains(app)) order.add(app);
        }
        if (!order.isEmpty()) priority = Collections.unmodifiableList(order);
        newDays=Math.max(1,Math.min(365,profile.optInt("newReleaseDays",90)));
        recentDays=Math.max(newDays,Math.min(730,profile.optInt("recentReleaseDays",365)));
        minimumIdleMs=Math.max(60,profile.optLong("minimumIdleSeconds",60))*1000L;
    }
    static int index(String id) { for(int i=0;i<IDS.length;i++) if(IDS[i].equals(id))return i; return -1; }
    static String name(int row) {
        switch(row) {
            case 0:return Ui.tr("For Me"); case 1:return Ui.tr("Anime Airing"); case 2:return Ui.tr("New Movies");
            case 3:return Ui.tr("New Shows"); case 4:return Ui.tr("Trending"); case 5:return Ui.tr("Indian");
            case 6:return Ui.tr("Sci-Fi"); case 7:return Ui.tr("Korean"); case 8:return Ui.tr("Japanese");
            case 9:return Ui.tr("Oscar Winners"); case 10:return Ui.tr("Must-Watch Movies"); case 11:return Ui.tr("Must-Watch TV");
            default:return "";
        }
    }
    static long age(String value, LocalDate today) {
        try { if(value==null||value.length()<10)return Long.MAX_VALUE; return ChronoUnit.DAYS.between(LocalDate.parse(value.substring(0,10)),today); }
        catch(RuntimeException e){return Long.MAX_VALUE;}
    }
    static double quality(Recs.Pick p) { return (p.votes*p.rating/10.0+500*.65)/(p.votes+500.0); }
    static double taste(Recs.Pick p) {
        if(p.duneRelated)return 1;
        if(p.genres.contains("Science Fiction")||p.genres.contains("Sci-Fi")||p.genres.contains("Fantasy")||p.genres.contains("Sci-Fi & Fantasy"))return .9;
        return p.genres.contains("Adventure")||p.genres.contains("Action & Adventure")?.35:.1;
    }
    boolean eligible(Recs.Pick p,int row,LocalDate today) {
        long release=age(p.releaseDate,today), season=age(p.seasonDate,today);
        switch(row) {
            case 0:return release==Long.MAX_VALUE||release>=0;
            case 1:return "Anime Kitsu".equals(p.source) && p.anime && p.airingRank!=Integer.MAX_VALUE && p.feeds.contains("kitsu-anime-airing");
            case 2:return "movie".equals(p.kind)&&release>=0&&release<=recentDays;
            case 3:return "tv".equals(p.kind)&&((release>=0&&release<=newDays)||(season>=0&&season<=newDays));
            case 4:return p.feeds.contains("tmdb-trending-movie")||p.feeds.contains("tmdb-trending-tv");
            case 5:return p.countries.contains("IN");
            case 6:return p.genres.contains("Science Fiction")||p.genres.contains("Sci-Fi")||!Collections.disjoint(p.keywords,Arrays.asList("science fiction","space opera","cyberpunk","alien invasion","time travel","space travel"));
            case 7:return p.countries.contains("KR"); case 8:return p.countries.contains("JP");
            case 9:return "movie".equals(p.kind)&&p.oscarWinner;
            case 10:case 11:return (row==10?"movie":"tv").equals(p.kind)&&p.votes>=200&&quality(p)>=.75;
            default:return false;
        }
    }
    static String groupKey(Recs.Pick p) {
        return p.kind+":"+(p.id>0?"tmdb:"+p.id:!p.imdb.isEmpty()?p.imdb:p.externalId);
    }
    static boolean sameTitle(Recs.Pick a,Recs.Pick b) {
        if(!Objects.equals(a.kind,b.kind))return false;
        if(a.id>0&&b.id>0)return a.id==b.id;
        return !a.imdb.isEmpty()&&a.imdb.equals(b.imdb)||Recs.key(a).equals(Recs.key(b));
    }
    /** Aliases group a work while the original Kitsu entries remain available to the strict airing row. */
    static List<Recs.Pick> grouped(List<Recs.Pick> library) {
        Map<String,Set<Integer>> ids=new HashMap<>();
        for(Recs.Pick p:library)if(!p.imdb.isEmpty()&&p.id>0)ids.computeIfAbsent(p.kind+":"+p.imdb,k->new HashSet<>()).add(p.id);
        Map<String,Recs.Pick> groups=new LinkedHashMap<>();
        for(Recs.Pick p:library) {
            Set<Integer> linked=ids.get(p.kind+":"+p.imdb);
            int canonical=p.id>0?p.id:linked!=null&&linked.size()==1?linked.iterator().next():0;
            String key=p.kind+":"+(canonical>0?"tmdb:"+canonical:!p.imdb.isEmpty()&&(linked==null||linked.size()<=1)?p.imdb:p.externalId);
            Recs.Pick previous=groups.get(key);
            if(previous==null) { groups.put(key,copy(p)); continue; }
            merge(previous,p);
        }
        return new ArrayList<>(groups.values());
    }
    static Recs.Pick copy(Recs.Pick p) {
        Recs.Pick out=new Recs.Pick();
        out.id=p.id;out.source=p.source;out.externalId=p.externalId;out.externalType=p.externalType;out.imdb=p.imdb;
        out.kind=p.kind;out.title=p.title;out.backdrop=p.backdrop;out.meta=p.meta;out.app=p.app;out.pkg=p.pkg;out.link=p.link;
        out.detailed=p.detailed;out.anime=p.anime;out.photo=p.photo;out.feeds=p.feeds;out.genres=p.genres;out.countries=p.countries;
        out.keywords=p.keywords;out.ranks=p.ranks;out.releaseDate=p.releaseDate;out.seasonDate=p.seasonDate;out.year=p.year;
        out.rating=p.rating;out.votes=p.votes;out.airingRank=p.airingRank;out.oscarWinner=p.oscarWinner;out.duneRelated=p.duneRelated;
        return out;
    }
    static void merge(Recs.Pick out,Recs.Pick p) {
        Set<String> feeds=new LinkedHashSet<>(out.feeds);feeds.addAll(p.feeds);out.feeds=feeds;
        Set<String> genres=new HashSet<>(out.genres);genres.addAll(p.genres);out.genres=genres;
        Set<String> countries=new HashSet<>(out.countries);countries.addAll(p.countries);out.countries=countries;
        Set<String> keywords=new HashSet<>(out.keywords);keywords.addAll(p.keywords);out.keywords=keywords;
        Map<String,Double> ranks=new HashMap<>(out.ranks);for(Map.Entry<String,Double> r:p.ranks.entrySet())ranks.merge(r.getKey(),r.getValue(),Math::max);out.ranks=ranks;
        out.anime|=p.anime;out.oscarWinner|=p.oscarWinner;out.duneRelated|=p.duneRelated;
        if(out.id<=0)out.id=p.id;if(out.imdb.isEmpty())out.imdb=p.imdb;
        if(out.releaseDate.isEmpty())out.releaseDate=p.releaseDate;if(out.seasonDate.isEmpty())out.seasonDate=p.seasonDate;
        if(out.year.isEmpty())out.year=p.year;
        if(p.votes>out.votes){out.votes=p.votes;out.rating=p.rating;}
        if(p.pkg!=null&&(out.pkg==null||"stremio".equals(out.app)&&!"stremio".equals(p.app))){out.pkg=p.pkg;out.app=p.app;out.link=p.link;}
    }
    static double random(String key,long cycle) {
        long hash=0xcbf29ce484222325L^cycle;for(int i=0;i<key.length();i++)hash=(hash^key.charAt(i))*0x100000001b3L;
        return (hash>>>11)*0x1.0p-53;
    }
    static double score(Recs.Pick p,int row,long cycle,LocalDate today) {
        double trend=0;for(Map.Entry<String,Double> r:p.ranks.entrySet())if(row!=4||r.getKey().startsWith("tmdb-trending-"))trend=Math.max(trend,r.getValue());
        long a=age(p.releaseDate,today);double fresh=a>=0&&a!=Long.MAX_VALUE?Math.max(0,1-a/365.0):0;
        double value;
        if(row==0)value=.45*taste(p)+.25*quality(p)+.2*trend+.1*fresh+(p.duneRelated?.18:0);
        else if(row==2||row==3){if(row==3){long s=age(p.seasonDate,today);a=Math.min(a<0?Long.MAX_VALUE:a,s<0?Long.MAX_VALUE:s);fresh=a!=Long.MAX_VALUE?Math.max(0,1-a/90.0):0;}value=.6*fresh+.25*trend+.15*quality(p);}
        else if(row==4)value=trend;
        else value=.7*quality(p)+.3*trend;
        return value+.12*random(groupKey(p),cycle);
    }
    List<Recs.Pick>[] rank(List<Recs.Pick> library,long cycle,LocalDate today) {
        List<Recs.Pick> groups=grouped(library);
        @SuppressWarnings("unchecked") List<Recs.Pick>[] rows=new List[COUNT];
        Map<String,Integer> seen=new HashMap<>();
        for(int row=0;row<COUNT;row++) {
            final int r=row;List<Recs.Pick> pool=new ArrayList<>();
            for(Recs.Pick p:row==1?library:groups)if(eligible(p,row,today))pool.add(p);
            if(row==1){pool.sort(Comparator.comparingInt(p->p.airingRank));Set<String> entries=new HashSet<>();pool.removeIf(p->!entries.add(p.externalId));}
            else pool.sort((a,b)->{double av=score(a,r,cycle,today)-(r==4?0:.12*seen.getOrDefault(groupKey(a),0));double bv=score(b,r,cycle,today)-(r==4?0:.12*seen.getOrDefault(groupKey(b),0));return Double.compare(bv,av);});
            if(row>=9)pool=diversify(pool,cycle);
            if(row==0)pool=personalize(pool,cycle,today);
            rows[row]=pool;
            for(int i=0;i<Math.min(pool.size(),specs[row].limit);i++)seen.merge(groupKey(pool.get(i)),1,Integer::sum);
        }
        return rows;
    }
    private static List<Recs.Pick> personalize(List<Recs.Pick> ranked,long cycle,LocalDate today) {
        List<Recs.Pick> out=new ArrayList<>();
        for(int slot=0;slot<6;slot++){
            int added=0,target=slot==3||slot==4?1:2;
            for(Recs.Pick p:ranked){
                long release=age(p.releaseDate,today),season=age(p.seasonDate,today);
                boolean match=slot==0?p.anime:slot==1?!p.anime&&p.duneRelated:slot==2?!p.anime&&taste(p)>=.9&&quality(p)>=.7:
                    slot==3?"movie".equals(p.kind)&&release>=0&&release<=365:
                    slot==4?"tv".equals(p.kind)&&((release>=0&&release<=90)||(season>=0&&season<=90)):quality(p)>=.7;
                if(match&&!out.contains(p)){out.add(p);if(++added==target)break;}}
        }
        for(Recs.Pick p:ranked)if(!out.contains(p))out.add(p);
        return out;
    }
    private static List<Recs.Pick> diversify(List<Recs.Pick> ranked,long cycle) {
        List<Recs.Pick> remaining=new ArrayList<>(ranked),out=new ArrayList<>();Map<String,Integer> countries=new HashMap<>(),eras=new HashMap<>();
        while(!remaining.isEmpty()&&out.size()<30){Recs.Pick best=null;double bestScore=-Double.MAX_VALUE;
            for(int i=0;i<remaining.size();i++){Recs.Pick p=remaining.get(i);String country=p.countries.isEmpty()?"?":new TreeSet<>(p.countries).first(),era=p.year.length()>=3?p.year.substring(0,3):"?";
                double value=-i*.01-.14*countries.getOrDefault(country,0)-.1*eras.getOrDefault(era,0)+.08*random(country+era,cycle);
                if(value>bestScore){best=p;bestScore=value;}}
            remaining.remove(best);out.add(best);String country=best.countries.isEmpty()?"?":new TreeSet<>(best.countries).first(),era=best.year.length()>=3?best.year.substring(0,3):"?";countries.merge(country,1,Integer::sum);eras.merge(era,1,Integer::sum);}
        out.addAll(remaining);return out;
    }
    int replacements(int row,boolean visit,long now,long lastRotation,long lastInput,boolean focused,boolean blocked) {
        if(row==1||!visit&&(focused||blocked||now-lastInput<minimumIdleMs||now-lastRotation<specs[row].idleMs))return 0;
        return visit?specs[row].visitReplace:specs[row].idleReplace;
    }
    static List<Recs.Pick> rotate(List<Recs.Pick> old,List<Recs.Pick> ranked,int limit,int count,long cycle) {
        Map<String,Recs.Pick> available=new LinkedHashMap<>();for(Recs.Pick p:ranked)available.put(Recs.key(p),p);
        List<Recs.Pick> out=new ArrayList<>();Set<String> held=new HashSet<>();List<Integer> positions=new ArrayList<>();
        for(int i=0;i<Math.min(old.size(),limit);i++){Recs.Pick p=available.get(Recs.key(old.get(i)));if(p!=null&&held.add(Recs.key(p))){out.add(p);positions.add(i);}else out.add(null);}
        List<Recs.Pick> fresh=new ArrayList<>();for(Recs.Pick p:ranked)if(held.add(Recs.key(p)))fresh.add(p);
        int at=0;for(int i=0;i<out.size();i++)if(out.get(i)==null&&at<fresh.size())out.set(i,fresh.get(at++));
        while(out.size()<limit&&at<fresh.size())out.add(fresh.get(at++));
        positions.sort(Comparator.comparingDouble(i->random("slot:"+i,cycle)));
        for(int i=0;i<Math.min(count,positions.size())&&at<fresh.size();i++)out.set(positions.get(i),fresh.get(at++));
        out.removeIf(Objects::isNull);return out;
    }
}

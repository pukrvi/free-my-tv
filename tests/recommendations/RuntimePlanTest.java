package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/** Contract checks for the Android selector. All inputs are synthetic; no TV, network or private configuration. */
public final class RuntimePlanTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static int cases, assertions, failed;
    private interface Case { void run() throws Exception; }
    private static void run(String name, Case body) {
        cases++;
        try { body.run(); }
        catch (Throwable e) { failed++; System.err.println("FAIL " + name + ": " + e); }
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static Recs.Pick pick(int id, String kind) {
        Recs.Pick p = new Recs.Pick();
        p.id=id; p.kind=kind; p.imdb="tt"+id; p.title="Title "+id;
        p.source="TMDB"; p.externalId=p.imdb; p.externalType="movie".equals(kind)?"movie":"series";
        p.backdrop="https://images.example/"+id+".jpg";
        p.releaseDate="2010-06-01"; p.year="2010"; p.votes=5000; p.rating=8.4;
        p.genres.add("Drama"); p.countries.add("US");
        return p;
    }
    private static Recs.Pick airing(int entry, int rank) {
        Recs.Pick p=pick(100,"tv");
        p.source="Anime Kitsu"; p.anime=true; p.externalId="kitsu:"+entry;
        p.title="Season "+entry; p.airingRank=rank; p.feeds.add("kitsu-anime-airing");
        p.link=StremioCatalogs.detailLink("series",p.externalId); p.app="stremio";p.pkg="com.stremio.one";
        return p;
    }
    private static List<String> keys(List<Recs.Pick> list) {
        List<String> out=new ArrayList<>();for(Recs.Pick p:list)out.add(Recs.key(p));return out;
    }
    private static List<Recs.Pick> pool(int start,int count,String kind) {
        List<Recs.Pick> out=new ArrayList<>();for(int i=start;i<start+count;i++)out.add(pick(i,kind));return out;
    }
    private static boolean has(List<Recs.Pick> list,int id) {
        return list.stream().anyMatch(p->p.id==id);
    }
    private static int changed(List<Recs.Pick> before,List<Recs.Pick> after) {
        int n=0;for(int i=0;i<Math.min(before.size(),after.size());i++)if(!Recs.key(before.get(i)).equals(Recs.key(after.get(i))))n++;return n;
    }
    private static List<Recs.Pick> first(List<Recs.Pick> list,int count) {
        return new ArrayList<>(list.subList(0,Math.min(count,list.size())));
    }

    private static void profile() throws Exception {
        run("twelve distinct row IDs",()->{
            check(RecommendationPlan.COUNT==12,"the user's layout has twelve independent rows");
            check(new HashSet<>(Arrays.asList(RecommendationPlan.IDS)).size()==12,"row IDs must be unique");
            String[] expected={"for-me","anime-airing","new-movies","new-shows","trending","indian","sci-fi","korean","japanese","oscars","must-watch-movies","must-watch-tv"};
            check(Arrays.equals(RecommendationPlan.IDS,expected),"five main rows followed by seven Explore rows");
        });
        run("profile schema and runtime IDs agree",()->{
            JSONObject profile=new JSONObject(new String(Files.readAllBytes(Paths.get("documentation/recommendations/home-profile.json")),StandardCharsets.UTF_8));
            JSONArray rows=profile.getJSONArray("rows");RecommendationPlan plan=new RecommendationPlan();plan.configure(profile);
            check(rows.length()==12,"profile describes twelve rows");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);RecommendationPlan.Spec spec=plan.specs[i];
                check(spec.id.equals(row.getString("id")),"profile/runtime row order differs at "+i);
                check(spec.limit==row.getInt("limit"),"row capacity differs for "+spec.id);
                check(spec.dataMs==row.getLong("dataHours")*3600_000L,"data cadence differs for "+spec.id);
                check(spec.visitReplace==row.getInt("visitReplace"),"return replacement differs for "+spec.id);
                check(spec.idleReplace==row.getInt("idleReplace"),"idle replacement differs for "+spec.id);
                if(i!=1)check(spec.idleMs==row.getLong("idleMinutes")*60_000L,"idle cadence differs for "+spec.id);
            }
            check(plan.priority.equals(Arrays.asList("netflix","prime","hotstar")),"selected native app priority lost");
            check(plan.newDays==90&&plan.recentDays==365,"new/recent windows differ from the chosen profile");
            check(plan.minimumIdleMs==60_000,"remote idle grace must be sixty seconds");
        });
        run("unknown row IDs rejected",()->check(RecommendationPlan.index("not-a-row")==-1,"unknown row has no position"));
        run("profile clamps and preserves strict airing capacity",()->{
            RecommendationPlan p=new RecommendationPlan();
            p.configure(new JSONObject().put("minimumIdleSeconds",0).put("newReleaseDays",0).put("recentReleaseDays",9999)
                .put("rows",new JSONArray().put(new JSONObject().put("id","for-me").put("limit",999).put("visitReplace",999).put("idleReplace",999))
                    .put(new JSONObject().put("id","anime-airing").put("limit",1))));
            check(p.specs[0].limit==20&&p.specs[0].visitReplace==20&&p.specs[0].idleReplace==20,"replacement count cannot exceed capacity");
            check(p.specs[1].limit==20,"the Kitsu first page retains up to twenty entries");
            check(p.newDays==1&&p.recentDays==730,"release windows clamp to supported range");
            check(p.minimumIdleMs==60_000,"profile cannot remove idle grace");
        });
    }

    private static void identity() throws Exception {
        run("same explicit TMDB ID joins missing IMDb aliases",()->{
            Recs.Pick a=pick(1,"movie"),b=pick(1,"movie");b.imdb="";b.source="Streaming Catalogs";
            check(RecommendationPlan.grouped(Arrays.asList(a,b)).size()==1,"same TMDB movie must remain one card even if one source lacks IMDb");
        });
        run("same explicit TMDB ID takes precedence over different IMDb strings",()->{
            Recs.Pick a=pick(1,"movie"),b=pick(1,"movie");b.imdb="tt999";
            check(RecommendationPlan.grouped(Arrays.asList(a,b)).size()==1,"a shared explicit TMDB identity must not create duplicate cards");
        });
        run("movie and TV namespaces remain separate",()->check(RecommendationPlan.grouped(Arrays.asList(pick(1,"movie"),pick(1,"tv"))).size()==2,"a film and series are different works"));
        run("same title and year cannot silently merge remakes",()->{
            Recs.Pick a=pick(1,"movie"),b=pick(2,"movie");b.title=a.title;b.year=a.year;
            check(RecommendationPlan.grouped(Arrays.asList(a,b)).size()==2,"title/year matching is only a review hint");
        });
        run("conflicting explicit TMDB mappings are not joined by IMDb",()->{
            Recs.Pick a=pick(1,"tv"),b=pick(2,"tv"),unresolved=pick(0,"tv");b.imdb=a.imdb;unresolved.imdb=a.imdb;unresolved.externalId="kitsu:9";unresolved.source="Anime Kitsu";
            check(RecommendationPlan.grouped(Arrays.asList(a,b,unresolved)).size()==3,"ambiguous shared IMDb must not choose one conflicting TV work");
        });
        run("many Kitsu seasons group as one broad work",()->check(RecommendationPlan.grouped(Arrays.asList(airing(10,0),airing(11,1))).size()==1,"broad rows group the work while airing retains editions"));
        run("grouping retains both feed memberships and genre evidence",()->{
            Recs.Pick a=pick(1,"movie"),b=pick(1,"movie");a.feeds.add("tmdb-trending-movie");b.feeds.add("nfx-movie");b.genres.add("Science Fiction");
            Recs.Pick result=RecommendationPlan.grouped(Arrays.asList(a,b)).get(0);
            check(result.feeds.containsAll(Arrays.asList("tmdb-trending-movie","nfx-movie")),"initial and corroborating source membership both survive");
            check(result.genres.contains("Science Fiction"),"explicit genre evidence survives joining");
        });
        run("metadata survives the actual persisted JSON representation",()->{
            Recs.Pick a=airing(10,7);a.countries.add("JP");a.keywords.add("time travel");a.ranks.put("kitsu-anime-airing",.8);a.seasonDate="2026-10-01";a.oscarWinner=true;a.duneRelated=true;
            Recs.Pick b=Recs.parse(new JSONArray().put(Recs.encode(a))).get(0);
            check(b.feeds.equals(a.feeds)&&b.genres.equals(a.genres)&&b.countries.equals(a.countries)&&b.keywords.equals(a.keywords)&&b.ranks.equals(a.ranks),"saved metadata loses matching evidence");
            check(b.releaseDate.equals(a.releaseDate)&&b.seasonDate.equals(a.seasonDate)&&b.year.equals(a.year),"saved date fields lose precision");
            check(b.airingRank==7&&b.rating==a.rating&&b.votes==a.votes&&b.oscarWinner&&b.duneRelated,"saved ranking and award evidence lost");
            check(b.link.equals(a.link)&&b.externalId.equals("kitsu:10"),"saved exact season route lost");
        });
        run("old cached records stay unknown rather than gaining false eligibility",()->{
            Recs.Pick p=Recs.parse(new JSONArray().put(new JSONObject().put("id",1).put("kind","tv").put("title","Old cache"))).get(0);
            check(p.feeds.isEmpty()&&p.countries.isEmpty()&&p.ranks.isEmpty()&&p.airingRank==Integer.MAX_VALUE,"unknown cached evidence must have conservative defaults");
            RecommendationPlan plan=new RecommendationPlan();check(!plan.eligible(p,1,TODAY)&&!plan.eligible(p,3,TODAY)&&!plan.eligible(p,5,TODAY),"legacy cache must not manufacture airing, recency or origin membership");
        });
        run("copy is independent and retains matching metadata",()->{
            Recs.Pick a=airing(10,7);a.countries.add("JP");a.keywords.add("time travel");a.ranks.put("kitsu-anime-airing",.8);a.seasonDate="2026-10-01";a.oscarWinner=true;a.duneRelated=true;
            Recs.Pick b=RecommendationPlan.copy(a);
            check(b.feeds.equals(a.feeds)&&b.genres.equals(a.genres)&&b.countries.equals(a.countries)&&b.keywords.equals(a.keywords)&&b.ranks.equals(a.ranks),"cache round-trip loses feed/genre/country/keyword/rank evidence");
            check(b.releaseDate.equals(a.releaseDate)&&b.seasonDate.equals(a.seasonDate)&&b.year.equals(a.year),"date precision and year survive");
            check(b.airingRank==7&&b.rating==a.rating&&b.votes==a.votes&&b.oscarWinner&&b.duneRelated,"ranking and award flags survive");
            check(b.link.equals(a.link)&&b.externalId.equals("kitsu:10"),"exact edition launch route survives");
            Recs.Pick extra=pick(100,"tv");extra.feeds.add("new");RecommendationPlan.merge(b,extra);
            check(b.feeds.contains("new")&&!a.feeds.contains("new"),"merging copied metadata must not mutate the original held record");
        });
    }

    private static void catalogContract() throws Exception {
        run("non-Kitsu Animation metadata is not anime provenance",()->{
            JSONObject raw=new JSONObject().put("id","tt9").put("type","movie").put("name","Western cartoon").put("background","https://images.example/9.jpg").put("genres",new JSONArray().put("Animation"));
            StremioCatalogs.Entry entry=StremioCatalogs.parse(raw,false);
            check(entry!=null&&!entry.anime,"broad Animation from Streaming Catalogs cannot consume an anime recipe slot");
        });
        run("catalog client retains every usable first-page airing entry",()->{
            StremioCatalogs client=new StremioCatalogs(url->{
                if(url.endsWith("manifest.json"))return new JSONObject().put("id","community.anime.kitsu").put("catalogs",new JSONArray()
                    .put(new JSONObject().put("id","kitsu-anime-trending").put("type","anime"))
                    .put(new JSONObject().put("id","kitsu-anime-airing").put("type","anime")));
                JSONArray entries=new JSONArray();boolean airing=url.contains("airing");
                for(int i=1;i<=(airing?20:50);i++)entries.put(new JSONObject().put("id","kitsu:"+(airing?i:100+i)).put("type","series").put("name","Anime "+i).put("background","https://images.example/"+i+".jpg"));
                return new JSONObject().put("metas",entries);
            });
            Set<String> returned=new HashSet<>();for(StremioCatalogs.Entry entry:client.anime())returned.add(entry.id);
            for(int i=1;i<=20;i++)check(returned.contains("kitsu:"+i),"six-per-feed sample cap dropped airing season "+i);
        });
    }

    private static void eligibility() throws Exception {
        RecommendationPlan plan=new RecommendationPlan();
        run("airing requires the strict Kitsu feed",()->{
            Recs.Pick a=airing(10,0);check(plan.eligible(a,1,TODAY),"Kitsu top-airing entry eligible");
            a.feeds.clear();a.feeds.add("kitsu-anime-trending");check(!plan.eligible(a,1,TODAY),"Kitsu trending is not airing membership");
        });
        run("airing rejects a non-Kitsu record carrying copied flags",()->{
            Recs.Pick a=airing(10,0);a.source="Streaming Catalogs";
            check(!plan.eligible(a,1,TODAY),"a grouped/foreign record must not replace an original airing season entry");
        });
        run("all twenty airing editions retain source order",()->{
            List<Recs.Pick> library=new ArrayList<>();for(int i=19;i>=0;i--)library.add(airing(100+i,i));
            List<Recs.Pick> result=plan.rank(library,5,TODAY)[1];
            check(result.size()==20,"twenty season entries must not collapse to one shared IMDb show");
            for(int i=0;i<20;i++){check(result.get(i).externalId.equals("kitsu:"+(100+i)),"airing rank or exact season changed at "+i);check(result.get(i).link.endsWith("kitsu:"+(100+i)),"airing launch lost season "+i);}
            check(keys(result).equals(keys(plan.rank(library,19,TODAY)[1])),"visit seed cannot randomize airing");
        });
        run("short airing feed is not filled from other catalogs",()->{
            Recs.Pick a=airing(1,0),other=pick(200,"tv");other.anime=true;
            List<Recs.Pick> result=plan.rank(Arrays.asList(a,other),0,TODAY)[1];check(result.size()==1&&result.get(0).externalId.equals(a.externalId),"no filler may enter Kitsu Airing");
        });
        run("duplicate exact Kitsu edition appears only once in airing",()->{
            Recs.Pick a=airing(1,0),duplicate=airing(1,0);
            check(plan.rank(Arrays.asList(a,duplicate),0,TODAY)[1].size()==1,"duplicate observations of one Kitsu edition must not occupy two cards");
        });
        run("new movie exact past date boundary",()->{
            Recs.Pick p=pick(1,"movie");p.releaseDate=TODAY.minusDays(365).toString();check(plan.eligible(p,2,TODAY),"inclusive recent release boundary");
            p.releaseDate=TODAY.minusDays(366).toString();check(!plan.eligible(p,2,TODAY),"old movie cannot be made new by trend rank");
            p.releaseDate=TODAY.toString();check(plan.eligible(p,2,TODAY),"released today qualifies");
        });
        run("unknown, year-only and future dates are not new",()->{
            for(String d:Arrays.asList("","2026","2026-","2026-02-30","2027-01-01")){Recs.Pick p=pick(1,"movie");p.releaseDate=d;check(!plan.eligible(p,2,TODAY),"not a known past release: "+d);}
        });
        run("old show with a recent season qualifies",()->{
            Recs.Pick p=pick(1,"tv");p.seasonDate=TODAY.minusDays(90).toString();check(plan.eligible(p,3,TODAY),"inclusive season premiere boundary");
            p.seasonDate=TODAY.minusDays(91).toString();check(!plan.eligible(p,3,TODAY),"older season does not qualify");
        });
        run("new show type and premiere rules",()->{
            Recs.Pick p=pick(1,"tv");p.releaseDate=TODAY.minusDays(30).toString();check(plan.eligible(p,3,TODAY),"recent series premiere qualifies");
            p.kind="movie";check(!plan.eligible(p,3,TODAY),"movie must not enter New Shows");
        });
        run("new movies cannot be a TV adaptation",()->{Recs.Pick p=pick(1,"tv");p.releaseDate=TODAY.toString();check(!plan.eligible(p,2,TODAY),"New Movies contains films only");});
        run("For Me excludes known unreleased but allows unknown metadata",()->{
            Recs.Pick p=pick(1,"movie");p.releaseDate=TODAY.plusDays(1).toString();check(!plan.eligible(p,0,TODAY),"known future work must not become a watch-now pick");
            p.releaseDate="";check(plan.eligible(p,0,TODAY),"unknown date may remain a broad discovery");
        });
        run("Trending membership is TMDB-specific",()->{
            Recs.Pick p=pick(1,"movie");p.feeds.add("nfx-movie");p.ranks.put("nfx-movie",1.0);check(!plan.eligible(p,4,TODAY),"provider trending cannot add membership");
            p.feeds.add("tmdb-trending-movie");check(plan.eligible(p,4,TODAY),"TMDB movie trending member");
            p.feeds.clear();p.feeds.add("tmdb-trending-tv");p.kind="tv";check(plan.eligible(p,4,TODAY),"TMDB TV trending member");
        });
        run("country shelves require origin evidence",()->{
            Recs.Pick p=pick(1,"movie");check(!plan.eligible(p,5,TODAY),"US origin is not Indian because the TV is in India");
            p.countries.add("IN");check(plan.eligible(p,5,TODAY),"Indian co-production qualifies");
            check(!plan.eligible(p,7,TODAY)&&!plan.eligible(p,8,TODAY),"unrecorded Korean/Japanese origin stays unknown");
            p.countries.add("KR");p.countries.add("JP");check(plan.eligible(p,7,TODAY)&&plan.eligible(p,8,TODAY),"explicit co-production origins preserved");
        });
        run("animation alone neither anime nor Japanese",()->{
            Recs.Pick p=pick(1,"movie");p.genres.add("Animation");check(!p.anime&&!plan.eligible(p,8,TODAY)&&!plan.eligible(p,1,TODAY),"broad Animation is not anime or Japanese origin");
        });
        run("strict sci-fi needs more than combined TV fantasy",()->{
            Recs.Pick p=pick(1,"tv");p.genres.add("Sci-Fi & Fantasy");check(!plan.eligible(p,6,TODAY),"generic fantasy alone cannot claim science fiction");
            p.keywords.add("time travel");check(plan.eligible(p,6,TODAY),"explicit science-fiction keyword qualifies");
            p.keywords.clear();p.genres.add("Science Fiction");check(plan.eligible(p,6,TODAY),"explicit science-fiction genre qualifies");
        });
        run("verified Oscar flag is film-only",()->{
            Recs.Pick p=pick(1,"movie");check(!plan.eligible(p,9,TODAY),"rating alone is not an Oscar assertion");p.oscarWinner=true;check(plan.eligible(p,9,TODAY),"validated winning film eligible");
            p.kind="tv";check(!plan.eligible(p,9,TODAY),"TV cannot become an Oscar winner shelf film");
        });
        run("must-watch vote and shrunk-quality floors",()->{
            Recs.Pick p=pick(1,"movie");check(plan.eligible(p,10,TODAY),"well-reviewed film qualifies");check(!plan.eligible(p,11,TODAY),"film cannot enter must-watch TV");
            p.votes=2;p.rating=10;check(!plan.eligible(p,10,TODAY),"tiny sample must not outrank established quality");
            p.votes=0;p.rating=9.9;check(Math.abs(RecommendationPlan.quality(p)-.65)<1e-9,"no TMDB votes uses neutral prior, not the Kitsu score");
        });
        run("unknown shelf ineligible",()->check(!plan.eligible(pick(1,"movie"),99,TODAY),"unsupported shelf must not accept all records"));
    }

    private static void ranking() throws Exception {
        run("same visit produces deterministic rankings",()->{
            List<Recs.Pick> input=pool(1,40,"movie");for(Recs.Pick p:input){p.feeds.add("tmdb-trending-movie");p.ranks.put("tmdb-trending-movie",1.0-p.id/40.0);}
            RecommendationPlan plan=new RecommendationPlan();List<Recs.Pick>[] a=plan.rank(input,9,TODAY),b=plan.rank(input,9,TODAY);
            for(int row=0;row<12;row++)check(keys(a[row]).equals(keys(b[row])),"same visit moved shelf "+row);
        });
        run("For Me reserves recent movie and show when all slot pools exist",()->{
            List<Recs.Pick> library=new ArrayList<>();
            for(int i=1;i<=2;i++){Recs.Pick p=airing(i,i);p.id=100+i;p.imdb="tt"+(100+i);library.add(p);}
            for(int i=3;i<=4;i++){Recs.Pick p=pick(i,"movie");p.duneRelated=true;library.add(p);}
            for(int i=5;i<=6;i++){Recs.Pick p=pick(i,"tv");p.genres.add("Fantasy");library.add(p);}
            for(int i=7;i<=12;i++)library.add(pick(i,"movie"));
            Recs.Pick recentMovie=pick(50,"movie");recentMovie.releaseDate=TODAY.minusDays(5).toString();recentMovie.rating=6;recentMovie.votes=1;library.add(recentMovie);
            Recs.Pick recentShow=pick(51,"tv");recentShow.releaseDate=TODAY.minusDays(5).toString();recentShow.rating=6;recentShow.votes=1;library.add(recentShow);
            List<Recs.Pick> visible=first(new RecommendationPlan().rank(library,0,TODAY)[0],10);
            check(has(visible,50)&&has(visible,51),"the chosen recipe requires one recent film and one recent/new-season show, not only quality backfill");
            check(visible.stream().filter(p->p.anime).count()>=2,"two anime discoveries retain space");
            check(visible.stream().filter(p->p.duneRelated).count()>=2,"Dune-related discoveries retain space");
        });
        run("quality rows retain country and era variety",()->{
            List<Recs.Pick> input=new ArrayList<>();String[] countries={"US","JP","IN","KR"};String[] years={"1972","1999","2008","2021"};
            for(int i=1;i<=32;i++){Recs.Pick p=pick(i,"movie");p.countries.clear();p.countries.add(countries[i%4]);p.year=years[(i/4)%4];input.add(p);}
            List<Recs.Pick> visible=first(new RecommendationPlan().rank(input,1,TODAY)[10],10);Set<String> countriesSeen=new HashSet<>(),eras=new HashSet<>();
            for(Recs.Pick p:visible){countriesSeen.addAll(p.countries);eras.add(p.year.substring(0,3));}
            check(countriesSeen.size()>1&&eras.size()>1,"the all-time row must not collapse to one country or decade");
        });
    }

    private static void refresh() throws Exception {
        RecommendationPlan plan=new RecommendationPlan();
        run("Home return replaces only configured few",()->{
            check(plan.replacements(0,true,0,0,0,false,false)==4,"For Me return budget");
            check(plan.replacements(4,true,0,0,0,false,false)==3,"Trending return budget");
            check(plan.replacements(9,true,0,0,0,false,false)==2,"Oscar return budget");
        });
        run("idle ten-minute rows and strict airing",()->{
            for(int row:new int[]{0,2,3})check(plan.replacements(row,false,600_000,0,0,false,false)==2,"ten-minute row budget "+row);
            check(plan.replacements(1,true,600_000,0,0,false,false)==0&&plan.replacements(1,false,99_000_000,0,0,false,false)==0,"airing never randomizes on return or idle");
        });
        run("section-specific idle thresholds",()->{
            check(plan.replacements(4,false,600_000,0,0,false,false)==0,"Trending waits fifteen minutes");
            check(plan.replacements(4,false,900_000,0,0,false,false)==2,"Trending fifteen-minute budget");
            check(plan.replacements(5,false,900_000,0,0,false,false)==0,"country row waits thirty minutes");
            check(plan.replacements(5,false,1800_000,0,0,false,false)==2,"country thirty-minute budget");
            check(plan.replacements(9,false,1800_000,0,0,false,false)==0,"awards waits sixty minutes");
            check(plan.replacements(9,false,3600_000,0,0,false,false)==1,"awards sixty-minute budget");
        });
        run("recent remote input defers idle changes",()->check(plan.replacements(0,false,600_000,0,599_999,false,false)==0,"remote activity needs sixty quiet seconds"));
        run("focused row is frozen during idle",()->check(plan.replacements(0,false,600_000,0,0,true,false)==0,"keep focused title, slot and neighbors stable"));
        run("active menu defers idle changes",()->check(plan.replacements(0,false,600_000,0,0,false,true)==0,"do not shuffle behind menus"));
        run("elapsed clock rollback does not cause catch-up",()->check(plan.replacements(0,false,100,600_000,0,false,false)==0,"negative elapsed difference must hold"));
        run("replacement threshold is inclusive",()->{
            check(plan.replacements(0,false,599_999,0,0,false,false)==0,"not before threshold");
            check(plan.replacements(0,false,600_000,0,540_000,false,false)==2,"at threshold with exactly sixty idle seconds");
        });
        run("partial refresh changes only budgeted slots",()->{
            List<Recs.Pick> old=pool(1,10,"movie"),ranked=pool(1,30,"movie");List<Recs.Pick> next=RecommendationPlan.rotate(old,ranked,10,2,8);
            check(next.size()==10&&changed(old,next)==2,"two-card idle budget must not replace the entire row");
            check(new HashSet<>(keys(next)).size()==10,"new cards must remain unique");
        });
        run("small pool retains exact order",()->{
            List<Recs.Pick> old=pool(1,5,"movie"),reverse=new ArrayList<>(old);Collections.reverse(reverse);
            check(keys(RecommendationPlan.rotate(old,reverse,10,4,3)).equals(keys(old)),"no unseen alternatives means no manufactured churn");
        });
        run("empty row fills within capacity",()->check(RecommendationPlan.rotate(Collections.emptyList(),pool(1,30,"movie"),10,4,2).size()==10,"initial population uses row capacity, not return replacement budget"));
        run("single title cannot duplicate to fill row",()->{
            List<Recs.Pick> input=Arrays.asList(pick(1,"movie"),pick(1,"movie"));List<Recs.Pick> out=RecommendationPlan.rotate(Collections.emptyList(),input,10,4,2);
            check(out.size()==1,"one identity must occupy one slot");
        });
        run("removed candidate cannot shift other held slot positions",()->{
            List<Recs.Pick> old=pool(1,5,"movie"),ranked=pool(2,7,"movie");List<Recs.Pick> out=RecommendationPlan.rotate(old,ranked,5,0,0);
            check(out.size()==5,"ineligible slot refilled");
            for(int i=1;i<5;i++)check(out.get(i).id==old.get(i).id,"held title at slot "+i+" moved after an earlier title became ineligible");
        });
        run("empty candidate pool produces no stale titles",()->check(RecommendationPlan.rotate(pool(1,5,"movie"),Collections.emptyList(),10,2,1).isEmpty(),"no stale ineligible title may remain"));
        run("zero replacement budget enriches without reordering",()->{
            List<Recs.Pick> old=pool(1,5,"movie"),ranked=pool(1,10,"movie");Collections.reverse(ranked);
            check(keys(RecommendationPlan.rotate(old,ranked,5,0,1)).equals(keys(old)),"metadata/new ranking alone must not shuffle held entries");
        });
    }
    public static void main(String[] args) throws Exception {
        profile();identity();catalogContract();eligibility();ranking();refresh();
        System.out.println((cases-failed)+"/"+cases+" runtime plan cases passed ("+assertions+" assertions; "+failed+" failing cases).");
        if(failed>0)throw new AssertionError(failed+" runtime plan contract cases failed");
    }
}

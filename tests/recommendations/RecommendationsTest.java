package io.freemytv;
import java.util.*;
import org.json.*;

public final class RecommendationsTest {
    private static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static JSONObject meta(String id,String type,String title) throws Exception {
        return new JSONObject().put("id",id).put("type",type).put("name",title).put("background","https://images.example/art.jpg");
    }
    static Recs.Pick pick(String source,String id,int tmdb,String imdb,String kind){
        Recs.Pick p=new Recs.Pick();p.source=source;p.externalId=id;p.externalType=kind.equals("movie")?"movie":"series";
        p.id=tmdb;p.imdb=imdb;p.kind=kind;p.title=id;p.backdrop="https://images.example/a.jpg";return p;
    }
    static void routing() throws Exception {
        JSONArray offers=new JSONArray().put(new JSONObject().put("provider_name","Amazon Prime Video").put("provider_id",119))
            .put(new JSONObject().put("provider_name","Netflix").put("provider_id",8));
        List<String> dock=Arrays.asList("com.netflix.ninja","com.amazon.amazonvideo.livingroom");
        check(RecommendationRouting.choose(offers,dock,p->true)[1].equals("netflix"),"dock beats API ordering");
        check(RecommendationRouting.choose(offers,Arrays.asList(dock.get(1),dock.get(0)),p->true)[1].equals("prime"),"reordered dock wins");
        check(RecommendationRouting.choose(offers,dock,p->!p.equals(dock.get(0)))[1].equals("prime"),"missing/hidden service skipped");
        check(RecommendationRouting.choose(offers,Collections.emptyList(),p->true)==null,"installed but off dock excluded");
        check(RecommendationRouting.choose(null,dock,p->true)==null,"empty region falls back");
        offers=new JSONArray().put(new JSONObject().put("provider_name","JioHotstar").put("provider_id",2336));
        check(RecommendationRouting.choose(offers,Arrays.asList("in.startv.hotstar"),p->true)[1].equals("hotstar"),"JioHotstar matching");
        offers=new JSONArray().put(new JSONObject().put("provider_name","Prime  Video").put("provider_id",119));
        check(RecommendationRouting.choose(offers,Arrays.asList("com.amazon.firebat"),p->true)[2].equals("com.amazon.firebat"),"alternate dock package preserved");
        offers=new JSONArray().put(new JSONObject().put("provider_name","Apple TV Amazon Channel").put("provider_id",350))
            .put(new JSONObject().put("provider_name","Apple TV Store").put("provider_id",2));
        check(RecommendationRouting.choose(offers,Arrays.asList("com.apple.atve.androidtv.appletv"),p->true)==null,"channels and rentals don't imply native subscription");
    }
    static void parser() throws Exception {
        StremioCatalogs.Entry e=StremioCatalogs.parse(meta("kitsu:7442","series","Attack on Titan").put("imdb_id","tt2560140"),true);
        check(e!=null&&e.id.equals("kitsu:7442")&&e.imdb.equals("tt2560140")&&e.anime,"Kitsu identity retained");
        check(StremioCatalogs.parse(meta("kitsu:1","series","Anime"),false)==null,"only approved Kitsu feed accepts Kitsu IDs");
        check(StremioCatalogs.parse(meta("tt1","anime","Bad type"),true)==null,"reject unsupported metadata types");
        check(StremioCatalogs.parse(meta("tt1","movie","Adult").put("adult",true),false)==null,"adult flag excluded");
        check(StremioCatalogs.parse(meta("kitsu:1","series","Adult").put("genres",new JSONArray().put("Hentai")),true)==null,"adult genre excluded");
        check(StremioCatalogs.parse(meta("tt1","movie","Title").put("background","http://example.com/a.jpg"),false)==null,"require HTTPS artwork");
        check(StremioCatalogs.parse(meta("tt1","movie","Title").put("background",JSONObject.NULL).put("poster","https://example.com/poster.jpg"),false)!=null,"poster fallback");
        check(StremioCatalogs.parse(meta("tt1","movie","  "),false)==null,"empty title rejected");
        check(StremioCatalogs.detailLink("series","kitsu:7442").equals("stremio:///detail/series/kitsu:7442"),"exact anime detail link");
        check(StremioCatalogs.detailLink("movie","tt1").equals("stremio:///detail/movie/tt1"),"exact movie detail link");
        check(StremioCatalogs.detailLink("tv","tt1")==null&&StremioCatalogs.detailLink("series","../secret")==null,"invalid link identity rejected");
    }
    static void feeds() throws Exception {
        List<String> requested=new ArrayList<>();
        StremioCatalogs client=new StremioCatalogs(url->{
            requested.add(url);
            if(url.endsWith("manifest.json"))return new JSONObject().put("id","community.anime.kitsu").put("catalogs",new JSONArray()
                .put(new JSONObject().put("id","kitsu-anime-trending").put("type","anime"))
                .put(new JSONObject().put("id","kitsu-anime-airing").put("type","anime")));
            if(url.contains("airing"))throw new java.io.IOException("one feed down");
            JSONArray entries=new JSONArray();for(int i=1;i<=20;i++)entries.put(meta("kitsu:"+i,"series","Anime "+i));
            entries.put(meta("kitsu:1","series","duplicate"));return new JSONObject().put("metas",entries);
        });
        check(client.anime().size()==20,"bounded sample survives second catalog failure");
        check(requested.size()==3&&requested.stream().noneMatch(x->x.contains("/stream/")),"discovery only");
        StremioCatalogs invalid=new StremioCatalogs(url->new JSONObject().put("id","other.addon"));
        boolean failed=false;try{invalid.anime();}catch(Exception e){failed=true;}check(failed,"wrong addon rejected");
        requested.clear();
        StremioCatalogs streaming=new StremioCatalogs(url->{
            requested.add(url);
            if(url.endsWith("manifest.json"))return new JSONObject().put("id","pw.ers.netflix-catalog").put("catalogs",new JSONArray()
                .put(new JSONObject().put("id","jhs").put("type","series"))
                .put(new JSONObject().put("id","jhs").put("type","movie").put("extra",new JSONArray().put(new JSONObject().put("name","search").put("isRequired",true)))));
            return new JSONObject().put("metas",new JSONArray().put(meta("tt99","series","Show")));
        });
        check(streaming.streaming(Arrays.asList("hotstar")).size()==1,"configured JioHotstar catalog supported");
        check(requested.size()==3&&requested.get(1).contains("/amhzOjo%3D/"),"configured manifest uses selected services");
        check(requested.stream().noneMatch(x->x.contains("catalog/movie")),"required search catalog skipped");
        requested.clear();check(streaming.streaming(Arrays.asList("unknown")).isEmpty()&&requested.isEmpty(),"unknown service sends no request");
    }
    static void cacheAndBlend() throws Exception {
        Recs.Pick k=pick("Anime Kitsu","kitsu:7442",0,"tt2560140","tv");k.anime=true;k.app="stremio";k.pkg="com.stremio.one";k.link=StremioCatalogs.detailLink(k.externalType,k.externalId);k.detailed=true;
        Recs.Pick saved=Recs.parse(new JSONArray().put(Recs.encode(k))).get(0);
        check(Recs.key(k).equals(Recs.key(saved))&&saved.link.equals(k.link)&&saved.anime&&saved.detailed,"cache retains exact route, type and source");
        Recs.Pick other=pick("Anime Kitsu","kitsu:12",0,"","tv");
        check(!Recs.key(k).equals(Recs.key(other)),"no numeric-zero collision");
        Recs.Pick t=pick("TMDB","",1429,"tt2560140","tv");String stable=Recs.key(t);t.externalId="tt2560140";
        check(stable.equals(Recs.key(t)),"metadata lookup cannot change TMDB focus identity");
        check(Recs.blend(Arrays.asList(Arrays.asList(t),Arrays.asList(k))).size()==1,"IMDb duplicate across sources merged");
        Recs.Pick stream=pick("Streaming Catalogs","tt10",1429,"","tv");
        check(Recs.blend(Arrays.asList(Arrays.asList(t),Arrays.asList(stream))).size()==1,"TMDB alias duplicate merged");
        List<Recs.Pick> a=new ArrayList<>(),b=new ArrayList<>(),c=new ArrayList<>();
        for(int i=1;i<=50;i++){a.add(pick("TMDB","",i,"","movie"));b.add(pick("Anime Kitsu","kitsu:"+i,0,"","tv"));c.add(pick("Streaming Catalogs","tt"+i,1000+i,"","movie"));}
        List<Recs.Pick> mixed=Recs.blend(Arrays.asList(a,b,c));
        check(mixed.size()==150,"bounded pool");
        for(String source:Arrays.asList("TMDB","Anime Kitsu","Streaming Catalogs"))check(mixed.stream().filter(x->x.source.equals(source)).count()==50,"balanced source representation");
        Recs.Pick duplicate=pick("TMDB","",1,"","movie");
        check(Recs.blend(Arrays.asList(Arrays.asList(a.get(0),duplicate,a.get(1)))).size()==2,"duplicate doesn't terminate later picks");
    }
    public static void main(String[] args)throws Exception{routing();parser();feeds();cacheAndBlend();System.out.println(checks+" recommendation checks passed.");}
}

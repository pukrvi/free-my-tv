import unittest
from collections import Counter
from datetime import date
from tools.recommendation_plan import (merge_observations, load_profile, eligible, quality, build_plan, route,
                                       refresh_decision, partial_refresh, data_fetch_due)

TODAY = date(2026, 10, 27)

def obs(title='Dune', kind='movie', tmdb=1, imdb='tt1', source='TMDB', feed='tmdb-trending-movie-week', **kw):
    o = dict(title=title, kind=kind, ids={'tmdb': tmdb, 'imdb': imdb}, source=source, feed=feed,
             rank=0, feedSize=20, year='2021', genres=['Science Fiction'], countries=['US'],
             rating=8.4, votes=5000, ratingSource='TMDB', releaseDate='2021-10-01')
    o.update(kw)
    return o

def title(**kw):
    return merge_observations([obs(**kw)])[0][0]

class MatchingTests(unittest.TestCase):
    def test_exact_cross_source_merge(self):
        lib, conflicts = merge_observations([obs(), obs(source='Streaming Catalogs', feed='nfx-movie')])
        self.assertEqual(len(lib), 1)
        self.assertEqual(set(lib[0]['sources']), {'TMDB', 'Streaming Catalogs'})
        self.assertFalse(conflicts)

    def test_type_namespaces(self):
        self.assertEqual(len(merge_observations([obs(), obs(kind='tv')])[0]), 2)

    def test_title_alone_never_merges(self):
        self.assertEqual(len(merge_observations([obs(), obs(tmdb=2, imdb='tt2')])[0]), 2)

    def test_conflicting_tmdb_alias_rejected(self):
        lib, conflicts = merge_observations([obs(), obs(tmdb=2)])
        self.assertEqual(len(lib), 2)
        self.assertTrue(conflicts)

    def test_unidentified_records_not_guessed(self):
        self.assertEqual(merge_observations([obs(ids={})])[0], [])

    def test_enrichment_is_not_discovery(self):
        lib, _ = merge_observations([obs(source='Streaming Catalogs', feed='nfx-movie'), obs(detail=True, discovery=False, feed='metadata')])
        self.assertEqual(lib[0]['sources'], ['Streaming Catalogs'])

    def test_kitsu_score_is_not_tmdb_score(self):
        t = title(source='Anime Kitsu', ratingSource='Kitsu', votes=0, rating=9.9)
        self.assertEqual(t['votes'], 0)
        self.assertEqual(quality(t), .65)

    def test_many_seasons_one_work(self):
        a=obs(kind='tv', source='Anime Kitsu', feed='kitsu-airing', ids={'kitsu':'10','imdb':'tt1'}, title='Season One', rank=0)
        b=obs(kind='tv', source='Anime Kitsu', feed='kitsu-airing', ids={'kitsu':'11','imdb':'tt1'}, title='Season Two', rank=1)
        lib,_=merge_observations([a,b]);self.assertEqual(len(lib),1);self.assertEqual(len(lib[0]['editions']),2)
        row=build_plan([a,b],today=TODAY)['rows'][1]
        self.assertEqual([x['id'] for x in row['items']], ['kitsu:10','kitsu:11'])
        self.assertEqual([x['title'] for x in row['items']], ['Season One','Season Two'])

class ShelfTests(unittest.TestCase):
    def setUp(self): self.p=load_profile()
    def test_twelve_independent_rows(self):
        self.assertEqual(len(build_plan([obs()],today=TODAY)['rows']),12)
    def test_airing_preserves_twenty(self):
        observations=[obs(kind='tv',source='Anime Kitsu',feed='kitsu-airing',ids={'kitsu':str(i),'imdb':'tt1'},title='Part '+str(i),rank=i) for i in range(20)]
        observations.append(obs(kind='tv', source='Anime Kitsu',feed='kitsu-trending',ids={'kitsu':'99'}))
        row=build_plan(observations,today=TODAY)['rows'][1]
        self.assertEqual(len(row['items']),20)
        self.assertEqual([x['id'] for x in row['items']],[f'kitsu:{i}' for i in range(20)])
    def test_small_airing_feed_not_filled(self):
        self.assertEqual(build_plan([obs()],today=TODAY)['rows'][1]['items'],[])
    def test_new_movie_requires_exact_past_date(self):
        for d in ['2026','2026-','2027-01-01','2020-01-01','']:
            self.assertFalse(eligible(title(releaseDate=d),'new-movies',TODAY,self.p),d)
        self.assertTrue(eligible(title(releaseDate='2026-10-01'),'new-movies',TODAY,self.p))
    def test_new_season_distinct_from_old_premiere(self):
        t=title(kind='tv',releaseDate='2015-01-01',seasonDate='2026-10-01')
        self.assertTrue(eligible(t,'new-shows',TODAY,self.p))
        self.assertFalse(eligible(title(kind='tv',releaseDate='2015-01-01'),'new-shows',TODAY,self.p))
    def test_trending_tmdb_only(self):
        self.assertTrue(eligible(title(),'trending',TODAY,self.p))
        self.assertFalse(eligible(title(source='Streaming Catalogs',feed='nfx-movie'),'trending',TODAY,self.p))
    def test_origin_not_language_or_availability(self):
        t=title(countries=['KR'], originalLanguage='en', region='IN',offers=['netflix'])
        self.assertTrue(eligible(t,'korean',TODAY,self.p));self.assertFalse(eligible(t,'indian',TODAY,self.p))
    def test_animation_not_automatically_anime(self):
        self.assertFalse(title(genres=['Animation'])['anime'])
    def test_tv_combined_fantasy_not_strict_scifi(self):
        self.assertFalse(eligible(title(kind='tv',genres=['Sci-Fi & Fantasy']),'sci-fi',TODAY,self.p))
        self.assertTrue(eligible(title(kind='tv',genres=['Sci-Fi & Fantasy'],keywords=['space opera']),'sci-fi',TODAY,self.p))
    def test_awards_require_verified_winner_film(self):
        award={'awardStatus':'winner','ceremonyYear':2025,'academySourceUrl':'https://www.oscars.org/oscars/ceremonies/2025'}
        self.assertTrue(eligible(title(awards=[award]),'oscars',TODAY,self.p))
        self.assertFalse(eligible(title(kind='tv',awards=[award]),'oscars',TODAY,self.p))
        self.assertFalse(eligible(title(awards=[dict(award,awardStatus='nominee')]),'oscars',TODAY,self.p))
    def test_quality_vote_floor(self):
        self.assertFalse(eligible(title(votes=2,rating=10),'must-watch-movies',TODAY,self.p))
        self.assertTrue(eligible(title(),'must-watch-movies',TODAY,self.p))
    def test_provider_priority_and_unknown_state(self):
        self.assertEqual(route(title(region='IN',offers=['prime','netflix']),self.p)['app'],'netflix')
        self.assertEqual(route(title(region='IN',offers=[]),self.p)['app'],'stremio')
        self.assertEqual(route(title(),self.p)['status'],'availability not checked')
    def test_for_me_keeps_dune_related_discoveries(self):
        pool=[obs(tmdb=i,imdb='tt'+str(i),title='Taste '+str(i),duneRelated=i<3) for i in range(1,20)]
        row=build_plan(pool,today=TODAY)['rows'][0]
        self.assertGreaterEqual(sum(x['reason']=='Dune recommendations' for x in row['items']),2)
    def test_for_me_excludes_known_unreleased(self):
        row=build_plan([obs(releaseDate='2027-01-01')],today=TODAY)['rows'][0]
        self.assertEqual(row['items'],[])
    def test_deterministic_visit(self):
        pool=[obs(tmdb=i,imdb='tt'+str(i)) for i in range(25)]
        self.assertEqual(build_plan(pool,today=TODAY,cycle=3),build_plan(pool,today=TODAY,cycle=3))

class RefreshTests(unittest.TestCase):
    def setUp(self): self.rows={r['id']:r for r in load_profile()['rows']}
    def decide(self,id,event='idle',minutes=10,idle=600,**kw):
        return refresh_decision(self.rows[id],event,minutes*60,minutes*60,idle,**kw)
    def test_fast_rows_after_ten_minutes(self):
        for id in ['for-me','new-movies','new-shows']:self.assertEqual(self.decide(id)['replace'],2)
    def test_recent_remote_activity_defers(self): self.assertEqual(self.decide('for-me',idle=1)['action'],'hold')
    def test_focused_row_frozen(self): self.assertEqual(self.decide('new-movies',focused=True)['action'],'hold')
    def test_overlay_defers(self): self.assertEqual(self.decide('for-me',blocked=True)['action'],'hold')
    def test_image_metadata_callbacks_never_shuffle(self):
        for event in ['image','metadata','network','redraw']:self.assertEqual(self.decide('for-me',event=event)['action'],'hold')
    def test_section_specific_idle_intervals(self):
        self.assertEqual(self.decide('trending',minutes=10)['action'],'hold')
        self.assertEqual(self.decide('trending',minutes=15)['replace'],2)
        self.assertEqual(self.decide('indian',minutes=15)['action'],'hold')
        self.assertEqual(self.decide('indian',minutes=30)['replace'],2)
        self.assertEqual(self.decide('oscars',minutes=30)['action'],'hold')
        self.assertEqual(self.decide('oscars',minutes=60)['replace'],1)
    def test_airing_never_randomly_shuffles(self):
        for event in ['visit','idle']:self.assertEqual(self.decide('anime-airing',event=event,minutes=600)['action'],'hold')
    def test_airing_feed_applies_only_at_safe_boundary(self):
        self.assertEqual(self.decide('anime-airing',event='visit',data_changed=True)['action'],'apply-feed')
        self.assertEqual(self.decide('anime-airing',event='network',data_changed=True)['action'],'hold')
        self.assertEqual(self.decide('anime-airing',idle=1,data_changed=True)['action'],'hold')
    def test_home_return_changes_some_slots(self): self.assertEqual(self.decide('for-me',event='visit',minutes=0)['replace'],4)
    def test_partial_replacement_preserves_slots(self):
        old=[{'id':str(i)} for i in range(10)];ranked=old+[{'id':str(i)} for i in range(10,30)]
        out=partial_refresh(old,ranked,2,1,10)
        self.assertEqual(sum(a['id']!=b['id'] for a,b in zip(old,out)),2)
        self.assertEqual(len({x['id'] for x in out}),10)
    def test_small_pool_stays_in_order(self):
        old=[{'id':str(i)} for i in range(5)]
        self.assertEqual(partial_refresh(old,list(reversed(old)),4,2,10),old)
    def test_no_stale_ineligible_titles(self):
        old=[{'id':str(i)} for i in range(10)];ranked=[{'id':str(i)} for i in range(5,20)]
        self.assertTrue(all(x['id'] in {r['id'] for r in ranked} for x in partial_refresh(old,ranked,2,1,10)))
    def test_fetch_expiry_does_not_define_visible_rotation(self):
        spec=self.rows['anime-airing']
        self.assertFalse(data_fetch_due(spec,10000,9000))
        self.assertTrue(data_fetch_due(spec,100000,9000))
        self.assertFalse(data_fetch_due(spec,100000,9000,99900))
        self.assertEqual(self.decide('anime-airing',minutes=600)['action'],'hold')

if __name__=='__main__':unittest.main()

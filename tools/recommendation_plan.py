#!/usr/bin/env python3
"""Executable reference for the twelve-row matching and refresh policy; no TV or network operations.

Consumes normalized public catalog observations. This is the review planner, not the Android renderer.
Metadata fetch/enrichment is deliberately outside selection, so its callbacks cannot shuffle a held row.
"""
import argparse
from collections import Counter, defaultdict
from datetime import date
import hashlib
import json
from pathlib import Path

PROFILE = Path(__file__).resolve().parents[1] / 'documentation/recommendations/home-profile.json'


def load_profile():
    return json.loads(PROFILE.read_text())


def day(value):
    try:
        return date.fromisoformat(str(value)[:10]) if len(str(value)) >= 10 else None
    except ValueError:
        return None


def age(value, today):
    parsed = day(value)
    return (today - parsed).days if parsed else None


def jitter(identity, cycle):
    raw = hashlib.sha256((str(cycle) + '|' + identity).encode()).digest()
    return int.from_bytes(raw[:8], 'big') / 2**64


def aliases(record):
    kind = record['kind']
    return {kind + '|' + name + ':' + str(value) for name, value in record.get('ids', {}).items() if value}


def merge_observations(observations):
    """Exact, type-safe ID joins; season editions survive inside each title group."""
    observations = [o for o in observations if o.get('kind') in ('movie', 'tv') and aliases(o)]
    parent = list(range(len(observations)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    links = defaultdict(list)
    for i, o in enumerate(observations):
        for alias in aliases(o):
            links[alias].append(i)
    conflicts = []
    for alias, members in sorted(links.items(), key=lambda x: (':tmdb:' not in x[0], x[0])):
        tmdb_ids = {str(observations[i].get('ids', {}).get('tmdb')) for i in members if observations[i].get('ids', {}).get('tmdb')}
        if len(tmdb_ids) > 1 and '|tmdb:' not in alias:
            conflicts.append(alias)
            continue
        for i in members[1:]:
            parent[find(i)] = find(members[0])
    grouped = defaultdict(list)
    for i, o in enumerate(observations):
        grouped[find(i)].append(o)
    library = []
    for records in grouped.values():
        records.sort(key=lambda o: (o.get('detail') is not True, o['source'] != 'TMDB', o.get('feed', ''), o.get('rank', 0)))
        all_aliases = sorted(set().union(*(aliases(o) for o in records)))
        canonical = next((a for a in all_aliases if '|tmdb:' in a), next((a for a in all_aliases if '|imdb:' in a), all_aliases[0]))
        group = {'id': canonical, 'kind': records[0]['kind'], 'title': records[0]['title'], 'aliases': all_aliases,
                 'genres': [], 'keywords': [], 'countries': [], 'sources': [], 'feeds': [], 'editions': [], 'awards': [], 'offers': None,
                 'releaseDate': '', 'seasonDate': '', 'rating': 0, 'votes': 0, 'year': '', 'anime': False, 'ranks': {}, 'duneRelated': False}
        for o in records:
            for field in ('genres', 'keywords', 'countries'):
                for value in o.get(field, []):
                    if value not in group[field]:
                        group[field].append(value)
            if o.get('discovery', True):
                if o['source'] not in group['sources']:
                    group['sources'].append(o['source'])
                if o.get('feed') not in group['feeds']:
                    group['feeds'].append(o.get('feed'))
                rank = o.get('rank', 0)
                group['ranks'][o.get('feed')] = 1 - rank / max(1, o.get('feedSize', rank + 1) - 1)
            for field in ('releaseDate', 'seasonDate', 'year'):
                if not group[field] and o.get(field):
                    group[field] = o[field]
            if o.get('ratingSource') == 'TMDB' and o.get('votes', 0) > group['votes']:
                group['rating'], group['votes'] = o.get('rating', 0), o['votes']
            if group['offers'] is None and o.get('offers') is not None and o.get('region') == 'IN':
                group['offers'] = o['offers']
            group['anime'] |= o['source'] == 'Anime Kitsu'
            group['duneRelated'] |= bool(o.get('duneRelated'))
            for award in o.get('awards', []):
                if award not in group['awards']:
                    group['awards'].append(award)
            if o['source'] == 'Anime Kitsu':
                edition = {k: o.get(k) for k in ('title', 'ids', 'feed', 'rank')}
                if edition not in group['editions']:
                    group['editions'].append(edition)
        # A metadata lookup enriches a discovered work; it doesn't create a discovery candidate by itself.
        if group['sources']:
            library.append(group)
    return sorted(library, key=lambda t: t['id']), sorted(set(conflicts))


def quality(t):
    votes, rating = t.get('votes', 0), t.get('rating', 0)
    return (votes * rating / 10 + 500 * .65) / (votes + 500)


def taste(t):
    if t.get('duneRelated'):
        return 1.0
    g = set(t.get('genres', []))
    if g & {'Science Fiction', 'Sci-Fi', 'Fantasy', 'Sci-Fi & Fantasy'}:
        return .9
    return .35 if g & {'Adventure', 'Action & Adventure'} else .1


def strict_scifi(t):
    g = set(t.get('genres', []))
    return bool(g & {'Science Fiction', 'Sci-Fi'} or set(t.get('keywords', [])) & {'science fiction', 'space opera', 'cyberpunk', 'alien invasion', 'time travel', 'space travel'})


def eligible(t, shelf, today, profile):
    if shelf == 'for-me':
        released_age = age(t.get('releaseDate'), today)
        return released_age is None or released_age >= 0
    if shelf == 'trending':
        return any(f and f.startswith('tmdb-trending-') for f in t['feeds'])
    if shelf == 'new-movies':
        a = age(t.get('releaseDate'), today)
        return t['kind'] == 'movie' and a is not None and 0 <= a <= profile['recentReleaseDays']
    if shelf == 'new-shows':
        if t['kind'] != 'tv':
            return False
        return any(a is not None and 0 <= a <= profile['newReleaseDays'] for a in (age(t.get('releaseDate'), today), age(t.get('seasonDate'), today)))
    if shelf in ('indian', 'korean', 'japanese'):
        return {'indian': 'IN', 'korean': 'KR', 'japanese': 'JP'}[shelf] in t['countries']
    if shelf == 'sci-fi':
        return strict_scifi(t)
    if shelf == 'oscars':
        return t['kind'] == 'movie' and any(a.get('awardStatus') == 'winner' and a.get('academySourceUrl', '').startswith('https://www.oscars.org/') for a in t['awards'])
    if shelf.startswith('must-watch-'):
        return t['kind'] == ('movie' if shelf.endswith('movies') else 'tv') and t.get('votes', 0) >= 200 and quality(t) >= .75
    return False


def score(t, shelf, today, cycle):
    trend = max(t.get('ranks', {}).values(), default=0)
    released_age = age(t.get('releaseDate'), today)
    fresh = max(0, 1 - released_age / 365) if released_age is not None and released_age >= 0 else 0
    if shelf == 'for-me':
        base = .45 * taste(t) + .25 * quality(t) + .2 * trend + .1 * fresh + (.18 if t.get('duneRelated') else 0)
    elif shelf in ('new-movies', 'new-shows'):
        if shelf == 'new-shows':
            ages = [a for a in (released_age, age(t.get('seasonDate'), today)) if a is not None and a >= 0]
            fresh = max(0, 1 - min(ages) / 90) if ages else 0
        base = .6 * fresh + .25 * trend + .15 * quality(t)
    elif shelf == 'trending':
        base = max((rank for feed, rank in t['ranks'].items() if feed and feed.startswith('tmdb-trending-')), default=0)
    else:
        base = .7 * quality(t) + .3 * trend
    # Reproducible visit variety; tiny changes cannot promote irrelevant candidates past a hard filter.
    return base + .12 * jitter(t['id'], cycle)


def select(candidates, shelf, limit, today, cycle, seen):
    out, remaining = [], list(candidates)
    country_counts, era_counts, kind_counts = Counter(), Counter(), Counter()
    diverse = shelf.startswith('must-watch-') or shelf == 'oscars'
    countries = sorted({c for t in remaining for c in t['countries']})
    eras = sorted({str(t.get('year', ''))[:3] for t in remaining if str(t.get('year', '')).isdigit()})
    for position in range(min(limit, len(remaining))):
        def priority(t):
            value = score(t, shelf, today, cycle)
            if shelf != 'trending':
                value -= .12 * seen[t['id']]
            if diverse:
                country = next(iter(t['countries']), 'unknown')
                era = str(t.get('year', ''))[:3]
                value -= .14 * country_counts[country] + .10 * era_counts[era]
                if countries and countries[(cycle + position) % len(countries)] in t['countries']:
                    value += .12
                if eras and era == eras[(cycle + position) % len(eras)]:
                    value += .08
            else:
                value -= .08 * kind_counts[t['kind']]
            return value, t['id']
        picked = max(remaining, key=priority)
        remaining.remove(picked)
        out.append(picked)
        country_counts[next(iter(picked['countries']), 'unknown')] += 1
        era_counts[str(picked.get('year', ''))[:3]] += 1
        kind_counts[picked['kind']] += 1
    return out


def route(t, profile):
    offers = t.get('offers')
    native = next((app for app in profile['providerPriority'] if offers is not None and app in offers), None)
    if native:
        return {'app': native, 'status': 'verified IN subscription'}
    return {'app': profile['fallback'], 'status': 'availability not checked' if offers is None else 'no preferred app offer in IN'}


def reason(t, shelf, today):
    if shelf == 'anime-airing':
        return 'Kitsu top-airing order; exact season entry retained'
    if shelf == 'for-me':
        return 'Dune recommendations' if t.get('duneRelated') else 'Sci-fi / fantasy taste' if taste(t) >= .9 else 'Anime discovery' if t['anime'] else 'Quality / fresh discovery'
    if shelf == 'new-movies':
        a = age(t.get('releaseDate'), today)
        return ('Latest release' if a <= 90 else 'Recent release + discovery') + ' · ' + t['releaseDate']
    if shelf == 'new-shows':
        a = age(t.get('releaseDate'), today)
        return ('New series · ' + t['releaseDate']) if a is not None and 0 <= a <= 90 else 'New season · ' + t['seasonDate']
    if shelf == 'trending':
        return 'TMDB weekly trending membership'
    if shelf == 'oscars':
        return 'Oscar winner · ' + str(t['awards'][0]['ceremonyYear'])
    if shelf.startswith('must-watch-'):
        return 'Quality-qualified · country / decade rotation'
    return 'Verified origin' if shelf in ('indian', 'korean', 'japanese') else 'Explicit science-fiction evidence'


def build_plan(observations, profile=None, today=None, cycle=0):
    profile, today = profile or load_profile(), today or date.today()
    library, conflicts = merge_observations(observations)
    by_alias = {alias: t for t in library for alias in t['aliases']}
    rows, seen = [], Counter()
    for spec in profile['rows']:
        shelf = spec['id']
        if shelf == 'anime-airing':
            records = sorted((o for o in observations if o.get('source') == 'Anime Kitsu' and o.get('feed') == 'kitsu-airing'), key=lambda o: o.get('rank', 0))
            candidates, ids = [], set()
            for o in records:
                kitsu = o.get('ids', {}).get('kitsu')
                if not kitsu or kitsu in ids:
                    continue
                ids.add(kitsu)
                group = by_alias.get(o['kind'] + '|kitsu:' + str(kitsu))
                if group:
                    entry = dict(group, id='kitsu:' + str(kitsu), title=o['title'], launchId='kitsu:' + str(kitsu), sources=['Anime Kitsu'])
                    candidates.append(entry)
            selected = candidates[:spec['limit']]
        else:
            candidates = [t for t in library if eligible(t, shelf, today, profile)]
            selected = select(candidates, shelf, spec['limit'], today, cycle, seen)
            if shelf == 'for-me':
                # Intent-based slots retain a recognisable taste while leaving room for new discoveries.
                selected = []
                slot_pools = [([t for t in candidates if t['anime']], 2, shelf),
                              ([t for t in candidates if not t['anime'] and t['duneRelated']], 2, 'taste-discovery'),
                              ([t for t in candidates if not t['anime'] and taste(t) >= .9 and quality(t) >= .70], 2, shelf),
                              ([t for t in candidates if eligible(t, 'new-movies', today, profile)], 1, shelf),
                              ([t for t in candidates if eligible(t, 'new-shows', today, profile)], 1, shelf),
                              ([t for t in candidates if quality(t) >= .70], 2, 'quality-discovery'),
                              (candidates, spec['limit'], shelf)]
                for slot_pool, count, rank_shelf in slot_pools:
                    unused = [t for t in slot_pool if t not in selected]
                    selected.extend(select(unused, rank_shelf, min(count, spec['limit'] - len(selected)), today, cycle, seen))
                selected.sort(key=lambda t: score(t, shelf, today, cycle), reverse=True)
        items = []
        for t in selected:
            seen[t['id']] += 1
            items.append({k: t.get(k) for k in ('id', 'title', 'kind', 'year', 'sources', 'feeds', 'genres', 'countries', 'launchId')} | {'reason': reason(t, shelf, today), 'route': route(t, profile)})
        rows.append({'id': shelf, 'title': spec['title'], 'limit': spec['limit'], 'candidateCount': len(candidates), 'policy': spec, 'items': items})
    return {'date': today.isoformat(), 'region': profile['region'], 'cycle': cycle, 'librarySize': len(library), 'observationCount': len(observations), 'ambiguousAliases': conflicts, 'rows': rows}


def refresh_decision(spec, event, elapsed_seconds, since_rotation_seconds, idle_seconds, focused=False, blocked=False, data_changed=False):
    """Monotonic durations only; fetch callbacks and remote activity never count as shuffle events."""
    if event not in ('visit', 'idle') or blocked or focused:
        return {'action': 'hold', 'replace': 0}
    if event == 'idle' and idle_seconds < 60:
        return {'action': 'hold', 'replace': 0}
    if spec.get('stableSourceOrder'):
        return {'action': 'apply-feed' if data_changed else 'hold', 'replace': 0}
    if event == 'visit':
        return {'action': 'rotate', 'replace': spec['visitReplace']}
    if idle_seconds < 60 or elapsed_seconds < 0 or since_rotation_seconds < spec['idleMinutes'] * 60:
        return {'action': 'hold', 'replace': 0}
    return {'action': 'rotate', 'replace': spec['idleReplace']}


def data_fetch_due(spec, now_seconds, last_success_seconds=None, last_attempt_seconds=None):
    """Wall-clock cache expiry is independent from visible selection. Failed fetches back off ten minutes."""
    if last_attempt_seconds is not None and 0 <= now_seconds - last_attempt_seconds < 600:
        return False
    return last_success_seconds is None or now_seconds < last_success_seconds or now_seconds - last_success_seconds >= spec['dataHours'] * 3600


def partial_refresh(existing, ranked, replace_count, cycle, limit):
    """Keep slot positions; never shuffle a small pool just to manufacture a change."""
    available = {t['id']: t for t in ranked}
    out = [available.get(t['id']) for t in existing[:limit]]
    out += [None] * (min(limit, len(ranked)) - len(out))
    held = {t['id'] for t in out if t}
    fresh = [t for t in ranked if t['id'] not in held]
    for i, t in enumerate(out):
        if t is None and fresh:
            out[i] = fresh.pop(0)
    positions = sorted((i for i, t in enumerate(out) if t and t['id'] in held), key=lambda i: jitter(str(i), cycle))
    for i in positions[:max(0, replace_count)]:
        if not fresh:
            break
        out[i] = fresh.pop(0)
    return [t for t in out if t]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--date', default=date.today().isoformat())
    p.add_argument('--cycle', type=int, default=0)
    args = p.parse_args()
    observations = json.loads(Path(args.input).read_text())
    result = build_plan(observations, today=date.fromisoformat(args.date), cycle=args.cycle)
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print('Planned', len(result['rows']), 'rows from', result['librarySize'], 'matched title groups.')


if __name__ == '__main__':
    main()

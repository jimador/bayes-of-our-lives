#!/usr/bin/env bash
set -euo pipefail
# Only the copied runner is executed, with all build/runtime/HTTP commands stubbed.
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
python3 - "$script_dir/run-live-check.sh" <<'PY'
import datetime as dt, json, os, pathlib, shutil, signal, socket, subprocess, sys, tempfile
from zoneinfo import ZoneInfo
source = pathlib.Path(sys.argv[1])
checks = 0
with tempfile.TemporaryDirectory(prefix='bayes-live-check-test-') as temporary:
    root = pathlib.Path(temporary)
    repo = root / 'repo'
    (repo / 'scripts').mkdir(parents=True)
    shutil.copy2(source, repo / 'scripts/run-live-check.sh')
    bin_dir = root / 'bin'
    bin_dir.mkdir()
    stub = '''import json, os, pathlib, signal, sys, time
name = pathlib.Path(sys.argv[0]).name
state = pathlib.Path(os.environ['STUB_STATE'])
def event(**values):
    with (state / 'events').open('a') as log:
        log.write(json.dumps(dict(tool=name, **values)) + '\\n')
if name == 'mvn':
    event(args=sys.argv[1:])
    sys.exit(int(os.environ.get('STUB_BUILD', '0')))
if name == 'java':
    (state / 'pid').write_text(str(os.getpid()))
    event(args=sys.argv[1:], pid=os.getpid())
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    while True: time.sleep(.05)
if name == 'curl':
    args = sys.argv[1:]
    if '--data-binary' not in args:
        # Synchronize readiness with the stub app's startup record.
        for _ in range(100):
            if (state / 'pid').exists(): break
            time.sleep(.01)
        event(ready=True)
        sys.exit(0)
    payload = json.load(sys.stdin)
    engine = payload['engine']
    event(engine=engine, payload=payload, args=args)
    mode = json.loads(os.environ.get('STUB_OUTCOMES', '{}')).get(engine, 'usable')
    output = pathlib.Path(args[args.index('-o') + 1])
    data = dict(status='usable', selectedAlternative='one', alternatives=[dict(id='one', status='usable')], processMs=12)
    if mode == 'unusable': data['status'] = 'unresolved'
    if mode == 'missing': data.pop('selectedAlternative')
    if mode == 'invalid': data['alternatives'][0]['status'] = 'invalid'
    if mode == 'shape': data['alternatives'] = None
    if mode == 'null': data = None
    output.write_text('partial' if mode == 'transport' else '{' if mode == 'malformed' else '' if mode == 'empty' else json.dumps(data))
    if mode == 'death': os.kill(int((state / 'pid').read_text()), signal.SIGTERM)
    if mode in ('term', 'int'): os.kill(int((state / 'runner-pid').read_text()), signal.SIGTERM if mode == 'term' else signal.SIGINT)
    print('503' if mode == 'http' else '000' if mode == 'transport' else '200', end='')
    sys.exit(7 if mode == 'transport' else 0)
'''
    for name in ('mvn', 'java', 'curl'):
        path = bin_dir / name
        path.write_text('#!' + sys.executable + '\n' + stub)
        path.chmod(0o755)
    future = str(dt.date.today().year + 2) + '-06-15'
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        port = str(probe.getsockname()[1])
    base = dict(PATH=str(bin_dir) + ':' + str(pathlib.Path(sys.executable).parent) + ':' + os.defpath,
                TMPDIR=str(root), OPENAI_API_KEY='private-sentinel-openai', TYPESAFE_API_KEY='private-sentinel-jev')
    def launch(args=(), env=None, outcomes=None, build=0):
        state = root / ('state-' + str(len(list(root.glob('state-*')))))
        state.mkdir()
        settings = dict(base, STUB_STATE=str(state), STUB_BUILD=str(build), STUB_OUTCOMES=json.dumps(outcomes or {}))
        settings.update(env or {})
        process = subprocess.Popen(['bash', str(repo / 'scripts/run-live-check.sh'), *args], cwd=repo,
                                   env=settings, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        (state / 'runner-pid').write_text(str(process.pid))
        return process, state
    def finish(job, expected=0):
        global checks
        process, state = job
        stdout, stderr = process.communicate(timeout=15)
        assert process.returncode == expected, (process.returncode, expected, stdout, stderr)
        events = [json.loads(line) for line in (state / 'events').read_text().splitlines()] if (state / 'events').exists() else []
        if (state / 'pid').exists():
            pid = int((state / 'pid').read_text())
            try: os.kill(pid, 0)
            except ProcessLookupError: pass
            else: raise AssertionError('Owned app survived cleanup')
        assert 'private-sentinel' not in stdout + stderr
        for path in (repo / 'results').glob('**/*') if (repo / 'results').exists() else []:
            if path.is_file(): assert b'private-sentinel' not in path.read_bytes(), path
        checks += 1
        return events, stdout
    def run(args=(), env=None, outcomes=None, expected=0, build=0):
        return finish(launch(args, env, outcomes, build), expected)
    def valid(*args, **kwargs):
        return run(('--date', future, '--port', port, *args), **kwargs)
    def reject(*args, env=None):
        prefix = ('--date', future) if '--port' in args else ('--date', future, '--port', port)
        events, _ = run((*prefix, *args), env=env, expected=2)
        assert not events, events
    def captures(events):
        return [e for e in events if 'engine' in e]
    def summary(stdout):
        path = repo / stdout.split('Summary: ')[-1].strip()
        text = path.read_text()
        for name in __import__('re').findall(r'\[JSON/partial\]\(([^)]+)\)', text): assert (path.parent / name).exists()
        return text, path
    # Defaults, exact payload shape, order, startup flags and no request retries/warmups.
    if dt.datetime.now(ZoneInfo('America/New_York')) >= dt.datetime(2026, 10, 20, 14, tzinfo=ZoneInfo('America/New_York')):
        events, _ = run(('--port', port), expected=2); assert not events
        default_args = ('--date', future, '--port', port)
        expected_date = future
    else:
        default_args = ('--port', port)
        expected_date = '2026-10-20'
    events, stdout = run(default_args)
    requests = captures(events)
    assert [e['engine'] for e in requests] == ['local', 'jev', 'openai']
    payload = requests[0]['payload']
    assert payload == dict(city='Fort Lauderdale, Florida', startingLocation='Downtown Fort Lauderdale', date=expected_date, startTime='07:00', endTime='14:00', breakfast=True, coffee=False, earlyFoodDeclined=False, lunch=True, dinner=False, partySize=2, childrenCount=0, interests='art', pace='steady', stepFreeAccess=False, cuisineLikes='seafood, cuban', cuisineDislikes='italian', dietaryOrAllergyNeeds='', publicMapConsent=True, remoteModelConsent=True, engine='local')
    assert events[0] == dict(tool='mvn', args=['test', 'package'])
    java = next(e for e in events if e['tool'] == 'java')
    assert java['args'] == ['-jar', 'target/bayes-0.1.0.jar', '--server.port='+port, '--bayes.models.researcher=gpt-4.1', '--bayes.models.planner=gpt-4.1', '--logging.level.root=ERROR', '--logging.level.dev.jimador.bayes=INFO', '--embabel.agent.platform.models.openai.max-attempts=1', '--embabel.agent.platform.llm-operations.data-binding.max-attempts=1', '--embabel.agent.platform.decisions.llm.max-attempts=1']
    assert all('--retry' not in e.get('args', []) for e in events)
    assert len([e for e in events if e.get('ready')]) == 1
    assert '3 outing submission(s)' in summary(stdout)[0]
    # Quoting stays JSON; flags override environment, including strict failure policy.
    events, stdout = valid('--city', 'Tampa "Bay" \\ Florida', '--origin', 'River \\ "walk"', '--interests', 'art "and" parks', '--pace', 'busy', '--engines', 'openai,local', '--meals', 'none', '--cuisine-likes', ' Cuban ; MIDDLE_eastern, cuban ', '--cuisine-dislikes', '', '--timezone', 'UTC', '--continue-on-failure', env={'BAYES_LIVE_CITY':'wrong', 'BAYES_LIVE_FAIL_FAST':'invalid', 'BAYES_LIVE_DATE':'bad'})
    assert [e['engine'] for e in captures(events)] == ['openai', 'local']
    p = captures(events)[0]['payload']
    assert p['city'] == 'Tampa "Bay" \\ Florida' and p['startingLocation'] == 'River \\ "walk"' and p['interests'] == 'art "and" parks'
    assert p['cuisineLikes'] == 'cuban, middle eastern' and p['cuisineDislikes'] == '' and p['earlyFoodDeclined']
    assert not any(p[m] for m in ('breakfast','coffee','lunch','dinner'))
    events, _ = run((future,), env={'BAYES_LIVE_DATE':'bad','BAYES_LIVE_CITY':'Tampa','BAYES_LIVE_ORIGIN':'Riverwalk','BAYES_LIVE_START':'09:00','BAYES_LIVE_END':'18:00','BAYES_LIVE_INTERESTS':'parks','BAYES_LIVE_PACE':'relaxed','BAYES_LIVE_MEALS':'coffee,lunch,dinner','BAYES_LIVE_CUISINE_LIKES':'pizza','BAYES_LIVE_CUISINE_DISLIKES':'sushi','BAYES_LIVE_ENGINES':'jev','BAYES_LIVE_TIMEZONE':'UTC','BAYES_LIVE_PORT':port})
    p = captures(events)[0]['payload']
    assert p['city']=='Tampa' and p['date']==future and p['coffee'] and p['lunch'] and p['dinner'] and not p['earlyFoodDeclined'] and p['pace']=='relaxed' and p['cuisineLikes']=='pizza' and p['cuisineDislikes']=='sushi' and p['startTime']=='09:00' and p['endTime']=='18:00' and p['interests']=='parks' and p['startingLocation']=='Riverwalk'
    # Every invalid scenario must stop before even the stub build.
    for args in [('--city',''),('--city','x'*101),('--origin','x'*121),('--interests','x'*101),('--interests',' '),('--city','a\x7fb'),('--origin','a\nb'),('--interests','a\x85b'),('--cuisine-likes','x'*121),('--cuisine-likes','cuban\n'),('--cuisine-likes','allergy'),('--cuisine-likes','cuban','--cuisine-dislikes','CUBAN'),('--cuisine-likes','american,cuban,chinese,french,german,greek,indian,italian,japanese'),('--pace','fast'),('--meals',''),('--meals','none,lunch'),('--meals','lunch,lunch'),('--engines',''),('--engines','local,local'),('--engines','other'),('--start','7:00'),('--start','24:00'),('--start','14:00'),('--end','06:00'),('--start','00:00','--end','12:01'),('--end','10:59'),('--meals','dinner'),('--timezone','Invalid/Zone'),('--timezone',''),('--port','80'),('--port','65536'),('--port','no'),('--what',),('--city',),('--city','a','--city','b'),('--fail-fast','--continue-on-failure'),(future,)]:
        reject(*args)
    for field in ('CITY','ORIGIN','DATE','START','END','INTERESTS','PACE','MEALS','ENGINES','TIMEZONE','PORT','FAIL_FAST'):
        # Avoid flags that intentionally override DATE and PORT in valid().
        events, _ = run((), env={'BAYES_LIVE_'+field:''}, expected=2)
        assert not events
    for date in ('2026-02-30','2026-2-03','2020-01-01'):
        events, _ = run(('--date',date), expected=2); assert not events
    for date, start, end in [('2027-03-14','02:00','04:00'),('2027-11-07','01:00','03:00'),('2027-03-14','01:00','04:00')]:
        events, _ = run(('--date',date,'--start',start,'--end',end,'--meals','none'), expected=2); assert not events
    # Exactly 12h and inclusive meal endpoints are valid.
    valid('--start','00:00','--end','12:00','--meals','lunch','--engines','local')
    valid('--start','14:30','--end','17:00','--meals','lunch,dinner','--engines','local')
    today = dt.datetime.now(dt.timezone.utc).date().isoformat()
    events, _ = run(('--date',today,'--timezone','UTC','--start','00:00','--end','00:01','--meals','none'), expected=2); assert not events
    with socket.socket() as occupied:
        occupied.bind(('127.0.0.1',0)); occupied.listen()
        events, _ = run(('--date',future,'--port',str(occupied.getsockname()[1])) , expected=2); assert not events
    events, _ = run(('--help',), env={'OPENAI_API_KEY':'','TYPESAFE_API_KEY':''}); assert not events
    events, _ = valid('--engines','local', env={'OPENAI_API_KEY':''}, expected=2); assert not events
    # Failures retain and link raw bodies, continue once per engine, and aggregate nonzero.
    for mode, label in [('transport','transport failure (partial)'),('http','HTTP failure'),('unusable','top-level unusable'),('missing','missing/invalid selected entry'),('invalid','missing/invalid selected entry'),('shape','missing/invalid selected entry'),('null','top-level unusable'),('malformed','malformed/empty JSON'),('empty','malformed/empty JSON')]:
        events, stdout = valid(outcomes={'local':mode}, expected=1)
        assert [e['engine'] for e in captures(events)] == ['local','jev','openai']
        text, path = summary(stdout)
        assert label in text and '3 outing submission(s)' in text and text.count('[JSON/partial]') == 3
        if mode == 'transport': assert next(path.parent.glob('*-local.json')).read_text() == 'partial'
    events, stdout = valid(outcomes={'local':'transport','jev':'http','openai':'empty'}, expected=1)
    assert [e['engine'] for e in captures(events)] == ['local','jev','openai'] and summary(stdout)[0].count('[JSON/partial]') == 3
    for policy in ({'args':('--fail-fast',)}, {'env':{'BAYES_LIVE_FAIL_FAST':'true'}}):
        args = policy.get('args', ())
        events, stdout = valid(*args, env=policy.get('env'), outcomes={'local':'http'}, expected=1)
        assert [e['engine'] for e in captures(events)] == ['local'] and '1 outing submission(s)' in summary(stdout)[0]
    # App death does not skip the remaining selected requests under continuation.
    events, _ = valid(outcomes={'local':'death','jev':'transport','openai':'transport'}, expected=1)
    assert len(captures(events)) == 3
    events, _ = valid(build=9, expected=9); assert [e['tool'] for e in events] == ['mvn']
    # Neither ordinary cleanup nor signal cleanup may touch an unrelated process.
    sentinel = subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)'])
    try:
        for mode, code in [('term',143),('int',130)]:
            events, stdout = valid(outcomes={'local':mode}, expected=code)
            assert len(captures(events)) == 1 and sentinel.poll() is None
            assert 'interrupted (partial)' in summary(stdout)[0]
        valid('--engines','local'); assert sentinel.poll() is None
    finally:
        sentinel.terminate(); sentinel.wait()
    # Two concurrent identical scenarios, then a repeat, must get distinct output dirs.
    jobs = [launch(('--date',future,'--port',port,'--engines','local')) for _ in range(2)]
    paths = [summary(finish(job)[1])[1].parent for job in jobs]
    paths.append(summary(valid('--engines','local')[1])[1].parent)
    assert len(set(paths)) == 3
print(f'PASS: {checks} isolated offline runner cases; no real Maven, Java, curl or providers executed.')
PY

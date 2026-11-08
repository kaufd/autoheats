#!/usr/bin/env python3
"""Reproduce the data audit with Python's standard library, git, and JDK 17.

Run: python3 tools/thermal-audit/audit.py > /tmp/thermal-audit.json
The deleted field CSV is read from its fixed Git revision without restoring it.
All compilation goes into a temporary directory. No parameter fitting is done.
"""
import csv
import importlib.util
import io
import json
from pathlib import Path
import random
import subprocess
import sys
import tempfile
from datetime import datetime

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / 'docs/superpowers/specs/2026-09-10-auto-heat-thermal-model-reference.py'
REVISION = '69cb0fc64f3c38f2811c6a4382c30d30446f161c'
CSV_PATH = 'docs/autoheat-data-20260912-131954.csv'
spec = importlib.util.spec_from_file_location('thermal_reference', REFERENCE)
ref = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = ref
spec.loader.exec_module(ref)


def java_lines(classes, commands):
    result = subprocess.run(['java', '-cp', str(classes), 'com.wt.airconditioner.ThermalAudit'],
                            input='\n'.join(commands)+'\n', text=True,
                            capture_output=True, check=True)
    return result.stdout.splitlines()


def raw_data():
    contents = subprocess.check_output(['git', 'show', f'{REVISION}:{CSV_PATH}'], cwd=ROOT, text=True)
    rows = list(csv.DictReader(io.StringIO(contents), delimiter=';'))
    for row in rows:
        row['date'] = datetime.fromisoformat(row['time'])
    return rows


def field_replay(rows, classes):
    # The only known subjective endpoint is recorded in the design, not the CSV.
    start = next(i for i, r in enumerate(rows) if r['driver_level'] == '3')
    stop = next(i for i in range(start+1, len(rows)) if rows[i]['driver_level'] == '0')
    profiles = [ref.Parameters()] + [p for p in ref.parameter_bank() if p.body > 0]
    output = []
    for initial in (17.0, 20.0, 23.0):
        states = [ref.State(initial, initial) for _ in profiles]
        for i in range(start, stop):
            # Piecewise constant last received air; use event-exact durations/levels.
            row = rows[i]
            duration = (rows[i+1]['date'] - row['date']).total_seconds()/60
            env = .8*float(row['cabin_c']) + .2*(float(row['outside_raw'])/2-40)
            states = [ref.advance(s, int(row['driver_level']), env, p, duration=duration)
                      for s, p in zip(states, profiles)]
        output.append(dict(initial=initial, base=states[0].surface,
                           occupied_bank_low=min(s.surface for s in states[1:]),
                           occupied_bank_high=max(s.surface for s in states[1:])))
    base = ref.Parameters()
    corroboration = []
    for temperature, minutes in [(-10, 5), (-10, 7), (20, 2), (20, 3)]:
        s = ref.advance(ref.State(temperature, temperature), 3, temperature, base, duration=minutes)
        corroboration.append(dict(air=temperature, minutes=minutes, predicted_surface=s.surface))
    events = [' '.join(map(str,[(r['date']-rows[0]['date']).total_seconds()*1000,
              float(r['cabin_c']),float(r['outside_raw'])/2-40,
              int(r['driver_level']),int(r['ignition'])])) for r in rows]
    observed = subprocess.run(['java','-cp',str(classes),
                              'com.wt.airconditioner.ThermalAudit','replay'],
                              input='\n'.join(events)+'\n',text=True,capture_output=True,check=True)
    bounds = list(map(float,observed.stdout.splitlines()[stop].split()))
    return dict(rows=len(rows), start=rows[start]['time'], stop=rows[stop]['time'],
                heating_seconds=(rows[stop]['date']-rows[start]['date']).total_seconds(),
                cabin_range=[min(float(r['cabin_c']) for r in rows[start:stop+1]),
                             max(float(r['cabin_c']) for r in rows[start:stop+1])],
                replays=output,unknown_history_at_stop=dict(base=bounds[:2],bank=bounds[2:]),
                qualitative_observations=corroboration)


def compare_ports(classes):
    rng = random.Random(20261001)
    commands, expected = [], []
    profiles = ref.parameter_bank()
    for _ in range(400):
        p = rng.choice(profiles)
        mat, surface, env = [rng.uniform(-30, 50) for _ in range(3)]
        level, duration = rng.randrange(4), rng.uniform(0, 30)
        commands.append('step '+' '.join(map(str, [mat,surface,level,env,duration,
                          p.p,p.a,p.r,p.air,p.body,p.body_temperature,*p.duty[1:]])))
        expected.append(ref.advance(ref.State(mat,surface),level,env,p,duration=duration))
    observed = [list(map(float, line.split())) for line in java_lines(classes,commands)]
    worst = max(abs(a-b) for want,got in zip(expected,observed) for a,b in zip(want,got))
    assert worst < 1e-8, worst
    commands, expected = [], []
    for _ in range(40):
        mat, surface, env = [rng.uniform(-25, 42) for _ in range(3)]
        cabin, current, elapsed, seat = rng.uniform(-20, 10), rng.randrange(4), rng.choice([0,1.5,3,10]), rng.randrange(2)
        selected = [p for p in profiles if seat == 1 or p.body > 0]
        scenarios = [ref.Scenario(ref.State(mat,surface),p,lambda t,e=env:e) for p in selected]
        expected.append(ref.choose(scenarios,current,elapsed,cabin)[0][0])
        commands.append('choose '+' '.join(map(str,[mat,surface,env,cabin,current,elapsed,seat])))
    actual = list(map(int,java_lines(classes,commands)))
    mismatches = [i for i,(a,b) in enumerate(zip(expected,actual)) if a!=b]
    assert not mismatches, mismatches
    return dict(thermal_cases=400,maximum_port_error=worst,decision_cases=40,decision_mismatches=len(mismatches))


def main():
    classes_to_compile = ['Seat','LogSink','Scheduler','Air','HeatSequence','PresetSettings',
                          'TemperatureConstants','SeatHeatDriver','AutoHeatEngine','SeatThermalModel',
                          'SeatThermostat','SeatObserver','ModelShadow','ModelHeatDriver']
    sources = [ROOT / 'app/src/main/java/com/wt/airconditioner' / (name+'.java') for name in classes_to_compile]
    with tempfile.TemporaryDirectory(prefix='autoheat-audit-') as directory:
        subprocess.run(['javac','-d',directory,*map(str,sources),str(Path(__file__).with_name('ThermalAudit.java'))],check=True)
        ports = compare_ports(directory)
        result = subprocess.check_output(['java','-cp',directory,'com.wt.airconditioner.ThermalAudit','simulate'],text=True)
        simulations = [json.loads(line) for line in result.splitlines()]
        assert all(s['peak'] <= 39.3 for s in simulations)
        assert all(s['enclosure_error'] <= .02 for s in simulations)
        for simulation in simulations:
            changes = simulation['switches']
            for previous,current in zip(changes,changes[1:]):
                if current[1] > previous[1]:
                    assert current[0]-previous[0] >= 3, changes
        report = dict(source_revision=REVISION,source_csv=CSV_PATH,ports=ports,
                      field=field_replay(raw_data(),directory),simulations=simulations)
        print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()

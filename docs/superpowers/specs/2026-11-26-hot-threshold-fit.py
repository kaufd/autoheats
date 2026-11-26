#!/usr/bin/env python3
"""Fit each bank profile's "hot" threshold to confirmed trips and replay them.

Usage: python3 2026-11-26-hot-threshold-fit.py <folder with autoheat-data-*.csv>

The trip CSVs are not in git (owner's decision). The seven moments below are
the owner's confirmed "hot" marks: the driver lowered level 3 by feeling.
Each profile starts from a seat cooled to its environment (long break).
"""

import csv
import importlib.util
import sys
from dataclasses import replace
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location(
    "reference", HERE / "2026-09-10-auto-heat-thermal-model-reference.py")
ref = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ref)

# CSV file stamp, first row of the level-3 stretch, observed minutes to "hot".
POINTS = [
    ("20260912-131954", "2026-09-12 13:22:34", 3.6),
    ("20261109-191540", "2026-11-09 19:15", 6.6),
    ("20261111-134352", "2026-11-11 13:44", 6.4),
    ("20261111-154646", "2026-11-14 16:32", 5.5),
    ("20261116-103754", "2026-11-16 18:2", 7.0),
    ("20261123-135105", "2026-11-23 13:51", 5.8),
    ("20261123-184727", "2026-11-23 18:47", 5.0),
]


def environment(row):
    """E = 0.8 cabin + 0.2 outside; raw < 0 and 255 are "no data"."""
    cabin = float(row["cabin_c"])
    raw = int(row["outside_raw"])
    return 0.8 * cabin + 0.2 * (raw / 2 - 40) if 0 <= raw < 255 else cabin


def trace(folder, stamp, start):
    """(minute, environment) from the start of level 3 to the end of the file."""
    with open(Path(folder) / f"autoheat-data-{stamp}.csv") as f:
        rows = [r for r in csv.DictReader(f, delimiter=";")
                if r["cabin_c"] and r["time"] >= start]
    t0 = datetime.strptime(rows[0]["time"], "%Y-%m-%d %H:%M:%S")
    return [((datetime.strptime(r["time"], "%Y-%m-%d %H:%M:%S") - t0).total_seconds() / 60,
             environment(r)) for r in rows]


def environment_at(points, minute):
    value = points[0][1]
    for at, e in points:
        if at <= minute:
            value = e
    return value


def minutes_to(points, profile, threshold, limit=20.0):
    """Level 3 from a cooled seat until the surface reaches the threshold."""
    state = ref.State(points[0][1], points[0][1])
    minute, dt = 0.0, ref.MODEL_STEP
    while minute < limit:
        after = ref.advance(state, 3, environment_at(points, minute), profile, duration=dt)
        if after.surface >= threshold:
            return minute + dt * (threshold - state.surface) / (after.surface - state.surface)
        state, minute = after, minute + dt
    return float("inf")


def fit(traces, observed, profile):
    best = None
    for tenth in range(250, 460):
        predicted = [minutes_to(points, profile, tenth / 10) for points in traces]
        if float("inf") in predicted:
            break
        rms = (sum((p - o) ** 2 for p, o in zip(predicted, observed)) / len(observed)) ** 0.5
        if best is None or rms < best[0]:
            best = (rms, tenth / 10)
    return best


def main(folder):
    traces = [trace(folder, stamp, start) for stamp, start, _ in POINTS]
    observed = [minutes for _, _, minutes in POINTS]
    # Level 3 has full power under both duty hypotheses: one row per seed and contact.
    names = ["base", "slow", "fast"]
    contacts = ["empty", "light", "firm"]
    bank = ref.parameter_bank()
    print("profile        fitted hot  in bank  time rms, min")
    for i, profile in enumerate(bank):
        if i % 6 >= 3:
            continue
        rms, hot = fit(traces, observed, replace(profile, hot=40.0))
        print(f"{names[i // 6]:5} {contacts[i % 3]:6}  {hot:9.1f}  {profile.hot:7.1f}  {rms:13.2f}")

    driver = [p for i, p in enumerate(bank) if i % 3]  # the driver is never empty
    print("\nclosed loop, driver bank: level 3 until / observed hot")
    for points, (_, start, minutes) in zip(traces, POINTS):
        e0 = points[0][1]
        cabin_at = lambda t, pts=points: environment_at(pts, t)  # E stands in for cabin
        result = ref.closed_loop([ref.State(e0, e0)] * len(driver), driver, cabin_at,
                                 e0, minutes=20)
        first = next((t for t, level in result["switches"] if level < 3), None)
        print(f"{start[5:16]:12} {first} / {minutes} min, switches {result['switches']}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])

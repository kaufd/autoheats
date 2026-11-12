#!/usr/bin/env python3
"""Executable checks of the design, independent of Android and application code.

Run from any directory with Python 3.9+: python3 <this-file>
Numbers printed here are synthetic predictions, not measurements of a Changan.
"""

from dataclasses import dataclass, replace
from math import ceil, exp, isclose, isfinite
from typing import Callable, NamedTuple, Tuple


MODEL_STEP = 1.0 / 30.0  # Two seconds; controller/telemetry ticks may be longer.


class State(NamedTuple):
    mat: float
    surface: float


@dataclass(frozen=True)
class Parameters:
    # Minutes and degrees Celsius; r = C_mat / C_surface.
    p: float = 43.0
    a: float = 1.0 / 1.75
    r: float = 1.0
    air: float = 1.0 / 24.5
    body: float = 0.0
    body_temperature: float = 33.0
    duty: Tuple[float, ...] = (0.0, 0.40, 0.66, 1.0)
    setpoint: Tuple[float, ...] = (0.0, 35.0, 40.0, 45.0)

    def __post_init__(self):
        assert all(isfinite(x) for x in (
            self.p, self.a, self.r, self.air, self.body,
            self.body_temperature, *self.duty, *self.setpoint))
        assert self.p > 0 and self.a > 0 and self.r > 0
        assert self.air >= 0 and self.body >= 0
        assert len(self.duty) == len(self.setpoint) == 4
        assert self.duty[0] == 0 and all(0 <= x <= 1 for x in self.duty)
        assert all(x <= y for x, y in zip(self.duty, self.duty[1:]))
        assert self.setpoint[1] <= self.setpoint[2] <= self.setpoint[3]


@dataclass(frozen=True)
class Scenario:
    state: State
    parameters: Parameters
    environment: Callable[[float], float]


def step(state, level, environment, parameters=Parameters(), dt=0.25):
    """One simultaneous Euler step; q is the average applied heating rate.

    Limit added energy, never remove stored energy to reach a new setpoint.
    The caller must subdivide long steps using advance().
    """
    p = parameters
    assert level in range(4) and dt > 0
    assert dt * p.a <= 1 and dt * (p.r * p.a + p.air + p.body) <= 1
    loss = p.a * (state.mat - state.surface)
    passive_mat = state.mat - dt * loss
    available = p.p * p.duty[level]
    heating = (min(available, max(0.0, (p.setpoint[level] - passive_mat) / dt))
               if level else 0.0)
    surface_rate = (p.r * loss - p.air * (state.surface - environment)
                    + p.body * (p.body_temperature - state.surface))
    return State(passive_mat + dt * heating,
                 state.surface + dt * surface_rate), heating


def advance(state, level, environment, parameters=Parameters(), duration=0.25,
            max_step=MODEL_STEP):
    assert duration >= 0 and max_step > 0
    p = parameters
    remaining = duration
    bound = min(max_step, 0.5 / p.a, 0.5 / (p.r * p.a + p.air + p.body))
    while remaining > 1e-10:
        dt = min(remaining, bound)
        state, _ = step(state, level, environment, p, dt)
        remaining -= dt
    return state


def cabin_forecast(cabin, outside, slope=0.0, mixing=0.2, decay=2.0):
    # slope is an observed, filtered slope, not inferred from coolant.
    assert isfinite(slope) and 0 <= mixing <= 1 and decay > 0
    slope = min(3.0, max(-3.0, slope))
    return lambda t: ((1 - mixing) *
                      (cabin + slope * decay * (1 - exp(-t / decay)))
                      + mixing * outside)


def parameter_bank():
    """18 explicitly hypothetical profiles for sensitivity experiments.

    These are representative combinations, not guaranteed physical bounds.
    No probability or measured confidence is attached to them.
    """
    base = Parameters()
    seeds = [base, replace(base, p=base.p * 0.7, r=0.5, air=base.air * 2),
             replace(base, p=base.p * 1.3, r=2.0, air=base.air * 0.5)]
    profiles = []
    for seed in seeds:
        for duty in [base.duty, (0.0, 1.0, 1.0, 1.0)]:
            for air_factor, body, temperature in [(1.0, 0.0, 33.0),
                                                   (0.75, 0.02, 32.0),
                                                   (0.5, 0.08, 35.0)]:
                profiles.append(replace(seed, duty=duty,
                                        air=seed.air * air_factor,
                                        body=body, body_temperature=temperature))
    return profiles


def plan_candidates(current, elapsed, max_level, horizon=6, dwell=3.0):
    """At most one later change; ordinary upward changes obey dwell.

    Lower levels are allowed immediately. Restrictions apply over the whole
    predicted plan. current=0 may be promoted immediately on a fresh start
    by supplying elapsed=dwell.
    """
    for first in range(max_level + 1):
        if first > current and elapsed < dwell:
            continue
        age = elapsed if first == current else 0.0
        yield (first,) * horizon
        for when in range(1, horizon):
            for second in range(max_level + 1):
                if second == first or (second > first and age + when < dwell):
                    continue
                yield (first,) * when + (second,) * (horizon - when)


def discomfort(temperature):
    return max(0.0, 33.0 - temperature) + 1.3 * max(0.0, temperature - 36.0)


def evaluate(plan, scenario, limit=39.0, max_step=MODEL_STEP):
    state = scenario.state
    total = 0.0
    peak = state.surface
    # Include boundaries and integrate discomfort with the trapezoidal rule.
    subdivisions = ceil(1.0 / max_step)
    dt = 1.0 / subdivisions
    for minute, level in enumerate(plan):
        for substep in range(subdivisions):
            before = state
            state = advance(state, level, scenario.environment(minute + substep * dt),
                            scenario.parameters, duration=dt, max_step=max_step)
            total += dt * (discomfort(before.surface) + discomfort(state.surface)) / 2
            peak = max(peak, state.surface)
    return total / len(plan), peak, peak <= limit + 1e-9


def choose(scenarios, current, elapsed, cabin, horizon=6, force_off=False,
           max_step=MODEL_STEP):
    """Reference receding-horizon choice, conditional on supplied scenarios.

    This is not an observer, persistence layer, or Android integration.
    """
    assert scenarios and horizon >= 3
    zero = (0,) * horizon
    if force_off:
        return zero, None, "forced off"
    # Explicit comfort policy: boost only at cabin <= 0 C.
    max_level = 3 if cabin <= 0 else 2
    best = None
    for plan in plan_candidates(current, elapsed, max_level, horizon):
        costs = []
        admissible = True
        for scenario in scenarios:
            cost, _, within_limit = evaluate(plan, scenario, max_step=max_step)
            if not within_limit:
                admissible = False
                break
            costs.append(cost)
        if not admissible:
            continue
        changes = int(plan[0] != current) + sum(x != y for x, y in zip(plan, plan[1:]))
        score = max(costs) + 0.2 * changes
        # Equal scores to 1e-9 C: preserve current action, then lower mean level.
        key = (round(score, 9), plan[0] != current, sum(plan), plan)
        if best is None or key < best[0]:
            best = (key, plan, score)
    if best is None:
        return zero, None, "no admissible plan; heating off"
    return best[1], best[2], "planned"


def must_coast(scenarios, current):
    """Intermediate control check: another minute on this level is too hot."""
    return current > 0 and any(not evaluate((current,), s)[2] for s in scenarios)


def closed_loop(initial_states, profiles, cabin_at, outside, minutes=120, horizon=6,
                use_trend=False):
    """Synthetic loop with immediate command confirmation and known profiles.

    Every profile is propagated independently under the same applied command.
    By default, forecast cabin temperature is held constant over each horizon.
    use_trend=True enables the research alternative: last one-minute slope,
    together with a constant-cabin scenario. Actual measured cabin temperature
    still changes every tick in both variants.
    """
    assert len(initial_states) == len(profiles)
    states = list(initial_states)
    current, elapsed = 0, 3.0
    switches = []
    peak = max(s.surface for s in states)
    outside_zone = 0.0
    guard_stops = 0
    integrals = [0.0 for _ in profiles]
    for minute in range(minutes):
        cabin = cabin_at(minute)
        slope = cabin - cabin_at(minute - 1) if minute else 0.0
        slopes = (0.0, slope) if use_trend else (0.0,)
        scenarios = [Scenario(s, p, cabin_forecast(cabin, outside, v))
                     for s, p in zip(states, profiles) for v in slopes]
        plan, _, _ = choose(scenarios, current, elapsed, cabin, horizon=horizon)
        if plan[0] != current:
            current, elapsed = plan[0], 0.0
            switches.append((float(minute), current))
        for tick in range(4):
            now = minute + tick / 4
            cabin = cabin_at(now)
            scenarios = [Scenario(s, p, cabin_forecast(cabin, outside, v))
                         for s, p in zip(states, profiles) for v in slopes]
            if must_coast(scenarios, current):
                current, elapsed = 0, 0.0
                switches.append((now, current))
                guard_stops += 1
            environment = 0.8 * cabin + 0.2 * outside
            previous = states
            states = [advance(s, current, environment, p)
                      for s, p in zip(states, profiles)]
            for i, (before, after) in enumerate(zip(previous, states)):
                integrals[i] += 0.125 * (discomfort(before.surface)
                                         + discomfort(after.surface))
            peak = max(peak, max(s.surface for s in states))
            if any(s.surface < 33 or s.surface > 36 for s in states):
                outside_zone += 0.25
            elapsed += 0.25
    return dict(final=(min(s.surface for s in states), max(s.surface for s in states)),
                peak=peak, outside_minutes=outside_zone, switches=switches,
                guard_stops=guard_stops, discomfort_integral=max(integrals))


def forecast_study():
    """Reproduce the decision to use a constant cabin forecast by default.

    Forecasts do affect some first actions. Their measured utility in these
    synthetic trips is small; this is not proof of irrelevance in all states.
    """
    p = Parameters()
    state = State(35, 33)
    base = Scenario(state, p, cabin_forecast(-15, -15, 0))
    trend = Scenario(state, p, cabin_forecast(-15, -15, 3))
    for dt in [MODEL_STEP, 1 / 120]:
        constant = choose([base], 1, 3, -15, max_step=dt)
        combined = choose([base, trend], 1, 3, -15, max_step=dt)
        assert constant[0][0] == 2 and combined[0][0] == 1
        print("Counterexample, step", dt * 60, "seconds: constant", constant[0],
              "constant + trend", combined[0])

    cases = [
        ("cold warming", State(-15, -15),
         lambda t: -15 if t < 5 else 22 - 37 * exp(-(t - 5) / 7)),
        ("warm seat, rapid warming", State(35, 33),
         lambda t: -15 + 6 * (1 - exp(-t / 2))),
        ("warm seat, rapid cooling", State(35, 33),
         lambda t: 5 - 6 * (1 - exp(-t / 2))),
        ("long cabin warmup", State(-15, -15),
         lambda t: -15 + min(37, max(0, t - 5) * 1.5)),
        ("cooling then recovery", State(40, 36),
         lambda t: 22 if t < 5 else 22 - 12 * (1 - exp(-(t - 5) / 2))
         if t < 12 else 10 + 12 * (1 - exp(-(t - 12) / 3))),
    ]
    changed = 0
    for name, initial, cabin in cases:
        constant = closed_loop([initial], [p], cabin, -15, minutes=40)
        trend_run = closed_loop([initial], [p], cabin, -15, minutes=40, use_trend=True)
        changed += constant["switches"] != trend_run["switches"]
        for mode, result in [("constant", constant), ("trend", trend_run)]:
            print(name, mode, "integral", round(result["discomfort_integral"], 6),
                  "peak", round(result["peak"], 6), "outside minutes",
                  result["outside_minutes"], "switches", result["switches"])
    assert changed == 2
    print("PASS: first-action sensitivity survives step refinement;",
          changed, "of", len(cases), "closed-loop schedules differ")


def checks():
    p = Parameters()
    # Switching must conserve previously stored heat; stronger heat cannot
    # produce a colder seat from identical initial conditions.
    start = State(45, 35)
    one, _ = step(start, 1, 0)
    assert one.mat > 35
    final = [advance(start, u, 0, duration=1) for u in range(4)]
    assert all(final[i].surface <= final[i + 1].surface for i in range(3))
    crossing, _ = step(State(44.9, 35), 3, 0)
    assert crossing.mat <= 45
    zero, heat = step(start, 0, 0)
    assert heat == 0 and zero.surface > start.surface

    # Energy conservation with independent effective heat capacities.
    for ratio in [0.5, 1, 2]:
        isolated = replace(p, r=ratio, air=0, body=0)
        state = State(45, 10)
        initial_energy = state.mat + state.surface / ratio
        state = advance(state, 0, 0, isolated, duration=30)
        assert isclose(state.mat + state.surface / ratio, initial_energy, abs_tol=1e-9)
        assert 10 <= state.surface <= state.mat <= 45
        heated, applied = step(State(20, 10), 3, 0, isolated)
        assert isclose(heated.mat + heated.surface / ratio - (20 + 10 / ratio),
                       0.25 * applied, abs_tol=1e-9)

    # A changed occupant profile changes derivatives, not the state itself.
    occupied = replace(p, body=0.08, body_temperature=33)
    assert advance(State(10, 10), 0, 10, occupied).surface > 10
    assert advance(State(40, 40), 0, 40, occupied).surface < 40
    # Pure cooling converges to the environment; dt=15s needs no snapping.
    cooled = advance(start, 0, -15, duration=1000)
    assert abs(cooled.mat + 15) < 1e-6 and abs(cooled.surface + 15) < 1e-6

    # Check discretization against a one-second calculation, not old timings.
    trajectories = []
    for dt in [0.25, 1 / 12, 1 / 60]:
        state = State(-10, -10)
        for level, duration in [(3, 3), (2, 3), (0, 3), (1, 6)]:
            state = advance(state, level, -10, duration=duration, max_step=dt)
        trajectories.append(state)
    assert abs(trajectories[0].surface - trajectories[2].surface) < 0.3

    # Final values alone hide the first-minute error. Compare whole traces,
    # including peaks, across every deliberately varied physical profile.
    bank = parameter_bank()
    worst_trace_error = 0.0
    for profile in bank:
        traces = []
        for dt in [MODEL_STEP, 1 / 120]:
            state = State(-15, -15)
            trace = []
            for level, duration in [(3, 3), (2, 3), (0, 3), (1, 6)]:
                for _ in range(duration * 4):
                    state = advance(state, level, -15, profile, max_step=dt)
                    trace.append(state.surface)
            traces.append(trace)
        worst_trace_error = max(worst_trace_error,
                                max(abs(x - y) for x, y in zip(*traces)))
    assert worst_trace_error < 0.3

    # Warm-cabin behavior is a policy, independent of the prediction horizon.
    warm = [Scenario(State(5, 5), p, lambda t: 5)]
    for horizon in [3, 4, 6, 8]:
        plan, _, _ = choose(warm, 0, 3, cabin=5, horizon=horizon)
        assert max(plan) <= 2
    cold = [Scenario(State(-15, -15), p, lambda t: -15)]
    plan, _, _ = choose(cold, 0, 3, cabin=-15)
    assert plan[0] == 3
    # A forced stop and excessively warm hypotheses override dwell.
    assert choose(cold, 3, 0, -15, force_off=True)[0][0] == 0
    hot = [Scenario(State(45, 42), p, lambda t: 0)]
    assert choose(hot, 3, 0, 0)[0][0] == 0
    assert must_coast(hot, 3)
    for plan in plan_candidates(1, 0, 3):
        assert plan[0] <= 1
        last_level, age = 1, 0
        for level in plan:
            if level != last_level:
                if level > last_level:
                    assert age >= 3
                age = 0
            age += 1
            last_level = level
    # The old initialization cannot distinguish histories with identical air.
    cold_seat, warm_seat = State(-10, -10), State(40, 36)
    assert advance(cold_seat, 0, 5, duration=10).surface < 5
    assert advance(warm_seat, 0, 5, duration=10).surface > 5
    # Both power hypotheses and all contact profiles remain numerically finite.
    assert len(bank) == 18
    for profile in bank:
        state = State(-20, -20)
        for level in [3, 2, 0, 1]:
            state = advance(state, level, -20, profile, duration=6)
            assert all(isfinite(t) and -20 <= t <= 45 for t in state)
    print("PASS: thermal invariants, occupancy, cooling, step convergence, policy and dwell")
    print("Switch 45/35 C -> levels 0..3; surface after 1 min:",
          ", ".join(f"{s.surface:.3f}" for s in final))
    print("44.9 C mat at level 3; next mat:", f"{crossing.mat:.3f}")
    print("15/5/1 second steps; final surface:",
          ", ".join(f"{s.surface:.3f}" for s in trajectories))
    print("Worst whole-trace error, 2 vs 0.5 seconds, 18 profiles:",
          f"{worst_trace_error:.3f}")
    for label, scenarios, cabin in [("cold reference", cold, -15),
                                     ("warm reference", warm, 5),
                                     ("cold 18-profile bank", [Scenario(State(-15, -15), b,
                                                                       lambda t: -15)
                                                               for b in bank], -15)]:
        plan, score, reason = choose(scenarios, 0, 3, cabin)
        print(label, "plan", plan, "cost", None if score is None else round(score, 3), reason)

    # Closed-loop regressions check outcomes, not a prescribed cascade.
    cold_run = closed_loop([State(-15, -15)], [p], lambda t: -15, -15)
    warm_run = closed_loop([State(5, 5)], [p], lambda t: 5, 5)
    warming = lambda t: -15 if t < 5 else 22 - 37 * exp(-(t - 5) / 7)
    warming_run = closed_loop([State(-15, -15)], [p], warming, -15)
    restart_run = closed_loop([State(45, 38)], [p], lambda t: 22, -15, minutes=60)
    bank_run = closed_loop([State(-15, -15)] * len(bank), bank,
                           lambda t: -15, -15, minutes=40)
    assert all(level <= 2 for _, level in warm_run["switches"])
    assert 33 <= warming_run["final"][0] <= 36
    assert restart_run["switches"] and restart_run["switches"][0][0] > 0
    # The initially hot mat can produce an unavoidable peak even at zero.
    # The controller must not add to that peak; it cannot actively cool.
    passive_peak = evaluate((0,) * 6, Scenario(State(45, 38), p, lambda t: 14.6))[1]
    assert restart_run["peak"] <= passive_peak + 0.01
    for name, result in [("120min cold", cold_run), ("120min +5", warm_run),
                         ("120min warming", warming_run), ("60min warm seat", restart_run),
                         ("40min 18-profile stress", bank_run)]:
        if result is not restart_run:
            assert result["peak"] <= 39.3
        print(name, "final", tuple(round(t, 3) for t in result["final"]),
              "peak", round(result["peak"], 3), "minutes outside zone",
              result["outside_minutes"], "switch count", len(result["switches"]),
              "intermediate stops", result["guard_stops"])
    print("PASS: closed-loop regressions, resumed heating, and handling of unavoidable heat")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forecast-study", action="store_true",
                        help="compare constant-cabin and trend forecasts")
    args = parser.parse_args()
    if args.forecast_study:
        forecast_study()
    else:
        checks()

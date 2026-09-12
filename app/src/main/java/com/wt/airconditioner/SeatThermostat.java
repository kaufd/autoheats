package com.wt.airconditioner;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор уровня подогрева по тепловой модели: перебор планов на горизонт с
 * минимаксом по набору сценариев. Заменит собой каскад по таймерам из
 * {@link AutoHeatEngine}, но пока никуда не подключён — параметры теплообмена
 * не откалиброваны, и до замеров остывания включать управление нельзя.
 *
 * Сценарии приходят готовыми: наблюдатель, хранение снимков и разворачивание
 * неизвестной истории — не здесь. Класс ничего не знает ни об Android, ни о
 * том, откуда взялись состояния.
 */
final class SeatThermostat {

    /** Горизонт планирования в целых минутах. */
    private static final int HORIZON = 6;

    /**
     * Обычное повышение — не раньше трёх минут после подтверждённой смены
     * уровня. Понижение разрешено сразу.
     */
    private static final double DWELL_MINUTES = 3.0;

    /** Штраф за одно переключение, °C: интеграл цены делится на горизонт. */
    private static final double SWITCH_PENALTY = 0.2;

    /**
     * Ограничитель расчётного комфорта. Не гарантия фактической температуры:
     * датчика поверхности нет.
     */
    private static final double GUARD_CELSIUS = 39.0;

    private static final double COMFORT_LOW_CELSIUS = 33.0;
    private static final double COMFORT_HIGH_CELSIUS = 36.0;

    /** Перегрев неприятнее недогрева — исходное предпочтение владельца. */
    private static final double OVERHEAT_WEIGHT = 1.3;

    /** Тройка разрешена только при достоверном салоне не выше этой температуры. */
    private static final double TOP_LEVEL_CABIN_CELSIUS = 0.0;

    /** Цены считаются равными с этой точностью, °C. */
    private static final double TIE_CELSIUS = 1e-9;

    /**
     * Одна гипотеза о сиденье: состояние, параметры теплообмена и окружение.
     *
     * Окружение постоянно на всём горизонте: прогноз воздуха не строится, а
     * берутся текущие температуры. Решение принято по расчёту — трендовый
     * прогноз почти не меняет команду; контрпример лежит в эталоне
     * ({@code --forecast-study}).
     */
    static final class Scenario {
        final SeatThermalModel.State state;
        final SeatThermalModel.Parameters parameters;
        final double environment;

        Scenario(SeatThermalModel.State state, SeatThermalModel.Parameters parameters,
                double environment) {
            this.state = state;
            this.parameters = parameters;
            this.environment = environment;
        }
    }

    /** Уровень на ближайшую минуту и человекочитаемое объяснение для лога. */
    static final class Decision {
        final int level;
        final String reason;

        Decision(int level, String reason) {
            this.level = level;
            this.reason = reason;
        }
    }

    /** Цена плана и пик поверхности в одном сценарии. */
    private static final class Outcome {
        final double discomfort;
        final double peak;

        Outcome(double discomfort, double peak) {
            this.discomfort = discomfort;
            this.peak = peak;
        }
    }

    private SeatThermostat() {
    }

    /**
     * Обычный выбор команды — раз в минуту.
     *
     * @param elapsedMinutes время с последней подтверждённой смены уровня;
     *        при первом запуске в сессии ожидание не добавляется искусственно
     * @param cabinCelsius достоверная температура салона; null — недостоверна,
     *        и тогда тройка не разрешается
     */
    static Decision choose(List<Scenario> scenarios, int currentLevel, double elapsedMinutes,
            Double cabinCelsius) {
        int maxLevel = cabinCelsius != null && cabinCelsius <= TOP_LEVEL_CABIN_CELSIUS ? 3 : 2;
        int[] best = null;
        double bestScore = 0;
        for (int[] plan : planCandidates(currentLevel, elapsedMinutes, maxLevel)) {
            /**
             * Минимакс: план допустим, только если ни один сценарий не выходит
             * за предел, и оценивается по худшему из них. Усреднение здесь
             * подменило бы неопределённость одним заведомо точным числом.
             */
            double worst = Double.NEGATIVE_INFINITY;
            boolean admissible = true;
            for (Scenario scenario : scenarios) {
                Outcome outcome = evaluate(plan, scenario);
                if (outcome.peak > GUARD_CELSIUS + TIE_CELSIUS) {
                    admissible = false;
                    break;
                }
                worst = Math.max(worst, outcome.discomfort);
            }
            if (!admissible) {
                continue;
            }
            double score = worst + SWITCH_PENALTY * switchCount(plan, currentLevel);
            if (best == null || preferable(score, plan, bestScore, best, currentLevel)) {
                best = plan;
                bestScore = score;
            }
        }
        if (best == null) {
            return new Decision(0, "нет допустимого плана, нагрев выключен");
        }
        return new Decision(best[0], "план " + join(best));
    }

    /**
     * Промежуточная проверка на 15-секундном такте: удержание текущего уровня
     * ещё на минуту перегревает хотя бы один сценарий. Тогда ноль подаётся, не
     * дожидаясь обычного решения.
     */
    static boolean mustCoast(List<Scenario> scenarios, int currentLevel) {
        if (currentLevel <= 0) {
            return false;
        }
        for (Scenario scenario : scenarios) {
            if (evaluate(new int[] {currentLevel}, scenario).peak > GUARD_CELSIUS + TIE_CELSIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Планы с не более чем одной будущей сменой уровня: постоянный уровень и
     * «первый до минуты j, затем второй». Это ограниченный перебор, а не
     * оптимизация всех последовательностей: применяется только первая минута,
     * а через минуту задача решается заново.
     */
    private static List<int[]> planCandidates(int current, double elapsed, int maxLevel) {
        List<int[]> plans = new ArrayList<>();
        for (int first = 0; first <= maxLevel; first++) {
            if (first > current && elapsed < DWELL_MINUTES) {
                continue;
            }
            /** Ожидание продолжается только там, где уровень не меняется. */
            double age = first == current ? elapsed : 0.0;
            int[] constant = new int[HORIZON];
            java.util.Arrays.fill(constant, first);
            plans.add(constant);
            for (int when = 1; when < HORIZON; when++) {
                for (int second = 0; second <= maxLevel; second++) {
                    if (second == first || (second > first && age + when < DWELL_MINUTES)) {
                        continue;
                    }
                    int[] plan = new int[HORIZON];
                    java.util.Arrays.fill(plan, 0, when, first);
                    java.util.Arrays.fill(plan, when, HORIZON, second);
                    plans.add(plan);
                }
            }
        }
        return plans;
    }

    /**
     * Средняя цена отклонения от зоны комфорта за горизонт и пик поверхности.
     * Интеграл считается методом трапеций; начальное состояние входит в пик.
     */
    private static Outcome evaluate(int[] plan, Scenario scenario) {
        SeatThermalModel.State state = scenario.state;
        int subdivisions = (int) Math.ceil(1.0 / SeatThermalModel.MAX_STEP_MINUTES);
        double dt = 1.0 / subdivisions;
        double total = 0;
        double peak = state.surface;
        for (int level : plan) {
            for (int substep = 0; substep < subdivisions; substep++) {
                SeatThermalModel.State before = state;
                state = SeatThermalModel.advance(state, level, scenario.environment,
                        scenario.parameters, dt);
                total += dt * (discomfort(before.surface) + discomfort(state.surface)) / 2;
                peak = Math.max(peak, state.surface);
            }
        }
        return new Outcome(total / plan.length, peak);
    }

    /** Внутри зоны комфорта цена нулевая: ради ровных 35 °C переключаться незачем. */
    private static double discomfort(double surface) {
        return Math.max(0.0, COMFORT_LOW_CELSIUS - surface)
                + OVERHEAT_WEIGHT * Math.max(0.0, surface - COMFORT_HIGH_CELSIUS);
    }

    /** Смена текущего уровня в начале плана считается наравне со сменой внутри. */
    private static int switchCount(int[] plan, int current) {
        int changes = plan[0] != current ? 1 : 0;
        for (int i = 1; i < plan.length; i++) {
            if (plan[i] != plan[i - 1]) {
                changes++;
            }
        }
        return changes;
    }

    /**
     * При равной цене сохраняется текущий первый уровень, затем выбирается
     * меньшая сумма уровней, затем лексикографически меньший план. Без
     * последних двух правил выбор зависел бы от порядка перебора.
     */
    private static boolean preferable(double score, int[] plan, double bestScore, int[] best,
            int current) {
        if (Math.abs(score - bestScore) > TIE_CELSIUS) {
            return score < bestScore;
        }
        boolean changes = plan[0] != current;
        boolean bestChanges = best[0] != current;
        if (changes != bestChanges) {
            return bestChanges;
        }
        int sum = 0;
        int bestSum = 0;
        for (int i = 0; i < plan.length; i++) {
            sum += plan[i];
            bestSum += best[i];
        }
        if (sum != bestSum) {
            return sum < bestSum;
        }
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] != best[i]) {
                return plan[i] < best[i];
            }
        }
        return false;
    }

    private static String join(int[] plan) {
        StringBuilder text = new StringBuilder();
        for (int level : plan) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(level);
        }
        return text.toString();
    }
}

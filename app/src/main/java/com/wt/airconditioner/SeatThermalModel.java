package com.wt.airconditioner;

/**
 * Тепловая модель подушки: мат и поверхность как два узла с собственными
 * эффективными теплоёмкостями. Заменяет собой таблицу из тридцати констант
 * {@link TemperatureConstants}, но пока ни на что не влияет — параметры не
 * откалиброваны, и управление на неё не переведено.
 *
 * Спецификация и расчётный эталон — в {@code docs/superpowers/specs/}:
 * порт обязан повторять числа эталона, иначе проверки спецификации к
 * приложению не относятся.
 *
 * Ни одной ссылки на Android: время в минутах, температуры в °C.
 */
final class SeatThermalModel {

    /**
     * Максимальный внутренний шаг — две секунды. Пятисекундный не проходит
     * допуск 0.3 °C на переходных участках, а пятнадцатисекундный ошибается на
     * целый градус во время разгона.
     */
    static final double MAX_STEP_MINUTES = 1.0 / 30.0;

    /** Два непрерывных состояния одного сиденья. */
    static final class State {
        /** Эффективная температура нагревательного мата. */
        final double mat;
        /** Эффективная температура поверхности подушки. */
        final double surface;

        State(double mat, double surface) {
            this.mat = mat;
            this.surface = surface;
        }
    }

    /**
     * Нормированные параметры теплообмена: абсолютные ватты и теплоёмкости по
     * этим уравнениям раздельно не определяются.
     *
     * Значения по умолчанию — прежние приближения и гипотезы, а не измерения:
     * калибровки на сентябрь 2026 ещё нет. Поля открыты для записи, чтобы
     * сценарий мог поменять одну гипотезу, не переписывая остальные семь.
     */
    static final class Parameters {
        /** p = Q/C₁, °C/мин: скорость нагрева изолированного мата, не мощность. */
        double p = 43.0;
        /** a = G₁₂/C₁, 1/мин: связь мата с поверхностью. */
        double a = 1.0 / 1.75;
        /** r = C₁/C₂: равные теплоёмкости — гипотеза базового примера. */
        double r = 1.0;
        /** cₐ = Gₐ/C₂, 1/мин. 1/cₐ — не время остывания всей системы. */
        double air = 1.0 / 24.5;
        /** cᵦ = Gᵦ/C₂, 1/мин. Ноль — определение пустого кресла. */
        double body = 0.0;
        /** Эффективная температура контакта через одежду, а не температура тела. */
        double bodyCelsius = 33.0;
        /** Доли мощности уровней 0…3 — гипотеза ШИМ. */
        double[] duty = {0.0, 0.40, 0.66, 1.0};
        /** Уставки уровней 0…3 — оценка владельца, не паспортные значения. */
        double[] setpoint = {0.0, 35.0, 40.0, 45.0};
    }

    private SeatThermalModel() {
    }

    /**
     * Состояние через {@code minutes} минут на постоянном уровне и постоянном
     * окружении. Длинный интервал делится на внутренние подшаги; интервал не
     * округляется до целого их числа.
     */
    static State advance(State state, int level, double environment, Parameters parameters,
            double minutes) {
        double remaining = minutes;
        while (remaining > 1e-10) {
            double dt = Math.min(remaining, stepBound(parameters));
            state = step(state, level, environment, parameters, dt);
            remaining -= dt;
        }
        return state;
    }

    /**
     * Явная схема требует неотрицательных весов пассивного обмена: при быстрых
     * параметрах шаг приходится уменьшать сильнее двух секунд.
     */
    private static double stepBound(Parameters p) {
        return Math.min(MAX_STEP_MINUTES, Math.min(0.5 / p.a,
                0.5 / (p.r * p.a + p.air + p.body)));
    }

    /** Один одновременный шаг Эйлера: оба состояния считаются из старых. */
    private static State step(State state, int level, double environment, Parameters p,
            double dt) {
        double loss = p.a * (state.mat - state.surface);
        double passiveMat = state.mat - dt * loss;
        /**
         * Штатный регулятор уменьшает подводимую мощность до нуля, но не
         * охлаждает мат до новой уставки: ограничивается только добавляемая за
         * шаг энергия. Если естественное остывание ещё не довело мат до
         * уставки, нагрев нулевой, и мат остаётся выше неё.
         */
        double heating = 0.0;
        if (level > 0) {
            double available = p.p * p.duty[level];
            heating = Math.min(available, Math.max(0.0, (p.setpoint[level] - passiveMat) / dt));
        }
        double surfaceRate = p.r * loss
                - p.air * (state.surface - environment)
                + p.body * (p.bodyCelsius - state.surface);
        return new State(passiveMat + dt * heating, state.surface + dt * surfaceRate);
    }
}

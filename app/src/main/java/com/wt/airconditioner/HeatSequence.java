package com.wt.airconditioner;

/**
 * Расписание каскада: длительности уровней 3/2/1 в минутах и температуры
 * досрочного перехода.
 *
 * Отдельно от TemperatureConstants: расписание задаёт и пользователь в
 * пресетах, а таблица авто-режима — лишь один из его источников и уйдёт вместе
 * с каскадом.
 *
 * Порог по умолчанию — минус бесконечность: у пресетов досрочных переходов
 * нет, каскад идёт строго по таймеру.
 */
final class HeatSequence {

    final int level3Minutes;
    final int level2Minutes;
    final int level1Minutes;
    final double level3StepDownCelsius;
    final double level2StepDownCelsius;
    final double level1StepDownCelsius;

    HeatSequence(int level3Minutes, int level2Minutes, int level1Minutes) {
        this(level3Minutes, level2Minutes, level1Minutes,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    HeatSequence(int level3Minutes, int level2Minutes, int level1Minutes,
            double level3StepDownCelsius, double level2StepDownCelsius,
            double level1StepDownCelsius) {
        this.level3Minutes = level3Minutes;
        this.level2Minutes = level2Minutes;
        this.level1Minutes = level1Minutes;
        this.level3StepDownCelsius = level3StepDownCelsius;
        this.level2StepDownCelsius = level2StepDownCelsius;
        this.level1StepDownCelsius = level1StepDownCelsius;
    }

    int minutesFor(int level) {
        switch (level) {
            case 3:
                return level3Minutes;
            case 2:
                return level2Minutes;
            case 1:
                return level1Minutes;
            default:
                return 0;
        }
    }

    /** Температура, при которой уровень можно снизить, не дожидаясь таймера. */
    double stepDownCelsiusFor(int level) {
        switch (level) {
            case 3:
                return level3StepDownCelsius;
            case 2:
                return level2StepDownCelsius;
            case 1:
                return level1StepDownCelsius;
            default:
                return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Сравнение планов: каскад, дошедший до нуля, перезапускается только
     * если расписание сменилось. Без этого повторное событие с той же
     * температурой снова включало бы уровень 3 — бесконечный цикл.
     */
    String planKey() {
        return level3Minutes + "," + level2Minutes + "," + level1Minutes + ","
                + level3StepDownCelsius + "," + level2StepDownCelsius + ","
                + level1StepDownCelsius;
    }
}

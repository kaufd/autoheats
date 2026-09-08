package com.wt.airconditioner;

/**
 * Пользовательский пресет: свои длительности уровней и температура, выше
 * которой пресет не запускается. Порт ManualHeatSettings.
 *
 * Отличие от авторежима принципиальное: порог проверяется только на старте, а
 * дальше каскад идёт строго по таймерам и на температуру не реагирует —
 * человек выбрал расписание сам.
 */
final class PresetSettings {

    /** Потолок из Flutter-версии: длительность уровня ограничена 15 минутами. */
    private static final int MAX_LEVEL_MINUTES = 15;

    final double thresholdCelsius;
    final TemperatureConstants.HeatSequence sequence;

    PresetSettings(double thresholdCelsius, int level3Minutes, int level2Minutes,
            int level1Minutes) {
        this.thresholdCelsius = thresholdCelsius;
        this.sequence = new TemperatureConstants.HeatSequence(
                clamp(level3Minutes), clamp(level2Minutes), clamp(level1Minutes));
    }

    private static int clamp(int minutes) {
        return Math.max(0, Math.min(MAX_LEVEL_MINUTES, minutes));
    }
}

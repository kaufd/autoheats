package com.wt.airconditioner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ряд измерений для калибровки тепловой модели: время, температуры воздуха,
 * ОЖ и фактические уровни подогрева.
 *
 * Отдельно от LogBuffer, хотя оба про диагностику. LogBuffer — кольцо на 500
 * строк в памяти, которое человек читает глазами и сохраняет руками; здесь
 * нужен непрерывный ряд за поездку, переживающий и переполнение, и потерю
 * питания. Общего у них только назначение, а требования противоположны.
 *
 * Файл открывается и закрывается на каждой строке. Выглядит расточительно при
 * записи раз в полминуты, но при выключении зажигания ГУ гаснет мгновенно и
 * уносит процесс с собой (см. SeatHeatService.onIgnition): буферизованный
 * хвост потерялся бы ровно в конце поездки, то есть там, где интереснее всего.
 */
final class CalibrationLog {

    /**
     * Точка с запятой, а не запятая: в полях встречаются массивы значений, и
     * с запятой пришлось бы экранировать. Excel такой файл открывает как есть.
     */
    private static final String SEPARATOR = ";";

    static final String HEADER = "time" + SEPARATOR
            + "reason" + SEPARATOR
            + "cabin_raw" + SEPARATOR
            + "cabin_c" + SEPARATOR
            + "outside_raw" + SEPARATOR
            + "coolant_float" + SEPARATOR
            + "coolant_int" + SEPARATOR
            + "driver_level" + SEPARATOR
            + "passenger_level" + SEPARATOR
            + "ignition" + SEPARATOR
            + modelHeader("driver") + SEPARATOR
            + modelHeader("passenger");

    /** Расширение общее с логом лишь по смыслу; префикс разный не случайно. */
    private static final String PREFIX = "autoheat-data-";
    static final String EXTENSION = ".csv";

    /** Снимок состояния на один момент времени. Поля заполняет вызывающий. */
    static final class Sample {
        String reason;
        Integer cabinRaw;
        Double cabinCelsius;
        Integer outsideRaw;
        float[] coolantFloats;
        int[] coolantInts;
        Integer driverLevel;
        Integer passengerLevel;
        Boolean ignitionOn;
        /** null — фоновая модель по сиденью не готова. */
        Model driverModel;
        Model passengerModel;
    }

    /**
     * Фоновая оценка сиденья: поверхность базового профиля и банка нижней и
     * верхней границей и решение термостата. Оценка — не измерение: датчика
     * поверхности нет.
     */
    static final class Model {
        double baseLow;
        double baseHigh;
        double bankLow;
        double bankHigh;
        /** null — решения нет: зажигание не ON или минута ещё не прошла. */
        Integer level;
    }

    private final File file;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    /** Имя по времени создания: перезапуск сервиса начинает новый файл. */
    CalibrationLog(File directory) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        this.file = new File(directory, PREFIX + stamp + EXTENSION);
    }

    File file() {
        return file;
    }

    /** false — записать не удалось; вызывающий решает, сообщать ли об этом. */
    boolean append(Sample sample) {
        boolean isNew = !file.exists();
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            if (isNew) {
                writer.write(HEADER + "\n");
            }
            writer.write(row(timeFormat.format(new Date()), sample) + "\n");
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /**
     * Одна строка ряда. Статический и package-private ради теста: форматирование
     * — единственная здесь логика, которую можно проверить на JVM, а запись в
     * файл на голове всё равно проверяется руками.
     *
     * Неизвестное значение — пустое поле, а не ноль: ноль градусов и «датчик
     * молчал» при разборе не должны выглядеть одинаково.
     */
    static String row(String time, Sample sample) {
        StringBuilder text = new StringBuilder();
        text.append(time).append(SEPARATOR);
        text.append(sample.reason == null ? "" : sample.reason).append(SEPARATOR);
        text.append(text(sample.cabinRaw)).append(SEPARATOR);
        text.append(sample.cabinCelsius == null
                ? "" : String.format(Locale.US, "%.1f", sample.cabinCelsius)).append(SEPARATOR);
        text.append(text(sample.outsideRaw)).append(SEPARATOR);
        text.append(text(sample.coolantFloats)).append(SEPARATOR);
        text.append(text(sample.coolantInts)).append(SEPARATOR);
        text.append(text(sample.driverLevel)).append(SEPARATOR);
        text.append(text(sample.passengerLevel)).append(SEPARATOR);
        text.append(sample.ignitionOn == null ? "" : (sample.ignitionOn ? "1" : "0"));
        appendModel(text, sample.driverModel);
        appendModel(text, sample.passengerModel);
        return text.toString();
    }

    private static String modelHeader(String seat) {
        return seat + "_base_lo" + SEPARATOR
                + seat + "_base_hi" + SEPARATOR
                + seat + "_bank_lo" + SEPARATOR
                + seat + "_bank_hi" + SEPARATOR
                + seat + "_model_level";
    }

    /** Пять полей всегда: без модели они пустые, и столбцы не съезжают. */
    private static void appendModel(StringBuilder text, Model model) {
        if (model == null) {
            text.append(SEPARATOR).append(SEPARATOR).append(SEPARATOR).append(SEPARATOR)
                    .append(SEPARATOR);
            return;
        }
        text.append(SEPARATOR).append(celsius(model.baseLow));
        text.append(SEPARATOR).append(celsius(model.baseHigh));
        text.append(SEPARATOR).append(celsius(model.bankLow));
        text.append(SEPARATOR).append(celsius(model.bankHigh));
        text.append(SEPARATOR).append(text(model.level));
    }

    private static String celsius(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String text(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Массив целиком через пробел: какой из индексов несёт температуру ОЖ на
     * этой голове, неизвестно, и выбирать один из них до проверки нельзя.
     */
    private static String text(float[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (float value : values) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(String.format(Locale.US, "%.2f", value));
        }
        return text.toString();
    }

    private static String text(int[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int value : values) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
        return text.toString();
    }
}

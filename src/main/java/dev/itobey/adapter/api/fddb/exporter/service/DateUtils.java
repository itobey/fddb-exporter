package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class DateUtils {

    private DateUtils() {
        // Private constructor to prevent instantiation
    }

    public static LocalDate parseDate(String dateString) throws ManualExporterException {
        try {
            return LocalDate.parse(dateString);
        } catch (DateTimeParseException dateTimeParseException) {
            throw new ManualExporterException("Invalid date format: " + dateString);
        }
    }
}
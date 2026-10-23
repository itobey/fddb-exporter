package dev.itobey.adapter.api.fddb.exporter.exception;

/** A recognized empty diary, rather than missing or malformed page structure. */
public class EmptyDiaryException extends ParseException {

    public EmptyDiaryException() {
        super("no diary entries available for the given day");
    }
}

package at.leoway.api.webuntis;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An absence or lateness entry. Reason, note and excuse text from WebUntis are deliberately not read (L02).
 */
public record Absence(LocalDate date, LocalTime start, LocalTime end) {}

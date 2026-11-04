package at.leoway.api.webuntis;

import java.time.LocalDate;
import java.time.LocalTime;

/** An exam from getExamsForRange (#2): only date, time and subject. */
public record Exam(LocalDate date, LocalTime start, LocalTime end, String subject) {}

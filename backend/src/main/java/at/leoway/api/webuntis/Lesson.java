package at.leoway.api.webuntis;

import java.time.LocalDate;
import java.time.LocalTime;

/** One period from the student's timetable. Only what LeoWay needs; teachers and rooms are dropped. */
public record Lesson(LocalDate date, LocalTime start, LocalTime end, String subject, Status status) {
    public enum Status {
        REGULAR,
        /** Substitution, room change or extra lesson: still a real lesson. */
        IRREGULAR,
        CANCELLED;

        static Status of(String code) {
            if (code == null) return REGULAR;
            return switch (code) {
                case "cancelled" -> CANCELLED;
                case "irregular" -> IRREGULAR;
                default -> REGULAR;
            };
        }
    }

    public boolean takesPlace() {
        return status != Status.CANCELLED;
    }
}

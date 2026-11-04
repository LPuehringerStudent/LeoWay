package at.leoway.api.webuntis;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Picks the lesson that decides when a student must leave: the earliest one that is not cancelled. */
public final class FirstLesson {
    private FirstLesson() {}

    public static Optional<Lesson> on(LocalDate date, List<Lesson> lessons) {
        return lessons.stream()
                .filter(l -> l.date().equals(date) && l.takesPlace())
                .min(Comparator.comparing(Lesson::start));
    }
}

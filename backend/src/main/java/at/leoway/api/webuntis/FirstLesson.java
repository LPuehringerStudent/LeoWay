package at.leoway.api.webuntis;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Picks the lesson that decides when a student must leave: the earliest one that is not cancelled. */
public final class FirstLesson {
    private FirstLesson() {}

    /** The first lesson of a day and whether an exam overlaps it (#63). */
    public record Found(Lesson lesson, boolean exam) {}

    /** True if an exam on the lesson's day overlaps it, even partly (exams can start mid-lesson). */
    public static boolean isExam(Lesson lesson, List<Exam> exams) {
        return exams.stream().anyMatch(e -> e.date() != null && e.start() != null && e.end() != null
                && e.date().equals(lesson.date()) && e.start().isBefore(lesson.end()) && e.end().isAfter(lesson.start()));
    }

    public static Optional<Lesson> on(LocalDate date, List<Lesson> lessons) {
        return lessons.stream()
                .filter(l -> l.date().equals(date) && l.takesPlace())
                .min(Comparator.comparing(Lesson::start));
    }
}

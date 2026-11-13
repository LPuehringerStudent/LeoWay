package at.leoway.api.webuntis;

import java.time.Instant;

/**
 * Fired after a sync stored a different first lesson for a student, so the leave-by time can be recalculated (#58).
 * Either start is null when there was no lesson in the next week.
 */
public record FirstLessonChanged(long studentId, Instant previousStart, Instant currentStart) {}

package at.leoway.api.webuntis;

import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.domain.LatenessEntry;
import at.leoway.api.domain.WebUntisCredential;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Keeps every student's next first lesson up to date in the background (#59, F02, A02) and picks up lateness entries
 * (F07, L03). Times are set in {@code leoway.sync.*}; WebUntis is only asked for what S05 allows.
 *
 * <p>One student's failure never stops the others. Logs carry the student id and the failure reason only, never
 * keys, usernames or timetable data (S01).
 */
@ApplicationScoped
public class TimetableSync {
    private static final Logger LOG = Logger.getLogger(TimetableSync.class);
    static final ZoneId SCHOOL_ZONE = ZoneId.of("Europe/Vienna");

    @Inject
    WebUntisConnection connection;

    @Inject
    Event<FirstLessonChanged> changed;

    Clock clock = Clock.systemUTC();

    public record Result(int synced, int failed) {}

    @Scheduled(identity = "timetable-evening", cron = "{leoway.sync.evening}", timeZone = "Europe/Vienna",
            concurrentExecution = ConcurrentExecution.SKIP)
    void evening() {
        log("evening", syncAll(today().plusDays(1)));
    }

    @Scheduled(identity = "timetable-early", cron = "{leoway.sync.morning-early}", timeZone = "Europe/Vienna",
            concurrentExecution = ConcurrentExecution.SKIP)
    void morningEarly() {
        log("morning", syncAll(today()));
    }

    @Scheduled(identity = "timetable-late", cron = "{leoway.sync.morning-late}", timeZone = "Europe/Vienna",
            concurrentExecution = ConcurrentExecution.SKIP)
    void morningLate() {
        log("morning", syncAll(today()));
    }

    @Scheduled(identity = "lateness-noon", cron = "{leoway.sync.lateness}", timeZone = "Europe/Vienna",
            concurrentExecution = ConcurrentExecution.SKIP)
    void noon() {
        log("lateness", syncLateness(today()));
    }

    /** Syncs the first lesson from {@code from} on for every student whose key still works. */
    public Result syncAll(LocalDate from) {
        return forEachStudent(id -> sync(id, from));
    }

    /**
     * Reads the next first lesson from {@code from} on and replaces the stored one. Fires {@link FirstLessonChanged}
     * if it moved. A rejected key is marked on the connection, then the exception is rethrown.
     */
    public Optional<FirstLessonCache> sync(long studentId, LocalDate from) {
        Optional<Lesson> lesson;
        try {
            lesson = connection.nextFirstLesson(studentId, from);
        } catch (WebUntisException e) {
            if (e.reason() == WebUntisException.Reason.KEY_REJECTED) markRejected(studentId);
            throw e;
        }
        Instant now = clock.instant();
        Instant[] before = new Instant[1];
        Optional<FirstLessonCache> stored = QuarkusTransaction.requiringNew().call(() -> {
            if (WebUntisCredential.findById(studentId) == null) return Optional.empty(); // disconnected meanwhile
            FirstLessonCache c = FirstLessonCache.findById(studentId);
            if (c == null) {
                c = new FirstLessonCache();
                c.studentId = studentId;
            } else {
                before[0] = c.startAt;
            }
            c.lessonDate = lesson.map(Lesson::date).orElse(null);
            c.startAt = lesson.map(l -> at(l.date(), l.start())).orElse(null);
            c.endAt = lesson.map(l -> at(l.date(), l.end())).orElse(null);
            c.subject = lesson.map(Lesson::subject).orElse(null);
            c.syncedAt = now;
            c.persist();
            return Optional.of(c);
        });
        // After the commit, so a recalculation sees the new lesson.
        stored.filter(c -> !Objects.equals(before[0], c.startAt))
                .ifPresent(c -> changed.fire(new FirstLessonChanged(studentId, before[0], c.startAt)));
        return stored;
    }

    /**
     * Stores lateness entries for {@code day}: absences that start in the first real lesson and end within it
     * (glossary). Longer absences are not matched. Entries already stored are not added again.
     */
    public Result syncLateness(LocalDate day) {
        return forEachStudent(id -> {
            record Day(Optional<Lesson> first, List<Absence> absences) {}
            Optional<Day> read = connection.withClient(id, c -> Optional.of(
                    new Day(FirstLesson.on(day, c.timetable(day, day)), c.absences(day, day))));
            read.flatMap(Day::first).ifPresent(first -> read.get().absences().stream()
                    .filter(a -> a.date().equals(day) && isLateness(a, first))
                    .forEach(a -> storeLateness(id, a)));
        });
    }

    static boolean isLateness(Absence a, Lesson first) {
        return !a.start().isBefore(first.start()) && a.start().isBefore(first.end()) && !a.end().isAfter(first.end());
    }

    private void storeLateness(long studentId, Absence a) {
        Instant start = at(a.date(), a.start());
        Instant end = at(a.date(), a.end());
        QuarkusTransaction.requiringNew().run(() -> {
            if (LatenessEntry.count("studentId = ?1 and startAt = ?2 and endAt = ?3", studentId, start, end) > 0) return;
            LatenessEntry e = new LatenessEntry();
            e.studentId = studentId;
            e.lessonDate = a.date();
            e.startAt = start;
            e.endAt = end;
            e.persist();
        });
    }

    private Result forEachStudent(java.util.function.LongConsumer work) {
        List<Long> ids = QuarkusTransaction.requiringNew().call(() -> WebUntisCredential
                .<WebUntisCredential>list("rejectedAt is null order by studentId").stream()
                .map(c -> c.studentId).toList());
        int failed = 0;
        for (long id : ids) {
            try {
                work.accept(id);
            } catch (WebUntisException e) {
                if (e.reason() == WebUntisException.Reason.KEY_REJECTED) markRejected(id);
                failed++;
                LOG.warnf("sync for student %d failed: %s", id, e.reason());
            } catch (RuntimeException e) {
                failed++;
                // Class name only: messages of unknown exceptions might carry school data.
                LOG.warnf("sync for student %d failed: %s", id, e.getClass().getSimpleName());
            }
        }
        return new Result(ids.size() - failed, failed);
    }

    private void markRejected(long studentId) {
        Instant now = clock.instant();
        QuarkusTransaction.requiringNew().run(() -> {
            WebUntisCredential c = WebUntisCredential.findById(studentId);
            if (c != null && c.rejectedAt == null) c.rejectedAt = now;
        });
    }

    private static void log(String what, Result r) {
        LOG.infof("%s sync: %d students synced, %d failed", what, r.synced(), r.failed());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SCHOOL_ZONE));
    }

    private static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(SCHOOL_ZONE).toInstant();
    }
}

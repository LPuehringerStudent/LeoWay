package at.leoway.api.warnings;

import at.leoway.api.alerts.Alerts;
import at.leoway.api.domain.DelayWarning;
import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.domain.SavedRoute;
import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.feed.DelaysRecorded;
import at.leoway.api.feed.LinzAgAdapter;
import at.leoway.api.leaveby.Connection;
import at.leoway.api.leaveby.ConnectionSource;
import at.leoway.api.leaveby.DelayCheck;
import at.leoway.api.leaveby.LeaveBy;
import at.leoway.api.leaveby.LeaveByCalculator;
import at.leoway.api.leaveby.LiveDelays;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Delay warnings (#63, F03, A03). After recorder polls, checks every student whose first lesson starts within the
 * next {@link #WINDOW}: if a live delay makes their connection late, the warning is stored for Home and sent to their
 * alert channels (#62). One message per change, not per poll: again only if it was resolved in between, the
 * connection changed, or it is at least {@link #CHANGE_STEP} minutes more or less late than last sent.
 *
 * <p>Connections come from the same {@link ConnectionSource} and cache as the leave-by endpoint, so a check costs no
 * extra Linz AG request while a student's Home is open. Background requests have their own, smaller budget
 * ({@code leoway.trips.background-gap}). A student skipped because the source was busy is queued: the queue is retried
 * on every recorder poll (15 s) and goes first in the next full run, so every route gets its turn instead of only the
 * first few in id order (#106 review). Logs carry the student id and a reason only (S01).
 */
@ApplicationScoped
public class DelayWarnings {
    private static final Logger LOG = Logger.getLogger(DelayWarnings.class);
    /** Live data only exists close to the trip, like {@code LinzAgTrips.LIVE_WINDOW}. */
    static final Duration WINDOW = Duration.ofHours(3);
    /** At most one check per this gap; the recorder polls every 15 s and live trip answers are cached 2 min. */
    static final Duration CHECK_GAP = Duration.ofMinutes(1);
    static final int CHANGE_STEP = 3;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public record Result(int checked, int warned, int skipped) {}

    @Inject
    ConnectionSource connections;

    @Inject
    LiveDelays liveDelays;

    @Inject
    Alerts alerts;

    @ConfigProperty(name = "leoway.warnings.enabled", defaultValue = "true")
    boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Instant lastCheck = Instant.EPOCH;
    /** Students skipped because the source was busy, oldest first. Guarded by itself. */
    private final Set<Long> skipped = new LinkedHashSet<>();
    private Clock clock = Clock.systemUTC();

    /** For tests. A method, because tests reach the bean through a client proxy. */
    void useClock(Clock c) {
        clock = c;
    }

    /** For tests. */
    void reset() {
        lastCheck = Instant.EPOCH;
        synchronized (skipped) {
            skipped.clear();
        }
    }

    void onRecorded(@ObservesAsync DelaysRecorded event) {
        if (!enabled || !LinzAgAdapter.ID.equals(event.source())) return;
        poll();
    }

    /** One recorder poll: everyone at most once per {@link #CHECK_GAP}, the queued students in between. */
    void poll() {
        Instant now = clock.instant();
        if (!now.isBefore(lastCheck.plus(CHECK_GAP))) {
            lastCheck = now;
            checkAll();
        } else if (hasSkipped()) {
            checkSkipped();
        }
    }

    private boolean hasSkipped() {
        synchronized (skipped) {
            return !skipped.isEmpty();
        }
    }

    /** Checks every student with a first lesson in the window, queued ones first. Never runs twice at once. */
    @ActivateRequestContext
    public Result checkAll() {
        return run(true);
    }

    /** Checks only the students skipped last time because the source was busy. */
    @ActivateRequestContext
    public Result checkSkipped() {
        return run(false);
    }

    private Result run(boolean everyone) {
        if (!running.compareAndSet(false, true)) return new Result(0, 0, 0);
        try {
            Instant now = clock.instant();
            List<Long> inWindow = QuarkusTransaction.requiringNew().call(() -> FirstLessonCache
                    .<FirstLessonCache>list("startAt > ?1 and startAt <= ?2 order by studentId", now, now.plus(WINDOW))
                    .stream().map(c -> c.studentId).toList());
            List<Long> ids = new ArrayList<>();
            synchronized (skipped) {
                skipped.retainAll(Set.copyOf(inWindow)); // lesson over or moved out of the window
                ids.addAll(skipped);
            }
            if (everyone) inWindow.stream().filter(id -> !ids.contains(id)).forEach(ids::add);
            int warned = 0;
            int skippedNow = 0;
            for (long id : ids) {
                boolean busy = false;
                try {
                    if (check(id, now)) warned++;
                } catch (ConnectionSource.Unavailable e) {
                    skippedNow++;
                    // Only "busy" is worth retrying soon; a failing source waits for the next full run.
                    busy = e.retryAfter != null;
                } catch (RuntimeException e) {
                    skippedNow++;
                    LOG.warnf("delay check for student %d failed: %s", id, e.getClass().getSimpleName());
                }
                synchronized (skipped) {
                    skipped.remove(id);
                    if (busy) skipped.add(id); // to the back of the queue
                }
            }
            return new Result(ids.size(), warned, skippedNow);
        } finally {
            running.set(false);
        }
    }

    /** Checks one student. True if a message went out. */
    boolean check(long studentId, Instant now) {
        record Input(FirstLessonCache lesson, SavedRoute route) {}
        Optional<Input> input = QuarkusTransaction.requiringNew().call(() -> {
            WebUntisCredential credential = WebUntisCredential.findById(studentId);
            FirstLessonCache lesson = FirstLessonCache.findById(studentId);
            // The first route, like the leave-by endpoint without routeId.
            SavedRoute route = SavedRoute.<SavedRoute>find("studentId = ?1 order by id", studentId).firstResult();
            if (credential == null || credential.rejectedAt != null || lesson == null || lesson.startAt == null
                    || route == null) {
                return Optional.<Input>empty();
            }
            return Optional.of(new Input(lesson, route));
        });
        if (input.isEmpty()) return false;
        FirstLessonCache lesson = input.get().lesson();
        SavedRoute route = input.get().route();
        LeaveByCalculator.Walks walks = LeaveByCalculator.Walks.of(route);

        // Same arguments as the leave-by endpoint, so both share one cached Linz AG answer.
        List<Connection> found = connections.arrivingByInBackground(route.homeStopId, route.schoolStopId,
                lesson.startAt.minus(Duration.ofMinutes(walks.toSchool())));
        List<Connection> live = liveDelays.complete(found, route.homeStopId, route.schoolStopId);
        Optional<DelayCheck.Late> late = DelayCheck.check(lesson.startAt, walks, live, now);

        boolean exam = lesson.exam;
        Instant lessonStart = lesson.startAt;
        boolean send = QuarkusTransaction.requiringNew().call(() -> store(studentId, lessonStart, exam, late, now));
        if (send) alerts.notify(studentId, message(lessonStart, exam, late.orElseThrow()));
        return send;
    }

    /** Updates the stored warning. True if this is a change worth a message. */
    static boolean store(long studentId, Instant lessonStart, boolean exam, Optional<DelayCheck.Late> late,
            Instant now) {
        DelayWarning w = DelayWarning.findById(studentId);
        if (late.isEmpty()) {
            if (w != null && w.resolvedAt == null) w.resolvedAt = now;
            return false;
        }
        DelayCheck.Late l = late.get();
        boolean change = w == null || w.resolvedAt != null || !w.lessonStart.equals(lessonStart)
                || !w.tripId.equals(l.chosen().tripId()) || !w.plannedDeparture.equals(l.chosen().plannedDeparture())
                || Math.abs(w.minutesLate - l.minutesLate()) >= CHANGE_STEP;
        if (w == null) {
            w = new DelayWarning();
            w.studentId = studentId;
        }
        w.lessonStart = lessonStart;
        w.line = l.chosen().line();
        w.tripId = l.chosen().tripId();
        w.plannedDeparture = l.chosen().plannedDeparture();
        w.delayMinutes = l.delayMinutes();
        w.atSchool = l.atSchool();
        w.minutesLate = (int) l.minutesLate();
        w.exam = exam;
        DelayCheck.Alternative a = l.alternative();
        w.alternativeLine = a == null ? null : a.connection().line();
        w.alternativeDeparture = a == null ? null : a.connection().departure();
        w.alternativeLeaveAt = a == null ? null : a.leaveAt();
        w.alternativeAtSchool = a == null ? null : a.atSchool();
        w.resolvedAt = null;
        if (change) w.sentAt = now;
        w.persist();
        return change;
    }

    /** The alert text, in Vienna time. No subject or other timetable data beyond the start time. */
    static String message(Instant lessonStart, boolean exam, DelayCheck.Late l) {
        StringBuilder m = new StringBuilder("LeoWay delay warning: ")
                .append(l.chosen().line()).append(" at ").append(time(l.chosen().plannedDeparture()))
                .append(" is ").append(l.delayMinutes()).append(" min late. You would reach school at ")
                .append(time(l.atSchool())).append(", ").append(l.minutesLate())
                .append(" min after your first lesson starts (").append(time(lessonStart)).append(").");
        if (exam) m.append(" There is an exam in your first lesson.");
        DelayCheck.Alternative a = l.alternative();
        if (a == null) {
            m.append(" No other connection gets you there earlier.");
        } else if (a.inTime()) {
            m.append(" Take ").append(a.connection().line()).append(" at ").append(time(a.connection().departure()))
                    .append(" instead: leave home at ").append(time(a.leaveAt())).append('.');
        } else {
            m.append(" Earliest arrival: ").append(a.connection().line()).append(" at ")
                    .append(time(a.connection().departure())).append(", at school ").append(time(a.atSchool()))
                    .append(" (leave home at ").append(time(a.leaveAt())).append(").");
        }
        return m.toString();
    }

    private static String time(Instant t) {
        return t.atZone(LeaveBy.VIENNA).format(HHMM);
    }
}

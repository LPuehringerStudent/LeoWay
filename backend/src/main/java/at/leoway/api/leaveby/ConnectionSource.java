package at.leoway.api.leaveby;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Where connections come from. Today the Linz AG trip request ({@link LinzAgTrips}, #56); MOTIS on the open GTFS
 * timetables (#93) can replace it without touching the calculation.
 */
public interface ConnectionSource {
    /**
     * Connections from {@code fromStopId} to {@code toStopId} arriving around {@code arriveBy}: the last few arriving
     * by then, and usually the first one arriving after it (for "you will be late"). Times already include real-time
     * data where the source has it.
     *
     * @throws Unavailable if the source can't answer right now
     */
    List<Connection> arrivingBy(String fromStopId, String toStopId, Instant arriveBy);

    /** The source can't be reached, or is busy and should be asked again after {@code retryAfter} (null: unknown). */
    class Unavailable extends RuntimeException {
        public final Duration retryAfter;

        public Unavailable(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }
    }
}

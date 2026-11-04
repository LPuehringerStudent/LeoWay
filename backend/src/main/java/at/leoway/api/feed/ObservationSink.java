package at.leoway.api.feed;

import java.time.Instant;
import java.util.List;

/** Receives what a feed reported in one successful fetch. */
public interface ObservationSink {
    void store(String source, Instant collectedAt, List<DepartureObservation> observations);
}

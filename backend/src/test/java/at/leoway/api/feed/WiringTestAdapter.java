package at.leoway.api.feed;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.List;

/** Being this one class is all it takes to add a feed (M01). */
@ApplicationScoped
public class WiringTestAdapter implements FeedAdapter {
    static final String ID = "wiring-test";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Duration pollInterval() {
        return Duration.ofHours(1);
    }

    @Override
    public List<DepartureObservation> fetch() {
        return List.of();
    }
}

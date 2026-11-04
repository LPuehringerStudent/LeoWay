package at.leoway.api.feed;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

/** Placeholder until the delay recorder (#6) provides the real sink. No adapters exist before that either. */
@DefaultBean
@ApplicationScoped
public class DiscardingObservationSink implements ObservationSink {
    @Override
    public void store(String source, Instant collectedAt, List<DepartureObservation> observations) {
    }
}

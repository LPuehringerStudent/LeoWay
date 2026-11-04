package at.leoway.api.feed;

import at.leoway.api.domain.FeedStatus;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FeedStatusStore {
    @Transactional
    public Optional<FeedHealth> find(String feedId) {
        return FeedStatus.<FeedStatus>findByIdOptional(feedId).map(FeedStatusStore::toHealth);
    }

    @Transactional
    public List<FeedHealth> findAll() {
        return FeedStatus.<FeedStatus>listAll(Sort.by("feedId")).stream()
                .map(FeedStatusStore::toHealth)
                .toList();
    }

    @Transactional
    public void save(FeedHealth health) {
        FeedStatus status = FeedStatus.findById(health.feedId());
        if (status == null) {
            status = new FeedStatus();
            status.feedId = health.feedId();
        }
        status.state = health.state();
        status.lastSuccessAt = health.lastSuccessAt();
        status.lastErrorAt = health.lastErrorAt();
        status.lastError = health.lastError();
        status.persist();
    }

    private static FeedHealth toHealth(FeedStatus status) {
        return new FeedHealth(status.feedId, status.state, status.lastSuccessAt, status.lastErrorAt, status.lastError);
    }
}

package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import at.leoway.api.domain.FeedStatus.State;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FeedSchedulingTest {
    @Inject
    FeedStatusStore store;

    @Test
    void schedulerPollsANewAdapterWithoutAnyOtherChange() throws InterruptedException {
        Optional<FeedHealth> health = Optional.empty();
        for (int i = 0; i < 100 && health.isEmpty(); i++) {
            Thread.sleep(100);
            health = store.find(WiringTestAdapter.ID);
        }

        assertEquals(State.OK, health.orElseThrow().state());
        assertNotNull(health.orElseThrow().lastSuccessAt());
    }
}

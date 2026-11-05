package at.leoway.api.feed;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import at.leoway.api.domain.FeedStatus.State;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs without logging in, like the Uptime Kuma monitor. */
@QuarkusTest
class FeedHealthEndpointTest {
    private static final String PATH = "/api/health/feeds";

    @Inject
    FeedStatusStore store;

    @BeforeEach
    void waitForFirstPoll() throws InterruptedException {
        for (int i = 0; i < 100 && store.find(WiringTestAdapter.ID).isEmpty(); i++) {
            Thread.sleep(100);
        }
    }

    @AfterEach
    void restore() {
        store.save(new FeedHealth(WiringTestAdapter.ID, State.OK, Instant.now(), null, null));
    }

    @Test
    void healthyFeedAnswers200() {
        store.save(new FeedHealth(WiringTestAdapter.ID, State.OK, Instant.now(), null, null));

        given().get(PATH).then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("feeds[0].feedId", equalTo(WiringTestAdapter.ID))
                .body("feeds[0].state", equalTo("OK"));
    }

    @Test
    void staleFeedAnswers503WithoutErrorDetails() {
        Instant old = Instant.now().minusSeconds(600);
        store.save(new FeedHealth(WiringTestAdapter.ID, State.STALE, old, old, "IOException: secret-detail"));

        given().get(PATH).then()
                .statusCode(503)
                .body("status", equalTo("DOWN"))
                .body("feeds[0].state", equalTo("STALE"))
                .body(not(containsString("secret-detail")));
    }
}

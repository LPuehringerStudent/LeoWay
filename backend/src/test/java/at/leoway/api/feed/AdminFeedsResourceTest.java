package at.leoway.api.feed;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import at.leoway.api.domain.FeedStatus;
import at.leoway.api.domain.FeedStatus.State;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminFeedsResourceTest {
    private static final String PATH = "/api/admin/feeds";

    @Inject
    FeedStatusStore store;

    @AfterEach
    void removeTestFeed() {
        QuarkusTransaction.requiringNew().run(() -> FeedStatus.deleteById("resource-test"));
    }

    @Test
    void anonymousCallerIsRejected() {
        // 403 until a login mechanism exists (#20), 401 after that.
        given().get(PATH).then().statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @TestSecurity(user = "anna", roles = "student")
    void studentIsForbidden() {
        given().get(PATH).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "root", roles = "admin")
    void adminSeesFeedStatuses() {
        store.save(new FeedHealth("resource-test", State.STALE, Instant.parse("2026-11-04T06:00:00Z"), null, null));

        given().get(PATH).then()
                .statusCode(200)
                .body("feedId", hasItem("resource-test"))
                .body("find { it.feedId == 'resource-test' }.state", equalTo("STALE"))
                .body("find { it.feedId == 'resource-test' }.lastSuccessAt", equalTo("2026-11-04T06:00:00Z"));
    }
}

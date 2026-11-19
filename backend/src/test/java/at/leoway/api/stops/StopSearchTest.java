package at.leoway.api.stops;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** GET /api/stops (#57) against a fake Linz AG stop finder on the port in %test.leoway.feeds.linz-ag.base-url. */
@QuarkusTest
@TestSecurity(user = "anna", roles = Roles.STUDENT)
class StopSearchTest {
    FakeLinzAg fake;

    @Inject
    StopFinder finder;

    @BeforeEach
    void start() throws Exception {
        fake = new FakeLinzAg(18766);
        finder.clearCache();
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    @Test
    void findsStopsBestFirstAndNoAddresses() {
        given().queryParam("q", "Auwiesen").get("/api/stops").then().statusCode(200)
                .body("id", contains("60501810", "60502210", "60508077"))
                .body("name[0]", equalTo("Linz/Donau, Auwiesen"));
    }

    @Test
    void asksForStopsOnly() {
        given().queryParam("q", "auwiesen").get("/api/stops").then().statusCode(200);
        assertTrue(fake.queries.getFirst().contains("anyObjFilter_sf=2"), fake.queries.getFirst());
        assertTrue(fake.queries.getFirst().contains("coordOutputFormat=WGS84"), fake.queries.getFirst());
    }

    @Test
    void handlesASingleMatchAndNoMatch() {
        given().queryParam("q", "Meixnerkr").get("/api/stops").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].id", equalTo("60500120"));
        given().queryParam("q", "xyzzy").get("/api/stops").then().statusCode(200).body("$", empty());
    }

    @Test
    void cachesNormalisedQueries() {
        given().queryParam("q", "Auwiesen").get("/api/stops").then().statusCode(200);
        given().queryParam("q", "  auwiesen ").get("/api/stops").then().statusCode(200).body("$", hasSize(3));
        assertEquals(1, fake.queries.size());
    }

    @Test
    void rejectsTooShortOrTooLongQueries() {
        given().get("/api/stops").then().statusCode(400)
                .body("errors", hasItem("q must have 2 to 60 characters"));
        given().queryParam("q", " a ").get("/api/stops").then().statusCode(400);
        given().queryParam("q", "x".repeat(61)).get("/api/stops").then().statusCode(400);
        assertEquals(0, fake.queries.size());
    }

    @Test
    void reportsAnUnreachableStopFinder() {
        given().queryParam("q", "fail").get("/api/stops").then().statusCode(503);
    }

    /** Every uncached search uses the shared request slot, so only students may search (#90). */
    @Test
    @TestSecurity
    void anonymousCannotSearch() {
        given().queryParam("q", "auwiesen").get("/api/stops").then().statusCode(401);
        assertEquals(0, fake.queries.size());
    }

    @Test
    @TestSecurity(user = "root", roles = {Roles.ADMIN, Roles.STUDENT})
    void adminCanSearchStopsForOwnRoutes() {
        given().queryParam("q", "auwiesen").get("/api/stops").then().statusCode(200);
    }
}

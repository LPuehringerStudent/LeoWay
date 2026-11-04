package at.leoway.api.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import at.leoway.api.domain.Student;
import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.webuntis.FakeWebUntis;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Cookie;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** #20: log in with WebUntis username and access key; the session cookie unlocks the student's own data. */
@QuarkusTest
class LoginTest {
    FakeWebUntis fake;

    @BeforeEach
    void start() throws Exception {
        fake = new FakeWebUntis(18765);
    }

    @AfterEach
    void stop() {
        fake.close();
        QuarkusTransaction.requiringNew().run(() -> Student.delete("username", FakeWebUntis.USER));
    }

    static Cookie login(String user, String key, int expectedStatus) {
        var res = given().contentType(ContentType.URLENC).formParam("j_username", user).formParam("j_password", key)
                .post("/j_security_check").then().statusCode(expectedStatus).extract();
        return res.detailedCookie("leoway-session");
    }

    @Test
    void firstLoginCreatesAccountAndConnectsWebUntis() {
        Cookie session = login("Test.Student ", FakeWebUntis.KEY, 200);
        assertNotNull(session);

        given().cookie(session).get("/api/me").then().statusCode(200).body("username", equalTo(FakeWebUntis.USER));
        QuarkusTransaction.requiringNew().run(() -> {
            Student s = Student.find("username", FakeWebUntis.USER).firstResult();
            WebUntisCredential c = WebUntisCredential.findById(s.id);
            assertNotNull(c, "the key is stored encrypted on first login (F01)");
        });

        // A second login reuses the account.
        login(FakeWebUntis.USER, FakeWebUntis.KEY, 200);
        assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> Student.count("username", FakeWebUntis.USER)));
    }

    @Test
    void wrongKeyIsRejectedAndCreatesNothing() {
        login("someone.else", FakeWebUntis.KEY, 401);
        login(FakeWebUntis.USER, "not-a-key!", 401);
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> Student.count()));
    }

    @Test
    void adminGetsFeedsButNoAccount() {
        Cookie session = login(FakeWebUntis.ADMIN, FakeWebUntis.KEY, 200);
        given().cookie(session).get("/api/admin/feeds").then().statusCode(200);
        given().cookie(session).get("/api/me").then().statusCode(403);
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> Student.count("username", FakeWebUntis.ADMIN)));
    }

    @Test
    void logoutEndsTheSession() {
        Cookie session = login(FakeWebUntis.USER, FakeWebUntis.KEY, 200);
        var res = given().cookie(session).post("/api/session/logout").then().statusCode(204).extract();
        Cookie cleared = res.detailedCookie("leoway-session");
        assertNotNull(cleared);
        assertEquals("", cleared.getValue());
    }

    @Test
    void sessionCookieIsHttpOnlyAndStrict() {
        Cookie session = login(FakeWebUntis.USER, FakeWebUntis.KEY, 200);
        assertEquals(true, session.isHttpOnly());
        assertEquals("Strict", session.getSameSite());
    }

    @Test
    void statusShowsConnectionAndDisconnectRemovesTheKey() {
        Cookie session = login(FakeWebUntis.USER, FakeWebUntis.KEY, 200);
        given().cookie(session).get("/api/me/webuntis").then().statusCode(200)
                .body("connected", equalTo(true)).body("available", equalTo(true))
                .body("username", equalTo(FakeWebUntis.USER));

        given().cookie(session).delete("/api/me/webuntis").then().statusCode(204);
        given().cookie(session).get("/api/me/webuntis").then().statusCode(200).body("connected", equalTo(false));
    }

    @Test
    void statusSaysUnavailableWhenWebUntisIsDown() {
        Cookie session = login(FakeWebUntis.USER, FakeWebUntis.KEY, 200);
        fake.close();
        given().cookie(session).get("/api/me/webuntis").then().statusCode(200)
                .body("connected", equalTo(true)).body("available", equalTo(false));
        given().get("/api/session/webuntis-reachable").then().statusCode(200).body("webUntisReachable", equalTo(false));
    }

    @Test
    void reachabilityNeedsNoLogin() {
        given().get("/api/session/webuntis-reachable").then().statusCode(200).body("webUntisReachable", equalTo(true));
    }
}

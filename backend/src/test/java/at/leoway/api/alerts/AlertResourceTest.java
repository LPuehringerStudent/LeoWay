package at.leoway.api.alerts;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.AlertChannel;
import at.leoway.api.domain.DeletedAccount;
import at.leoway.api.domain.Student;
import at.leoway.api.security.Roles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** /api/me/alerts (#62, F04, S01) against a fake Discord on port 18767. */
@QuarkusTest
@TestSecurity(user = "anna", roles = Roles.STUDENT)
class AlertResourceTest {
    static final String ID = "1234567890123456789";
    static final String TOKEN = "aB3_-".repeat(13) + "xyz";
    static final String URL = "https://discord.com/api/webhooks/" + ID + "/" + TOKEN;
    static final Instant T0 = Instant.parse("2026-11-18T18:00:00Z");

    FakeDiscord discord;
    long annaId;
    final List<String> logs = new CopyOnWriteArrayList<>();
    final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord r) {
            logs.add(r instanceof ExtLogRecord e ? e.getFormattedMessage() : r.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @Inject
    Alerts alerts;

    @BeforeEach
    void start() throws Exception {
        discord = new FakeDiscord(18767);
        alerts.resetThrottle();
        at(T0);
        annaId = QuarkusTransaction.requiringNew().call(() -> {
            Student s = new Student();
            s.username = "anna";
            s.persist();
            return s.id;
        });
        java.util.logging.Logger.getLogger("").addHandler(capture);
    }

    @AfterEach
    void stop() {
        java.util.logging.Logger.getLogger("").removeHandler(capture);
        discord.close();
        alerts.useClock(Clock.systemUTC());
        QuarkusTransaction.requiringNew().run(() -> {
            Student.delete("username", "anna");
            DeletedAccount.deleteById(annaId);
        });
        assertTrue(logs.stream().noneMatch(l -> l != null && l.contains(TOKEN)), "token in the log: " + logs);
    }

    void at(Instant t) {
        alerts.useClock(Clock.fixed(t, ZoneOffset.UTC));
    }

    static Map<String, String> body(String url) {
        return Map.of("webhookUrl", url);
    }

    @Test
    void savingSendsATestMessageAndStoresTheWebhookEncrypted() {
        String res = given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord")
                .then().statusCode(200)
                .body("kind", equalTo("DISCORD")).body("working", equalTo(true)).body("since", notNullValue())
                .extract().asString();
        assertFalse(res.contains(TOKEN) || res.contains(ID), "response never contains the webhook");

        assertEquals(1, discord.posts.size());
        FakeDiscord.Post post = discord.posts.getFirst();
        assertEquals("/api/webhooks/" + ID + "/" + TOKEN, post.path());
        assertTrue(post.body().contains("LeoWay test message"), post.body());
        assertTrue(post.body().contains("\"allowed_mentions\":{\"parse\":[]}"), "no @everyone: " + post.body());

        byte[] stored = QuarkusTransaction.requiringNew().call(
                () -> AlertChannel.find(annaId, AlertChannel.Kind.DISCORD).encryptedTarget);
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains(TOKEN), "stored as ciphertext");

        String list = given().get("/api/me/alerts").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].working", equalTo(true)).extract().asString();
        assertFalse(list.contains(TOKEN) || list.contains(ID));
    }

    @Test
    void invalidOrRefusedWebhooksAreNotStored() {
        given().contentType(ContentType.JSON).body(body("https://evil.example/api/webhooks/" + ID + "/" + TOKEN))
                .put("/api/me/alerts/discord").then().statusCode(400)
                .body("errors", hasItem("webhookUrl is not a Discord webhook URL"));
        assertEquals(0, discord.posts.size(), "nothing sent for an invalid URL");

        discord.deleted.add(ID);
        given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord").then().statusCode(400);
        given().get("/api/me/alerts").then().body("$", empty());
    }

    @Test
    void outageIs503AndStoresNothing() {
        String down = "https://discord.com/api/webhooks/9234567890123456789/" + TOKEN;
        given().contentType(ContentType.JSON).body(body(down)).put("/api/me/alerts/discord").then().statusCode(503);
        given().get("/api/me/alerts").then().body("$", empty());
    }

    @Test
    void aDeletedWebhookMarksTheChannelBrokenUntilSavedAgain() {
        given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord").then().statusCode(200);
        discord.deleted.add(ID);
        at(T0.plusSeconds(60));
        given().post("/api/me/alerts/discord/test").then().statusCode(409);
        given().get("/api/me/alerts").then().body("[0].working", equalTo(false)).body("[0].brokenAt", notNullValue());

        assertEquals(0, alerts.notify(annaId, "Leave by 07:24"), "broken channels get no alerts");
        assertEquals(2, discord.posts.size());

        discord.deleted.clear();
        at(T0.plusSeconds(120));
        given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord").then().statusCode(200)
                .body("working", equalTo(true));
        assertEquals(1, alerts.notify(annaId, "Leave by 07:24"));
        assertTrue(discord.posts.getLast().body().contains("Leave by 07:24"));
    }

    @Test
    void testMessagesAreThrottled() {
        given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord").then().statusCode(200);
        at(T0.plusSeconds(4));
        given().post("/api/me/alerts/discord/test").then().statusCode(429).header("Retry-After", "6");
        at(T0.plusSeconds(10));
        given().post("/api/me/alerts/discord/test").then().statusCode(200);
        assertEquals(2, discord.posts.size());
    }

    @Test
    void unknownOrUnsetChannelsAre404AndRemovingWorks() {
        given().post("/api/me/alerts/discord/test").then().statusCode(404);
        given().post("/api/me/alerts/pigeon/test").then().statusCode(404);
        given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord").then().statusCode(200);
        given().delete("/api/me/alerts/discord").then().statusCode(204);
        given().get("/api/me/alerts").then().body("$", empty());
    }

    @Test
    void deletingTheAccountDeletesTheWebhook() {
        given().contentType(ContentType.JSON).body(body(URL)).put("/api/me/alerts/discord").then().statusCode(200);
        given().delete("/api/me").then().statusCode(204);
        assertNull(QuarkusTransaction.requiringNew().call(() -> AlertChannel.find(annaId, AlertChannel.Kind.DISCORD)));
    }

    @Test
    @TestSecurity(user = "root.admin", roles = Roles.ADMIN)
    void adminsAndGuestsCantUseIt() {
        given().get("/api/me/alerts").then().statusCode(403);
    }

    @Test
    @TestSecurity
    void guestsCantUseIt() {
        given().get("/api/me/alerts").then().statusCode(401);
    }
}

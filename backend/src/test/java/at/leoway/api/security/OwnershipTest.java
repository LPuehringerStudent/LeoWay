package at.leoway.api.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import at.leoway.api.domain.LatenessEntry;
import at.leoway.api.domain.SavedRoute;
import at.leoway.api.domain.Student;
import at.leoway.api.domain.WebUntisCredential;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A13: student A can't reach B's data, admins can't use private endpoints, deletion removes private data. */
@QuarkusTest
class OwnershipTest {
    long annaRoute;
    long benRoute;
    long benId;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            annaRoute = route(student("anna").id);
            Student ben = student("ben");
            benId = ben.id;
            benRoute = route(ben.id);

            WebUntisCredential key = new WebUntisCredential();
            key.studentId = ben.id;
            key.webUntisUsername = "ben.untis";
            key.encryptedAccessKey = new byte[] {9};
            key.keyVersion = 1;
            key.persist();

            LatenessEntry late = new LatenessEntry();
            late.studentId = ben.id;
            late.lessonDate = LocalDate.of(2026, 11, 4);
            late.startAt = Instant.parse("2026-11-04T07:00:00Z");
            late.endAt = Instant.parse("2026-11-04T07:10:00Z");
            late.persist();
        });
    }

    @AfterEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> Student.delete("username in ('anna', 'ben')"));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void studentSeesOwnRoute() {
        given().get("/api/me/routes/" + annaRoute).then().statusCode(200).body("id", equalTo((int) annaRoute));
        given().get("/api/me/routes").then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void otherStudentsRouteLooksMissing() {
        given().get("/api/me/routes/" + benRoute).then().statusCode(404);
        given().delete("/api/me/routes/" + benRoute).then().statusCode(404);
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> SavedRoute.count("id", benRoute)));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void studentCannotUseAdminEndpoints() {
        given().get("/api/admin/feeds").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "root", roles = Roles.ADMIN)
    void adminSeesFeedsButNoPrivateData() {
        given().get("/api/admin/feeds").then().statusCode(200);
        given().get("/api/me").then().statusCode(403);
        given().get("/api/me/routes/" + benRoute).then().statusCode(403);
    }

    @Test
    void anonymousIsRejected() {
        given().get("/api/me").then().statusCode(401);
        given().get("/api/admin/feeds").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "ghost", roles = Roles.STUDENT)
    void loginWithoutAccountIsForbidden() {
        given().get("/api/me").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "ben", roles = Roles.STUDENT)
    void deletingTheAccountRemovesPrivateData() {
        given().get("/api/me").then().statusCode(200).body("username", equalTo("ben"));
        given().delete("/api/me").then().statusCode(204);

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(0, Student.count("id", benId));
            assertEquals(0, WebUntisCredential.count("studentId", benId));
            assertEquals(0, SavedRoute.count("studentId", benId));
            assertEquals(0, LatenessEntry.count("studentId", benId));
        });
        given().get("/api/me").then().statusCode(403);
    }

    private static Student student(String username) {
        Student s = new Student();
        s.username = username;
        s.persist();
        return s;
    }

    private static long route(long studentId) {
        SavedRoute r = new SavedRoute();
        r.studentId = studentId;
        r.homeStopId = "1";
        r.schoolStopId = "60501780";
        r.walkToStopMinutes = 4;
        r.walkToSchoolMinutes = 6;
        r.persist();
        return r.id;
    }
}

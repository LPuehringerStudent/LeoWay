package at.leoway.api.me;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.SavedRoute;
import at.leoway.api.domain.Student;
import at.leoway.api.security.Roles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Saving and changing routes (#57, F02): validation, default buffer, ownership. */
@QuarkusTest
class RouteResourceTest {
    long annaId;
    long benRoute;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            annaId = student("anna").id;
            SavedRoute r = new SavedRoute();
            r.studentId = student("ben").id;
            r.homeStopId = "60501720";
            r.schoolStopId = "60500120";
            r.walkToStopMinutes = 3;
            r.walkToSchoolMinutes = 2;
            r.persist();
            benRoute = r.id;
        });
    }

    @AfterEach
    void clean() {
        QuarkusTransaction.requiringNew().run(() -> Student.delete("username in ('anna', 'ben')"));
    }

    static Map<String, Object> route(Object home, Object school, Object walkToStop, Object walkToSchool, Object buffer) {
        var body = new java.util.HashMap<String, Object>();
        body.put("homeStopId", home);
        body.put("schoolStopId", school);
        body.put("walkToStopMinutes", walkToStop);
        body.put("walkToSchoolMinutes", walkToSchool);
        if (buffer != null) body.put("bufferMinutes", buffer);
        return body;
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void createsARouteWithTheSuggestedBuffer() {
        int id = given().contentType(ContentType.JSON).body(route("60501810", "60500120", 4, 6, null))
                .post("/api/me/routes").then().statusCode(201)
                .body("homeStopId", equalTo("60501810")).body("bufferMinutes", equalTo(10))
                .extract().path("id");
        given().get("/api/me/routes/" + id).then().statusCode(200).body("walkToSchoolMinutes", equalTo(6));
        assertEquals(annaId, QuarkusTransaction.requiringNew().call(() -> SavedRoute.<SavedRoute>findById((long) id).studentId));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void locationPointsToTheNewRoute() {
        var res = given().contentType(ContentType.JSON).body(route("60501810", "60500120", 4, 6, 10))
                .post("/api/me/routes").then().statusCode(201).extract();
        assertTrue(res.header("Location").endsWith("/api/me/routes/" + res.path("id")));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void reportsEveryInvalidFieldAtOnce() {
        given().contentType(ContentType.JSON).body(route(null, "", -1, 61, 31))
                .post("/api/me/routes").then().statusCode(400)
                .body("errors", containsInAnyOrder(
                        "homeStopId must be a stop id from GET /api/stops",
                        "schoolStopId must be a stop id from GET /api/stops",
                        "walkToStopMinutes must be 0 to 60 minutes",
                        "walkToSchoolMinutes must be 0 to 60 minutes",
                        "bufferMinutes must be 0 to 30 minutes"));
        assertEquals(0, QuarkusTransaction.requiringNew().call(() -> SavedRoute.count("studentId", annaId)));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void acceptsTheLimits() {
        given().contentType(ContentType.JSON).body(route("60501810", "60500120", 0, 60, 30))
                .post("/api/me/routes").then().statusCode(201);
        given().contentType(ContentType.JSON).body(route("60501810", "60500120", 60, 0, 0))
                .post("/api/me/routes").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void storesStopsNeverAnAddress() {
        given().contentType(ContentType.JSON).body(route("Hauptstraße 5, Leonding", "60500120", 4, 6, null))
                .post("/api/me/routes").then().statusCode(400)
                .body("errors", hasItem("homeStopId must be a stop id from GET /api/stops"));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void rejectsTheSameStopTwiceAndAMissingBody() {
        given().contentType(ContentType.JSON).body(route("60500120", "60500120", 4, 6, null))
                .post("/api/me/routes").then().statusCode(400)
                .body("errors", hasItem("homeStopId and schoolStopId must differ"));
        given().contentType(ContentType.JSON).post("/api/me/routes").then().statusCode(400)
                .body("errors", hasItem("route body is missing"));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void changesOwnRoute() {
        int id = given().contentType(ContentType.JSON).body(route("60501810", "60500120", 4, 6, null))
                .post("/api/me/routes").then().statusCode(201).extract().path("id");
        given().contentType(ContentType.JSON).body(route("60501660", "60501780", 2, 3, 8))
                .put("/api/me/routes/" + id).then().statusCode(200)
                .body("homeStopId", equalTo("60501660")).body("schoolStopId", equalTo("60501780"))
                .body("bufferMinutes", equalTo(8));
        given().contentType(ContentType.JSON).body(route("60501660", "60501780", 2, 99, 8))
                .put("/api/me/routes/" + id).then().statusCode(400);
        given().get("/api/me/routes/" + id).then().body("walkToSchoolMinutes", equalTo(3));
    }

    @Test
    @TestSecurity(user = "anna", roles = Roles.STUDENT)
    void otherStudentsRouteCannotBeChanged() {
        given().contentType(ContentType.JSON).body(route("60501810", "60501780", 9, 9, 9))
                .put("/api/me/routes/" + benRoute).then().statusCode(404);
        given().contentType(ContentType.JSON).body(route("60501810", "60501780", 9, 9, 9))
                .put("/api/me/routes/999999999").then().statusCode(404);
        SavedRoute ben = QuarkusTransaction.requiringNew().call(() -> SavedRoute.findById(benRoute));
        assertEquals("60501720", ben.homeStopId);
        assertEquals(3, ben.walkToStopMinutes);
    }

    @Test
    void anonymousCannotSaveRoutes() {
        given().contentType(ContentType.JSON).body(route("60501810", "60500120", 4, 6, null))
                .post("/api/me/routes").then().statusCode(401);
    }

    private static Student student(String username) {
        Student s = new Student();
        s.username = username;
        s.persist();
        return s;
    }
}

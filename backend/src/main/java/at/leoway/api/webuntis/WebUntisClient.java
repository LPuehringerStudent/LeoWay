package at.leoway.api.webuntis;

import at.leoway.api.webuntis.WebUntisException.Reason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only WebUntis access for one student, logged in with their access key (no password).
 *
 * <p>S05: only the calls in {@link #ALLOWED_RPC} and {@link #ALLOWED_REST} can be made. Messages, homework,
 * grades and everything else are unreachable from this class. S01: nothing here logs keys, session ids or
 * school data, and exception messages never contain them.
 *
 * <p>Protocol as in the {@code webuntis} npm package used by {@code webuntis-spike/}.
 */
public class WebUntisClient implements AutoCloseable {
    static final Set<String> ALLOWED_RPC = Set.of("getUserData2017", "getTimetable", "getTimegridUnits", "logout");
    static final Set<String> ALLOWED_REST = Set.of("/WebUntis/api/app/config",
            "/WebUntis/api/classreg/absences/students", "/WebUntis/api/exams");

    private static final DateTimeFormatter UNTIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern SESSION_COOKIE = Pattern.compile("JSESSIONID=([^;]+)");

    private final HttpClient http;
    private final ObjectMapper json;
    private final URI baseUrl;
    private final String school;
    private final Clock clock;

    private String sessionId;
    private long personId;
    private int personType;

    public WebUntisClient(HttpClient http, ObjectMapper json, URI baseUrl, String school, Clock clock) {
        this.http = http;
        this.json = json;
        this.baseUrl = baseUrl;
        this.school = school;
        this.clock = clock;
    }

    /** Logs in with the access key's one-time code. Throws KEY_REJECTED if WebUntis says no. */
    public void login(String username, String accessKey) {
        String otp;
        try {
            otp = Totp.code(accessKey, clock.instant());
        } catch (IllegalArgumentException e) {
            throw new WebUntisException(Reason.KEY_REJECTED, "access key is not a valid WebUntis key");
        }
        ObjectNode auth = json.createObjectNode()
                .put("clientTime", clock.millis()).put("user", username).put("otp", otp);
        ObjectNode params = json.createObjectNode().set("auth", auth);
        HttpResponse<String> res = rpc("/WebUntis/jsonrpc_intern.do", "getUserData2017",
                json.createArrayNode().add(params), "m=getUserData2017&v=i2.2&");
        JsonNode body = parse(res);
        if (body.has("error") || res.statusCode() != 200) {
            throw new WebUntisException(Reason.KEY_REJECTED, "WebUntis did not accept the access key");
        }
        Matcher m = res.headers().allValues("set-cookie").stream()
                .map(SESSION_COOKIE::matcher).filter(Matcher::find).findFirst()
                .orElseThrow(() -> new WebUntisException(Reason.BAD_RESPONSE, "login returned no session"));
        sessionId = m.group(1);

        JsonNode user = rest("/WebUntis/api/app/config", "").path("data").path("loginServiceConfig").path("user");
        personId = user.path("personId").asLong(-1);
        personType = -1;
        for (JsonNode p : user.path("persons")) {
            if (p.path("id").asLong() == personId) personType = p.path("type").asInt(-1);
        }
        if (personId < 0 || personType < 0) {
            throw new WebUntisException(Reason.BAD_RESPONSE, "login returned no person");
        }
    }

    public List<Lesson> timetable(LocalDate from, LocalDate to) {
        ObjectNode options = json.createObjectNode()
                .put("startDate", untisDate(from)).put("endDate", untisDate(to));
        options.putObject("element").put("id", personId).put("type", personType);
        options.putArray("subjectFields").add("name");
        JsonNode result = call("getTimetable", json.createObjectNode().set("options", options));
        List<Lesson> lessons = new ArrayList<>();
        for (JsonNode l : result) {
            lessons.add(new Lesson(date(l.path("date")), time(l.path("startTime")), time(l.path("endTime")),
                    l.path("su").path(0).path("name").asText(null), Lesson.Status.of(l.path("code").asText(null))));
        }
        return lessons;
    }

    /** Period start times per weekday (1 = Sunday … 7 = Saturday, as WebUntis counts). */
    public JsonNode timegrid() {
        return call("getTimegridUnits", json.createObjectNode());
    }

    public List<Absence> absences(LocalDate from, LocalDate to) {
        JsonNode data = rest("/WebUntis/api/classreg/absences/students",
                "startDate=" + untisDate(from) + "&endDate=" + untisDate(to) + "&studentId=" + personId
                        + "&excuseStatusId=-1").path("data").path("absences");
        List<Absence> out = new ArrayList<>();
        for (JsonNode a : data) {
            // Reason, text and excuse are never read (L02).
            out.add(new Absence(date(a.path("startDate")), time(a.path("startTime")), time(a.path("endTime"))));
        }
        return out;
    }

    /** Exams of the student's class. Grades are never requested (S05). */
    public List<Exam> exams(LocalDate from, LocalDate to) {
        JsonNode data = rest("/WebUntis/api/exams", "startDate=" + untisDate(from) + "&endDate=" + untisDate(to)
                + "&klasseId=-1&withGrades=false").path("data").path("exams");
        List<Exam> out = new ArrayList<>();
        for (JsonNode e : data) {
            out.add(new Exam(date(e.path("examDate")), time(e.path("startTime")), time(e.path("endTime")),
                    e.path("subject").asText(null)));
        }
        return out;
    }

    @Override
    public void close() {
        if (sessionId == null) return;
        try {
            call("logout", json.createObjectNode());
        } catch (WebUntisException ignored) {
            // The session expires on its own.
        } finally {
            sessionId = null;
        }
    }

    // --- transport -------------------------------------------------------------------------------------

    private JsonNode call(String method, JsonNode params) {
        requireSession();
        HttpResponse<String> res = rpc("/WebUntis/jsonrpc.do", method, params, "");
        JsonNode body = parse(res);
        if (res.statusCode() != 200 || body.has("error") || !body.has("result")) {
            throw new WebUntisException(Reason.UNAVAILABLE, method + " failed (HTTP " + res.statusCode() + ")");
        }
        return body.get("result");
    }

    private HttpResponse<String> rpc(String path, String method, JsonNode params, String extraQuery) {
        if (!ALLOWED_RPC.contains(method)) {
            throw new WebUntisException(Reason.FORBIDDEN_CALL, method + " is not on the allow-list (S05)");
        }
        ObjectNode body = json.createObjectNode().put("id", "leoway").put("method", method).put("jsonrpc", "2.0");
        body.set("params", params);
        HttpRequest.Builder req = HttpRequest.newBuilder(url(path, extraQuery + "school=" + enc(school)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return send(req);
    }

    private JsonNode rest(String path, String query) {
        if (!ALLOWED_REST.contains(path)) {
            throw new WebUntisException(Reason.FORBIDDEN_CALL, path + " is not on the allow-list (S05)");
        }
        requireSession();
        HttpResponse<String> res = send(HttpRequest.newBuilder(url(path, query)).GET());
        if (res.statusCode() != 200) {
            throw new WebUntisException(Reason.UNAVAILABLE, path + " failed (HTTP " + res.statusCode() + ")");
        }
        return parse(res);
    }

    private HttpResponse<String> send(HttpRequest.Builder req) {
        req.timeout(Duration.ofSeconds(15)).header("User-Agent", "LeoWay");
        if (sessionId != null) req.header("Cookie", "JSESSIONID=" + sessionId + "; schoolname=\"_" + school + "\"");
        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new WebUntisException(Reason.UNAVAILABLE, "WebUntis not reachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebUntisException(Reason.UNAVAILABLE, "interrupted");
        }
    }

    private JsonNode parse(HttpResponse<String> res) {
        try {
            return json.readTree(res.body());
        } catch (IOException e) {
            throw new WebUntisException(Reason.BAD_RESPONSE, "WebUntis returned no JSON (HTTP " + res.statusCode() + ")");
        }
    }

    private void requireSession() {
        if (sessionId == null) throw new IllegalStateException("not logged in");
    }

    private URI url(String path, String query) {
        return baseUrl.resolve(path + (query.isEmpty() ? "" : "?" + query));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static int untisDate(LocalDate d) {
        return Integer.parseInt(d.format(UNTIS_DATE));
    }

    static LocalDate date(JsonNode n) {
        return LocalDate.parse(String.format("%08d", n.asInt()), UNTIS_DATE);
    }

    static LocalTime time(JsonNode n) {
        int t = n.asInt();
        return LocalTime.of(t / 100, t % 100);
    }
}

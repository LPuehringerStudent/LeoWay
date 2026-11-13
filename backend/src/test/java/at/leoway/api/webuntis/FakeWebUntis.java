package at.leoway.api.webuntis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local stand-in for WebUntis that replays synthetic responses from src/test/resources/webuntis. */
public class FakeWebUntis implements AutoCloseable {
    public static final String USER = "test.student";
    public static final String ADMIN = "root.admin";
    /** Base32 test key; the fake accepts any 6-digit code for it. */
    public static final String KEY = "JBSWY3DPEHPK3PXP";

    public final List<String> requests = new CopyOnWriteArrayList<>();
    /** WebUntis users whose key is accepted. Remove one to simulate a revoked key. */
    public final Set<String> users = ConcurrentHashMap.newKeySet();
    /** Replaces timetable.json / absences.json when set, e.g. to cancel a lesson overnight. */
    public volatile String timetable;
    public volatile String absences;
    private final HttpServer server;

    public FakeWebUntis(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.start();
        users.add(USER);
        users.add(ADMIN);
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + body);
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        boolean session = cookie != null && cookie.contains("JSESSIONID=fake-session");

        switch (path) {
            case "/WebUntis/jsonrpc_intern.do" -> {
                if (users.stream().anyMatch(u -> body.contains("\"user\":\"" + u + "\"")) && body.matches("(?s).*\"otp\":\"\\d{6}\".*")) {
                    ex.getResponseHeaders().add("Set-Cookie", "JSESSIONID=fake-session; Path=/WebUntis");
                    reply(ex, 200, "{\"jsonrpc\":\"2.0\",\"id\":\"leoway\",\"result\":{}}");
                } else {
                    reply(ex, 200, "{\"jsonrpc\":\"2.0\",\"id\":\"leoway\",\"error\":{\"code\":-8504,\"message\":\"bad credentials\"}}");
                }
            }
            case "/WebUntis/jsonrpc.do" -> {
                if (!session) reply(ex, 200, "{\"error\":{\"code\":-8520,\"message\":\"not authenticated\"}}");
                else if (body.contains("\"getTimetable\"")) reply(ex, 200, timetable != null ? timetable : resource("timetable.json"));
                else if (body.contains("\"getTimegridUnits\"")) reply(ex, 200, "{\"result\":[{\"day\":2,\"timeUnits\":[{\"startTime\":800,\"endTime\":850}]}]}");
                else if (body.contains("\"logout\"")) reply(ex, 200, "{\"result\":null}");
                else reply(ex, 200, "{\"error\":{\"code\":-32601,\"message\":\"unknown\"}}");
            }
            case "/WebUntis/api/app/config" -> reply(ex, session ? 200 : 401, resource("app-config.json"));
            case "/WebUntis/api/classreg/absences/students" -> reply(ex, session ? 200 : 401, absences != null ? absences : resource("absences.json"));
            case "/WebUntis/api/exams" -> reply(ex, session ? 200 : 401, resource("exams.json"));
            default -> reply(ex, 404, "{}");
        }
    }

    static String resource(String name) {
        try (InputStream in = FakeWebUntis.class.getResourceAsStream("/webuntis/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

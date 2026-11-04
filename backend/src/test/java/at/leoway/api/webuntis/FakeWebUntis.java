package at.leoway.api.webuntis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local stand-in for WebUntis that replays synthetic responses from src/test/resources/webuntis. */
class FakeWebUntis implements AutoCloseable {
    static final String USER = "test.student";
    /** Base32 test key; the fake accepts any 6-digit code for it. */
    static final String KEY = "JBSWY3DPEHPK3PXP";

    final List<String> requests = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    FakeWebUntis(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.start();
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
                if (body.contains("\"user\":\"" + USER + "\"") && body.matches("(?s).*\"otp\":\"\\d{6}\".*")) {
                    ex.getResponseHeaders().add("Set-Cookie", "JSESSIONID=fake-session; Path=/WebUntis");
                    reply(ex, 200, "{\"jsonrpc\":\"2.0\",\"id\":\"leoway\",\"result\":{}}");
                } else {
                    reply(ex, 200, "{\"jsonrpc\":\"2.0\",\"id\":\"leoway\",\"error\":{\"code\":-8504,\"message\":\"bad credentials\"}}");
                }
            }
            case "/WebUntis/jsonrpc.do" -> {
                if (!session) reply(ex, 200, "{\"error\":{\"code\":-8520,\"message\":\"not authenticated\"}}");
                else if (body.contains("\"getTimetable\"")) reply(ex, 200, resource("timetable.json"));
                else if (body.contains("\"getTimegridUnits\"")) reply(ex, 200, "{\"result\":[{\"day\":2,\"timeUnits\":[{\"startTime\":800,\"endTime\":850}]}]}");
                else if (body.contains("\"logout\"")) reply(ex, 200, "{\"result\":null}");
                else reply(ex, 200, "{\"error\":{\"code\":-32601,\"message\":\"unknown\"}}");
            }
            case "/WebUntis/api/app/config" -> reply(ex, session ? 200 : 401, resource("app-config.json"));
            case "/WebUntis/api/classreg/absences/students" -> reply(ex, session ? 200 : 401, resource("absences.json"));
            case "/WebUntis/api/exams" -> reply(ex, session ? 200 : 401, resource("exams.json"));
            default -> reply(ex, 404, "{}");
        }
    }

    private static String resource(String name) {
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

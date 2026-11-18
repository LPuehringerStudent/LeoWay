package at.leoway.api.stops;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local stand-in for the Linz AG stop finder, replaying recorded responses from src/test/resources/linz-ag.
 * Stop finder: "auwiesen" gives several stops and an address, "meixnerkr" one stop, "fail" HTTP 500, anything else
 * none. Trip request: always the trips of trip-arrive-by.json (Hauptbahnhof to Limesstraße, 19.11.2026, by 07:50),
 * HTTP 500 if the origin is "fail", an unusable answer if it is "broken".
 */
public class FakeLinzAg implements AutoCloseable {
    public final List<String> queries = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    public FakeLinzAg(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/XML_STOPFINDER_REQUEST", this::handle);
        server.createContext("/XML_TRIP_REQUEST2", this::trip);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        String query = param(ex.getRequestURI().getRawQuery(), "name_sf");
        queries.add(ex.getRequestURI().getRawQuery());
        switch (query) {
            case "auwiesen" -> reply(ex, 200, resource("stopfinder-many.json"));
            case "meixnerkr" -> reply(ex, 200, resource("stopfinder-one.json"));
            case "fail" -> reply(ex, 500, "{}");
            default -> reply(ex, 200, resource("stopfinder-none.json"));
        }
    }

    private void trip(HttpExchange ex) throws IOException {
        queries.add(ex.getRequestURI().getRawQuery());
        switch (param(ex.getRequestURI().getRawQuery(), "name_origin")) {
            case "fail" -> reply(ex, 500, "{}");
            // A ride without points: parses as JSON, but not as a trip.
            case "broken" -> reply(ex, 200, "{\"trips\": [{\"legs\": [{\"mode\": {\"diva\": {\"stateless\": \"esg:1\"}}}]}]}");
            default -> reply(ex, 200, resource("trip-arrive-by.json"));
        }
    }

    static String param(String rawQuery, String name) {
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = FakeLinzAg.class.getResourceAsStream("/linz-ag/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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

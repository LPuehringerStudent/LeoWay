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
 * "auwiesen" gives several stops and an address, "meixnerkr" one stop, "fail" HTTP 500, anything else none.
 */
public class FakeLinzAg implements AutoCloseable {
    public final List<String> queries = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    public FakeLinzAg(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/XML_STOPFINDER_REQUEST", this::handle);
        server.start();
    }

    String baseUrl() {
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

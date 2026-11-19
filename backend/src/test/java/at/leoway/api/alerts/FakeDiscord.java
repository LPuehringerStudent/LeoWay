package at.leoway.api.alerts;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local stand-in for Discord webhooks on the port in %test.leoway.alerts.discord.base-url. Webhooks are known
 * unless deleted; ids starting with 9 answer 500 (outage).
 */
public class FakeDiscord implements AutoCloseable {
    public record Post(String path, String body) {}

    public final List<Post> posts = new CopyOnWriteArrayList<>();
    public final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private final HttpServer server;

    public FakeDiscord(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/webhooks/", this::handle);
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String id = path.substring("/api/webhooks/".length()).split("/")[0];
        posts.add(new Post(path, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        int status = deleted.contains(id) ? 404 : id.startsWith("9") ? 500 : 204;
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

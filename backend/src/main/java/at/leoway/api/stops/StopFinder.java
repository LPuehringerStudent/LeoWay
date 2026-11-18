package at.leoway.api.stops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Stop search for the route editor (F02), using the Linz AG stop finder (EFA {@code XML_STOPFINDER_REQUEST}) as
 * tested in the trip request spike (#56, linz-ag-spike/trip.mjs). Only stops are asked for and returned, never
 * addresses or places (L02).
 *
 * <p>Linz AG publishes no rate limit, and the recorder already uses one request every 15 s on the same server.
 * Results are cached, and requests that aren't cached are spaced at least {@code min-gap} apart across all students.
 */
@ApplicationScoped
public class StopFinder {
    public record Stop(String id, String name) {}

    /** Linz AG is busy; the caller should try again shortly. */
    public static class Busy extends RuntimeException {
        public final Duration retryAfter;

        Busy(Duration retryAfter) {
            super("stop finder busy");
            this.retryAfter = retryAfter;
        }
    }

    static final int MAX_RESULTS = 10;
    static final int MAX_CACHED_QUERIES = 2_000;

    /** Same server as the departure monitor (LinzAgAdapter). */
    @ConfigProperty(name = "leoway.feeds.linz-ag.base-url", defaultValue = "https://www.linzag.at/static")
    String baseUrl;

    @ConfigProperty(name = "leoway.stops.cache-ttl", defaultValue = "P1D")
    Duration cacheTtl;

    @ConfigProperty(name = "leoway.stops.min-gap", defaultValue = "2s")
    Duration minGap;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private record Entry(List<Stop> stops, Instant expires) {}

    /** Insertion order, so the oldest query is dropped first when the cache is full. */
    private final Map<String, Entry> cache = new LinkedHashMap<>();
    private Instant nextRequest = Instant.EPOCH;
    private Clock clock = Clock.systemUTC();

    /** For tests. A method, because tests reach the bean through a client proxy. */
    void useClock(Clock c) {
        clock = c;
    }

    /** For tests. */
    synchronized void clearCache() {
        cache.clear();
        nextRequest = Instant.EPOCH;
    }

    /** Normalised so "Meixnerkreuzung " and "meixnerkreuzung" share one cache entry. */
    static String key(String query) {
        return query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public List<Stop> search(String query) throws IOException, InterruptedException {
        String key = key(query);
        synchronized (this) {
            Instant now = clock.instant();
            Entry hit = cache.get(key);
            if (hit != null && hit.expires.isAfter(now)) return hit.stops;
            if (now.isBefore(nextRequest)) throw new Busy(Duration.between(now, nextRequest));
            nextRequest = now.plus(minGap);
        }
        List<Stop> stops = parse(json.readTree(request(key)));
        synchronized (this) {
            cache.remove(key);
            if (cache.size() >= MAX_CACHED_QUERIES) cache.remove(cache.keySet().iterator().next());
            cache.put(key, new Entry(stops, clock.instant().plus(cacheTtl)));
        }
        return stops;
    }

    private String request(String query) throws IOException, InterruptedException {
        // anyObjFilter_sf=2: stops only, so addresses and points of interest never come back (L02)
        String params = "outputFormat=JSON&locationServerActive=1&stateless=1&type_sf=any&anyObjFilter_sf=2"
                + "&name_sf=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/XML_STOPFINDER_REQUEST?" + params))
                .timeout(Duration.ofSeconds(10)).header("User-Agent", "LeoWay-school-project").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Linz AG stop finder returned HTTP " + res.statusCode());
        }
        return res.body();
    }

    /** Best matches first. EFA returns one match as {@code points.point} (an object) and several as an array. */
    static List<Stop> parse(JsonNode body) {
        JsonNode points = body.path("stopFinder").path("points");
        if (points.has("point")) points = points.path("point");
        List<JsonNode> list = new ArrayList<>();
        if (points.isArray()) points.forEach(list::add);
        else if (points.isObject()) list.add(points);

        return list.stream()
                .filter(p -> "stop".equals(p.path("anyType").asText()))
                .filter(p -> !p.path("ref").path("id").asText().isBlank())
                .sorted(Comparator.comparingInt((JsonNode p) -> p.path("quality").asInt()).reversed())
                .map(p -> new Stop(p.path("ref").path("id").asText(), p.path("name").asText()))
                .distinct()
                .limit(MAX_RESULTS)
                .toList();
    }
}

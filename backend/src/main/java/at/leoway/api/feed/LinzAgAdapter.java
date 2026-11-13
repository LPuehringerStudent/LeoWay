package at.leoway.api.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Linz AG live departures (EFA departure monitor), as tested in the spike (#3, linz-ag-spike/probe.mjs).
 * The feed is open and needs no key. One fetch asks each configured stop once; with the default 30 s interval
 * and two stops that is one request every 15 s on average.
 */
@ApplicationScoped
@IfBuildProperty(name = "leoway.feeds.linz-ag.enabled", stringValue = "true", enableIfMissing = true)
public class LinzAgAdapter implements FeedAdapter {
    static final ZoneId LINZ = ZoneId.of("Europe/Vienna");

    @ConfigProperty(name = "leoway.feeds.linz-ag.base-url", defaultValue = "https://www.linzag.at/static")
    String baseUrl;

    /** Stop ids from XML_STOPFINDER_REQUEST. Default: Limesstraße and Meixnerkreuzung (near HTL Leonding). */
    @ConfigProperty(name = "leoway.feeds.linz-ag.stops", defaultValue = "60501780,60500120")
    List<String> stops;

    @ConfigProperty(name = "leoway.feeds.linz-ag.interval", defaultValue = "30s")
    Duration interval;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String id() {
        return "linz-ag";
    }

    @Override
    public Duration pollInterval() {
        return interval;
    }

    @Override
    public List<DepartureObservation> fetch() throws IOException, InterruptedException {
        List<DepartureObservation> all = new ArrayList<>();
        for (String stop : stops) {
            all.addAll(parse(stop, json.readTree(request(stop))));
        }
        return all;
    }

    private String request(String stopId) throws IOException, InterruptedException {
        String query = "outputFormat=JSON&locationServerActive=1&stateless=1&type_dm=any&limit=40&mode=direct"
                + "&useRealtime=1&name_dm=" + URLEncoder.encode(stopId, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/XML_DM_REQUEST?" + query))
                .timeout(Duration.ofSeconds(20)).header("User-Agent", "LeoWay-school-project").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Linz AG returned HTTP " + res.statusCode());
        }
        return res.body();
    }

    static List<DepartureObservation> parse(String stopId, JsonNode body) {
        List<DepartureObservation> out = new ArrayList<>();
        for (JsonNode d : body.path("departureList")) {
            JsonNode line = d.path("servingLine");
            JsonNode planned = d.path("dateTime");
            if (planned.isMissingNode() || line.isMissingNode()) continue;
            LocalDateTime local = LocalDateTime.of(planned.path("year").asInt(), planned.path("month").asInt(),
                    planned.path("day").asInt(), planned.path("hour").asInt(), planned.path("minute").asInt());
            Integer delay = line.hasNonNull("delay") ? line.path("delay").asInt() : null;
            if (delay != null && delay == -9999) delay = null;
            out.add(new DepartureObservation(
                    line.path("number").asText(),
                    tripId(d, line),
                    stopId,
                    text(d, "platform"),
                    local.toLocalDate(),
                    local.atZone(LINZ).toInstant(),
                    delay,
                    text(d, "realtimeTripStatus")));
        }
        return out;
    }

    /** AVMSTripID when present; otherwise the line's stateless id plus its trip key (unique only per line). */
    static String tripId(JsonNode departure, JsonNode line) {
        for (JsonNode a : departure.path("attrs")) {
            if ("AVMSTripID".equals(a.path("name").asText())) return a.path("value").asText();
        }
        return line.path("stateless").asText() + "#" + line.path("key").asText();
    }

    /** True for the fallback id built by {@link #tripId}; real AVMSTripIDs never contain {@code #}. */
    static boolean isFallbackTripId(String tripId) {
        return tripId.contains("#");
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) && !n.path(field).asText().isEmpty() ? n.path(field).asText() : null;
    }
}

package at.leoway.api.leaveby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Connections from the Linz AG trip request (EFA {@code XML_TRIP_REQUEST2}), as tested in the trip request spike
 * (#56, linz-ag-spike/trip.mjs). Asked with "arrive by", so EFA returns the last trips arriving in time and one
 * arriving after.
 *
 * <p>Real-time: EFA fills {@code rtTime} for every leg, but it is only live data if the leg's {@code mode.realtime}
 * is 1; otherwise it just repeats the timetable. Live times come per ride, so a trip with a change gets its
 * departure delay from the first ride and its arrival delay from the last one.
 *
 * <p>Budget: the recorder already uses one request every 15 s on this server. Answers are cached per (home stop,
 * school stop, arrival time), not per student, so students with the same route and first lesson share one request
 * (#56). Close to the trip the cache is short, so real-time data stays fresh; further ahead it is long. Requests that
 * aren't cached are spaced at least {@code min-gap} apart across all students.
 */
@ApplicationScoped
public class LinzAgTrips implements ConnectionSource {
    private static final Logger LOG = Logger.getLogger(LinzAgTrips.class);
    static final int TRIPS = 4;
    static final int MAX_CACHED = 2_000;
    /** Closer than this to the arrival, answers carry real-time data and are cached only for {@code live-ttl}. */
    static final Duration LIVE_WINDOW = Duration.ofHours(3);
    private static final DateTimeFormatter EFA_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Same server as the departure monitor (LinzAgAdapter) and the stop finder. */
    @ConfigProperty(name = "leoway.feeds.linz-ag.base-url", defaultValue = "https://www.linzag.at/static")
    String baseUrl;

    @ConfigProperty(name = "leoway.trips.cache-ttl", defaultValue = "PT6H")
    Duration cacheTtl;

    @ConfigProperty(name = "leoway.trips.live-ttl", defaultValue = "PT1M")
    Duration liveTtl;

    @ConfigProperty(name = "leoway.trips.min-gap", defaultValue = "2s")
    Duration minGap;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private record Key(String from, String to, Instant arriveBy) {}
    private record Entry(List<Connection> connections, Instant expires) {}

    /** Insertion order, so the oldest entry is dropped first when the cache is full. */
    private final Map<Key, Entry> cache = new LinkedHashMap<>();
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

    @Override
    public List<Connection> arrivingBy(String fromStopId, String toStopId, Instant arriveBy) {
        Key key = new Key(fromStopId, toStopId, arriveBy.truncatedTo(ChronoUnit.MINUTES));
        Instant now;
        synchronized (this) {
            now = clock.instant();
            Entry hit = cache.get(key);
            if (hit != null && hit.expires.isAfter(now)) return hit.connections;
            if (now.isBefore(nextRequest)) {
                throw new Unavailable("Linz AG trip request busy", Duration.between(now, nextRequest));
            }
            nextRequest = now.plus(minGap);
        }
        List<Connection> connections;
        try {
            connections = parse(json.readTree(request(key)));
        } catch (IOException e) {
            LOG.warnf("Linz AG trip request failed: %s", e.getMessage());
            throw new Unavailable("Linz AG trip request failed", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("interrupted", null);
        }
        Duration ttl = Duration.between(now, key.arriveBy).compareTo(LIVE_WINDOW) < 0 ? liveTtl : cacheTtl;
        synchronized (this) {
            cache.remove(key);
            if (cache.size() >= MAX_CACHED) cache.remove(cache.keySet().iterator().next());
            cache.put(key, new Entry(connections, now.plus(ttl)));
        }
        return connections;
    }

    private String request(Key key) throws IOException, InterruptedException {
        LocalDateTime local = key.arriveBy.atZone(LeaveBy.VIENNA).toLocalDateTime();
        String params = "outputFormat=JSON&locationServerActive=1&stateless=1"
                + "&type_origin=any&name_origin=" + key.from + "&type_destination=any&name_destination=" + key.to
                + "&itdDate=" + local.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&itdTime=" + local.format(DateTimeFormatter.ofPattern("HHmm"))
                + "&itdTripDateTimeDepArr=arr&calcNumberOfTrips=" + TRIPS + "&useRealtime=1";
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/XML_TRIP_REQUEST2?" + params))
                .timeout(Duration.ofSeconds(15)).header("User-Agent", "LeoWay-school-project").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("Linz AG trip request returned HTTP " + res.statusCode());
        return res.body();
    }

    /** Trips without a ride (walking only) and trips with a cancelled ride are left out. */
    static List<Connection> parse(JsonNode body) {
        List<Connection> out = new ArrayList<>();
        for (JsonNode trip : list(body.path("trips"))) {
            List<JsonNode> legs = list(trip.path("legs"));
            List<JsonNode> rides = legs.stream().filter(LinzAgTrips::isRide).toList();
            if (rides.isEmpty() || rides.stream().anyMatch(LinzAgTrips::cancelled)) continue;
            JsonNode first = rides.getFirst();
            JsonNode lastRide = rides.getLast();
            JsonNode departure = list(legs.getFirst().path("points")).getFirst().path("dateTime");
            JsonNode arrival = list(legs.getLast().path("points")).getLast().path("dateTime");
            JsonNode firstDeparture = list(first.path("points")).getFirst().path("dateTime");
            out.add(new Connection(
                    rides.stream().map(r -> r.path("mode").path("number").asText()).collect(Collectors.joining(" > ")),
                    tripId(first.path("mode").path("diva")),
                    LocalDate.parse(firstDeparture.path("date").asText(), EFA_DATE),
                    planned(departure), planned(arrival),
                    delay(first, firstDeparture).orElse(null),
                    delay(lastRide, list(lastRide.path("points")).getLast().path("dateTime")).orElse(null)));
        }
        return out;
    }

    /** Walks have no line: {@code mode.type} 99/100 and an empty {@code diva.stateless} like {@code ::}. */
    static boolean isRide(JsonNode leg) {
        String stateless = leg.path("mode").path("diva").path("stateless").asText();
        return !stateless.isBlank() && !stateless.startsWith(":");
    }

    private static boolean cancelled(JsonNode leg) {
        return leg.path("realtimeStatus").asText().contains("CANCELLED");
    }

    /** The recorder's id for the same trip (LinzAgAdapter.tripId): AVMSTripID, else {@code stateless#tripCode}. */
    static String tripId(JsonNode diva) {
        for (JsonNode a : list(diva.path("attrs"))) {
            if ("AVMSTripID".equals(a.path("name").asText()) && !a.path("value").asText().isBlank()) {
                return a.path("value").asText();
            }
        }
        return diva.path("stateless").asText() + "#" + diva.path("tripCode").asText();
    }

    private static Instant planned(JsonNode dateTime) {
        return at(dateTime.path("date").asText(), time(dateTime, "timeSec", "time"));
    }

    /** Minutes between the real-time and the planned time, only if the ride really has real-time data. */
    private static Optional<Integer> delay(JsonNode ride, JsonNode dateTime) {
        if (!"1".equals(ride.path("mode").path("realtime").asText())) return Optional.empty();
        String rtDate = dateTime.path("rtDate").asText(dateTime.path("date").asText());
        String rtTime = time(dateTime, "rtTimeSec", "rtTime");
        if (rtTime.isBlank()) return Optional.empty();
        long seconds = Duration.between(planned(dateTime), at(rtDate, rtTime)).toSeconds();
        return Optional.of((int) Math.round(seconds / 60.0));
    }

    private static String time(JsonNode dateTime, String withSeconds, String withoutSeconds) {
        String t = dateTime.path(withSeconds).asText();
        return t.isBlank() ? dateTime.path(withoutSeconds).asText() : t;
    }

    private static Instant at(String date, String time) {
        return LocalDate.parse(date, EFA_DATE).atTime(LocalTime.parse(time)).atZone(LeaveBy.VIENNA).toInstant();
    }

    /** EFA returns one element as an object and several as an array. */
    private static List<JsonNode> list(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node.isArray()) node.forEach(out::add);
        else if (node.isObject()) out.add(node);
        return out;
    }
}

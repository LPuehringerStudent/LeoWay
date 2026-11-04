package at.leoway.api.webuntis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Creates one client per student session. School and server come from config (default: HTL Leonding). */
@ApplicationScoped
public class WebUntisClientFactory {
    @ConfigProperty(name = "leoway.webuntis.base-url", defaultValue = "https://htl-leonding.webuntis.com")
    URI baseUrl;

    @ConfigProperty(name = "leoway.webuntis.school", defaultValue = "htl-leonding")
    String school;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** True if the WebUntis server answers at all (any HTTP status). Sends no student data. */
    public boolean reachable() {
        try {
            http.send(java.net.http.HttpRequest.newBuilder(baseUrl.resolve("/WebUntis/"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), java.net.http.HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (java.io.IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public WebUntisClient create() {
        return new WebUntisClient(http, json, baseUrl, school, Clock.systemUTC());
    }
}

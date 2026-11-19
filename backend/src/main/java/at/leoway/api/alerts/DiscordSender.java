package at.leoway.api.alerts;

import at.leoway.api.domain.AlertChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Discord alerts through a webhook the student creates in their own channel (#62). No bot, no Discord login.
 *
 * <p>Only the webhook id and token are stored ({@code id/token}), never the pasted URL, and messages always go to
 * {@code base-url}. So a pasted URL can never make the server call another host (SSRF), and the stored secret is
 * the smallest one that works. Mentions are switched off, so a message can't ping {@code @everyone}.
 */
@ApplicationScoped
public class DiscordSender implements AlertSender {
    /** Hosts Discord shows webhook URLs on (stable, PTB and Canary clients, and the old domain). */
    static final Set<String> HOSTS = Set.of("discord.com", "ptb.discord.com", "canary.discord.com", "discordapp.com");
    /** {@code /api/webhooks/<id>/<token>}, optionally with an API version like {@code /api/v10/webhooks/...}. */
    static final Pattern PATH = Pattern.compile("/api(?:/v\\d+)?/webhooks/(\\d{17,20})/([A-Za-z0-9_-]{60,80})/?");
    static final int MAX_LENGTH = 2000;

    @ConfigProperty(name = "leoway.alerts.discord.base-url", defaultValue = "https://discord.com")
    String baseUrl;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public AlertChannel.Kind kind() {
        return AlertChannel.Kind.DISCORD;
    }

    @Override
    public String parseTarget(String input) {
        if (input == null || input.isBlank()) throw new InvalidTarget("webhookUrl is missing");
        URI uri;
        try {
            uri = new URI(input.strip());
        } catch (URISyntaxException e) {
            throw new InvalidTarget("webhookUrl is not a Discord webhook URL");
        }
        var path = uri.getPath() == null ? null : PATH.matcher(uri.getPath());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !HOSTS.contains(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || path == null || !path.matches()) {
            throw new InvalidTarget("webhookUrl is not a Discord webhook URL");
        }
        return path.group(1) + "/" + path.group(2);
    }

    @Override
    public void send(String target, String message) {
        ObjectNode body = json.createObjectNode();
        body.put("content", message.length() > MAX_LENGTH ? message.substring(0, MAX_LENGTH) : message);
        body.put("username", "LeoWay");
        body.putObject("allowed_mentions").putArray("parse");
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/webhooks/" + target))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("User-Agent", "LeoWay-school-project")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        int status;
        try {
            status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            // Class name only: messages of network errors can contain the URL, which holds the token (S01).
            throw new Unavailable("Discord not reachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("interrupted");
        }
        if (status >= 200 && status < 300) return;
        // 401: invalid token, 403: no access, 404: webhook deleted. Retrying won't help.
        if (status == 401 || status == 403 || status == 404) throw new Rejected("Discord refused the webhook: " + status);
        throw new Unavailable("Discord returned HTTP " + status);
    }
}

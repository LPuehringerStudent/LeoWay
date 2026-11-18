package at.leoway.api.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Which webhook URLs are accepted (#62). Only Discord's own hosts and paths, so nothing else can be called. */
class DiscordSenderTest {
    static final String ID = "1234567890123456789";
    static final String TOKEN = "aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-aB3_-xyz"; // 68 chars, like real ones
    static final DiscordSender SENDER = new DiscordSender();

    @ParameterizedTest
    @ValueSource(strings = {
        "https://discord.com/api/webhooks/" + ID + "/" + TOKEN,
        "  https://discord.com/api/webhooks/" + ID + "/" + TOKEN + "/  ",
        "https://ptb.discord.com/api/webhooks/" + ID + "/" + TOKEN,
        "https://canary.discord.com/api/v10/webhooks/" + ID + "/" + TOKEN,
        "https://discordapp.com/api/webhooks/" + ID + "/" + TOKEN,
    })
    void acceptsDiscordWebhooksAndKeepsOnlyIdAndToken(String url) {
        assertEquals(ID + "/" + TOKEN, SENDER.parseTarget(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "not a url",
        "http://discord.com/api/webhooks/" + ID + "/" + TOKEN,
        "https://discord.com.evil.example/api/webhooks/" + ID + "/" + TOKEN,
        "https://evil.example/api/webhooks/" + ID + "/" + TOKEN,
        "https://user@discord.com/api/webhooks/" + ID + "/" + TOKEN,
        "https://discord.com:8443/api/webhooks/" + ID + "/" + TOKEN,
        "https://discord.com/api/webhooks/" + ID,
        "https://discord.com/api/webhooks/abc/" + TOKEN,
        "https://discord.com/api/webhooks/" + ID + "/" + TOKEN + "/slack",
        "https://discord.com/api/webhooks/" + ID + "/" + TOKEN + "/../../users/@me",
        "https://discord.com/channels/123/456",
    })
    void rejectsEverythingElse(String url) {
        assertThrows(AlertSender.InvalidTarget.class, () -> SENDER.parseTarget(url));
    }

    @Test
    void errorMessagesNeverRepeatTheInput() {
        String url = "https://evil.example/api/webhooks/" + ID + "/" + TOKEN;
        var e = assertThrows(AlertSender.InvalidTarget.class, () -> SENDER.parseTarget(url));
        assertFalse(e.getMessage().contains(TOKEN));
    }
}

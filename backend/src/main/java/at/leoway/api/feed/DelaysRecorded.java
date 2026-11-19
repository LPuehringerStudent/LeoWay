package at.leoway.api.feed;

import java.time.Instant;

/** Fired (async) after a feed's poll was stored, so delay warnings can be checked (#63). */
public record DelaysRecorded(String source, Instant collectedAt) {}

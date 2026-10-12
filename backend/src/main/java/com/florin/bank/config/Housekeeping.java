package com.florin.bank.config;

import com.florin.bank.auth.AuthSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Expired sessions are already ignored by every lookup; this only stops the table growing forever.
 * Safe to run on every replica at once.
 */
@Component
public class Housekeeping {

    private static final Logger LOG = LoggerFactory.getLogger(Housekeeping.class);

    private final AuthSessionRepository sessions;
    private final Clock clock;

    public Housekeeping(AuthSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    @Transactional
    public void purgeExpiredSessions() {
        int removed = sessions.deleteExpired(clock.instant());
        if (removed > 0) {
            LOG.info("Removed {} expired sessions", removed);
        }
    }
}

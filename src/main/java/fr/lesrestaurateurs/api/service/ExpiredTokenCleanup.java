package fr.lesrestaurateurs.api.service;

import fr.lesrestaurateurs.api.repo.AuthSessionRepository;
import fr.lesrestaurateurs.api.repo.MagicLinkTokenRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purge quotidienne des liens magiques et sessions expirés. */
@Component
public class ExpiredTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanup.class);

    private final MagicLinkTokenRepository tokens;
    private final AuthSessionRepository sessions;

    public ExpiredTokenCleanup(MagicLinkTokenRepository tokens, AuthSessionRepository sessions) {
        this.tokens = tokens;
        this.sessions = sessions;
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        int removedTokens = tokens.deleteExpiredBefore(now);
        int removedSessions = sessions.deleteExpiredBefore(now);
        if (removedTokens > 0 || removedSessions > 0) {
            log.info("Purge : {} liens magiques et {} sessions expirés supprimés",
                    removedTokens, removedSessions);
        }
    }
}

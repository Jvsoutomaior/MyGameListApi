package com.mygamelist.api.security;

import com.mygamelist.api.config.RegistrationRateLimitProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationRateLimiter {

  private final JdbcClient jdbc;
  private final Clock clock;
  private final RegistrationRateLimitProperties properties;

  public RegistrationRateLimiter(
      JdbcClient jdbc, Clock clock, RegistrationRateLimitProperties properties) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.properties = properties;
  }

  @Transactional
  public long retryAfterSeconds(String remoteAddress) {
    Instant now = clock.instant();
    // All requests lock the global bucket first, including requests from the same IP.
    long globalDelay = consume("global", properties.globalRequests(), now);
    if (globalDelay > 0) {
      return globalDelay;
    }

    jdbc.sql(
            """
            DELETE FROM registration_rate_limits
            WHERE bucket_key <> 'global' AND expires_at <= :now
            """)
        .param("now", Timestamp.from(now))
        .update();
    return consume("ip:" + remoteAddress, properties.requestsPerIp(), now);
  }

  private long consume(String key, int limit, Instant now) {
    return jdbc.sql(
            """
            INSERT INTO registration_rate_limits (bucket_key, attempts, expires_at)
            VALUES (:key, 1, :expires)
            ON CONFLICT (bucket_key) DO UPDATE SET
                attempts = CASE
                    WHEN registration_rate_limits.expires_at <= :now THEN 1
                    ELSE LEAST(registration_rate_limits.attempts + 1, :limit + 1)
                END,
                expires_at = CASE
                    WHEN registration_rate_limits.expires_at <= :now THEN :expires
                    ELSE registration_rate_limits.expires_at
                END
            RETURNING attempts, expires_at
            """)
        .param("key", key)
        .param("now", Timestamp.from(now))
        .param("expires", Timestamp.from(now.plusSeconds(properties.windowSeconds())))
        .param("limit", limit)
        .query(
            (result, row) -> {
              if (result.getInt("attempts") <= limit) {
                return 0L;
              }
              Instant expiresAt = result.getTimestamp("expires_at").toInstant();
              return Math.max(1, Math.ceilDiv(Duration.between(now, expiresAt).toMillis(), 1000));
            })
        .single();
  }
}

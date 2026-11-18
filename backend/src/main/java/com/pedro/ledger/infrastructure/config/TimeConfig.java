package com.pedro.ledger.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application clock used by date-sensitive financial operations.
 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock(
      @Value("${ledger.credit-card.zone-id:America/Sao_Paulo}")
      String zoneId
  ) {
    return Clock.system(ZoneId.of(zoneId));
  }
}

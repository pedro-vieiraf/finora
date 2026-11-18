package com.pedro.ledger.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TimeConfigTest {

  @Test
  void shouldCreateClockWithConfiguredZone() {
    Clock clock = new TimeConfig().clock("America/Sao_Paulo");

    assertThat(clock.getZone())
        .isEqualTo(ZoneId.of("America/Sao_Paulo"));
  }
}

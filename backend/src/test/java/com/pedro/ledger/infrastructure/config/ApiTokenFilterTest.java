package com.pedro.ledger.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiTokenFilterTest {

  private static final String TOKEN = "0123456789abcdef".repeat(4);

  @Test
  void rejectsMissingAndIncorrectCredentialsBeforeCallingTheApplication() throws Exception {
    var filter = new ApiTokenFilter(new MockEnvironment().withProperty("FINORA_API_TOKEN", TOKEN));
    var request = new MockHttpServletRequest("POST", "/transactions");
    var response = new MockHttpServletResponse();
    FilterChain blocked = (req, res) -> { throw new AssertionError("Request reached the application"); };
    filter.doFilter(request, response, blocked);
    assertThat(response.getStatus()).isEqualTo(401);

    request.addHeader("Authorization", "Bearer " + "f".repeat(64));
    response = new MockHttpServletResponse();
    filter.doFilter(request, response, blocked);
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void acceptsValidCredentialsAndPreservesCorsPreflight() throws Exception {
    var filter = new ApiTokenFilter(new MockEnvironment().withProperty("FINORA_API_TOKEN", TOKEN));
    var request = new MockHttpServletRequest("GET", "/accounts");
    request.addHeader("Authorization", "Bearer " + TOKEN);
    FilterChain allowed = (req, res) -> ((MockHttpServletResponse) res).setStatus(204);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, allowed);
    assertThat(response.getStatus()).isEqualTo(204);

    response = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest("OPTIONS", "/accounts"), response, allowed);
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  void requiresATokenOnNeonAndRenderButKeepsLocalDevelopmentWorking() {
    var neon = new MockEnvironment();
    neon.setActiveProfiles("neon");
    assertThatThrownBy(() -> new ApiTokenFilter(neon)).isInstanceOf(IllegalStateException.class);
    var render = new MockEnvironment().withProperty("RENDER", "true");
    assertThatThrownBy(() -> new ApiTokenFilter(render)).isInstanceOf(IllegalStateException.class);
    var local = new ApiTokenFilter(new MockEnvironment());
    assertThat(local.shouldNotFilter(new MockHttpServletRequest("GET", "/accounts"))).isTrue();
  }
}

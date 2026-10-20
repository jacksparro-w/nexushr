package com.nexushr.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
  @Test
  void healthEndpointReportsUp() {
    Assertions.assertEquals("UP", new HealthController().health().get("status"));
  }
}

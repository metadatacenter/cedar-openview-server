package org.metadatacenter.cedar.open.health;

import com.codahale.metrics.health.HealthCheck;

public class OpenServerHealthCheck extends HealthCheck {

  public OpenServerHealthCheck() {
  }

  @Override
  protected Result check() throws Exception {
    if (2 * 2 == 5) {
      return Result.unhealthy("Unhealthy, because 2 * 2 == 5");
    }
    return Result.healthy();
  }
}
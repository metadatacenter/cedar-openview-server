package org.metadatacenter.cedar.fairdata.health;

import com.codahale.metrics.health.HealthCheck;

public class FairdataServerHealthCheck extends HealthCheck {

  public FairdataServerHealthCheck() {
  }

  @Override
  protected Result check() throws Exception {
    if (2 * 2 == 5) {
      return Result.unhealthy("Unhealthy, because 2 * 2 == 5");
    }
    return Result.healthy();
  }
}
package org.metadatacenter.cedar.openview.health;

import com.codahale.metrics.health.HealthCheck;

public class OpenViewServerHealthCheck extends HealthCheck {

  public OpenViewServerHealthCheck() {
  }

  @Override
  protected Result check() throws Exception {
    if (2 * 2 == 5) {
      return Result.unhealthy("Unhealthy, because 2 * 2 == 5");
    }
    return Result.healthy();
  }
}
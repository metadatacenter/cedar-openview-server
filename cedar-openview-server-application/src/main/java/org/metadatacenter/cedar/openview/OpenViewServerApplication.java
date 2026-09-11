package org.metadatacenter.cedar.openview;

import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.metadatacenter.cedar.openview.resources.*;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceIndexResource;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.server.cache.user.UserSummaryCache;

public class OpenViewServerApplication extends CedarMicroserviceApplication<OpenViewServerConfiguration> {

  public static void main(String[] args) throws Exception {
    new OpenViewServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.OPENVIEW;
  }

  @Override
  protected void initializeWithBootstrap(Bootstrap<OpenViewServerConfiguration> bootstrap, CedarConfig cedarConfig) {
  }

  @Override
  public void initializeApp() {
    UserSummaryCache.init(cedarConfig, userService);

  }

  @Override
  public void runApp(OpenViewServerConfiguration configuration, Environment environment) {

    final CedarMicroserviceIndexResource index =
        new CedarMicroserviceIndexResource(cedarConfig, getServerName());
    environment.jersey().register(index);

    final TemplateFieldsResource fields = new TemplateFieldsResource(cedarConfig);
    environment.jersey().register(fields);

    final TemplateElementsResource elements = new TemplateElementsResource(cedarConfig);
    environment.jersey().register(elements);

    final TemplatesResource templates = new TemplatesResource(cedarConfig);
    environment.jersey().register(templates);

    final TemplateInstancesResource instances = new TemplateInstancesResource(cedarConfig);
    environment.jersey().register(instances);

    final FoldersResource folders = new FoldersResource(cedarConfig);
    environment.jersey().register(folders);

  }
}

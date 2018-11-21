package org.metadatacenter.cedar.fairdata;

import com.mongodb.MongoClient;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.fairdata.health.FairdataServerHealthCheck;
import org.metadatacenter.cedar.fairdata.resources.*;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplicationWithMongo;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.model.ServerName;

public class FairdataServerApplication extends CedarMicroserviceApplicationWithMongo<FairdataServerConfiguration> {

  public static void main(String[] args) throws Exception {
    new FairdataServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.FAIRDATA;
  }

  @Override
  protected void initializeWithBootstrap(Bootstrap<FairdataServerConfiguration> bootstrap, CedarConfig cedarConfig) {
  }

  @Override
  public void initializeApp() {
    MongoConfig templateServerConfig = cedarConfig.getTemplateServerConfig();
    CedarDataServices.initializeMongoClientFactoryForDocuments(templateServerConfig.getMongoConnection());

    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();

    initMongoServices(mongoClientForDocuments, templateServerConfig);
  }

  @Override
  public void runApp(FairdataServerConfiguration configuration, Environment environment) {

    final IndexResource index = new IndexResource();
    environment.jersey().register(index);

    final TemplateFieldsResource fields = new TemplateFieldsResource(cedarConfig, templateFieldService);
    environment.jersey().register(fields);

    final TemplateElementsResource elements = new TemplateElementsResource(cedarConfig, templateElementService);
    environment.jersey().register(elements);

    final TemplatesResource templates = new TemplatesResource(cedarConfig, templateService);
    environment.jersey().register(templates);

    final TemplateInstancesResource instances = new TemplateInstancesResource(cedarConfig, templateInstanceService);
    environment.jersey().register(instances);

    final FairdataServerHealthCheck healthCheck = new FairdataServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);
  }
}

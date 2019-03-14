package org.metadatacenter.cedar.open;

import com.mongodb.MongoClient;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.open.health.OpenServerHealthCheck;
import org.metadatacenter.cedar.open.resources.*;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplicationWithMongo;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.model.ServerName;

public class OpenServerApplication extends CedarMicroserviceApplicationWithMongo<OpenServerConfiguration> {

  public static void main(String[] args) throws Exception {
    new OpenServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.OPEN;
  }

  @Override
  protected void initializeWithBootstrap(Bootstrap<OpenServerConfiguration> bootstrap, CedarConfig cedarConfig) {
  }

  @Override
  public void initializeApp() {
    CedarDataServices.initializeNeo4jServices(cedarConfig);

    MongoConfig artifactServerConfig = cedarConfig.getArtifactServerConfig();
    CedarDataServices.initializeMongoClientFactoryForDocuments(artifactServerConfig.getMongoConnection());

    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();

    initMongoServices(mongoClientForDocuments, artifactServerConfig);
  }

  @Override
  public void runApp(OpenServerConfiguration configuration, Environment environment) {

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

    final OpenServerHealthCheck healthCheck = new OpenServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);
  }
}

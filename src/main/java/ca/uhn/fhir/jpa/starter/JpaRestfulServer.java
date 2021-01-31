package ca.uhn.fhir.jpa.starter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import ca.uhn.fhir.context.FhirVersionEnum;

import javax.servlet.ServletException;

@Import(AppProperties.class)
public class JpaRestfulServer extends BaseJpaRestfulServer {

  @Autowired
  AppProperties appProperties;

  @Autowired
  CollectDataResourceProvider collectDataResourceProvider;

  private static final long serialVersionUID = 1L;

  public JpaRestfulServer() {
    super();
  }

  @Override
  protected void initialize() throws ServletException {
    super.initialize();

    this.registerInterceptor(new AuthenticationInterceptor());

 //This resource Provider Should only be registerd for FHIR Context R4
    if (appProperties.getFhir_version() == FhirVersionEnum.R4) {
      this.registerProvider(collectDataResourceProvider);
    }
  }

}

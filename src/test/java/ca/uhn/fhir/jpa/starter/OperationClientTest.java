package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.util.BundleUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import com.google.common.base.Charsets;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ca.uhn.fhir.util.TestUtil.waitForSize;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class, properties =
  {
    "spring.batch.job.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:dbr4",
    "hapi.fhir.fhir_version=r4",
    "hapi.fhir.subscription.websocket_enabled=true",
    "hapi.fhir.empi_enabled=true",
    //Override is currently required when using Empi as the construction of the Empi beans are ambiguous as they are constructed multiple places. This is evident when running in a spring boot environment
    "spring.main.allow-bean-definition-overriding=true"
  })
public class OperationClientTest {

    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ExampleServerDstu2IT.class);
    private IGenericClient ourClient;
    private FhirContext ourCtx;
    protected static CloseableHttpClient ourHttpClient;
    protected static String ourServerBase;
    String ourServerBase2 ;

    // @Autowired
    // protected FhirContext myFhirCtx;
  
    @LocalServerPort
    private int port;

    @Test
    public void testOperation () throws ClientProtocolException, IOException {

      Measure measure = new Measure();
      measure.setId("TX-PVLS");
      measure.addIdentifier().setValue("Measure TX-PVLS");
      IIdType id =ourClient.update().resource(measure).withId("TX-PVLS").execute().getId();
      // System.out.println(">>>>>>>>>>>*****************finally  aaaaaaa****************<<<<<<<<<<<");
      // System.out.println(id.getValue());
      // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");


    

    
      //Parameters params = fetchParameter(ourServerBase + "$collect-data?periodStart=2020-01-01&periodEnd=2020-12-31", EncodingEnum.JSON);
      
      String resp = fetchREsp(ourServerBase + "Measure" + "/TX-PVLS/$collect-data?periodStart=2020-01-01&periodEnd=2020-12-31");

    
      //String resp = fetchREsp(ourServerBase + "Measure/TX-PVLS/$collect-data?periodStart=2020-01-01&periodEnd=2020-12-31");
      //  System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
      // System.out.println(resp);
      // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");

      // Parameters respParams = ourClient
			// .operation()
      // .onType(Measure.class)
      // .named(PlirConstants.OPERATION_COLLECT_DATA)
      // .withNoParameters(Parameters.class)
      // .execute();
      
      // Measure measure = fetchMeasure("http://localhost:8888/fhir/Measure/TX-PVLS", EncodingEnum.JSON);
    
      // String expression = measure.getGroupFirstRep().getPopulationFirstRep().getCriteria().getExpression();

      // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
      // System.out.println(expression);
      // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
    }

    @BeforeEach
    void beforeEach() {
  
      ourCtx = FhirContext.forR4();
      ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
      ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
      ourCtx.setParserErrorHandler(new StrictErrorHandler());
      
      ourServerBase = "http://localhost:" + port + "/fhir/";
      ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
      ourClient.registerInterceptor(new LoggingInterceptor(true));

      //ourServerBase2 = "http://localhost:" + port + "/fhir/context/";
      
      //Create an HTTP basic auth interceptor
      String username = "hapi";
      String password = "hapi123";
      IClientInterceptor authInterceptor = new BasicAuthInterceptor(username, password);
      ourClient.registerInterceptor(authInterceptor); 

      ourHttpClient = HttpClientBuilder.create().build();
    }

    private Parameters fetchParameter(String theUrl, EncodingEnum theEncoding) throws IOException, ClientProtocolException {
    Parameters parameters;
    HttpGet get = new HttpGet(theUrl);
    
    

		CloseableHttpResponse resp = ourHttpClient.execute(get);
		try {
			assertEquals(theEncoding.getResourceContentTypeNonLegacy(), resp.getFirstHeader(ca.uhn.fhir.rest.api.Constants.HEADER_CONTENT_TYPE).getValue().replaceAll(";.*", ""));
      parameters = theEncoding.newParser(ourCtx).parseResource(Parameters.class, IOUtils.toString(resp.getEntity().getContent(), Charsets.UTF_8));
		} finally {
			IOUtils.closeQuietly(resp);
		}
		
		return parameters;
  }

  private Measure fetchMeasure(String theUrl, EncodingEnum theEncoding) throws IOException, ClientProtocolException {
    Measure measure;
    HttpGet get = new HttpGet(theUrl);

    String username = "hapi";
    String password = "hapi123";

    String auth = username + ":" + password;
    byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
     String authHeader = "Basic " + new String(encodedAuth);
     get.addHeader(HttpHeaders.AUTHORIZATION, authHeader);

    get.addHeader(Constants.HEADER_CACHE_CONTROL, Constants.CACHE_CONTROL_NO_CACHE);
    
    

		CloseableHttpResponse resp = ourHttpClient.execute(get);
		try {
			assertEquals(theEncoding.getResourceContentTypeNonLegacy(), resp.getFirstHeader(ca.uhn.fhir.rest.api.Constants.HEADER_CONTENT_TYPE).getValue().replaceAll(";.*", ""));
      measure = theEncoding.newParser(ourCtx).parseResource(Measure.class, IOUtils.toString(resp.getEntity().getContent(), Charsets.UTF_8));
		} finally {
			IOUtils.closeQuietly(resp);
		}
		
		return measure;
  }
  

  private String fetchREsp(String theUrl) throws IOException, ClientProtocolException {
    HttpGet get = new HttpGet(theUrl);

    String username = "hapi";
    String password = "hapi123";

    String auth = username + ":" + password;
    byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
     String authHeader = "Basic " + new String(encodedAuth);
     get.addHeader(HttpHeaders.AUTHORIZATION, authHeader);

    get.addHeader(Constants.HEADER_CACHE_CONTROL, Constants.CACHE_CONTROL_NO_CACHE);
    CloseableHttpResponse resp = ourHttpClient.execute(get);
    
    HttpEntity responseEntity = resp.getEntity();
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		responseEntity.writeTo(byteStream);
		
    return byteStream.toString();
    //return resp.getStatusLine().toString();
	}
  }
    


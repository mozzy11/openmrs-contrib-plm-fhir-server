package ca.uhn.fhir.jpa.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class, properties = {
    "spring.batch.job.enabled=false", "spring.datasource.url=jdbc:h2:mem:dbr4", "hapi.fhir.fhir_version=r4",
    "hapi.fhir.subscription.websocket_enabled=true", "hapi.fhir.empi_enabled=true",
    // Override is currently required when using Empi as the construction of the
    // Empi beans are ambiguous as they are constructed multiple places. This is
    // evident when running in a spring boot environment
    "spring.main.allow-bean-definition-overriding=true" })
public class OperationClientTest {

  private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ExampleServerDstu2IT.class);
  private IGenericClient ourClient;
  private FhirContext ourCtx;
  protected static CloseableHttpClient ourHttpClient;
  protected static String ourServerBase;

  private static String OBS_FILE_PATH = "src/test/resources/ObsBundle.json";

  private static String PATIENT_FILE_PATH = "src/test/resources/PatientBundle.json";

  private static String MEASURE_FILE_PATH = "src/test/resources/FhirMeasure.json";

  private static String MEASURE_RESOURCE_ID = "TX-PVLS";

  @LocalServerPort
  private int port;

  @Test
  public void testCollectOperation() throws ClientProtocolException, IOException {

    // Post the Measure Resource

    Measure measure = readMeasureFromFile();
    ourClient.update().resource(measure).withId(MEASURE_RESOURCE_ID).encodedJson().execute();

    // post the patient BUndle
    postResource(ourServerBase, PATIENT_FILE_PATH);

    // post obs BUndle
    postResource(ourServerBase, OBS_FILE_PATH);

    // fetch parameter
    Parameters params = fetchParameter(ourServerBase + "$collect-data?periodStart=2020-01-01&periodEnd=2020-12-31");

    // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
    // System.out.println(expression);
    // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
  }

  @BeforeEach
  void beforeEach() {
    ourServerBase = "http://localhost:" + port + "/fhir/";
    setHapiClient();
    ourHttpClient = HttpClientBuilder.create().build();

  }

  private Parameters fetchParameter(String theUrl) throws IOException, ClientProtocolException {
    Parameters parameters;
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
      assertEquals(EncodingEnum.JSON.getResourceContentTypeNonLegacy(),
          resp.getFirstHeader(ca.uhn.fhir.rest.api.Constants.HEADER_CONTENT_TYPE).getValue().replaceAll(";.*", ""));
      parameters = EncodingEnum.JSON.newParser(ourCtx).parseResource(Parameters.class,
          IOUtils.toString(resp.getEntity().getContent(), Charsets.UTF_8));
    } finally {
      IOUtils.closeQuietly(resp);
    }
    return parameters;
  }

  private CloseableHttpResponse postResource(String theUrl, String path) throws IOException, ClientProtocolException {
    HttpPost post = new HttpPost(theUrl);
    String username = "hapi";
    String password = "hapi123";
    String json = readJsonFile(path);

    StringEntity entity = new StringEntity(json);
    post.setEntity(entity);
    post.setHeader("Accept", "application/json");
    post.setHeader("Content-type", "application/json");

    String auth = username + ":" + password;
    byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
    String authHeader = "Basic " + new String(encodedAuth);
    post.addHeader(HttpHeaders.AUTHORIZATION, authHeader);

    post.addHeader(Constants.HEADER_CACHE_CONTROL, Constants.CACHE_CONTROL_NO_CACHE);
    CloseableHttpResponse resp = ourHttpClient.execute(post);

    return resp;
  }

  private String readJsonFile(String path) throws IOException {
    String content = null;
    try {
      content = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return content;
  }

  private void setHapiClient() {
    ourCtx = FhirContext.forR4();
    ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
    ourCtx.setParserErrorHandler(new StrictErrorHandler());

    ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
    ourClient.registerInterceptor(new LoggingInterceptor(true));

    // Create an HTTP basic auth interceptor
    String username = "hapi";
    String password = "hapi123";
    IClientInterceptor authInterceptor = new BasicAuthInterceptor(username, password);
    ourClient.registerInterceptor(authInterceptor);
  }

  public Measure readMeasureFromFile() throws IOException {
    String json = readJsonFile(MEASURE_FILE_PATH);

    IParser parser = ourCtx.newJsonParser();
    Measure measure = parser.parseResource(Measure.class, json);
    return measure;
  }

  // HttpEntity responseEntity = resp.getEntity();
  // ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
  // responseEntity.writeTo(byteStream);

  // return byteStream.toString();
  // //return resp.getStatusLine().toString();

}

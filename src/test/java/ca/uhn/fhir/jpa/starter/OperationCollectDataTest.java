package ca.uhn.fhir.jpa.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
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
public class OperationCollectDataTest {

  private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OperationCollectDataTest.class);
  private IGenericClient ourClient;
  private FhirContext ourCtx;
  protected static CloseableHttpClient ourHttpClient;
  protected static String ourServerBase;

  private static String OBS_FILE_PATH = "src/test/resources/ObsBundle.json";

  private static String MEASURE_FILE_PATH = "src/test/resources/FhirMeasure.json";

  private static String MEASURE_RESOURCE_ID = "TX-PVLS";

  private static String USER_NAME = "hapi";

  private static String USER_PASSWORD = "hapi123";

  @LocalServerPort
  private int port;

  @Test
  public void testCollectDataOperation() throws ClientProtocolException, IOException {
    String paramName1 = "measureReport";
    String paramName2 = "resource";
    // Post the Measure Resource
    Measure measure = readMeasureFromFile();
    ourClient.update().resource(measure).withId(MEASURE_RESOURCE_ID).encodedJson().execute();
    // post theobs BUndle
    postResource(ourServerBase, OBS_FILE_PATH);
    // fetch parameter reuslt from the Opration
    Parameters result = fetchParameter(ourServerBase + "/Measure/" + MEASURE_RESOURCE_ID
        + "/$collect-data?periodStart=2021-01-01&periodEnd=2021-01-31");

    assertTrue(result.hasParameter(paramName1));
    assertTrue(result.hasParameter(paramName2));
    assertEquals(5, result.getParameter().size());

    assertTrue(result.getParameter().get(0).getResource() instanceof MeasureReport);
    assertTrue(result.getParameter().get(1).getResource() instanceof Observation);
    assertTrue(result.getParameter().get(2).getResource() instanceof Observation);
    assertTrue(result.getParameter().get(3).getResource() instanceof Patient);
    assertTrue(result.getParameter().get(4).getResource() instanceof Patient);
    // get measure report from the Parameter Result
    MeasureReport report = (MeasureReport) result.getParameter().get(0).getResource();
    assertEquals(report.getEvaluatedResource().size(), 4);
    assertEquals(report.getMeasure(), "Measure/TX_PVLS");
    assertEquals(report.getStatus(), MeasureReport.MeasureReportStatus.COMPLETE);
    assertEquals(report.getType(), MeasureReport.MeasureReportType.DATACOLLECTION);
    // get Observation Bundle from the Parameter Result
    Observation observation1 = (Observation) result.getParameter().get(1).getResource();
    assertEquals(observation1.getCode().getCodingFirstRep().getCode(), "1305AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

    Observation observation2 = (Observation) result.getParameter().get(2).getResource();
    assertEquals(observation2.getCode().getCodingFirstRep().getCode(), "856AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
  }

  @BeforeEach
  void beforeEach() {
    ourServerBase = "http://localhost:" + port + "/fhir";
    ourCtx = FhirContext.forR4();
    ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
    ourCtx.setParserErrorHandler(new StrictErrorHandler());

    ourHttpClient = HttpClientBuilder.create().build();
    setHapiClient();
  }

  private Parameters fetchParameter(String theUrl) throws IOException, ClientProtocolException {
    Parameters parameters;
    HttpGet get = new HttpGet(theUrl);

    String auth = USER_NAME + ":" + USER_PASSWORD;
    byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
    String authHeader = "Basic " + new String(encodedAuth);
    get.addHeader(HttpHeaders.AUTHORIZATION, authHeader);

    get.addHeader(Constants.HEADER_CACHE_CONTROL, Constants.CACHE_CONTROL_NO_CACHE);

    try (CloseableHttpResponse resp = ourHttpClient.execute(get)) {
      parameters = ourCtx.newJsonParser().parseResource(Parameters.class,
          EntityUtils.toString(resp.getEntity(), Charsets.UTF_8));
    }
    return parameters;
  }

  private CloseableHttpResponse postResource(String theUrl, String filePath)
      throws IOException, ClientProtocolException {
    HttpPost post = new HttpPost(theUrl);
    String json = readJsonFile(filePath);

    StringEntity entity = new StringEntity(json);
    post.setEntity(entity);
    post.setHeader("Accept", "application/json");
    post.setHeader("Content-type", "application/json");

    String auth = USER_NAME + ":" + USER_PASSWORD;
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
    ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
    ourClient.registerInterceptor(new LoggingInterceptor(true));
    // Create an HTTP basic auth interceptor
    IClientInterceptor authInterceptor = new BasicAuthInterceptor(USER_NAME, USER_PASSWORD);
    ourClient.registerInterceptor(authInterceptor);
  }

  public Measure readMeasureFromFile() throws IOException {
    String json = readJsonFile(MEASURE_FILE_PATH);

    IParser parser = ourCtx.newJsonParser();
    Measure measure = parser.parseResource(Measure.class, json);
    return measure;
  }
}

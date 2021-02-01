package ca.uhn.fhir.jpa.starter;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Conditional(OnR4Condition.class)
@Lazy
public class CollectDataResourceProvider {

	private static final Logger log = LoggerFactory.getLogger(CollectDataResourceProvider.class);

	private FhirContext context;

	private IFhirResourceDao<Measure> dao;

	private int port = 8080;

	@Operation(name = PlirConstants.OPERATION_COLLECT_DATA, idempotent = true, type = Measure.class)
	@SuppressWarnings("unused")
	public Parameters collectDataOperation(HttpServletRequest req, @IdParam IdType theId,
			@OperationParam(name = PlirConstants.PARAM_PERIOD_START, min = 1, max = 1) DateParam periodStart,
			@OperationParam(name = PlirConstants.PARAM_PERIOD_END, min = 1, max = 1) DateParam periodEnd) {

		Measure measure = getDao().read(theId);

		if (measure == null) {
			throw new ResourceNotFoundException("Could not find Measure/" + theId.getIdPart());
		}

		String fullUrl = constructUrl(measure, periodStart, periodEnd);
		log.info("Sending request for {}", fullUrl);

		Parameters parameters = new Parameters();
		try {
			Bundle bundle = fetchBundle(fullUrl, req, theId);
			MeasureReport report = generateMeasureReport(req, bundle, measure);

			parameters.addParameter(new Parameters.ParametersParameterComponent()
					.setName(PlirConstants.PARAMETER_NAME_MEASURE_REPORT).setResource(report));
			generateObservationData(parameters, bundle);
		}
		catch (IOException e) {
			log.error("Caught exception while evaluating measure {}", theId.getIdPart(), e);
			throw new InternalErrorException("Could not collect data for measure " + theId, e);
		}
		return parameters;
	}

	public FhirContext getContext() {
		return context;
	}

	@Autowired
	public void setContext(FhirContext context) {
		this.context = context;
	}

	public IFhirResourceDao<Measure> getDao() {
		return dao;
	}

	@Autowired
	public void setDao(IFhirResourceDao<Measure> dao) {
		this.dao = dao;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@EventListener
	public void onApplicationEvent(ServletWebServerInitializedEvent event) {
		setPort(event.getWebServer().getPort());
	}

	private MeasureReport generateMeasureReport(HttpServletRequest req, Bundle bundle, Measure measure) throws IOException {
		MeasureReport report = new MeasureReport();
		report.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
		report.setType(MeasureReport.MeasureReportType.DATACOLLECTION);

		report.setMeasure(measure.getResourceType().name() + "/"
				+ measure.getIdentifierFirstRep().getValue());

		report.setEvaluatedResource(bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
				.map(Bundle.BundleEntryComponent::getResource).peek(r -> {
					IdType idElement = r.getIdElement();
					String scheme = req.getScheme();
					StringBuilder sb = new StringBuilder(scheme).append("://").append(req.getServerName());
					if (("https".equalsIgnoreCase(scheme) && req.getServerPort() != 443) ||
							("http".equalsIgnoreCase(scheme) && req.getServerPort() != 80)) {
						sb.append(":").append(req.getServerPort());
					}

					String baseUrl = sb.append(req.getServletPath()).toString();
					idElement.setParts(baseUrl, idElement.getResourceType(), idElement.getIdPart(),
							idElement.getVersionIdPart());

					r.setIdElement(idElement);
				}).map(Reference::new).collect(Collectors.toList()));
		return report;
	}

	private void generateObservationData(Parameters parameters, Bundle bundle) throws IOException {
		bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
				.map(Bundle.BundleEntryComponent::getResource)
				.forEach(r -> parameters.addParameter().setName(PlirConstants.PARAMETER_NAME_RESOURCE).setResource(r));
	}

	private String constructUrl(Measure measure, DateParam periodStart,
			DateParam periodEnd) {
		ParamPrefixEnum startPrefix = ParamPrefixEnum.GREATERTHAN_OR_EQUALS;
		if (periodStart.getPrefix() != null) {
			startPrefix = periodStart.getPrefix();
		}

		ParamPrefixEnum endPrefix = ParamPrefixEnum.LESSTHAN_OR_EQUALS;
		if (periodEnd.getPrefix() != null) {
			endPrefix = periodEnd.getPrefix();
		}

		if (periodEnd.getPrefix() == null) {
			periodEnd.setPrefix(ParamPrefixEnum.LESSTHAN_OR_EQUALS);
		}

		return "http://127.0.0.1:" + port + "/fhir/" + measure.getGroupFirstRep().getPopulationFirstRep().getCriteria()
				.getExpression()
				+ "&date=" + startPrefix.getValue() + periodStart.getValueAsString()
				+ "&date=" + endPrefix.getValue() + periodEnd.getValueAsString();
	}

	private Bundle fetchBundle(String theUrl, HttpServletRequest httpRequest, IdType theId) throws IOException {
		try (CloseableHttpClient ourHttpClient = HttpClientBuilder.create().build()) {
			HttpGet get = new HttpGet(theUrl);

			final String authorization = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
			if (authorization != null) {
				get.addHeader(HttpHeaders.AUTHORIZATION, authorization);
			}
			get.addHeader(HttpHeaders.ACCEPT, "application/fhir+json");

			try (CloseableHttpResponse resp = ourHttpClient.execute(get)) {
				String respEntity = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
				try {
					return getContext().newJsonParser().parseResource(Bundle.class, respEntity);
				}
				catch (DataFormatException e) {
					log.error("An exception occurred while trying to parse the bundle returned from the server {}",
							respEntity, e);
					throw new InternalErrorException("Could not collect data for measure " + theId, e);
				}
			}
		}
	}
}

package ca.uhn.fhir.jpa.starter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
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
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.rp.r4.MeasureResourceProvider;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class CollectDataResourceProvider {

    @Autowired
    private MeasureResourceProvider measureResourceProvider;

    private static final FhirContext CONTEXT = FhirContext.forR4();

    @Operation(name = PlirConstants.OPERATION_COLLECT_DATA, idempotent = true, type = Measure.class)
    public Parameters collectDataOperation(HttpServletRequest theServletRequest, @IdParam IdType theId,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_START, min = 1, max = 1) String periodStart,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_END, min = 1, max = 1) String periodEnd)
            throws ClientProtocolException, IOException {

        Measure measure = this.measureResourceProvider.getDao().read(theId);

        if (measure == null) {
            throw new ResourceNotFoundException("Could not find Measure/" + theId.getIdPart());
        }
        String fullUrl = constructUrl(theServletRequest, measure, periodStart, periodEnd);
        Parameters parameters = new Parameters();
        Bundle bundle = fetchBundle(fullUrl, theServletRequest);
        MeasureReport report = generateMeasureReport(bundle, measure);

        parameters.addParameter(new Parameters.ParametersParameterComponent()
                .setName(PlirConstants.PARAMETER_NAME_MEASURE_REPORT).setResource(report));
        generateObservationData(parameters, bundle);
        return parameters;
    }

    public MeasureReport generateMeasureReport(Bundle bundle, Measure measure)
            throws ClientProtocolException, IOException {
        StringBuffer measureName = new StringBuffer(measure.getResourceType().name());
        measureName.append("/");
        measureName.append(measure.getIdentifierFirstRep().getValue());

        MeasureReport report = new MeasureReport();
        report.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        report.setType(MeasureReport.MeasureReportType.DATACOLLECTION);
        report.setMeasure(measureName.toString());

        List<Reference> references = bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
                .map(Bundle.BundleEntryComponent::getResource).map(Reference::new).collect(Collectors.toList());
        report.setEvaluatedResource(references);
        return report;
    }

    public void generateObservationData(Parameters parameters, Bundle bundle)
            throws ClientProtocolException, IOException {
        bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
                .map(Bundle.BundleEntryComponent::getResource)
                .forEach(r -> parameters.addParameter().setName(PlirConstants.PARAMETER_NAME_RESOURCE).setResource(r));
    }

    private String constructUrl(HttpServletRequest theServletRequest, Measure measure, String periodStart,
            String periodEnd) {
        StringBuffer baseUrl = new StringBuffer(theServletRequest.getScheme());
        baseUrl.append("://");
        baseUrl.append(theServletRequest.getServerName());
        baseUrl.append(":");
        baseUrl.append(theServletRequest.getServerPort());
        baseUrl.append(theServletRequest.getServletPath());
        baseUrl.append("/");

        String expression = measure.getGroupFirstRep().getPopulationFirstRep().getCriteria().getExpression();
        StringBuffer params = new StringBuffer("&date=ge");
        params.append(periodStart);
        params.append("&date=lt");
        params.append(periodEnd);
        String fullUrl = baseUrl + expression + params;
        return fullUrl;
    }

    private Bundle fetchBundle(String theUrl, HttpServletRequest httpRequest)
            throws IOException, ClientProtocolException {
        Bundle bundle;
        CloseableHttpClient ourHttpClient = HttpClientBuilder.create().build();
        HttpGet get = new HttpGet(theUrl);

        final String authorization = httpRequest.getHeader("Authorization");

        String base64Credentials = "";
        if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
            base64Credentials = authorization.substring("Basic".length()).trim();
        }
        String authHeader = "Basic " + new String(base64Credentials);
        get.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        get.addHeader(Constants.HEADER_CACHE_CONTROL, Constants.CACHE_CONTROL_NO_CACHE);

        try (CloseableHttpResponse resp = ourHttpClient.execute(get)) {
            bundle = CONTEXT.newJsonParser().parseResource(Bundle.class,
                    EntityUtils.toString(resp.getEntity(), Charsets.UTF_8));
        }
        return bundle;
    }
}

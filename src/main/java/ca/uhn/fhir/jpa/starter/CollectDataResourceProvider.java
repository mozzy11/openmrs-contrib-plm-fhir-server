package ca.uhn.fhir.jpa.starter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Charsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.rp.r4.MeasureResourceProvider;
import ca.uhn.fhir.jpa.rp.r4.ObservationResourceProvider;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;

public class CollectDataResourceProvider {
    protected static CloseableHttpClient ourHttpClient;

    @Autowired
    MeasureResourceProvider measureResourceProvider;

    @Autowired
    ObservationResourceProvider ObservationResourceProvider;

    FhirContext ctx = FhirContext.forR4();

    @Operation(name = PlirConstants.OPERATION_COLLECT_DATA, idempotent = true, type = Measure.class)
    public Parameters collectDataOperation(HttpServletRequest theServletRequest, @IdParam IdType theId,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_START, min = 1, max = 1) String periodStart,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_END, min = 1, max = 1) String periodEnd)
            throws ClientProtocolException, IOException {

        Measure measure = this.measureResourceProvider.getDao().read(theId);

        String fullUrl = constructUrl(theServletRequest, measure, periodStart, periodEnd);

        Parameters parameters = new Parameters();

        MeasureReport report = evaluateMeasure(measure, fullUrl);

        parameters.addParameter(new Parameters.ParametersParameterComponent()
                .setName(PlirConstants.RESOURCE_MEASURE_REPORT).setResource(report));

        return parameters;
    }

    public MeasureReport evaluateMeasure(Measure measure, String url) throws ClientProtocolException, IOException {

        if (measure == null) {
            throw new RuntimeException("Could not find Measure/" + measure.getId());
        }
        MeasureReport report = new MeasureReport();
        report.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        report.setType(MeasureReport.MeasureReportType.DATACOLLECTION);
        report.setMeasure(PlirConstants.MEASURE_RESOURCE_MEASURE);

        Bundle bundle = fetchBundle(url);
        // String bundle = fetchBundle(url);
        // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
        // System.out.println(bundle);
        // System.out.println(">>>>>>>>>>>*********************************<<<<<<<<<<<");
        // report.setEvaluatedResource(theEvaluatedResource);
        return report;

    }

    private String constructUrl(HttpServletRequest theServletRequest, Measure measure, String periodStart,
            String periodEnd) {
        StringBuffer baseUrl = new StringBuffer(theServletRequest.getScheme());
        baseUrl.append("://");
        baseUrl.append(theServletRequest.getServerName());
        baseUrl.append(":");
        baseUrl.append(theServletRequest.getServerPort());

        String expression = measure.getGroupFirstRep().getPopulationFirstRep().getCriteria().getExpression();

        StringBuffer params = new StringBuffer("&date=ge");
        params.append(periodStart);
        params.append("&date=lt");
        params.append(periodEnd);

        String fullUrl = baseUrl + "/fhir/" + expression + params;

        return fullUrl;
    }

    private Bundle fetchBundle(String theUrl) throws IOException, ClientProtocolException {

        Bundle bundle;
        ourHttpClient = HttpClientBuilder.create().build();
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
        bundle = EncodingEnum.JSON.newParser(ctx).parseResource(Bundle.class,
        IOUtils.toString(resp.getEntity().getContent(), Charsets.UTF_8));
        } finally {
        IOUtils.closeQuietly(resp);
        }
        return bundle;
    }

}

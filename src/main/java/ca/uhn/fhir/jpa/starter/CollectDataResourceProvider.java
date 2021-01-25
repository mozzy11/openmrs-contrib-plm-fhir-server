package ca.uhn.fhir.jpa.starter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.jpa.rp.r4.MeasureResourceProvider;
import ca.uhn.fhir.jpa.rp.r4.ObservationResourceProvider;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.Constants;
import org.apache.http.impl.client.HttpClientBuilder;

public class CollectDataResourceProvider {
    protected static CloseableHttpClient ourHttpClient;

    @Autowired
    MeasureResourceProvider measureResourceProvider;

    @Autowired
    ObservationResourceProvider ObservationResourceProvider;

    @Operation(name = PlirConstants.OPERATION_COLLECT_DATA, idempotent = true, type = Measure.class)
    public Parameters collectDataOperation(
            HttpServletRequest theServletRequest,
            @IdParam IdType theId,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_START, min = 1, max = 1) String periodStart,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_END, min = 1, max = 1) String periodEnd) {

        Parameters  parameters = new Parameters();

        MeasureReport report = evaluateMeasure(theId, periodStart ,periodEnd);

        Measure measure = this.measureResourceProvider.getDao().read(theId);


        parameters.addParameter(
            new Parameters.ParametersParameterComponent().setName(PlirConstants.RESOURCE_MEASURE_REPORT).setResource(report));

      System.out.println(">>>>>>>>>>***************ya we made it man*****************<<<<<<<<<<<<<<<<");
        String baseUrl = theServletRequest.getContextPath() ;
        System.out.println("Base Url :" + baseUrl);

        String servletPath = theServletRequest.getServletPath();

        System.out.println("servletPath :" + servletPath);

        ServletContext serv = theServletRequest.getServletContext();

        String contextPath = serv.getContextPath();

        System.out.println("contextPath :" + contextPath);
        
        String url = theServletRequest.getRequestURI() ;

        System.out.println("whole Url :" + url);
        String params = theServletRequest.getQueryString() ;

        System.out.println("query String :" + params);
       System.out.println(">>>>>>>>>>***************my Measure id*****************<<<<<<<<<<<<<<<<");   

        // IIdType theId //
        System.out.println(">>>>>>>>>>***************ya we made it man*****************<<<<<<<<<<<<<<<<");
        System.out.println("measure : " + measure.getIdentifier().iterator().next().getValue());

        System.out.println("periodStart :" + periodStart);
        // System.out.println(">>>>>>>>>>***************my Measure id*****************<<<<<<<<<<<<<<<<");
        return  parameters;
    }

    public MeasureReport evaluateMeasure(@IdParam IdType theId,
            @OperationParam(name = "periodStart") String periodStart,
            @OperationParam(name = "periodEnd") String periodEnd) {

        Measure measure = this.measureResourceProvider.getDao().read(theId);
        String expression = measure.getGroupFirstRep().getPopulationFirstRep().getCriteria().getExpression();


        if (measure == null) {
            throw new RuntimeException("Could not find Measure/" + theId.getIdPart());
        }

        MeasureReport report = new MeasureReport();
        report.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        report.setType(MeasureReport.MeasureReportType.DATACOLLECTION);
        report.setMeasure(PlirConstants.MEASURE_RESOURCE_MEASURE);

        //Bundle bundle  = ObservationResourceProvider.getDao().search(theParams);
        //report.setEvaluatedResource(theEvaluatedResource);
        return report;

    }


    private String fetchREsp(String theUrl) throws IOException, ClientProtocolException {

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
        
        HttpEntity responseEntity = resp.getEntity();
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            responseEntity.writeTo(byteStream);
            
        return byteStream.toString();
        //return resp.getStatusLine().toString();
        }

}

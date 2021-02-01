package ca.uhn.fhir.jpa.starter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.jpa.rp.r4.MeasureResourceProvider;
import ca.uhn.fhir.jpa.rp.r4.ObservationResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class CollectDataResourceProvider {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CollectDataResourceProvider.class);

    @Autowired
    private MeasureResourceProvider measureResourceProvider;

    @Autowired
    private ObservationResourceProvider observationResourceProvider;

    @Operation(name = PlirConstants.OPERATION_COLLECT_DATA, idempotent = true, type = Measure.class)
    public Parameters collectDataOperation(HttpServletRequest theServletRequest, @IdParam IdType theId,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_START, min = 1, max = 1) String periodStart,
            @OperationParam(name = PlirConstants.PARAM_PERIOD_END, min = 1, max = 1) String periodEnd) {

        Measure measure = this.measureResourceProvider.getDao().read(theId);

        if (measure == null) {
            throw new ResourceNotFoundException("Could not find Measure/" + theId.getIdPart());
        }
        Parameters parameters = new Parameters();
        try {
            Bundle bundle = fetchBundle(measure, periodStart, periodEnd);
            MeasureReport report = generateMeasureReport(bundle, measure);

            parameters.addParameter(new Parameters.ParametersParameterComponent()
                    .setName(PlirConstants.PARAMETER_NAME_MEASURE_REPORT).setResource(report));
            generateObservationData(parameters, bundle);
        } catch (IOException e) {
            log.error("Caught exception while evaluating measure {}", theId.getIdPart(), e);
            throw new InternalErrorException("Could not process measure " + theId, e);
        }
        return parameters;
    }

    private MeasureReport generateMeasureReport(Bundle bundle, Measure measure) throws IOException {
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

    private void generateObservationData(Parameters parameters, Bundle bundle) throws IOException {
        bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
                .map(Bundle.BundleEntryComponent::getResource)
                .forEach(r -> parameters.addParameter().setName(PlirConstants.PARAMETER_NAME_RESOURCE).setResource(r));
    }

    private Bundle fetchBundle(Measure measure, String startDate, String endDate) {
        String expression = measure.getGroupFirstRep().getPopulationFirstRep().getCriteria().getExpression();

        String code1 = "";
        String code2 = "";
        String includeValue = "";

        if (expression.contains("?code=")) {
            String[] parts = expression.split("\\?code=");
            String allParams = parts[1];
            if (allParams.contains("&_include=")) {
                String[] paramValues = allParams.split("&_include=");
                String conceptCodes = paramValues[0];
                if (conceptCodes.contains(",")) {
                    String[] conceptCodesArray = conceptCodes.split(",");
                    code1 = conceptCodesArray[0];
                    code2 = conceptCodesArray[1];
                } else {
                    code1 = conceptCodes;
                }
                includeValue = paramValues[1];
            }

        }

        TokenOrListParam code = new TokenOrListParam();
        TokenParam codingToken = new TokenParam();
        codingToken.setValue(code1);
        code.add(codingToken);

        if (!code2.isEmpty()) {
            TokenParam codingToken2 = new TokenParam();
            codingToken2.setValue(code2);
            code.addOr(codingToken2);
        }

        DateRangeParam dateRange = new DateRangeParam();
        dateRange.setLowerBound(startDate);
        dateRange.setUpperBound(endDate);

        Include include = new Include(includeValue);

        SearchParameterMap paramMap = new SearchParameterMap();
        paramMap.add(Observation.SP_CODE, code);
        paramMap.add(Observation.SP_DATE, dateRange);
        paramMap.addInclude(include);

        IBundleProvider bundle = observationResourceProvider.getDao().search(paramMap);
        return getBundle(bundle);
    }

    private Bundle getBundle(IBundleProvider results) {
        Bundle searchBundle = new Bundle();
        searchBundle.setType(Bundle.BundleType.SEARCHSET);
        for (IBaseResource resource : results.getResources(0, results.size())) {
            Bundle.BundleEntryComponent component = searchBundle.addEntry();
            component.setResource((Resource) resource);
        }
        return searchBundle;
    }
}

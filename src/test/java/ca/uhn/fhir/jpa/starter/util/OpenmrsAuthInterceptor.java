package ca.uhn.fhir.jpa.starter.util;

import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenmrsAuthInterceptor extends BasicAuthInterceptor {

	public OpenmrsAuthInterceptor(@Value("${openmrs.login.username}") String username,
			@Value("${openmrs.login.password}") String password) {
		super(username, password);
	}
}

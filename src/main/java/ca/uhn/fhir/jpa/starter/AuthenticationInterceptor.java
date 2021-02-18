package ca.uhn.fhir.jpa.starter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@Interceptor
@Component
public class AuthenticationInterceptor {

    @Value("${openmrs.login.username}")
    private String username;

    @Value("${openmrs.login.password}")
    private String password;

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public boolean incomingRequestPostProcessed(HttpServletRequest theRequest, HttpServletResponse theResponse)
            throws AuthenticationException, IOException {

        String authHeader = theRequest.getHeader("Authorization");

        if (!(theRequest.getRequestURI().contains("/.well-known")
                || theRequest.getRequestURI().endsWith("/metadata"))) {

            // The format of the header must be:
            // Authorization: Basic [base64 of username:password]
            if (StringUtils.isBlank(authHeader)) {
                throw new AuthenticationException("Invalid username or password");
            }

            if (!authHeader.startsWith("Basic")) {
                throw new AuthenticationException("Invalid username or password");
            }

            String base64 = authHeader.substring(6);
            String base64decoded = new String(Base64.decodeBase64(base64), StandardCharsets.UTF_8);
            String[] parts = base64decoded.split(":");

            String username = parts[0];
            String password = parts[1];
            
            // Here we test for a hardcoded username & password use to get access to the
            // hapi fhir JPA sever..
            if (!username.equalsIgnoreCase(this.username) || !password.equals(this.password)) {
                throw new AuthenticationException("Invalid username or password");
            }
        }

        // Return true to allow the request to proceed
        return true;
    }
}

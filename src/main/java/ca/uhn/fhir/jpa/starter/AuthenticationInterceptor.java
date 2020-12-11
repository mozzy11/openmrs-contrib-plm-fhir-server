package ca.uhn.fhir.jpa.starter;

import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;

@Interceptor
public class AuthenticationInterceptor {
	
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public boolean incomingRequestPostProcessed(HttpServletRequest theRequest,
            HttpServletResponse theResponse) throws AuthenticationException {

        String authHeader = theRequest.getHeader("Authorization");

        if (!(theRequest.getRequestURI().contains("/.well-known")
                || theRequest.getRequestURI().endsWith("/metadata"))) {

            // The format of the header must be:
            // Authorization: Basic [base64 of username:password]
            if (StringUtils.isBlank(authHeader)) {
                throw new AuthenticationException("Missing Authorization header");
            }

            if (!authHeader.startsWith("Basic")) {
                throw new AuthenticationException("Invalid Authorization header.Missing Basic prefix");
            }
            String base64 = authHeader.substring(6);
            String base64decoded = new String(Base64.decodeBase64(base64), StandardCharsets.UTF_8);
            String[] parts = base64decoded.split(":");

            String username = parts[0].toLowerCase();
            String password = parts[1];
            
            
            // Here we test for a hardcoded username & password use to get access to the
            // hapi fhir JPA sever..
            if (!username.equals("hapi") || !password.equals("hapi123")) {
                throw new AuthenticationException("Invalid username or password");
            }
        }

        // Return true to allow the request to proceed
        return true;
    }
}

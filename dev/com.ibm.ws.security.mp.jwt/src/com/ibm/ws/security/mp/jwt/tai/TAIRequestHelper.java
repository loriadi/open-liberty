/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.security.mp.jwt.tai;

import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.security.mp.jwt.MicroProfileJwtConfig;
import com.ibm.ws.security.mp.jwt.TraceConstants;
import com.ibm.ws.security.mp.jwt.error.MpJwtProcessingException;
import com.ibm.ws.security.mp.jwt.impl.utils.ClientConstants;
import com.ibm.ws.security.mp.jwt.impl.utils.MicroProfileJwtTaiRequest;

/**
 *
 */
public class TAIRequestHelper {

    private static TraceComponent tc = Tr.register(TAIRequestHelper.class, TraceConstants.TRACE_GROUP, TraceConstants.MESSAGE_BUNDLE);

    public static final String ATTRIBUTE_TAI_REQUEST = "MPJwtTaiRequest";

    private static final String Authorization_Header = "Authorization";
    private static final String APPLICATION_AUTH_METHOD = "com.ibm.ws.security.tai.appAuthType";
    public final static String REQ_METHOD_POST = "POST";
    public final static String REQ_CONTENT_TYPE_NAME = "Content-Type";
    public final static String REQ_CONTENT_TYPE_APP_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String AUTHN_TYPE = "MP-JWT";

    /**
     * Creates a new {@link MicroProfileJwtTaiRequest} object and sets the object as an attribute in the request object provided.
     *
     * @param request
     * @return
     */
    public MicroProfileJwtTaiRequest createMicroProfileJwtTaiRequestAndSetRequestAttribute(HttpServletRequest request) {
        String methodName = "createMicroProfileJwtTaiRequestAndSetRequestAttribute";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request);
        }
        MicroProfileJwtTaiRequest mpJwtTaiRequest = new MicroProfileJwtTaiRequest(request);
        request.setAttribute(ATTRIBUTE_TAI_REQUEST, mpJwtTaiRequest);
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, mpJwtTaiRequest);
        }
        return mpJwtTaiRequest;
    }

    /**
     * Returns whether the provided request should be handled by the microprofile jwt TAI, based on the request path and
     * information
     * in the {@link McroProfileJwtTaiRequest} object provided.
     *
     * @param request
     * @param mpJwtTaiRequest
     * @return
     */
    public boolean requestShouldBeHandledByTAI(HttpServletRequest request, MicroProfileJwtTaiRequest mpJwtTaiRequest) {
        String methodName = "requestShouldBeHandledByTAI";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request, mpJwtTaiRequest);
        }

        String loginHint = getLoginHint(request);
        mpJwtTaiRequest = setTaiRequestConfigInfo(request, loginHint, mpJwtTaiRequest);
        boolean result = false;
        boolean ignoreAppAuthMethod = true;

        MicroProfileJwtConfig mpJwtConfig = null;
        try {
            mpJwtConfig = mpJwtTaiRequest.getOnlyMatchingConfig();
        } catch (MpJwtProcessingException e) {

        }
        if (mpJwtConfig != null) {
            ignoreAppAuthMethod = mpJwtConfig.ignoreApplicationAuthMethod(); // true by default
        }
        if (ignoreAppAuthMethod) {
            result = mpJwtTaiRequest.hasServices();
        } else {
            result = isMpJwtSpecifiedInLoginConfig(request);
        }

        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, result);
        }
        return result;
    }

    /**
     * @param request
     */
    private boolean isMpJwtSpecifiedInLoginConfig(HttpServletRequest request) {

        if (request.getAttribute(APPLICATION_AUTH_METHOD) != null) {
            String loginCfg = (String) request.getAttribute(APPLICATION_AUTH_METHOD);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Auth method = ", loginCfg);
                Tr.debug(tc, "isMpJwtSpecifiedInLoginConfig ", AUTHN_TYPE.equals(loginCfg));
            }
            if (!AUTHN_TYPE.equals(loginCfg)) {
                String msg = Tr.formatMessage(tc, "MPJWT_NOT_FOUND_IN_APPLICATION", new Object[] { AUTHN_TYPE, loginCfg, "ignoreApplicationAuthMethod", "false" });
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "isMpJwtSpecifiedInLoginConfig ", msg);
                }
            }
            return (AUTHN_TYPE.equals(loginCfg));
        }
        String msg = Tr.formatMessage(tc, "MPJWT_NOT_FOUND_IN_APPLICATION", new Object[] { AUTHN_TYPE, "null", "ignoreApplicationAuthMethod", "false" });
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "isMpJwtSpecifiedInLoginConfig ", msg);
        }
        return false;

    }

    String getLoginHint(HttpServletRequest request) {
        String methodName = "getLoginHint";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request);
        }
        String specifiedServiceId = getLoginHintFromHeaderOrParameter(request);
        if (specifiedServiceId == null || specifiedServiceId.isEmpty()) {
            // The request did not contain a login hint
            specifiedServiceId = null;
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, specifiedServiceId);
        }
        return specifiedServiceId;
    }

    String getLoginHintFromHeaderOrParameter(HttpServletRequest request) {
        String methodName = "getLoginHintFromHeaderOrParameter";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request);
        }
        String specifiedServiceId = request.getHeader(ClientConstants.LOGIN_HINT);
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "specifiedService(h) id:" + specifiedServiceId);
        }
        if (specifiedServiceId == null || specifiedServiceId.isEmpty()) {
            specifiedServiceId = request.getParameter(ClientConstants.LOGIN_HINT);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "specifiedService(p) id:" + specifiedServiceId);
            }
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, specifiedServiceId);
        }
        return specifiedServiceId;
    }

    public String getBearerToken(HttpServletRequest req, MicroProfileJwtConfig clientConfig) {
        String methodName = "getBearerToken";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, req, clientConfig);
        }
        String token = getBearerTokenFromHeader(req);
        if (token == null) {
            token = getBearerTokenFromParameter(req);
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, token);
        }
        return token;
    }

    String getBearerTokenFromHeader(HttpServletRequest req) {
        String methodName = "getBearerTokenFromHeader";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, req);
        }
        String hdrValue = req.getHeader(Authorization_Header);
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "Authorization header=", hdrValue);
        }
        String bearerAuthzMethod = "Bearer ";
        if (hdrValue != null && hdrValue.startsWith(bearerAuthzMethod)) {
            hdrValue = hdrValue.substring(bearerAuthzMethod.length());
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, hdrValue);
        }
        return hdrValue;
    }

    String getBearerTokenFromParameter(HttpServletRequest req) {
        String methodName = "getBearerTokenFromParameter";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, req);
        }
        String param = null;
        String reqMethod = req.getMethod();
        if (REQ_METHOD_POST.equalsIgnoreCase(reqMethod)) {
            String contentType = req.getHeader(REQ_CONTENT_TYPE_NAME);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Request content type: " + contentType);
            }
            if (REQ_CONTENT_TYPE_APP_FORM_URLENCODED.equals(contentType)) {
                param = req.getParameter(ACCESS_TOKEN);
            }
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, param);
        }
        return param;
    }

    MicroProfileJwtTaiRequest setTaiRequestConfigInfo(HttpServletRequest request, String specifiedServiceId, MicroProfileJwtTaiRequest mpJwtTaiRequest) {
        String methodName = "setTaiRequestConfigInfo";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request, specifiedServiceId, mpJwtTaiRequest);
        }
        if (specifiedServiceId == null) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Specific config ID not provided, so will set generic config information for MpJwtTaiRequest object");
            }
            MicroProfileJwtTaiRequest result = setGenericAndFilteredConfigTaiRequestInfo(request, mpJwtTaiRequest);
            if (tc.isDebugEnabled()) {
                Tr.exit(tc, methodName, result);
            }
            return result;
        }
        MicroProfileJwtTaiRequest result = setSpecificConfigTaiRequestInfo(request, specifiedServiceId, mpJwtTaiRequest);
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, result);
        }
        return result;
    }

    MicroProfileJwtTaiRequest setGenericAndFilteredConfigTaiRequestInfo(HttpServletRequest request, MicroProfileJwtTaiRequest mpJwtTaiRequest) {
        String methodName = "setGenericAndFilteredConfigTaiRequestInfo";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request, mpJwtTaiRequest);
        }
        if (mpJwtTaiRequest == null) {
            mpJwtTaiRequest = createMicroProfileJwtTaiRequestAndSetRequestAttribute(request);
        }
        Iterator<MicroProfileJwtConfig> services = getConfigServices();
        MicroProfileJwtTaiRequest result = setGenericAndFilteredConfigTaiRequestInfoFromConfigServices(request, mpJwtTaiRequest, services);
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, result);
        }
        return result;
    }

    MicroProfileJwtTaiRequest setGenericAndFilteredConfigTaiRequestInfoFromConfigServices(HttpServletRequest request, MicroProfileJwtTaiRequest mpJwtTaiRequest, Iterator<MicroProfileJwtConfig> services) {
        String methodName = "setGenericAndFilteredConfigTaiRequestInfoFromConfigServices";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request, mpJwtTaiRequest, services);
        }
        if (services == null) {
            if (tc.isDebugEnabled()) {
                Tr.exit(tc, methodName, mpJwtTaiRequest);
            }
            return mpJwtTaiRequest;
        }
        if (mpJwtTaiRequest == null) {
            mpJwtTaiRequest = createMicroProfileJwtTaiRequestAndSetRequestAttribute(request);
        }

        while (services.hasNext()) {
            MicroProfileJwtConfig mpJwtConfig = services.next();
            //            AuthenticationFilter authFilter = mpJwtConfig.getAuthFilter();
            //            if (authFilter != null) {
            //                if (authFilter.isAccepted(request)) {
            //                    mpJwtTaiRequest.addFilteredConfig(mpJwtConfig);
            //                }
            //            } else {
            mpJwtTaiRequest.addGenericConfig(mpJwtConfig);
            //            }
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, mpJwtTaiRequest);
        }
        return mpJwtTaiRequest;
    }

    MicroProfileJwtTaiRequest setSpecificConfigTaiRequestInfo(HttpServletRequest request, String configId, MicroProfileJwtTaiRequest mpJwtTaiRequest) {
        String methodName = "setSpecificConfigTaiRequestInfo";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request, configId, mpJwtTaiRequest);
        }
        if (mpJwtTaiRequest == null) {
            mpJwtTaiRequest = createMicroProfileJwtTaiRequestAndSetRequestAttribute(request);
        }

        MicroProfileJwtConfig config = getConfigAssociatedWithRequestAndId(request, configId);
        if (config == null) {
            mpJwtTaiRequest = handleNoMatchingConfiguration(configId, mpJwtTaiRequest);
        } else {
            mpJwtTaiRequest.setSpecifiedConfig(config);
        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, mpJwtTaiRequest);
        }
        return mpJwtTaiRequest;
    }

    MicroProfileJwtTaiRequest handleNoMatchingConfiguration(String configId, MicroProfileJwtTaiRequest mpJwtTaiRequest) {
        String msg = Tr.formatMessage(tc, "MPJWT_NO_SUCH_PROVIDER", new Object[] { configId });
        Tr.error(tc, msg);
        MpJwtProcessingException mpjwtException = new MpJwtProcessingException(msg);
        mpJwtTaiRequest.setTaiException(mpjwtException);
        return mpJwtTaiRequest;
    }

    MicroProfileJwtConfig getConfigAssociatedWithRequestAndId(HttpServletRequest request, String configId) {
        String methodName = "getConfigAssociatedWithRequestAndId";
        if (tc.isDebugEnabled()) {
            Tr.entry(tc, methodName, request, configId);
        }
        MicroProfileJwtConfig mpJwtConfig = getConfig(configId);
        //        if (!configAuthFilterMatchesRequest(request, mpJwtConfig)) {
        //            // The config with the specified ID isn't configured to service this request
        //            mpJwtConfig = null;
        //        }
        if (tc.isDebugEnabled()) {
            Tr.exit(tc, methodName, mpJwtConfig);
        }
        return mpJwtConfig;
    }

    Iterator<MicroProfileJwtConfig> getConfigServices() {
        return MicroProfileJwtTAI.getServices();
    }

    MicroProfileJwtConfig getConfig(String configId) {
        return MicroProfileJwtTAI.getMicroProfileJwtConfig(configId);
    }

    //    boolean configAuthFilterMatchesRequest(HttpServletRequest request, MicroProfileJwtConfig config) {
    //        if (config == null) {
    //            return false;
    //        }
    //        AuthenticationFilter authFilter = config.getAuthFilter();
    //        if (authFilter != null) {
    //            if (!authFilter.isAccepted(request)) {
    //                // Specified configuration is present but its auth filter is not configured to service this request
    //                return false;
    //            }
    //        }
    //        return true;
    //    }

}

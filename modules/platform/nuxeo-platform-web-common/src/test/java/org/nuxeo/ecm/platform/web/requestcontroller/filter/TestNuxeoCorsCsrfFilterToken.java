/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.web.requestcontroller.filter;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.nuxeo.ecm.platform.web.common.MockHttpServletRequest;
import org.nuxeo.ecm.platform.web.common.MockHttpServletResponse;
import org.nuxeo.runtime.test.runner.Deploy;

/**
 * CSRF Token tests.
 *
 * @since 10.3
 */
@Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-csrf-token-config.xml")
public class TestNuxeoCorsCsrfFilterToken extends TestNuxeoCorsCsrfFilter {

    protected static final String CSRF_TOKEN_ATTRIBUTE = "NuxeoCSRFToken";

    protected static final String CSRF_TOKEN_HEADER = "CSRF-Token";

    protected static final String CSRF_TOKEN_FETCH = "fetch";

    protected static final String CSRF_TOKEN_INVALID = "invalid";

    protected static final String CSRF_TOKEN_PARAM = "csrf-token";

    @Override
    protected void maybeSetupToken(MockHttpServletRequest requestHandler) {
        String token = "realtoken";
        requestHandler.whenGetSessionAttributeThenReturn(CSRF_TOKEN_ATTRIBUTE, token)
                      .whenGetHeaderThenReturn(CSRF_TOKEN_HEADER, token);
    }

    /**
     * Browser sending a header "CSRF-Token: fetch".
     */
    @Test
    public void testCSRFTokenAcquire() throws Exception {
        var requestHandler = buildRequestHandler("GET").whenGetHeaderThenReturn(CSRF_TOKEN_HEADER, CSRF_TOKEN_FETCH);
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(requestHandler.mock(), response, chain);
        // chain not called
        assertFalse(chain.called);
        // but a token was created in session
        String token = requestHandler.getSessionAttributeValue(CSRF_TOKEN_ATTRIBUTE);
        assertNotNull(token);
        // and we have a response
        verify(response).setStatus(eq(SC_OK));
        verify(response).setHeader(CSRF_TOKEN_HEADER, token);
    }

    /**
     * Browser sending no token.
     */
    @Test
    public void testCSRFTokenMissing() throws Exception {
        doTestCSRFTokenInvalid(null, null);
    }

    /**
     * Browser sending no token when a real one exists in the session.
     */
    @Test
    public void testCSRFTokenMissingButExistsInSession() throws Exception {
        doTestCSRFTokenInvalid("realtoken", null);
    }

    /**
     * Browser sending an invalid token when there is none in the session.
     */
    @Test
    public void testCSRFTokenInvalid() throws Exception {
        doTestCSRFTokenInvalid(null, "badtoken");
    }

    /**
     * Browser sending an invalid token when a real one exists in the session.
     */
    @Test
    public void testCSRFTokenInvalidButExistsInSession() throws Exception {
        doTestCSRFTokenInvalid("realtoken", "badtoken");
    }

    @SuppressWarnings("boxing")
    protected void doTestCSRFTokenInvalid(String token, String requestToken) throws Exception {
        var requestHandler = this.buildRequestHandler("POST", "/site/something")
                                 .whenGetHeaderThenReturn(CSRF_TOKEN_HEADER, requestToken)
                                 .whenGetSessionAttributeThenReturn(CSRF_TOKEN_ATTRIBUTE, token);
        var responseHandler = MockHttpServletResponse.init();

        filter.doFilter(requestHandler.mock(), responseHandler.mock(), chain);
        // chain not called
        assertFalse(chain.called);
        // no new token was created in session
        if (token == null) {
            assertNull(requestHandler.getSessionAttributeValue(CSRF_TOKEN_ATTRIBUTE));
        }
        // and we have an error status
        var error = responseHandler.getError();
        assertEquals(SC_FORBIDDEN, error.code()); // 403
        assertEquals("CSRF check failure", error.message());
        // and a header saying this is due to invalid CSRF token
        verify(responseHandler.mock()).setHeader(CSRF_TOKEN_HEADER, CSRF_TOKEN_INVALID);
    }

    /**
     * Some endpoints can be configured to allow POST without a CSRF token. This is needed for SAML.
     */
    @Test
    public void testCSRFTokenMissingOnAllowedEndpoint() throws Exception {
        var requestHandler = buildRequestHandler("POST", "/mysaml/mylogin");
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(requestHandler.mock(), response, chain);
        // chain called
        assertTrue(chain.called);
        // no new token was created in session
        assertNull(requestHandler.getSessionAttributeValue(CSRF_TOKEN_ATTRIBUTE));
    }

}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.http.HttpHeaders.NUXEO_VIRTUAL_HOST;
import static org.nuxeo.common.http.HttpHeaders.ORIGIN;
import static org.nuxeo.common.http.HttpHeaders.REFERER;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_HOST;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_PORT;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_PROTO;

import java.util.List;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpTrace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.web.common.MockHttpServletRequest;
import org.nuxeo.ecm.platform.web.common.MockHttpServletResponse;
import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoCorsCsrfFilter;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.ConsoleLogLevelThreshold;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogFeature;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, MockitoFeature.class, LogFeature.class, LogCaptureFeature.class })
@LogCaptureFeature.FilterOn(logLevel = "WARN", loggerClass = NuxeoCorsCsrfFilter.class)
@ConsoleLogLevelThreshold("ERROR")
@Deploy("org.nuxeo.ecm.platform.web.common:OSGI-INF/web-request-controller-framework.xml")
@Deploy("org.nuxeo.ecm.platform.web.common:OSGI-INF/cors-configuration.xml")
public class TestNuxeoCorsCsrfFilter {

    protected static final String SCHEME = "http";

    protected static final String HOST = "example.com";

    protected static final int PORT = 8080;

    protected static final String URL_BASE = SCHEME + "://" + HOST + ":" + PORT + "/";

    protected static final String CONTEXT = "/nuxeo";

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    protected NuxeoCorsCsrfFilter filter;

    protected DummyFilterChain chain;

    protected static class DummyFilterChain implements FilterChain {

        protected boolean called;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            called = true;
        }
    }

    @Before
    public void setUp() throws Exception {
        filter = new NuxeoCorsCsrfFilter();
        filter.init(null);
        chain = new DummyFilterChain();
    }

    @After
    public void tearDown() {
        filter.destroy();
    }

    protected MockHttpServletRequest buildRequestHandler(String method) {
        return buildRequestHandler(method, null);
    }

    protected MockHttpServletRequest buildRequestHandler(String method, String servletPath) {
        var handler = MockHttpServletRequest.builder().method(method).scheme(SCHEME).host(HOST).port(PORT);
        handler.contextPath(CONTEXT);
        if (servletPath != null) {
            handler.servletPath(servletPath);
        }
        return handler.build();
    }

    protected void maybeSetupToken(MockHttpServletRequest requestHandler) {
        // overridden in token-checking subclass
    }

    /**
     * User agent sending no Origin nor Referer header.
     */
    @Test
    public void testNoOriginNorReferer() throws Exception {
        var request = buildRequestHandler("GET").whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE).mock();
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(request, response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending the Origin header, no proxy.
     */
    @Test
    public void testMatchOrigin() throws Exception {
        var request = buildRequestHandler("GET").whenGetHeaderThenReturn(ORIGIN, URL_BASE).mock();
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(request, response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending the Origin header, proxy configured with Nuxeo-Virtual-Host header.
     */
    @Test
    public void testMatchOriginWithVirtualHost() throws Exception {
        var request = buildRequestHandler("GET").whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                                .whenGetHeaderThenReturn(ORIGIN, URL_BASE)
                                                .mock();
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(request, response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending the Origin header, proxy configured with X-Forwarded headers.
     */
    @Test
    public void testMatchOriginWithForwardedHeaders() throws Exception {
        var request = buildRequestHandler("GET").whenGetHeaderThenReturn(ORIGIN, "https://nicesite.example.com")
                                                .whenGetHeaderThenReturn(X_FORWARDED_HOST, "nicesite.example.com")
                                                .whenGetHeaderThenReturn(X_FORWARDED_PORT, "443")
                                                .whenGetHeaderThenReturn(X_FORWARDED_PROTO, "https")
                                                .mock();
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(request, response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending the Referer header.
     */
    @Test
    public void testMatchReferer() throws Exception {
        var request = buildRequestHandler("GET").whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                                .whenGetHeaderThenReturn(REFERER, URL_BASE + "nuxeo/somepage.html")
                                                .mock();
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(request, response, chain);
        assertTrue(chain.called);
    }

    @Test
    public void testMismatchButGet() throws Exception {
        doTestMismatchButNonStateChangingMethod(HttpGet.METHOD_NAME);
    }

    @Test
    public void testMismatchButHead() throws Exception {
        doTestMismatchButNonStateChangingMethod(HttpHead.METHOD_NAME);
    }

    @Test
    public void testMismatchButOptions() throws Exception {
        doTestMismatchButNonStateChangingMethod(HttpOptions.METHOD_NAME);
    }

    @Test
    public void testMismatchButTrace() throws Exception {
        doTestMismatchButNonStateChangingMethod(HttpTrace.METHOD_NAME);
    }

    /**
     * Browser sending the Referer header from an external page with a non-state-changing method.
     */
    public void doTestMismatchButNonStateChangingMethod(String method) throws Exception {
        var request = buildRequestHandler(method).whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                                 .whenGetHeaderThenReturn(REFERER, "http://google.com/")
                                                 .mock();
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(request, response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending an Origin header with a whitelisted browser-specific scheme.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-cors-config.xml")
    public void testOriginFromBrowserExtension() throws Exception {
        var requestHandler = this.buildRequestHandler("POST", "/site/something")
                                 .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                 .whenGetHeaderThenReturn(ORIGIN,
                                         "moz-extension://12345678-1234-1234-1234-1234567890ab");
        maybeSetupToken(requestHandler);
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(requestHandler.mock(), response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending the Origin header from another page which is allowed by CORS.
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-cors-config.xml")
    public void testMismatchPostButAllowedByCORS() throws Exception {
        var requestHandler = this.buildRequestHandler("POST", "/site/something")
                                 .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                 .whenGetHeaderThenReturn(ORIGIN, "http://friendly.com");
        maybeSetupToken(requestHandler);
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(requestHandler.mock(), response, chain);
        assertTrue(chain.called);
    }

    /**
     * Buggy browser (Edge/IE11) not sending a Origin header but just a Referer header (which can include path and query
     * parts) when redirecting to a POST on the site (SAML login use case).
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-cors-config.xml")
    public void testMismatchPostFromBuggyBrowser() throws Exception {
        var requestHandler = this.buildRequestHandler("POST", "/site/something")
                                 .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                 .whenGetHeaderThenReturn(REFERER, "http://friendly.com/myapp/login?key=123"); // SSO
        maybeSetupToken(requestHandler);
        var response = MockHttpServletResponse.init().mock();

        filter.doFilter(requestHandler.mock(), response, chain);
        assertTrue(chain.called);
    }

    /**
     * Browser sending the Origin header from an attacker page, must fail.
     */
    @Test
    public void testMismatchPost() throws Exception {
        doTestMismatchPost("http://attacker.com", false);
    }

    @Test
    public void testMismatchPostNullOriginDefault() throws Exception {
        doTestMismatchPostNullOrigin(false);
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-cors-null-origin-forbidden.xml")
    public void testMismatchPostNullOriginForbidden() throws Exception {
        doTestMismatchPostNullOrigin(false);
    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.web.common.test:OSGI-INF/test-cors-null-origin-allowed.xml")
    public void testMismatchPostNullOriginAllowed() throws Exception {
        doTestMismatchPostNullOrigin(true);
    }

    /**
     * Browser sending the Origin header from a local filesystem page, must fail.
     * <p>
     * Per RFC 6454, 7.3: Whenever a user agent issues an HTTP request from a "privacy-sensitive" context, the user
     * agent MUST send the value "null" in the Origin header field.
     */
    protected void doTestMismatchPostNullOrigin(boolean allowNullOrigin) throws Exception {
        doTestMismatchPost("null", allowNullOrigin);
    }

    @SuppressWarnings("boxing")
    protected void doTestMismatchPost(String origin, boolean allowNullOrigin) throws Exception {
        var requestHandler = this.buildRequestHandler("POST", "/site/something")
                                 .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, URL_BASE)
                                 .whenGetHeaderThenReturn(ORIGIN, origin);
        maybeSetupToken(requestHandler);
        var responseHandler = MockHttpServletResponse.init();

        filter.doFilter(requestHandler.mock(), responseHandler.mock(), chain);

        if ("null".equals(origin) && allowNullOrigin) {
            assertTrue(chain.called);
        } else {
            assertFalse(chain.called);
            var error = responseHandler.getError();
            assertEquals(HttpServletResponse.SC_FORBIDDEN, error.code()); // 403
            assertEquals("CSRF check failure", error.message());

            List<String> events = logCaptureResult.getCaughtEventMessages();
            assertFalse("Expected WARN", events.isEmpty());
            String warn = events.get(events.size() - 1);
            String originInMessage = origin.equals("null") ? "privacy-sensitive:///" : origin;
            assertEquals("CSRF check failure: source: " + originInMessage + " does not match target: " + URL_BASE
                    + " and not allowed by CORS config", warn);
        }
    }

}

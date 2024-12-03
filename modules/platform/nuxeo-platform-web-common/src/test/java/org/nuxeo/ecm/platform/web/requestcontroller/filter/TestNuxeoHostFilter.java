/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.nuxeo.ecm.platform.web.requestcontroller.filter;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.http.HttpHeaders.NUXEO_VIRTUAL_HOST;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_HOST;
import static org.nuxeo.launcher.config.ConfigurationConstants.PARAM_NUXEO_ALLOWED_HOSTS;
import static org.nuxeo.launcher.config.ConfigurationConstants.PARAM_NUXEO_URL;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.web.common.MockHttpServletRequest;
import org.nuxeo.ecm.platform.web.common.MockHttpServletResponse;
import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoHostFilter;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@WithFrameworkProperty(name = PARAM_NUXEO_URL, value = "http://localhost:8080/nuxeo")
public class TestNuxeoHostFilter {

    protected static final String MY_HOST_ORG = "myhost.org";

    protected static final String REQUEST_URI = "http://" + MY_HOST_ORG;

    protected NuxeoHostFilter filter;

    protected IndicatorFilter finisher;

    protected static class IndicatorFilter implements FilterChain {

        protected boolean called;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called = true;
        }
    }

    @Before
    public void setUp() throws ServletException {
        filter = new NuxeoHostFilter();
        filter.init(null);
        finisher = new IndicatorFilter();
    }

    @Test
    public void testNuxeoUrlHostFilteringDisabledByDefault() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", REQUEST_URI).mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", REQUEST_URI).mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG)
    public void testAllowedHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", REQUEST_URI).mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG)
    public void testNuxeoUrlAlwaysAllowed() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://localhost").mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG + ",oh" + MY_HOST_ORG)
    public void testMultipleAllowedHostFilteringFirst() throws IOException, ServletException { // NOSONAR
        var request = MockHttpServletRequest.init("GET", REQUEST_URI).mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG + ",oh" + MY_HOST_ORG)
    public void testMultipleAllowedHostFilteringSecond() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://oh" + MY_HOST_ORG).mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = MY_HOST_ORG + ",oh" + MY_HOST_ORG)
    public void testDeniedHostAgainstMultipleHostFiltering() throws ServletException, IOException {
        var request = MockHttpServletRequest.init("GET", "http://h" + MY_HOST_ORG).mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedSubdomainHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://not.localhost").mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedSuffixHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://localhost.attacker.org").mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedForwardedHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://localhost")
                                            .whenGetHeaderThenReturn(X_FORWARDED_HOST, "not.localhost")
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testWellformedVirtualHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://localhost")
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, "http://localhost/")
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testMalformedVirtualHostFiltering() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, "http://localhost/^")
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        var t = assertThrows(NuxeoException.class, () -> filter.doFilter(request, response, finisher));
        assertEquals("java.net.URISyntaxException: Illegal character in path at index 17: http://localhost/^",
                t.getMessage());
        assertEquals(SC_BAD_REQUEST, t.getStatusCode());
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testRejectNullResultingVirtualHostFiltering() {
        var request = MockHttpServletRequest.init("GET", "http://localhost")
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST,
                                                    "rejectable.null.resulting.host")
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        var t = assertThrows(NuxeoException.class, () -> filter.doFilter(request, response, finisher));
        assertEquals(
                "Rejecting null resulting host of url: rejectable.null.resulting.host from nuxeo-virtual-host header",
                t.getMessage());
        assertEquals(SC_BAD_REQUEST, t.getStatusCode());
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testDeniedNuxeoVirtualHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://localhost")
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, "http://not.localhost/")
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertFalse(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testMultipleHeadersAllowedHostFiltering() throws IOException, ServletException {
        var request = MockHttpServletRequest.init("GET", "http://localhost")
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, "http://localhost:8080/")
                                            .whenGetHeaderThenReturn(X_FORWARDED_HOST, "localhost")
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        filter.doFilter(request, response, finisher);
        assertTrue(finisher.called);
    }

    @Test
    @WithFrameworkProperty(name = PARAM_NUXEO_ALLOWED_HOSTS, value = "localhost")
    public void testMultipleHeadersMixedHostFiltering() {
        var request = MockHttpServletRequest.init("GET", "http://localhost")
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST, "localhost")
                                            .whenGetHeaderThenReturn(X_FORWARDED_HOST, MY_HOST_ORG)
                                            .mock();
        var response = MockHttpServletResponse.init().mock();
        var t = assertThrows(NuxeoException.class, () -> filter.doFilter(request, response, finisher));
        assertEquals("Rejecting null resulting host of url: localhost from nuxeo-virtual-host header", t.getMessage());
        assertEquals(SC_BAD_REQUEST, t.getStatusCode());
        assertFalse(finisher.called);
    }
}

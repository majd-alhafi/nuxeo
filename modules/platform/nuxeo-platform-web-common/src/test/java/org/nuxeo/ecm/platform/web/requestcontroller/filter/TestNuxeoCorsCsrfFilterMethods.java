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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.nuxeo.common.http.HttpHeaders.NUXEO_VIRTUAL_HOST;
import static org.nuxeo.common.http.HttpHeaders.ORIGIN;
import static org.nuxeo.common.http.HttpHeaders.REFERER;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_HOST;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_PORT;
import static org.nuxeo.common.http.HttpHeaders.X_FORWARDED_PROTO;
import static org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoCorsCsrfFilter.PRIVACY_SENSITIVE;

import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.web.common.MockHttpServletRequest;
import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoCorsCsrfFilter;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class TestNuxeoCorsCsrfFilterMethods {

    protected NuxeoCorsCsrfFilter filter;

    @Before
    public void setUp() {
        filter = new NuxeoCorsCsrfFilter();
    }

    @Test
    public void testSourceURIOrigin() {
        var request = MockHttpServletRequest.init().whenGetHeaderThenReturn(ORIGIN, "http://example.com:8080").mock();
        assertEquals("http://example.com:8080", filter.getSourceURI(request).toASCIIString());
    }

    @Test
    public void testSourceURIOriginList() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(ORIGIN, "http://example.com:8080 http://other.com")
                                            .mock();
        assertEquals("http://example.com:8080", filter.getSourceURI(request).toASCIIString());
    }

    @Test
    public void testSourceURIOriginNullDefault() {
        var request = MockHttpServletRequest.init().whenGetHeaderThenReturn(ORIGIN, "null").mock();
        URI uri = filter.getSourceURI(request);
        assertNotNull(uri);
        assertEquals("privacy-sensitive:///", uri.toASCIIString());
    }

    @Test
    public void testSourceURIReferer() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(REFERER, "http://example.com:8080/nuxeo")
                                            .mock();
        assertEquals("http://example.com:8080/nuxeo", filter.getSourceURI(request).toASCIIString());
    }

    @SuppressWarnings("boxing")
    @Test
    public void testTargetURI() {
        var request = MockHttpServletRequest.init("GET", "http://example.com:8080").mock();
        assertEquals("http://example.com:8080/", filter.getTargetURI(request).toASCIIString());
    }

    @Test
    public void testTargetURINuxeoVirtualHostHeader() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(NUXEO_VIRTUAL_HOST,
                                                    "http://example.com:8080/nuxeo/")
                                            .mock();
        when(request.getHeader(eq(NUXEO_VIRTUAL_HOST))).thenReturn("http://example.com:8080/nuxeo/");
        assertEquals("http://example.com:8080/nuxeo/", filter.getTargetURI(request).toASCIIString());
    }

    @Test
    public void testTargetURIForwardedHeaders() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(X_FORWARDED_PROTO, "http")
                                            .whenGetHeaderThenReturn(X_FORWARDED_HOST, "example.com")
                                            .whenGetHeaderThenReturn(X_FORWARDED_PORT, "80")
                                            .mock();
        assertEquals("http://example.com/", filter.getTargetURI(request).toASCIIString());
    }

    @Test
    public void testTargetURIForwardedHeadersHttps() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(X_FORWARDED_PROTO, "https")
                                            .whenGetHeaderThenReturn(X_FORWARDED_HOST, "example.com")
                                            .whenGetHeaderThenReturn(X_FORWARDED_PORT, "443")
                                            .mock();
        assertEquals("https://example.com/", filter.getTargetURI(request).toASCIIString());
    }

    @Test
    public void testTargetURIForwardedHeadersCustomPort() {
        var request = MockHttpServletRequest.init()
                                            .whenGetHeaderThenReturn(X_FORWARDED_PROTO, "http")
                                            .whenGetHeaderThenReturn(X_FORWARDED_HOST, "example.com")
                                            // TODO bug in VHH, ignored
                                            .whenGetHeaderThenReturn(X_FORWARDED_PORT, "8080")
                                            .mock();
        assertEquals("http://example.com/", filter.getTargetURI(request).toASCIIString());
    }

    @Test
    public void testPrivacySensitiveURIDoesNotMatch() {
        assertFalse(filter.sourceAndTargetMatch(PRIVACY_SENSITIVE, URI.create("http://example.com:8080")));
    }

}

/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.web.common.requestcontroller.filter;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.web.common.MockHttpServletRequest;
import org.nuxeo.ecm.platform.web.common.MockHttpServletResponse;
import org.nuxeo.ecm.platform.web.common.requestcontroller.service.RequestControllerManager;
import org.nuxeo.ecm.platform.web.common.requestcontroller.service.RequestFilterConfig;
import org.nuxeo.ecm.platform.web.common.requestcontroller.service.RequestFilterConfigImpl;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.TransactionalConfig;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, TransactionalFeature.class, MockitoFeature.class })
@TransactionalConfig(autoStart = false)
public class TestNuxeoRequestControllerFilter {

    @Mock
    @RuntimeService
    protected RequestControllerManager manager;

    protected NuxeoRequestControllerFilter filter;

    protected DummyFilterChain chain;

    protected Exception chainException;

    public class DummyFilterChain implements FilterChain {

        public boolean hasTransaction;

        @SuppressWarnings("resource")
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            hasTransaction = TransactionHelper.isTransactionActive();
            response.getOutputStream().write("ABC".getBytes());
            try {
                if (chainException != null) {
                    throw chainException;
                }
            } catch (IOException | ServletException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                fail(e.toString());
            }
        }
    }

    @Before
    public void setUp() {
        filter = new NuxeoRequestControllerFilter();
        chain = new DummyFilterChain();
    }

    @Test
    public void testBasics() throws IOException, ServletException {
        var requestHandler = MockHttpServletRequest.init("GET", "http://localhost:8080");
        var responseHandler = MockHttpServletResponse.init();

        RequestFilterConfig filterConfig = new RequestFilterConfigImpl(false, true, true, true, true, "123");
        when(manager.getConfigForRequest(any())).thenReturn(filterConfig);
        Map<String, String> managerHeaders = new HashMap<>();
        managerHeaders.put("MyHeader", "my-header-value");
        when(manager.getResponseHeaders()).thenReturn(managerHeaders);

        filter.doFilter(requestHandler.mock(), responseHandler.mock(), chain);

        var response = responseHandler.mock();
        verify(response, never()).setStatus(anyInt());
        verify(response, never()).sendError(anyInt());
        verify(response, never()).sendError(anyInt(), anyString());

        Map<String, List<String>> expectedResponseHeaders = new HashMap<>();
        expectedResponseHeaders.put("MyHeader", List.of("my-header-value"));
        expectedResponseHeaders.put("Cache-Control", List.of("private, max-age=123"));
        var actualResponseHeaders = new HashMap<>(responseHandler.getHeaders());
        assertNotNull(actualResponseHeaders.remove("Expires"));
        assertEquals(expectedResponseHeaders, actualResponseHeaders);

        assertTrue(chain.hasTransaction);
        assertEquals("ABC", responseHandler.getResponseAsString());
    }

    @Test
    public void testNuxeoException() throws IOException {
        doTestException(new NuxeoException(456), 456);
    }

    @Test
    public void testRuntimeException() throws IOException {
        doTestException(new RuntimeException(), SC_INTERNAL_SERVER_ERROR);
    }

    protected void doTestException(Exception exc, int expectedStatus) throws IOException {
        var requestHandler = MockHttpServletRequest.init("GET", "http://localhost:8080");
        var responseHandler = MockHttpServletResponse.init();

        RequestFilterConfig filterConfig = new RequestFilterConfigImpl(false, true, true, false, false, "");
        when(manager.getConfigForRequest(any())).thenReturn(filterConfig);

        chainException = exc;
        try {
            filter.doFilter(requestHandler.mock(), responseHandler.mock(), chain);
            fail();
        } catch (ServletException e) {
            assertEquals(exc, e.getCause());
        }

        var response = responseHandler.mock();
        verify(response).setStatus(expectedStatus);
        verify(response, never()).sendError(anyInt());
        verify(response, never()).sendError(anyInt(), anyString());

        assertEquals("", responseHandler.getResponseAsString()); // output was suppressed
    }

}

/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.web.common.exceptionhandling;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
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
import org.nuxeo.runtime.test.runner.ConsoleLogLevelThreshold;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogFeature;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class, LogFeature.class, LogCaptureFeature.class })
@Deploy("org.nuxeo.ecm.platform.web.common:OSGI-INF/exception-handling-service.xml")
public class TestNuxeoExceptionFilter {

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    protected NuxeoExceptionFilter filter;

    protected DummyFilterChain chain;

    protected Exception chainException;

    public class DummyFilterChain implements FilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            try {
                throw chainException;
            } catch (IOException | ServletException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                fail(e.toString());
            }
        }
    }

    @Before
    public void setUp() {
        filter = new NuxeoExceptionFilter();
        chain = new DummyFilterChain();
    }

    @Test
    public void testNuxeoException() throws IOException, ServletException {
        doTestException(new NuxeoException("oops", 456), 456, "oops", "oops", null);
    }

    @Test
    public void testNuxeoExceptionAsCause() throws IOException, ServletException {
        doTestException(new ServletException(new NuxeoException("oops", 456)), 456, "oops", "oops", null);
    }

    @Test
    @ConsoleLogLevelThreshold("FATAL")
    @LogCaptureFeature.FilterOn(logLevel = "ERROR")
    public void testRuntimeException() throws IOException, ServletException {
        doTestException(new RuntimeException("oops"), SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "oops",
                "java.lang.RuntimeException: oops");
    }

    @SuppressWarnings("resource")
    protected void doTestException(Exception exc, int expectedStatus, String expectedJsonMessage,
            String expectedMessage, String expectedLog) throws IOException, ServletException {
        var requestHandler = MockHttpServletRequest.init();
        var responseHandler = MockHttpServletResponse.init();

        logCaptureResult.clear();

        chainException = exc;
        filter.doFilter(requestHandler.mock(), responseHandler.mock(), chain);

        assertEquals("{\"entity-type\":\"exception\",\"status\":" + expectedStatus + ",\"message\":\""
                + expectedJsonMessage + "\"}", responseHandler.getResponseAsString());

        List<String> expectedEvents = expectedLog == null ? Collections.emptyList()
                : Collections.singletonList(expectedLog);
        List<String> caughtEvents = logCaptureResult.getCaughtEventMessages();
        assertEquals(expectedEvents, caughtEvents);

        Map<String, Object> expectedRequestAttributes = new HashMap<>();
        expectedRequestAttributes.put("NuxeoExceptionHandlerMarker", true);
        expectedRequestAttributes.put("nuxeo.disable.redirect.wrapper", true);
        expectedRequestAttributes.put("user_message", "Error.Unknown");
        expectedRequestAttributes.put("exception_message", expectedMessage);
        expectedRequestAttributes.put("securityError", false);
        expectedRequestAttributes.put("isDevModeSet", false);
        assertEquals(expectedRequestAttributes, requestHandler.getAttributes());

        var request = requestHandler.mock();
        verify(request, atLeastOnce()).getAttribute(anyString());
        verify(request, atLeastOnce()).setAttribute(anyString(), any());
        verify(request, atLeastOnce()).getHeader(anyString());
        verifyNoMoreInteractions(request);

        var response = responseHandler.mock();
        verify(response).isCommitted();
        verify(response).reset();
        verify(response).setStatus(expectedStatus);
        verify(response).setContentType(anyString());
        verify(response).getWriter();
        verifyNoMoreInteractions(response);
    }

}

/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.ecm.platform.web.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.nuxeo.ecm.core.io.DummyServletOutputStream;

/**
 * @since 2025.0
 */
public class MockHttpServletResponse {

    protected final HttpServletResponse mock;

    protected final Map<String, List<String>> headers;

    protected final ByteArrayOutputStream responseOutputStream;

    protected int status;

    protected List<Cookie> cookies;

    protected MockHttpServletResponse(HttpServletResponse response) {
        mock = response;
        when(mock.getCharacterEncoding()).thenReturn(UTF_8.name());
        // initialize headers
        headers = new HashMap<>();
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            return headers.get(key);
        }).when(mock).containsHeader(anyString());
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            String value = (String) i.getArguments()[1];
            headers.put(key, new ArrayList<>(List.of(value)));
            return null;
        }).when(mock).setHeader(anyString(), any());
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            String value = (String) i.getArguments()[1];
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            return null;
        }).when(mock).addHeader(anyString(), any());
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            List<String> values = headers.get(key);
            return values == null || values.isEmpty() ? null : values.get(0);
        }).when(mock).getHeader(anyString());
        doAnswer(i -> {
            String key = (String) i.getArguments()[0];
            List<String> values = headers.get(key);
            return values == null ? List.of() : values;
        }).when(mock).getHeaders(anyString());
        doAnswer(i -> headers.keySet()).when(mock).getHeaderNames();
        // initialize responseOutputStream
        try {
            responseOutputStream = new ByteArrayOutputStream();
            when(mock.getOutputStream()).thenReturn(new DummyServletOutputStream(responseOutputStream));
            when(mock.getWriter()).thenReturn(new PrintWriter(new DummyServletOutputStream(responseOutputStream)));
        } catch (IOException e) {
            throw new AssertionError("Unexpected error", e);
        }
        // initialize status
        doAnswer(invocation -> status).when(mock).getStatus();
        doAnswer(invocation -> {
            status = (Integer) invocation.getArguments()[0];
            return null;
        }).when(mock).setStatus(anyInt());
    }

    public static MockHttpServletResponse init() {
        var response = Mockito.mock(HttpServletResponse.class, RETURNS_DEEP_STUBS);
        return new MockHttpServletResponse(response);
    }

    public HttpServletResponse mock() {
        return mock;
    }

    // APIs to get data set to the response

    public Cookie getCookie(String name) {
        if (cookies == null) {
            ArgumentCaptor<Cookie> captor = ArgumentCaptor.forClass(Cookie.class);
            verify(mock).addCookie(captor.capture());
            cookies = captor.getAllValues();
        }
        return cookies.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    }

    public Map<String, List<String>> getHeaders() {
        return Map.copyOf(headers);
    }

    public List<String> getHeaders(String name) {
        return headers.get(name);
    }

    public String getFirstHeader(String name) {
        List<String> headerValues = headers.get(name);
        return isNotEmpty(headerValues) ? headerValues.get(0) : null;
    }

    /**
     * @return the {@link ErrorSent} when the code has called {@link HttpServletResponse#sendError(int, String)}
     */
    public ErrorSent getError() {
        try {
            var codeCaptor = ArgumentCaptor.forClass(Integer.class);
            var messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(mock).sendError(codeCaptor.capture(), messageCaptor.capture());
            return new ErrorSent(codeCaptor.getValue(), messageCaptor.getValue());
        } catch (IOException e) {
            throw new AssertionError("Unexpected error", e);
        }
    }

    public String getRedirect() {
        try {
            var argCaptor = ArgumentCaptor.forClass(String.class);
            verify(mock).sendRedirect(argCaptor.capture());
            return argCaptor.getValue();
        } catch (IOException e) {
            throw new AssertionError("Unexpected error", e);
        }
    }

    public int getStatus() {
        return status;
    }

    public String getResponseAsString() {
        return responseOutputStream.toString();
    }

    public record ErrorSent(int code, String message) {
    }
}

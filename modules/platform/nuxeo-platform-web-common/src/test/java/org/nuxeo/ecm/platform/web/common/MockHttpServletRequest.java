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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * @since 2025.0
 */
public class MockHttpServletRequest {

    protected final HttpServletRequest mock;

    protected final Map<String, Object> attributes;

    protected Map<String, Object> sessionAttributes;

    protected List<Cookie> cookies;

    protected MockHttpServletRequest(HttpServletRequest request) {
        mock = request;
        when(mock.getLocale()).thenReturn(Locale.ENGLISH);
        // initialize attributes
        attributes = new HashMap<>();
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            attributes.put(key, value);
            return null;
        }).when(mock).setAttribute(anyString(), any());
        when(mock.getAttribute(anyString())).thenAnswer(
                invocation -> attributes.get(invocation.<String> getArgument(0)));
        when(mock.getAttributeNames()).thenAnswer(invocation -> Collections.enumeration(attributes.keySet()));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            attributes.remove(key);
            return null;
        }).when(mock).removeAttribute(anyString());
    }

    public static MockHttpServletRequest init() {
        var request = Mockito.mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
        return new MockHttpServletRequest(request);
    }

    public static MockHttpServletRequest init(String method, String requestURLString) {
        try {
            var request = Mockito.mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
            when(request.getMethod()).thenReturn(method);
            when(request.getRequestURL()).thenReturn(new StringBuffer(requestURLString));
            var requestURL = new URI(requestURLString).toURL();
            when(request.getServerName()).thenReturn(requestURL.getHost());
            when(request.getServerPort()).thenReturn(requestURL.getPort());
            when(request.getScheme()).thenReturn(requestURL.getProtocol());
            return new MockHttpServletRequest(request);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new AssertionError("Failed to build MockHttpServletRequest", e);
        }
    }

    // Mocking APIs

    public MockHttpServletRequest whenGetAttributeThenReturn(String key, Object value) {
        // as we store attributes to be able to retrieve them, just put it in the map
        attributes.put(key, value);
        return this;
    }

    public MockHttpServletRequest whenGetCookieThenReturn(String name, String value) {
        if (cookies == null) {
            cookies = new ArrayList<>();
            when(mock.getCookies()).thenAnswer(invocation -> cookies.toArray(Cookie[]::new));
        }
        cookies.add(new Cookie(name, value));
        return this;
    }

    public MockHttpServletRequest whenGetParameterThenReturn(String name, String value) {
        when(mock.getParameter(name)).thenReturn(value);
        return this;
    }

    /**
     * @return the mock this handler holds
     */
    public HttpServletRequest mock() {
        return mock;
    }

    // APIs to get data set to the request

    /**
     * @return the request attribute that we get from {@link HttpServletRequest#getAttribute(String)}
     */
    @SuppressWarnings("unchecked")
    public <R> R getAttribute(String key) {
        return (R) attributes.get(key);
    }

    /**
     * @return the session attribute that we get from {@link HttpServletRequest#getSession()} then
     *         {@link HttpSession#getAttribute(String)}
     */
    @SuppressWarnings("unchecked")
    public <R> R getSessionAttributeValue(String name) {
        if (sessionAttributes == null) {
            var sessionAttributeNamesCaptor = ArgumentCaptor.forClass(String.class);
            var sessionAttributeValuesCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mock.getSession(anyBoolean())).setAttribute(sessionAttributeNamesCaptor.capture(),
                    sessionAttributeValuesCaptor.capture());
            sessionAttributes = IntStream.range(0, sessionAttributeNamesCaptor.getAllValues().size())
                                         .boxed()
                                         .collect(Collectors.toMap(sessionAttributeNamesCaptor.getAllValues()::get,
                                                 sessionAttributeValuesCaptor.getAllValues()::get));
        }
        return (R) sessionAttributes.get(name);
    }
}

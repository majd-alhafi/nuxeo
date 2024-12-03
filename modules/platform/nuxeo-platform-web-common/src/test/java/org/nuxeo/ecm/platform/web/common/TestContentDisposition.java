/*
 * (C) Copyright 2013-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.platform.web.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @since 5.7.2
 */
public class TestContentDisposition {

    protected static final String MSIE_7 = "Mozilla/4.0 (compatible; MSIE 7.0)";

    protected static final String USER_AGENT = "User-Agent";

    @Test
    public void testContentDisposition() {
        var requestHandler = MockHttpServletRequest.init().whenGetHeaderThenReturn(USER_AGENT, MSIE_7);
        assertEquals("attachment; filename=myfile.txt",
                ServletHelper.getRFC2231ContentDisposition(requestHandler.mock(), "myfile.txt"));
    }

    @Test
    public void testContentDispositionWithInlineEnabledAsAttribute() {
        var requestHandler = MockHttpServletRequest.init()
                                                   .whenGetHeaderThenReturn(USER_AGENT, MSIE_7)
                                                   .whenGetAttributeThenReturn("inline", "true");
        assertEquals("inline; filename=myfile.txt",
                ServletHelper.getRFC2231ContentDisposition(requestHandler.mock(), "myfile.txt"));
    }

    @Test
    public void testContentDispositionWithInlineDisabledAsAttribute() {
        var requestHandler = MockHttpServletRequest.init()
                                                   .whenGetHeaderThenReturn(USER_AGENT, MSIE_7)
                                                   .whenGetAttributeThenReturn("inline", "false");
        assertEquals("attachment; filename=myfile.txt",
                ServletHelper.getRFC2231ContentDisposition(requestHandler.mock(), "myfile.txt"));
    }

    @Test
    public void testContentDispositionWithInlineEnabledAsParameter() {
        var requestHandler = MockHttpServletRequest.init()
                                                   .whenGetHeaderThenReturn(USER_AGENT, MSIE_7)
                                                   .whenGetAttributeThenReturn("inline", "true");
        assertEquals("inline; filename=myfile.txt",
                ServletHelper.getRFC2231ContentDisposition(requestHandler.mock(), "myfile.txt"));
    }

    @Test
    public void testContentDispositionWithInlineDisabledAsParameter() {
        var requestHandler = MockHttpServletRequest.init()
                                                   .whenGetHeaderThenReturn(USER_AGENT, MSIE_7)
                                                   .whenGetAttributeThenReturn("inline", "false");
        assertEquals("attachment; filename=myfile.txt",
                ServletHelper.getRFC2231ContentDisposition(requestHandler.mock(), "myfile.txt"));
    }
}

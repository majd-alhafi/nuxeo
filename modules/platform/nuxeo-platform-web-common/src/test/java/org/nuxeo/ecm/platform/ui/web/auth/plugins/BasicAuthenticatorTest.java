/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.ui.web.auth.plugins;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.platform.web.common.MockHttpServletRequest;
import org.nuxeo.ecm.platform.web.common.MockHttpServletResponse;

import com.google.common.collect.ImmutableMap;

/**
 * @since 5.9.2
 */
public class BasicAuthenticatorTest {

    static final ImmutableMap<String, String> BA_INIT_NOTOKEN = //
            new ImmutableMap.Builder<String, String>() //
                                                      .put("ExcludeBAHeader_Token", "X-Authorization-token") //
                                                      .put("ExcludeBAHeader_Other", "X-NoBAPrompt")//
                                                      .build();

    private BasicAuthenticator ba;

    @Before
    public void doBefore() {
        ba = new BasicAuthenticator();
        ba.initPlugin(BA_INIT_NOTOKEN);
    }

    @Test
    public void itDoesntSentBAHeaderWhenExcludeHeaderIsPresent() {
        var req = MockHttpServletRequest.init().whenGetHeaderThenReturn("X-Authorization-token", "bla").mock();
        var resp = MockHttpServletResponse.init().mock();

        ba.handleLoginPrompt(req, resp, "/");

        verify(resp, never()).addHeader(eq(BasicAuthenticator.BA_HEADER_NAME), anyString());
    }

    @Test
    public void itDoesntSendBaHeaderWhenExcludedCookieIsPresent() {
        var req = MockHttpServletRequest.init().whenGetCookieThenReturn("X-Authorization-token", "bla").mock();
        var resp = MockHttpServletResponse.init().mock();

        ba.handleLoginPrompt(req, resp, "/");

        verify(resp, never()).addHeader(eq(BasicAuthenticator.BA_HEADER_NAME), anyString());
    }

    @Test
    public void itSendsABAHeaderWhenNoExcludeHeaderIsSet() {
        var req = MockHttpServletRequest.init().mock();
        var resp = MockHttpServletResponse.init().mock();

        ba.handleLoginPrompt(req, resp, "/");

        verify(resp).addHeader(eq(BasicAuthenticator.BA_HEADER_NAME), anyString());
    }
}

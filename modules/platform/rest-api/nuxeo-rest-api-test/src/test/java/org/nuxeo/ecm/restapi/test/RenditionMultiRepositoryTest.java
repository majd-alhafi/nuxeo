/*
 * (C) Copyright 2024 Nuxeo SA (http://nuxeo.com/) and others.
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
 *    Guillaume Renard
 */
package org.nuxeo.ecm.restapi.test;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.nuxeo.ecm.core.io.registry.context.RenderingContext.REPOSITORY_NAME_REQUEST_HEADER;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * Tests the REST API document rendition endpoints with multiple repositories.
 *
 * @since 2025.0
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@Deploy("org.nuxeo.ecm.platform.rendition.api")
@Deploy("org.nuxeo.ecm.platform.rendition.core")
@Deploy("org.nuxeo.ecm.platform.restapi.test:renditions-test-contrib.xml")
@Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-multi-repository-contrib.xml")
public class RenditionMultiRepositoryTest {

    protected static final String OTHER_REPO = "other";

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    // NXP-32944
    @Test
    public void testRendition() {
        CoreSession otherRepositorySession = CoreInstance.getCoreSession(OTHER_REPO);
        DocumentModel doc = otherRepositorySession.createDocumentModel("/", "adoc", "File");
        doc = otherRepositorySession.createDocument(doc);
        txFeature.nextTransaction();

        httpClient.buildGetRequest("/path" + doc.getPathAsString() + "/@rendition/dummyRendition")
                  .addHeader(REPOSITORY_NAME_REQUEST_HEADER, OTHER_REPO)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

    }

}

/*
 * (C) Copyright 2021-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ftest.server;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.nuxeo.ecm.platform.web.common.idempotency.NuxeoIdempotentFilter.HEADER_KEY;
import static org.nuxeo.functionaltests.AbstractTest.NUXEO_URL;

import java.util.Date;

import javax.ws.rs.core.MediaType;

import org.junit.Rule;
import org.junit.Test;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;

/**
 * Tests for idempotent request mechanism.
 *
 * @since 11.5
 */
public class ITNuxeoIdempotentRequestTest {

    private static final String TEST_KEY = "idempotenttestkey" + new Date().getTime();

    private static final String PARENT_PATH = "/default-domain/workspaces/";

    private static final String QUERY_CHILDREN = String.format("SELECT * FROM Document WHERE %s STARTSWITH '%s'",
            NXQL.ECM_PATH, PARENT_PATH);

    private static final String TEST_TITLE = "testdoc";

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.builder()
                                                                   .url(NUXEO_URL + "/api/v1")
                                                                   .adminCredentials()
                                                                   .accept(MediaType.APPLICATION_JSON)
                                                                   .contentType(MediaType.APPLICATION_JSON)
                                                                   .header("X-NXproperties", "*")
                                                                   .build();

    protected String createDocument(boolean idempotent) {
        String document = """
                {
                  "entity-type": "document",
                  "type": "File",
                  "name": "testdoc",
                  "properties": {
                    "dc:title": "testdoc"
                  }
                }
                """;
        var requestBuilder = httpClient.buildPostRequest("/path" + PARENT_PATH).entity(document);
        if (idempotent) {
            requestBuilder.addHeader(HEADER_KEY, TEST_KEY);
        }
        var docId = requestBuilder.executeAndThen(new JsonNodeHandler(SC_CREATED), node -> node.get("uid").textValue());
        waitForAsyncWork();
        return docId;
    }

    /**
     * Prevents from random failures when counting children.
     */
    protected void waitForAsyncWork() {
        String entity = """
                {
                  "params": {
                    "timeoutSecond": 110,
                    "refresh": true,
                    "waitForAudit": true
                  }
                }
                """;
        httpClient.buildPostRequest("/automation/Elasticsearch.WaitForIndexing")
                  .entity(entity)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    protected int getNumberOfChildren() {
        return httpClient.buildGetRequest("/search/execute")
                         .addQueryParameter("query", QUERY_CHILDREN)
                         .executeAndThen(new JsonNodeHandler(), node -> node.get("entries").size());
    }

    protected String fetchDocumentTile(String id) {
        return httpClient.buildGetRequest("/id/" + id)
                         .executeAndThen(new JsonNodeHandler(), node -> node.get("title").textValue());
    }

    protected void delete(String id) {
        httpClient.buildDeleteRequest("/id/" + id)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
    }

    @Test
    public void testIdempotentRequest() {
        String id = null;
        String dupeId = null;
        try {
            assertEquals(0, getNumberOfChildren());

            // create child document
            id = createDocument(true);
            assertEquals(TEST_TITLE, fetchDocumentTile(id));
            // doc created
            assertEquals(1, getNumberOfChildren());

            // try creating it again
            dupeId = createDocument(true);
            assertEquals(id, dupeId);
            // dupe not created
            assertEquals(1, getNumberOfChildren());

            // create again without idempotency key
            dupeId = createDocument(false);
            assertNotEquals(id, dupeId);
            // dupe created
            assertEquals(2, getNumberOfChildren());
        } finally {
            if (id != null) {
                delete(id);
            }
            if (dupeId != null && !dupeId.equals(id)) {
                delete(dupeId);
            }
        }
    }

}

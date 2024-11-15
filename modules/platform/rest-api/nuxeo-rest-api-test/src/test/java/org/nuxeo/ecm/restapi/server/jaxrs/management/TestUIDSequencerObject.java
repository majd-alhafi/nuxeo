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
package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.uidgen.KeyValueStoreUIDSequencer;
import org.nuxeo.ecm.core.uidgen.UIDGeneratorService;
import org.nuxeo.ecm.restapi.test.JsonNodeHelper;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.LoggerLevel;

/**
 * Tests {@link UIDSequencerObject} endpoint.
 *
 * @since 2025.0
 */
@Deploy("org.nuxeo.ecm.core:OSGI-INF/uidgenerator-service.xml")
@Deploy("org.nuxeo.ecm.core:OSGI-INF/uidgenerator-keyvalue-config.xml")
@Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-uidsequencer-contrib.xml")
@LoggerLevel(klass = UIDSequencerObject.class, level = "ERROR") // hide init sequence logs
public class TestUIDSequencerObject extends ManagementBaseTest {

    @Inject
    protected UIDGeneratorService uidGeneratorService;

    @Before
    public void setup() {
        // init the sequencer to have keys
        var uidgenSequencer = uidGeneratorService.getSequencer("uidgen");
        uidgenSequencer.initSequence("seq1", 0);
        uidgenSequencer.initSequence("seq2", 100);

        // just test the sequencer is contributed
        var myseqSequencer = uidGeneratorService.getSequencer("myseq");
        assertNotNull(myseqSequencer);
    }

    @Test
    public void testGetSequencers() {
        httpClient.buildGetRequest("/management/sequencers").executeAndConsume(new JsonNodeHandler(), node -> {
            var entries = JsonNodeHelper.getEntries("sequencers", node)
                                        .stream()
                                        .collect(Collectors.toMap(n -> n.get("name").textValue(), Function.identity()));
            assertEquals(2, entries.size());

            var myseqEntry = entries.get("myseq");
            assertEquals(KeyValueStoreUIDSequencer.class.getName(), myseqEntry.get("implementation").textValue());
            var myseqKeyValues = myseqEntry.get("keyValues");
            assertTrue(myseqKeyValues.isArray());
            assertTrue(myseqKeyValues.isEmpty());

            var uidgenEntry = entries.get("uidgen");
            assertEquals(KeyValueStoreUIDSequencer.class.getName(), uidgenEntry.get("implementation").textValue());
            var uidgenKeyValues = uidgenEntry.get("keyValues");
            assertTrue(uidgenKeyValues.isArray());
            assertEquals(2, uidgenKeyValues.size());
            var uidgenSeq1 = uidgenKeyValues.get(0);
            assertEquals("seq1", uidgenSeq1.get("key").textValue());
            assertEquals(0L, uidgenSeq1.get("value").longValue());
            var uidgenSeq2 = uidgenKeyValues.get(1);
            assertEquals("seq2", uidgenSeq2.get("key").textValue());
            assertEquals(100L, uidgenSeq2.get("value").longValue());
        });
    }

    @Test
    public void testInitSequenceParametersRequired() {
        httpClient.buildPostRequest("/management/sequencers/myseq")
                  .contentType("application/x-www-form-urlencoded")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> {
                      String message = JsonNodeHelper.getErrorMessage(node);
                      assertEquals("The key and value cannot be null or empty", message);
                  });
    }

    @Test
    public void testInitNonExistingSequence() {
        httpClient.buildPostRequest("/management/sequencers/myseq")
                  .entity(Map.of("key", "myseq1", "value", "1000"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        assertEquals(1_000L, uidGeneratorService.getSequencer("myseq").getCurrent("myseq1"));
    }

    @Test
    public void testInitExistingSequence() {
        httpClient.buildPostRequest("/management/sequencers/uidgen")
                  .entity(Map.of("key", "seq1", "value", "50"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        assertEquals(50, uidGeneratorService.getSequencer("uidgen").getCurrent("seq1"));
    }
}

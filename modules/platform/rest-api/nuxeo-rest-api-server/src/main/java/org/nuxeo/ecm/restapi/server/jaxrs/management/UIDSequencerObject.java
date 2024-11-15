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
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.uidgen.UIDGeneratorService;
import org.nuxeo.ecm.restapi.jaxrs.io.management.Sequencer;
import org.nuxeo.ecm.restapi.jaxrs.io.management.SequencerKeyValue;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.AbstractResource;
import org.nuxeo.ecm.webengine.model.impl.ResourceTypeImpl;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 2025.0
 */
@WebObject(type = ManagementObject.MANAGEMENT_OBJECT_PREFIX + "sequencers")
@Produces(APPLICATION_JSON)
public class UIDSequencerObject extends AbstractResource<ResourceTypeImpl> {

    private static final Logger log = LogManager.getLogger(UIDSequencerObject.class);

    @GET
    public List<Sequencer> getSequencers() {
        return Framework.getService(UIDGeneratorService.class)
                        .getSequencers()
                        .stream()
                        .map(sequencer -> new Sequencer(sequencer.getName(), sequencer.getClass().getName(),
                                sequencer.getKeys()
                                         .stream()
                                         .map(key -> new SequencerKeyValue(key, sequencer.getCurrent(key)))
                                         .toList()))
                        .toList();
    }

    @POST
    @Path("{sequencer}")
    public Response initSequence(@PathParam("sequencer") String sequencer, @FormParam("key") String key,
            @FormParam("value") Long value) {
        if (StringUtils.isBlank(key) || value == null) {
            throw new NuxeoException("The key and value cannot be null or empty", SC_BAD_REQUEST);
        }
        log.warn("Initializing the sequence: {} to value: {} for the sequencer: {}", key, value, sequencer);
        Framework.getService(UIDGeneratorService.class).getSequencer(sequencer).initSequence(key, value);
        return Response.noContent().build();
    }
}

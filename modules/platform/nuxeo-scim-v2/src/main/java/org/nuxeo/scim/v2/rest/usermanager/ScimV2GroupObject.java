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
 *     Thierry Delprat
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.rest.usermanager;

import static com.unboundid.scim2.common.exceptions.BadRequestException.INVALID_SYNTAX;
import static com.unboundid.scim2.common.utils.ApiConstants.MEDIA_TYPE_SCIM;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_ATTRIBUTES;
import static com.unboundid.scim2.common.utils.ApiConstants.QUERY_PARAMETER_EXCLUDED_ATTRIBUTES;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.nuxeo.scim.v2.api.ScimV2QueryContext.FETCH_GROUP_MEMBERS_CTX_PARAM;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.nuxeo.common.function.ThrowableUnaryOperator;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.scim.v2.api.ScimV2Helper;
import org.nuxeo.scim.v2.api.ScimV2QueryContext;

import com.sun.jersey.api.core.HttpContext;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.server.PATCH;
import com.unboundid.scim2.server.annotations.ResourceType;
import com.unboundid.scim2.server.utils.ResourcePreparer;
import com.unboundid.scim2.server.utils.ResourceTypeDefinition;
import com.unboundid.scim2.server.utils.ServerUtils;

/**
 * SCIM 2.0 Group object.
 *
 * @since 2023.14
 */
@Produces({ MEDIA_TYPE_SCIM, APPLICATION_JSON })
@ResourceType(description = "Group", name = "Group", schema = GroupResource.class)
@Path("/Groups")
public class ScimV2GroupObject extends ScimV2BaseUMObject {

    protected static final ResourceTypeDefinition RESOURCE_TYPE_DEFINITION = ResourceTypeDefinition.fromJaxRsResource(
            ScimV2UserObject.class);

    public ScimV2GroupObject(@Context HttpContext httpContext) {
        super(httpContext);
    }

    @POST
    public Response createGroup(GroupResource group) throws ScimException {
        checkUpdateGuardPreconditions();
        return ServerUtils.setAcceptableType(
                Response.status(CREATED).entity(newResourcePreparer().trimCreatedResource(doCreateGroup(group), group)),
                webContext.getHttpHeaders().getAcceptableMediaTypes()).build();
    }

    @GET
    @Path("{uid}")
    public ScimResource getGroupResource(@PathParam("uid") String uid) throws ScimException {
        return newResourcePreparer().trimRetrievedResource(resolveGroupResource(uid));
    }

    @PUT
    @Path("{uid}")
    public ScimResource updateGroup(@PathParam("uid") String uid, GroupResource group) throws ScimException {
        checkUpdateGuardPreconditions();
        return newResourcePreparer().trimReplacedResource(doUpdateGroup(uid, group), group);
    }

    @PATCH
    @Path("{uid}")
    public ScimResource patchGroup(@PathParam("uid") String uid, PatchRequest patch) throws ScimException {
        checkUpdateGuardPreconditions();
        return newResourcePreparer().trimModifiedResource(doPatchGroup(uid, patch), patch.getOperations());
    }

    @DELETE
    @Path("{uid}")
    public Response deleteGroupResource(@PathParam("uid") String uid) throws ScimException {
        checkUpdateGuardPreconditions();
        return doDeleteGroup(uid);
    }

    protected GroupResource doCreateGroup(GroupResource group) throws ScimException {
        if (group == null) {
            throw new BadRequestException("Cannot create group without a group resource as request body",
                    INVALID_SYNTAX);
        }
        DocumentModel newGroup = mappingService.createNuxeoGroupFromGroupResource(group);
        return mappingService.getGroupResourceFromNuxeoGroup(newGroup, baseURL);

    }

    protected GroupResource resolveGroupResource(String uid) throws ScimException {
        DocumentModel groupModel = ScimV2Helper.getGroupModel(uid, isFetchMembers());
        return mappingService.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
    }

    protected GroupResource doUpdateGroup(String uid, GroupResource group) throws ScimException {
        if (group == null) {
            throw new BadRequestException("Cannot update group without a group resource as request body",
                    INVALID_SYNTAX);
        }
        DocumentModel groupModel = mappingService.updateNuxeoGroupFromGroupResource(uid, group);
        return mappingService.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
    }

    protected GroupResource doPatchGroup(String uid, PatchRequest patch) throws ScimException {
        if (patch == null) {
            throw new BadRequestException("Cannot patch group without a patch request resource as request body",
                    INVALID_SYNTAX);
        }
        mappingService.patchNuxeoGroup(uid, patch);
        DocumentModel groupModel = ScimV2Helper.getGroupModel(uid, isFetchMembers());
        return mappingService.getGroupResourceFromNuxeoGroup(groupModel, baseURL);
    }

    protected Response doDeleteGroup(String uid) throws ScimException {
        um.deleteGroup(ScimV2Helper.getGroupModel(uid, false));
        return Response.noContent().build();
    }

    @Override
    protected ListResponse<ScimResource> doSearch(ScimV2QueryContext queryCtx) throws ScimException {
        queryCtx.withContextParam(FETCH_GROUP_MEMBERS_CTX_PARAM, isFetchMembers());
        return mappingService.queryGroups(queryCtx.withTransform(
                ThrowableUnaryOperator.asUnaryOperator(newResourcePreparer()::trimRetrievedResource)));
    }

    protected ResourcePreparer<ScimResource> newResourcePreparer() throws BadRequestException {
        return new ResourcePreparer<>(RESOURCE_TYPE_DEFINITION, webContext.getUriInfo());
    }

    protected boolean isFetchMembers() {
        var uriInfo = webContext.getUriInfo();
        var excludedAttributes = uriInfo.getQueryParameters().getFirst(QUERY_PARAMETER_EXCLUDED_ATTRIBUTES);
        var includedAttributes = uriInfo.getQueryParameters().getFirst(QUERY_PARAMETER_ATTRIBUTES);
        return (excludedAttributes == null || !excludedAttributes.contains("members"))
                && (includedAttributes == null || includedAttributes.contains("members"));
    }

}

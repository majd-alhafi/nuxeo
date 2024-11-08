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
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.apache.commons.lang3.StringUtils.isBlank;

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
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import com.unboundid.scim2.server.PATCH;
import com.unboundid.scim2.server.annotations.ResourceType;
import com.unboundid.scim2.server.utils.ResourcePreparer;
import com.unboundid.scim2.server.utils.ResourceTypeDefinition;
import com.unboundid.scim2.server.utils.ServerUtils;

/**
 * SCIM 2.0 User object.
 *
 * @since 2023.14
 */
@Produces({ MEDIA_TYPE_SCIM, APPLICATION_JSON })
@ResourceType(description = "User Account", name = "User", schema = UserResource.class)
@Path("/Users")
public class ScimV2UserObject extends ScimV2BaseUMObject {

    protected static final ResourceTypeDefinition RESOURCE_TYPE_DEFINITION = ResourceTypeDefinition.fromJaxRsResource(
            ScimV2UserObject.class);

    public ScimV2UserObject(@Context HttpContext httpContext) {
        super(httpContext);
    }

    @POST
    public Response createUser(UserResource user) throws ScimException {
        checkUpdateGuardPreconditions();
        return ServerUtils.setAcceptableType(
                Response.status(CREATED).entity(newResourcePreparer().trimCreatedResource(doCreateUser(user), user)),
                webContext.getHttpHeaders().getAcceptableMediaTypes()).build();
    }

    @GET
    @Path("{uid}")
    public ScimResource getUserResource(@PathParam("uid") String uid) throws ScimException {
        return newResourcePreparer().trimRetrievedResource(resolveUserResource(uid));
    }

    @PUT
    @Path("{uid}")
    public ScimResource updateUser(@PathParam("uid") String uid, UserResource user) throws ScimException {
        checkUpdateGuardPreconditions();
        return newResourcePreparer().trimReplacedResource(doUpdateUser(uid, user), user);
    }

    @PATCH
    @Path("{uid}")
    public ScimResource patchUser(@PathParam("uid") String uid, PatchRequest patch) throws ScimException {
        checkUpdateGuardPreconditions();
        return newResourcePreparer().trimModifiedResource(doPatchUser(uid, patch), patch);
    }

    @DELETE
    @Path("{uid}")
    public Response deleteUserResource(@PathParam("uid") String uid) throws ScimException {
        checkUpdateGuardPreconditions();
        return doDeleteUser(uid);
    }

    protected UserResource doCreateUser(UserResource user) throws ScimException {
        if (user == null) {
            throw new BadRequestException("Cannot create user without a user resource as request body", INVALID_SYNTAX);
        }
        var userName = user.getUserName();
        if (isBlank(userName)) {
            throw new BadRequestException("Cannot create user without a username", INVALID_SYNTAX);
        }
        DocumentModel newUser = mappingService.createNuxeoUserFromUserResource(user);
        return mappingService.getUserResourceFromNuxeoUser(newUser, baseURL);
    }

    protected UserResource resolveUserResource(String uid) throws ScimException {
        DocumentModel userModel = um.getUserModel(uid);
        if (userModel == null) {
            throw new ResourceNotFoundException("Cannot find user: " + uid); // NOSONAR
        }
        return mappingService.getUserResourceFromNuxeoUser(userModel, baseURL);
    }

    protected UserResource doUpdateUser(String uid, UserResource user) throws ScimException {
        if (user == null) {
            throw new BadRequestException("Cannot update user without a user resource as request body", INVALID_SYNTAX);
        }
        DocumentModel userModel = mappingService.updateNuxeoUserFromUserResource(uid, user);
        return mappingService.getUserResourceFromNuxeoUser(userModel, baseURL);
    }

    protected UserResource doPatchUser(String uid, PatchRequest patch) throws ScimException {
        if (patch == null) {
            throw new BadRequestException("Cannot patch user without a patch request resource as request body",
                    INVALID_SYNTAX);
        }
        DocumentModel userModel = mappingService.patchNuxeoUser(uid, patch);
        return mappingService.getUserResourceFromNuxeoUser(userModel, baseURL);
    }

    protected Response doDeleteUser(String uid) throws ScimException {
        um.deleteUser(ScimV2Helper.getUserModel(uid));
        return Response.noContent().build();
    }

    @Override
    protected ListResponse<ScimResource> doSearch(ScimV2QueryContext queryCtx) throws ScimException {
        return mappingService.queryUsers(queryCtx.withTransform(
                ThrowableUnaryOperator.asUnaryOperator(newResourcePreparer()::trimRetrievedResource)));
    }

    protected ResourcePreparer<ScimResource> newResourcePreparer() throws BadRequestException {
        return new ResourcePreparer<>(RESOURCE_TYPE_DEFINITION, webContext.getUriInfo());
    }
}

/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.pso.data.services.security;

import com.google.cloud.policytroubleshooter.v1.IamCheckerClient;
import com.google.cloud.policytroubleshooter.v1.TroubleshootIamPolicyRequest;
import google.cloud.policytroubleshooter.v1.Explanations;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.Codec;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Singleton
public class IAMCheckSecurityFilter {
  private static final Logger LOG = LoggerFactory.getLogger(IAMCheckSecurityFilter.class);

  private static final String API_GATEWAY_USERINFO_HEADER = "x-apigateway-api-userinfo";
  private static final String CLOUDRUN_INVOKE_SERVICE_PERMISSION = "run.routes.invoke";

  @Inject
  @Named("cloudrun.service.id")
  private String cloudRunServiceId;

  private IamCheckerClient checkerClient;

  @ServerRequestFilter
  public Optional<RestResponse<ForbiddenReason>> postFilter(ContainerRequestContext ctx) {
    /**
     * At the point, only already authenticated requests will reach. If the request is being served
     * directly from CloudRun it was already authorized since we only enable access to an email
     * domain (for users) and the solution's service account, so we should let it pass returning an
     * empty Optional. If the request is being served by API Gateway, then we should see
     * 'x-apigateway-api-userinfo' as part of the headers including a value and we should check then
     * if the email authenticated does have permissions to access the service before authorizing the
     * access.
     */
    return Optional.ofNullable(ctx.getHeaders().getFirst(API_GATEWAY_USERINFO_HEADER))
        .map(encodedUserInfo -> new JsonObject(new String(Codec.base64Decode(encodedUserInfo))))
        .filter(json -> !isAccessAuthorized(json))
        .map(
            denied ->
                RestResponse.status(
                    Response.Status.FORBIDDEN,
                    new ForbiddenReason(
                        "The access to the resource has been denied for the user.")));
  }

  void createCheckerIfNeeded() throws IOException {
    if (checkerClient == null || checkerClient.isTerminated()) {
      checkerClient = IamCheckerClient.create();
    }
  }

  boolean isAccessAuthorized(JsonObject userInfo) {
    // if email was not verified or is not there then deny
    if (!userInfo.getBoolean("email_verified", false)
        || userInfo.getString("email", "").isEmpty()) {
      return false;
    }
    var email = userInfo.getString("email");
    try {
      createCheckerIfNeeded();
      var response =
          checkerClient.troubleshootIamPolicy(
              TroubleshootIamPolicyRequest.newBuilder()
                  .setAccessTuple(
                      Explanations.AccessTuple.newBuilder()
                          .setPrincipal(email)
                          .setFullResourceName(cloudRunServiceId)
                          .setPermission(CLOUDRUN_INVOKE_SERVICE_PERMISSION)
                          .build())
                  .build());
      return response.getAccess().equals(Explanations.AccessState.GRANTED);
    } catch (Exception ex) {
      LOG.error("Error while trying to validate access, email: " + email, ex);
      return false;
    }
  }

  @PreDestroy
  public void destroy() {
    if (checkerClient != null) {
      checkerClient.close();
    }
  }

  public record ForbiddenReason(String reason) {}
}

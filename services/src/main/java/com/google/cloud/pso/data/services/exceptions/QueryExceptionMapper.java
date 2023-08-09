/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.google.cloud.pso.data.services.exceptions;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/** */
@Provider
public class QueryExceptionMapper implements ExceptionMapper<Throwable> {

  public record ErrorResponse(String error) {}

  @Override
  public Response toResponse(Throwable e) {
    if (e instanceof QueryResourceException qre) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              new ErrorResponse(
                  qre.getMessage()
                      + String.format(
                          " Query: '%s'. Session id: '%s'", qre.getQueryText(), qre.getSessionId())))
          .build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
  }
}

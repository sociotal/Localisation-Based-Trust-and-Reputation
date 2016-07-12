package lsoc.gateway.standalone.core;

import com.fasterxml.jackson.core.JsonParseException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class JSONParseException implements ExceptionMapper<JsonParseException> {
    @Override
    public Response toResponse(final JsonParseException jpe) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid data supplied for request").build();
    }
}
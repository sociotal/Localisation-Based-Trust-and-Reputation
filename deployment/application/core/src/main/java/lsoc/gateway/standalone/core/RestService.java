package lsoc.gateway.standalone.core;

import lsoc.gateway.standalone.data.Resource;
import lsoc.gateway.standalone.store.Store;
import org.slf4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

@Path("/")
public class RestService {
    private static Logger logger = org.slf4j.LoggerFactory.getLogger(GatewayCore.class);
    private Store store;

    public void setStore(Store store) {
        this.store = store;
    }

    @GET
    @Path("/resources")
    @Produces({MediaType.APPLICATION_JSON})
    public Collection<Resource> resourceList() {
        return store.getResources();
    }

    @DELETE
    @Path("/resources")
    public void deleteResources() {
        store.deleteResources();
    }

    @DELETE
    @Path("/resource/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public boolean deleteResource(@PathParam("id") String resourceId) {
        return store.deleteResource(resourceId);
    }

    @POST
    @Path("/resource")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public boolean resourceAdd(Resource resource) {
        return store.putResource(resource);
    }
}

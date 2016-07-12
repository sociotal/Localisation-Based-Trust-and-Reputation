package lsoc.gateway.standalone.store;

import lsoc.gateway.standalone.data.Resource;

import java.util.Collection;

public interface Store {
    Collection<Resource> getResources();

    Resource getResource(String resourceId);

    boolean putResource(Resource resource);

    boolean deleteResource(String resourceId);

    void deleteResources();

}

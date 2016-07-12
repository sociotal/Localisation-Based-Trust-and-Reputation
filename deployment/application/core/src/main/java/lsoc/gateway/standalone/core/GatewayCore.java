package lsoc.gateway.standalone.core;

import lsoc.gateway.standalone.store.Store;
import org.slf4j.Logger;

public class GatewayCore {
    private static Logger logger = org.slf4j.LoggerFactory.getLogger(GatewayCore.class);
    private Store store = null;

    public void start() {
        logger.info("Starting GatewayCore");
        logger.debug("Store service is {}", store);
    }

    public void setStore(Store store) {
        this.store = store;
    }
}

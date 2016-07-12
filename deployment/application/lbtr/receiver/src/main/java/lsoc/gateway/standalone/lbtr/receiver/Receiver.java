package lsoc.gateway.standalone.lbtr.receiver;

import lsoc.gateway.standalone.store.Store;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class Receiver {
	private static Logger logger = org.slf4j.LoggerFactory.getLogger(Receiver.class);
	private Store store = null;

	private String listenAddress;
	private int listenPort;
	private ServerThread serverThread;
	private LocationCalculator locationCalculator;

	public LocationCalculator getLocationCalculator() {
		return locationCalculator;
	}

	public void start() throws IOException {
		logger.info("Started LBTR receiver with store " + store);

		locationCalculator = new LocationCalculator();

		serverThread = new ServerThread(listenAddress, listenPort, store, locationCalculator);
		serverThread.start();
	}

	public void stop() {
		logger.info("Terminating ServerThread");
		serverThread.terminate();
	}

	public void setStore(Store store) {
		this.store = store;
	}

	public void setListenAddress(String listenAddress) {
		this.listenAddress = listenAddress;
	}

	public void setListenPort(int listenPort) {
		this.listenPort = listenPort;
	}
}

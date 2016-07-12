package lsoc.gateway.standalone.lbtr.receiver;

import lsoc.gateway.standalone.data.Resource;
import lsoc.gateway.standalone.store.Store;
import org.slf4j.Logger;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.SocketException;
import java.util.*;

public class ServerThread extends Thread {
	private static final int DATAGRAM_LENGTH = 1024;
	private static final String OWNER_ID = "mobilenode";

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(ServerThread.class);
	private final Store store;
	private final LocationCalculator locationCalculator;
	private final DatagramSocket serverSocket;
	private boolean running = true;

	ServerThread(String listenAddress, int listenPort, Store store, LocationCalculator locationCalculator) throws IOException {
		super(ServerThread.class.getName());
		this.store = store;
		this.locationCalculator = locationCalculator;
		this.serverSocket = new DatagramSocket(listenPort, Inet6Address.getByName(listenAddress));
	}

	@Override
	public void run() {
		byte[] buffer = new byte[DATAGRAM_LENGTH];
		while (running) {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			try {
				serverSocket.receive(packet);
				final int length = packet.getLength();
				final byte[] data = Arrays.copyOfRange(packet.getData(), 0, length);
				logger.info("New packet: {}:{}, length {}", packet.getAddress(), packet.getPort(), length);
				if (length == 3 && new String(data).equals("RST")) {
					logger.info("Clearing locator data");
					locationCalculator.clear();
					continue;
				}
				if (length < 2) {
					logger.warn("Malformed packet (too short)");
					continue;
				}
				int index = data[0] & 0xff, size = data[1] & 0xff;
				logger.info("Measure index: {}; anchor count: {}; raw: {}", index, size, DatatypeConverter.printHexBinary(data));
				if (length != size * 2 + 3) {
					logger.warn("Malformed packet (wrong size)");
					continue;
				}

				Map<String, Double> rssis = new HashMap<>();
				int ourChksum = 0;
				ourChksum ^= index;
				for (int i = 2; i < 2 + size; i++) {
					int from = data[i], to = data[i + size];
					ourChksum ^= from;
					ourChksum ^= to;
					if (from == 0 || to == 0 || from <= -98 || to <= -98)
						continue;
					rssis.put(String.valueOf(i - 1), (from + to) / 2.);
				}
				int chksum = data[2 + size * 2] & 0xff;
				logger.info("ourChksum={}, chksum={}; rssi={}", ourChksum, chksum, rssis);
				if (ourChksum != chksum) {
					logger.warn("Wrong checksum; dropping measure");
					continue;
				}

				for (Map.Entry<String, Double> entry : rssis.entrySet()) {
					Resource resource = new Resource();
					resource.owner = OWNER_ID;
					resource.type = "integer";
					resource.actions = "GET";
					resource.id = String.format("%s/rssi/%s", resource.owner, entry.getKey());
					resource.timestamp = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
					resource.value = String.valueOf(entry.getValue());
					if (store.putResource(resource)) {
						logger.info("Storing resource {}", resource);
					}
					MoteRssiPayload payload = new MoteRssiPayload(entry.getKey(), entry.getValue());
					locationCalculator.push(payload);
				}
			} catch (SocketException e) {
				// Most likely interrupted while blocking on receive()
				logger.warn("Socket closed");
				running = false;
				break;
			} catch (IOException e) {
				// Other I/O errors
				e.printStackTrace();
				running = false;
				break;
			}
		}
		serverSocket.close();
	}

	public void terminate() {
		logger.info("Interrupting the thread");
		running = false;
		serverSocket.close();
		interrupt();
	}
}

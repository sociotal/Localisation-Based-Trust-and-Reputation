package lsoc.gateway.standalone.lbtr.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class LocDemoServlet extends HttpServlet {
	private static Logger logger = org.slf4j.LoggerFactory.getLogger(Receiver.class);
	private String alias;
	private Receiver receiver;

	static private Map<String, Object> locationMap(String method, LocationCalculator.Coord coord) {
		if (coord == null)
			return null;
		Map<String, Object> map = new HashMap<>();
		map.put("method", method);
		map.put("x", coord.x);
		map.put("y", coord.y);
		return map;
	}

	private String getUri(HttpServletRequest request) {
		final String uri = request.getRequestURI();
		if (uri.startsWith(alias))
			return uri.substring(alias.length());
		return uri;
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		final ObjectMapper mapper = new ObjectMapper();
		final String uri = getUri(request);
		final LocationCalculator locator = receiver.getLocationCalculator();
		if (uri.equals("/anchors")) {
			response.setContentType("application/json");
			final Map<String, LocationCalculator.Coord> anchors = LocationCalculator.getAnchorMap();
			final List<AnchorData> anchorData = new ArrayList<>();
			for (final Map.Entry<String, LocationCalculator.Coord> entry : anchors.entrySet()) {
				anchorData.add(new AnchorData(entry.getKey(), entry.getValue()));
			}
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(mapper.writeValueAsString(anchorData));
			return;
		}
		if (uri.equals("/locations")) {
			response.setStatus(HttpServletResponse.SC_OK);
			final LocationCalculator.Coord locationBC = locator.computeBarycenter();
			final LocationCalculator.Coord locationLMS = locator.computeLeastMeanSquare();
			final LocationCalculator.Coord locationGD = locator.computeGradientDescent();
			final HashMap<String, Object> map = new HashMap<>();
			final List<Map<String, Object>> anchors = new ArrayList<>();
			for (Map.Entry<String, Double> entry : locator.meanRssi().entrySet()) {
				Map<String, Object> anchor = new HashMap<>();
				anchor.put("name", entry.getKey());
				anchor.put("rssi", entry.getValue());
				anchors.add(anchor);
			}
			map.put("anchors", anchors);
			map.put("locations", Arrays.asList(
					locationMap("Bary", locationBC),
					locationMap("LMS", locationLMS),
					locationMap("GD", locationGD))
			);
			logger.info("Location GD: {}", locationGD);
			response.getWriter().write(mapper.writeValueAsString(map));
			return;
		}
		super.doGet(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		final String uri = getUri(request);
		if (uri.equals("/clear")) {
			receiver.getLocationCalculator().clear();
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		}
		super.doPost(request, response);
	}

	@SuppressWarnings("unused")
	public void setReceiver(Receiver receiver) {
		this.receiver = receiver;
	}

	@SuppressWarnings("unused")
	public void setAlias(String alias) {
		this.alias = alias;
	}

	static class AnchorData {
		public String name;
		public double x, y;

		public AnchorData(String name, LocationCalculator.Coord coord) {
			this.name = name;
			this.x = coord.x;
			this.y = coord.y;
		}
	}
}

package lsoc.gateway.standalone.lbtr.receiver;

import org.la4j.LinearAlgebra;
import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.matrix.functor.MatrixFunction;
import org.la4j.vector.functor.VectorAccumulator;
import org.la4j.vector.functor.VectorFunction;
import org.slf4j.Logger;

import java.util.*;

public class LocationCalculator {
	private static final int MEAN_SAMPLES = 1;
	private static double REF_DISTANCE = 1;
	private static final double REF_RSSI = -62; // RSSI observed at REF_DISTANCE meters
	private static final double PATHLOSS_EXP = 3.5;
	private static final double PATHLOSS_SHADOWING = 6.9;
	private static final Coord AREA_BOUNDS_TOP_LEFT = new Coord(0, 0);
	private static final Coord AREA_BOUNDS_BOTTOM_RIGHT = new Coord(6, 10);
	private static final Map<String, Coord> anchorMap = new LinkedHashMap<>();
	private static Logger logger = org.slf4j.LoggerFactory.getLogger(LocationCalculator.class);

	static {
		anchorMap.put("1", new Coord(-2.37, 5));
		anchorMap.put("2", new Coord(-5.1, 6.7));
		anchorMap.put("3", new Coord(-4.71, 7.73));
		anchorMap.put("4", new Coord(-4.52, 9.25));
		anchorMap.put("5", new Coord(-2.41, 9.7));
		anchorMap.put("6", new Coord(-1.04, 7.3));
		anchorMap.put("7", new Coord(-0, 6.61));
		anchorMap.put("8", new Coord(-1, 5.5));
		anchorMap.put("9", new Coord(-0.78, 4.74));
		anchorMap.put("10", new Coord(-2.14, 2.43));
		anchorMap.put("11", new Coord(-1.56, 2.17));
		anchorMap.put("12", new Coord(-0, 1.8));
		anchorMap.put("13", new Coord(-0, 0));

		// scale/translate
		double scale = 42.8;
		Coord translate = new Coord(258, 52);
		REF_DISTANCE *= scale;
		AREA_BOUNDS_BOTTOM_RIGHT.x = AREA_BOUNDS_BOTTOM_RIGHT.x * scale + translate.x;
		AREA_BOUNDS_BOTTOM_RIGHT.y = AREA_BOUNDS_BOTTOM_RIGHT.y * scale + translate.y;
		for (Map.Entry<String, Coord> entry : anchorMap.entrySet()) {
			Coord coord = entry.getValue();
			coord.x = coord.x * scale + translate.x;
			coord.y = coord.y * scale + translate.y;
			anchorMap.put(entry.getKey(), coord);
		}
	}

	private final Map<String, List<Double>> rawRssi = new LinkedHashMap<>();

	public static Map<String, Coord> getAnchorMap() {
		return anchorMap;
	}

	public void push(MoteRssiPayload payload) {
		if (!anchorMap.containsKey(payload.macAddress))
			throw new IllegalArgumentException("Unknown MAC " + payload.macAddress);

		if (rawRssi.containsKey(payload.macAddress)) {
			rawRssi.get(payload.macAddress).add(payload.rssi());
		} else {
			rawRssi.put(payload.macAddress, new ArrayList<>(Collections.singletonList(payload.rssi())));
		}
	}

	public int sampleCount() {
		return meanRssi().size();
	}

	public void clear() {
		rawRssi.clear();
	}

	public Map<String, Double> meanRssi() {
		Map<String, Double> meanRssi = new LinkedHashMap<>();
		for (Map.Entry<String, List<Double>> macRssi : rawRssi.entrySet()) {
			final List<Double> rssis = macRssi.getValue();
			final String mac = macRssi.getKey();
			double mean = 0;
			int size = 0;
			for (double rssi : rssis) {
				mean += rssi;
				size++;
			}
			if (size >= MEAN_SAMPLES)
				meanRssi.put(mac, mean / size);
		}
		return meanRssi;
	}

	public Coord computeBarycenter() {
		Map<String, Double> weights = new HashMap<>();

		Map<String, Double> meanRssi = meanRssi();
		if (meanRssi.size() < 3)
			return null;

		double maxRssi = Double.MIN_VALUE;
		String maxRssiMac = null;
		for (Map.Entry<String, Double> entry : meanRssi.entrySet()) {
			double rssi = entry.getValue();
			if (rssi > maxRssi) {
				maxRssi = rssi;
				maxRssiMac = entry.getKey();
			}
		}

		assert maxRssiMac != null;
		double weightSum = 0;
		for (Map.Entry<String, Double> entry : meanRssi.entrySet()) {
			double rssi = entry.getValue();
			final double value = Math.pow(10, (Math.abs(maxRssi) - Math.abs(rssi)) / 10 * PATHLOSS_EXP);
			weights.put(entry.getKey(), value);
			weightSum += value;
		}

		double x = 0, y = 0;
		for (Map.Entry<String, Double> entry : weights.entrySet()) {
			final Coord coord = anchorMap.get(entry.getKey());
			x += entry.getValue() * coord.x;
			y += entry.getValue() * coord.y;
		}

		x /= weightSum;
		y /= weightSum;
		return new Coord(x, y);
	}

	private double distanceLMS(double rssi) {
		// d = ( d_ref × 10^(rssi_ref - rssi) / (10 × α) )²
		return Math.pow(REF_DISTANCE * Math.pow(10, (REF_RSSI - rssi) / (10 * PATHLOSS_EXP)), 2);
	}

	private double distanceGD(double rssi) {
		final double s = PATHLOSS_SHADOWING * Math.log(10) / (10 * PATHLOSS_EXP);
		final double m = (REF_RSSI - rssi) * Math.log(10) / (10 * PATHLOSS_EXP) + Math.log(REF_DISTANCE);
		return Math.exp(m - Math.pow(s, 2));
	}

	public Coord computeLeastMeanSquare() {
		// Mapping of anchor address ⇔ mean RSSI with this anchor
		Map<String, Double> meanRssi = meanRssi();

		int size = meanRssi.size();
		if (size < 3)
			// We obviously cannot compute localization with less that three svpmap
			return null;

		final List<String> macs = new ArrayList<>(meanRssi.keySet());
		// Choose the first anchor as an arbitrary reference for computations
		final String firstMac = macs.get(0);
		final Coord firstCoord = anchorMap.get(firstMac);
		final double firstDistance = distanceLMS(meanRssi.get(firstMac));

		// Fill the matrices
		Matrix a = Matrix.zero(size, 2);
		Vector h = Vector.zero(size);
		for (int i = 1; i < size; i++) {
			final String mac = macs.get(i);
			final Coord coord = anchorMap.get(mac);

			//     ⎡ x_i - x_0   y_i - y_0 ⎤
			// A = ⎢           …           ⎥
			//     ⎣ x_k - x_0   y_k - y_0 ⎦
			a.set(i, 0, coord.x - firstCoord.x);
			a.set(i, 1, coord.y - firstCoord.y);

			//     ⎡ x_i² - x_0² + y_i² - y_0² + d_0² - d_i² ⎤
			// h = ⎢                    …                    ⎥
			//     ⎣ x_k² - x_0² + y_k² - y_0² + d_0² - d_k² ⎦
			//       ╰───────────────────────╯
			//                hprime
			final double hprime = Math.pow(coord.x, 2) - Math.pow(firstCoord.x, 2) + Math.pow(coord.y, 2) - Math.pow(firstCoord.y, 2);
			h.set(i, hprime + firstDistance - distanceLMS(meanRssi.get(mac)));
		}

		// Apply the Least Square formula
		// X = ½ (Aᵀ A)⁻¹ Aᵀ h
		final Matrix aTransposed = a.transpose();
		final Vector result;
		try {
			result = aTransposed
					.multiply(a)
					.withInverter(LinearAlgebra.InverterFactory.GAUSS_JORDAN).inverse()
					.multiply(aTransposed)
					.multiply(h)
					.multiply(.5);
		} catch (IllegalArgumentException err) {
			// Not invertible
			return null;
		}
		return new Coord(result.get(0), result.get(1));
	}

	private Matrix realDistance(Vector x, Vector y) {
		int length = x.length();
		assert length == y.length();
		Matrix lx = Matrix.zero(length, length);
		Matrix ly = Matrix.zero(length, length);
		for (int i = 0; i < length; i++) {
			lx.setColumn(i, x);
			ly.setColumn(i, y);
		}
		lx = lx.subtract(lx.transpose()).transform(new Squared());
		ly = ly.subtract(ly.transpose()).transform(new Squared());
		Matrix d = lx.add(ly).transform(new SquaredRoot()).transform(new UpperTriangular());
		d = d.add(d.transpose());
		d = d.subtract(d.transform(new Diagonal()).transform(new Diagonal()));
		return d;
	}

	public Coord computeGradientDescent() {
		final int MAX_ITER = 100;
		final double MAX_DELTA = 1e-5;
		final double alpha = 0.1;
		// Mapping of anchor address ⇔ mean RSSI with this anchor
		Map<String, Double> meanRssi = meanRssi();

		int length = anchorMap.size();
		boolean[] select = new boolean[length];
		Vector anchorX = Vector.zero(length),
				anchorY = Vector.zero(length),
				distEstim = Vector.constant(length, Float.POSITIVE_INFINITY);

		// Build the anchor map vectors
		int okCount = 0;
		for (Map.Entry<String, Coord> entry : anchorMap.entrySet()) {
			int i = Integer.parseInt(entry.getKey()) - 1;
			Coord coord = entry.getValue();
			anchorX.set(i, coord.x);
			anchorY.set(i, coord.y);
			if (meanRssi.containsKey(entry.getKey())) {
				final double rssi = meanRssi.get(entry.getKey());
				if (Math.abs(rssi) > 1e-2 && rssi >= -92) {
					okCount += 1;
					select[i] = true;
					System.out.printf("%s rssi %s => %s%n", i, rssi, distanceGD(rssi));
					distEstim.set(i, distanceGD(rssi));
				}
			}
		}
		if (okCount < 3) {
			logger.warn("Not enough valid datapoints: {}", okCount);
			return new Coord(0, 0);
		}

		logger.info("Anchor RSSI {}", meanRssi);
		logger.info("Filtered RSSI {}", Arrays.toString(select));

		// get centroid of 3 nearest
		final List<Pair<String, Double>> estimIndexed = new ArrayList<>();
		for (int i = 0; i < distEstim.length(); i++)
			estimIndexed.add(new Pair<>(String.valueOf(i + 1), distEstim.get(i)));
		Collections.sort(estimIndexed);

		double initX = anchorMap.get(estimIndexed.get(0).index).x + anchorMap.get(estimIndexed.get(1).index).x + anchorMap.get(estimIndexed.get(2).index).x;
		double initY = anchorMap.get(estimIndexed.get(0).index).y + anchorMap.get(estimIndexed.get(1).index).y + anchorMap.get(estimIndexed.get(2).index).y;
		Vector result = Vector.fromArray(new double[]{initX / 3, initY / 3});

		double delta = Double.POSITIVE_INFINITY;
		int iter = 0;

		while (delta > MAX_DELTA && iter < MAX_ITER) {
			iter++;
			final Vector estimX = anchorX.copyOfLength(length + 1);
			estimX.set(length, result.get(0));
			final Vector estimY = anchorY.copyOfLength(length + 1);
			estimY.set(length, result.get(1));

			final Vector estimD = realDistance(estimX, estimY).getRow(length).sliceLeft(length);

			final Matrix lx = Matrix.zero(length + 1, length + 1);
			final Matrix ly = Matrix.zero(length + 1, length + 1);
			for (int i = 0; i < length + 1; i++) {
				lx.setRow(i, estimX);
				ly.setRow(i, estimY);
			}
			final Vector estimDX = lx.transpose().subtract(lx).getRow(length).sliceLeft(length);
			final Vector estimDY = ly.transpose().subtract(ly).getRow(length).sliceLeft(length);

			final double meanX = distEstim
					.subtract(estimD)
					.hadamardProduct(estimDX)
					.transform(new DivideBy(estimD))
					.fold(new NanMeanSelect(select)) * alpha;

			final double meanY = distEstim
					.subtract(estimD)
					.hadamardProduct(estimDY)
					.transform(new DivideBy(estimD))
					.fold(new NanMeanSelect(select)) * alpha;

			result = result.add(Vector.fromArray(new double[]{meanX, meanY}));
			result.set(0, Math.min(Math.max(AREA_BOUNDS_TOP_LEFT.x, result.get(0)), AREA_BOUNDS_BOTTOM_RIGHT.x));
			result.set(1, Math.min(Math.max(AREA_BOUNDS_TOP_LEFT.y, result.get(1)), AREA_BOUNDS_BOTTOM_RIGHT.y));
			delta = Math.sqrt(Math.pow(meanX, 2) + Math.pow(meanY, 2));
		}

		logger.debug("{} iterations, delta = {}", iter, delta);
		return new Coord(result.get(0), result.get(1));
	}

	static class Squared implements MatrixFunction {
		@Override
		public double evaluate(int row, int col, double v) {
			return v * v;
		}
	}

	static class SquaredRoot implements MatrixFunction {
		@Override
		public double evaluate(int row, int col, double v) {
			return Math.sqrt(v);
		}
	}

	static class UpperTriangular implements MatrixFunction {
		@Override
		public double evaluate(int row, int col, double v) {
			return (row <= col) ? v : 0;
		}
	}

	static class Diagonal implements MatrixFunction {

		@Override
		public double evaluate(int row, int col, double v) {
			return (row == col) ? v : 0;
		}
	}

	static class DivideBy implements VectorFunction {
		private final Vector divisor;

		DivideBy(Vector divisor) {
			this.divisor = divisor;
		}

		@Override
		public double evaluate(int i, double v) {
			return v / divisor.get(i);
		}
	}

	static class NanMeanSelect implements VectorAccumulator {
		private final boolean[] selector;
		double result = 0;
		int length = 0;

		NanMeanSelect(boolean[] selector) {
			this.selector = selector;
		}

		@Override
		public void update(int i, double v) {
			if (Double.isNaN(v) || Double.isInfinite(v))
				return;
			if (selector[i])
				result += v;
			length++;
		}

		@Override
		public double accumulate() {
			return result / length;
		}
	}

	public static class Coord {

		public double x, y;

		public Coord(double x, double y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public String toString() {
			return String.format("(%.2f, %.2f)", x, y);
		}
	}

	public class Pair<I, T extends Comparable<T>> implements Comparable<Pair<I, T>> {
		public final I index;
		public final T value;

		public Pair(I index, T value) {
			this.index = index;
			this.value = value;
		}

		@Override
		public int compareTo(Pair<I, T> other) {
			return this.value.compareTo(other.value);
		}
	}
}

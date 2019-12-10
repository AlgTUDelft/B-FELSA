package nl.tudelft.alg.fcc.model;

import java.util.Arrays;

public class Grid {
	double[][] capacities;

	public Grid(String[][] data) {
		capacities = Arrays.stream(data).skip(1)
				.map(d -> Arrays.stream(d).skip(1).mapToDouble(Double::parseDouble).toArray())
				.toArray(double[][]::new);
	}

	public double getCapacity(int timestep, int lineNumber) {
		return capacities[timestep][lineNumber];
	}

	public int getNGridLines() {
		if (capacities.length > 0)
			return capacities[0].length;
		return 0;
	}
}

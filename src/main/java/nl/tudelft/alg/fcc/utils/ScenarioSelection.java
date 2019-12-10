package nl.tudelft.alg.fcc.utils;

import java.util.Arrays;

public class ScenarioSelection {
	public int[] scenarios;
	public double[][] e,f;
	
	public String toString() {
		return Arrays.toString(scenarios) + "\n" + Arrays.deepToString(e) + "\n" + Arrays.deepToString(f);
	}
}

package nl.tudelft.alg.fcc.model;

import java.util.Arrays;

/**
 * Data class used by the algfcc.solution.mip.LinApproxModel class to model the piecewise linear parts
 */
public class Line {
	public double r;
	public double b;
	public double lb;
	public double rb;
	
	public Line(String[] raw) {
		r = Double.parseDouble(raw[1]);
		b = Double.parseDouble(raw[2]);
		lb = Double.parseDouble(raw[3]);
		rb = Double.parseDouble(raw[4]);
	}
	
	public static Line[] newLines(String[] raw) {
		int n = (raw.length-1)/4;
		Line[] result = new Line[n];
		//raw[3] = "0";
		for(int i=0; i<n; i++) {
			result[i] = new Line(Arrays.copyOfRange(raw, i*4, (i+1)*4+1));
		}
		return result;
	}
	
	@Override
	public String toString() {
		return r+"x + "+b;
	}
	
	public double getStartY() {
		return b+ lb*r;
	}
	
	public double getEndY() {
		return b+rb*r;
	}
}

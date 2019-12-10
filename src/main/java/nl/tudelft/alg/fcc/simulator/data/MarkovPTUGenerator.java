package nl.tudelft.alg.fcc.simulator.data;

import java.util.PriorityQueue;
import java.util.Random;

import nl.tudelft.alg.fcc.utils.Utils;

public class MarkovPTUGenerator {
	
	public static void generate(MarkovConfig c, double[][] input, int realStartT, int startT, int totalT, int PTUsPerDay, Random random, double scenarioFactor) {
		if(startT == 0) startT = 1;
		generate(input, realStartT, startT, totalT, PTUsPerDay, random, c.probabilities, scenarioFactor);
	}
	
	/**
	 * Generate a series for the reserve deployment percentages based on a Markov process
	 * @param input the original series
	 * @param realStartT the real beginning time step of the series
	 * @param startT the beginning time step of the series
	 * @param totalT the total number of time steps in the data set
	 * @param PTUsPerDay the number of PTUs in one day
	 * @param random the random number seed
	 * @param probs the matrix with transition probabilities with indices: period of the year, time of the day, current reserve usage, next reserve usage 
	 * @param scenarioFactor the ratio of scenarios to be generated in comparison to the number of scenarios to return
	 */
	private static void generate(double[][] input, int realStartT, int startT, int totalT, int PTUsPerDay, Random random, int[][][][] probs, double scenarioFactor) {
		final int nResultScenarios = input[0].length;
		double[][] oldValues = new double[input.length-startT][nResultScenarios];
		System.arraycopy(input, startT, oldValues, 0, input.length-startT);
		oldValues = Utils.deepArrayCopy(oldValues);
		scenarioFactor = nResultScenarios > 1 ? scenarioFactor : 1;
		PriorityQueue<QueueEntry> scenarioQueue = new PriorityQueue<QueueEntry>(nResultScenarios);
		
		int nPeriods = probs.length;
		int blocksize = probs[0].length;
		int resolution = probs[0][0].length-1;
		for (int _i = 0; _i < nResultScenarios * scenarioFactor; _i++) {
			final int i = (_i < nResultScenarios ? _i : scenarioQueue.poll().value);
			for(int t=startT;t<input.length; t++) {
				int realT = realStartT - startT + t;
				int h = (realT / (PTUsPerDay / blocksize)) % blocksize;
				int p = (realT / (totalT / nPeriods)) % nPeriods;
				int current = (int) Math.round(input[t-1][i] * resolution);
				int probsum = Utils.sum(probs[p][h][current]);
				if(probsum == 0) input[t][i] = 0.0;
				else {
					input[t][i] = Utils.weightedChoice(random, probs[p][h][current]) / ((double) resolution);
				}
			}
			if(scenarioFactor > 1) {
				double error = ArimaGenerator.getError(random, input, oldValues, startT, i, 0.5);
				scenarioQueue.add(new QueueEntry(error, i));
			}
		}
	}
	
	static class QueueEntry implements Comparable<QueueEntry> {
	    private double key;
	    private int value;

	    public QueueEntry(double key, int value) {
	        this.key = key;
	        this.value = value;
	    }

	    @Override
	    public int compareTo(QueueEntry other) {
	        return Double.compare(other.key, key);
	    }
	    
	    @Override
	   public String toString() {
	   	return Integer.toString(value) + ": " + Double.toString(key);
	   }
	}
	
}

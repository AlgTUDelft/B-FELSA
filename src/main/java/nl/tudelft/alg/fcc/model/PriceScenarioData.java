package nl.tudelft.alg.fcc.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.utils.CSVWriter;
import nl.tudelft.alg.fcc.utils.Utils;

public class PriceScenarioData extends PriceData implements Cloneable {
	double[][] downprice, upprice, 
			imbalanceprice, 
			proportionDownUsed, proportionUpUsed,
			capdownprice, capupprice;
	double[] scenarioProbability, expectedImbalancePrice, daPrice, 
		expectedProportionDownUsed, expectedProportionUpUsed,
		expectedDownprice, expectedUpprice,
		expectedCapdownprice, expectedCapupprice;
	int[][] sortedIndicesUpCap, sortedIndicesUpReg,
		sortedIndicesDownCap, sortedIndicesDownReg;
	Line[][] APup, APdown, ERup, ERdown;
	double[][] distance;
	double downPriceStd;
	double upPriceStd;
	double downUsedStd;
	double upUsedStd;

	public PriceScenarioData(int nTimeSteps, int nScenarios) {
		super(nTimeSteps);
		downprice = new double[nTimeSteps][nScenarios];
		upprice = new double[nTimeSteps][nScenarios];
		capdownprice = new double[nTimeSteps][nScenarios];
		capupprice = new double[nTimeSteps][nScenarios];
		expectedDownprice = new double[nTimeSteps];
		expectedUpprice = new double[nTimeSteps];
		expectedCapdownprice = new double[nTimeSteps];
		expectedCapupprice = new double[nTimeSteps];
		imbalanceprice = new double[nTimeSteps][nScenarios];
		proportionDownUsed = new double[nTimeSteps][nScenarios];
		expectedProportionDownUsed = new double[nTimeSteps];
		proportionUpUsed = new double[nTimeSteps][nScenarios];
		expectedProportionUpUsed = new double[nTimeSteps];
		scenarioProbability = new double[nScenarios];
		expectedImbalancePrice = new double[nTimeSteps];
		daPrice = new double[nTimeSteps];
		APdown = new Line[nTimeSteps][];
		ERdown = new Line[nTimeSteps][];
		APup = new Line[nTimeSteps][];
		ERup = new Line[nTimeSteps][];
		sortedIndicesDownCap = new int[nTimeSteps][];
		sortedIndicesDownReg = new int[nTimeSteps][];
		sortedIndicesUpCap = new int[nTimeSteps][];
		sortedIndicesUpReg = new int[nTimeSteps][];
		reset();
	}
	
	/**
	 * Concatenate two PriceScenarioData objects
	 * A new object is created. Does not alter this or other
	 */
	public PriceScenarioData concat(PriceScenarioData other) {
		PriceScenarioData result = new PriceScenarioData(this.getNTimeSteps(), this.getNScenarios() + other.getNScenarios());
		for(int t=0; t<result.getNTimeSteps(); t++) {
   		result.downprice[t] = Utils.concatArrays(this.downprice[t], other.downprice[t]);
   		result.upprice[t] = Utils.concatArrays(this.upprice[t], other.upprice[t]);
   		result.capdownprice[t] = Utils.concatArrays(this.capdownprice[t], other.capdownprice[t]);
   		result.capupprice[t] = Utils.concatArrays(this.capupprice[t], other.capupprice[t]);
   		result.imbalanceprice[t] = Utils.concatArrays(this.imbalanceprice[t], other.imbalanceprice[t]);
   		result.proportionDownUsed[t] = Utils.concatArrays(this.proportionDownUsed[t], other.proportionDownUsed[t]);
   		result.proportionUpUsed[t] = Utils.concatArrays(this.proportionUpUsed[t], other.proportionUpUsed[t]);
		}
		result.daPrice = this.daPrice;
		Arrays.fill(result.scenarioProbability, 1.0 / result.getNScenarios());
		result.calcExpected();
		result.reset();
		return result;
	}
	
	/**
	 * Return a new PriceScenarioData object that contains only the data from startT to endT
	 */
	public PriceScenarioData limit(int startT, int endT) {
		PriceScenarioData result = new PriceScenarioData(endT - startT, this.getNScenarios());
		for(int t=0; t<endT-startT; t++) {
   		result.downprice[t] = this.downprice[t+startT];
   		result.upprice[t] = this.upprice[t+startT];
   		result.capdownprice[t] = this.capdownprice[t+startT];
   		result.capupprice[t] = this.capupprice[t+startT];
   		result.imbalanceprice[t] = this.imbalanceprice[t+startT];
   		result.proportionDownUsed[t] = this.proportionDownUsed[t+startT];
   		result.proportionUpUsed[t] = this.proportionUpUsed[t+startT];
		}
		result.daPrice = this.daPrice;
		Arrays.fill(result.scenarioProbability, 1.0 / result.getNScenarios());
		result.calcExpected();
		result.reset();
		return result;
	}

	/**
	 * Reset the stored information in this object:
	 * 1) scenario ordering
	 * 2) scenario distance matrix
	 * 3) calculated standard deviations
	 */
	public void reset() {
		Arrays.fill(sortedIndicesDownCap, null);
		Arrays.fill(sortedIndicesDownReg, null);
		Arrays.fill(sortedIndicesUpCap, null);
		Arrays.fill(sortedIndicesUpReg, null);
		distance = null;
		downPriceStd = 0;
		upPriceStd = 0;
		downUsedStd = 0;
		upUsedStd = 0;
	}

	/**
	 * Create a new PriceScenarioData object with only the selected scenarios
	 */
	public PriceScenarioData filter(int[] scenarios) {
		int nTimeSteps = daPrice.length;
		PriceScenarioData result = new PriceScenarioData(nTimeSteps, scenarios.length);
		for(int i=0; i<scenarios.length; i++) {
			int ix = scenarios[i];
			for(int t=0; t<nTimeSteps; t++) {
				result.downprice[t][i] = downprice[t][ix];
				result.upprice[t][i] = upprice[t][ix];
				result.capdownprice[t][i] = capdownprice[t][ix];
				result.capupprice[t][i] = capupprice[t][ix];
				result.imbalanceprice[t][i] = imbalanceprice[t][ix];
				result.proportionDownUsed[t][i] = proportionDownUsed[t][ix];
				result.proportionUpUsed[t][i] = proportionUpUsed[t][ix];
			}
			result.scenarioProbability[i] = scenarioProbability[ix] * (scenarioProbability.length) / (scenarios.length);
		}
		result.expectedDownprice = expectedDownprice.clone();
		result.expectedUpprice = expectedUpprice.clone();
		result.expectedCapdownprice = expectedCapdownprice.clone();
		result.expectedCapupprice = expectedCapupprice.clone();
		result.expectedProportionDownUsed = expectedProportionDownUsed.clone();
		result.expectedProportionUpUsed = expectedProportionUpUsed.clone();
		result.expectedImbalancePrice = expectedImbalancePrice.clone();
		result.daPrice = daPrice.clone();
		result.APdown = APdown.clone();
		result.ERdown = ERdown.clone();
		result.APup = APup.clone();
		result.ERup = ERup.clone();
		return result;
	}
	
	public void setDownPrice(int t, int i, double p) {
		downprice[t][i] = p;
	}
	
	public double getDownPrice(int t, int i) {
		return downprice[t][i];
	}
	
	public void setCapDownPrice(int t, int i, double p) {
		capdownprice[t][i] = p;
	}
	
	public double getCapDownPrice(int t, int i) {
		return capdownprice[t][i];
	}
	
	public double getExpectedDownPrice(int t) {
		return expectedDownprice[t];
	}
	
	public double getExpectedCapDownPrice(int t) {
		return expectedCapdownprice[t];
	}
	
	public void setUpPrice(int t, int i, double p) {
		upprice[t][i] = p;
	}
	
	public double getUpPrice(int t, int i) {
		return upprice[t][i];
	}
	
	public void setCapUpPrice(int t, int i, double p) {
		capupprice[t][i] = p;
	}
	
	public double getCapUpPrice(int t, int i) {
		return capupprice[t][i];
	}
	
	public double getExpectedCapUpPrice(int t) {
		return expectedCapupprice[t];
	}
	
	public double getExpectedUpPrice(int t) {
		return expectedUpprice[t];
	}
	
	
	
	public void setImbalancePrice(int t, int i, double p) {
		imbalanceprice[t][i] = p;
	}
	
	public double getImbalancePrice(int t, int i) {
		return imbalanceprice[t][i];
	}
	
	public void setProportionDownUsed(int t, int i, double p) {
		proportionDownUsed[t][i] = p;
	}
	
	public double getProportionDownUsed(int t, int i) {
		return proportionDownUsed[t][i];
	}
	
	public double getExpectedProportionDownUsed(int t) {
		return expectedProportionDownUsed[t];
	}
	
	public void setProportionUpUsed(int t, int i, double p) {
		proportionUpUsed[t][i] = p;
	}
	
	public double getProportionUpUsed(int t, int i) {
		return proportionUpUsed[t][i];
	}
	
	public double getExpectedProportionUpUsed(int t) {
		return expectedProportionUpUsed[t];
	}
	
	public void setScenarioProbability(int i, double p) {
		scenarioProbability[i] = p;
	}
	
	public double getScenarioProbability(int i) {
		return scenarioProbability[i];
	}
	
	public void setExpectedImbalancePrice(int t, double p) {
		expectedImbalancePrice[t] = p;
	}
	
	public double getExpectedImbalancePrice(int t) {
		return expectedImbalancePrice[t];
	}

	public void calcExpected() {
		calcExpected(0, expectedImbalancePrice.length);
	}
	
	/**
	 * Calculates the expected/mean of the data from startT to endT and updates the values in-place
	 * 1) imbalance price
	 * 2) proportion of down reserves used
	 * 3) proportion of up reserves used
	 * 4) down price
	 * 5) up price
	 * 6) down capacity price
	 * 7) up capacity price
	 */
	public void calcExpected(int startT, int endT) {
		for(int t=startT; t<endT; t++) {
			final int tx = t;
			expectedImbalancePrice[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * imbalanceprice[tx][i]).sum();
			expectedProportionDownUsed[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * proportionDownUsed[tx][i]).sum();
			expectedProportionUpUsed[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * proportionUpUsed[tx][i]).sum();
			expectedDownprice[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * downprice[tx][i]).sum();
			expectedUpprice[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * upprice[tx][i]).sum();
			expectedCapdownprice[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * capdownprice[tx][i]).sum();
			expectedCapupprice[t] = IntStream.range(0,  getNScenarios()).mapToDouble(i -> scenarioProbability[i] * capupprice[tx][i]).sum();
		}
	}
	
	public void setExpectedDownPrice(int t, double p) {
		expectedDownprice[t] = p;
	}
	public void setExpectedUpPrice(int t, double p) {
		expectedUpprice[t] = p;
	}
	public void setExpectedCapDownPrice(int t, double p) {
		expectedCapdownprice[t] = p;
	}
	public void setExpectedCapUpPrice(int t, double p) {
		expectedCapupprice[t] = p;
	}
	public void setExpectedDownProportion(int t, double p) {
		expectedProportionDownUsed[t] = p;
	}
	public void setExpectedUpProportion(int t, double p) {
		expectedProportionUpUsed[t] = p;
	}
	public void setAPDown(int t, Line[] lines) {
		APdown[t] = lines;
	}
	public void setAPUp(int t, Line[] lines) {
		APup[t] = lines;
	}
	public void setERDown(int t, Line[] lines) {
		ERdown[t] = lines;
	}
	public void setERUp(int t, Line[] lines) {
		ERup[t] = lines;
	}
	
	public int getAPpieces() {
		return APdown[0].length;
	}
	
	public int getERpieces() {
		return ERdown[0].length;
	}

	public int getNScenarios() {
		return scenarioProbability.length;
	}
	
	public int getNTimeSteps() {
		return expectedImbalancePrice.length;
	}
	
	/**
	 * Get the scenarios ordered by down price from  high to low at time step t
	 */
	public int[] getScenariosOrderedByDownPrice(int t) {
		if(sortedIndicesDownReg[t] != null) return sortedIndicesDownReg[t];
		int[] sortedIndices = IntStream.range(0, downprice[t].length)
                .boxed().sorted((i, j) -> Double.compare(downprice[t][j], downprice[t][i]))
                .mapToInt(e -> e).toArray();
		sortedIndicesDownReg[t] = sortedIndices;
		return sortedIndices;
	}
	
	/**
	 * Get the scenarios ordered by up price from  high to low at time step t
	 */
	public int[] getScenariosOrderedByUpPrice(int t) {
		if(sortedIndicesUpReg[t] != null) return sortedIndicesUpReg[t];
		int[] sortedIndices = IntStream.range(0, upprice[t].length)
                .boxed().sorted((i, j) -> Double.compare(upprice[t][j], upprice[t][i]))
                .mapToInt(e -> e).toArray();
		sortedIndicesUpReg[t] = sortedIndices;
		return sortedIndices;
	}
	
	/**
	 * Get the scenarios ordered by capacity down price from  high to low at time step t
	 */
	public int[] getScenariosOrderedByDownCap(int t) {
		if(sortedIndicesDownCap[t] != null) return sortedIndicesDownCap[t];
		int[] sortedIndices = IntStream.range(0, capdownprice[t].length)
                .boxed().sorted((i, j) -> Double.compare(capdownprice[t][j], capdownprice[t][i]))
                .mapToInt(e -> e).toArray();
		sortedIndicesDownCap[t] = sortedIndices;
		return sortedIndices;
	}
	
	/**
	 * Get the scenarios ordered by capacity up price from  high to low at time step t
	 */
	public int[] getScenariosOrderedByUpCap(int t) {
		if(sortedIndicesUpCap[t] != null) return sortedIndicesUpCap[t];
		int[] sortedIndices = IntStream.range(0, capupprice[t].length)
                .boxed().sorted((i, j) -> Double.compare(capupprice[t][j], capupprice[t][i]))
                .mapToInt(e -> e).toArray();
		sortedIndicesUpCap[t] = sortedIndices;
		return sortedIndices;
	}
	
	/**
	 * Get the distance (RMSE) between two scenarios i and j considering only the data between startT and endT
	 */
	public double getScenarioDistance(int i, int j, int startT, int endT) {
		double distance = 0.0;
		double downpriceStd = getDownPriceStd();
		double uppriceStd = getUpPriceStd();
		double ptudownStd = getDownUsedStd();
		double ptuupStd = getUpUsedStd();
		for (int t = startT; t < endT; t++) {
			distance += Math.pow((getDownPrice(t, i) - getDownPrice(t, j)) / downpriceStd, 2);
			distance += Math.pow((getUpPrice(t, i) - getUpPrice(t, j)) / uppriceStd, 2);
			distance += Math.pow((getProportionDownUsed(t, i) - getProportionDownUsed(t, j)) / ptudownStd, 2);
			distance += Math.pow((getProportionUpUsed(t, i) - getProportionUpUsed(t, j)) / ptuupStd, 2);
		}
		distance = Math.sqrt(distance);
		return distance;
	}

	/**
	 * Get the standard deviation of data
	 */
	private double getStandardDeviation(double[][] data) {
		double sum = 0;
		int n = 0;
		for (int i = 0; i < data.length; i++) {
			for (int j = 0; j < data[i].length; j++) {
				sum += data[i][j];
				n++;
			}
		}
		double avg = sum / n;
		double std = 0;
		for (int i = 0; i < data.length; i++) {
			for (int j = 0; j < data[i].length; j++) {
				std += Math.pow(avg - data[i][j], 2);
			}
		}
		return Math.sqrt(std / (n - 1));
	}
	
	private double getDownPriceStd() {
		if (downPriceStd == 0)
			downPriceStd = getStandardDeviation(downprice);
		return downPriceStd;
	}

	private double getUpPriceStd() {
		if (upPriceStd == 0)
			upPriceStd = getStandardDeviation(upprice);
		return upPriceStd;
	}

	private double getDownUsedStd() {
		if (downUsedStd == 0)
			downUsedStd = getStandardDeviation(proportionDownUsed);
		return downUsedStd;
	}

	private double getUpUsedStd() {
		if (upUsedStd == 0)
			upUsedStd = getStandardDeviation(proportionUpUsed);
		return upUsedStd;
	}

	/**
	 * Get the n scenarios that are most similar to i in the range from startT to endT
	 */
	public int[] getMostSimilarScenarios(int i, int n, int startT, int endT) {
		double[] distance = new double[getNScenarios()];
		for (int j = 0; j < getNScenarios(); j++) {
			distance[j] = getScenarioDistance(i, j, startT, endT);
		}
		return IntStream.range(0, getNScenarios())
				.boxed().sorted((x, y) -> Double.compare(distance[x], distance[y]))
				.mapToInt(ele -> ele).filter(x -> x != i).limit(n).toArray();
	}

	public double getScenarioGroupDissimilarity(int startT, int endT) {
		double dissimilarity = 0;
		final int n = getNScenarios();
		for(int i=0; i<n; i++) {
			for(int j=i+1; j<n; j++) {
				dissimilarity += getScenarioDistance(i, j, startT, endT);
			}
		}
		return dissimilarity / ((n*(n-1) /2));
	}
	
	public double getIntraScenarioGroupDissimilarity(int[] scenarioGroupA, int[] scenarioGroupB, int startT, int endT) {
		double dissimilarity = 0;
		double maxDissimilarity = -Double.MAX_VALUE;
		for(int i=0; i<scenarioGroupA.length; i++) {
			for(int j=0; j<scenarioGroupB.length; j++) {
				double d = getScenarioDistance(scenarioGroupA[i] - 1, scenarioGroupB[j] - 1, startT, endT);
				dissimilarity += d;
				if(d>maxDissimilarity) maxDissimilarity = d;
			}
		}
		return dissimilarity / (scenarioGroupA.length * scenarioGroupB.length);
		//return maxDissimilarity;
	}

	/**
	 * Select the n most dissimilar scenarios from time step startT to endT by using fast forward selection
	 */
	public PriceScenarioData getFastForwardScenarioSelection(int startT, int endT, int n) {
		if (!isDistanceInitialized()) {
			distance = new double[getNScenarios()][getNScenarios()];
			for (int i = 0; i < getNScenarios(); i++) {
				for (int j = 0; j < getNScenarios(); j++) {
					distance[i][j] = getScenarioDistance(i, j, startT, endT);
				}
			}
		}
		return getFastForwardScenarioSelection(startT, endT, n, distance);
	}

	/**
	 * Select the n most dissimilar scenarios from time step startT to endT by using fast forward selection
	 */
	public PriceScenarioData getFastForwardScenarioSelection(int startT, int endT, int n, double[][] distance) {
		double[][] oDistance = new double[getNScenarios()][getNScenarios()];
		for (int i = 0; i < getNScenarios(); i++) {
			for (int j = 0; j < getNScenarios(); j++) {
				oDistance[i][j] = distance[i][j];
			}
		}
		List<Integer> selected = new ArrayList<Integer>(n);
		List<Integer> notSelected = IntStream.range(0, getNScenarios()).boxed().collect(Collectors.toList());
		
		for(int p=0; p<n; p++) {
			int w = notSelected.stream().min(Comparator.comparingDouble(ix ->
			notSelected.stream().mapToDouble(j -> distance[ix][j]).sum())).get();
			selected.add(w);
			notSelected.remove(Integer.valueOf(w));
			for (int i : notSelected) {
				for (int j : notSelected) {
					distance[i][j] = Math.min(distance[i][j], distance[i][w]);
				}
			}
		}
		double[] probs = new double[n];
		Arrays.fill(probs, 1.0 / getNScenarios());
		for (int i : notSelected) {
			int w = IntStream.range(0, n).boxed().min(Comparator.comparingDouble(j -> oDistance[i][selected.get(j)])).get();
			probs[w] += 1.0 / getNScenarios();
		}
		int[] scenarios = selected.stream().mapToInt(i -> i).toArray();
		PriceScenarioData result = filter(scenarios);
		result.scenarioProbability = probs;
		return result;
	}

	public void setDAPrice(int t, double daprice) {
		daPrice[t] = daprice;
	}
	
	public double getDAPrice(int t) {
		return daPrice[t];
	}

	public boolean isDistanceInitialized() {
		return distance != null;
	}

	public void initializeDistance(double[][] distance) {
		this.distance = distance;
	}

	public void replaceWithNoise(double std) {
		Random rand = new Random();
		for(int t=0; t<getNTimeSteps(); t++) {
			double imb = getExpectedImbalancePrice(t);
			for(int i=0; i<getNScenarios(); i++) {
				double down = imb - rand.nextGaussian() * std;
				double up =   imb + rand.nextGaussian() * std;
				setDownPrice(t, i, down);
				setUpPrice(t, i, up);
			}
		}
	}

	public PriceScenarioData getRandomSubset(int nScenarios, Random random) {
		if(nScenarios < getNScenarios()) {
   		int[] scenarios = IntStream.range(0, getNScenarios()).toArray();
   		Utils.shuffle(scenarios, random);
   		scenarios = Arrays.copyOfRange(scenarios, 0, nScenarios);
   		return filter(scenarios);
		}
		return this;
	}

	private double simpleSolutionValue(Loads loads, double ptu, int s, int startT) {
		double costs = 0;
		for (int e = 0; e < loads.getNLoads(); e++) {
			int _startT = Math.max(startT, loads.getArrivalT(e));
			int endT = Math.max(startT, loads.getDepartureT(e));
			double[] prices = new double[endT - _startT];
			for (int t = _startT; t < endT; t++)
				prices[t - _startT] = getImbalancePrice(t, s);
			Arrays.sort(prices);
			double nChargingSessions = Math.min(Math.max(0, loads.getMinimumSOC(e) - loads.getArrivalSOC(e))
					/ (loads.getMaximumChargingSpeed(e) * ptu), endT - _startT);
			for (int i = 0; i < nChargingSessions-1; i++) {
				costs += ptu * loads.getMaximumChargingSpeed(e) * prices[i];
			}
			if (nChargingSessions % 1.0 > 0 && nChargingSessions < endT - _startT)
				costs += (nChargingSessions % 1.0) * ptu * loads.getMaximumChargingSpeed(e) * prices[(int) Math.floor(nChargingSessions)];
		}
		return costs;
	}

	private void initializeDistance(Loads loads, double ptu, int t) {
		int n = getNScenarios();
		double[][] distance = new double[n][n];
		double[] result = new double[n];
		Arrays.stream(distance).forEach(a -> Arrays.fill(a, 0));
		for (int i = 0; i < n; i++) {
			result[i] = simpleSolutionValue(loads, ptu, i, t);
		}
		for (int i = 0; i < n - 1; i++) {
			for (int j = i + 1; j < n; j++) {
				distance[i][j] = Math.abs(result[i] - result[j]);
				distance[j][i] = Math.abs(result[i] - result[j]);
			}
		}
		initializeDistance(distance);
	}

	public PriceScenarioData reduceScenarioSet(Loads loads, double ptu, int t, int nScenarios) {
		if (!isDistanceInitialized())
			initializeDistance(loads, ptu, t);
		return getFastForwardScenarioSelection(t, loads.getLastT(), nScenarios);
	}

	@Override
	public PriceScenarioData clone() {
		return filter(IntStream.range(0, downprice[0].length).toArray());
	}
	
	public void writeToFile(String folder) throws IOException {
		CSVWriter.writeCsvFile(folder + "/price_down.csv", downprice, new String[] { "PTU", "Scenario" });
		CSVWriter.writeCsvFile(folder + "/price_up.csv", upprice, new String[] { "PTU", "Scenario" });
		if(Math.abs(Utils.sum(capdownprice)) > 1e-4)
			CSVWriter.writeCsvFile(folder + "/cprice_down.csv", capdownprice, new String[] { "PTU", "Scenario" });
		if(Math.abs(Utils.sum(capupprice)) > 1e-4)
			CSVWriter.writeCsvFile(folder + "/cprice_up.csv", capupprice, new String[] { "PTU", "Scenario" });
	}
}

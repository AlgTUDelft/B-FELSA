package nl.tudelft.alg.fcc.solution;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * Heuristic/Dynamic-Programming Approach to the FlexibleLoadProblem.
 * Still in development - may be buggy.
 * Up reserves and day-ahead not implemented.
 */
public class CurveApproxModel implements ISolveModel {
	private final static int mPTU = 10; //The granularity of this method. (now set to 1/10th of a PTU)
	FlexibleLoadProblem problem;
	DecisionVariables dec;
	
	public CurveApproxModel(FlexibleLoadProblem p) {
		super();
		problem = p;
	}
	
	@Override
	public void initialize(ISolver solver) {}
	
	/**
	 * Combine two partial solutions (curves) into one solution
	 */
	private Curve combineCurves(Curve curve, Curve curve2) {
		return curve.combine(curve2);
	}

	/**
	 * Get a curve (partial solution) that has already decided variables fixed (ie. committed reserve bids)
	 * @param e the load id
	 * @param t the time step
	 */
	private Curve fixedCurve(int e, int t) {
		final double E = 1e-5;
		DecisionVariables dec = problem.getVars();
		Market market = problem.getMarket();
		double[] imb = new double[mPTU + 1];
		double[] cost = new double[mPTU + 1];
		Double[] down = new Double[mPTU + 1];
		Double[] up = new Double[mPTU + 1];
		Arrays.fill(cost, Double.POSITIVE_INFINITY);
		Arrays.fill(up, null);
		Arrays.fill(down, null);
		if (dec.rcd[e][t] > E) {			
			int[] scenarios = IntStream.range(0, market.getNScenarios()).filter(i -> market.getDownRegulatingPrice(t, i) <= dec.bd[e][t]).toArray();
			double probsum = Arrays.stream(scenarios).mapToDouble(i -> market.getScenarioProbability(i)).sum();
			double c = Arrays.stream(scenarios).mapToDouble(i -> market.getDownRegulatingPrice(t, i) * market.getScenarioProbability(i)).sum() / probsum;
			double prob = Arrays.stream(scenarios).mapToDouble(i -> market.getDownReserveProportion(t, i) * market.getScenarioProbability(i)).sum();
					//* scenarios.length / market.getNScenarios();
			if(probsum == 0) c = 0;
			int n = (int) (Math.floor(prob * mPTU));
			cost[n] = (n * c) / mPTU;
			down[n] = dec.bd[e][t];
		} else if (dec.rcu[e][t] > E) {
			int[] scenarios = IntStream.range(0, market.getNScenarios()).filter(i -> market.getUpRegulatingPrice(t, i) >= dec.bu[e][t]).toArray();
			double probsum = Arrays.stream(scenarios).mapToDouble(i -> market.getScenarioProbability(i)).sum();
			double c = Arrays.stream(scenarios).mapToDouble(i -> market.getUpRegulatingPrice(t, i) * market.getScenarioProbability(i)).sum() / probsum;
			double prob = 1 - Arrays.stream(scenarios).mapToDouble(i -> market.getUpReserveProportion(t, i) * market.getScenarioProbability(i)).sum();
					//* scenarios.length / market.getNScenarios();
			if(probsum == 0) c = 0;
			int n = (int) (Math.floor(prob * mPTU));
			cost[n] = getExpectedPrice(t, 1) - ((mPTU-n) * c) / mPTU;
			imb[n] = 1;
			up[n] = dec.bu[e][t];
		} else {
			imb = IntStream.range(0, mPTU + 1).mapToDouble(i -> ((double) i) / mPTU).toArray();
			cost = IntStream.range(0, mPTU + 1).mapToDouble(i -> getExpectedPrice(t, ((double) i) / mPTU)).toArray();
		}
		return new Curve(imb, down, up, cost);
	}

	/**
	 * Get a cost curve (partial solution) for time step t.
	 * Determine when it is profitable to provide reserves and when to buy from the imbalance market
	 * @param t time step
	 */
	private Curve findCurve(int t) {
		Market market = problem.getMarket();
		int[] order = market.getScenariosOrderedByDownRegulatingPrice(t); //from high to low
		double sum = 0;
		double eSum = 0;
		double[] cost = new double[mPTU + 1];
		double[] imb = new double[mPTU + 1];
		Double[] down = new Double[mPTU + 1];
		Double[] up = new Double[mPTU + 1];
		Arrays.fill(imb, 0);
		Arrays.fill(down, null);
		Arrays.fill(up, null);
		int i = 1;
		double cap = 1.0 / mPTU;
		if(problem.getConfig().hasReserves()) {
   		//Loop over the scenario's ordered by down regulating price
   		for(int s=1; s <= market.getNScenarios(); s++) {
   			int sx = order[market.getNScenarios() - s];
   			sum += market.getDownRegulatingPrice(t, sx); //Sum the down regulating prices for calculating the expected value
   			eSum += market.getDownReserveProportion(t, sx); //Sum the down regulating proportions for calculating the expected value
   			double prob = eSum / s * (((double) s) / market.getNScenarios()); //The expected value of reserves being used, based on the scenario's seen up to now
   			if (prob >= cap) { //If this expected value exceeds at least cap, consider providing reserves
   				cost[i] = sum / s * cap; //Calculate the expected costs
   				down[i] = market.getDownRegulatingPrice(t, sx);
   				if (getExpectedPrice(t, cap) <= cost[i]) { //If imbalance charging is cheaper, don't provide reserves
   					cost[i] = getExpectedPrice(t, cap);
   					down[i] = null;
   					imb[i] = cap;
   				}
   				i++; 
   				cap = ((double) i) / mPTU;
   			}
   		}
		}
		for (; i <= mPTU; i++) { //Fill the remaining part of the curve with imbalance purchases
			cap = ((double) i) / mPTU;
			cost[i] = getExpectedPrice(t, cap);
			imb[i] = cap;
		}
		if(problem.getConfig().hasReserves()) {
   		order = market.getScenariosOrderedByUpRegulatingPrice(t); //from high to low
   		sum = 0;
   		eSum = 0;
   		cap = ((double) i--) / mPTU;
   		for(int s=1; s <= market.getNScenarios(); s++) {
   			int sx = order[s-1];
   			sum += market.getUpRegulatingPrice(t, sx); //Sum the up regulating prices for calculating the expected value
   			eSum += market.getUpReserveProportion(t, sx); //Sum the up regulating proportions for calculating the expected value
   			double prob = 1.0 - eSum / s * (((double) s) / market.getNScenarios()); //The expected value of reserves being used, based on the scenario's seen up to now
   			while(cap > prob) cap = ((double) --i) / mPTU;
   			double expcost = getExpectedPrice(t, 1) - sum / s * (1-prob); 
   			if(cost[i] > expcost) {	
   				cost[i] = expcost; //Calculate the expected costs
   				down[i] = null;
   				up[i] = market.getUpRegulatingPrice(t, sx);
   				imb[i] = 1;
   			}
   		}
		}
		
		return new Curve(imb, down, up, cost);
	}
	
	private double getExpectedPrice(int t, double amount)  {
		int h = problem.getMarket().PTUtoH(t);
		double avgChgSpeed = Arrays.stream(problem.getLoads().getLoads()).filter(l -> l.isAvailable(t+problem.getStartT()))
			.mapToDouble(l -> l.getChargingSpeed(t+problem.getStartT())).average().orElse(0);
		if(avgChgSpeed != 0)
			amount -= problem.getVars().pda[h] / avgChgSpeed;
		return amount * problem.getMarket().getExpectedImbalancePrice(t); 
	}

	@Override
	public void printSolution() {
		problem.getVars().printSolution(problem);
	}

	/**
	 * Calculate the DA-purchase decisions. Buy all your required energy DA
	 */
	private void calcDayAheadPurchase() {
		if(!problem.getMarket().hasDayAhead()) return;
		if(problem.getMarket().isDayAheadFixed()) return;
		DecisionVariables dec = problem.getVars();
		Loads loads = problem.getLoads();
		int PTUsPerHour = (int) (1.0 / problem.getMarket().getPTU());
		for(int i=0; i<loads.getNLoads(); i++) {
			int startT = loads.getStartT(i);
			int endT = loads.getEndT(i);
			int startH = (startT + PTUsPerHour - 1) / PTUsPerHour;
			int endH = endT / PTUsPerHour;
			//Find the imbalance and da-prices and sort them from low to high
			double[] daprices = new double[endH-startH];
			double[] imbprices = new double[endT-startT];
			for (int t = startH; t < endH; t++)
				daprices[t - startH] = problem.getMarket().getDAPriceByHour(t);
			for (int t = startT; t < endT; t++)
				imbprices[t - startT] = problem.getMarket().getExpectedImbalancePrice(t);
			int[] hourOrder = IntStream.range(0, daprices.length).boxed()
					.sorted((j, k) -> new Double(daprices[j]).compareTo(daprices[k]) ).mapToInt(j -> j).toArray();
			int[] imbOrder = IntStream.range(0, imbprices.length).boxed()
					.sorted((j, k) -> new Double(imbprices[j]).compareTo(imbprices[k]) ).mapToInt(j -> j).toArray();
			//Find the amount of energy to be bought DA
			double nChargingSessions = Math.min(Math.max(0, loads.getRequiredChargeAmount(i))
					/ (loads.getMaximumChargingSpeed(i) * loads.getChargingEfficiency() ), endH - startH - 1);
			for(int imbPTUs = 0; imbPTUs < startT - endT && daprices[hourOrder[(int) Math.floor(nChargingSessions)]] > imbOrder[imbPTUs]; imbPTUs++)
				nChargingSessions -= problem.getMarket().getPTU();
			//Set the DA purchase decision
			for (int j = 0; j < nChargingSessions-1; j++)
				dec.pda[hourOrder[j]] += loads.getMaximumChargingSpeed(i);
			if (nChargingSessions % 1.0 > 0 && nChargingSessions < endH - startH)
				dec.pda[hourOrder[(int) Math.floor(nChargingSessions)]] += (nChargingSessions % 1.0) * loads.getMaximumChargingSpeed(i);
		}
	}

	@Override
	public void solve() {
		calcDayAheadPurchase();
		DecisionVariables dec = problem.getVars();
		Loads loads = problem.getLoads();
		int nFixed = problem.getMarket().getFixedPTUs();
		Curve[] curves = new Curve[problem.getNTimeSteps()];
		Arrays.fill(curves, null);
		for (int e = 0; e < problem.getNLoads(); e++) {
			int startT = loads.getStartT(e);
			int endT = loads.getEndT(e);
			for (int t = startT; t < endT; t++) {
				if (curves[t] == null)
					curves[t] = findCurve(t);
			}
			Curve curve = startT < nFixed && true ? fixedCurve(e, startT) : curves[startT];
			for (int t = startT + 1; t < endT; t++) {
				curve = combineCurves(curve, t < nFixed && true ? fixedCurve(e, t) : curves[t]);
			}
			curve.makeDecisions(dec, e);
		}
		dec.imbalancePurchaseFromCharging();
	}

	/**
	 * Curve that represents a (partial) solution.
	 * Contains a dynamic programming solution.
	 * Contains a (partial) solution for every amount of charge that is still needed (on the nT axis)
	 * The solution describes the decisions per PTU that is considered (on the nPTU axis)
	 */
	private class Curve {
		double[][] imb;
		Double[][] down;
		Double[][] up;
		double[] cost;
		
		/**
		 * Create a curve instance
		 * @param imb the amount of imbalance-charge to buy (for increasing amounts of demand)
		 * @param down the down reserve bids (price) to commit (for increasing amounts of demand)
		 * @param up the up reserve bids (price) to commit (for increasing amounts of demand)
		 * @param cost the costs to expect based on the other decisions (for increasing amounts of demand)
		 */
		public Curve(double[] imb, Double[] down, Double[] up, double[] cost) {
			this(cost.length);
			for(int i=0; i<getMaxChargingTime(); i++) {
				this.imb[i][0] = imb[i];
				this.down[i][0] = down[i];
				this.up[i][0] = up[i];
			}
			this.cost = cost;
		}

		/**
		 * Create a curve instance for one PTU
		 * @param nT the granularity of the decisions
		 */
		public Curve(int nT) {
			this(1, nT);
		}

		/**
		 * Create a curve instance
		 * @param nPTUs the number of PTUs that this solution covers
		 * @param nT the granularity of the decisions (within one PTU)
		 */
		private Curve(int nPTUs, int nT) {
			imb = new double[nT][nPTUs];
			down = new Double[nT][nPTUs];
			up = new Double[nT][nPTUs];
			cost = new double[nT];
		}
		
		private int getNPTUs() {
			return imb[0].length;
		}

		private int getMaxChargingTime() {
			return cost.length;
		}

		/**
		 * Get the decisions from this curve and store them
		 * @param dec the DecisionVariables object to store the decisions in
		 * @param e the load id for which to store the decisions
		 */
		public void makeDecisions(DecisionVariables dec, int e) {
			Loads loads = problem.getLoads();
			int startT = Math.max(0, loads.getStartT(e));
			int endT = loads.getEndT(e);
			double maxChgSpeed = loads.getMaximumChargingSpeed(e);
			double efficiency = loads.getChargingEfficiency();
			double reqChg = loads.getMinimumSOC(e) - loads.getArrivalSOC(e);
			int n = (int) Math.ceil(mPTU * reqChg / (efficiency * maxChgSpeed * problem.getMarket().getPTU()));
			n = Math.max(0, Math.min(getMaxChargingTime() - 1, n)); //Determine the amounts of time units needed to charge this load
			final int _n = n;
			if(cost[n] == Double.POSITIVE_INFINITY)
				n = IntStream.range(0, getMaxChargingTime()-1).filter(i -> cost[i] != Double.POSITIVE_INFINITY).boxed().min(Comparator.comparingInt(i -> Math.abs(i-_n))).orElse(n);
			for(int t=0; t<endT-startT; t++) {
				dec.p[e][startT + t] = imb[n][t] * maxChgSpeed;
				if (startT + t < problem.getMarket().getFixedPTUs() && cost[n] == Double.POSITIVE_INFINITY) {
					dec.p[e][startT + t] = Math.min(maxChgSpeed-dec.rcd[e][startT + t], Math.max(dec.rcu[e][startT + t], dec.p[e][startT + t]));
					continue;
				}
				assert !(down[n][t] != null && up[n][t] != null);
				if(down[n][t] != null) {
					dec.bd[e][startT + t] = down[n][t];
					dec.rcd[e][startT + t] = maxChgSpeed;
				} else {
					dec.rcd[e][startT + t] = 0;
				}
				if (up[n][t] != null) {
					dec.bu[e][startT + t] = up[n][t];
					dec.rcu[e][startT + t] = maxChgSpeed;
				} else {
					dec.rcu[e][startT + t] = 0;
				}
			}
		}

		/**
		 * Combine two curves. For every amount of demand, choose the cheapest decision combination from the two curves
		 * @param c the curve to combine this with
		 * @return the combined curve
		 */
		public Curve combine(Curve c) {
			int nPTUs = getNPTUs() + c.getNPTUs();
			int nT = getMaxChargingTime() + c.getMaxChargingTime() - 1;
			Curve r = new Curve(nPTUs, nT);
			for (int i = 0; i < nT; i++) {
				int best1 = -1;
				int best2 = -1;
				for (int j = 0; j <= i; j++) {
					int k = i - j;
					if (j >= getMaxChargingTime() || k >= c.getMaxChargingTime()) continue;
					if (best1 == -1 || best2 == -1 || cost[j] + c.cost[k] < cost[best1] + c.cost[best2]) {
						best1 = j;
						best2 = k;
					}
				}
				r.cost[i] = cost[best1] + c.cost[best2];
				assert !Double.isNaN(r.cost[i]);
				for(int j=0; j<getNPTUs(); j++) {
					r.down[i][j] = down[best1][j];
					r.up[i][j] = up[best1][j];
					r.imb[i][j] = imb[best1][j];
				}
				for (int j = 0; j < c.getNPTUs(); j++) {
					r.down[i][getNPTUs() + j] = c.down[best2][j];
					r.up[i][getNPTUs() + j] = c.up[best2][j];
					r.imb[i][getNPTUs() + j] = c.imb[best2][j];
				}
			}
			return r;
		}

	}

	@Override
	public boolean isSolvable() {
		return true;
	}

}

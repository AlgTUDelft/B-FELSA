package nl.tudelft.alg.fcc.solution;

import java.util.Arrays;

import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.utils.Utils;

/**
 * MaxReg and Price algorithm from E. Sortomme, M. A. El-Sharkawi, 
 * Optimal charging strategies for unidirectional vehicle-to-grid, 
 * IEEE Transactions on Smart Grid 2 (2010) 131–138.
 * 
 * Grid constraints are not considered
 * V2G is not considered
 */
public class SortommeModel implements ISolveModel{
	FlexibleLoadProblem problem;
	DecisionVariables dec;
	boolean calcDA;
	
	public SortommeModel(FlexibleLoadProblem p) {
		super();
		problem = p;
	}

	@Override
	public void printSolution() {
		problem.getVars().printSolution(problem);
	}

	@Override
	public void initialize(ISolver solver) throws SolverException {
		calcDA = problem.getMarket().hasDayAhead() && !problem.getConfig().isDayAheadFixed();
	}

	@Override
	public boolean isSolvable() {
		return true;
	}

	protected double[][] getPOP() {
		String modelSetting = problem.getConfig().getModelSetting();
		double[][] pop;
		if(modelSetting.equals("price"))
			pop = getPriceBasedPOP();
		else if(modelSetting.equals("maxreg"))
			pop = getMaxRegBasedPOP();
		else pop = null;
		return makePOPFinal(pop);
	}
	
	/**
	 * Update POP to stay within 
	 * - charging limits
	 * - committed reserve limits
	 * - battery capacity limits
	 */
	protected double[][] makePOPFinal(double[][] pop) {
		final Loads loads = problem.getLoads();
		final double ptu = problem.getMarket().getPTU();
		final double eta = loads.getChargingEfficiency();
		for(int e=0; e<pop.length; e++) {
			for(int t=0; t<pop[e].length; t++) {
				pop[e][t] = Math.max(0, Math.min(pop[e][t], loads.getChargingSpeed(e, t)));
			}
			double remaining = Math.max(0,loads.getMinimumSOC(e) - loads.getArrivalSOC(e));
			for(int t=0; t<problem.getNTimeSteps(); t++) {
				pop[e][t] = Math.min(pop[e][t], (remaining / ptu) / eta);
				if(problem.getMarket().hasReserves() && t < problem.getMarket().getFixedPTUs()) {
					pop[e][t] = Math.min(pop[e][t], loads.getChargingSpeed(e, t) - problem.getVars().rcd[e][t]);
					pop[e][t] = Math.max(pop[e][t], problem.getVars().rcu[e][t]);		
				}
				remaining = Math.max(0, remaining - pop[e][t] * ptu * eta);
			}
		}
		return pop;
	}

	/**
	 * The MaxReg algorithm 
	 */
	protected double[][] getMaxRegBasedPOP() {
		Loads loads = problem.getLoads();
		double ptu = problem.getMarket().getPTU();
		double eta = loads.getChargingEfficiency();
		double[][] pop = new double[problem.getNLoads()][problem.getNTimeSteps()];
		for(int e=0; e<problem.getNLoads(); e++) {
   		int H = loads.getEndT(e) - loads.getStartT(e);
   		if(H == 0) Arrays.fill(pop[e], 0);
   		else {
   			double remaining = Math.max(0, loads.getMinimumSOC(e) - loads.getArrivalSOC(e));
   			for(int t=0; t<pop[e].length; t++) {
   				pop[e][t] = Math.min(loads.getChargingSpeed(e, t), ((remaining / eta) / ((H-t)*ptu)));
   				remaining = Math.max(0, remaining - pop[e][t] * ptu * eta);
   			}
   		}
		}
		return pop;
	}

	/**
	 * The Price algorithm 
	 */
	protected double[][] getPriceBasedPOP() {
		double[][] pop = new double[problem.getNLoads()][problem.getNTimeSteps()];
		double[] prices = new double[problem.getNTimeSteps()];
		for(int t=0; t<prices.length; t++) {
			prices[t] = problem.getMarket().getExpectedImbalancePrice(t);
		}
		double maxPrice = Utils.max(prices);
		double minPrice = Utils.min(prices);
		for(int e=0; e<problem.getNLoads(); e++) {
   		if(maxPrice == minPrice) Arrays.fill(pop[e], 1);
   		else {
      		for(int t=0; t<pop[e].length; t++) {
      			pop[e][t] = (maxPrice - prices[t]) / (maxPrice - minPrice) * problem.getLoads().getChargingSpeed(e, t);
      		}
   		}
		}
		return pop;
	}
	
	@Override
	public void solve() throws SolverException {
		if(calcDA) {
			FastImbalanceModel.calcDayAheadPurchase(problem);
			return;
		}
		DecisionVariables dec = problem.getVars();
		Loads loads = problem.getLoads();
		double[][] pop = getPOP();
		double ptu = problem.getMarket().getPTU();
		double eta = loads.getChargingEfficiency(); 
		for(int e=0; e<problem.getNLoads(); e++) {
			double maxRemaining = Math.max(0, loads.getMinimumSOC(e) - loads.getArrivalSOC(e));
			double minRemaining = maxRemaining;
			for(int t=0; t<problem.getNTimeSteps(); t++) {
				double bidDown = getPriceDownBid(t);
				double bidUp = getPriceUpBid(t);
				dec.p[e][t] = pop[e][t];
				maxRemaining = Math.max(0, maxRemaining - pop[e][t] * ptu * eta);
				//Bid the next reserve bid in such a way that in worst cases the minimum required SOC is reached and the battery capacity is not violated
				if(problem.getMarket().hasReserves() && t >= problem.getMarket().getFixedPTUs() && t <= problem.getConfig().getOriginalFixedPTUs()) {
					dec.bd[e][t] = bidDown;
					dec.bu[e][t] = bidUp;
					dec.rcd[e][t] = Math.max(0, Math.min(loads.getChargingSpeed(e, t) - pop[e][t], 
						(maxRemaining / ptu) / eta - pop[e][t]));
					double maxRemainingCharge = (problem.getNTimeSteps() - 1 - t) * loads.getMaximumChargingSpeed(e) * ptu * eta;
					dec.rcu[e][t] = Math.max(0, Math.min(maxRemainingCharge - minRemaining, pop[e][t]));
				} else if(t > problem.getConfig().getOriginalFixedPTUs()) {
					dec.p[e][t] = Math.min(pop[e][t], (maxRemaining / ptu) / eta);
				}
				maxRemaining = Math.max(0, maxRemaining - dec.rcd[e][t] * ptu * eta);
				minRemaining = Math.max(0, minRemaining - pop[e][t] * ptu * eta);
				minRemaining = Math.max(0, minRemaining + dec.rcu[e][t] * ptu * eta);
			}
		}
		dec.imbalancePurchaseFromCharging();
	}
	
	private double getPriceDownBid(int t) {
		if(problem.getMarket().hasCapacityPayments()) return 0;
		return 1000;
	}

	private double getPriceUpBid(int t) {
		if(problem.getMarket().hasCapacityPayments()) return 0;
		return -1000;
	}
	
}

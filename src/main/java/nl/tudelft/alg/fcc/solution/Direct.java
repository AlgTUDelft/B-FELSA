package nl.tudelft.alg.fcc.solution;

import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * The ADirect class solves the FlexibleLoadProblem by direct charging.
 * Grid constrains are not considered.
 * V2G is not considered.
 * The Day-ahead market is not considered.
 */
public class Direct implements ISolveModel {
	FlexibleLoadProblem problem;
	DecisionVariables dec;
	
	public Direct(FlexibleLoadProblem p) {
		super();
		problem = p;
	}
	
	@Override
	public void initialize(ISolver solver) {}

	/**
	 * Solves the direct charging problem
	 * @return an array [loads, time steps] with the charge amounts 
	 */
	private double[][] getDirectChargingAmount() {
		Loads loads = problem.getLoads();
		Market market = problem.getMarket();
		double[][] p = new double[problem.getNLoads()][problem.getNTimeSteps()];
		for(int e=0; e<problem.getNLoads(); e++) {
			double soc = loads.getArrivalSOC(e);
			int t=loads.getStartT(e);
			while(soc < loads.getMinimumSOC(e)) {
				double inc = Math.min(loads.getChargingSpeed(e,t), 
					(loads.getMinimumSOC(e) - soc) / loads.getChargingEfficiency() / market.getPTU());
				soc += inc * loads.getChargingEfficiency() * market.getPTU();
				p[e][t] = inc;
				t++;
			}
		}
		return p;
	}
	
	@Override
	public void printSolution() {
		problem.getVars().printSolution(problem);
	}


	@Override
	public void solve() {
		double[][] p = getDirectChargingAmount();
		problem.getVars().p = p;
		problem.getVars().imbalancePurchaseFromCharging();
	}

	@Override
	public boolean isSolvable() {
		return true;
	}

}

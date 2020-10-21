package nl.tudelft.alg.fcc.solution;

import java.util.Arrays;
import java.util.stream.IntStream;

import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * The simple passive imbalance charging model for multiple flexible loads 
 */
public class FastImbalanceModel implements ISolveModel {
	FlexibleLoadProblem problem;
	DecisionVariables dec;
	
	public FastImbalanceModel(FlexibleLoadProblem p) {
		super();
		problem = p;
	}
	
	@Override
	public void initialize(ISolver solver) {}


	
	@Override
	public void printSolution() {
		problem.getVars().printSolution(problem);
	}

	/**
	 * Calculate the DA-purchase decisions. Buy all your required energy DA
	 */
	public static void calcDayAheadPurchase(FlexibleLoadProblem problem) {
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
			for(int imbPTUs = 0; imbPTUs < endT - startT && nChargingSessions > 0 && daprices[hourOrder[(int) Math.floor(nChargingSessions)]] > imbprices[imbOrder[imbPTUs]]; imbPTUs++)
				nChargingSessions = Math.max(0, nChargingSessions - problem.getMarket().getPTU());
			//Set the DA purchase decision
			for (int j = 0; j <= nChargingSessions-1; j++)
				dec.pda[hourOrder[j]] += loads.getMaximumChargingSpeed(i);
			if (nChargingSessions % 1.0 > 0 && nChargingSessions < endH - startH)
				dec.pda[hourOrder[(int) Math.floor(nChargingSessions)]] += (nChargingSessions % 1.0) * loads.getMaximumChargingSpeed(i);
		}
	}
	
	/**
	 * Update charging amounts based on the expected costs (costs needed to pay for deviating from DA plan)
	 */
	private void setChargingAmounts() {
		DecisionVariables dec = problem.getVars();
		Loads loads = problem.getLoads();
		Market market = problem.getMarket();
		if(market.considerImbalance()) {
			double[]  imbprices = IntStream.range(0, problem.getNTimeSteps()).mapToDouble(t -> getExpectedPrice(t)).toArray();
			int[] imbOrder = IntStream.range(0, imbprices.length).boxed()
					.sorted((j, k) -> new Double(imbprices[j]).compareTo(imbprices[k]) ).mapToInt(j -> j).toArray();
			for(int i=0; i<loads.getNLoads(); i++) {
				int startT = loads.getStartT(i);
				int endT = loads.getEndT(i);
				double nChargingSessions = Math.min(Math.max(0, loads.getRequiredChargeAmount(i))
						/ (loads.getMaximumChargingSpeed(i) * loads.getChargingEfficiency() * market.getPTU()), endT - startT);
				int index = 0;
				while(nChargingSessions > 0) {
					while(index < imbOrder.length && (imbOrder[index] < startT || imbOrder[index] >= endT)) index++;
					if(index == imbOrder.length) break;
					double delta = Math.min(1.0, nChargingSessions);
					dec.p[i][imbOrder[index]] = delta * loads.getMaximumChargingSpeed(i);
					nChargingSessions -= delta;
					index++;
				}
			}
		}
	}
	
	/**
	 * Returns the expected costs at PTU t for deviating from the DA plan 
	 */
	private double getExpectedPrice(int t)  {
		double amount = 1.0;
		int h = problem.getMarket().PTUtoH(t);
		double avgChgSpeed = Arrays.stream(problem.getLoads().getLoads()).filter(l -> l.isAvailable(t+problem.getStartT()))
			.mapToDouble(l -> l.getChargingSpeed(t+problem.getStartT())).average().orElse(0);
		if(avgChgSpeed != 0)
			amount -= problem.getVars().pda[h] / avgChgSpeed;
		return amount * problem.getMarket().getExpectedImbalancePrice(t); 
	}

	@Override
	public void solve() {
		calcDayAheadPurchase(problem);
		setChargingAmounts();
		problem.getVars().imbalancePurchaseFromCharging();
	}

	

	@Override
	public boolean isSolvable() {
		if(!problem.getMarket().considerImbalance()) return false;
		return true;
	}

}

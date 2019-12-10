package nl.tudelft.alg.fcc.solution.mip;

import java.util.stream.IntStream;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

public class Heuristic extends OptimalPriceModel {

	public Heuristic(FlexibleLoadProblem p) {
		super(p);
	}

	@Override
	protected void setFirstFixed() {
		super.setFirstFixed();
		DecisionVariables d = problem.getVars();
		Loads loads = getLoads();
		LinExp left, right;
		for(int t=0; t<getMarket().getFixedPTUs(); t++) {
			if(t >= problem.getNTimeSteps()) break;
			for(int e=0; e<getLoads().getNLoads(); e++) {
				//the provide down and up ward reserves is as committed
				left = getPc(e, t).addTerm(d.rcd[e][t]);
				right = new LinExp(loads.getChargingSpeed(e, t));
				addConstraint(left, right, CMP.SMALLEREQ, "fixResD" + e + "_" + t);
				left = getPc(e, t);
				right = new LinExp(d.rcu[e][t]);
				addConstraint(left, right, CMP.LARGEREQ, "fixResU" + e + "_" + t);
			}
		}
	}


	public double getExpectedPrice() {
		DecisionVariables d = problem.getVars();
		Market market = getMarket();
		double sumP = 0;
		double sumE = 0;
		for (int t = 0; t < nTimeSteps; t++) {
			sumP += d.pimb[t] * market.getPTU() * market.getExpectedImbalancePrice(t);
			sumE += d.pimb[t] * market.getPTU();
			sumP += d.pda[market.PTUtoH(t)] * market.getPTU() * market.getDAPrice(t);
			sumE += d.pda[market.PTUtoH(t)] * market.getPTU();
		}
		if (sumE == 0) return 0;
		return sumP / sumE;
	}

	@Override
	public void writeSolution(IMIPSolver solver) throws SolverException {
		super.writeSolution(solver);
		if(!getConfig().hasReserves()) return;
		DecisionVariables d = problem.getVars();
		Loads loads = getLoads();
		double price = getExpectedPrice();		
		for (int e = 0; e < nLoads; e++) {
			int startT = Math.max(getMarket().getFixedPTUs(), loads.getStartT(e));
			int endT = loads.getEndT(e);
			if(startT > endT) continue;
			double[] imbprices = new double[endT-startT];
			for (int t = startT; t < endT; t++)
				imbprices[t - startT] = getMarket().getExpectedImbalancePrice(t);
			int[] imbOrder = IntStream.range(0, imbprices.length).boxed()
					.sorted((j, k) -> new Double(imbprices[j]).compareTo(imbprices[k]) ).mapToInt(j -> j).toArray();
			for(int t=getMarket().getFixedPTUs(); t<endT; t++) {
				d.rcd[e][t] = 0;
				d.rcu[e][t] = 0;
			}
			int maxDown = getMaxDownReservePTUs(e);
			int resDown = 0;
			for(int t=0; resDown < maxDown && t < endT - startT; t++) {
				int tx = imbOrder[t] + startT;
				if (d.p[e][tx] < loads.getChargingSpeed(e, tx)) {
   				d.rcd[e][tx] = loads.getChargingSpeed(e, tx) - d.p[e][tx];
   				d.bd[e][tx] = 0.8 * price;
   				resDown++;
   			} else {
   				d.rcd[e][tx] = 0;
   			}
			}
			int maxUp = getMaxUpReservePTUs(e);
			int resUp = 0;
			for(int t=0; resUp < maxUp && t < endT - startT; t++) {
				int tx = imbOrder[imbOrder.length-1-t] + startT;
   			if (d.p[e][tx] > 0) {
   				d.rcu[e][tx] = d.p[e][tx];
   				d.bu[e][tx] = 1 * price;
   				resUp++;
   			} else {
   				d.rcu[e][tx] = 0;
   			}
			}
		}
	}
	
	private int getMaxDownReservePTUs(int e) {
		Loads loads = problem.getLoads();
		DecisionVariables d = problem.getVars();
		double ptu = problem.getMarket().getPTU();
		double margin = (loads.getBatteryCapacity(e) - loads.getMinimumSOC(e)) / (loads.getChargingEfficiency() * ptu);
		int startT = Math.max(0, loads.getStartT(e));
		int endT = loads.getEndT(e);
		for (int t = startT; t < endT; t++) {
			if(t <= getMarket().getFixedPTUs())
				margin -= (d.rcd[e][t] + d.rdd[e][t]) * ptu;
			if(d.rcu[e][t] + d.rdu[e][t] == 0 && (t > getMarket().getFixedPTUs() || d.rcd[e][t] + d.rdd[e][t] == 0))
				margin += loads.getMaximumChargingSpeed(e) * ptu;
		}		
		return (int) Math.floor(margin / (loads.getMaximumChargingSpeed(e) * ptu));
	}
	
	private int getMaxUpReservePTUs(int e) {
		Loads loads = problem.getLoads();
		DecisionVariables d = problem.getVars();
		double ptu = problem.getMarket().getPTU();
		double margin = 0;
		int startT = Math.max(0, loads.getStartT(e));
		int endT = loads.getEndT(e);
		for (int t = startT; t < endT; t++) {
			if(t <= getMarket().getFixedPTUs())
				margin -= (d.rcu[e][t] + d.rdu[e][t]) * ptu;
			if(d.p[e][t] < loads.getMaximumChargingSpeed(e) && (t > getMarket().getFixedPTUs() || d.rcd[e][t] + d.rdd[e][t] == 0))
				margin += (loads.getMaximumChargingSpeed(e) - d.p[e][t]) * ptu;
		}		
		return (int) Math.floor(margin / (loads.getMaximumChargingSpeed(e) * ptu));
	}

}

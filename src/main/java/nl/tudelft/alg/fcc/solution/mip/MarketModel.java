package nl.tudelft.alg.fcc.solution.mip;

import java.util.stream.IntStream;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;

public class MarketModel implements ConstraintGenerator {
	FlexibleLoadMIP	mip;
	/**
	 * binary help variable to allow only both positive da and imb purchase, or both negative
	 * This is to prevent energy arbitrage
	 */
	Variable[] daorimb; 
	
	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		this.mip = mip;
		Market market = mip.getMarket();
		addMarketBalance();
		if (market.considerImbalance() && market.hasDayAhead() && !market.isDayAheadFixed()) addMarketSeperation();
		if (!market.considerImbalance())
			setimbalancezero();
	}
	
	/**
	 * Constraint: The sum of energy bought in DA and Imbalance market is equal to the amount of charge
	 */
	protected void addMarketBalance() {
		Market market = mip.getMarket();
		for(int t=0; t<market.getNPTUs(); t++) {
			LinExp left = mip.getChargeSum(t);
			LinExp right = mip.getPDA(market.PTUtoH(t))
				.addLinExp(mip.getPimb(t));
			mip.addConstraint(left, right, CMP.EQ, "PB"+t);
		}
	}
	
	/**
	 * Constraint: Either DA and imbalance purchase are both positive, or both negative
	 * This is to prevent energy arbitrage
	 */
	protected void addMarketSeperation() {
		LinExp left, right;
		Market market = mip.getMarket();
		Loads loads = mip.getLoads();
		daorimb = (Variable[]) mip.newVarArray("daorimb", VarType.Binary, market.getDATimesteps());
		mip.addVars(daorimb);
		for(int t=0; t<market.getNPTUs(); t++) {
			final int _t = t;
			double M = IntStream.range(0, loads.getNLoads()).mapToDouble(i -> 
					loads.isLoadAvailable(i, _t) ? loads.getChargingSpeed(i,_t) : 0).sum();
			left = mip.getPimb(t);
			right = new LinExp().addNegationTerm(daorimb[market.PTUtoH(t)], -M);
			mip.addConstraint(left, right, CMP.LARGEREQ, "DAORIMB1_"+t);
			left = mip.getPDA(market.PTUtoH(t));
			mip.addConstraint(left, right, CMP.LARGEREQ, "DAORIMB2_"+t);
			
			left = mip.getPimb(t);
			right = new LinExp(M, daorimb[market.PTUtoH(t)]);
			mip.addConstraint(left, right, CMP.SMALLEREQ, "DAORIMB3_"+t);
			left = mip.getPDA(market.PTUtoH(t));
			mip.addConstraint(left, right, CMP.SMALLEREQ, "DAORIMB4_"+t);
		}
	}
	
	protected void setimbalancezero() {
		mip.fixVariables(0, mip.pimb);
	}

}

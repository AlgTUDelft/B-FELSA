package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;

public class StochasticCompactLoadModel extends StochasticLoadModel {
	
	public StochasticCompactLoadModel(Market market) {
		super(market);
	}

	@Override
	protected void addDownReserveLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		int nScenarios = market.getNScenarios();
		LinExp left, right;
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getNPTUs(); t++) {
				int[] scenarioOrder = mip.getDownScenarioOrder(t);
				double PCMax = loads.getChargingSpeed(e,t);
				double PDMax = loads.getDischargingSpeed(e,t);
				int last = market.hasCapacityPayments() ? scenarioOrder[0] : scenarioOrder[nScenarios-1];
				int first = market.hasCapacityPayments() ? scenarioOrder[nScenarios-1] : scenarioOrder[0];
				//the charge + down reserve should always be smaller than the maximum charging speed
				left = mip.getPc(e,t).addLinExp(mip.getRcd(e,t,last));
				right = getMaxChargingSpeed(e, t);
				mip.addConstraint(left, right, CMP.SMALLEREQ, "CL1_"+e+"_"+t);
				
				//The provided down reserves should never exceed the amount of discharge
				if(mip.getConfig().considerV2G()) {
					left = mip.getPd(e,t).subtractLinExp(mip.getRdd(e,t,last));
					right = new LinExp();
					mip.addConstraint(left, right, CMP.LARGEREQ, "CL4_"+e+"_"+t);
				}
								
				//When not called, the provided down reserves are equal to zero
				left = mip.getRcd(e,t,first);
				right = mip.getVd(e,t,first).multiplyBy(PCMax);
				mip.addConstraint(left, right, CMP.SMALLEREQ, "DRL3_"+e+"_"+t);
				if(mip.getConfig().considerV2G()) {
					left = mip.getRdd(e,t,first);
					right = mip.getVd(e,t,first).multiplyBy(PDMax);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DDRL3_"+e+"_"+t);
				} 
				
				for(int i=0; i<scenarioOrder.length-1; i++) {
					int i0 = scenarioOrder[i];
					int i1 = scenarioOrder[i+1];
					//Two scenarios only differ in the amount of provided reserves when the vd values differ
					int f = market.hasCapacityPayments() ? -1 : 1;
					left = mip.getRcd(e,t,i1).subtractLinExp(mip.getRcd(e,t,i0)).multiplyBy(f);
					right = mip.getVd(e,t,i1).subtractLinExp(mip.getVd(e,t,i0)).multiplyBy(f*PCMax);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DRL1_"+e+"_"+t+"_"+i);
					if(mip.getConfig().considerV2G()) {
						left = mip.getRdd(e,t,i1).subtractLinExp(mip.getRdd(e,t,i0)).multiplyBy(f);
						right = mip.getVd(e,t,i1).subtractLinExp(mip.getVd(e,t,i0)).multiplyBy(f*PDMax);
						mip.addConstraint(left, right, CMP.SMALLEREQ, "DDRL1_"+e+"_"+t+"_"+i);
					}
					
					//The provided reserves in this scenario are always larger or equal than those in the previous scenario with a larger down regulating price
					left = mip.getRcd(e,t,i1);
					right = mip.getRcd(e,t,i0);
					CMP cmp = market.hasCapacityPayments() ? CMP.SMALLEREQ : CMP.LARGEREQ;
					mip.addConstraint(left, right, cmp, "DRL2_"+e+"_"+t+"_"+i);
					if(mip.getConfig().considerV2G()) {
						left = mip.getRdd(e,t,i1);
						right = mip.getRdd(e,t,i0);
						mip.addConstraint(left, right, cmp, "DDRL2_"+e+"_"+t+"_"+i);
					}
				}
			}
		}
	} 
	
	@Override
	protected void addUpReserveLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		int nScenarios = market.getNScenarios();
		LinExp left, right;
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getNPTUs(); t++) {
				double PCMax = loads.getChargingSpeed(e,t);
				double PDMax = loads.getDischargingSpeed(e,t);
				boolean available = loads.isLoadAvailable(e, t); 
				int[] scenarioOrder = mip.getUpScenarioOrder(t);
				int last = scenarioOrder[nScenarios-1];
				int first = scenarioOrder[0];
				
				//The provided upward reserves are always limited by the amount of charge that is taken
				left = mip.getPc(e,t).subtractLinExp(mip.getRcu(e,t,first));
				right = new LinExp();
				mip.addConstraint(left, right, CMP.LARGEREQ, "CL3_"+e+"_"+t);
				
				if(mip.getConfig().considerV2G()) {
					//The amount of discharge + provided upward reserves should always be smaller than the maximum discharging speed
					left = mip.getPd(e,t).addLinExp(mip.getRdu(e,t,first));
					right = getMaxDischargingSpeed(e,t);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "CL5_"+e+"_"+t);
				}
				
				//When not called, the provided upward reserves are equal to zero
				left = mip.getRcu(e,t,last);
				right = mip.getVu(e,t,last).multiplyBy(PCMax);
				if(!available) right = new LinExp();
				mip.addConstraint(left, right, CMP.SMALLEREQ, "URL3_"+e+"_"+t);
				if(mip.getConfig().considerV2G()) {
					left = mip.getRdu(e,t,last);
					right = mip.getVu(e,t,last).multiplyBy(PDMax);
					if(!available) right = new LinExp();
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DURL3_"+e+"_"+t);
				} 				
				
				for(int i=0; i<scenarioOrder.length-1; i++) {
					int i0 = scenarioOrder[i];
					int i1 = scenarioOrder[i+1];
					//Two scenarios only differ in the amount of provided reserves when the vu values differ
					left = mip.getRcu(e,t,i0).subtractLinExp(mip.getRcu(e,t,i1));
					right = mip.getVu(e,t,i0).subtractLinExp(mip.getVu(e,t,i1)).multiplyBy(PCMax);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "URL2_"+e+"_"+t+"_"+i);
					if(mip.getConfig().considerV2G()) {
						left = mip.getRdu(e,t,i0).subtractLinExp(mip.getRdu(e,t,i1));
						right = mip.getVu(e,t,i0).subtractLinExp(mip.getVu(e,t,i1)).multiplyBy(PDMax);
						mip.addConstraint(left, right, CMP.SMALLEREQ, "DURL2_"+e+"_"+t+"_"+i);
					}
					
					//The provided reserves in this scenario are always smaller or equal than those in the previous scenario with a larger up regulating price
					left = mip.getRcu(e,t,i1);
					right = mip.getRcu(e,t,i0);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "URL3_"+e+"_"+t+"_"+i);
					if(mip.getConfig().considerV2G()) {
						left = mip.getRdu(e,t,i1);
						right = mip.getRdu(e,t,i0);
						mip.addConstraint(left, right, CMP.SMALLEREQ, "DURL3_"+e+"_"+t+"_"+i);
					}
				}
			}
		}
	}
}

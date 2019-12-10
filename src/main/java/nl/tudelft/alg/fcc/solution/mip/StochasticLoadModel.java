package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;

public class StochasticLoadModel extends LoadModel {
	@SuppressWarnings("hiding")
	StochasticModel mip;
	Market allScenariosMarket;
	
	public StochasticLoadModel(Market market) {
		super();
		allScenariosMarket = market;
	}

	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		assert mip instanceof StochasticModel;
		this.mip = (StochasticModel) mip;
		super.addConstraints(mip);
	}
	
	@Override
	protected void addDownReserveLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		LinExp left, right;
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getNPTUs(); t++) {
				boolean available = loads.isLoadAvailable(e, t);
				for(int i=0; i<market.getNScenarios(); i++) {
					//The sum of the planned charge + downward reserves should be lower than the battery charge speed
					left = mip.getPc(e, t).addLinExp(mip.getRcd(e, t, i));
					right = getMaxChargingSpeed(e, t);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "CCL"+e+"_"+t+"_"+i);
					//the provided downward reserves is zero, when you are not expected to be called
					left = mip.getRcd(e,t,i);
					right = mip.getVd(e,t,i).multiplyBy(loads.getChargingSpeed(e,t));
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DRL1_"+e+"_"+t+"_"+i);
					//When you are called, always provide all remaining charging capacity as downward reserves
					if(available) {
						left = mip.getRcd(e,t,i);
						right = mip.getVd(e,t,i).multiplyBy(loads.getChargingSpeed(e,t))
								.subtractLinExp(mip.getPc(e,t));
						mip.addConstraint(left, right, CMP.LARGEREQ, "DRL2_"+e+"_"+t+"_"+i);
					}
					
					if(!mip.getConfig().considerV2G()) continue;
					//The provided downward reserves should always be smaller than the planned amount of discharge
					left = mip.getPd(e, t).subtractLinExp(mip.getRdd(e, t, i));
					right = new LinExp();
					mip.addConstraint(left, right, CMP.LARGEREQ, "DCPL"+e+"_"+t+"_"+i);
					//The amount of provided downward reserves is zero, when you are not called
					left = mip.getRdd(e,t,i);
					right = mip.getVd(e,t,i);
					right = right.multiplyBy(available ? loads.getDischargingSpeed(e,t) : 0);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DDRL1_"+e+"_"+t+"_"+i);
					
					//Limit the down reserves discharge to zero if not providing reserves
					left = mip.getRdd(e,t,i);
					right = mip.getPd(e,t);
					right.addNegationLinExp(mip.getVd(e,t,i), -loads.getDischargingSpeed(e,t));
					mip.addConstraint(left, right, CMP.LARGEREQ, "DDRL2_" + e +"_"+t+"_"+i);	
				}
			}
		}
	}

	@Override
	protected void addUpReserveLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		LinExp left, right;
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getNPTUs(); t++) {
				boolean available = loads.isLoadAvailable(e, t);
				for(int i=0; i<market.getNScenarios(); i++) {
					//The provided upward reserves should always be smaller than the planned amount of charge
					left = mip.getPc(e,t).subtractLinExp(mip.getRcu(e,t,i));
					right = new LinExp();
					mip.addConstraint(left, right, CMP.LARGEREQ, "CPL"+e+"_"+t+"_"+i);
					//The amount of provided upward reserves is zero, when you are not called
					left = mip.getRcu(e,t,i);
					if(available)
						right = mip.getVu(e,t,i)
						.multiplyBy(loads.getChargingSpeed(e,t));
					else right = new LinExp();
					mip.addConstraint(left, right, CMP.SMALLEREQ, "URL1_"+e+"_"+t+"_"+i);
					
					//Limit up reserves to zero when not used
					left = mip.getRcu(e,t,i);
					right = mip.getPc(e,t);
					right.addNegationLinExp(mip.getVu(e,t,i),-loads.getChargingSpeed(e,t));
					mip.addConstraint(left, right, CMP.LARGEREQ, "URL2_" + e +"_"+t+"_"+i);
					
					
					if(!mip.getConfig().considerV2G()) continue;
					//The sum of the planned discharge + upward reserves should be lower than the battery discharge speed
					left = mip.getPd(e, t).addLinExp(mip.getRdu(e,t,i));
					right = getMaxDischargingSpeed(e, t);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DCCL"+e+"_"+t+"_"+i);
					//the provided upward reserves is zero, when you are not expected to be called
					left = mip.getRdu(e,t,i);
					right = mip.getVu(e,t,i)
						.multiplyBy(loads.getDischargingSpeed(e,t));
					mip.addConstraint(left, right, CMP.SMALLEREQ, "DURL1_"+e+"_"+t+"_"+i);
					//When you are called, always provide all remaining charging capacity as upward reserves
					if(available) {
						left = mip.getRdu(e,t,i);
						right = mip.getVu(e,t,i).multiplyBy(loads.getDischargingSpeed(e,t))
								.subtractLinExp(mip.getPd(e,t));
						mip.addConstraint(left, right, CMP.LARGEREQ, "DURL2_"+e+"_"+t+"_"+i);
					}
				}
			}
		}
	}
	
	@Override
	public Market getAllScenariosMarket() {
		return allScenariosMarket;
	}

}

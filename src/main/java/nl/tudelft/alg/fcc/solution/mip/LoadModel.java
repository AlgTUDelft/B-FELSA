package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;

public class LoadModel implements ConstraintGenerator{
	FlexibleLoadMIP mip;
	protected Variable[][] d; // true if the load is discharging, false if it is charging, for every load, for every time step, binary
	Variable[][][] soc; // the state of charge for every load, per time step, per scenario, 'positive' continuous, and one low, and one high soc state
	Variable[][]
			surp1, // surplus variable for every load, to penalize limits that are not reached, without making the problem infeasible, positive continuous
			surp2; // surplus variable for every load, to penalize limits that are not reached, without making the problem infeasible, positive continuous
	
	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		this.mip = mip;
		addVars();
		addChargingLimits();
		addDownReserveLimits();
		addUpReserveLimits();
		addSOCLimits();
	}
	
	protected void addVars() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		
		if(considerV2G())
			addDischargeVariable();
		
		soc = (Variable[][][]) mip.newVarArray("soc", VarType.Real, 
				loads.getNLoads(), market.getNTimeSteps(), market.getNScenarios());
		surp1 = (Variable[][]) mip.newVarArray("surp1", VarType.PositiveContinuous, loads.getNLoads(), market.getNScenarios());
		surp2 = (Variable[][]) mip.newVarArray("surp2", VarType.PositiveContinuous, loads.getNLoads(), market.getNScenarios());
		mip.addVars(soc, surp1, surp2);
	}

	protected boolean considerV2G() {
		return mip.getConfig().considerV2G();
	}
	
	protected void addDischargeVariable() {
		d = (Variable[][]) mip.newVarArray("d", VarType.Binary,
				mip.getLoads().getNLoads(),
				mip.getMarket().getNTimeSteps());
		mip.addVars(d);
	}
	
	protected LinExp getMaxChargingSpeed(int e, int t) {
		Loads loads = mip.getLoads();
		LinExp result = new LinExp();
		if(loads.isLoadAvailable(e, t)) {
			double m = loads.getChargingSpeed(e,t);
			if(considerV2G()) {
				result.addNegationTerm(d[e][t], m);
			} else {
				result.addTerm(m);
			}
		}
		return result;
	}
	
	protected LinExp getMaxDischargingSpeed(int e, int t) {
		Loads loads = mip.getLoads();
		LinExp result = new LinExp();
		if(loads.isLoadAvailable(e, t)) {
			double m = loads.getDischargingSpeed(e,t);
			if(considerV2G()) {
				result.addTerm(d[e][t], m);
			} else {
				result.addTerm(m);
			}
		}
		return result;
	}
	
	protected void addChargingLimits() {
		LinExp left, right;
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		for(int t=0; t<market.getNPTUs(); t++) {
			for(int e=0; e<loads.getNLoads(); e++) {
				left = mip.getPc(e, t);
				right = getMaxChargingSpeed(e, t);
				mip.addConstraint(left, right, CMP.SMALLEREQ, "MAXCHG"+e+"_"+t);
				if (considerV2G()) {
					left = mip.getPd(e, t);
					right = getMaxDischargingSpeed(e, t);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "MAXDCHG" + e + "_" + t);
				}
			}
		}
		
	}

	protected void addDownReserveLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		LinExp left, right;
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getNPTUs(); t++) {
				//The sum of the planned charge + downward reserves should be lower than the battery charge speed
				left = mip.getPc(e, t).addLinExp(mip.getRcd(e,t,0));
				right = getMaxChargingSpeed(e, t);
				mip.addConstraint(left, right, CMP.SMALLEREQ, "CCL"+e+"_"+t);				
				if(!considerV2G()) continue;
				//The provided downward reserves should always be smaller than the planned amount of discharge
				left = mip.getPd(e,t).addLinExp(mip.getRdd(e, t,0).multiplyBy(-1));
				right = new LinExp();
				mip.addConstraint(left, right, CMP.LARGEREQ, "DCPL"+e+"_"+t);
			}
		}
	}

	protected void addUpReserveLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		LinExp left, right;
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getNPTUs(); t++) {
				//The provided upward reserves should always be smaller than the planned amount of charge
				left = mip.getPc(e,t).addLinExp(mip.getRcu(e, t, 0).multiplyBy(-1));
				right = new LinExp();
				mip.addConstraint(left, right, CMP.LARGEREQ, "CPL"+e+"_"+t);		
				if(!considerV2G()) continue;
				//The sum of the planned discharge + upward reserves should be lower than the battery discharge speed
				left = mip.getPd(e, t).addLinExp(mip.getRdu(e,t, 0));
				right = getMaxDischargingSpeed(e, t);
				mip.addConstraint(left, right, CMP.SMALLEREQ, "DCCL"+e+"_"+t);	
			}
		}
	}

	public Market getAllScenariosMarket() {
		return mip.getMarket();
	}

	protected void addSOCLimits() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		ProblemConfiguration config = mip.getConfig();
		
		LinExp left, right;
		//TODO change for reserve market dt!=1ptu
		for(int e=0; e<loads.getNLoads(); e++) {
			if(loads.getEndT(e) <= 0) continue;
			double eta_c = loads.getChargingEfficiency();
			double eta_d = 1.0 / loads.getChargingEfficiency();
			if(!config.considerV2G()) eta_d = 0;
			for (int i = 0; i < market.getNScenarios(); i++) {
				for (int t = 0; t < market.getNPTUs(); t++) {
					double epsilonDown = mip.getDownReserveProportion(t, i);
					double epsilonUp = mip.getUpReserveProportion(t, i);
					if(!market.hasReserves()) { epsilonDown = 0; epsilonUp = 0;}
					left = new LinExp(soc[e][t][i]);
					right =        mip.getEPc (e,t).multiplyBy( eta_c)
						.addLinExp(mip.getEPd (e,t).multiplyBy(-eta_d))
						.addLinExp(mip.getERcd(e,t,i).multiplyBy( eta_c*epsilonDown))
						.addLinExp(mip.getERdd(e,t,i).multiplyBy( eta_d*epsilonDown))
						.addLinExp(mip.getERcu(e,t,i).multiplyBy(-eta_c*epsilonUp))
						.addLinExp(mip.getERdu(e,t,i).multiplyBy(-eta_d*epsilonUp));
					if(t == loads.getStartT(e)) {
						right.addTerm(loads.getArrivalSOC(e));
					} else if(t > 0) {
						right.addTerm(soc[e][t-1][i]);
					} else continue;
					mip.addConstraint(left, right, CMP.EQ, "SOC"+e+"_"+t+"_"+i);
					right = new LinExp(loads.getBatteryCapacity(e)).addTerm(surp1[e][i]);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "BATLIMUP"+e+"_"+t+"_"+i);
					left = new LinExp(soc[e][t][i]);
					right = new LinExp( -1, surp1[e][i]);
					mip.addConstraint(left, right, CMP.LARGEREQ, "BATLIMDO"+e+"_"+t+"_"+i);
					//TODO model is sometimes mistaken by one ptu, and therefore can yield shortages up to 15% 
				}
				left = new LinExp(soc[e][loads.getEndT(e)-1][i]).addTerm(surp2[e][i]);
				right = new LinExp(loads.getMinimumSOC(e));
				mip.addConstraint(left, right, CMP.LARGEREQ, "BATREQ"+e+"_"+i);
			}
		}
	}

	public void addPenalizationObj() {
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int i=0; i<market.getNScenarios(); i++) {
				double prob = market.getScenarioProbability(i);
				//The battery limit may not be exceeded. However, we penalize it, so that 'infeasible' models are still solved
				mip.getObjectiveFunction().addTerm(surp1[e][i],1000); 
				//unmet demand is penalized as set by the parameter
				mip.getObjectiveFunction().addTerm(surp2[e][i], mip.getConfig().getShortagePenalty()*prob);
			}
		}
	}

}

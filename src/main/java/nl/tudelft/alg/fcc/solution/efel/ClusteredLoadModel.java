package nl.tudelft.alg.fcc.solution.efel;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.Exp;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;
import nl.tudelft.alg.fcc.solution.mip.FlexibleLoadMIP;
import nl.tudelft.alg.fcc.solution.mip.StochasticCompactLoadModel;

public class ClusteredLoadModel extends StochasticCompactLoadModel {
	EFEL_P1 mip;
	Variable[][]
			edep, //Energy at departure for (original) EV j  for scenario i. For EV j the model needs to know time of arrival and departure
			surp3, // Energy below minimum SOC at departure for each (original) EV j and scenario i
			surp4; // Energy above maximum SOC at departure for each (original) EV j and scenario i
	Variable[][][] 
		surp5,
		exchange, //Potential energy exchange between EVs to penalize as discharge
		soc;

	public ClusteredLoadModel(Market market) {
		super(market);
	}

	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		this.mip = (EFEL_P1) mip;
		super.addConstraints(mip);
		setminSOCEV();
	}
	
	@Override
	protected void addVars() {
		super.addVars();
		int maxEVsPerCluster = ((ClusteredLoads) mip.getLoads()).getMaxEVPerCluster();
		int nOrgLoads = mip.getOrgLoads().getNLoads();
		soc = (Variable[][][]) mip.newVarArray("soc", VarType.PositiveContinuous, 
				mip.nLoads, mip.nTimeSteps, mip.nScenarios);
		edep = (Variable[][]) mip.newVarArray("edep",VarType.PositiveContinuous, nOrgLoads, mip.nScenarios);
		surp3 = (Variable[][]) mip.newVarArray("surp3",VarType.PositiveContinuous, nOrgLoads, mip.nScenarios);
		surp4 = (Variable[][]) mip.newVarArray("surp4",VarType.PositiveContinuous, nOrgLoads, mip.nScenarios);
		surp5 = (Variable[][][]) mip.newVarArray("surp1", VarType.PositiveContinuous, mip.nLoads, mip.nTimeSteps, mip.nScenarios);
		exchange = (Variable[][][]) mip.newVarArray("exchange",VarType.PositiveContinuous, maxEVsPerCluster, mip.nLoads, mip.nScenarios);
		mip.addVars(soc, edep, surp3, surp4, surp5, exchange);
	}
	
	@Override
	protected void addDischargeVariable() {
		d = (Variable[][]) mip.newVarArray("d", VarType.BinaryContinuous,
				mip.getLoads().getNLoads(),
				mip.getMarket().getNTimeSteps());
		mip.addVars(d);
	}

	@Override
	protected void addSOCLimits() {
		LinExp left, right;
		ClusteredLoads loads = (ClusteredLoads) mip.getLoads();
		Loads orgLoads = mip.getOrgLoads();
		Market market = mip.getMarket();
		for(int e=0; e<mip.nLoads; e++) {
			if(loads.getEndT(e) <= 0) continue;
			double eta_c = loads.getChargingEfficiency();
			double eta_d = 1.0 / loads.getChargingEfficiency();
			for(int i=0; i<market.getNScenarios(); i++) {
				Cluster c = mip.getCluster(e);
				for(int t=loads.getStartT(e); t<loads.getEndT(e); t++) { 	
					double epsilonDown = market.getDownReserveProportion(t, i);
					double epsilonUp = market.getUpReserveProportion(t, i);
					left = new LinExp(soc[e][t][i]);
					right =        mip.getEPc (e,t).multiplyBy( eta_c)
						.addLinExp(mip.getEPd (e,t).multiplyBy(-eta_d))
						.addLinExp(mip.getERcd(e,t,i).multiplyBy( eta_c*epsilonDown))
						.addLinExp(mip.getERdd(e,t,i).multiplyBy( eta_d*epsilonDown))
						.addLinExp(mip.getERcu(e,t,i).multiplyBy(-eta_c*epsilonUp))
						.addLinExp(mip.getERdu(e,t,i).multiplyBy(-eta_d*epsilonUp));
					for(int j=0; j<c.getEVs();j++) {
						int evid = c.getEVid(j);
						if(orgLoads.getStartT(evid) == t) { 
							right.addTerm(orgLoads.getArrivalSOC(evid));
						} else if(t > 0 && orgLoads.getEndT(evid) == t-1) { 
							left.addTerm(edep[evid][i]);
						}
					}
					if(t > 0) right.addTerm(soc[e][t-1][i]);
					mip.addConstraint(left, right, CMP.EQ, "SOC"+e+"_"+t+"_"+i);
					left = new LinExp(soc[e][t][i]);
					right = new LinExp(loads.getBatteryCapacity(e, t));
					right.addTerm(surp5[e][t][i]);
					mip.addConstraint(left, right, CMP.SMALLEREQ, "BATLIMINT"+e+"_"+t+"_"+i);
					right = new LinExp(c.getMinimumSOC(t));//Maybe add another surp6
					//right.addTerm(surp2[e][t][i], -1.0);
					mip.addConstraint(left, right, CMP.LARGEREQ, "BATLIMAXT"+e+"_"+t+"_"+i);
				}
				left = new LinExp(soc[e][loads.getEndT(e)-1][i]);
				right = new LinExp();
				for(int j=0; j<c.getEVs();j++) {
					int evid = c.getEVid(j);
					if(orgLoads.getEndT(evid) == loads.getEndT(e))
						right.addTerm(edep[evid][i]);
				}
				mip.addConstraint(left, right, CMP.EQ, "FINALDEP"+e+"_"+i);
			
				//Constraining the departure charge stage
				for(int j=0; j<c.getEVs();j++) {
					int evid = c.getEVid(j);
					left = new LinExp(edep[evid][i]);
					right = new LinExp(surp3[evid][i], orgLoads.getBatteryCapacity(evid)); 
					mip.addConstraint(left, right, CMP.SMALLEREQ, "BATLIMSUBEV"+j+"_"+e+"_"+i);
					left = new LinExp(edep[evid][i], surp4[evid][i]);
					right = new LinExp(orgLoads.getMinimumSOC(evid)); 
					mip.addConstraint(left, right, CMP.LARGEREQ, "BATREQSUBEVE"+j+"_"+e+"_"+i);
				}
			}
		}
	}
	
	/**
	 * EV cannot depart with less energy than upon arrival
	 */
	protected void setminSOCEV() {
		LinExp left, right;
		Loads orgLoads = mip.getOrgLoads();
		for(int e=0; e < mip.nLoads; e++) {
			Cluster c = mip.getCluster(e);
			for(int i=0;i<mip.nScenarios;i++) {
				for(int j=0; j<c.getEVs();j++) {
					int evid = c.getEVid(j);
					right = new LinExp(orgLoads.getArrivalSOC(evid));
					left = new LinExp(edep[evid][i]);
					mip.addConstraint(left, right, CMP.LARGEREQ, "MinFinSOCeqEV_EV_sc"+e+"_"+j+"_"+i);
				}
			}
		}
	}
	
	protected void setloadexchange() {
		Market market = mip.getMarket();
		Loads loads = mip.getLoads();
		Loads orgLoads = mip.getOrgLoads();
		boolean connected = false;
		for(int e=0;e<loads.getNLoads();e++) {
			Cluster c = mip.getCluster(e);
			double eta_c = loads.getChargingEfficiency();
			double eta_d = 1.0 / loads.getChargingEfficiency();
			for(int i=0; i<market.getNScenarios();i++) {
				LinExp left, right;
				left = new LinExp();
				right = new LinExp();
				for(int j = 0;j<c.getEVs();j++) {
					int evid = c.getEVid(j);
					for(int t=0; t<market.getNTimeSteps(); t++) {
						if(orgLoads.getStartT(evid) == t) 
							connected = true;
						if(connected) {
							double epsilonDown = market.getDownReserveProportion(t, i);
							double epsilonUp = market.getUpReserveProportion(t, i);
							left = mip.getEPc (e,t).multiplyBy( eta_c)
								.addLinExp(mip.getEPd (e,t).multiplyBy(-eta_d))
								.addLinExp(mip.getERcd(e,t,i).multiplyBy( eta_c*epsilonDown))
								.addLinExp(mip.getERdd(e,t,i).multiplyBy( eta_d*epsilonDown))
								.addLinExp(mip.getERcu(e,t,i).multiplyBy(-eta_c*epsilonUp))
								.addLinExp(mip.getERdu(e,t,i).multiplyBy(-eta_d*epsilonUp));								
						}
						if(orgLoads.getEndT(evid) == t) connected = false; 
					}
					left.addTerm(exchange[j][e][i]);
					left.addTerm(orgLoads.getArrivalSOC(evid)); 
					right.addTerm(edep[evid][i]);
					mip.addConstraint(left, right, CMP.LARGEREQ, "ForEstExchange_"+j+e+"_"+i);
				}
			}
		}
	}

	@Override
	public void addPenalizationObj() {
		super.addPenalizationObj();
		Loads loads = mip.getLoads();
		Market market = mip.getMarket();
		ProblemConfiguration config = mip.getConfig();
		Exp obj = mip.getObjectiveFunction();
		for(int e=0; e<loads.getNLoads(); e++) {
			Cluster c = mip.getCluster(e);
			for(int i=0; i<market.getNScenarios(); i++) {
				for(int j=0; j<c.getEVs(); j++) {
					int evid = c.getEVid(j); 
					obj.addTerm(surp3[evid][i], 1000); //The battery limit may not be exceeded. However, we penalize it, so that 'infeasible' models are still solved
					obj.addTerm(surp4[evid][i], market.getScenarioProbability(i)
						* config.getShortagePenalty()); //unmet demand is penalized as set by the parameter BY 2
					obj.addTerm(exchange[j][e][i], loads.getBatteryDegradationCost() *
						market.getScenarioProbability(i) * market.getPTU());
				}
			}
			for(int t=0; t<market.getNTimeSteps(); t++) {
				for(int i=0; i<market.getNScenarios(); i++) {
					obj.addTerm(surp5[e][t][i], 1000);
				}
			}
		}
		
	}

}

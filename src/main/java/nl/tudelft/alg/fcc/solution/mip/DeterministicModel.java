package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * Solves the FlexibleLoadProblem with deterministic optimization for multiple EVs
 * The model results in at most one regulation bid for every PTU.
 */
public class DeterministicModel extends FlexibleLoadMIPcon {
	Variable[][]
			rcu_bid, // the scheduled amount of bid up reserves while charging for every EV, for every time step, positive continuous
			rcd_bid, // the scheduled amount of bid down reserves while charging for every EV, for every time step, positive continuous
			rdu_bid, // the scheduled amount of bid up reserves while discharging for every EV, for every time step, positive continuous
			rdd_bid; // the scheduled amount of bid down reserves while discharging for every EV, for every time step, positive continuous
	
	public DeterministicModel(FlexibleLoadProblem p) {
		super(p);
	}
	
	@Override
	protected void setFirstFixed() {
		super.setFirstFixed();
		if(getMarket().hasReserves()) {
   		for(int t=0; t<getMarket().getFixedPTUs(); t++) {
   			if(t >= problem.getNTimeSteps()) break;
   			for(int e=0; e<getLoads().getNLoads(); e++) {
   				//the provide down and up ward reserves is as committed
   				fixVariable(rcu_bid[e][t], problem.getVars().rcu[e][t]);
   				fixVariable(rcd_bid[e][t], problem.getVars().rcd[e][t]);
   				if(problem.getConfig().considerV2G()) {
   					fixVariable(rdu_bid[e][t], problem.getVars().rdu[e][t]);
   					fixVariable(rdd_bid[e][t], problem.getVars().rdd[e][t]);
   				}
   			}
   		}
   	}
	}
	
	@Override
	public double getDownReserveProportion(int t, int i) {
		if(getConfig().isSemiStochastic())
			return super.getDownReserveProportion(t, i);
		return getMarket().getExpectedDownReserveProportion(t);
	}
	
	@Override
	public double getUpReserveProportion(int t, int i) {
		if(getConfig().isSemiStochastic())
			return super.getUpReserveProportion(t, i);
		return getMarket().getExpectedUpReserveProportion(t);
	}
		
	@Override
	public double getImbDownObjPrice(int t) {
		double downPrice = getPriceDownBid(t);
		return getMarket().getImbDownObjPriceByBid(t, downPrice)
			* getConfig().getDesiredProbabilityOfAcceptanceDown(t);
	}
	@Override
	public double getImbUpObjPrice(int t) {
		double upPrice = getPriceUpBid(t);
		return getMarket().getImbUpObjPriceByBid(t, upPrice)
			* getConfig().getDesiredProbabilityOfAcceptanceUp(t);
	}
	@Override
	public double getExpectedDownCapacityPayment(int t) {
		double downPrice = getPriceDownBid(t);
		return getMarket().getExpectedDownCapacityPaymentByBid(t, downPrice)
			* getConfig().getDesiredProbabilityOfAcceptanceDown(t);
	}
	@Override
	public double getExpectedUpCapacityPayment(int t) {
		double upPrice = getPriceUpBid(t);
		return getMarket().getExpectedUpCapacityPaymentByBid(t, upPrice)
			* getConfig().getDesiredProbabilityOfAcceptanceUp(t);
	}
	@Override
	public LinExp getERcu(int e, int t, int i) {
		return super.getERcu(e,t, i).multiplyBy(
			getConfig().getDesiredProbabilityOfAcceptanceUp(t));
	}
	@Override
	public LinExp getERcd(int e, int t, int i) {
		return super.getERcd(e,t,i).multiplyBy(
			getConfig().getDesiredProbabilityOfAcceptanceDown(t));
	}
	@Override
	public LinExp getERdu(int e, int t, int i) {
		return super.getERdu(e,t, i).multiplyBy(
			getConfig().getDesiredProbabilityOfAcceptanceUp(t));
	}
	@Override
	public LinExp getERdd(int e, int t, int i) {
		return super.getERdd(e,t, i).multiplyBy(
			getConfig().getDesiredProbabilityOfAcceptanceDown(t));
	}
	
	@Override
	protected void setVars() {
		super.setVars();
		addVars(rcd_bid, rcu_bid);
		addVars(rdu_bid, rdd_bid);
	}


	//Write the model solution back to the problem instance
	@Override
	public void writeSolution(IMIPSolver solver) throws SolverException {
		super.writeSolution(solver);
		DecisionVariables d = problem.getVars();
		writeVarsBack(rcu_bid, d.rcu);
		writeVarsBack(rcd_bid, d.rcd);
		if(problem.getConfig().considerV2G()) {
			writeVarsBack(rdu_bid, d.rdu);
			writeVarsBack(rdd_bid, d.rdd);
		}
	}
	
	@Override
	protected void writePriceBidSolution() {
		double[][] pu_bid = problem.getVars().bu;
		double[][] pd_bid = problem.getVars().bd;
		for(int e=0; e<getLoads().getNLoads(); e++) {
			for(int t=problem.getMarket().getFixedPTUs(); t<getMarket().getNTimeSteps(); t++) {
				pd_bid[e][t] = getPriceDownBid(t);
				pu_bid[e][t] = getPriceUpBid(t);
			}
		}
		problem.getVars().bd = pd_bid;
		problem.getVars().bu = pu_bid;
	}
	
	/**
	 * Get the price bid based on the chi (desired acceptance probability) parameter
	 * @param t at time step t
	 */
	private double getPriceDownBid(int t) {
		double chi = problem.getConfig().getDesiredProbabilityOfAcceptanceDown(t);
		boolean capacity = problem.getMarket().hasCapacityPayments();
		if(chi >= 1-1e-3) {
			if(capacity) return 0;
			return 1000.0;
		}
		//Ordered from  high to low
		int[] scenarioOrder = capacity ?
				problem.getMarket().getScenariosOrderedByDownCapacityPayment(t):
				problem.getMarket().getScenariosOrderedByDownRegulatingPrice(t);
		int scenario = (int) Math.floor((1-chi) * (nScenarios-1));
		if(capacity)
			scenario = (int) Math.floor(chi * (nScenarios-1));
		scenario = scenarioOrder[scenario];
		return capacity ?
				problem.getMarket().getDownCapacityPayment(t, scenario):
				problem.getMarket().getDownRegulatingPrice(t, scenario);
	}
	
	/**
	 * Get the price bid based on the chi (desired acceptance probability) parameter
	 * @param t at time step t
	 */
	private double getPriceUpBid(int t) {
		double chi = problem.getConfig().getDesiredProbabilityOfAcceptanceUp(t);
		boolean capacity = problem.getMarket().hasCapacityPayments();
		if(chi >= 1-1e-3) {
			if(capacity) return 0;
			return -1000;
		}
		//Ordered from  high to low
		int[] scenarioOrder = capacity ?
				problem.getMarket().getScenariosOrderedByUpCapacityPayment(t):
				problem.getMarket().getScenariosOrderedByUpRegulatingPrice(t);
		int scenario = (int) Math.floor((problem.getConfig().getDesiredProbabilityOfAcceptanceUp(t)) * (nScenarios-1));
		scenario = scenarioOrder[scenario];
		return capacity ?
				problem.getMarket().getUpCapacityPayment(t, scenario):
				problem.getMarket().getUpRegulatingPrice(t, scenario);
	}

	@Override
	protected void fixVariables() {
		super.fixVariables();
		if(!getMarket().hasReserves()) {
			fixVariables(0, rcd_bid, rcu_bid);
			fixVariables(0, rdd_bid, rdu_bid);
		} else if(!getConfig().considerV2G())
			fixVariables(0, rdu_bid, rdd_bid);
	}
	
	@Override
	protected void initiliazeVars() {
		super.initiliazeVars();
		int nLoads = getLoads().getNLoads();
		int nTimeSteps = getMarket().getNPTUs();
		rcu_bid = (Variable[][]) newVarArray("rcu_bid", VarType.PositiveContinuous, nLoads, nTimeSteps);
		rcd_bid = (Variable[][]) newVarArray("rcd_bid", VarType.PositiveContinuous, nLoads, nTimeSteps);
		rdu_bid = (Variable[][]) newVarArray("rdu_bid", VarType.PositiveContinuous, nLoads, nTimeSteps);
		rdd_bid = (Variable[][]) newVarArray("rdd_bid", VarType.PositiveContinuous, nLoads, nTimeSteps);
	}
	
	@Override
	protected LinExp getRcd(int e, int t, int i) {
		return new LinExp(rcd_bid[e][t]);
	}

	@Override
	protected LinExp getRcu(int e, int t, int i) {
		return new LinExp(rcu_bid[e][t]);
	}

	@Override
	protected LinExp getRdd(int e, int t, int i) {
		if(considerV2G())
			return new LinExp(rdd_bid[e][t]);
		return new LinExp();
	}

	@Override
	protected LinExp getRdu(int e, int t, int i) {
		if(considerV2G())
			return new LinExp(rdu_bid[e][t]);
		return new LinExp();
	}

}

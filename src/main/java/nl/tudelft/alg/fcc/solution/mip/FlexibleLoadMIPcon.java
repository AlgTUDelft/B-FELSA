package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * Extension of the FlexibleLoadMIP that has continues variables for the pc and pd variables
 */
public abstract class FlexibleLoadMIPcon extends FlexibleLoadMIP {
	Variable[][]  
			pu_bid, // the scheduled downward regulating price bid for every EV, for every time step (if applicable), continuous
			pd_bid, // the scheduled downward regulating price bid for every EV, for every time step (if applicable), continuous
			pc, // the scheduled amount of 'charge' for every EV, every time step, positive continuous
			pd; // the scheduled amount of 'discharge' for every EV, every time step, positive continuous

	public FlexibleLoadMIPcon(FlexibleLoadProblem problem) {
		super(problem);
	}
	
	@Override
	protected void fixVariables() {
		super.fixVariables();
		if(!getConfig().considerV2G())
			fixVariables(0, pd);
	}
	
	@Override
	protected void setVars() {
		super.setVars();
		addVars(pu_bid, pd_bid, pc, pd);
	}

	@Override
	protected void initiliazeVars() {
		super.initiliazeVars();
		int nLoads = getLoads().getNLoads();
		int nTimeSteps = getMarket().getNPTUs();
		pc = (Variable[][]) newVarArray("pc", VarType.PositiveContinuous, nLoads, nTimeSteps);
		pd = (Variable[][]) newVarArray("pd", VarType.PositiveContinuous, nLoads, nTimeSteps);
		pu_bid = (Variable[][]) newVarArray("pu_bid", VarType.Real, nLoads, nTimeSteps);
		pd_bid = (Variable[][]) newVarArray("pd_bid", VarType.Real, nLoads, nTimeSteps);
	}

	@Override
	public void writeSolution() throws SolverException {
		super.writeSolution();
		DecisionVariables d = problem.getVars();
		writeVarsBack(pc, d.p);
		if(getConfig().considerV2G())
			writeVarsBack(pd, d.dp);
		writePriceBidSolution();
	}

	/**
	 * Find the optimal reserve price bids and save the solution 
	 */
	protected abstract void writePriceBidSolution();

	@Override
	protected LinExp getPc(int e, int t) {
		return new LinExp(pc[e][t]);
	}
	@Override
	protected LinExp getPd(int e, int t) {
		if(considerV2G())
			return new LinExp(pd[e][t]);
		return new LinExp();
	}

	@Override
	public boolean isSolvable() {
		if (!problem.getMarket().getReservesMarketClearance().equalsIgnoreCase("paid as cleared")) return false;
		return true;
	}
}

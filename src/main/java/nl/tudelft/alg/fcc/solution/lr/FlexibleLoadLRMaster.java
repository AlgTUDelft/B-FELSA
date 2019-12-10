package nl.tudelft.alg.fcc.solution.lr;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.solution.mip.CompactStochasticModel;

/**
 * The master problem for the lagrangian relaxation the stochastic model
 */
public class FlexibleLoadLRMaster extends CompactStochasticModel {
	FlexibleLoadLRProblem step;
	Variable solution;
	
	public FlexibleLoadLRMaster(FlexibleLoadProblem p, FlexibleLoadLRProblem s) {
		super(p);
		step = s;
	}

	@Override
	protected void fixVariables() {
		super.fixVariables();
		if(clusterModel!=null)
			clusterModel.fixClusterVariables(step.getFd(), step.getFu());
	}
	
	@Override
	protected void setObjectiveFunction() {
		super.setObjectiveFunction();
		LinExp right = new LinExp(solution);
		addConstraint((LinExp) objectiveFunction, right, CMP.EQ, "Objective");
		objectiveFunction = new LinExp(solution);	
	}
	
	@Override
	protected void setVars() {
		super.setVars();
		addVars(solution);
	}

	@Override
	//need to initialize variables
	protected void initiliazeVars() {
		super.initiliazeVars();
		solution = (Variable) newVarArray("solu",VarType.Real); 
	}

	//Write the model solution back to the problem instance
	@Override
	public void writeSolution(IMIPSolver solver) throws SolverException {
		double solution = 0;
		solution = this.solution.getSolution();
		step.setUPObj(solution);
		if(step.getBestSolution()>solution || step.getiteration() == 1) {
			super.writeSolution(solver);
			step.setBestSolution(solution);
		}
	}
}
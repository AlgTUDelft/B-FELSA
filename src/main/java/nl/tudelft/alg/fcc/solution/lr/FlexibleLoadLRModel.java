package nl.tudelft.alg.fcc.solution.lr;

import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.MipSolverCore.LRModel;
import nl.tudelft.alg.MipSolverCore.LRProblem;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * The lagrangian model class for the Flexible load problem
 */
public class FlexibleLoadLRModel extends LRModel<FlexibleLoadProblem> {
	FlexibleLoadLRProblem lrProblem;
	
	public FlexibleLoadLRModel(FlexibleLoadProblem problem) {
		super(problem);
	}
	
	@Override
	public void initialize(ISolver solver) {
		getMasterProblemModel(getLagrangianProblem()).initialize(solver);
		super.initialize(solver);
	}
	
	@Override
	public int getNSubproblems() {
		return problem.getLoads().getNLoads();
	}

	@Override
	public MIP getSubproblemModel(int e, LRProblem<FlexibleLoadProblem> relax) {
		return new FlexibleLoadLRSubproblem(problem, e, (FlexibleLoadLRProblem) relax);
	}

	@Override
	public MIP getMasterProblemModel(LRProblem<FlexibleLoadProblem> relax) {
		return new FlexibleLoadLRMaster(problem, (FlexibleLoadLRProblem) relax);
	}
	
	@Override
	public void finishSubProblems() {
		super.finishSubProblems();
		if(problem.getConfig().getModelSetting().toLowerCase().equals("gr")) {
			lrProblem.defineCLgreedy();
		}
	}

	@Override
	public LRProblem<FlexibleLoadProblem> getLagrangianProblem() {
		if(lrProblem != null) return lrProblem;
		lrProblem = new FlexibleLoadLRProblem(this);
		if(problem.getNLoads() == 1) {
			lrProblem.defineSingleEV();
		}
		return lrProblem;
	}

	@Override
	public void printSolution() {
		//TODO
	}

}

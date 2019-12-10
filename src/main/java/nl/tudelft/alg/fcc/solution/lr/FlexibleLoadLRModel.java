package nl.tudelft.alg.fcc.solution.lr;

import nl.tudelft.alg.MipSolverCore.LRModel;
import nl.tudelft.alg.MipSolverCore.LRProblem;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * The lagrangian model class for the Flexible load problem
 */
public class FlexibleLoadLRModel extends LRModel<FlexibleLoadProblem> {

	public FlexibleLoadLRModel(FlexibleLoadProblem problem) {
		super(problem);
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
	public LRProblem<FlexibleLoadProblem> getLagrangianProblem() {
		return new FlexibleLoadLRProblem(this);
	}

	@Override
	public void printSolution() {
		//TODO
	}

}

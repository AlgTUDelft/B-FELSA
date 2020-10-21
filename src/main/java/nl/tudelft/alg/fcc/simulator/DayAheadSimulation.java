package nl.tudelft.alg.fcc.simulator;


import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;
import nl.tudelft.alg.fcc.problem.Result;

public class DayAheadSimulation {
	
	public static Result run(Simulator simulator, ProblemConfiguration pConfig) throws SolverException, InvalidConfigurationException {
		FlexibleLoadProblem problem = simulator.createProblem(pConfig, 0);
		ResultChecker checker = simulator.createChecker(pConfig);
		if(problem.getMarket().hasDayAhead() && simulator.config.dayAheadSeperate) {
			FlexibleLoadProblem daProblem = simulator.createProblem(pConfig, -12);
			daProblem.getMarket().ignoreReserves();
			simulator.solve(daProblem);
			checker.setPreviousDecisions(daProblem.getVars());
			problem.getMarket().fixDayAhead();
			setVariables(daProblem, problem); //Copy the da data to the problem to be used for fixing
		} else if(!problem.getMarket().hasDayAhead())
			problem.getMarket().ignoreDayAhead();
		problem.getConfig().setFixedPTUs(0);
		checker.getConfig().setFixedPTUs(0);
		long start = System.nanoTime();
		simulator.solve(problem);
		long duration = System.nanoTime() - start;
		checker.writeResult(problem);
		Result r = checker.check();
		if (simulator.config.verbose >= 2) problem.getVars().printSolution(problem);
		r.setRuntime(duration);
		simulator.printResultsToFile(pConfig.getOutputFolder(), problem.getVars(), "", r);
		return r;
	}
	
	//Copy the data in the da-arrays to the problem instance (used to fix variables)
	private static void setVariables(FlexibleLoadProblem prevProblem, FlexibleLoadProblem problem) {
		problem.getVars().pda = prevProblem.getVars().pda;
	}
	
}

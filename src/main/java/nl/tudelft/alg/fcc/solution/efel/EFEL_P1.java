package nl.tudelft.alg.fcc.solution.efel;

import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.solution.mip.CompactStochasticModel;

//Solves the imbalance reserve problem using stochastic optimization for multiple EVs
//The model results in at most one regulation bid for every PTU.
//Use the InbalanceReserveStochasticCompact for better run time results
public class EFEL_P1 extends CompactStochasticModel {
	Cluster cluster[]; //class where all the information per cluster is stored and that links each EV to a cluster
	ClusteredLoads eqevLoads;

	public EFEL_P1(FlexibleLoadProblem p) {
		super(p);
		cluster = problem.createcluster();
		nLoads = cluster.length; //This is now the number of eqEVs or clusters
		eqevLoads = new ClusteredLoads(p.getLoads(), cluster);
		eqevLoads.setProblem(problem);
		loadModel = new ClusteredLoadModel(loadModel.getAllScenariosMarket());
	}
	
	@Override
	public Loads getLoads() {
		return eqevLoads;
	}
	
	public Loads getOrgLoads() {
		return problem.getLoads();
	}

	//Write the model solution back to the problem instance - Those variables that belong to many EVs in a single cluster, only write the solution in the first EV or the eqEV
	@Override
	public void writeSolution(IMIPSolver solver) throws SolverException {
		DecisionVariables d = new DecisionVariables(nLoads, nTimeSteps, getMarket().getNumberOfHours(), getMarket().getStartT(), getMarket().getPTU());
		problem.setVars(d);
		super.writeSolution(solver);
		d.printSolution(problem);
		problem.setVars(new DecisionVariables(problem));
		solveSubproblem(solver, d); 
	}
	
	private void solveSubproblem(IMIPSolver solver, DecisionVariables d) throws SolverException {
		MIP model = new EFEL_P2(problem, d, cluster);
		model.initialize();
		solver.build(model);
		solver.save("mip.lp");
		solver.solve();
	}
	
	
	public Cluster getCluster(int e) {
		return cluster[e];
	}

}

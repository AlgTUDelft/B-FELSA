package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

//Solves the imbalance reserve problem using stochastic optimization for multiple EVs
//With less constraints and variables than AImbalanceReserveStochastic, this model can be solved faster
public class CompactStochasticModel extends
		StochasticModel {

	public CompactStochasticModel(FlexibleLoadProblem p) {
		super(p);
		this.loadModel = new StochasticCompactLoadModel(loadModel.getAllScenariosMarket());
	}	

}


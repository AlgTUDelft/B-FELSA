package nl.tudelft.alg.fcc.solution;

import nl.tudelft.alg.MipSolverCore.IModel;
import nl.tudelft.alg.MipSolverCore.SolverException;

//A model class for models that provide a solve function, and do not need an external solver to be solved
public interface ISolveModel extends IModel {
	public void solve() throws SolverException;
}

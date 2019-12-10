package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * The simple optimal price charging model for multiple flexible loads 
 */
public class OptimalPriceModel extends FlexibleLoadMIP {
	/**
	 * the amount of scheduled charge per load, per PTU
	 */
	Variable[][] pc;
	
	public OptimalPriceModel(FlexibleLoadProblem p) {
		super(p);
	}

	@Override
	protected void setVars() {
		super.setVars();
		addVars(pc);		
	}

	@Override
	protected void initiliazeVars() {
		super.initiliazeVars();
		pc = (Variable[][]) newVarArray("pc", VarType.PositiveContinuous, nLoads, nTimeSteps);
	}

	@Override
	public void writeSolution(IMIPSolver solver) throws SolverException {
		super.writeSolution(solver);
		DecisionVariables d = problem.getVars();
		writeVarsBack(pc, d.p);
	}

	@Override
	protected LinExp getPc(int e, int t) {
		return new LinExp(pc[e][t]);
	}

	@Override
	protected LinExp getPd(int e, int t) {
		return new LinExp();
	}

	@Override
	protected LinExp getRcd(int e, int t, int i) {
		return new LinExp();
	}

	@Override
	protected LinExp getRcu(int e, int t, int i) {
		return new LinExp();
	}

	@Override
	protected LinExp getRdd(int e, int t, int i) {
		return new LinExp();
	}

	@Override
	protected LinExp getRdu(int e, int t, int i) {
		return new LinExp();
	}
	
	@Override
	public void printSolution() {
		System.out.print("t\t");
		for(int i=0; i<nLoads; i++) System.out.print("p"+i+"\t");
		System.out.println("l");
		for(int t=0; t<nTimeSteps; t++) {
			System.out.print(t+"\t");
			for(int i=0; i< nLoads; i++) {
				System.out.format("%.5f\t", this.pc[i][t].getSolution());
			}
			System.out.format("%.2f", problem.getMarket().getExpectedImbalancePrice(t));
			System.out.println();
		}
	}

	@Override
	public boolean isSolvable() {
		return true;
	}
}
package nl.tudelft.alg.fcc.solution;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;

import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.utils.CSVReader;

public class SolveFromFile implements ISolveModel {
	private FlexibleLoadProblem problem;
	private String[][] solution;

	public SolveFromFile(FlexibleLoadProblem problem) {
		this.problem = problem;
		this.solution = null;
	}

	@Override
	public void printSolution() {

	}

	@Override
	public void initialize(ISolver solver) throws SolverException {
		try {
			solution = CSVReader.readCsvFile(problem.getConfig().getSolutionFile());
		} catch (FileNotFoundException e) {
			throw new SolverException("Solution file not found", e);
		}
	}

	@Override
	public boolean isSolvable() {
		String file = problem.getConfig().getSolutionFile();
		return file != null && new File(file).exists() && problem.getConfig().getNLoads() == 1;
	}

	@Override
	public void solve() throws SolverException {
		if(solution == null || solution.length == 0) return;
		DecisionVariables dec = problem.getVars();
		List<String> columns = Arrays.asList(solution[0]);
		final int tIndex = columns.indexOf("t");
		final int pIndex = columns.indexOf("p");
		final int dpIndex = columns.indexOf("dp");
		final int rcdIndex = columns.indexOf("rcd");
		final int rcuIndex = columns.indexOf("rcu");
		final int rddIndex = columns.indexOf("rdd");
		final int rduIndex = columns.indexOf("rdu");
		final int buIndex = columns.indexOf("bu");
		final int bdIndex = columns.indexOf("bd");
		final int pdaIndex = columns.indexOf("pda");
		final int pimbIndex = columns.indexOf("pimb");
		if (tIndex == -1) return;
		for (int i = 1; i < solution.length; i++) {
			String[] row = solution[i];
			for (int t = 0; t < row.length; t++)
				row[t] = row[t].equals("") ? "0" : row[t];
			String date = row[tIndex];
			int t=0;
			try {
				t = problem.getTimeStepFromDateString(date) - problem.getStartT();
			} catch(Exception e) {
				throw new SolverException("Error in reading time stamp in solution file", e);
			}
			if (t >= dec.nTimesteps || t < 0) continue;
			if (pIndex >= 0) dec.p[0][t] = Double.parseDouble(solution[i][pIndex]);
			if (dpIndex >= 0) dec.dp[0][t] = Double.parseDouble(solution[i][dpIndex]);
			if (rcdIndex >= 0) dec.rcd[0][t] = Double.parseDouble(solution[i][rcdIndex]);
			if (rcuIndex >= 0) dec.rcu[0][t] = Double.parseDouble(solution[i][rcuIndex]);
			if (rddIndex >= 0) dec.rdd[0][t] = Double.parseDouble(solution[i][rddIndex]);
			if (rduIndex >= 0) dec.rdu[0][t] = Double.parseDouble(solution[i][rduIndex]);
			if (buIndex >= 0) dec.bu[0][t] = Double.parseDouble(solution[i][buIndex]);
			if (bdIndex >= 0) dec.bd[0][t] = Double.parseDouble(solution[i][bdIndex]);
			if (pdaIndex >= 0) dec.pda[dec.PTUtoH(t)] = Double.parseDouble(solution[i][pdaIndex]);
			if (pimbIndex >= 0) dec.pimb[t] = Double.parseDouble(solution[i][pimbIndex]);
		}
		if (pimbIndex == -1) {
			dec.imbalancePurchaseFromCharging();
		}
	}

}

package nl.tudelft.alg.fcc.simulator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.problem.OnlineResult;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;
import nl.tudelft.alg.fcc.problem.Result;
import nl.tudelft.alg.fcc.utils.Utils;

public class OnlineSimulation {
		
	//Run a rolling horizon simulation
	public static OnlineResult run(Simulator simulator, ProblemConfiguration pConfig) throws SolverException, InvalidConfigurationException, IOException {
		FlexibleLoadProblem problem = simulator.createProblem(pConfig, -12);
		ResultChecker checker = simulator.createChecker(pConfig);
		FlexibleLoadProblem prevProblem;
		int startT = problem.getStartT();
		int nTimeSteps = problem.getNTimeSteps();
		int nLoads = simulator.config.nLoads;
		double[] soc = problem.getLoads().getArrivalSOCs();
		double startSoc = Utils.sum(soc);
		double cost = 0;
		int da = problem.getMarket().hasDayAhead() ? 1 : 0;
		Result[] results = new Result[nTimeSteps + da];
		Result[] resultsAll = new Result[nTimeSteps];
		OnlineResult onlineResult = new OnlineResult(nTimeSteps);
		
		//Build and solve the DA model
		if(problem.getMarket().hasDayAhead()) {
			problem.getMarket().ignoreReserves();
			problem.getConfig().setFixedPTUs(0);
			simulator.solve(problem);
			checker.writeResult(problem);
			Result dar = checker.checkDA();
			cost += dar.getAvgCost();
			results[0] = dar;
			println(simulator.config.verbose,
					neatDouble(Utils.sum(dar.getAvgDA())) + " MWh bought DA.\tCost from DA: \u20ac "
							+ neatDouble(dar.getAvgCost()) + ".\tAverage: "
							+ neatDouble(dar.getAvgCost() / Utils.sum(dar.getAvgDA())) + " \u20ac/MWh");
		}
		println(simulator.config.verbose, "timestep\tsoc\tcost\tdsoc\tpda\tp\trd\tru\timb\tpd\tpu\truntime\tavgcost\tnew avgcost");
		for (int t = 0; t < nTimeSteps; t++) {
			prevProblem = problem;
			problem = simulator.createProblem(pConfig, t);
			checker = simulator.createChecker(pConfig);
			checker.setPreviousDecisions(prevProblem.getVars());
			
			problem.getLoads().setArrivalSOCs(soc); //Reset the arrival soc to the current soc
			problem.setStartT(startT + t); //Set the problem start to the current time
			problem.setNTimeSteps(nTimeSteps - t); //Set the number of remaining time steps to the current remaining time steps
			problem.getMarket().fixDayAhead();
			if(t==0) {
				problem.getConfig().setFixedPTUs(0);
				checker.getConfig().setFixedPTUs(0);
			}
			setVariables(prevProblem, problem); //Copy the ru, rd, pu and pd data to the problem to be used for fixing
			
			//Build and solve the model
			long start = System.nanoTime();
			simulator.solve(problem);
			long duration = System.nanoTime() - start;
			
			//Check the model result
			checker.writeResult(problem);
			checker.getProblem().getMarket().ignoreDayAhead(); //Ignore DA in check
			Result resultAll = checker.check();
			checker.getProblem().setNTimeSteps(1); //Run the checker for only one PTU
			Result resultOne = checker.check();

			resultAll.setRuntime(duration);
			resultOne.setRuntime(duration);
			problem.setNTimeSteps(nTimeSteps-t);
			cost += resultOne.getAvgCost();
			printSummary(simulator, problem, cost, resultOne.getAvgCost(), soc, resultOne, duration, startSoc);
			onlineResult.addDecision(t, problem.getVars());
			soc = resultOne.getAvgSoc();
			results[da + t] = resultOne;
			if (t > 0)
				resultAll = resultAll.padWithZeros(resultsAll[t - 1], Result.combine(Arrays.copyOfRange(results, 0, da + t)));
			else if (da == 1)
				resultAll.incCost(results[0].getAvgCost());
			resultsAll[t] = resultAll;
			if(simulator.config.fileOutput > 2) {
				String folder = Paths.get(simulator.getFolder(pConfig.getOutputFolder()), "PTU " + t, "data").toString();
				new File(folder).mkdirs();
				problem.getMarket().getPricedata().writeToFile(folder);
			}
			
		}

		printEndingSummary(simulator, problem, cost, results[results.length - 1], startSoc);
		
		onlineResult.addEvaluation(Result.combine(results));
		onlineResult.addEvaluationList(resultsAll);
		
		for(int e=0; e<nLoads; e++) {
			//assert soc[e] <= problem.getLoads().getBatteryCapacity(e) + 1e-3;
			//assert soc[e] >= problem.getLoads().getMinimumSOC(e) - 1e-3;
		}
		return onlineResult;
	}
	
	private static String neatDoubleIf(double d) {
		return Math.abs(d) > 1e-5 ? String.format("%.4f", d) : "       ";
	}
	
	private static String neatDouble(double d) {
		return String.format("%.4f", d);
	}
	
	private static void printSummary(Simulator simulator, FlexibleLoadProblem problem, 
			double cost, double dCost, double[] oldsoc, Result result, long duration, double startSoc) {
		DecisionVariables d = problem.getVars();
		double socsum = result.getAvgSocSum();
		double dsoc = socsum - Utils.sum(oldsoc); 
		double avgcost = socsum - startSoc != 0 ? cost / (socsum - startSoc) : 0;
		double newAvgcost = (dsoc == 0) ? 0.0 : dCost / dsoc; 
		double psum = Arrays.stream(d.p).mapToDouble(_p -> _p[0]).sum();
		if(problem.getConfig().considerV2G())
			psum -= Arrays.stream(d.dp).mapToDouble(_p -> _p[0]).sum();
		double pda = result.getAvgDA()[0];
		double rdsum = Arrays.stream(d.rcd).mapToDouble(_rd -> _rd[0]).sum();
		if(problem.getConfig().considerV2G())
			rdsum += Arrays.stream(d.rdd).mapToDouble(_rd -> _rd[0]).sum();
		double rusum = Arrays.stream(d.rcu).mapToDouble(_ru -> _ru[0]).sum();
		if(problem.getConfig().considerV2G())
			rusum += Arrays.stream(d.rdu).mapToDouble(_ru -> _ru[0]).sum();
		double pdmin = problem.getMarket().hasCapacityPayments() ?
				Arrays.stream(d.bd).mapToDouble(_pd -> _pd[0]).min().orElse(0) :
				Arrays.stream(d.bd).mapToDouble(_pd -> _pd[0]).max().orElse(0);
		double pumin = Arrays.stream(d.bu).mapToDouble(_pu -> _pu[0]).min().orElse(0);
		println(simulator.config.verbose,
				PTUtoNeat(problem, 0) + "\t" + neatDouble(socsum) + "\t" + neatDoubleIf(cost) + "\t" +
				neatDoubleIf(dsoc)+"\t"+neatDoubleIf(pda)+"\t"+neatDoubleIf(psum)+"\t"+neatDoubleIf(rdsum)+"\t"+neatDoubleIf(rusum)+"\t"
				+neatDouble(problem.getMarket().getExpectedImbalancePrice(0))+"\t"+(rdsum > 1e-4 ? neatDouble(pdmin) : "")
				+"\t"+(rusum>1e-4 ? neatDouble(pumin): "")+"\t"+neatDouble(duration/1e9)+"\t"+neatDoubleIf(avgcost)
				+"\t"+neatDoubleIf(newAvgcost));
	}
	
	private static void printEndingSummary(Simulator simulator, FlexibleLoadProblem problem, double cost,
			Result result, double startSoc) {
		final String skip = "     ";
		double socsum = result.getAvgSocSum();
		double avgcost = socsum - startSoc != 0 ? cost / (socsum - startSoc) : 0;
		println(simulator.config.verbose,
				PTUtoNeat(problem, 1) + "\t" + neatDouble(socsum) + "\t" + neatDoubleIf(cost) + "\t" + skip + "\t" + skip
						+ "\t" + skip + "\t" + skip + "\t" + skip + "\t"
						+ neatDouble(problem.getMarket().getExpectedImbalancePrice(1)) + "\t" + "     " + "\t" + "     "
						+ "\t" + neatDouble(0) + "\t" + neatDoubleIf(avgcost) + "\t" + skip);
	}

	private static String PTUtoNeat(FlexibleLoadProblem p, int offset) {
		Calendar cal = (Calendar) p.getConfig().getStartDate().clone(); // first startTime in minutes
		cal.add(Calendar.MINUTE, (p.getStartT() + offset) * (int) (60 * p.getMarket().getPTU()));
		SimpleDateFormat format = new SimpleDateFormat("dd-MM-yy HH:mm");
		return format.format(cal.getTime());
	}

	//Copy the data in the arrays ru, rd, ... to the problem instance (used to fix variables)
	private static void setVariables(FlexibleLoadProblem prevProblem, FlexibleLoadProblem problem) {
		DecisionVariables prevd = prevProblem.getVars();
		DecisionVariables newd = problem.getVars();
		int nLoads = problem.getConfig().getNLoads();
		int nTimeSteps = problem.getNTimeSteps();
		double[][] new_rcu = new double[nLoads][nTimeSteps];
		double[][] new_rcd = new double[nLoads][nTimeSteps];
		double[][] new_rdu = new double[nLoads][nTimeSteps];
		double[][] new_rdd = new double[nLoads][nTimeSteps];
		double[][] new_pu = new double[nLoads][nTimeSteps];
		double[][] new_pd = new double[nLoads][nTimeSteps];
		if(prevProblem.getNTimeSteps() != problem.getNTimeSteps()) {
			for(int e=0; e<nLoads; e++) {
				new_rcu[e] = Arrays.copyOfRange(prevd.rcu[e], 1, nTimeSteps+1);
				new_rcd[e] = Arrays.copyOfRange(prevd.rcd[e], 1, nTimeSteps+1);
				new_rdu[e] = Arrays.copyOfRange(prevd.rdu[e], 1, nTimeSteps+1);
				new_rdd[e] = Arrays.copyOfRange(prevd.rdd[e], 1, nTimeSteps+1);
				new_pu[e] = Arrays.copyOfRange(prevd.bu[e], 1, nTimeSteps+1);
				new_pd[e] = Arrays.copyOfRange(prevd.bd[e], 1, nTimeSteps+1);
			}
		}
		newd.rcu = new_rcu;
		newd.rcd = new_rcd;
		newd.rdu = new_rdu;
		newd.rdd = new_rdd;
		newd.bu = new_pu;
		newd.bd = new_pd;
		newd.nTimesteps = nTimeSteps;
		if(prevProblem.getMarket().getNumberOfHours() == problem.getMarket().getNumberOfHours())
			newd.pda = prevd.pda;
		else {
			double[] new_pda = Arrays.copyOfRange(prevd.pda, 1, problem.getMarket().getNumberOfHours()+1);
			newd.pda = new_pda;
		}
		
	}
	
	private static double getAverageTakenImbalancePrice(FlexibleLoadProblem problem, double[][] p) {
		double totalCharge = 0;
		double totalCost = 0;
		for(int e=0; e<problem.getConfig().getNLoads(); e++) {
			for(int t=problem.getLoads().getStartT(e); t<problem.getLoads().getEndT(e); t++) {
				totalCharge += p[e][t];
				totalCost += p[e][t] * problem.getMarket().getExpectedImbalancePrice(t);
			}
		}
		if(totalCharge == 0) return 0;
		return totalCost / totalCharge;
	}
	
	private static void manualChangingOfBids(FlexibleLoadProblem problem, double[][] p, double[][] pu, double[][] pd) {
		double averageTakenImbalancePrice = getAverageTakenImbalancePrice(problem, p);
		for(int e=0; e<pd.length; e++) {
			for(int t=0; t<pd[e].length; e++) {
				pd[e][t] = Math.max(pd[e][t], 0.0); // 0.0 can be replaced with the expected lowest taken imbalance price
			}
		}
	}
		
	private static void print(int verbose, String s) {
		if(verbose >= 2) System.out.print(s);
	}
	
	private static void println(int verbose, String s) {
		print(verbose, s + "\n");
	}
}

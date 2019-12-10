package nl.tudelft.alg.fcc.simulator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.IModel;
import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.MipSolverCore.LRModel;
import nl.tudelft.alg.MipSolverCore.LRSolver;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.problem.OnlineResult;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;
import nl.tudelft.alg.fcc.problem.Result;
import nl.tudelft.alg.fcc.simulator.data.Data;
import nl.tudelft.alg.fcc.solution.ISolveModel;
import nl.tudelft.alg.fcc.utils.ConsoleOutputCapturer;
import nl.tudelft.alg.fcc.utils.ScenarioSelector;
import nl.tudelft.alg.fcc.utils.Utils;

/**
 * Simulator class to simulate an electricity market, and solve FlexibleLoadProblems with different solution models
 */
public class Simulator {
	DataProvider dataProvider; //The class that generates the data for the simulation and evaluation
	Config config;
	Map<String, IMIPSolver> solvers;

	/**
	 * Load an experiment from a ini file with address 'filename'
	 * @param filename the address of the config (ini) file
	 * @throws IOException when the ini file cannot be found or cannot be read for some other reason
	 * @throws InvalidConfigurationException when the configuration file contains invalid values
	 */
	public Simulator(String filename) throws IOException, InvalidConfigurationException {
		config = new Config(filename);
		dataProvider = new DataProvider(this);
		solvers = new HashMap<String, IMIPSolver>();
	}

	/**
	 * Run the simulation
	 * @throws SolverException when an exception occurs in one of the solvers/solution models
	 * @throws InvalidConfigurationException when the configuration of the problem is invalid
	 * @throws IOException when reading or writing a file results in an error
	 */
	public void run() throws SolverException, InvalidConfigurationException, IOException {
		init();
		Result[] results = new Result[config.getNumberOfRuns()];
		if (config.nTests * config.nEvaluationScenarios * config.getNumberOfRuns() * config.nLoads == 0)
			return;
		if (config.verbose == 1)
			System.out.println(config.getColumnHeaders() + "\tCharging costs\tShortage\tOverflow\tTotal costs\tRun time");
		for (int i = 0; i < config.getNumberOfRuns(); i++) {
			ProblemConfiguration pConfig = config.setProblemConfiguration(i);
			PriceScenarioData[] realization;
			reset();
			if (config.isOnline()) {
				OnlineResult onlineResult = null;
				realization = new PriceScenarioData[config.nTests * config.nEvaluationScenarios];
				for (int t = 0; t < config.nTests; t++) {
					pConfig = config.setProblemConfiguration(i, t);
					for (int r = 0; r < config.nEvaluationScenarios; r++) {
						config.evaluationScenario = (config.startEvaluationScenario + r) % dataProvider.getNScenarios();
						pConfig.setRealization(dataProvider.getRealization());
						OnlineResult newResult = OnlineSimulation.run(this, pConfig);
						if (onlineResult == null) onlineResult = newResult;
						else onlineResult.concat(newResult);
						realization[t * config.nEvaluationScenarios + r] = pConfig.getPriceDataRealization();
					}
				}
				printResultsToFile(pConfig.getOutputFolder(), onlineResult);
				results[i] = onlineResult.evaluation;
			} else {
				realization = new PriceScenarioData[config.nTests];
				for (int t = 0; t < config.nTests; t++) {
					pConfig = config.setProblemConfiguration(i, t);
					pConfig.setRealization(dataProvider.getRealization());
					Result res = DayAheadSimulation.run(this, pConfig);
					if (results[i] == null) results[i] = res;
					else results[i].concat(res);
					realization[t] = pConfig.getPriceDataRealization();
				}
			}
			if (config.verbose >= 1 && !config.output.equals("boxplot")) {
				System.out.print(config.toStringTabbed(i) + "\t");
				System.out.println(results[i].toString(config.resultShortagePenalty));
			}
			realizationOutput(pConfig.getOutputFolder(), realization);
		}
		Result.boxplotOutput(getFolder(config.getBoxplotOutputFile()), config, results);
		dispose();
	}

	/**
	 * Reset the state of the simulator (ie. the random number seed)
	 * @throws IOException when a reload of the data results in a file read error
	 */
	private void reset() throws IOException {
		initRand(this); //Reset the random seed so that every experiment gives the same results
		ScenarioSelector.resetCache();
		if(dataProvider.data.getMarket().getPTU() == config.ptu)
			dataProvider.data.reset();
		else
			dataProvider = new DataProvider(this);
	}

	/**
	 * Load and dispose the solver, so that the Gurobi license notice does not interfere with the result output  
	 * @throws SolverException when the mip solver cannot be loaded
	 */
	private void supressLicenseLine() throws SolverException {
		IMIPSolver solver = getMIPSolver();
		solver.dispose();
	}

	/**
	 * Initialize the random seed to a fixed number, or random if randomSeed = 0
	 * @param simulator the simulator
	 */
	private static void initRand(Simulator simulator) {
		Utils.initRand(simulator.config.randomSeed);
		DataProvider.initRand(simulator.config.randomSeed);
	}

	/**
	 * Initialize the simulator
	 * @throws SolverException when the mip solver cannot be loaded
	 */
	private void init() throws SolverException {
		supressLicenseLine();
		if (config.fileOutput >= 1) ConsoleOutputCapturer.redirectToFile(getFolder(config.getOutputFile()));
	}

	/**
	 * Dispose the simulator
	 */
	private void dispose() {
		ConsoleOutputCapturer.stop();
		for (IMIPSolver solver : solvers.values()) {
			solver.dispose();
		}
		solvers.clear();
	}

	/**
	 * Create FlexibleLoadProblem instance
	 * @param pConfig based on the problem configuration pConfig
	 * @param t starting at time step t
	 * @return the FlexibleLoadProblem instance
	 * @throws InvalidConfigurationException when the problem configuration is invalid
	 */
	public FlexibleLoadProblem createProblem(ProblemConfiguration pConfig, int t) throws InvalidConfigurationException {
		Data data = dataProvider.getScenarioTree(pConfig.getRealization(), t);
		FlexibleLoadProblem problem = new FlexibleLoadProblem(pConfig.clone(), data);
		return problem;
	}

	/**
	 * Creates a result checker
	 * @param pConfig based on the problem configuration pConfig
	 */
	public ResultChecker createChecker(ProblemConfiguration pConfig) {
		Data data = pConfig.getRealization();
		FlexibleLoadProblem problem = new FlexibleLoadProblem(pConfig.clone(), data);
		if (config.isOnline() && config.perMinute)
			return new PerMinuteResultChecker(problem);
		return new ResultChecker(problem);
	}

	/**
	 * @return the MIP solver based on the configuration
	 * @throws SolverException when the MIP solver cannot be instantiated.
	 */
	public IMIPSolver getMIPSolver() throws SolverException {
		if (solvers.containsKey(config.mipsolver)) return solvers.get(config.mipsolver);
		String className = "";
		switch (config.mipsolver.toLowerCase()) {
			case "gurobi":
				className = "nl.tudelft.alg.MipSolverGurobi.MIPSolver";
				break;
			case "glpk":
				className = "nl.tudelft.alg.MipSolverGLPK.GLPKSolver";
				break;
		}
		IMIPSolver solver;
		try {
			solver = (IMIPSolver) Class.forName(className).newInstance();
			solver.setDebug(config.mipDebug);
			solver.setLogFile(config.mipLogFile);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw new SolverException("MIP Solver not found: " + config.mipsolver + " (" + className + ")", e);
		}
		solvers.put(config.mipsolver, solver);
		return solver;
	}

	/**
	 * @return the solver
	 * @throws SolverException when the solver cannot be instantiated
	 */
	public ISolver getSolver() throws SolverException {
		if (config.model.equals("LR"))
			return new LRSolver<FlexibleLoadProblem>(getMIPSolver());
		return getMIPSolver();
	}

	/**
	 * @param problem the problem to solve
	 * @return the model that can solve the problem based on the configuration
	 * @throws InvalidConfigurationException when the provided model configuration is invalid 
	 */
	private IModel getModel(FlexibleLoadProblem problem) throws InvalidConfigurationException {
		try {
			return SolverMap.valueOf(config.model).getType().getConstructor(problem.getClass()).newInstance(problem);
		} catch (Exception e) {
			throw new InvalidConfigurationException(String.format("Model '%s' is not a valid model specifier.", config.model), e);
		}
	}

	/**
	 * Solve the FlexibleLoadProblem
	 * @param problem the problem
	 * @throws SolverException when the solver cannot be instantiated
	 * @throws InvalidConfigurationException when the model is not suitable for solving this problem configuration
	 */
	public void solve(FlexibleLoadProblem problem) throws SolverException, InvalidConfigurationException {
		IModel model = getModel(problem);
		if (!model.isSolvable())
			throw new InvalidConfigurationException("The configuration provided is invalid for this solution model ("+model.getClass().getSimpleName()+ ")");
		if (model instanceof MIP || model instanceof LRModel) {
			ISolver solver = getSolver();
			buildModel(solver, model);
			if (config.mipDebug) solver.save("mip.lp");
			solver.solve();
		} else if (model instanceof ISolveModel) {
			model.initialize();
			((ISolveModel) model).solve();
		}
	}

	/**
	 * Build the model
	 * @param solver the solver used to build the model
	 * @param model the model to be build
	 * @throws SolverException when an exception occurs in the solver
	 * @throws InvalidConfigurationException when the configuration is invalid
	 */
	private IModel buildModel(ISolver solver, IModel model) throws SolverException, InvalidConfigurationException {
		model.initialize();
		solver.build(model);
		solver.setMipGap(config.mipGap);
		solver.setTimeLimit(config.mipTimeLimit);
		return model;
	}

	/**
	 * Get the output folder
	 * @param info based on the info string
	 */
	public String getFolder(String info) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HHmmss");
		String folder = Paths.get(config.outputFolder, dateFormat.format(config.startTime.getTime()), info).toString();
		return folder;
	}

	/**
	 * The base folder for this run
	 * @param info run description (used to create a folder)
	 */
	private String getBaseFolder(String info) {
		String folder = getFolder(info);
		String basefolder = new File(folder).getParentFile().toString();
		if (basefolder.equals(config.outputFolder)) basefolder = folder;
		return basefolder;
	}

	/**
	 * Copy the configuration file to the base folder
	 * @param info simulation description (used to create a folder)
	 */
	private void copyConfigFile(String info) {
		String basefolder = getBaseFolder(info);
		try {
			Files.copy(new File(config.expFileName).toPath(), Paths.get(basefolder, "experiment.ini"),
					StandardCopyOption.REPLACE_EXISTING);
		
		} catch (IOException e) {
			System.out.println("Error in copying config file: " + e.getMessage());
		}
	}

	/**
	 * Print the results to files
	 * @param info simulation description (used to create a folder)
	 * @param vars the variables to output
	 */
	public void printResultsToFile(String info, DecisionVariables vars) {
		if (config.fileOutput < 1) return;
		String folder = getFolder(info);
		new File(folder).mkdirs();
		try {
			vars.printResultsToFile(folder);
		} catch (IOException e) {
			System.out.println("Error in printing results: " + e.getLocalizedMessage());
		}
		copyConfigFile(info);
	}

	/**
	 * Print the results to files
	 * @param info simulation description (used to create a folder)
	 * @param vars the variables to output
	 * @param rInfo run description (used to create a folder)
	 * @param result the results to output
	 */
	public void printResultsToFile(String info, DecisionVariables vars, String rInfo, Result result) {
		if (config.fileOutput < 1) return;
		printResultsToFile(info, vars);
		String folder = Paths.get(getFolder(info), "eval", rInfo).toString();
		new File(folder).mkdirs();
		try {
			result.printResultsToFile(folder, config.resultShortagePenalty);
		} catch (IOException e) {
			System.out.println("Error in printing results: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Print the results to files
	 * @param info simulation description (used to create a folder)
	 * @param results the results to output
	 */
	public void printResultsToFile(String info, Result[] results) {
		if (config.fileOutput < 1) return;
		String folder = Paths.get(getFolder(info), "eval").toString();
		new File(folder).mkdirs();
		try {
			Result.combine(results).printResultsToFile(folder, config.resultShortagePenalty);
		} catch (IOException e) {
			System.out.println("Error in printing results: " + e.getLocalizedMessage());
		}
	}

	/**
	 * Print the results to files
	 * @param info simulation description (used to create a folder)
	 * @param onlineResult the results to output
	 */
	public void printResultsToFile(String info, OnlineResult onlineResult) {
		if (config.fileOutput < 1) return;
		String folder = Paths.get(getFolder(info), "eval").toString();
		new File(folder).mkdirs();
		copyConfigFile(info);
		try {
			onlineResult.evaluation.printResultsToFile(folder, config.resultShortagePenalty);
		} catch (IOException e) {
			System.out.println("Error in printing results: " + e.getLocalizedMessage());
		}
		if (config.fileOutput < 2) return;
		String baseFolder = getFolder(info);
		for(int t=0; t<onlineResult.decisions.length; t++) {
			folder = Paths.get(baseFolder, "PTU " + t).toString();
			new File(folder).mkdirs();
			try {
				DecisionVariables.printResultsToFile(onlineResult.decisions[t], folder);
			} catch (IOException e) {
				System.out.println("Error in printing results: " + e.getLocalizedMessage());
			}
		}
		for (int t = 0; t < onlineResult.evaluationList.length; t++) {
			folder = Paths.get(baseFolder, "PTU " + t, "eval").toString();
			new File(folder).mkdirs();
			try {
				onlineResult.evaluationList[t].printResultsToFile(folder, config.resultShortagePenalty);
			} catch (IOException e) {
				System.out.println("Error in printing results: " + e.getLocalizedMessage());
			}
		}
	}
	
	private void realizationOutput(String info, PriceScenarioData[] realization) throws IOException {
		if (config.fileOutput < 2) return;
		PriceScenarioData data = realization[0];
		for(int i=1; i<realization.length; i++) {
			data = data.concat(realization[i]);
		}
		String folder = Paths.get(getFolder(info), "data").toString();
		new File(folder).mkdirs();
		data.writeToFile(folder);
	}

	public String getModelType() {
		return this.config.model;
	}

}

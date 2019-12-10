package nl.tudelft.alg.fcc.simulator;

import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;

import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.model.FlexibleLoad;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.data.ArimaGenerator;
import nl.tudelft.alg.fcc.simulator.data.Data;
import nl.tudelft.alg.fcc.simulator.data.DataGenerator;
import nl.tudelft.alg.fcc.simulator.data.HistoricDataGenerator;
import nl.tudelft.alg.fcc.simulator.data.HistoricGradientDataGenerator;
import nl.tudelft.alg.fcc.simulator.data.PerfectInformationDataGenerator;
import nl.tudelft.alg.fcc.utils.ScenarioSelection;
import nl.tudelft.alg.fcc.utils.ScenarioSelector;

public class DataProvider {
	Simulator simulator;
	Data data;
	Config config;
	static Random realizationRandom;
	static Random scenariosRandom;

	public DataProvider(Simulator simulator) throws IOException {
		this.simulator = simulator;
		this.config = simulator.config;
		this.data = new Data(config);
	}
	
	 public static void initRand(int seed) {
	    	if(seed != 0) {
	    		realizationRandom = new Random(seed);
	    		scenariosRandom = new Random(seed+1);
	    	} else {
				realizationRandom = new Random();
				scenariosRandom = new Random();
	    	}
	    }

	protected DataGenerator getRealizationGenerator() throws InvalidConfigurationException {
		switch (config.realizationType.toLowerCase()) {
			case "historic":
				return new HistoricDataGenerator();
			case "arima":
				return new ArimaGenerator(realizationRandom);
			default:
				throw new InvalidConfigurationException("Realization type not known: " + config.realizationType);
		}
	}

	protected DataGenerator getScenarioTreeGenerator() throws InvalidConfigurationException {
		switch (config.generatorType.toLowerCase()) {
			case "historic":
				return new HistoricDataGenerator();
			case "historic-gradient":
				return new HistoricGradientDataGenerator(scenariosRandom);
			case "perfect-information":
					return new PerfectInformationDataGenerator();
			case "arima":
				return new ArimaGenerator(scenariosRandom);
			default:
				throw new InvalidConfigurationException("Generation type not known: " + config.generatorType);
		}
	}

	private Data getDataFromPriceScenarioData(PriceScenarioData pdata) {
		Market oldMarket = data.getMarket();
		Market newMarket = new Market(config, pdata,
					oldMarket.getPerMinuteData());
		return new Data(newMarket, getSelectedLoads());
	}

	private Data getHistoricScenario(int scenario) {
		return getHistoricScenarios(scenario, 1);
	}
	
	private Data getHistoricScenarios(int start, int nScenarios) {	
		int[] scenarios = IntStream.range(start, start + nScenarios)
				.map(i -> i % getNScenarios()).toArray();
		Market oldMarket = data.getMarket();
		Market newMarket = new Market(config, oldMarket.getPricedata().filter(scenarios),
					oldMarket.getPerMinuteData());
		newMarket.getPricedata().calcExpected();
		return new Data(newMarket, getSelectedLoads());
	}
	
	private Data getRandomHistoricScenarios(int nScenarios) {	
		Market oldMarket = data.getMarket();
		Market newMarket;
		if(nScenarios < oldMarket.getNScenarios()) {
			newMarket = new Market(config, oldMarket.getPricedata().getRandomSubset(nScenarios, scenariosRandom),
					oldMarket.getPerMinuteData());
			newMarket.getPricedata().calcExpected();
		} else newMarket = oldMarket;
		return new Data(newMarket, getSelectedLoads());
	}

	public Data getRealization() throws SolverException, InvalidConfigurationException {
		if (config.isOnline() && !config.perMinute) {
			Data real = getHistoricScenario(config.evaluationScenario);
			return getDataFromPriceScenarioData(getRealizationGenerator().generate(config, -1, real, real, 1));
		}
		Data real = getHistoricScenarios(config.startEvaluationScenario, config.nEvaluationScenarios);
		return getDataFromPriceScenarioData(getRealizationGenerator().generate(config, -1, real, real, config.nEvaluationScenarios));
	}

	public Data getScenarioTree(Data real, int t) throws InvalidConfigurationException {
		Data data = getRandomHistoricScenarios(config.maxScenarios);
		Data ret = getDataFromPriceScenarioData(getScenarioTreeGenerator().generate(config, t, real, data, config.maxScenarios));
		return ret;
	}

	private int[] findOptimalScenarios(PriceScenarioData priceData, int nScenarios) throws SolverException {
		ScenarioSelection selection = null;
		if (ScenarioSelector.cache.containsKey(priceData.getNScenarios())) {
			selection = ScenarioSelector.cache.get(priceData.getNScenarios()).get(nScenarios);
		}
		if (selection == null) {
			selection = new ScenarioSelection();
			Loads loads = getSelectedLoads();
			ScenarioSelector selector = new ScenarioSelector(priceData, nScenarios, selection, loads.getFirstT(), loads.getLastT());
			IMIPSolver solver = simulator.getMIPSolver();
			solver.build(selector);
			solver.solve();
		}
		return selection.scenarios;
	}

	protected Loads getSelectedLoads() {
		FlexibleLoad[] loads = new FlexibleLoad[config.nLoads];
		for (int e = config.firstLoadID; e < config.firstLoadID + config.nLoads; e++) {
			loads[e - config.firstLoadID] = data.getLoads().getLoads()[e].clone();
		}
		return new Loads(loads, data.getLoads().getGrid());
	}

	public double getTotalChargeAmount() {
		return getSelectedLoads().getRequiredChargeAmount();
	}

	public int getNScenarios() {
		return data.getMarket().getNScenarios();
	}

}

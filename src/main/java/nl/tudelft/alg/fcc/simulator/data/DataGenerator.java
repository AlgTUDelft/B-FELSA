package nl.tudelft.alg.fcc.simulator.data;

import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.simulator.InvalidConfigurationException;

/**
 * A Data Generator
 */
public abstract class DataGenerator {
	
	/**
	 * Generate a set of scenarios
	 * @param config the configuration describing how to generate the scenario
	 * @param offset the starting time step. The generation of the scenarios should start at this time step
	 * @param real the real/original data that is used as input to generate a new set of scenarios
	 * @param distr a distribution of original data that is used as input to generate a new set of scenarios
	 * @param nResultScenarios the number of resulting output scenarios that is required
	 * @return a set of price scenarios
	 * @throws InvalidConfigurationException
	 */
	public abstract PriceScenarioData generate(Config config, int offset, Data real, Data distr, int nResultScenarios) throws InvalidConfigurationException;
}

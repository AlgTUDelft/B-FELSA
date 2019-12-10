package nl.tudelft.alg.fcc.simulator.data;

import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.simulator.InvalidConfigurationException;

public class HistoricDataGenerator extends DataGenerator {

	@Override
	public PriceScenarioData generate(Config config, int offset, Data real, Data distr, int nResultScenarios) throws InvalidConfigurationException {
		return distr.getMarket().getPricedata().clone();
	}

}

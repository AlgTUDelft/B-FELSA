package nl.tudelft.alg.fcc.simulator.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.ini4j.Ini;

import nl.tudelft.alg.fcc.model.DAPrice;
import nl.tudelft.alg.fcc.model.FlexibleLoad;
import nl.tudelft.alg.fcc.model.Grid;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.model.PerMinuteData;
import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.utils.CSVReader;
import nl.tudelft.alg.fcc.utils.ProblemImporter;
import nl.tudelft.alg.fcc.utils.Utils;

public class Data {
	Market market;
	Loads loads;

	/**
	 * Make a new Data object
	 * @param config based on the config object that contains the paths to the data files
	 * @throws IOException throw an IOException when the data cannot be successfully read from the files
	 */
	public Data(Config config) throws IOException {
		this(readMarket(config), readLoads(config));
	}

	/**
	 * Make a new Data object from market and loads
	 */
	public Data(Market market, Loads loads) {
		super();
		this.market = market;
		this.loads = loads;
	}

	/**
	 * Read a grid specification from a file
	 * @param config contains the reference to the grid file
	 * @return a Grid object, or null when the configuration does not include a grid specification
	 * @throws IOException throw an exception when the file cannot be found
	 */
	private static Grid readGrid(Config config) throws IOException {
		if (config.gridFile != null) {
			String[][] gridRaw = CSVReader.readCsvFile(config.gridFile);
			return new Grid(gridRaw);
		}
		return null;
	}

	/**
	 * Import the loads from a load file
	 * @param config contains the reference to the load file
	 * @return a Loads object that contains a list with all the imported loads
	 * @throws IOException throws an IOException when the load file could not be successfully read
	 */
	private static Loads readLoads(Config config) throws IOException {
		Ini ini = new Ini(new File(config.evStateFile));
		List<FlexibleLoad> loads = new ArrayList<FlexibleLoad>(ini.size());
		ini.entrySet().stream().sorted((o1, o2) -> Integer.parseInt(o1.getKey()) - Integer.parseInt(o2.getKey()))
				.forEach(e -> loads.add(FlexibleLoad.createFromMap(e.getValue(), config.startdate, config.ptuLength)));
		Grid grid = readGrid(config);
		return new Loads(loads.toArray(new FlexibleLoad[0]), grid);
	}

	/**
	 * Import the market data from a file
	 * @param config contains the reference to the market data folder
	 * @return a Market object
	 * @throws FileNotFoundException throws an IOException when any of the files could not be successfully read
	 */
	private static Market readMarket(Config config) throws IOException {
		Utils.setDateFormat(config.dateformat);
		String apxpricefile = Paths.get(config.pricedataFolder, "da.csv").toString();
		DAPrice apxPrice = ProblemImporter.importDAPrices(apxpricefile);
		PerMinuteData perMinuteData = config.perMinute ? ProblemImporter.importPerMinuteImbalance(config.pricedataFolder, config.ptuLength) : null;
		PriceScenarioData priceData = ProblemImporter.importPriceScenarioData(apxPrice, config.pricedataFolder, config.ptuLength, config.capacityMarket);
		return new Market(config, priceData, perMinuteData);
	}

	/**
	 * Get the market
	 */
	public Market getMarket() {
		return market;
	}

	/**
	 * Get the loads
	 */
	public Loads getLoads() {
		return loads;
	}

	/**
	 * Reset the price data
	 */
	public void reset() {
		market.getPricedata().reset();
	}

}

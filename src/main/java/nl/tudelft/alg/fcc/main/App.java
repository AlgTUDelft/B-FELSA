package nl.tudelft.alg.fcc.main;

import java.io.IOException;
//dev
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.fcc.simulator.InvalidConfigurationException;
import nl.tudelft.alg.fcc.simulator.Simulator;

public class App {
	public static final Logger logger = Logger.getLogger(App.class.getSimpleName());

	public static void main(String[] args) {
		Locale.setDefault(new Locale("EN", "BR"));
		runSimulator("data/config1.ini");
	}
	
	public static void runSimulator(String file) {
		try {
			Simulator ev1 = new Simulator(file);
			ev1.run();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error in reading/writing file: " + e.getLocalizedMessage(), e);
		} catch (SolverException e) {
			logger.log(Level.SEVERE, "Error in running solver: " + e.getLocalizedMessage(), e);
		} catch (InvalidConfigurationException e) {
			logger.log(Level.SEVERE, "Error in configuring the simulator: " + e.getLocalizedMessage(), e);
		}
	}
}

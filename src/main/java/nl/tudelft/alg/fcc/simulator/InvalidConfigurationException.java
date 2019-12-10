package nl.tudelft.alg.fcc.simulator;

public class InvalidConfigurationException extends Exception {
	private static final long serialVersionUID = 8161835711644453583L;

	public InvalidConfigurationException(String message) {
		super(message);
	}
	
	public InvalidConfigurationException(String message, Exception e) {
		super(message, e);
	}
}

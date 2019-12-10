package nl.tudelft.alg.fcc.model;

import java.util.Calendar;
import java.util.Map;

public abstract class FlexibleLoad {
	
	public FlexibleLoad() {}
	
	public FlexibleLoad(Map<String, String> map) {}

	/**
	 * Returns true iff flexible load is available at time step t
	 */
	public boolean isAvailable(int t) {
		return true;
	}
	
	/**
	 * Returns true iff flexible load is available at t
	 */
	public boolean isConsidered(int t) {
		return true;
	}	
	
	@Override
	public abstract FlexibleLoad clone();
	
	/**
	 * Get the maximum charging speed at time step t. This is
	 * either the maximum charging speed of the device, or 0 when the device is not connected
	 */
	public abstract double getChargingSpeed(int t);
	
	/**
	 * Get the maximum charging speed
	 */
	public abstract double getMaxChargingSpeed();
	
	/**
	 * Get the maximum storage capacity of the device
	 */
	public abstract double getCapacity();
	
	/**
	 * Get the current state of charge of the device (SOC)
	 */
	public abstract double getSoc();
	
	/**
	 * Set the state of charge of the device to soc
	 */
	public abstract void setSoc(double soc);
	
	/**
	 * Get the minimum required state of charge for at the end of the charging session
	 */
	public double getMinsoc() {
		return 0;
	}
	
	/**
	 * Get the starting time of the charging session
	 */
	public int getArrival() {
		return 0;
	}
	
	/**
	 * Get the departure time of the charging session
	 */
	public int getDeparture() {
		return Integer.MAX_VALUE;
	}

	/**
	 * Get the position of the device in the grid
	 */
	public int getGridPosition() {
		return 0;
	}

	public static FlexibleLoad createFromMap(Map<String, String> map, Calendar startdate, int ptu) {

		switch (map.get("type")) {
			case "EV":
				return new EVState(map, startdate, ptu);
			case "Battery":
				return new Battery(map);
			default:
				return null;
		}
	}
}

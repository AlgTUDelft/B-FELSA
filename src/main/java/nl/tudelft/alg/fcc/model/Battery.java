package nl.tudelft.alg.fcc.model;

import java.util.Map;

public class Battery extends FlexibleLoad {
	/**
	 * The state of charge
	 */
	protected double soc;
	/**
	 * The maximum charging speed expressed in MW
	 */
	protected double chargingSpeed;
	/**
	 * The battery capacity expressed in MWh
	 */
	protected double capacity; 
	/**
	 * The position of the battery in the grid
	 */
	protected int gridPosition;
	
	public Battery(double soc, double chargingSpeed, double cap, int gridposition) {
		super();
		this.soc = soc;
		this.chargingSpeed = chargingSpeed;
		this.capacity = cap;
		this.gridPosition = gridposition;
	}

	public Battery(Battery other) {
		this(other.soc, other.chargingSpeed, other.capacity, other.gridPosition);
	}

	public Battery(Map<String, String> map) {
		super(map);
		this.soc = Double.parseDouble(map.get("arrival soc"));
		this.chargingSpeed = Double.parseDouble(map.get("max charging speed"));
		this.capacity = Double.parseDouble(map.get("battery capacity"));
		this.gridPosition = Integer.parseInt(map.get("grid position"));
	}

	@Override
	public FlexibleLoad clone() {
		return new Battery(this);
	}
	
	@Override
	public double getChargingSpeed(int t) {
		return chargingSpeed;
	}

	@Override
	public double getMaxChargingSpeed() {
		return chargingSpeed;
	}
	
	@Override
	public double getCapacity() {
		return capacity;
	}
	
	@Override
	public double getSoc() {
		return soc;
	}
	
	@Override
	public void setSoc(double soc) {
		this.soc = soc;
	}

	@Override
	public int getGridPosition() {
		return gridPosition;
	}
	
}

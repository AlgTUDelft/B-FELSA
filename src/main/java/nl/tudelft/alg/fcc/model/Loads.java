package nl.tudelft.alg.fcc.model;

import java.util.Arrays;
import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * Data class for the loads of a problem
 */
public class Loads {
	protected FlexibleLoad[] loads;
	protected FlexibleLoadProblem problem;
	protected int nLoads;
	protected Grid grid;
	
	public Loads(FlexibleLoad[] loads, Grid grid) {
		this.loads = loads;
		this.nLoads = loads.length;
		this.grid = grid;
	}
	
	public void setProblem(FlexibleLoadProblem problem) {
		this.problem = problem;
	}

	public Grid getGrid() {
		return grid;
	}

	public int getNLoads() {
		return nLoads;
	}
	
	public FlexibleLoad[] getLoads() {
		return loads;
	}
	
	/**
	 * Get the first arrival time of all the flexible loads
	 */
	public int getFirstT() {
		return Arrays.stream(loads).mapToInt(s -> s.getArrival()).min().orElse(0);
	}
	
	/**
	 * Get the last departure time of all the flexible loads
	 */
	public int getLastT() {
		return Arrays.stream(loads).mapToInt(s -> s.getDeparture()).max().orElse(0);
	}
	
	/**
	 * Returns true iff the load with id i should be considered?
	 */
	public boolean considerLoad(int i) {
		return true;
	}
	
	/**
	 * Get the minimum state of charge of load i
	 */
	public double getMinimumSOC(int i) {
		if(!considerLoad(i))
			return 0; //return 0 when the EV should not be considered
		return loads[i].getMinsoc();
	}
	
	/**
	 * Set the arrival SOC (or the updated SOC) of load i to soc
	 */
	public void setArrivalSOC(int i, double soc) {
		loads[i].setSoc(soc);
	}
	
	/**
	 * Set the arrival SOCs (or the updated SOCs) for each load from soc
	 */
	public void setArrivalSOCs(double[] soc) {
		IntStream.range(0, nLoads).forEach(i -> loads[i].setSoc(soc[i]));
	}
	
	/**
	 * Get the arrival state of charge for load i
	 */
	public double getArrivalSOC(int i) {
		return loads[i].getSoc();
	}
	
	/**
	 * Get an array describing the arrival state of charge of all the loads
	 */
	public double[] getArrivalSOCs() {
		return Arrays.stream(loads).mapToDouble(e -> e.getSoc()).toArray();
	}

	/**
	 * Get the battery capacity of load i
	 */
	public double getBatteryCapacity(int i) {
		return loads[i].getCapacity();
	}

	/**
	 * Get the charging efficiency
	 */
	public double getChargingEfficiency() {
		return 0.90; //TODO dummy charging efficiency should be replaced by a more realistic value,
					//perhaps it should differ per Load
	}
	
	/**
	 * Get the battery degradation costs per MWh
	 */
	public double getBatteryDegradationCost() {
		return problem.getConfig().getBatteryDegradation();
	}
	
	/**
	 * Get the required charging amount for load i
	 * (minimum soc - arrival soc)
	 */
	public double getRequiredChargeAmount(int i) {
		return loads[i].getMinsoc() - loads[i].getSoc();
	}
	
	/**
	 * Get the total required charging amount of all loads
	 */
	public double getRequiredChargeAmount() {
		return IntStream.range(0, nLoads).mapToDouble(this::getRequiredChargeAmount).sum();
	}
	
	/**
	 * Returns true iff load i is available at time step t
	 */
	public boolean isLoadAvailable(int i, int t) {
		return loads[i].isAvailable(t + problem.getStartT());
	}

	/**
	 * Returns the maximum charging speed of load i at time step t
	 */
	public double getChargingSpeed(int i, int t) {
		if(!considerLoad(i))
			return 0;
		return loads[i].getChargingSpeed(t + problem.getStartT());
	}
	
	/**
	 * Returns the maximum charging speed of load i
	 */
	public double getMaximumChargingSpeed(int i) {
		return loads[i].getMaxChargingSpeed();
	}
	
	/**
	 * Returns the maximum discharging speed of load i at time step t
	 */
	public double getDischargingSpeed(int i, int t) {
		if(!considerLoad(i))
			return 0;
		//TODO set maximum discharging speed
		return loads[i].getChargingSpeed(t + problem.getStartT());
	}
	
	/**
	 * Get the relative startT of load i compared to startT of the problem
	 */
	public int getStartT(int i) {
		return Math.max(0, loads[i].getArrival() - problem.getStartT());
	}
	
	/**
	 * Get the relative endT of load i compared to startT of the problem
	 */
	public int getEndT(int i) {
		return Math.max(0, loads[i].getDeparture() - problem.getStartT());
	}
	
	/**
	 * Get the arrival time step of load i
	 */
	public int getArrivalT(int i) {
		return loads[i].getArrival();
	}

	/**
	 * Get the departure time step of load i
	 */
	public int getDepartureT(int i) {
		return loads[i].getDeparture();
	}

	/**
	 * Get the length of the charging session of load i
	 */
	public int getNTimeSteps(int i) {
		return loads[i].getDeparture() - loads[i].getArrival();
	}

	/**
	 * Returns true iff grid constraints are considered
	 */
	public boolean considerGrid() {
		return problem.getConfig().considerGrid();
	}

	/**
	 * get the number of grid lines
	 */
	public int getNGridLines() {
		if (considerGrid()) return grid.getNGridLines();
		return 0;
	}

	/**
	 * Get the grid position of load i
	 */
	public int getGridPosition(int i) {
		return loads[i].getGridPosition();
	}

	/**
	 * Get the grid capacity at position pos as time step t
	 */
	public double getGridCapacity(int t, int pos) {
		if(considerGrid())
			return grid.getCapacity(t + problem.getStartT(), pos);
		return 0;
	}
}

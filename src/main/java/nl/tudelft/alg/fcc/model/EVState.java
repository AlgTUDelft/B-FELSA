package nl.tudelft.alg.fcc.model;

import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Map;

import nl.tudelft.alg.fcc.utils.Utils;

public class EVState extends Battery {
	/**
	 * the arrival time expressed in minutes since the start of the experiment
	 */
	int arrival;
	/**
	 * the departure time expressed in minutes since the start of the experiment
	 */
	int departure;
	/**
	 * the minimum required state of charge (SOC)
	 */
	double minsoc;	
	
	public EVState(int arrival, int departure, double soc, double minsoc, double batcap, double chargingspeed,
			int gridposition) {
		super(soc, chargingspeed, batcap, gridposition);
		this.arrival = arrival;
		this.departure = departure;
		this.minsoc = minsoc;
	}
	
	public EVState(EVState other) {
		this(other.arrival, other.departure, other.soc,
				other.minsoc, other.capacity, other.chargingSpeed, other.gridPosition);
	}

	public EVState(Map<String, String> map, Calendar startdate, int ptu) {
		super(map);
		this.arrival = timeToPTU(map.get("arrival"), startdate, ptu);
		this.departure = timeToPTU(map.get("departure"), startdate, ptu);
		this.minsoc = Double.parseDouble(map.get("minimum soc"));
	}

	private static int timeToPTU(String date, Calendar startdate, int ptu) {
		Calendar d = Utils.stringToCalender(date);
		return ((int) ChronoUnit.MINUTES.between(startdate.toInstant(), d.toInstant())) / ptu;
	}

	@Override
	public EVState clone() {
		return new EVState(this);	
	}

	@Override
	public int getArrival() {
		return arrival;
	}

	public void setArrival(int arrival) {
		this.arrival = arrival;
	}

	@Override
	public int getDeparture() {
		return departure;
	}

	public void setDeparture(int departure) {
		this.departure = departure;
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
	public double getMinsoc() {
		return minsoc;
	}

	@Override
	public boolean isAvailable(int t) {
		return arrival <= t && t < departure;
	}
	
	@Override
	public boolean isConsidered(int t) {
		return arrival <= t;
	}

}

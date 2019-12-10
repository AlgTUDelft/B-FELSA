package nl.tudelft.alg.fcc.solution.efel;

import java.util.Arrays;

import nl.tudelft.alg.fcc.model.EVState;
import nl.tudelft.alg.fcc.model.FlexibleLoad;

public class EQEV extends FlexibleLoad {
	EVState[] evstates;
	double chgSpeed;
	
	public EQEV(EVState[] evstates, double chgSpeed) {
		super();
		this.evstates = evstates;
		this.chgSpeed = chgSpeed;
	}

	@Override
	public FlexibleLoad clone() {
		return new EQEV(this.evstates, this.chgSpeed);
	}
	
	@Override
	public boolean isAvailable(int m) {
		return getArrival() <= m && m < getDeparture();
	}
	
	@Override
	public boolean isConsidered(int m) {
		return getArrival() <= m;
	}

	@Override
	public double getChargingSpeed(int m) {
		return Arrays.stream(evstates).mapToDouble(
			ev -> ev.isAvailable(m) ? chgSpeed : 0).sum();
	}
	
	@Override
	public double getMaxChargingSpeed() {
		return evstates.length * chgSpeed;
	}

	public double getCapacity(int m) {
		return Arrays.stream(evstates).mapToDouble(
				ev -> ev.isAvailable(m) ? ev.getCapacity() : 0).sum();
	}
	
	@Override
	public double getCapacity() {
		assert false;
		return 0;
	}

	@Override
	public double getSoc() {
		assert false;
		return 0;
	}

	@Override
	public void setSoc(double soc) {
		assert false;
	}

	@Override
	public int getDeparture() {
		return Arrays.stream(evstates).mapToInt(ev -> ev.getDeparture()).max().orElse(0);
	}
	
	@Override
	public int getArrival() {
		return Arrays.stream(evstates).mapToInt(ev -> ev.getArrival()).min().orElse(0);
	}
	

}

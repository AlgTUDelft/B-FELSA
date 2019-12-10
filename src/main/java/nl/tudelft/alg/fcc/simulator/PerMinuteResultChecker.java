package nl.tudelft.alg.fcc.simulator;

import nl.tudelft.alg.fcc.model.PerMinuteData;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

public class PerMinuteResultChecker extends ResultChecker {
	
	public PerMinuteResultChecker(FlexibleLoadProblem p) {
		super(p);
	}
	
	private PerMinuteData getMinuteData() {
		return problem.getMarket().getPerMinuteData();
	}

	@Override
	protected int getNScenarios() {
		return 1;
	}

	@Override
	protected double getExpectedImbalancePrice(int t) {
		return getMinuteData().getImbalancePrice(problem.getStartT() + t);
	}
	
	@Override
	protected double getImbalancePrice(int t, int i) {
		return getMinuteData().getImbalancePrice(problem.getStartT() + t);
	}

	@Override
	protected double getDownImbalancePrice(int t, int i) {
		return getMinuteData().getMinDownRegulatingPrice(problem.getStartT() + t);
	}

	@Override
	protected double getUpImbalancePrice(int t, int i) {
		return getMinuteData().getMaxUpRegulatingPrice(problem.getStartT() + t);
	}

	@Override
	protected double getDownRegulatingPrice(int t, int i) {
		return getMinuteData().getMinDownRegulatingPrice(problem.getStartT() + t);
	}

	@Override
	protected double getUpRegulatingPrice(int t, int i) {
		return getMinuteData().getMaxUpRegulatingPrice(problem.getStartT() + t);
	}

	private int toMinutes(int t) {
		return (problem.getStartT() + t) * getMinuteData().getPTULength();
	}

	@Override
	protected double getDownReserveProportion(int e, int t, int i) {
		DecisionVariables dec = getNetDecisions();
		double bidprice = dec.bd[e][t];
		double prop = 0;
		for (int a = 0; a < getMinuteData().getPTULength(); a++) {
			if (downReservesAccepted(bidprice, getMinuteData().getDownRegulatingPrice(toMinutes(t) + a)))
				prop += 1.0 / getMinuteData().getPTULength();
		}
		return prop;
	}

	@Override
	protected double getUpReserveProportion(int e, int t, int i) {
		DecisionVariables dec = getNetDecisions();
		double bidprice = dec.bu[e][t];
		double prop = 0;
		for (int a = 0; a < getMinuteData().getPTULength(); a++) {
			if (upReservesAccepted(bidprice, getMinuteData().getUpRegulatingPrice(toMinutes(t) + a)))
				prop += 1.0 / getMinuteData().getPTULength();
		}
		return prop;
	}
}

package nl.tudelft.alg.fcc.simulator;

import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;
import nl.tudelft.alg.fcc.problem.Result;
import nl.tudelft.alg.fcc.utils.Utils;

public class ResultChecker {
	FlexibleLoadProblem problem;
	DecisionVariables previousDecisions;
	
	public ResultChecker(FlexibleLoadProblem p) {
		problem = p;
		previousDecisions = null;
	}
	
	public DecisionVariables getNetDecisions() {
		return problem.getVars();
	}

	public Result check() {
		return check(getNetDecisions());
	}
	
	public void setPreviousDecisions(DecisionVariables dec) {
		previousDecisions = dec;
	}

	protected void assertValidDecisions(DecisionVariables dec) {
		final double E = 1e-4;
		for(int t=0; t<problem.getNTimeSteps(); t++) {
			assert Math.abs(dec.pda[problem.getMarket().PTUtoH(t)] + dec.pimb[t] 
					- Utils.columnSum(dec.p)[t]
					+ Utils.columnSum(dec.dp)[t]) < E;
			for(int e=0; e<problem.getNLoads(); e++) {
				assert dec.p[e][t] + dec.rcd[e][t] <= problem.getLoads().getChargingSpeed(e, t) + E;
				assert dec.dp[e][t] - dec.rdd[e][t] >= 0 - E;
				assert dec.p[e][t] - dec.rcu[e][t] >= 0 - E;
				assert dec.dp[e][t] + dec.rdu[e][t] <= problem.getLoads().getDischargingSpeed(e, t) + E;
			}
		}
		if (previousDecisions != null) {
			previousDecisions.assertUnaltered(dec, problem.getMarket().getFixedPTUs());
		}
	}

	public Result check(DecisionVariables dec) {
		assertValidDecisions(dec);
		Result result = initializeResult();
		checkDA(dec, result);
		checkImb(dec, result);
		checkCharge(dec, result);
		checkReserves(dec, result);
		checkShortage(result);
		return result;
	}

	protected int getNScenarios() {
		return problem.getMarket().getNScenarios();
	}

	public Result initializeResult() {
		return new Result(problem.getLoads().getArrivalSOCs(), getNScenarios(), problem.getNTimeSteps(), problem.getMarket().getNumberOfHours());
	}

	protected double getExpectedImbalancePrice(int t) {
		return problem.getMarket().getExpectedImbalancePrice(t);
	}
	
	protected double getImbalancePrice(int t, int i) {
		return problem.getMarket().getImbalancePrice(t, i);
	}

	protected double getDownImbalancePrice(int t, int i) {
		Market market = problem.getMarket();
		return market.hasCapacityPayments() ? getImbalancePrice(t, i) : market.getDownRegulatingPrice(t, i);
	}

	protected double getUpImbalancePrice(int t, int i) {
		Market market = problem.getMarket();
		return market.hasCapacityPayments() ? getImbalancePrice(t, i) : market.getUpRegulatingPrice(t, i);
	}

	protected double getDownRegulatingPrice(int t, int i) {
		Market market = problem.getMarket();
		return market.hasCapacityPayments()
				? market.getDownCapacityPayment(t, i)
				: market.getDownRegulatingPrice(t, i);
	}

	protected double getUpRegulatingPrice(int t, int i) {
		Market market = problem.getMarket();
		return market.hasCapacityPayments()
				? market.getUpCapacityPayment(t, i)
				: market.getUpRegulatingPrice(t, i);
	}

	protected double getDownReserveProportion(int e, int t, int i) {
		return problem.getMarket().getDownReserveProportion(t, i);
	}

	protected double getUpReserveProportion(int e, int t, int i) {
		return problem.getMarket().getUpReserveProportion(t, i);
	}

	public void checkReserves(DecisionVariables dec, Result result) {
		for (int t = 0; t < problem.getNTimeSteps(); t++) {
			for (int e = 0; e < problem.getNLoads(); e++) {
				for (int i = 0; i < getNScenarios(); i++) {
					if (downReservesAccepted(dec, e, t, i))
						checkDownReserves(dec, result, e, t, i);
					if (upReservesAccepted(dec, e, t, i))
						checkUpReserves(dec, result, e, t, i);
				}
			}
		}
	}

	protected boolean downReservesAccepted(double bidprice, double marketprice) {
		return (marketprice >= bidprice && problem.getMarket().hasCapacityPayments()) ||
				(marketprice <= bidprice && !problem.getMarket().hasCapacityPayments());
	}

	protected boolean downReservesAccepted(DecisionVariables dec, int e, int t, int i) {
		return downReservesAccepted(dec.bd[e][t], getDownRegulatingPrice(t, i));
	}

	protected boolean upReservesAccepted(double bidprice, double marketprice) {
		return marketprice >= bidprice;
	}

	protected boolean upReservesAccepted(DecisionVariables dec, int e, int t, int i) {
		return upReservesAccepted(dec.bu[e][t], getUpRegulatingPrice(t, i));
	}

	protected void checkDownReserves(DecisionVariables dec, Result result, int e, int t, int i) {
		Market market = problem.getMarket();
		final double ptu = market.getPTU();
		boolean paidAsBid = market.getReservesMarketClearance().equalsIgnoreCase("paid as bid");
		boolean capmarket = market.hasCapacityPayments();
		double price = paidAsBid && !capmarket ? dec.bd[e][t] : getDownImbalancePrice(t, i);
		double capprice = paidAsBid && capmarket ? dec.bd[e][t] : market.getDownCapacityPayment(t, i);
		double prop = getDownReserveProportion(e, t, i);
		double dres = (dec.rcd[e][t] + dec.rdd[e][t]);
		double dsoc = dec.rcd[e][t] * getEfficiency(dec.rcd[e][t])
				+ dec.rdd[e][t] * getEfficiency(-dec.rdd[e][t]);
		dsoc *= ptu * prop;
		result.incSoc(t, e, i, dsoc);
		result.incRD(t, i, dres * ptu * prop);
		result.incCost(i, dres * ptu * (-capprice + prop * price));
		result.incCost(i, -ptu * prop *
				problem.getLoads().getBatteryDegradationCost() * dec.rdd[e][t]);
	}

	protected void checkUpReserves(DecisionVariables dec, Result result, int e, int t, int i) {
		Market market = problem.getMarket();
		final double ptu = market.getPTU();
		boolean paidAsBid = market.getReservesMarketClearance().equalsIgnoreCase("paid as bid");
		boolean capmarket = market.hasCapacityPayments();
		double price = paidAsBid && !capmarket ? dec.bu[e][t] : getUpImbalancePrice(t, i);
		double capprice = paidAsBid && capmarket ? dec.bu[e][t] : market.getUpCapacityPayment(t, i);
		double prop = getUpReserveProportion(e, t, i);
		double ures = (dec.rcu[e][t] + dec.rdu[e][t]);
		double dsoc = dec.rcu[e][t] * getEfficiency(dec.rcu[e][t])
				+ dec.rdu[e][t] * getEfficiency(-dec.rdu[e][t]);
		dsoc *= ptu * prop;
		result.incSoc(t, e, i, -dsoc);
		result.incRU(t, i, ures * ptu * prop);
		result.incCost(i, -ures * ptu * (capprice + prop * price));
		result.incCost(i, ptu * prop *
				problem.getLoads().getBatteryDegradationCost() * dec.rdu[e][t]);
	}

	protected double getEfficiency(double p) {
		if(p < 0) return 1.0 / problem.getLoads().getChargingEfficiency();
		return problem.getLoads().getChargingEfficiency();
	}
	
	protected void checkCharge(DecisionVariables dec, Result result) {
		final double ptu = problem.getMarket().getPTU();
		for (int t = 0; t < problem.getNTimeSteps(); t++) {
			for (int e = 0; e < problem.getNLoads(); e++) {
				double pIn = dec.p[e][t] - dec.dp[e][t];
				result.incSoc(t, e, pIn * ptu * getEfficiency(pIn));
				result.incCost(ptu * problem.getLoads().getBatteryDegradationCost() * dec.dp[e][t]);
			}
		}
	}

	protected void checkImb(DecisionVariables dec, Result result) {
		final double ptu = problem.getMarket().getPTU();
		result.setImb(dec.pimb);
		if (!problem.getMarket().considerImbalance()) return;
		for(int t=0; t<problem.getNTimeSteps(); t++) {
			for(int i=0; i<getNScenarios(); i++) {
				double price = getImbalancePrice(t, i);
				result.incCost(i, price * dec.pimb[t] * ptu);	
			}
		}
	}

	protected void checkDA(DecisionVariables dec, Result result) {
		result.setDA(dec.pda);
		if (!problem.getMarket().hasDayAhead()) return;
		for (int h = 0; h < problem.getMarket().getNumberOfHours(); h++) {
			result.incCost(problem.getMarket().getDAPriceByHour(h) * dec.pda[h]);
		}
	}

	public Result checkDA() {
		DecisionVariables dec = getNetDecisions();
		Result result = new Result(problem.getLoads().getArrivalSOCs(), 1, problem.getNTimeSteps(), problem.getMarket().getNumberOfHours());
		checkDA(dec, result);
		return result;
	}
	
	protected void checkShortage(Result result) {
		Market market = problem.getMarket();
		double[] shortage = new double[getNScenarios()];
		double[] overflow = new double[getNScenarios()];
		double[][] socs = result.getSoc();
		for (int e = 0; e < problem.getNLoads(); e++) {
			for (int i = 0; i < getNScenarios(); i++) {
				shortage[i] += Math.max(0, problem.getLoads().getMinimumSOC(e) - socs[e][i]);
				overflow[i] += Math.max(0, socs[e][i] - problem.getLoads().getBatteryCapacity(e));
				overflow[i] += -Math.min(0, socs[e][i]);
			}
		}
		result.setShortage(shortage);
		result.setOverflow(overflow);
	}

	
	public void writeResult(FlexibleLoadProblem p) {
		problem.writeResult(p);
	}

	public double shortage() {
		return check(getNetDecisions()).getAvgShortage();
	}
	
	public double getTotalChargeAmount() {
		return problem.getLoads().getRequiredChargeAmount();
	}

	public FlexibleLoadProblem getProblem() {
		return problem;
	}

	public ProblemConfiguration getConfig() {
		return getProblem().getConfig();
	}
}

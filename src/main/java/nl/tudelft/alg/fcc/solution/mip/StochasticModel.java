package nl.tudelft.alg.fcc.solution.mip;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.Constraint;
import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.utils.Utils;

/**
 * Solves the FlexibleLoadProblem using stochastic optimization for multiple Flexible Loads
 * The model results in at most one regulation bid for every PTU.
 * Use the AImbalanceReserveStochasticCompact for better run time results
 */
public class StochasticModel extends FlexibleLoadMIPcon {
	static Random random;
	protected Variable[][][] vd, vu; // is the up/down-ward reserve bid called? for every session, time step, per scenario,  binary
	Variable[][][]  rcd, // the scheduled amount of downward reserve capacity while charging for every EV, per time step, per scenario, positive continuous
					rcu, // the scheduled amount of upward reserve capacity while charging for every EV, per time step, per scenario, positive continuous
					rdd, // the scheduled amount of downward reserve capacity while discharging for every EV, per time step, per scenario, positive continuous
					rdu; // the scheduled amount of upward reserve capacity while discharging for every EV, per time step, per scenario, positive continuous
	
	
	public StochasticModel(FlexibleLoadProblem p) {
		super(p);
		loadModel = new StochasticLoadModel(getMarket());
		selectSubsetOfScenarios();
		if(getConfig().getClusterMethod().equalsIgnoreCase("per flexible load"))
			clusterModel = new ClusterModelPerEV();
		else if(getConfig().getClusterMethod().equalsIgnoreCase("per time step"))
			clusterModel = new ClusterModel();
		else clusterModel = null;
	}

	/**
	 * Selected a sub set of all scenarios to be used in the model
	 */
	protected void selectSubsetOfScenarios() {
		int nScenarios = getConfig().getNScenarios();
		if(nScenarios >= this.nScenarios) return;
		PriceScenarioData data = getMarket().getPricedata();
		if (getConfig().randomScenarios())
			data = data.getRandomSubset(nScenarios, Utils.random);
		else {
			data = data.reduceScenarioSet(getLoads(), getMarket().getPTU(), problem.getStartT(), nScenarios);
		}
		Market newMarket = new Market(getMarket(), data);
		problem.setMarket(newMarket);
		this.nScenarios = getMarket().getNScenarios();
	}

	@Override
	protected void setConstraints() {
		super.setConstraints();
		setPriceBidConstraints();
	}
	
	protected void setPriceBidConstraints() {
		if (getConfig().quantityOnly()) {
			fixVariables(1, vd, vu);
		} else {
			setBidLogic();
		}
	}
	
	protected int[] getDownScenarioOrder(int t) {
		return getMarket().hasCapacityPayments() ? getMarket().getScenariosOrderedByDownCapacityPayment(t) : getMarket().getScenariosOrderedByDownRegulatingPrice(t);
	}
	
	protected int[] getUpScenarioOrder(int t) {
		return getMarket().hasCapacityPayments() ? getMarket().getScenariosOrderedByUpCapacityPayment(t) : getMarket().getScenariosOrderedByUpRegulatingPrice(t);
	}
	
	protected void setBidLogic() {
		Market market = problem.getMarket();
		boolean cap = market.hasCapacityPayments();
		for(int t=0; t<nTimeSteps; t++) {
			for(int e = 0; e<nLoads; e++) {			
				//Ordered from  high to low
				int[] scenarioOrder = getDownScenarioOrder(t);
			
				for(int i=0; i<scenarioOrder.length-1; i++) {
					// in a scenario a downward bid is only called, if it could also be called in a scenario with a 
					// higher (lower, if not considering capacity payments) downward capacity price (regulating price)
					int i0 = scenarioOrder[i];
					int i1 = scenarioOrder[i+1];
					LinExp left = new LinExp(vd[e][t][i1]); // smaller price
					LinExp right = new LinExp(vd[e][t][i0]);  // larger price
					CMP sign = null;
					double price0 = cap ? market.getDownCapacityPayment(t, i0) : market.getDownRegulatingPrice(t, i0);
					double price1 = cap ? market.getDownCapacityPayment(t, i1) : market.getDownRegulatingPrice(t, i1);
					if (price0 == price1) sign = CMP.EQ;
					else if (cap) sign = CMP.SMALLEREQ;
					else sign = CMP.LARGEREQ;
					constraints.add(new Constraint(left, right, sign, "BLD" + t + "_" + i));
				}
				//Ordered from  high to low
				scenarioOrder = getUpScenarioOrder(t);
				for(int i=0; i<scenarioOrder.length-1; i++) {
					// in a scenario a upward bid is only called, if it could also be called in a scenario with a 
					// lower upward capacity price (regulating price)
					int i0 = scenarioOrder[i]; //higher price
					int i1 = scenarioOrder[i + 1]; //smaller price
					LinExp left = new LinExp(vu[e][t][i1]); // smaller price
					LinExp right = new LinExp(vu[e][t][i0]); // larger price
					CMP sign = null;
					double price0 = cap ? market.getUpCapacityPayment(t, i0) : market.getUpRegulatingPrice(t, i0);
					double price1 = cap ? market.getUpCapacityPayment(t, i1) : market.getUpRegulatingPrice(t, i1);
					if (price0 == price1) sign = CMP.EQ;
					else sign = CMP.SMALLEREQ;
					constraints.add(new Constraint(left, right, sign, "BLU" + t + "_" + i));
				}
			}
		}
	}
	
	@Override
	protected void setFirstFixed() {
		super.setFirstFixed();
		Market market = problem.getMarket();
		Loads loads = problem.getLoads();
		DecisionVariables dec = problem.getVars();
		if(!market.hasReserves()) {
			fixVariables(0, rcd, rcu, vd, vu);
			if(problem.getConfig().considerV2G())
				fixVariables(0, rdd, rdu);
		} else {
			boolean cap = market.hasCapacityPayments();				
			for(int t=0; t<market.getFixedPTUs(); t++) {
				if(t >= problem.getNTimeSteps()) break;
				int[] downScenarioOrder = getDownScenarioOrder(t);
				int[] upScenarioOrder = getUpScenarioOrder(t);
				int highestDown = cap ? downScenarioOrder[0] : downScenarioOrder[nScenarios-1];
				int highestUp = upScenarioOrder[0];
				double highestdownprice = cap ? market.getDownCapacityPayment(t, highestDown) : market.getDownRegulatingPrice(t, highestDown);
				double highestupprice = cap ? market.getUpCapacityPayment(t, highestUp) : market.getUpRegulatingPrice(t, highestUp);
				for(int e=0; e<nLoads; e++) {
					if(dec.rcd[e][t] + dec.rcu[e][t] > loads.getMaximumChargingSpeed(e) - 1e-5) {
						dec.rcd[e][t] = Math.max(0, loads.getMaximumChargingSpeed(e) - 1e-5 - dec.rcu[e][t]);
						dec.rcu[e][t] = Math.max(0, loads.getMaximumChargingSpeed(e) - 1e-5 - dec.rcd[e][t]);
					}
					if(dec.rdd[e][t] + dec.rdu[e][t] > loads.getMaximumChargingSpeed(e) - 1e-5) {
						dec.rdd[e][t] = Math.max(0, loads.getMaximumChargingSpeed(e) - 1e-5 - dec.rdu[e][t]);
						dec.rdu[e][t] = Math.max(0, loads.getMaximumChargingSpeed(e) - 1e-5 - dec.rdd[e][t]);
					}
					for(int i=0; i<nScenarios; i++) {;
						double downprice = cap ? market.getDownCapacityPayment(t, i) : market.getDownRegulatingPrice(t, i);
						double upprice = cap ? market.getUpCapacityPayment(t, i) : market.getUpRegulatingPrice(t, i);
						int down = (!cap && downprice <= dec.bd[e][t]) || (cap && downprice >= dec.bd[e][t]) ? 1 : 0;
						int up = upprice >= dec.bu[e][t] ? 1 : 0;
						if(downprice == highestdownprice && dec.rcd[e][t] + dec.rdd[e][t] > 0) down = 1;
						if(upprice == highestupprice && dec.rcu[e][t] + dec.rdu[e][t] > 0) up = 1;
						fixVariable(vd[e][t][i], down);
						fixVariable(vu[e][t][i], up);
						fixVariable(rcd[e][t][i], down * dec.rcd[e][t]);
						fixVariable(rcu[e][t][i], up * dec.rcu[e][t]);
						if(problem.getConfig().considerV2G()) {
							fixVariable(rdu[e][t][i], up * dec.rdu[e][t]);
							fixVariable(rdd[e][t][i], down * dec.rdd[e][t]);
						}
					}
				}
			}
		}
	}
	
	@Override
	protected void addDegObj() {
		Market market = getMarket();
		Loads loads = getLoads();
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getResTimesteps(); t++) {
				//Battery degradation cost
				double deg = getLoads().getBatteryDegradationCost();
				for(int i=0; i<market.getNScenarios(); i++) {
					double prob = market.getScenarioProbability(i);
					double propdown = market.getDownReserveProportion(t,i);
					double propup = market.getUpReserveProportion(t,i);
					LinExp rdu = getERdu(e,t,i).multiplyBy(deg * propup * prob);
					LinExp rdd = getERdd(e, t, i).multiplyBy(-deg * propdown * prob);
					LinExp pd = getEPd(e,t).multiplyBy(deg * prob);
					objectiveFunction.addLinExps(rdu, rdd, pd);
				}
			}
		}	
	}

	@Override
	protected void setVars() {
		super.setVars();
		addVars(rcd, rcu, vd, vu);
		if(problem.getConfig().considerV2G())
			addVars(rdd, rdu);
	}

	@Override
	//need to initialize variables
	protected void initiliazeVars() {
		super.initiliazeVars();
		vd = (Variable[][][]) newVarArray("vd", VarType.Binary, nLoads, nTimeSteps, nScenarios);
		vu = (Variable[][][]) newVarArray("vu", VarType.Binary, nLoads, nTimeSteps, nScenarios);
		rcd = (Variable[][][]) newVarArray("rcd", VarType.PositiveContinuous, nLoads, nTimeSteps, nScenarios);
		rcu = (Variable[][][]) newVarArray("rcu", VarType.PositiveContinuous, nLoads, nTimeSteps, nScenarios);
		rdd = (Variable[][][]) newVarArray("rdd", VarType.PositiveContinuous, nLoads, nTimeSteps, nScenarios);
		rdu = (Variable[][][]) newVarArray("rdu", VarType.PositiveContinuous, nLoads, nTimeSteps, nScenarios);
		for(int t=0; t < nTimeSteps; t++) {
			for(int e=0; e < nLoads; e++)
				for(int i=0; i<nScenarios; i++)
					if(t > problem.getConfig().getRelaxedBinaryAfter()) {
						vd[e][t][i] = new Variable("vd_"+e+"_"+t+"_"+i, VarType.BinaryContinuous);
						vu[e][t][i] = new Variable("vu_"+e+"_"+t+"_"+i, VarType.BinaryContinuous);
					}
		}
	}

	//Write the model solution back to the problem instance
	@Override
	public void writeSolution(IMIPSolver solver) throws SolverException {
		super.writeSolution(solver);
		DecisionVariables d = problem.getVars();
		int nTFixed = problem.getMarket().getFixedPTUs();
		boolean[][][] vd = (boolean[][][]) writeVarsBack(this.vd, boolean.class);
		boolean[][][] vu = (boolean[][][]) writeVarsBack(this.vu, boolean.class);
		double[][][] rcd = (double[][][]) writeVarsBack(this.rcd);
		double[][][] rcu = (double[][][]) writeVarsBack(this.rcu);
		if(problem.getConfig().considerV2G()) {
			double[][][] rdd = (double[][][]) writeVarsBack(this.rdd);
			double[][][] rdu = (double[][][]) writeVarsBack(this.rdu);
			for(int t=nTFixed; t<nTimeSteps; t++) {
				for(int e=0; e<nLoads; e++) {
					d.rdu[e][t] = Arrays.stream(rdu[e][t]).max().orElse(0.0);
					d.rdd[e][t] = Arrays.stream(rdd[e][t]).max().orElse(0.0);
				}
			}
			d.addExtraVars("rdu", rdu, new String[] {"L", "PTU", "Scenario"});
			d.addExtraVars("rdd", rdd, new String[] {"L", "PTU", "Scenario"});
		}
		for(int t=nTFixed; t<nTimeSteps; t++) {
			for(int e=0; e<nLoads; e++) {
				d.rcu[e][t] = Arrays.stream(rcu[e][t]).max().orElse(0.0);
				d.rcd[e][t] = Arrays.stream(rcd[e][t]).max().orElse(0.0);
			}
		}
		d.addExtraVars("rcu", rcu, new String[] {"L", "PTU", "Scenario"});
		d.addExtraVars("rcd", rcd, new String[] {"L", "PTU", "Scenario"});
		d.addExtraVars("vd", vd, new String[] {"L", "PTU", "Scenario"});
		d.addExtraVars("vu", vu, new String[] {"L", "PTU", "Scenario"});
	}

	//Derive price bids from the vu and vd variables
	@Override
	protected void writePriceBidSolution() {
		Market market = getMarket();
		double[][] pu_bid = problem.getVars().bu;
		double[][] pd_bid = problem.getVars().bd;
		final double epsilon = 1e-3;
		final boolean cap = market.hasCapacityPayments();
		for(int e=0; e<getLoads().getNLoads(); e++) {		
			final int _e = e;
			for(int t=market.getFixedPTUs(); t<nTimeSteps; t++) {
				if(cap && getConfig().quantityOnly()) {
					pd_bid[e][t] = 0;
					pu_bid[e][t] = 0;
					continue;
				}
				final int tx = t;
				double maxDown = cap ? market.getSuperiorDownCapacityPayment(t) : market.getSuperiorDownRegulatingPrice(t);
				double minDown = cap ? market.getInferiorDownCapacityPayment(t) : market.getInferiorDownRegulatingPrice(t);
				double maxUp = cap ? market.getSuperiorUpCapacityPayment(t) : market.getSuperiorUpRegulatingPrice(t);
				double minUp = cap ? market.getInferiorUpCapacityPayment(t) : market.getInferiorUpRegulatingPrice(t);
				double[] down = IntStream.range(0, nScenarios).mapToDouble(
						i ->  cap ? market.getDownCapacityPayment(tx, i) : market.getDownRegulatingPrice(tx, i)).toArray();
				double[] up = IntStream.range(0, nScenarios).mapToDouble(
						i ->  cap ? market.getUpCapacityPayment(tx, i) : market.getUpRegulatingPrice(tx, i)).toArray();
				
				double upper, lower;
				upper = IntStream.range(0, nScenarios).mapToDouble(
						i -> vd[_e][tx][i].getSolution() > 0.8 ? maxDown : down[i] - epsilon).min().orElse(0.0);
				lower = IntStream.range(0, nScenarios).mapToDouble(
						i -> vd[_e][tx][i].getSolution() > 0.8 ? down[i] : minDown - epsilon).max().orElse(0.0);
				pd_bid[e][t] = Math.max(lower, upper);
				if(cap) {
					upper = IntStream.range(0, nScenarios).mapToDouble(
							i -> vd[_e][tx][i].getSolution() > 0.8 ? down[i] : maxDown + epsilon).min().orElse(0.0);
					lower = IntStream.range(0, nScenarios).mapToDouble(
							i -> vd[_e][tx][i].getSolution() > 0.8 ? minDown : down[i] + epsilon).max().orElse(0.0);
					pd_bid[e][t] = Math.max(lower, upper);
				}
				
				upper = IntStream.range(0, nScenarios).mapToDouble(
						i -> vu[_e][tx][i].getSolution() > 0.8 ? up[i] : maxUp + epsilon).min().orElse(0.0);
				lower = IntStream.range(0, nScenarios).mapToDouble(
						i -> vu[_e][tx][i].getSolution() > 0.8 ? minUp : up[i] + epsilon).max().orElse(0.0);
				pu_bid[e][t] = Math.max(lower, upper);
			}
		}
		problem.getVars().bd = pd_bid;
		problem.getVars().bu = pu_bid;
		if(clusterModel!=null)
			clusterModel.addExtraVars(problem.getVars());
	}
	
	@Override
	public void printSolution() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected LinExp getRcd(int e, int t, int i) {
		return new LinExp(rcd[e][t][i]);
	}
	@Override
	protected LinExp getRcu(int e, int t, int i) {
		return new LinExp(rcu[e][t][i]);
	}
	@Override
	protected LinExp getRdd(int e, int t, int i) {
		if(considerV2G())
			return new LinExp(rdd[e][t][i]);
		return new LinExp();
	}
	@Override
	protected LinExp getRdu(int e, int t, int i) {
		if(considerV2G())
			return new LinExp(rdu[e][t][i]);
		return new LinExp();
	}
	protected LinExp getVd(int e, int t, int i) {
		return new LinExp(vd[e][t][i]);
	}
	protected LinExp getVu(int e, int t, int i) {
		return new LinExp(vu[e][t][i]);
	}
	
	protected boolean getVds(int e, int t, int i) {
		return vd[e][t][i].getSolution() > 0.9;
	}
	protected boolean getVus(int e, int t, int i) {
		return vu[e][t][i].getSolution() > 0.9;
	}
	protected double getRds(int e, int t, int i) {
		return rcd[e][t][i].getSolution() + 
			(considerV2G() ? rdd[e][t][i].getSolution() : 0);
	}
	protected double getRus(int e, int t, int i) {
		return rcu[e][t][i].getSolution() + 
				(considerV2G() ? rdu[e][t][i].getSolution() : 0);
	}
	
	@Override
	protected LinExp getObjResDown(int e, int t, int i) {
		double imbdown = getMarket().hasCapacityPayments() ? 
				getMarket().getImbalancePrice(t, i) :
				getMarket().getDownRegulatingPrice(t, i);
		double propdown = getMarket().getDownReserveProportion(t, i);
		return getERd(e,t,i).multiplyBy(propdown * imbdown);
	}
	@Override
	protected LinExp getObjResUp(int e, int t, int i) {
		double imbup = getMarket().hasCapacityPayments() ? 
				getMarket().getImbalancePrice(t, i) :
				getMarket().getUpRegulatingPrice(t, i);
		double propup = getMarket().getUpReserveProportion(t, i);
		return getERu(e,t,i).multiplyBy(propup * imbup);
	}
	@Override
	protected LinExp getObjResCapDown(int e, int t, int i) {
		double capdown = getMarket().getDownCapacityPayment(t, i);
		return getRd(e, t, i).multiplyBy(capdown * getMarket().getPTU());//TODO multiply with Delta?
	}
	@Override
	protected LinExp getObjResCapUp(int e, int t, int i) {
		double capup = getMarket().getUpCapacityPayment(t, i);
		return getRu(e, t, i).multiplyBy(capup * getMarket().getPTU());//TODO multiply with Delta?
	}

}

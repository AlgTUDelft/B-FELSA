package nl.tudelft.alg.fcc.solution.mip;


import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;

/**
 * (Abstract) base class for the FlexibleLoadProblem MIP model
 */
public abstract class FlexibleLoadMIP extends MIP {
	protected FlexibleLoadProblem problem;
	protected MarketModel marketModel;
	protected LoadModel loadModel;
	protected GridModel gridModel;
	protected ClusterModel clusterModel;
	public int nTimeSteps, nScenarios, nLoads, nHours;

	//Variables
	Variable[]
			pda, //The amount of 'energy' to be purchased day-ahead per Hour, positive continuous
			pimb; //The amount of 'energy' to be purchased/sold on the imbalance market, continuous
				//the unit of pda, and pimb is MW, but when multiplied with Delta, it is an energy amount in MWh
	
	public FlexibleLoadMIP(FlexibleLoadProblem problem) {
		super();
		this.problem = problem;
		this.marketModel = new MarketModel();
		this.loadModel = new LoadModel();
		if (problem.getLoads().considerGrid()) this.gridModel = new GridModel();
		this.clusterModel = null;
		nTimeSteps = problem.getMarket().getNTimeSteps();
		nHours = problem.getMarket().getNumberOfHours();
		nLoads = problem.getNLoads();
		nScenarios = problem.getMarket().getNScenarios();
		}
	
	public Market getMarket() {
		return problem.getMarket();
	}
	
	public ProblemConfiguration getConfig() {
		return problem.getConfig();
	}
	
	public Loads getLoads() {
		return problem.getLoads();
	}
	
	public boolean considerV2G() {
		return problem.getConfig().considerV2G();
	}
	
	public FlexibleLoadProblem getProblem() {
		return problem;
	}

	@Override
	protected void setConstraints() {
		marketModel.addConstraints(this);
		loadModel.addConstraints(this);
		if(gridModel!=null)
			gridModel.addConstraints(this);
		if(clusterModel!=null)
			clusterModel.addConstraints(this);
		fixVariables();
		setFirstFixed();
	}

	@Override
	protected void setObjectiveFunction() {
		objectiveFunction = new LinExp();
		Market market = problem.getMarket();
		if(market.hasDayAhead())
			addDAObj();
		addImbObj();
		addPenObj();
		if(market.hasReserves()) {
			addResObj();
		}
		if(considerV2G())
			addDegObj();
	}

	/**
	 * Penalize every deviation from the limit constraints in such a way that they are never violated,
	 * except when the model otherwise would be infeasible
	 */
	protected void addPenObj() {
		loadModel.addPenalizationObj();
	}

	/**
	 * Penalize battery degradation in the case of V2G
	 */
	protected void addDegObj() {
		Market market = getMarket();
		Loads loads = getLoads();
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getResTimesteps(); t++) {
				//Battery degradation cost
				double deg = getLoads().getBatteryDegradationCost();
				double propdown = market.getExpectedDownReserveProportion(t);
				double propup = market.getExpectedUpReserveProportion(t);
				LinExp rdu = getERdu(e,t,0).multiplyBy(deg * propup);
				LinExp rdd = getERdd(e, t, 0).multiplyBy(-deg * propdown);
				LinExp pd = getEPd(e,t).multiplyBy(deg);
				objectiveFunction.addLinExps(rdu, rdd, pd);
			}
		}	
	}

	/**
	 * Add the costs/profits of reserves to the objective
	 */
	protected void addResObj() {
		Market market = getMarket();
		Loads loads = getLoads();
		for(int e=0; e<loads.getNLoads(); e++) {
			for(int t=0; t<market.getResTimesteps(); t++) {
				for(int i=0; i<nScenarios; i++) {
					double prob = market.getScenarioProbability(i);
					LinExp rd = getObjResDown(e, t, i)
							.subtractLinExp(getObjResCapDown(e,t,i))
							.multiplyBy(prob);
					LinExp ru = getObjResUp(e, t, i).multiplyBy(-1)
							.subtractLinExp(getObjResCapUp(e,t,i))
							.multiplyBy(prob);
					objectiveFunction.addLinExps(rd, ru);
				}
			}
		}
	}

	/**
	 * Add the costs/profits of the imbalance market to the objective
	 */
	protected void addImbObj() {
		Market market = getMarket();
		for(int t=0; t<market.getNPTUs(); t++) {
			//for every PTU the imbalance price is paid for energy bought on the imbalance market
			double imbPrice = market.getExpectedImbalancePrice(t);
			objectiveFunction.addLinExp(getEPimb(t).multiplyBy(imbPrice));				
		}
	}

	/**
	 * Add the costs/profits of the DA market to the objective
	 */
	protected void addDAObj() {
		Market market = getMarket();
		//For every hour the APX price is paid for energy bought day-ahead
		for(int h=0; h<market.getDATimesteps(); h++) { //for every hour
			double daPrice = market.getDAPriceByHour(h);
			objectiveFunction.addLinExp(getEPDA(h).multiplyBy(daPrice));
		}
	}
	
	/**
	 * Get the total amount of charge for a time step t
	 * @param t time step t
	 * @return the total amount of planned charge
	 */
	protected LinExp getChargeSum(int t) {
		LinExp result = new LinExp();
		Loads loads = getLoads();
		for(int e=0; e<loads.getNLoads(); e++) {
			result.addLinExp(getPc(e,t));
			if(considerV2G())
				result.subtractLinExp(getPd(e,t));
		}
		return result;
	}
	
	/**
	 * Add constraints that fix the (first) variables of the problem
	 * Such as Day-ahead decisions, and reserve commitments
	 */
	protected void setFirstFixed() {
		if(getMarket().isDayAheadFixed() && getMarket().hasDayAhead()) {
			for(int h=0; h<getMarket().getNumberOfHours(); h++) {
				fixVariable(pda[h], problem.getVars().pda[h]);
			}
		}
	}

	/**
	 * Fix variables, based on problem configurations
	 */
	protected void fixVariables() {
		if(!getMarket().hasDayAhead())
			fixVariables(0, pda);
	}
	
	@Override
	protected void setVars() {
		addVars(pimb, pda);
	}

	@Override
	protected void initiliazeVars() {
		int nTimeSteps = getMarket().getNPTUs();
		int nDATimeSteps = getMarket().getDATimesteps();
		pda =  (Variable[]) newVarArray("pda", VarType.Real, nDATimeSteps);
		pimb = (Variable[]) newVarArray("pimb", VarType.Real, nTimeSteps);
	}
	
	@Override
	public void writeSolution() throws SolverException {
		DecisionVariables d = problem.getVars();
		writeVarsBack(pimb, d.pimb);
		if(getMarket().hasDayAhead())
			writeVarsBack(pda, d.pda);
	}
	
	/**
	 * Get the linear expression for the amount of charge (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @return the linear expression for the amount of charge (energy)
	 */
	public LinExp getEPc(int e, int t) {
		return getPc(e,t).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for the amount of discharge (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @return the linear expression for the amount of discharge (energy)
	 */
	public LinExp getEPd(int e, int t) {
		return getPd(e,t).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for the amount of up reserves (charge -> not charge) (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of up reserves (charge -> not charge) (energy)
	 */
	public LinExp getERcu(int e, int t, int i) {
		return getRcu(e,t,i).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for the amount of down reserves (not charge -> charge) (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of down reserves (not charge -> charge) (energy)
	 */
	public LinExp getERcd(int e, int t, int i) {
		return getRcd(e,t,i).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for the amount of up reserves (not discharge -> discharge) (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of up reserves (not discharge -> discharge) (energy)
	 */
	public LinExp getERdu(int e, int t, int i) {
		return getRdu(e,t,i).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for the amount of down reserves (discharge -> not discharge) (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of down reserves (discharge -> not discharge) (energy)
	 */
	public LinExp getERdd(int e, int t, int i) {
		return getRdd(e,t,i).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for the amount of up reserves (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of up reserves (energy)
	 */
	public LinExp getERu(int e, int t, int i) {
		return getERcu(e,t,i).addLinExp(getERdu(e,t,i));
	}
	
	/**
	 * Get the linear expression for the amount of down reserves (energy)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of down reserves (energy)
	 */
	public LinExp getERd(int e, int t, int i) {
		return getERcd(e,t,i).addLinExp(getERdd(e,t,i));
	}
	
	/**
	 * Get the linear expression for the amount of up reserves (capacity)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of up reserves (capacity)
	 */
	public LinExp getRu(int e, int t, int i) {
		return getRcu(e,t,i).addLinExp(getRdu(e,t,i));
	}
	
	/**
	 * Get the linear expression for the amount of down reserves (capacity)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 * @return the linear expression for the amount of down reserves (capacity)
	 */
	public LinExp getRd(int e, int t, int i) {
		return getRcd(e,t,i).addLinExp(getRdd(e,t,i));
	}
	
	//Get price methods
	public double getImbDownObjPrice(int t) {
		return getMarket().getImbDownObjPrice(t);
	}
	public double getImbUpObjPrice(int t) {
		return getMarket().getImbUpObjPrice(t);
	}
	public double getExpectedDownCapacityPayment(int t) {
		return getMarket().getExpectedDownCapacityPayment(t);
	}
	public double getExpectedUpCapacityPayment(int t) {
		return getMarket().getExpectedUpCapacityPayment(t);
	}
	
	/**
	 * Get the linear expression for in the objective for the down reserves 
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 */
	protected LinExp getObjResDown(int e, int t, int i) {
		double imbdown = getImbDownObjPrice(t);
		double propdown = getMarket().getExpectedDownReserveProportion(t);
		return getERd(e,t,i).multiplyBy(propdown * imbdown);
	}
	
	/**
	 * Get the linear expression for in the objective for the up reserves 
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 */
	protected LinExp getObjResUp(int e, int t, int i) {
		double imbup = getImbUpObjPrice(t);
		double propup = getMarket().getExpectedUpReserveProportion(t);
		return getERu(e,t,i).multiplyBy(propup * imbup);
	}
	
	/**
	 * Get the linear expression for in the objective for the down reserves (in the case of a capacity market)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 */
	protected LinExp getObjResCapDown(int e, int t, int i) {
		double capdown = getExpectedDownCapacityPayment(t);
		return getRd(e, t, i).multiplyBy(capdown * getMarket().getPTU());
	}
	
	/**
	 * Get the linear expression for in the objective for the up reserves (in the case of a capacity market)
	 * @param e for load e
	 * @param t at time step t
	 * @param i in scenario i
	 */
	protected LinExp getObjResCapUp(int e, int t, int i) {
		double capup = getExpectedUpCapacityPayment(t);
		return getRu(e, t, i).multiplyBy(capup * getMarket().getPTU());
	}
	
	/**
	 * Get the amount of energy bought in the day ahead market
	 * @param h at hour h
	 */
	protected LinExp getPDA(int h) {
		return new LinExp(pda[h]);
	}
	
	/**
	 * Get the amount of energy 'bought' in the imbalance market (capacity)
	 * @param h at time step t
	 */
	protected LinExp getPimb(int t) {
		return new LinExp(pimb[t]);
	}
	
	/**
	 * Get the amount of energy bought in the day ahead market
	 * @param h at hour h
	 */
	protected LinExp getEPDA(int h) {
		return getPDA(h);
	}
	
	/**
	 * Get the amount of energy 'bought' in the imbalance market (energy)
	 * @param h at hour h
	 */
	protected LinExp getEPimb(int t) {
		return getPimb(t).multiplyBy(getMarket().getPTU());
	}
	
	/**
	 * Get the amount of planned charge (capacity)
	 * @param e for load e
	 * @param t at time step t
	 */
	protected abstract LinExp getPc(int e, int t);
	
	/**
	 * Get the amount of planned discharge (capacity)
	 * @param e for load e
	 * @param t at time step t
	 */
	protected abstract LinExp getPd(int e, int t);
	
	/**
	 * Get the amount of planned down reserves (not charge -> charge) (capacity)
	 * @param e for load e
	 * @param t at time step t
	 */
	protected abstract LinExp getRcd(int e, int t, int i);
	
	/**
	 * Get the amount of planned up reserves (charge -> not charge) (capacity)
	 * @param e for load e
	 * @param t at time step t
	 */
	protected abstract LinExp getRcu(int e, int t, int i);
	
	/**
	 * Get the amount of planned down reserves (discharge -> not discharge) (capacity)
	 * @param e for load e
	 * @param t at time step t
	 */
	protected abstract LinExp getRdd(int e, int t, int i);
	
	/**
	 * Get the amount of planned up reserves (not discharge -> discharge) (capacity)
	 * @param e for load e
	 * @param t at time step t
	 */
	protected abstract LinExp getRdu(int e, int t, int i);
	
	@Override
	public void printSolution() {
		problem.getVars().printSolution(problem);
	}

	public double getDownReserveProportion(int t, int i) {
		return getMarket().getDownReserveProportion(t, i);
	}
	
	public double getUpReserveProportion(int t, int i) {
		return getMarket().getUpReserveProportion(t, i);
	}
	
}

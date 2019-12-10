package nl.tudelft.alg.fcc.problem;

import java.time.temporal.ChronoUnit;
import java.util.Calendar;

import nl.tudelft.alg.MipSolverCore.IProblem;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.simulator.data.Data;
import nl.tudelft.alg.fcc.solution.efel.Cluster;
import nl.tudelft.alg.fcc.solution.efel.DefineClusters;
import nl.tudelft.alg.fcc.utils.Utils;

/**
 * A problem class that can be used to build and solved by one of the following ImbalanceModel, ImbalanceReserveStochastic model, etc.
 */
public class FlexibleLoadProblem implements IProblem, Cloneable {
	ProblemConfiguration config;
	Market market;
	Loads loads;
	DecisionVariables vars;
	
	//Parameters
	int startT, 	//the offset in time (expressed as a PTU number). Offset is measured against the start date of the price (and other) data
		nTimeSteps, //the number of time steps in the problem
		nUClusters,  //the number of maximum clusters by up res
		nDClusters;  //the number of maximum clusters by down res

	/**
	 * Initialize the problem with price data and with EVstates
	 * @param config the problem configuration
	 * @param data the problem data
	 */
	public FlexibleLoadProblem(ProblemConfiguration config, Data data) {
		this.config = config;
		this.market = data.getMarket();
		this.loads = data.getLoads();
		this.market.setProblem(this);
		this.loads.setProblem(this);
		//Calculate the startT as the earliest arrival time of all EVs
		startT = loads.getFirstT();
		int endT = loads.getLastT();
		nTimeSteps = Math.min(config.getNTimeSteps(), endT-startT);
		this.vars = new DecisionVariables(this);		
	}
	
	public FlexibleLoadProblem(FlexibleLoadProblem p) {
		Utils.copyMatchingFields(p, this);
		this.config = p.config;
		this.market = p.market;
		this.loads = p.loads;
		this.vars = p.vars;
	}

	public ProblemConfiguration getConfig() {
		return config;
	}
	
	public Market getMarket() {
		return market;
	}
	
	public void setMarket(Market market) {
		this.market = market;
	}

	public Loads getLoads() {
		return loads;
	}
	
	public DecisionVariables getVars() {
		return vars;
	}
	
	public void setVars(DecisionVariables vars) {
		this.vars = vars;
	}
	
	public int getNLoads() {
		return loads.getNLoads();
	}
	
	public int getStartT() {
		return startT;
	}

	public void setStartT(int startT) {
		this.startT = startT;
		this.vars.startT = startT;
	}
	
	public Cluster[] createcluster() {
		DefineClusters cc1 = new DefineClusters(this);
		cc1.createclusters(this);
		return cc1.getCluster();
	}

	public int getNTimeSteps() {
		return nTimeSteps;
	}

	public void setNTimeSteps(int nTimeSteps) {
		this.nTimeSteps = Math.min(getConfig().getNTimeSteps(), nTimeSteps);
	}
	
	public int getTimeStepFromDateString(String date) {
		Calendar d = Utils.stringToCalender(date);
		return (int)(ChronoUnit.MINUTES.between(config.startdate.toInstant(), d.toInstant()) / (60 * market.getPTU()));
	}
	
	@Override
	public String toString() {
		return vars.toString();
	}
	
	public void writeResult(FlexibleLoadProblem problem) {
		this.vars = problem.vars;
		this.nTimeSteps = problem.nTimeSteps;
		this.startT = problem.startT;
		this.config = problem.config.clone();
		this.loads = problem.loads;
	}
	
	@Override
	public FlexibleLoadProblem clone() {
		return new FlexibleLoadProblem(this);
	}

}

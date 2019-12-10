package nl.tudelft.alg.fcc.problem;

import java.util.Calendar;

import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.data.Data;
import nl.tudelft.alg.fcc.utils.Utils;

public class ProblemConfiguration implements Cloneable {
	String repr;
	Data realization;
	int nLoads, nScenarios,
		nUClusters, nDClusters, 
		shortagePenalty = 1000, //Penalty for unmet demand or battery overflow
		nTimeSteps = Integer.MAX_VALUE; //maximum of timesteps considered
	String experimentType; //Set to true to make the problem a rolling horizon problem. This means among others that only EVs that are 'currently'
									//available are considered.
	
	int fixedPTUs = 0; //the number of PTUs for which reserve bids are already fixed (use only in rolling horizon)
	private int originalFixedPTUs = 0;
	boolean dayAhead = true; //Set to true to consider Day-ahead purchases
	boolean dayAheadFixed = false; //Set to true to fix DA purchases to pre-calculated values
	boolean imbalance = true;
	boolean capacityPayment = true; //Set to true to consider capacity payments
	boolean capacityMarket = true; //set to true to have a market auction on the capacity price
	boolean reserves = true; //set to false to not consider reserves 
	double minBid; 	//minimum volume for bid
	String reservesMarketClearance; // How is the reserves market cleared (as bid, as cleared)
	
	boolean V2G = true; //Set to true to consider Vehicle-to-Grid
	boolean quantityOnly = false; //set to true to assume price-taking in reserves
	boolean grid = false; //set to true to consider grid constraints
	boolean randomScenarios;
	boolean semiStochastic = false;
	double desiredProbabilityOfAcceptance = 0.9; //Only for deterministic
	double batteryDegradation = 42;
	
	double robustness; 	//minimum volume for bid
	
	//double gd[]; 	//TODO robustness factor down for every PTU, only for rolling horizon
	//double gu[]; 	//TODO robustness factor up for every PTU, only for rolling horizon
	
	int relaxedBinaryAfter = Integer.MAX_VALUE; //After which PTUs may binary variables be relaxed (to reduce computational burden). Integer.MAX_VALUE means never
	String clusterMethod;
	Calendar startdate;
	String solutionFile;
	int fileOutput;
	String modelSetting;
	 

	public ProblemConfiguration() { }
	
	public double getGd(int t) {
		return robustness; //TODO gd[t]
	}

	public double getGu(int t) {
		return robustness; //TODO gu[t];
	}
	
	public void setShortagePenalty(int p) {
		this.shortagePenalty = p;
	}

	public void setDesiredProbabilityOfAcceptance(double prob) {
		this.desiredProbabilityOfAcceptance = prob;
	}

	public int getNLoads() {
		return nLoads;
	}

	public int getNUClusters() {
		return nUClusters;
	}

	public int getNDClusters() {
		return nDClusters;
	}

	public int getShortagePenalty() {
		return shortagePenalty;
	}

	public boolean isOnline() {
		return experimentType.equalsIgnoreCase("Rolling horizon");
	}

	public boolean considerV2G() {
		return V2G;
	}

	public boolean quantityOnly() {
		return quantityOnly;
	}

	public boolean considerGrid() {
		return grid;
	}

	public int getNScenarios() {
		return nScenarios;
	}

	public boolean randomScenarios() {
		return randomScenarios;
	}

	public double getDesiredProbabilityOfAcceptanceDown(int t) {
		if(quantityOnly) return 1.0;
		return desiredProbabilityOfAcceptance;
	}
	
	public double getDesiredProbabilityOfAcceptanceUp(int t) {
		if(quantityOnly) return 1.0;
		return desiredProbabilityOfAcceptance;
	}

	public double getRobustness() {
		return robustness;
	}

	public int getRelaxedBinaryAfter() {
		return relaxedBinaryAfter;
	}

	public String getClusterMethod() {
		return clusterMethod;
	}
	
	public int getNTimeSteps() {
		return nTimeSteps;
	}

	public void setRepr(String repr) {
		this.repr = repr;
	}
	
	public String getRepr() {
		return this.repr;
	}

	public String getOutputFolder() {
		return getRepr();
	}

	public String getReprTabbed() {
		return this.repr.replaceAll("_", "\t");
	}

	public void setRealization(Data d) {
		realization = d;
	}

	public Data getRealization() {
		return realization;
	}
	
	public PriceScenarioData getPriceDataRealization() {
		Data realization = getRealization();
		int startT = realization.getLoads().getFirstT();
		int endT = realization.getLoads().getLastT();
		return realization.getMarket().getPricedata().limit(startT, endT);
	}

	public Calendar getStartDate() {
		return startdate;
	}

	public String getSolutionFile() {
		return solutionFile;
	}
	
	public int getFixedPTUs() {
		return fixedPTUs;
	}
	
	public void setFixedPTUs(int t) {
		if(originalFixedPTUs == 0)
			originalFixedPTUs = fixedPTUs;
		fixedPTUs = t;
	}
	
	public int getOriginalFixedPTUs() {
		if(originalFixedPTUs == 0)
			return fixedPTUs;
		return originalFixedPTUs;
	}
	
	public boolean considerImbalance() {
		return imbalance;
	}
	public boolean hasDayAhead() {
		return dayAhead;
	}
	public boolean hasCapacityPayments() {
		return capacityPayment;
	}
	public boolean isCapacityMarket() {
		return capacityMarket;
	}
	public boolean hasReserves() {
		return reserves;
	}
	public double getMinBid() {
		return minBid;
	}
	
	public void ignoreReserves() {
		this.reserves = false;
	}
	
	public void ignoreDayAhead() {
		this.dayAhead = false;
	}
	
	public void fixDayAhead() {
		this.dayAheadFixed = true;
	}
	
	public boolean isDayAheadFixed() {
		return this.dayAheadFixed;
	}
	
	public String getReservesMarketClearance() {
		return reservesMarketClearance;
	}
	
	public boolean isSemiStochastic() {
		return semiStochastic;
	}
	
	public int getFileOutput() {
		return fileOutput;
	}
	
	public double getBatteryDegradation() {
		return batteryDegradation;
	}
	
	public String getModelSetting() {
		return this.modelSetting;
	}
	
	@Override
	public ProblemConfiguration clone()  {
		ProblemConfiguration res = new ProblemConfiguration();
		Utils.copyMatchingFields(this, res);
		res.realization = this.realization;
		return res;
	}

}

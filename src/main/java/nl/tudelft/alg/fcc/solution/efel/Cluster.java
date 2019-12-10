package nl.tudelft.alg.fcc.solution.efel;

import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

public class Cluster {
	FlexibleLoadProblem problem;
	//Optimization parameter
	//Make this disappear
	//int[][] avMatrix; //0,1,-1 by PTU and EV: indicator of PTU when EV arrives and departs
	//
	double[] pc, // the scheduled amount of 'charge' for every equivalent EV, for every time step 
			 pd, // the scheduled amount of 'discharge' for every equivalent EV, for every time step
			 MinSOC, // by PTU: the estimated lower bound for SOC of the eqEV, this lower bound could be simply zero;
			 MaxSOC, // by PTU: the estimated upper bound for SOC of the eqEV, this upper bound could be simply the battery capacity times plugged EVs;
			 NumEVplug; //by PTU: number of EVs per PTU. The array will be generated for all PTUs in the problem
	int []	 EVidInProblem; // by EV number: identifier of each EV in original problem
	double ChargingSpeed, PTU;
	int TotalEV, nTimeSteps, clustercounter;

	//Variables
	double[][] 	diffsoc; //For (All) scenarios and by PTU: change in charge of eqEV. This will be used for EFEL_P2
	
	// Initialize cluster with ev -data from problem
	
	public  void InitializeClusterProblem(FlexibleLoadProblem p) {
		problem = p; 
		nTimeSteps = p.getNTimeSteps();
		PTU = p.getMarket().getPTU();
		TotalEV = 0;
		clustercounter = 0;
		MinSOC = new double[nTimeSteps];
		MaxSOC = new double[nTimeSteps];
		NumEVplug = new double[nTimeSteps];
		pc = new double [nTimeSteps];
		pd = new double [nTimeSteps];
		
		//return TotalEV;
	}
	
	public Cluster() {
		TotalEV = 0;
		clustercounter = 0;
		//return TotalEV;
	}

	public double getMinimumSOC(int t) {
		return MinSOC[t];
	}
	
	public double getMaximumSOC(int t) {
		return MaxSOC[t];
	}
	
	public double getChargingSpeed() {
		return ChargingSpeed;
	}
	
	public double getDisChargingSpeed() {
		return ChargingSpeed;
	}
	
	public int getEVs() {
		return TotalEV;
	}
	
	public double NumberEVptu(int t) {
		return NumEVplug[t];
	}
	
	public int getEVid(int j) {
		return EVidInProblem[j];
	}
	
	public int [] getEVidarray() {
		return EVidInProblem;
	}
	
	public double getdSOC(int t,int s) {
		return diffsoc[t][s];
	}
	
	public void setTotalEV(int i) {
		this.TotalEV += i;
	}
	
	//This needs first the setTotalEV
	public void initializeEVperCL() {
		this.EVidInProblem = new int [TotalEV];

	}
	
	public int getclustercounter() {
		return clustercounter;
	}
	
	public void setEVperCL(int clusterID, int evdataID) {
		this.EVidInProblem[clusterID] = evdataID;
		this.clustercounter += 1;
	}
	
	//As defined, all EVs in same cluster share similar charging speed this needs at least one EV in set
	public void setChargingSpeed(FlexibleLoadProblem p) {
		this.ChargingSpeed = IntStream.range(0, TotalEV).mapToDouble(i ->
				p.getLoads().getMaximumChargingSpeed(EVidInProblem[i])).min().orElse(0);
	}
	
	public void setMaxeqEV(int t, double maxSOC, int numEV){
		this.NumEVplug[t] += numEV;
		this.MaxSOC[t] += maxSOC;
	}
	
	public void setMineqEV(int t, double minSOC){
		this.MinSOC[t] += minSOC;
	}
	
	public void setdSOCclus(double[][][] dsoc, int e, int TotalSCE) {
		this.diffsoc = new double [nTimeSteps][TotalSCE];
		for (int t = 0; t<nTimeSteps; t++) {
			for (int s = 0; s<TotalSCE; s++) this.diffsoc[t][s] = dsoc[e][t][s];		
		}
	}
	
	public double[] getPc() {
		return pc;
	}
	
	public double[] getPd() {
		return pd;
	}
	
	public void setPc(double[][] p, int e) {
		for (int t = 0; t<nTimeSteps; t++) this.pc[t] = p[e][t];
	}
	
	public void setPd(double[][] p, int e) {
		for (int t = 0; t<nTimeSteps; t++) this.pd[t] = p[e][t];
	}
	
	public void writeResult(Cluster cluster) {
		this.pc = cluster.pc;
		this.pd = cluster.pd;
		this.diffsoc = cluster.diffsoc;
	}
	
	
	
}

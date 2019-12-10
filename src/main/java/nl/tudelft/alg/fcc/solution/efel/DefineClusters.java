package nl.tudelft.alg.fcc.solution.efel;

import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.problem.ProblemConfiguration;



//A problem class that can be used to build and solved by one of the following ImbalanceModel, ImbalanceReserveStochastic model, etc.
public class DefineClusters {
	Cluster cluster[];
	
	double[] ClusterPar, // per EV: idle time or delta energy per delta time
				upperb, lowerb; //arrays for each cluster group to capture the range of clustering parameters		

	int []	GrID, GrCount, //Get group/cluster ID and count in each group/cluster
		    BatteryGro, // by EV: battery group for clustering processing
			CountPerBatGr; //by Battery groups to count number of EVs
	int MinForClusterDef, //Minimum number of EVs to consider a cluster
		//MinInClusterDef, //Minimum number of EVs in cluster - not used at the moment
		NumClusterTotal, //Total number of clusters
		nTimeSteps, //Global number of time steps for all EVs
		NumBatRange, //Number of charging speed interval
		MaxClusters,
		MaxEVfinal; //Maximum number of clusters - currently given by us but could be asked from user
	double MinSOCfactor; //The user could defined this. It helps to tighten the boundaries so the eqEV stays within charging needs upon departure

	
	// Initialize cluster with ev -data from problem
	public DefineClusters(FlexibleLoadProblem p) {
		nTimeSteps = p.getNTimeSteps();
		
		//This information could be requested from user
		NumBatRange = 5; //Number of charging speed categories
		
		MinForClusterDef = 51; //Minimum number of EVs for further clustering
		MaxClusters = 9; //Maximum number of clusters
		
		MinSOCfactor = 1;
		
		ClusterPar = new double [p.getNLoads()];
		BatteryGro = new int [p.getNLoads()];
		upperb = new double [NumBatRange];
		lowerb = new double [NumBatRange];
		
	}
	
	public void createclusters(FlexibleLoadProblem p) {
		//This information could be requested from user
		double [] MinLimBattery = {0, 0.004, 0.0065, 0.009, 0.0105}; // Per battery charging speed range, minimum limit
		int [] CountPerBatGr = {0,0,0,0,0}; //Count of EVs in each charging speed category
		int [] IdealNumCL = {0,0,0,0,0}; //COnsidering number of EVs of each charging speed and clustering limits, decide desired number of clusters
		Market market = p.getMarket();
		ProblemConfiguration config = p.getConfig();
		Loads loads = p.getLoads();
		for(int e = 0; e< p.getNLoads(); e++) {
			//Per user input, decide if clustering by 
			double diff = (loads.getMinimumSOC(e) - loads.getArrivalSOC(e));
			switch(config.getClusterMethod()) {
				case "Idle time": 
					ClusterPar[e] = (loads.getEndT(e)-loads.getStartT(e))*market.getPTU()  
						- diff/(loads.getMaximumChargingSpeed(e)*loads.getChargingEfficiency()); 
					break;
				case "dEnergy by dTime": 
					ClusterPar[e] = diff/(Math.max(loads.getEndT(e)-loads.getStartT(e),1)*market.getPTU());
					break;
				default: 
					ClusterPar[e] = 0;
					break;
			}
			//All EVs are assumed to have the largest charging speed unless different in data
			BatteryGro[e]=NumBatRange;
			//Go over each battery charging speed range to determine the ev charging speed group and update the upper and lower bounds for the gorup if necessary
			for(int i = 0; i<NumBatRange-1; i++) {
				if (loads.getMaximumChargingSpeed(e) < MinLimBattery[i+1] && loads.getMaximumChargingSpeed(e) >= MinLimBattery[i]) {
					BatteryGro[e]=i+1;
					CountPerBatGr[i] +=1;
					if(CountPerBatGr[i]==1) {
						upperb[i] = ClusterPar[e];
						lowerb[i] = ClusterPar[e];
					}
					else {
						if(upperb[i] < ClusterPar[e])upperb[i] = ClusterPar[e];
						if(lowerb[i] > ClusterPar[e])lowerb[i] = ClusterPar[e];
					}
					break;
				}
			}
			//If the battery group was not updated before, then it is the last group so this code if for updating the upper and lower bounds of the clustering parameter
			if(BatteryGro[e]==NumBatRange) {
				CountPerBatGr[NumBatRange-1] +=1;
				if(CountPerBatGr[NumBatRange-1]==1) {
					upperb[NumBatRange-1] = ClusterPar[e];
					lowerb[NumBatRange-1] = ClusterPar[e];
				}
				else {
					if(upperb[NumBatRange-1] < ClusterPar[e])upperb[NumBatRange-1] = ClusterPar[e];
					if(lowerb[NumBatRange-1] > ClusterPar[e])lowerb[NumBatRange-1] = ClusterPar[e];
				}
			}
		}
		
		//Determine the possible number of partitions per EV-charging speed group staying within the 9 EV limit
		int j = 1;
		NumClusterTotal = MaxClusters+1;
		while (NumClusterTotal > MaxClusters) {
			NumClusterTotal = 0;
			for(int i = 0; i<NumBatRange; i++) {
				IdealNumCL[i] = 0;
				if(config.getClusterMethod().equals("Idle time") || config.getClusterMethod().equals("dEnergy by dTime")) {
					if(CountPerBatGr[i] > 0) IdealNumCL[i] = (int) Math.max(1, 1+Math.floor((double)(CountPerBatGr[i]-1)/(j*MinForClusterDef)));
				}
				else {
					if(CountPerBatGr[i] > 0) IdealNumCL[i] = 1;
				}
				NumClusterTotal = NumClusterTotal + IdealNumCL[i];
			}
			j+=1;
		}
		
		cluster = new Cluster [NumClusterTotal];
		GrCount = new int [(MaxClusters+1)*10]; //This construction allows keeping track change in cluster ID without adding cycles - there might be a more efficient way of doing it
		GrID = new int [NumClusterTotal];
		double UpperB, LowerB;
		
		j=0;
		for(int i = 0; i < NumBatRange; i++) {
			if(IdealNumCL[i]>=1) {
				for (int e=1;e<=IdealNumCL[i];e++) {
					GrID[j]= (i+1)*10 + e;
					j+=1;
				}
			}
		}
		
		//Define cluster and assign EV to each
		for(int e = 0; e<p.getNLoads(); e++) {
			if(IdealNumCL[BatteryGro[e]-1] == 1) {
				BatteryGro[e] = BatteryGro[e]*10 + 1;
				GrCount[BatteryGro[e]] += 1;
			}
			else { //For groups with more than one EV
				j=BatteryGro[e];
				for(int i = 1; i <= IdealNumCL[j-1]; i++) {
					LowerB = (i-1)*(upperb[BatteryGro[e]-1]-lowerb[BatteryGro[e]-1])/IdealNumCL[BatteryGro[e]-1]+ lowerb[BatteryGro[e]-1];
					UpperB = i*(upperb[BatteryGro[e]-1]-lowerb[BatteryGro[e]-1])/IdealNumCL[BatteryGro[e]-1]+ lowerb[BatteryGro[e]-1];
					if(i==1) {
						if(ClusterPar[e] <= UpperB) {
							BatteryGro[e] = BatteryGro[e]*10 + i;
							GrCount[BatteryGro[e]] += 1;
							i = p.getNLoads()+1;
						}
					}
					else {
						if(ClusterPar[e] > LowerB &&  ClusterPar[e] <= UpperB){
							BatteryGro[e] = BatteryGro[e]*10 + i;
							GrCount[BatteryGro[e]] += 1;
							i = p.getNLoads()+1;
						}
					}
				}
			}
		}
		
		cluster = new Cluster [NumClusterTotal];
		//Assign EV to each cluster in cluster array and creating the array with EVs ID. This cannot be initialize before because it requires the number of EVs in each cluster
		for(int i=0;i<NumClusterTotal ;i++) {
			Cluster cc = new Cluster();
			cc.InitializeClusterProblem(p);
			cc.setTotalEV(GrCount[GrID[i]]);
			GrCount[GrID[i]] = i;//This creates a map to the cluster numbering
			cc.initializeEVperCL();
			cluster[i] = cc;
		}
		
		//Update minimum and maximum SOC and EVperPTU StartT to EndT...you will do this for each PTU and for each EV and CLUSTER!!
		double SOCe =0;
		for(int e= 0; e <p.getNLoads(); e++) {
			int c = GrCount[BatteryGro[e]];
			cluster[c].setEVperCL(cluster[c].getclustercounter(), e);
			SOCe = loads.getArrivalSOC(e);
			for(int t = loads.getStartT(e); t < loads.getEndT(e); t++) {
				if(SOCe + loads.getChargingEfficiency()*loads.getChargingSpeed(e,t)*market.getPTU() < loads.getBatteryCapacity(e)) 
					SOCe += loads.getChargingEfficiency()*loads.getChargingSpeed(e,t)*market.getPTU();
				else SOCe = loads.getBatteryCapacity(e);
				cluster[c].setMaxeqEV(t, SOCe, 1);
				if(t+1==loads.getEndT(e) && t+1<p.getNTimeSteps()) cluster[c].setMaxeqEV(t+1, SOCe, 0);//Check
			}
			SOCe = loads.getMinimumSOC(e)*MinSOCfactor;
			for (int t = loads.getEndT(e)-1; t >= loads.getStartT(e) && SOCe > 0; t--) {
				cluster[c].setMineqEV(t, SOCe);
				SOCe = Math.max(0, SOCe-loads.getChargingEfficiency()*loads.getChargingSpeed(e,t)*market.getPTU());
				if(t+1==loads.getEndT(e) && t+1<p.getNTimeSteps()) 
					cluster[c].setMaxeqEV(t+1, SOCe,0);//Check
			}
		}
		
		MaxEVfinal = 0;
		for(int i =0; i<NumClusterTotal; i++) {
			cluster[i].setChargingSpeed(p);
			if( MaxEVfinal < cluster[i].getEVs()) MaxEVfinal = cluster[i].getEVs();
		}
		//p.setMaxEVeachCL(MaxEVfinal);
		//CLusters are fully defined
		//return cluster;
	}
	
	public int getNumclusters() {
		return NumClusterTotal;
	}
	
	public Cluster[] getCluster() {
		return cluster;
	}
	
}

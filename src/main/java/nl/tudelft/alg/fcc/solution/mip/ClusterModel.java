package nl.tudelft.alg.fcc.solution.mip;

import java.util.ArrayList;
import java.util.List;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;

/**
 * Cluster model that contains constraints for clustering EVs for the AImbalanceReserveStochastic class
 */
public class ClusterModel implements ConstraintGenerator {
	StochasticModel mip;
	protected Variable[][] 
			fu, //counter for clusters for upward reserve for every time step, and every scenario
    		fd; //counter for clusters for downward reserve for every time step, and every scenario
	protected int nUClusters , nDClusters, nScenarios, nTimeSteps, nLoads;
	protected double MinBID;
	
	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		assert mip instanceof StochasticModel;
		this.mip = (StochasticModel) mip;
		this.nUClusters = mip.getConfig().getNUClusters();
		this.nDClusters = mip.getConfig().getNDClusters();
		this.MinBID     = mip.getMarket().getMinBid();
		this.nScenarios = mip.getMarket().getNScenarios();
		this.nTimeSteps = mip.getMarket().getNPTUs();
		this.nLoads     = mip.getLoads().getNLoads();
		addVars();
		setDownBidCounter();
		setUpBidCounter();
		if (MinBID > 0) setMinBid();
		setLimitCluster();
	}
	
	protected void addVars() {
		fu = (Variable[][]) mip.newVarArray("fu", VarType.Binary, nTimeSteps, nScenarios);
		fd = (Variable[][]) mip.newVarArray("fd", VarType.Binary, nTimeSteps, nScenarios);
		mip.addVars(fu,fd);
	}
	
	/**
	 * Add a constraint that counts the number of down bid clusters
	 */
	protected void setDownBidCounter() {
		if(mip.getConfig().quantityOnly()) return; //With a quantity-only bid, only one cluster is possible anyway
		if (nDClusters == 0) return;
		Market market = mip.getMarket();
		int sign = market.hasCapacityPayments() ? 1 : -1;
		int lastix = market.hasCapacityPayments() ? nScenarios-1 : 0;
		int startix = market.hasCapacityPayments() ? 0 : 1;
		//For down reserves and every time period and scenario these two sets of constraints count 1 if any bid in that time step.
		LinExp left, right;
		for(int t=0; t<nTimeSteps; t++) {
			int[] scenarioOrder = mip.getDownScenarioOrder(t);
			for(int e = 0; e<nLoads; e++) {
				for(int i=0; i<nScenarios-1; i++) {
					left = new LinExp(fd[t][scenarioOrder[i+startix]]);
					right = new LinExp();
					right.addLinExp(mip.getVd(e,t,scenarioOrder[i]).multiplyBy(sign));
					right.subtractLinExp(mip.getVd(e,t,scenarioOrder[i+1]).multiplyBy(sign));
					mip.addConstraint(left, right, CMP.LARGEREQ, "CountVd_"+e+"_"+t+"_"+i);
				}
				left = new LinExp(fd[t][scenarioOrder[lastix]]);
				right = mip.getVd(e,t,scenarioOrder[lastix]);
				mip.addConstraint(left, right, CMP.LARGEREQ, "CountVd0_"+e+"_"+t);
			}
		}
	}
	
	/**
	 * Add a constraint that counts the number of up bid clusters
	 */
	protected void setUpBidCounter() {
		if(mip.getConfig().quantityOnly()) return; //With a quantity-only bid, only one cluster is possible anyway
		if (nUClusters == 0) return;
		//For up reserves every time period and scenario these two sets of constraints count 1 if any bid in that time step.
		LinExp left, right;
		int last = nScenarios-1;
		for(int t=0; t<nTimeSteps; t++) {
			int[] scenarioOrder = mip.getUpScenarioOrder(t);
			for(int e = 0; e<nLoads; e++) {
				for(int i=0; i<nScenarios-1; i++) {
					left = new LinExp(fu[t][scenarioOrder[i]]);
					right = new LinExp();
					right.addLinExp(mip.getVu(e,t,scenarioOrder[i]));
					right.subtractLinExp(mip.getVu(e,t,scenarioOrder[i+1]));
					mip.addConstraint(left, right, CMP.LARGEREQ, "CountVu_"+e+"_"+t+"_"+i);	
				}
				left = new LinExp(fu[t][scenarioOrder[last]]);
				right = mip.getVu(e,t,scenarioOrder[last]);
				mip.addConstraint(left, right, CMP.LARGEREQ, "CountVu0_"+e+"_"+t);
			}
		}
	}
	
	/**
	 * Add a constraint that sets the minimum bid size (in MW)
	 */
	protected void setMinBid() {
		Market market = mip.getMarket();
		int sign = market.hasCapacityPayments() ? 1 : -1;
		int startix = market.hasCapacityPayments() ? 0 : 1 ;
		int last = nScenarios-1;
		int lastix = market.hasCapacityPayments() ? last : 0;
		LinExp leftu, rightu, leftd, rightd;
		//Minimum bid value
		for(int t=0; t<nTimeSteps; t++) {
			if(t<market.getFixedPTUs()) continue;
			//Prices ordered from high to low, so r[i] <= r[i+1]
			// This means r[i+1] - r[i] >= 0
			int[] scenarioOrderDown = mip.getDownScenarioOrder(t);
			int[] scenarioOrderUp = mip.getUpScenarioOrder(t);
			for(int i=0; i<nScenarios-1; i++) {
				leftu = new LinExp(MinBID, fu[t][scenarioOrderUp[i]]);
				leftd = new LinExp(MinBID, fd[t][scenarioOrderDown[i+startix]]);
				rightu = new LinExp();
				rightd = new LinExp();
				int i0u = scenarioOrderUp[i];
				int i1u = scenarioOrderUp[i+1];
				int i0d = scenarioOrderDown[i];
				int i1d = scenarioOrderDown[i+1];
				for(int e = 0; e<nLoads; e++) {
					rightu.addLinExp(mip.getRcu(e,t,i0u));
					rightu.addLinExp(mip.getRdu(e,t,i0u));
					rightu.subtractLinExp(mip.getRcu(e,t,i1u));
					rightu.subtractLinExp(mip.getRdu(e,t,i1u));
					rightd.addLinExp(mip.getRcd(e,t,i0d).multiplyBy(sign));
					rightd.addLinExp(mip.getRdd(e,t,i0d).multiplyBy(sign));
					rightd.subtractLinExp(mip.getRcd(e,t,i1d).multiplyBy(sign));
					rightd.subtractLinExp(mip.getRdd(e,t,i1d).multiplyBy(sign));
				}
				if (nUClusters > 0) mip.addConstraint(leftu, rightu, CMP.SMALLEREQ, "MinBidU_" + t + "_" + i);
				if (nDClusters > 0) mip.addConstraint(leftd, rightd, CMP.SMALLEREQ, "MinBidD_" + t + "_" + i);
			}
			leftu = new LinExp(MinBID, fu[t][scenarioOrderUp[last]]);
			leftd = new LinExp(MinBID, fd[t][scenarioOrderDown[lastix]]);
			rightu = new LinExp();
			rightd = new LinExp();
			for(int e = 0; e<nLoads; e++) {
				rightu.addLinExp(mip.getRcu(e,t,scenarioOrderUp[last]));
				rightu.addLinExp(mip.getRdu(e,t,scenarioOrderUp[last]));
				rightd.addLinExp(mip.getRcd(e,t,scenarioOrderUp[lastix]));
				rightd.addLinExp(mip.getRdd(e,t,scenarioOrderUp[lastix]));
			}
			if (nUClusters > 0) mip.addConstraint(leftu, rightu, CMP.SMALLEREQ, "MinBidU0_" + t);
			if (nDClusters > 0) mip.addConstraint(leftd, rightd, CMP.SMALLEREQ, "MinBidD0_" + t);
		}
	}
	
	/**
	 * Add a constraint that sets the maximum size of a cluster
	 */
	protected void setLimitCluster() {
		if(mip.getConfig().quantityOnly()) return; //With a quantity-only bid, only one cluster is possible anyway
		//Limits the variables fs up and down by number of clusters
		for(int t=0; t<nTimeSteps; t++) {
			if(t<mip.getMarket().getFixedPTUs()) continue;
			LinExp leftu, leftd, rightu, rightd;
			leftu = new LinExp();
			leftd = new LinExp();
			for(int i=0;i<nScenarios; i++) {
				leftu.addTerm(fu[t][i]);
				leftd.addTerm(fd[t][i]);
			}
			rightu = new LinExp(nUClusters);
			rightd = new LinExp(nDClusters);
			if (nUClusters > 0) mip.addConstraint(leftu, rightu, CMP.SMALLEREQ, "LClusterU" + t);
			if (nDClusters > 0) mip.addConstraint(leftd, rightd, CMP.SMALLEREQ, "LClusterD" + t);
		}
	}
	

	/**
	 * Get the down clusters from the solution
	 * @return an array describing per time step per cluster a list of EVs that are part of that cluster
	 */
	protected int[][][] getDClusters() {
		Market market = mip.getMarket();
		int[][][] clusters = new int[nTimeSteps][nDClusters][];
		int last = market.hasCapacityPayments() ? nScenarios-1 : 0;
		int starti = market.hasCapacityPayments() ? 0 : 1;
		int sign = market.hasCapacityPayments() ? 1 : -1;
		boolean signb = market.hasCapacityPayments();
		for(int t=0; t<nTimeSteps; t++) {
			int cl = 0;
			int[] scenarioOrder = mip.getDownScenarioOrder(t);
			int lastix = scenarioOrder[last];
			for(int i=0; i<nScenarios-1; i++) {
				int ix = scenarioOrder[i+starti];
				int ixnow = scenarioOrder[i];
				int ixnext = scenarioOrder[i+1];
				if(fd[t][ix].getSolution() > 0.9) {
					List<Integer> cluster = new ArrayList<Integer>();
					for(int e=0; e<nLoads; e++) {
						if( ((signb && mip.getVds(e,t,ixnow) && !mip.getVds(e,t,ixnext)) ||
							(!signb && !mip.getVds(e,t,ixnow) && mip.getVds(e,t,ixnext))) &&
							sign*mip.getRds(e,t,ix) - sign*mip.getRds(e,t,ixnext) > 1e-3){
							cluster.add(e);
						}
					}
					if(cluster.size() > 0)
						clusters[t][cl++] = cluster.stream().mapToInt(a->a).toArray();
				}
			}
			if(fd[t][lastix].getSolution() > 0.9) {
				List<Integer> cluster = new ArrayList<Integer>();
				for(int e=0; e<nLoads; e++) {
					if(mip.getVds(e,t,lastix) && mip.getRds(e,t,lastix) >= 1e-3) {
						cluster.add(e);
					}
				}
				if(cluster.size() > 0)
					clusters[t][cl++] = cluster.stream().mapToInt(a->a).toArray();
			}
			while(cl < nDClusters)
				clusters[t][cl++] = new int[0];
		}
		return clusters;
	}
	
	/**
	 * Get the up clusters from the solution
	 * @return an array describing per time step per cluster a list of EVs that are part of that cluster
	 */
	protected int[][][] getUClusters() {
		int[][][] clusters = new int[nTimeSteps][nUClusters][];
		int last = nScenarios-1;
		for(int t=0; t<nTimeSteps; t++) {
			int cl = 0;
			int[] scenarioOrder = mip.getUpScenarioOrder(t);
			int lastix = scenarioOrder[last];
			for(int i=0; i<nScenarios-1; i++) {
				int ix = scenarioOrder[i];
				int ixnext = scenarioOrder[i+1];
				if(fu[t][ix].getSolution() > 0.9) {
					List<Integer> cluster = new ArrayList<Integer>();
					for(int e=0; e<nLoads; e++) {
						if(mip.getVus(e,t,ix) && !mip.getVus(e,t,ixnext) &&
							(mip.getRus(e,t,ix) - mip.getRus(e,t,ixnext) > 1e-3))
							cluster.add(e);
					}
					if(cluster.size() > 0)
						clusters[t][cl++] = cluster.stream().mapToInt(a->a).toArray();
				}
			}
			if(fu[t][lastix].getSolution() > 0.9) {
				List<Integer> cluster = new ArrayList<Integer>();
				for(int e=0; e<nLoads; e++) {
					if(mip.getVus(e,t,lastix) && mip.getRus(e,t,lastix) >= 1e-3) {
						cluster.add(e);
					}
				}
				if(cluster.size() > 0)
					clusters[t][cl++] = cluster.stream().mapToInt(a->a).toArray();
			}
			while(cl < nUClusters)
				clusters[t][cl++] = new int[0];
		}
		return clusters;
	}

	public void addExtraVars(DecisionVariables d) {
		if (nUClusters > 0) {
			double[][] fu = (double[][]) mip.writeVarsBack(this.fu);
			d.addExtraVars("fu", fu, new String[] { "PTU", "Scenario" });
			d.addExtraVars("UClusters", getUClusters(), new String[] { "PTU", "Cluster", "L" });
		}
		if (nDClusters > 0) {
			double[][] fd = (double[][]) mip.writeVarsBack(this.fd);
			d.addExtraVars("fd", fd, new String[] { "PTU", "Scenario" });
			d.addExtraVars("DClusters", getDClusters(), new String[] { "PTU", "Cluster", "L" });
		}
	}
	
	public void fixClusterVariables(double[][] fd, double[][] fu) {
		if (nDClusters > 0) mip.fixVariable(this.fd, fd, CMP.SMALLEREQ);
		if (nUClusters > 0) mip.fixVariable(this.fu, fu, CMP.SMALLEREQ);
	}


}

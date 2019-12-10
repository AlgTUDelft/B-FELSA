package nl.tudelft.alg.fcc.solution.efel;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.solution.mip.CompactStochasticModel;

//Solves the imbalance reserve problem using stochastic optimization for multiple EVs
//The model results in at most one regulation bid for every PTU.
//Use the InbalanceReserveStochasticCompact for better run time results
public class EFEL_P2 extends CompactStochasticModel {
	Cluster cluster[]; //class where all the information per cluster is stored and that links each EV to a cluster
	DecisionVariables fixes;
	int neqEVs;
		
	public EFEL_P2(FlexibleLoadProblem p, DecisionVariables d, Cluster[] c) {
		super(p);
		this.neqEVs = c.length;
		this.clusterModel = null;
		this.cluster = c;
		this.fixes = d;
			
	}
	
	@Override
	protected void setConstraints() {
		super.setConstraints();
		//setSums();
		fixPriceBids();
	}
	
	protected void fixPriceBids() {
		boolean[][][] vd = (boolean[][][]) fixes.getExtraVars("vd");
		boolean[][][] vu = (boolean[][][]) fixes.getExtraVars("vu");
		LinExp left1, right1, left2, right2;
		for (int e = 0; e < neqEVs; e++) {
			Cluster c = cluster[e];
			for (int t = 0; t < nTimeSteps; t++) {
				for (int i = 0; i < nScenarios; i++) {
					left1 =  new LinExp(vd[e][t][i]);
					left2 =  new LinExp(vu[e][t][i]); 
					for (int j = 0; j < c.getEVs(); j++) {
						int evid = c.getEVid(j);
						right1 = new LinExp(this.vd[evid][t][i]);
						right2 = new LinExp(this.vu[evid][t][i]);
						addConstraint(left1, right1, CMP.EQ, "fxVD_"+evid+"_"+t+"_"+i);
						addConstraint(left2, right2, CMP.EQ, "fxVU_"+evid+"_"+t+"_"+i);
					}
				}
			}
		}
	}
	
	
	protected void setSums() {
		LinExp left1,right1,left2,right2,left3,right3,left4,right4;
		boolean[][][] vd = (boolean[][][]) fixes.getExtraVars("vd");
		boolean[][][] vu = (boolean[][][]) fixes.getExtraVars("vu");
		for(int e=0; e<neqEVs; e++) {
			Cluster c = cluster[e];
			for(int t=0; t<nTimeSteps; t++){
				for(int i=0; i<nScenarios; i++) {
					left1 =  new LinExp(vd[e][t][i] ? fixes.rcd[e][t] : 0);
					left2 =  new LinExp(vu[e][t][i] ? fixes.rcu[e][t] : 0); 
					left3 =  new LinExp(vd[e][t][i] ? fixes.rdd[e][t] : 0); 
					left4 =  new LinExp(vu[e][t][i] ? fixes.rdu[e][t] : 0); 
					right1 = new LinExp();
					right2 = new LinExp();
					right3 = new LinExp();
					right4 = new LinExp();
					for(int j =0; j<c.getEVs();j++) {
						int evid = c.getEVid(j);
						right1.addLinExp(getRcd(evid,t,i));
						right2.addLinExp(getRcu(evid,t,i));
						right3.addLinExp(getRdd(evid,t,i));
						right4.addLinExp(getRdu(evid,t,i));
					}
					addConstraint(left1, right1, CMP.EQ, "RESEq_RCD_"+e+"_"+t+"_"+i);
					addConstraint(left2, right2, CMP.EQ, "RESEq_RCU_"+e+"_"+t+"_"+i);
					addConstraint(left3, right3, CMP.EQ, "RESEq_RDD_"+e+"_"+t+"_"+i);
					addConstraint(left4, right4, CMP.EQ, "RESEq_RDU_"+e+"_"+t+"_"+i);
				}
				left1 = new LinExp(fixes.p[e][t]);
				left2 = new LinExp(fixes.dp[e][t]);
				right1 = new LinExp();
				right2 = new LinExp();
				for(int j =0; j<c.getEVs();j++) {
					int evid = c.getEVid(j);
					right1.addLinExp(getPc(evid,t));
					right2.addLinExp(getPd(evid,t));
				}
				addConstraint(left1, right1, CMP.EQ, "PCEq_"+e+"_"+t);
				addConstraint(left2, right2, CMP.EQ, "PDEq_"+e+"_"+t);
			}			
		}
	}

}
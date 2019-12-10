package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.problem.DecisionVariables;

/**
 * This Cluster model ensures that an EV is always clustered in the same cluster
 */
public class ClusterModelPerEV extends ClusterModel {
	Variable[][] fixcl; //is the indicator of sessions assigned to clusters
	Variable[][][] vdc, // is the downward reserve bid called? for every cluster, time step, per scenario,  binary
				vuc; // is the upward reserve bid called? for every cluster, time step, per scenario,  binary
	
	public ClusterModelPerEV() {
		
	}
	
	@Override
	public void addConstraints(FlexibleLoadMIP mip) {
		super.addConstraints(mip);
		setOnecluster();
	}
	
	@Override
	protected void addVars() {
		super.addVars();
		vdc = (Variable[][][]) mip.newVarArray("vdc", VarType.Binary, nUClusters, nTimeSteps, nScenarios);
		vuc = (Variable[][][]) mip.newVarArray("vuc", VarType.Binary, nUClusters, nTimeSteps, nScenarios);
		fixcl = (Variable[][]) mip.newVarArray("fixcl", VarType.Binary, nLoads, nUClusters);
		mip.addVars(vdc,vuc,fixcl);
	}
	
	protected void setOnecluster() {
		//The variable will be zero for the cluster to which e belongs
		for(int e=0; e<nLoads; e++) {
			LinExp leftp, right, leftn, rightu, rightd;
			right = new LinExp(nUClusters-1);
			leftp = new LinExp();
			leftn = new LinExp();
			rightu = new LinExp();
			rightd = new LinExp();
			for(int c=0;c<nUClusters; c++) {
				leftp.addTerm(fixcl[e][c]);
				leftn.addTerm(fixcl[e][c],-1);
				for(int i=0;i<nScenarios; i++) {
					for(int t=0; t<nTimeSteps; t++) {
						rightu = mip.getVu(e,t,i);
						rightu.addTerm(vuc[e][t][i],-1);
						rightd = mip.getVd(e,t,i);
						rightd.addTerm(vdc[e][t][i],-1);
						mip.addConstraint(leftp,rightu,CMP.SMALLEREQ, "negUcluster"+e+"_"+t+"_"+i+"_"+c);
						mip.addConstraint(leftn,rightu,CMP.LARGEREQ, "posUcluster"+e+"_"+t+"_"+i+"_"+c);
						mip.addConstraint(leftp,rightd,CMP.SMALLEREQ, "negDcluster"+e+"_"+t+"_"+i+"_"+c);
						mip.addConstraint(leftn,rightd,CMP.LARGEREQ, "posDcluster"+e+"_"+t+"_"+i+"_"+c);
					}
				}
			}
			mip.addConstraint(leftp,right,CMP.EQ, "onecluster"+e);
		}
	}
	
	@Override
	public void addExtraVars(DecisionVariables d) {
		super.addExtraVars(d);
		double[][] fixcl = (double[][]) mip.writeVarsBack(this.fixcl);
		double[][][] vdc = (double[][][]) mip.writeVarsBack(this.vdc);
		double[][][] vuc = (double[][][]) mip.writeVarsBack(this.vuc);
		d.addExtraVars("fixcl", fixcl, new String[] {"Load", "Cluster"});
		d.addExtraVars("vdc", vdc, new String[] {"Load", "PTU", "Scenario"});
		d.addExtraVars("vuc", vuc, new String[] {"Load", "PTU", "Scenario"});
	}

}

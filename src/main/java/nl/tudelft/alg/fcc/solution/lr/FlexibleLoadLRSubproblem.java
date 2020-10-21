package nl.tudelft.alg.fcc.solution.lr;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.FlexibleLoad;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.solution.mip.CompactStochasticModel;

/**
 * The subproblem for the langrangian solution for the flexible load problem
 */
public class FlexibleLoadLRSubproblem extends CompactStochasticModel {
	FlexibleLoadLRProblem step;
	Variable solution;
	Loads loads;
	int loadID;
	double [][][] multipliersUP, multipliersDO;
	
	public FlexibleLoadLRSubproblem(FlexibleLoadProblem p, int e, FlexibleLoadLRProblem s) {
		super(p);
		step = s;
		nLoads = 1;
		loads = new Loads(new FlexibleLoad[] {p.getLoads().getLoads()[e]}, null);
		loads.setProblem(p);
		loadID = e;
		clusterModel = null;
	}

	@Override
	public Loads getLoads() {
		return loads;
	}
	
	
	protected void addMultipliersObj() {
		for(int t=0; t<nTimeSteps; t++) {
			int[] scenarioOrderUP = getUpScenarioOrder(t);
			int[] scenarioOrderDO = getDownScenarioOrder(t);
			for(int i=0; i<nScenarios; i++) {
				//Lagrangean multipliers
				int sign = getMarket().hasCapacityPayments() ? 1 : -1;
				//int lastix = getMarket().hasCapacityPayments() ? nScenarios-1 : 0;
				//int startix = getMarket().hasCapacityPayments() ? 0 : 1;
				if(i==nScenarios-1) {
					//objectiveFunction.addTerm(vd[0][t][scenarioOrderDO[lastix]],step.getmultipliersDO(loadID,t,nScenarios-1));
					objectiveFunction.addTerm(vu[0][t][scenarioOrderUP[i]],step.getmultipliersUP(loadID,t,nScenarios-1));
					
					//objectiveFunction.addTerm(rcd[0][t][scenarioOrderDO[lastix]],-1*sign*step.getmultipliersMBDO(t,nScenarios-1));
					objectiveFunction.addTerm(rcu[0][t][scenarioOrderUP[i]],-1*step.getmultipliersMBUP(t,nScenarios-1));
					//objectiveFunction.addTerm(rdd[0][t][scenarioOrderDO[lastix]],-1*sign*step.getmultipliersMBDO(t,nScenarios-1));
					objectiveFunction.addTerm(rdu[0][t][scenarioOrderUP[i]],-1*step.getmultipliersMBUP(t,nScenarios-1));
				}
				else {
					objectiveFunction.addTerm(vd[0][t][scenarioOrderDO[i]], sign*step.getmultipliersDO(loadID,t,i));
					objectiveFunction.addTerm(vd[0][t][scenarioOrderDO[i+1]], -1*sign*step.getmultipliersDO(loadID,t,i));
					objectiveFunction.addTerm(vu[0][t][scenarioOrderUP[i]], step.getmultipliersUP(loadID,t,i));
					objectiveFunction.addTerm(vu[0][t][scenarioOrderUP[i+1]], -1*step.getmultipliersUP(loadID,t,i));
					
					objectiveFunction.addTerm(rcd[0][t][scenarioOrderDO[i]], -1*sign*step.getmultipliersMBDO(t,i));
					objectiveFunction.addTerm(rcd[0][t][scenarioOrderDO[i+1]], sign*step.getmultipliersMBDO(t,i));
					objectiveFunction.addTerm(rcu[0][t][scenarioOrderUP[i]], -1*step.getmultipliersMBUP(t,i));
					objectiveFunction.addTerm(rcu[0][t][scenarioOrderUP[i+1]], step.getmultipliersMBUP(t,i));
					
					objectiveFunction.addTerm(rdd[0][t][scenarioOrderDO[i]], -1*sign*step.getmultipliersMBDO(t,i));
					objectiveFunction.addTerm(rdd[0][t][scenarioOrderDO[i+1]], sign*step.getmultipliersMBDO(t,i));
					objectiveFunction.addTerm(rdu[0][t][scenarioOrderUP[i]], -1*step.getmultipliersMBUP(t,i));
					objectiveFunction.addTerm(rdu[0][t][scenarioOrderUP[i+1]], step.getmultipliersMBUP(t,i));
				}
			}
		}
		
	}
	
	@Override
	protected void setObjectiveFunction() {
		super.setObjectiveFunction();
		addMultipliersObj();
		LinExp right = new LinExp(solution);
		addConstraint((LinExp) objectiveFunction, right, CMP.EQ, "Objective");
		objectiveFunction = new LinExp(solution);	
	}
	
	@Override
	protected void setVars() {
		super.setVars();
		addVars(solution);
	}

	@Override
	protected void initiliazeVars() {
		super.initiliazeVars();
		solution = (Variable) newVarArray("solu",VarType.Real); 
	}

	//Write the model solution back to the problem instance
	@Override
	public void writeSolution() throws SolverException {
		super.writeSolution();
		DecisionVariables d = problem.getVars();
		step.setLBObjPerSubproblem(loadID, solution.getSolution());
		
		boolean[][] vd = (boolean[][]) writeVarsBack(this.vd[0], boolean.class);
		boolean[][] vu = (boolean[][]) writeVarsBack(this.vu[0], boolean.class);
		double[][] rcd = (double[][]) writeVarsBack(this.rcd[0], double.class);
		double[][] rcu = (double[][]) writeVarsBack(this.rcu[0], double.class);
		step.setPc(loadID, d.p[loadID]);
		step.setVd(loadID, vd);
		step.setVu(loadID, vu);
		step.setRcd(loadID, rcd);
		step.setRcu(loadID, rcu);
		if(problem.getConfig().considerV2G()) {
			double[][] rdd = (double[][]) writeVarsBack(this.rdd[0], double.class);
			double[][] rdu = (double[][]) writeVarsBack(this.rdu[0], double.class);
			step.setRdd(loadID, rdd);
			step.setRdu(loadID, rdu);
			step.setPd(loadID, d.dp[loadID]);
		} 
	}
	
	@Override
	public void printSolution() {
		// TODO Auto-generated method stub
	}
}
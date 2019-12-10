package nl.tudelft.alg.fcc.solution.lr;

import java.io.File;
import java.io.IOException;

import nl.tudelft.alg.MipSolverCore.LRModel;
import nl.tudelft.alg.MipSolverCore.LRProblem;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.utils.CSVWriter;

/**
 * The lagrangian problem class describing the flexible load problem lagrangian
 */
public class FlexibleLoadLRProblem extends LRProblem<FlexibleLoadProblem> {
	int
		nTimeSteps,nLoads,nScenarios,
		UClusters, DClusters,
		sign, lastix, startix;
	int [][] orderUP,orderdown;
	double [][][] vu,vd,
				newGradientUP, newGradientDO, 
				oldGradientUP, oldGradientDO,
				subGradientUP, subGradientDO, 
				multipliersUP, multipliersDO;
	double [][] fu,fd;
	
	// Initialize cluster with ev - data from problem
	public FlexibleLoadLRProblem(LRModel<FlexibleLoadProblem> instance) {
		super(instance);
		this.nTimeSteps = problem.getNTimeSteps();
		this.nLoads = problem.getLoads().getNLoads();
		this.nScenarios = problem.getMarket().getNScenarios();
		this.vu = new double [nLoads][nTimeSteps][nScenarios];
		this.vd = new double [nLoads][nTimeSteps][nScenarios];
		this.newGradientUP = new double [nLoads][nTimeSteps][nScenarios];
		this.newGradientDO = new double [nLoads][nTimeSteps][nScenarios];
		this.oldGradientUP = new double [nLoads][nTimeSteps][nScenarios];
		this.oldGradientDO = new double [nLoads][nTimeSteps][nScenarios];
		this.subGradientUP = new double [nLoads][nTimeSteps][nScenarios];
		this.subGradientDO = new double [nLoads][nTimeSteps][nScenarios];
		this.multipliersUP = new double [nLoads][nTimeSteps][nScenarios];
		this.multipliersDO = new double [nLoads][nTimeSteps][nScenarios];
		this.fu = new double [nTimeSteps][nScenarios];
		this.fd = new double [nTimeSteps][nScenarios];
		this.UClusters = problem.getConfig().getNUClusters();
		this.DClusters = problem.getConfig().getNDClusters();
		this.sign =  problem.getMarket().hasCapacityPayments() ? 1 : -1;
		this.lastix = problem.getMarket().hasCapacityPayments() ? nScenarios-1 : 0;
		this.startix = problem.getMarket().hasCapacityPayments() ? 0 : 1;
		this.orderUP = new int[nTimeSteps][nScenarios];
		this.orderdown = new int[nTimeSteps][nScenarios]; 
		
		for(int t = 0; t<nTimeSteps; t++) {
			int[] tempSCorderup = getUpScenarioOrder(t,problem);
			int[] tempSCorderdo = getDownScenarioOrder(t,problem);
			for(int s = 0; s<nScenarios; s++){
				this.orderUP[t][s] = tempSCorderup [s];
				this.orderdown[t][s] = tempSCorderdo [s];
			}
		}
	}
		
	public void setVd(int e, boolean [][] sol) {
		for(int i=0;i<nScenarios;i++) {
			for(int t=0; t<nTimeSteps;t++) {
				this.vd[e][t][i] = sol[t][i] ? 1 : 0;
			}
		}
	}
	public void setVu(int e, boolean [][] sol) {
		for(int i=0;i<nScenarios;i++) {
			for(int t=0; t<nTimeSteps;t++) {
				this.vu[e][t][i] = sol[t][i] ? 1 : 0;
			}
		}
	}
	public double[][] getFu() {
		return this.fu;
	}
	public double[][] getFd() {
		return this.fd;
	}
	
	public double getmultipliersDO(int e, int t, int i) {
		return this.multipliersDO[e][t][i];
	}
	
	public double getmultipliersUP(int e, int t, int i) {
		return this.multipliersUP[e][t][i];
	}
	
	@Override
	public void addTolowerObj() {
		double[][] summultiUP, summultiDO;
		int[][] orsummultiUP, orsummultiDO, revorderUP, revorderdown;
		summultiUP=new double [nTimeSteps][nScenarios];
		summultiDO=new double [nTimeSteps][nScenarios];
		orsummultiUP=new int [nTimeSteps][nScenarios];
		orsummultiDO=new int [nTimeSteps][nScenarios];
		revorderUP=new int [nTimeSteps][nScenarios];
		revorderdown=new int [nTimeSteps][nScenarios];
		for(int t=0;t<nTimeSteps;t++) {
			for(int i=0;i<nScenarios;i++) {
				revorderUP[t][orderUP[t][i]] = i;
				revorderdown[t][orderdown[t][i]] = i;
				for(int e=0;e<nLoads;e++) {
					summultiUP[t][i] += this.multipliersUP[e][t][i];
					summultiDO[t][i] += this.multipliersDO[e][t][i];
				}
			}
		}
		
		//Order multipliers from largest to smallest (Minimization of - multiplier*fd-u)
		for(int t=0; t<nTimeSteps; t++) {
			orsummultiUP[t][0] = 0;
			orsummultiDO[t][0] = 0;
			this.fu[t][0]=0;
			this.fd[t][0]=0;
			for(int i=1; i<nScenarios; i++) {
				this.fu[t][i]=0;
				this.fd[t][i]=0;
				orsummultiUP[t][i] = i;
				orsummultiDO[t][i] = i;
				for(int j=i-1;j>=0;j--) {
					if ( summultiUP[t][i] > summultiUP[t][orsummultiUP[t][j]] ) {
						orsummultiUP[t][j+1] = orsummultiUP[t][j];
						orsummultiUP[t][j] = i;
					}
					if ( summultiDO[t][i] > summultiDO[t][orsummultiDO[t][j]] ) {
						orsummultiDO[t][j+1] = orsummultiDO[t][j];
						orsummultiDO[t][j] = i;
					}
				}
			}
		}
		
		for(int t=0; t<nTimeSteps; t++) {
			for(int i=0; i<nScenarios; i++) {
				if(i<UClusters) this.fu[t][orderUP[t][orsummultiUP[t][i]]] = 1;//revorderUP or orderUP???
				if(i<DClusters) this.fd[t][orderdown[t][orsummultiDO[t][i]]] = 1;	
			}
		}
		
		for(int t=0; t<nTimeSteps; t++) {
			for(int i=0; i<nScenarios; i++) {
				this.lowerObj-= this.fu[t][orderUP[t][i]]*summultiUP[t][i];
				this.lowerObj-= this.fd[t][orderdown[t][i]]*summultiDO[t][i];
			}
		}
	}
	
	@Override
	public void updateMultipliers() {
		double stepsize;
		if(parC > parF && this.iteration > parC) {
			this.counter2 +=1;
			if (counter2 == parC+1) {
				this.parB = parB/parD;
				this.parC = parC/parD;
				this.counter2 = 0;
			}
		}
		else if(parC <= parF && this.iteration > parC) {
			this.counter1 +=1;
			if (counter1 == parF+1) {
				this.parB = parB/parD;
				this.counter1 = 0;
			}
		}
		
		//Direction of each for each multiplier - how is the order compatible?
		for(int e=0;e<nLoads;e++) {
			for(int t=0;t<nTimeSteps;t++) {
				for(int i=0;i<nScenarios-1;i++) {
					this.newGradientUP[e][t][i] = -fu[t][orderUP[t][i]]+(this.vu[e][t][orderUP[t][i]]-this.vu[e][t][orderUP[t][i+1]]);
					this.newGradientDO[e][t][i] = -fd[t][orderdown[t][i+startix]]+(sign*this.vd[e][t][orderdown[t][i]]-sign*this.vd[e][t][orderdown[t][i+1]]);
				}
				this.newGradientUP[e][t][nScenarios-1] = -fu[t][orderUP[t][nScenarios-1]]+this.vu[e][t][orderUP[t][nScenarios-1]];
				this.newGradientDO[e][t][nScenarios-1] = -fd[t][orderdown[t][lastix]]+this.vd[e][t][orderdown[t][lastix]];
			}		
		}
		
		//Update subgradient
		stepsize = 0;
		for(int e=0;e<nLoads;e++) {
			for(int t=0;t<nTimeSteps;t++) {
				for(int i=0;i<nScenarios;i++) {
					this.subGradientUP[e][t][i] = this.newGradientUP[e][t][i]+this.parFi*this.oldGradientUP[e][t][i];
					this.subGradientDO[e][t][i] = this.newGradientDO[e][t][i]+this.parFi*this.oldGradientDO[e][t][i];
					stepsize += Math.pow(this.subGradientUP[e][t][i],2);
					stepsize += Math.pow(this.subGradientDO[e][t][i],2);
				}
			}		
		}
		//Estimate stepsize
		stepsize = this.parB*(this.upperObj-this.lowerObj)/stepsize;
		if(stepsize>this.maxStep)stepsize = this.maxStep;
		//Update multipliers
		for(int e=0;e<nLoads;e++) {
			for(int t=0;t<nTimeSteps;t++) {
				for(int i=0;i<nScenarios;i++) {
					//if(iteration==1) {
						//this.multipliersUP[e][t][i] = this.multipliersUP[e][t][i]+stepsize*this.subGradientUP[e][t][i];
						//this.multipliersDO[e][t][i] = this.multipliersDO[e][t][i]+stepsize*this.subGradientDO[e][t][i];
					//}else {
						this.multipliersUP[e][t][i] = Math.max(0, this.multipliersUP[e][t][i]+stepsize*this.subGradientUP[e][t][i]);
						this.multipliersDO[e][t][i] = Math.max(0, this.multipliersDO[e][t][i]+stepsize*this.subGradientDO[e][t][i]);
					//}
				}
			}
		}
		
		this.oldGradientUP = this.newGradientUP;
		this.oldGradientDO = this.newGradientDO;
		//this.iteration++;
	}
	
	protected int[] getDownScenarioOrder(int t, FlexibleLoadProblem p) {
		Market market = p.getMarket();
		return market.hasCapacityPayments() ?
				market.getScenariosOrderedByDownCapacityPayment(t):
				market.getScenariosOrderedByDownRegulatingPrice(t);
	}
	
	protected int[] getUpScenarioOrder(int t, FlexibleLoadProblem p) {
		Market market = p.getMarket();
		return market.hasCapacityPayments() ?
				market.getScenariosOrderedByUpCapacityPayment(t):
				market.getScenariosOrderedByUpRegulatingPrice(t);
	}
	
	public void printResultsToFile(String info) throws IOException {

		new File(info).mkdirs();
		CSVWriter.writeCsvFile(info+"/gap.csv", gap, new String[] {"Iteration"});
		CSVWriter.writeCsvFile(info+"/UPobjective.csv", upobject, new String[] {"Iteration"});
		CSVWriter.writeCsvFile(info+"/LOWobjective.csv", lowobject, new String[] {"Iteration"});

	}
}

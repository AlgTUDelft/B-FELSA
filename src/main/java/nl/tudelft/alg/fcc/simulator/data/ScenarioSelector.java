package nl.tudelft.alg.fcc.simulator.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.Constraint;
import nl.tudelft.alg.MipSolverCore.ISolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.utils.Utils;


public class ScenarioSelector extends MIP {
	PriceScenarioData data; // the problem that is solved by this model
	Variable[] x; // the selected scenarios
	Variable[][] e,f; // the errors on average and standard deviation
	int totalScenarios, nScenarios, nTimeSteps;
	static enum TYPE {
		CAPUPPOS,CAPUPNEG,
		CAPDOWNPOS, CAPDOWNNEG,
		IMBUPPOS, IMBUPNEG,
		IMBDOWNPOS, IMBDOWNNEG
	};
	final static int typecount = TYPE.values().length;
	ScenarioSelection selection;
	
	
	public ScenarioSelector(PriceScenarioData data, int nScenarios) {
		super();
		this.data = data;
		this.nScenarios = nScenarios;
		this.totalScenarios = data.getNScenarios();
		this.nTimeSteps = data.getNTimeSteps();
		this.selection = new ScenarioSelection();
		initiliazeVars();
		setVars();
		setObjectiveFunction();
		setConstraints();
	}


	@Override
	protected void setConstraints() {
		double[] stdcapdown = new double[nTimeSteps];
		double[] stdcapup = new double[nTimeSteps];
		double[] stdimbdown = new double[nTimeSteps];
		double[] stdimbup = new double[nTimeSteps];
		for(int t=0; t<nTimeSteps; t++) {
			for(int i=0; i<totalScenarios; i++) {
				stdcapdown[t] += Math.pow(data.getCapDownPrice(t, i) - data.getExpectedCapDownPrice(t), 2);
				stdcapup[t] += Math.pow(data.getCapUpPrice(t, i) - data.getExpectedCapUpPrice(t), 2);
				stdimbdown[t] += Math.pow(data.getDownPrice(t, i) - data.getExpectedDownPrice(t), 2);
				stdimbup[t] += Math.pow(data.getUpPrice(t, i) - data.getExpectedUpPrice(t), 2);
			}
			stdcapdown[t] = stdcapdown[t] / (totalScenarios-1);
			stdcapup[t] = stdcapup[t] / (totalScenarios-1);
			stdimbdown[t] = stdimbdown[t] / (totalScenarios-1);
			stdimbup[t] = stdimbup[t] / (totalScenarios-1);
		}
		
		
		LinExp left, right;
		for(int t=0; t<nTimeSteps; t++) {
			///////////////////////////////////////
			//           AVERAGE                 //
			///////////////////////////////////////
			
			//Minimizing the error in the capacity down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getCapDownPrice(t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.CAPDOWNPOS.ordinal()][t], -1.0, e[TYPE.CAPDOWNNEG.ordinal()][t], data.getExpectedCapDownPrice(t));
			constraints.add(new Constraint(left, right, CMP.EQ, "ACDP"+t));
			//Minimizing the error in the capacity up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getCapUpPrice(t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.CAPUPPOS.ordinal()][t], -1.0, e[TYPE.CAPUPNEG.ordinal()][t], data.getExpectedCapUpPrice(t));
			constraints.add(new Constraint(left, right, CMP.EQ, "ACUP"+t));
			
			//Minimizing the error in the imbalance down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getDownPrice(t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.IMBDOWNPOS.ordinal()][t], -1.0, e[TYPE.IMBDOWNNEG.ordinal()][t], data.getExpectedDownPrice(t));
			constraints.add(new Constraint(left, right, CMP.EQ, "AIDP"+t));
			//Minimizing the error in the imbalance up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getUpPrice(t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.IMBUPPOS.ordinal()][t], -1.0, e[TYPE.IMBUPNEG.ordinal()][t], data.getExpectedUpPrice(t));
			constraints.add(new Constraint(left, right, CMP.EQ, "AIUP"+t));
			
			///////////////////////////////////////
			//           DEVIATION               //
			///////////////////////////////////////
			
			//Minimizing the error in the capacity down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], Math.pow(data.getCapDownPrice(t, i) - data.getExpectedCapDownPrice(t),2) / (nScenarios-1));
			}
			right = new LinExp(f[TYPE.CAPDOWNPOS.ordinal()][t], -1.0, f[TYPE.CAPDOWNNEG.ordinal()][t], stdcapdown[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DCDP"+t));
			//Minimizing the error in the capacity up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], Math.pow(data.getCapUpPrice(t, i) - data.getExpectedCapUpPrice(t),2) / (nScenarios-1));
			}
			right = new LinExp(f[TYPE.CAPUPPOS.ordinal()][t], -1.0, f[TYPE.CAPUPNEG.ordinal()][t], stdcapup[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DCUP"+t));
			
			//Minimizing the error in the imbalance down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], Math.pow(data.getDownPrice(t, i) - data.getExpectedDownPrice(t),2) / (nScenarios-1));
			}
			right = new LinExp(f[TYPE.IMBDOWNPOS.ordinal()][t], -1.0, f[TYPE.IMBDOWNNEG.ordinal()][t], stdimbdown[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DIDP"+t));
			//Minimizing the error in the imbalance up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], Math.pow(data.getUpPrice(t, i) - data.getExpectedUpPrice(t),2) / (nScenarios-1));
			}
			right = new LinExp(f[TYPE.IMBUPPOS.ordinal()][t], -1.0, f[TYPE.IMBUPNEG.ordinal()][t], stdimbup[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DIUP"+t));
			
		}
		left = new LinExp();
		for(int i=0; i<totalScenarios; i++) {
			left.addTerm(x[i]);
		}
		right = new LinExp(nScenarios);
		constraints.add(new Constraint(left, right, CMP.EQ, "NSC"));
	}


	@Override
	protected void setObjectiveFunction() {
		objectiveFunction = new LinExp();
		for(int t=0; t<nTimeSteps; t++) {
			for(TYPE type: TYPE.values()) {
				objectiveFunction.addTerm(e[type.ordinal()][t]);
				objectiveFunction.addTerm(f[type.ordinal()][t]);
			}
		}
	}


	@Override
	protected void setVars() {
		vars = new ArrayList<Variable>();
		vars.addAll(Arrays.asList(x));		
		for(TYPE type: TYPE.values()) {
			vars.addAll(Arrays.asList(e[type.ordinal()]));
			vars.addAll(Arrays.asList(f[type.ordinal()]));
		}
	}


	@Override
	protected void initiliazeVars() {
		x = (Variable[]) newVarArray("x", VarType.Binary, data.getNScenarios());
		e = new Variable[typecount][nTimeSteps];
		f = new Variable[typecount][nTimeSteps];
		for(TYPE type: TYPE.values()) {
			for(int t=0; t<nTimeSteps; t++) {
				e[type.ordinal()][t] = new Variable("e_"+type+"_"+t, VarType.PositiveContinuous);
				f[type.ordinal()][t] = new Variable("f_"+type+"_"+t, VarType.PositiveContinuous);
			}
		}
	}

	@Override
	public void printSolution() {}


	@Override
	public void writeSolution() throws SolverException {
		List<Integer> selected = new ArrayList<Integer>(nScenarios);
		for(int i=0; i<totalScenarios; i++) {
			if(x[i].getSolution() > 0.8) selected.add(i+1);
		}
		double[][] e = new double[typecount][nTimeSteps];
		double[][] f = new double[typecount][nTimeSteps];
		for(TYPE type: TYPE.values()) {
			for(int t=0; t<nTimeSteps; t++) {
				e[type.ordinal()][t] = this.e[type.ordinal()][t].getSolution();
				f[type.ordinal()][t] = this.f[type.ordinal()][t].getSolution();
			}
		}
		selection.scenarios = selected.stream().mapToInt(i->i).toArray();
		selection.e = e;
		selection.f = f;
	}
	
	public static PriceScenarioData getOptimalScenarios(ISolver solver, PriceScenarioData data, int nScenarios) {
		ScenarioSelector selector = new ScenarioSelector(data, nScenarios);
		try {
			solver.build(selector);
			solver.solve();
		} catch (SolverException e) {
			System.out.println("Error in selecting scenarios. Defaulting to random scenarios");
			return data.getRandomSubset(nScenarios, Utils.random);
		}
		
		return data.filter(selector.selection.scenarios);
	}
	
	

	public class ScenarioSelection {
		public int[] scenarios;
		public double[][] e,f;
		
		public String toString() {
			return Arrays.toString(scenarios) + "\n" + Arrays.deepToString(e) + "\n" + Arrays.deepToString(f);
		}
	}

}


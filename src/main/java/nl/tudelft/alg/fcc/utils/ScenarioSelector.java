package nl.tudelft.alg.fcc.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.Constraint;
import nl.tudelft.alg.MipSolverCore.IMIPSolver;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.MIP;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.PriceScenarioData;

public class ScenarioSelector extends MIP {
	public static Map<Integer, Map<Integer, ScenarioSelection>> cache = new HashMap<>();
	PriceScenarioData data; // the problem that is solved by this model
	Variable[] x; // the selected scenarios
	Variable[][] e,f; // the errors on average and standard deviation
	int totalScenarios, nScenarios, startT, nTimeSteps;
	static enum TYPE {
		CAPUPPOS,CAPUPNEG,
		CAPDOWNPOS, CAPDOWNNEG,
		IMBUPPOS, IMBUPNEG,
		IMBDOWNPOS, IMBDOWNNEG
	};
	final static int typecount = TYPE.values().length;
	ScenarioSelection selection;
	
	
	public ScenarioSelector(PriceScenarioData data, int nScenarios, ScenarioSelection selection, int startT, int endT) {
		super();
		this.data = data;
		this.nScenarios = nScenarios;
		this.totalScenarios = data.getNScenarios();
		this.startT = startT;
		this.nTimeSteps = endT - startT;
		this.selection = selection;
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
				stdcapdown[t] += Math.pow(data.getCapDownPrice(startT + t, i) -
						data.getExpectedCapDownPrice(startT + t), 2);
				stdcapup[t] += Math.pow(data.getCapUpPrice(startT + t, i) - data.getExpectedCapUpPrice(startT + t), 2);
				stdimbdown[t] += Math.pow(data.getDownPrice(startT + t, i) - data.getExpectedDownPrice(startT + t), 2);
				stdimbup[t] += Math.pow(data.getUpPrice(startT + t, i) - data.getExpectedUpPrice(startT + t), 2);
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
				left.addTerm(x[i], data.getCapDownPrice(startT + t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.CAPDOWNPOS.ordinal()][t], -1.0, e[TYPE.CAPDOWNNEG.ordinal()][t],
					data.getExpectedCapDownPrice(startT + t));
			constraints.add(new Constraint(left, right, CMP.EQ, "ACDP"+t));
			//Minimizing the error in the capacity up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getCapUpPrice(startT + t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.CAPUPPOS.ordinal()][t], -1.0, e[TYPE.CAPUPNEG.ordinal()][t],
					data.getExpectedCapUpPrice(startT + t));
			constraints.add(new Constraint(left, right, CMP.EQ, "ACUP"+t));
			
			//Minimizing the error in the imbalance down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getDownPrice(startT + t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.IMBDOWNPOS.ordinal()][t], -1.0, e[TYPE.IMBDOWNNEG.ordinal()][t],
					data.getExpectedDownPrice(startT + t));
			constraints.add(new Constraint(left, right, CMP.EQ, "AIDP"+t));
			//Minimizing the error in the imbalance up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], data.getUpPrice(startT + t, i) / nScenarios);
			}
			right = new LinExp(e[TYPE.IMBUPPOS.ordinal()][t], -1.0, e[TYPE.IMBUPNEG.ordinal()][t],
					data.getExpectedUpPrice(startT + t));
			constraints.add(new Constraint(left, right, CMP.EQ, "AIUP"+t));
			
			///////////////////////////////////////
			//           DEVIATION               //
			///////////////////////////////////////
			
			//Minimizing the error in the capacity down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i],
						Math.pow(data.getCapDownPrice(startT + t, i) - data.getExpectedCapDownPrice(startT + t), 2)
								/ (nScenarios - 1));
			}
			right = new LinExp(f[TYPE.CAPDOWNPOS.ordinal()][t], -1.0, f[TYPE.CAPDOWNNEG.ordinal()][t], stdcapdown[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DCDP"+t));
			//Minimizing the error in the capacity up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], Math.pow(data.getCapUpPrice(startT + t, i) - data.getExpectedCapUpPrice(startT + t), 2)
						/ (nScenarios - 1));
			}
			right = new LinExp(f[TYPE.CAPUPPOS.ordinal()][t], -1.0, f[TYPE.CAPUPNEG.ordinal()][t], stdcapup[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DCUP"+t));
			
			//Minimizing the error in the imbalance down price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i], Math.pow(data.getDownPrice(startT + t, i) - data.getExpectedDownPrice(startT + t), 2)
						/ (nScenarios - 1));
			}
			right = new LinExp(f[TYPE.IMBDOWNPOS.ordinal()][t], -1.0, f[TYPE.IMBDOWNNEG.ordinal()][t], stdimbdown[t]);
			constraints.add(new Constraint(left, right, CMP.EQ, "DIDP"+t));
			//Minimizing the error in the imbalance up price
			left = new LinExp();
			for(int i=0; i<totalScenarios; i++) {
				left.addTerm(x[i],
						Math.pow(data.getUpPrice(startT + t, i) - data.getExpectedUpPrice(startT + t), 2) / (nScenarios - 1));
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
		x = new Variable[data.getNScenarios()];
		for(int i=0;i<data.getNScenarios();i++)
			x[i] = new Variable("x"+i, VarType.Binary);
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
	public void writeSolution(IMIPSolver solver) {
		List<Integer> selected = new ArrayList<Integer>(nScenarios);
		for(int i=0; i<totalScenarios; i++) {
			if (x[i].getSolution() > 0.8) selected.add(i);
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

		if (!cache.containsKey(data.getNScenarios()))
			cache.put(data.getNScenarios(), new HashMap<Integer, ScenarioSelection>());
		cache.get(data.getNScenarios()).put(nScenarios, selection);
	}

	public static void resetCache() {
		cache = new HashMap<>();
	}


	@Override
	public void printSolution() {}
}

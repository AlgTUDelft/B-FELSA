package nl.tudelft.alg.fcc.solution.mip;

import nl.tudelft.alg.MipSolverCore.CMP;
import nl.tudelft.alg.MipSolverCore.LinExp;
import nl.tudelft.alg.MipSolverCore.SolverException;
import nl.tudelft.alg.MipSolverCore.VarType;
import nl.tudelft.alg.MipSolverCore.Variable;
import nl.tudelft.alg.fcc.model.Line;
import nl.tudelft.alg.fcc.model.Loads;
import nl.tudelft.alg.fcc.model.Market;
import nl.tudelft.alg.fcc.problem.DecisionVariables;
import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;

/**
 * MIP model for solving the FlexibleLoadProblem based on piecewise linear approximations
 * of the acceptance probability and expected return of reserves
 */
public class LinApproxModel extends FlexibleLoadMIP {
	Variable[][] d, // Charging (binary), per PTU 
				ru, // up reserves (binary), per PTU
				rd, // down reserves (binary), per PTU
				xu, // reserves up contribution to SOC, per PTU
				xd, // reserves down contribution to SOC, per PTU
				zu, // reserves up contribution to objective, per PTU
				zd; // reserves down contribution to objective, per PTU
	Variable[][] bu,  // up price bid, per PTU
				bd, // down price bid, per PTU
				uu, // up acceptance probability, per PTU
				ud, // down acceptance probability, per PTU
				hu, // up expected return
				hd; // down expected return
	Variable[][][] uuq, udq, huq, hdq, // binary variables for selecting piecewise linear subpiece
					uuz, udz, huz, hdz,  // continuous variable for multiplication of price and binary [piecewise linear]
					bud, buu, bhd, bhu;
	Variable[][][] lambda_apd, lambda_apu, lambda_erd, lambda_eru;
	
	public LinApproxModel(FlexibleLoadProblem p) {
		super(p);
	}

	@Override
	protected void setConstraints() {
		super.setConstraints();
		setLineConstraints();
		setBigMConstraints();
	}
	
	protected void addBinConMult(Variable bin, Variable con, Variable res, double low, double high, boolean rfb, boolean rft) {
		String name = "M"+res.getName();
		LinExp middle = new LinExp(res);
		LinExp left = new LinExp(low, bin);
		LinExp right = new LinExp(high, bin);
		if(rfb) addConstraint(left, middle, CMP.SMALLEREQ, name + "~1");
		if(rft) addConstraint(middle, right, CMP.SMALLEREQ, name + "~2");
		middle = new LinExp(res);
		left = new LinExp(con, high, bin, -high);
		right = new LinExp(con, low, bin, -low);
		addConstraint(left, middle, CMP.SMALLEREQ, name + "~3");
		addConstraint(middle, right, CMP.SMALLEREQ, name + "~4");
	}
	
	protected void setBigMConstraints() {
		double min = problem.getMarket().hasCapacityPayments() ? 0 : -500;
		double max = problem.getMarket().hasCapacityPayments() ? 20 : 500;
		boolean cap = problem.getMarket().hasCapacityPayments();
		for(int e=0; e < nLoads; e++) {
			for(int t=0; t<nTimeSteps; t++) {
				addBinConMult(ru[e][t], uu[e][t], xu[e][t], 0, 1, true, true);
				addBinConMult(rd[e][t], ud[e][t], xd[e][t], 0, 1, true, true);
				addBinConMult(ru[e][t], hu[e][t], zu[e][t], min, max, false, true);
				addBinConMult(rd[e][t], hd[e][t], zd[e][t], min, max, !cap, cap);
			}
		}
	}

	protected void setLineConstraints() {
		LinExp leftD, rightD, leftU, rightU, zsumD, zsumU, bsumD, bsumU;
		for(int e=0; e<nLoads; e++) {
			for(int t=0; t<nTimeSteps; t++) {
				Line[] apup = problem.getMarket().getAPup(t);
				Line[] apdown = problem.getMarket().getAPdown(t);
				Line[] erup = problem.getMarket().getERup(t);
				Line[] erdown = problem.getMarket().getERdown(t);
				
				leftD = new LinExp(ud[e][t]);
				rightD = new LinExp();
				leftU = new LinExp(uu[e][t]);
				rightU = new LinExp();
				zsumD = new LinExp();
				zsumU = new LinExp();
				bsumD = new LinExp();
				bsumU = new LinExp();
				for(int a=0; a<problem.getMarket().getAPpieces(); a++) {
					rightD.addTerm(udq[e][t][a], apdown[a].b);
					rightD.addTerm(bud[e][t][a], apdown[a].r);
					rightU.addTerm(uuq[e][t][a], apup[a].b);
					rightU.addTerm(buu[e][t][a], apup[a].r);
					zsumD.addTerm(udq[e][t][a]);
					zsumU.addTerm(uuq[e][t][a]);
					bsumD.addTerm(bud[e][t][a]);
					bsumU.addTerm(buu[e][t][a]);
					if(problem.getMarket().getAPpieces() > 1) {
						piecewiseconstraint(bud[e][t][a], udq[e][t][a], apdown[a].lb, apdown[a].rb, "PWpud"+e+"_"+t+"_"+a);
						piecewiseconstraint(buu[e][t][a], uuq[e][t][a], apup[a].lb, apup[a].rb, "PWpuu"+e+"_"+t+"_"+a);
					}
				}
				addConstraint(leftD, rightD, CMP.EQ, "apd"+t);
				addConstraint(leftU, rightU, CMP.EQ, "apu"+t);
				addConstraint(zsumD, new LinExp(1), CMP.EQ, "apdz"+t);
				addConstraint(zsumU, new LinExp(1), CMP.EQ, "apuz"+t);
				addConstraint(bsumD, new LinExp(bd[e][t]), CMP.EQ, "apdb"+t);
				addConstraint(bsumU, new LinExp(bu[e][t]), CMP.EQ, "apub"+t);
				
				leftD = new LinExp(hd[e][t]);
				rightD = new LinExp();
				leftU = new LinExp(hu[e][t]);
				rightU = new LinExp();
				for(int a=0; a<problem.getMarket().getERpieces(); a++) {
					rightD.addTerm(udq[e][t][a], erdown[a].b);
					rightD.addTerm(bud[e][t][a], erdown[a].r);
					rightU.addTerm(uuq[e][t][a], erup[a].b);
					rightU.addTerm(buu[e][t][a], erup[a].r);
					if(problem.getMarket().getERpieces() > 1) {
						piecewiseconstraint(bud[e][t][a], udq[e][t][a], erdown[a].lb, erdown[a].rb, "PWphd"+e+"_"+t+"_"+a);
						piecewiseconstraint(buu[e][t][a], uuq[e][t][a], erup[a].lb, erup[a].rb, "PWphu"+e+"_"+t+"_"+a);
					}
				}
				addConstraint(leftD, rightD, CMP.EQ, "erd"+t);
				addConstraint(leftU, rightU, CMP.EQ, "eru"+t);
				//addConstraint(zsumD, new LinExp(1), CMP.EQ, "erdz"+t));
				//addConstraint(zsumU, new LinExp(1), CMP.EQ, "eruz"+t));
				//addConstraint(bsumD, new LinExp(bd[e][t]), CMP.EQ, "erdb"+t));
				//addConstraint(bsumU, new LinExp(bu[e][t]), CMP.EQ, "erub"+t));
				
			}
		}
		
	}
	
	@Override
	protected void setFirstFixed() {
		super.setFirstFixed();
		if(!problem.getMarket().hasReserves()) {
			fixVariables(0, ru, rd);
		}
		for(int t=0; t<problem.getMarket().getFixedPTUs(); t++) {
			if(t >= problem.getNTimeSteps()) break;
			for(int e=0; e<nLoads; e++) {
				//The provide down and up ward reserves is as committed
				int rubid = problem.getVars().rcu[e][t] > 5e-4 ? 1 : 0;
				int rdbid = problem.getVars().rcd[e][t] > 5e-4 ? 1 : 0;
				if(rubid > 0) fixVariable(bu[e][t], problem.getVars().bu[e][t]);
				if(rdbid > 0) fixVariable(bd[e][t], problem.getVars().bd[e][t]);
				fixVariable(ru[e][t], rubid);
				fixVariable(rd[e][t], rdbid);
			}
		}
	}
	
	protected void piecewiseconstraint(Variable con, Variable bin, double lb, double rb, String name) {
		LinExp left = new LinExp(lb, bin);
		LinExp middle = new LinExp(con);
		LinExp right = new LinExp(rb, bin);
		addConstraint(left, middle, CMP.SMALLEREQ, name+"L");
		addConstraint(middle, right, CMP.SMALLEREQ, name+"R");
	}
	
	@Override
	protected void addResObj() {
		Market market = problem.getMarket();
		for(int e=0; e<nLoads; e++) {
			for(int t=0; t<nTimeSteps; t++) {
				double f = market.getPTU() * getLoads().getChargingSpeed(e,t);
				for(int w=0; w<nScenarios; w++) {
					double prob = market.getScenarioProbability(w);
					double imbdown = getImbDownObjPrice(t);
					double imbup   = getImbUpObjPrice(t);
					double e_down = market.getDownReserveProportion(t, w) ;
					double e_up = market.getUpReserveProportion(t, w);
					if(market.hasCapacityPayments()) {
						objectiveFunction.addTerm(xd[e][t], e_down * imbdown * f * prob);
						objectiveFunction.addTerm(xu[e][t], - e_up * imbup * f * prob);
						objectiveFunction.addTerm(zd[e][t], - f * prob);
						objectiveFunction.addTerm(zu[e][t], - f * prob);
					} else {
						objectiveFunction.addTerm(zd[e][t], e_down * f * prob);
						objectiveFunction.addTerm(zu[e][t], - e_up * f * prob);
					}
				}
			}
		}		
	}
	
	@Override
	protected void setVars() {
		super.setVars();
		addVars(d, ru, rd, xu, xd, zu, zd,
				bu, bd, uu, ud, hu, hd,
				udq, uuq, udz, uuz,
				hdq, huq, hdz, huz,
				bud, buu, bhd, bhu,
				lambda_apd, lambda_apu, lambda_erd, lambda_eru);
	}

	@Override
	protected void initiliazeVars() {
		super.initiliazeVars();
		d = (Variable[][]) newVarArray("d", VarType.Binary, nLoads, nTimeSteps);
		ru = (Variable[][]) newVarArray("ru", VarType.Binary, nLoads, nTimeSteps);
		rd = (Variable[][]) newVarArray("rd", VarType.Binary, nLoads, nTimeSteps);
		xu = (Variable[][]) newVarArray("xu", VarType.PositiveContinuous, nLoads, nTimeSteps);
		xd = (Variable[][]) newVarArray("xd", VarType.PositiveContinuous, nLoads, nTimeSteps);
		zu = (Variable[][]) newVarArray("zu", VarType.Real, nLoads, nTimeSteps);
		zd = (Variable[][]) newVarArray("zd", VarType.Real, nLoads, nTimeSteps);
		VarType btype = VarType.Real;
		if(problem.getMarket().hasCapacityPayments())
			btype = VarType.PositiveContinuous;
		bu =  (Variable[][]) newVarArray("bu", btype, nLoads, nTimeSteps);
		bd =  (Variable[][]) newVarArray("bd", btype, nLoads, nTimeSteps);
		uu =  (Variable[][]) newVarArray("uu", VarType.BinaryContinuous, nLoads, nTimeSteps);
		ud =  (Variable[][]) newVarArray("ud", VarType.BinaryContinuous, nLoads, nTimeSteps);
		hu =  (Variable[][]) newVarArray("hu", VarType.Real, nLoads, nTimeSteps);
		hd =  (Variable[][]) newVarArray("hd", VarType.Real, nLoads, nTimeSteps);
		uuq = (Variable[][][]) newVarArray("uuq", VarType.Binary, nLoads, nTimeSteps, problem.getMarket().getAPpieces());
		udq = (Variable[][][]) newVarArray("udq", VarType.Binary, nLoads, nTimeSteps, problem.getMarket().getAPpieces());
		huq = (Variable[][][]) newVarArray("huq", VarType.Binary, nLoads, nTimeSteps, problem.getMarket().getERpieces());
		hdq = (Variable[][][]) newVarArray("hdq", VarType.Binary, nLoads, nTimeSteps, problem.getMarket().getERpieces());
		uuz = (Variable[][][]) newVarArray("uuz", VarType.BinaryContinuous, nLoads, nTimeSteps, problem.getMarket().getAPpieces());
		udz = (Variable[][][]) newVarArray("udz", VarType.BinaryContinuous, nLoads, nTimeSteps, problem.getMarket().getAPpieces());
		huz = (Variable[][][]) newVarArray("huz", VarType.Real, nLoads, nTimeSteps, problem.getMarket().getERpieces());
		hdz = (Variable[][][]) newVarArray("hdz", VarType.Real, nLoads, nTimeSteps, problem.getMarket().getERpieces());
		buu =  (Variable[][][]) newVarArray("buu", btype, nLoads, nTimeSteps, problem.getMarket().getAPpieces());
		bud =  (Variable[][][]) newVarArray("bud", btype, nLoads, nTimeSteps, problem.getMarket().getAPpieces());
		bhu =  (Variable[][][]) newVarArray("bhu", btype, nLoads, nTimeSteps, problem.getMarket().getERpieces());
		bhd =  (Variable[][][]) newVarArray("bhd", btype, nLoads, nTimeSteps, problem.getMarket().getERpieces());
		lambda_apd =  (Variable[][][]) newVarArray("l_apd", VarType.PositiveContinuous, nLoads, nTimeSteps, problem.getMarket().getAPpieces()+1);
		lambda_apu =  (Variable[][][]) newVarArray("l_apu", VarType.PositiveContinuous, nLoads, nTimeSteps, problem.getMarket().getAPpieces()+1);
		lambda_erd =  (Variable[][][]) newVarArray("l_erd", VarType.PositiveContinuous, nLoads, nTimeSteps, problem.getMarket().getERpieces()+1);
		lambda_eru =  (Variable[][][]) newVarArray("l_eru", VarType.PositiveContinuous, nLoads, nTimeSteps, problem.getMarket().getERpieces()+1);
	}

	//Write the model solution back to the problem instance
	@Override
	public void writeSolution() throws SolverException {
		super.writeSolution();
		Loads loads = getLoads();
		DecisionVariables d = problem.getVars();
		for(int i=0; i < nLoads; i++) {
			for(int t=0; t < nTimeSteps; t++) {
				double max = Math.min(loads.getChargingSpeed(i,t),
						(loads.getBatteryCapacity(i) - loads.getArrivalSOC(i))/problem.getMarket().getPTU()); //TODO subtract current SOC instead of arrival SOC
				d.p[i][t] = this.d[i][t].getSolution() * max;
						
				d.rcd[i][t] = rd[i][t].getSolution() * max;
				d.rcu[i][t] = ru[i][t].getSolution() * max;
				if(t>=problem.getMarket().getFixedPTUs()) {
					d.bd[i][t] = bd[i][t].getSolution();
					d.bu[i][t] = bu[i][t].getSolution();
				}
			}
		}		
		//printSolution();
	}
	
	private static String neatDoubleIf(double d) {
		return Math.abs(d) > 1e-4 ? String.format("%.4f", d) : "      ";
	}
	
	@Override
	public void printSolution() {
		System.out.print("t\t");
		for(int i=0; i<nLoads; i++) { 
			System.out.print("p"+i+"\trd"+i+"\tru"+i+"\t");
			System.out.print("bd"+i+"\tbu"+i+"\t");
		}
		System.out.println("l");
		for(int t=0; t<nTimeSteps; t++) {
			System.out.print(t+"\t");
			for(int i=0; i< nLoads; i++) {
				boolean down = false;
				boolean up = false;
				double P = getLoads().getChargingSpeed(i,t);
				System.out.print(neatDoubleIf(this.d[i][t].getSolution() * P) +"\t");
				System.out.print(neatDoubleIf(this.rd[i][t].getSolution() * P)+"\t");
				System.out.print(neatDoubleIf(this.ru[i][t].getSolution() * P)+"\t");
				if(this.rd[i][t].getSolution()>0.8) down = true;
				if(this.ru[i][t].getSolution()>0.8) up = true;
				if(down)
					System.out.format("%.2f\t",this.bd[i][t].getSolution());
				else System.out.print("\t");
				if(up)
					System.out.format("%.2f\t", this.bu[i][t].getSolution());
				else System.out.print("\t");
			}
			System.out.format("%.2f", problem.getMarket().getExpectedImbalancePrice(t));
			System.out.println();
		}
	}

	@Override
	protected LinExp getPc(int e, int t) {
		return new LinExp(getLoads().getChargingSpeed(e,t), d[e][t]);
	}
	@Override
	protected LinExp getPd(int e, int t) {
		// TODO implement V2G
		return new LinExp();
	}
	@Override
	protected LinExp getRcd(int e, int t, int i) {
		return new LinExp(getLoads().getChargingSpeed(e,t), rd[e][t]);
	}
	@Override
	protected LinExp getRcu(int e, int t, int i) {
		return new LinExp(getLoads().getChargingSpeed(e,t), ru[e][t]);
	}
	@Override
	protected LinExp getRdd(int e, int t, int i) {
		// TODO implement V2G
		return new LinExp();
	}
	@Override
	protected LinExp getRdu(int e, int t, int i) {
		// TODO implement V2G
		return new LinExp();
	}
	
	@Override
	public LinExp getERcu(int e, int t, int i) {
		return new LinExp(xu[e][t])
			.multiplyBy(getMarket().getPTU() * getLoads().getChargingSpeed(e,t));
	}
	@Override
	public LinExp getERcd(int e, int t, int i) {
		return new LinExp(xd[e][t])
			.multiplyBy(getMarket().getPTU() * getLoads().getChargingSpeed(e,t));
	}
	@Override
	public LinExp getERdu(int e, int t, int i) {
		// TODO implement V2G
		return new LinExp();
	}
	@Override
	public LinExp getERdd(int e, int t, int i) {
		// TODO implement V2G
		return new LinExp();
	}

	@Override
	public boolean isSolvable() {
		try {
			problem.getMarket().getPricedata().getERpieces();
			problem.getMarket().getPricedata().getAPpieces();
		} catch (Exception e) {
			return false;
		}
		return true;
	}

}
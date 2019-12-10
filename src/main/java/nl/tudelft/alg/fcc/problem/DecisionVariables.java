package nl.tudelft.alg.fcc.problem;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.utils.CSVWriter;
import nl.tudelft.alg.fcc.utils.Utils;

public class DecisionVariables {
	public double[][] p, dp, rcd, rcu, bd, bu, rdd, rdu;
	public double[] pda, pimb;
	public int nLoads, nTimesteps, startT;
	double ptu;
	Map<String, ExtraVar> extraVars;
	boolean storeExtraVars;
	
	public DecisionVariables(FlexibleLoadProblem p) {
		this(p.getNLoads(), p.getNTimeSteps(), p.getMarket().getNumberOfHours(), p.getMarket().getStartT(), p.getMarket().getPTU());
		this.storeExtraVars = p.getConfig().getFileOutput() > 2;
	}
	
	public DecisionVariables(int nLoads, int nTimesteps, int nHours, int startT, double ptu) {
		this.nLoads = nLoads;
		this.nTimesteps = nTimesteps;
		this.startT = startT;
		this.ptu = ptu;
		this.bd = new double[nLoads][nTimesteps];	//price bid down
		this.bu = new double[nLoads][nTimesteps];	//price bid up
		this.pimb = new double[nTimesteps];			//imbalance purchase
		this.p = new double[nLoads][nTimesteps];	//charging
		this.rcd = new double[nLoads][nTimesteps];	//charging reserves down
		this.rcu = new double[nLoads][nTimesteps];	//charging reserves up
		this.rdd = new double[nLoads][nTimesteps];	//discharging reserves down
		this.rdu = new double[nLoads][nTimesteps];	//discharging reserves up
		this.pda = new double[nHours]; //da purchase
		this.dp = new double[nLoads][nTimesteps];	//discharging
		extraVars = new HashMap<String, ExtraVar>();
		storeExtraVars = true;
	}
	
	/**
	 * Copy the first PTUs and first hours from an other DecisionVariable object
	 */
	public void copyFirstFrom(DecisionVariables other, int firstPTUs, int firstHours) {
		firstPTUs = Math.min(firstPTUs, nTimesteps);
		firstHours = Math.min(firstHours, pda.length);
		for (int t = 0; t < firstPTUs; t++) {
			for (int e = 0; e < nLoads; e++) {
				this.bd[e][t] = other.bd[e][t];
				this.bu[e][t] = other.bu[e][t];
				this.p[e][t] = other.p[e][t];
				this.dp[e][t] = other.dp[e][t];
				this.rcd[e][t] = other.rcd[e][t];
				this.rcu[e][t] = other.rcu[e][t];
				this.rdd[e][t] = other.rdd[e][t];
				this.rdu[e][t] = other.rdu[e][t];
			}
			this.pimb[t] = other.pimb[t];
		}
		for (int h = 0; h < firstHours; h++) {
			this.pda[h] = other.pda[h];
		}
	}

	public int PTUtoH(int t) {
		return (int) (Math.floor((t + startT) * ptu) - Math.floor(startT * ptu));
	}

	public int HtoPTU(int h) {
		return (int) Math.floor(h / ptu);
	}

	/**
	 * Determine the imbalance purchase decisions from the DA purchase and charging decisions
	 */
	public void imbalancePurchaseFromCharging() {
		double[][] ps = new double[nLoads][nTimesteps];
		for(int e=0; e<nLoads; e++) {
			for(int t=0; t<nTimesteps; t++) {
				ps[e][t] = p[e][t] - dp[e][t];
			}
		}
		pimb = Utils.columnSum(ps);
		pimb = IntStream.range(0, nTimesteps).mapToDouble(t -> pimb[t] - pda[PTUtoH(t)]).toArray();
	}
	
	public void printResultsToFile(String folder) throws IOException {
		CSVWriter.writeCsvFile(folder+"/pc.csv", p, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/pd.csv", dp, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/rcd_bid.csv", rcd, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/rcu_bid.csv", rcu, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/rdd_bid.csv", rdd, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/rdu_bid.csv", rdu, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/pd_bid.csv", bd, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/pu_bid.csv", bu, new String[] {"L", "PTU"});
		CSVWriter.writeCsvFile(folder+"/pimb.csv", pimb, new String[] {"PTU"});
		CSVWriter.writeCsvFile(folder+"/pda.csv", pda, new String[] {"PTU"});
		
		for (Map.Entry<String,ExtraVar> entry : extraVars.entrySet()) {
			String key = entry.getKey();
			ExtraVar var = entry.getValue();
			CSVWriter.writeCsvFile(folder+"/"+key+".csv", var.object, var.dims);
		}
	}
	
	public static void printResultsToFile(DecisionVariables[] decs, String folder) throws IOException {
		if (decs.length == 0) return;
		CSVWriter.writeCsvFile(folder + "/pc.csv", Arrays.stream(decs).map(d -> d.p).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/pd.csv", Arrays.stream(decs).map(d -> d.dp).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/rcd_bid.csv", Arrays.stream(decs).map(d -> d.rcd).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/rcu_bid.csv", Arrays.stream(decs).map(d -> d.rcu).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/rdd_bid.csv", Arrays.stream(decs).map(d -> d.rdd).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/rdu_bid.csv", Arrays.stream(decs).map(d -> d.rdu).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/pd_bid.csv", Arrays.stream(decs).map(d -> d.bd).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/pu_bid.csv", Arrays.stream(decs).map(d -> d.bu).toArray(),
				new String[] { "S", "L", "PTU" });
		CSVWriter.writeCsvFile(folder + "/pimb.csv", Arrays.stream(decs).map(d -> d.pimb).toArray(),
				new String[] { "S", "PTU" });
		CSVWriter.writeCsvFile(folder + "/pda.csv", Arrays.stream(decs).map(d -> d.pda).toArray(),
				new String[] { "S", "PTU" });
		for (String key : decs[0].extraVars.keySet()) {
			String[] dims = decs[0].extraVars.get(key).dims;
			String[] newDims = new String[dims.length + 1];
			newDims[0] = "S";
			System.arraycopy(dims, 0, newDims, 1, dims.length);
			Object o = Arrays.stream(decs).map(d -> d.extraVars.get(key).object).toArray();
			CSVWriter.writeCsvFile(folder + "/" + key + ".csv", o, newDims);
		}
	}

	public void addExtraVars(String name, Object o, String[] dims) {
		if(storeExtraVars)
			extraVars.put(name,  new ExtraVar(o, dims));
	}
	
	public Object getExtraVars(String name) {
		return extraVars.get(name).object;
	}
	
	public void assertEqualSolution(DecisionVariables other) {
		for(int t=0; t<nTimesteps; t++) {
			assert Math.abs(pimb[t] - other.pimb[t]) < 1e-3;
			assert Math.abs(pda[t] - other.pda[t]) < 1e-3;
			for(int i=0; i< nLoads; i++) {
				assert Math.abs(p[i][t] - other.p[i][t]) < 1e-3;
				assert Math.abs(dp[i][t] - other.dp[i][t]) < 1e-3;
				assert Math.abs(rcd[i][t] - other.rcd[i][t]) < 1e-3;
				assert Math.abs(rcu[i][t] - other.rcu[i][t]) < 1e-3;
				assert Math.abs(rdd[i][t] - other.rdd[i][t]) < 1e-3;
				assert Math.abs(rdu[i][t] - other.rdu[i][t]) < 1e-3;
				assert Math.abs(bd[i][t] - other.bd[i][t]) < 1e-3;
				assert Math.abs(bu[i][t] - other.bu[i][t]) < 1e-3;
			}
		}
	}
	
	@Override
	public String toString() {
		String result = Arrays.toString(p) +
				Arrays.toString(rcu) + "\n" +
				Arrays.toString(rdu) + "\n" +
				Arrays.toString(rcd) + "\n" +
				Arrays.toString(rdd) + "\n" +
				Arrays.toString(bu) + "\n" +
				Arrays.toString(bd) + "\n";
		result = result.replace(',', '\n');
		result = result.replace("]", "");
		result = result.replace("[", "");
		return result;
	}
	
	public void printSolution(FlexibleLoadProblem problem) {
		boolean v2g = Utils.sum(Utils.columnSum(dp)) > 1e-3;
		boolean da = Utils.sum(pda) > 1e-3;
		System.out.print("t");
		if(da) System.out.print("\tpda\tpimb");
		System.out.print("\tp");
		if(v2g) System.out.print("\td");
		System.out.print("\tpd\tpu\trd\tru\t");
		System.out.println();
		for(int t=0; t<nTimesteps; t++) {
			final int _t = t;
			System.out.print(t+"\t");
			if(da) {
				int h = problem.getMarket().PTUtoH(t);
				if(Math.abs(this.pda[h]) > 1e-3)
					System.out.format("%.5f\t", this.pda[h]);
				else System.out.print("\t");
				if(Math.abs(this.pimb[t]) > 1e-3)
					System.out.format("%.5f\t", this.pimb[t]);
				else System.out.print("\t");
			}
			double psum = IntStream.range(0, nLoads).mapToDouble(e -> this.p[e][_t]).sum();
			if(psum > 1e-3) System.out.format("%.5f\t", psum);
			else System.out.print("\t");
			if(v2g) {
				psum = IntStream.range(0, nLoads).mapToDouble(e -> this.dp[e][_t]).sum();
				if(psum > 1e-3) System.out.format("%.5f\t", psum);
				else System.out.print("\t");
			}
			double rd = IntStream.range(0, nLoads).mapToDouble(e -> rcd[e][_t] + rdd[e][_t]).sum();
			double ru = IntStream.range(0, nLoads).mapToDouble(e -> rcu[e][_t] + rdu[e][_t]).sum();
			double pd = true ?//TODO problem.getMarket().hasCapacityPayments() ? 
					IntStream.range(0, nLoads).mapToDouble(e -> bd[e][_t]).min().orElse(0) :
					IntStream.range(0, nLoads).mapToDouble(e -> bd[e][_t]).max().orElse(0);
			double pu = IntStream.range(0, nLoads).mapToDouble(e -> bu[e][_t]).min().orElse(0);
			if(rd > 1e-3) System.out.format("%.2f\t", pd);
			else System.out.print("\t");
			if(ru > 1e-3) System.out.format("%.2f\t", pu);
			else System.out.print("\t");
			if(rd > 1e-3) System.out.format("%.5f\t", rd);
			else System.out.print("\t");
			if(ru > 1e-3) System.out.format("%.5f\t", ru);
			else System.out.print("\t");
			System.out.println();
		}
		System.out.print("t\t");
		for(int e=0; e<nLoads; e++) {
			System.out.print("p"+e+"\t");
		}
		System.out.println();
		for(int t=0; t<nTimesteps; t++) {
			System.out.print(t+"\t");
			for(int e=0; e<nLoads; e++) {
				if(Math.abs(p[e][t] + dp[e][t]) > 1e-3)
					System.out.format("%.5f\t", p[e][t] + dp[e][t]);
				else
					System.out.print("\t");
			}
			System.out.println();
		}
	}
	
	private class ExtraVar {
		public Object object;
		public String[] dims;
		
		public ExtraVar(Object o, String[] dims) {
			this.object = o;
			this.dims = dims;
		}
	}

	public void assertUnaltered(DecisionVariables other, int fixedPTUs) {
		int hourShift = pda.length - other.pda.length;
		int ptuShift = nTimesteps - other.nTimesteps;
		fixedPTUs = Math.min(fixedPTUs, other.nTimesteps);
		for(int t=0; t<fixedPTUs; t++) {
			for (int i = 0; i < nLoads; i++) {
				assert Math.abs(rcd[i][t + ptuShift] - other.rcd[i][t]) < 1e-3;
				assert Math.abs(rcu[i][t + ptuShift] - other.rcu[i][t]) < 1e-3;
				assert Math.abs(rdd[i][t + ptuShift] - other.rdd[i][t]) < 1e-3;
				assert Math.abs(rdu[i][t + ptuShift] - other.rdu[i][t]) < 1e-3;
				assert Math.abs(bd[i][t + ptuShift] - other.bd[i][t]) < 1e-3;
				assert Math.abs(bu[i][t + ptuShift] - other.bu[i][t]) < 1e-3;
			}
		}
		for (int h = 0; h < other.pda.length; h++) {
			assert Math.abs(pda[h + hourShift] - other.pda[h]) < 1e-3;
		}
	}

	/**
	 * Pad this object with zero's at the start to make it align with a previous DecisionVariables object
	 */
	public DecisionVariables padWithZeros(DecisionVariables prev) {
		DecisionVariables res = new DecisionVariables(prev.nLoads, prev.nTimesteps, prev.pda.length, prev.startT, prev.ptu);
		for(int e=0; e<nLoads; e++) {
			int nTimesteps = this.nTimesteps;
			System.arraycopy(prev.bd[e], 0, res.bd[e], 0, prev.bd[e].length - nTimesteps);
			System.arraycopy(prev.bu[e], 0, res.bu[e], 0, prev.bu[e].length - nTimesteps);
			System.arraycopy(prev.p[e], 0, res.p[e], 0, prev.p[e].length - nTimesteps);
			System.arraycopy(prev.dp[e], 0, res.dp[e], 0, prev.dp[e].length - nTimesteps);
			System.arraycopy(prev.rcd[e], 0, res.rcd[e], 0, prev.rcd[e].length - nTimesteps);
			System.arraycopy(prev.rdd[e], 0, res.rdd[e], 0, prev.rdd[e].length - nTimesteps);
			System.arraycopy(prev.rcu[e], 0, res.rcu[e], 0, prev.rcu[e].length - nTimesteps);
			System.arraycopy(prev.rdu[e], 0, res.rdu[e], 0, prev.rdu[e].length - nTimesteps);

			System.arraycopy(this.bd[e], 0, res.bd[e], prev.bd[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.bu[e], 0, res.bu[e], prev.bu[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.p[e], 0, res.p[e], prev.p[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.dp[e], 0, res.dp[e], prev.dp[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.rcd[e], 0, res.rcd[e], prev.rcd[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.rdd[e], 0, res.rdd[e], prev.rdd[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.rcu[e], 0, res.rcu[e], prev.rcu[e].length - nTimesteps, nTimesteps);
			System.arraycopy(this.rdu[e], 0, res.rdu[e], prev.rdu[e].length - nTimesteps, nTimesteps);
		}
		System.arraycopy(prev.pimb, 0, res.pimb, 0, prev.pimb.length - nTimesteps);
		System.arraycopy(prev.pda, 0, res.pda, 0, prev.pda.length - this.pda.length);

		System.arraycopy(this.pimb, 0, res.pimb, prev.pimb.length - nTimesteps, nTimesteps);
		System.arraycopy(this.pda, 0, res.pda, prev.pda.length - this.pda.length, this.pda.length);
		for (Map.Entry<String, ExtraVar> e : prev.extraVars.entrySet()) {
			res.addExtraVars(e.getKey(), Utils.deepArrayCopy(e.getValue().object), e.getValue().dims);
			updateExtraVars(res.getExtraVars(e.getKey()), this.getExtraVars(e.getKey()));
		}
		return res;
	}

	private static void updateExtraVars(Object prev, Object cur) {
		if (!prev.getClass().isArray() || cur.getClass().isArray())
			return;
		int prevLength = Array.getLength(prev);
		int curLength = Array.getLength(cur);
		if (prevLength == curLength) {
			for (int i = 0; i < prevLength; i++) {
				updateExtraVars(Array.get(prev, i), Array.get(cur, i));
			}
		} else {
			System.arraycopy(cur, 0, prev, prevLength - curLength, curLength);
		}
	}
}

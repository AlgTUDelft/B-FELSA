package nl.tudelft.alg.fcc.problem;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.utils.CSVWriter;
import nl.tudelft.alg.fcc.utils.Utils;

public class Result {
	double[] cost;
	double[] runtime;
	double[] initialSoc;
	double[][] soc;
	double[][][] dsoc;
	double[][] imb;
	double[][] da;
	double[][] rd;
	double[][] ru;
	double[] shortage;
	double[] overflow;
	int nScenarios, nTimeSteps, nLoads;
	
	public Result(double[] initialSoc, int nScenarios, int nTimeSteps, int nHours) {
		super();
		this.nLoads = initialSoc.length;
		this.nScenarios = nScenarios;
		this.nTimeSteps = nTimeSteps;
		this.cost = new double[nScenarios];
		this.runtime = new double[nScenarios];
		this.shortage = new double[nScenarios];
		this.overflow = new double[nScenarios];
		this.initialSoc = Arrays.copyOf(initialSoc, nLoads);
		soc = new double[nLoads][nScenarios];
		for(int e=0; e<nLoads; e++)
			for(int i=0; i<nScenarios; i++)
				this.soc[e][i] = initialSoc[e];
		this.dsoc = new double[nTimeSteps][nLoads][nScenarios];
		this.imb = new double[nTimeSteps][nScenarios];
		this.da = new double[nHours][nScenarios];
		this.rd = new double[nTimeSteps][nScenarios];
		this.ru = new double[nTimeSteps][nScenarios];
	}

	public double getAvgCost() {
		return Utils.avg(cost);
	}

	public double[] getCost() {
		return cost;
	}

	public void setCost(double[] cost) {
		this.cost = cost;
	}
	
	public void setCost(double c) {
		for(int i=0; i<cost.length; i++)
			cost[i] = c;	
	}

	public double[] getAvgSoc() {
		return IntStream.range(0, soc.length).mapToDouble(e -> Utils.avg(soc[e])).toArray();
	}
	
	public double getAvgSocSum() {
		return Utils.sum(getAvgSoc());
	}

	public void incCost(double dCost) {
		for(int i=0; i<cost.length; i++)
			cost[i] += dCost;	
	}
	
	public void incCost(int scenario, double dCost) {
			cost[scenario] += dCost;	
	}
		
	public void incSoc(int t, int vid, double dSoc) {
		for(int i=0; i<soc[vid].length; i++) {
			this.soc[vid][i] += dSoc;
			this.dsoc[t][vid][i] += dSoc;
		}
	}
	
	public void incSoc(int t, int vid, int scenario, double dSoc) {
		this.soc[vid][scenario] += dSoc;
		this.dsoc[t][vid][scenario] += dSoc;
	}
	
	public void incRD(int t, int scenario, double r) {
		this.rd[t][scenario] += r;
	}
	
	public void incRU(int t, int scenario, double r) {
		this.ru[t][scenario] += r;
	}
	
	public void incRD(int t, double r) {
		for(int i=0; i<nScenarios; i++)
			this.rd[t][i] += r;
	}
	
	public void incRU(int t, double r) {
		for(int i=0; i<nScenarios; i++)
			this.ru[t][i] += r;
	}
	

	public double getAvgDSoc() {
		return (Utils.sum(getAvgSoc()) - Utils.sum(initialSoc));
	}

	public double[][] getDSocPerLoad() {
		double[][] dsoc = new double[nLoads][];
		for (int e = 0; e < nLoads; e++) {
			final int _e = e;
			dsoc[e] = IntStream.range(0, nScenarios).mapToDouble(i -> soc[_e][i] - initialSoc[_e]).toArray();
		}
		return dsoc;
	}

	public double[] getDSoc() {
		return IntStream.range(0, nScenarios)
				.mapToDouble(i -> (Arrays.stream(soc).mapToDouble(s -> s[i]).sum() - Utils.sum(initialSoc))).toArray();
	}

	public double[] getReqDSoc() {
		double[] dsoc = getDSoc();
		return IntStream.range(0, nScenarios).mapToDouble(i -> dsoc[i] + shortage[i]).toArray();
	}

	public double getAvgReqDSoc() {
		return Utils.avg(getReqDSoc());
	}

	public double[] getPercShortage() {
		double[] dsoc = getDSoc();
		return IntStream.range(0, nScenarios).mapToDouble(i -> shortage[i] / (dsoc[i] + shortage[i])).toArray();
	}

	public double getAvgPercShortage() {
		return Utils.avg(getPercShortage());
	}

	public double[] getPercOverflow() {
		double[] dsoc = getDSoc();
		return IntStream.range(0, nScenarios).mapToDouble(i -> overflow[i] / (dsoc[i] - overflow[i])).toArray();
	}

	public double getAvgPercOverflow() {
		return Utils.avg(getPercOverflow());
	}

	public double getAvgCostPerMWh() {
		return getAvgCost() / getAvgDSoc();
	}
	
	public double[] getCostPerMWh() {
		double[] dsoc = getDSoc();
		return IntStream.range(0, nScenarios).mapToDouble(i -> cost[i] / dsoc[i]).toArray();
	}
	
	public double getAvgRuntime() {
		return Utils.avg(runtime);
	}

	public void setRuntime(long runtime) {
		for (int i = 0; i < nScenarios; i++)
			this.runtime[i] = runtime / 1e9;
	}
	
	public double[][] getSoc() {
		return this.soc;
	}
	
	public void setShortage(double[] shortage) {
		this.shortage = shortage;		
	}
	
	public void setShortage(double shortage) {
		for (int i = 0; i < nScenarios; i++)
			this.shortage[i] = shortage;
	}

	public double[] getShortage() {
		return this.shortage;
	}

	public double getAvgShortage() {
		return Utils.avg(getShortage());
	}

	public void setOverflow(double[] overflow) {
		this.overflow = overflow;
	}

	public void setOverflow(double overflow) {
		for (int i = 0; i < nScenarios; i++)
			this.overflow[i] = overflow;
	}

	public double[] getOverflow() {
		return this.overflow;
	}

	public double getAvgOverflow() {
		return Utils.avg(getOverflow());
	}

	public double[] getTotalCost(double penalty) {
		return IntStream.range(0, nScenarios).mapToDouble(i -> (cost[i] + shortage[i] * penalty)).toArray();
	}
	
	public double[] getTotalCostPerMWh(double penalty) {
		double[] reqdsoc = getReqDSoc();
		return IntStream.range(0, nScenarios).mapToDouble(i -> (cost[i] + shortage[i] * penalty) / reqdsoc[i]).toArray();
	}

	public double getAvgTotalCostPerMWh(double penalty) {
		return Utils.avg(getTotalCostPerMWh(penalty));
	}

	public String toString(double penalty) {
		double c = getAvgCostPerMWh();
		double p = getAvgPercShortage();
		double o = getAvgPercOverflow();
		double tc = getAvgTotalCostPerMWh(penalty);
		double rt = getAvgRuntime();
		return String.format("%9.4f\t%6.4f\t%6.4f\t%9.4f\t%8.3f", c, p, o, tc, rt);
	}

	public String toString(int i, double penalty) {
		double c = getCostPerMWh()[i];
		double p = getPercShortage()[i];
		double o = getPercOverflow()[i];
		double tc = (getCost()[i] + getShortage()[i] * penalty) / getAvgReqDSoc();
		return String.format("%9.4f\t%6.4f\t%6.4f\t%9.4f", c, p, o, tc);
	}


	public void printResultsToFile(String folder, double penalty) throws IOException {
		CSVWriter.writeCsvFile(folder + "/soc.csv", soc, new String[] { "L", "Scenario" });
		CSVWriter.writeCsvFile(folder + "/dsoc.csv", dsoc, new String[] { "PTU", "L", "Scenario" });
		CSVWriter.writeCsvFile(folder+"/cost.csv", cost, new String[] {"Scenario"});
		CSVWriter.writeCsvFile(folder+"/penalizedcost.csv", getTotalCost(penalty), new String[] {"Scenario"});
		CSVWriter.writeCsvFile(folder+"/costpermwh.csv", getCostPerMWh(), new String[] {"Scenario"});
		CSVWriter.writeCsvFile(folder+"/penalizedcostpermwh.csv", getTotalCostPerMWh(penalty), new String[] {"Scenario"});
		CSVWriter.writeCsvFile(folder+"/rd.csv", rd, new String[] {"PTU", "Scenario"});
		CSVWriter.writeCsvFile(folder+"/ru.csv", ru, new String[] {"PTU", "Scenario"});
		CSVWriter.writeCsvFile(folder + "/runtime.csv", runtime, new String[] {"Scenario"});
		CSVWriter.writeCsvFile(folder + "/shortage.csv", shortage, new String[] { "Scenario" });
		CSVWriter.writeCsvFile(folder + "/overflow.csv", overflow, new String[] { "Scenario" });
	}

	public void setImb(double[] imb) {
		for (int t = 0; t < this.imb.length; t++) {
			for (int i = 0; i < nScenarios; i++) {
				this.imb[t][i] = imb[t];
			}
		}
	}
	
	public void setDA(double[] da) {
		for (int t = 0; t < this.da.length; t++) {
			for (int i = 0; i < nScenarios; i++) {
				this.da[t][i] = da[t];
			}
		}
	}
	
	public double[] getAvgDA() {
		return Arrays.stream(da).mapToDouble(a -> Utils.avg(a)).toArray();
	}

	/**
	 * Combine an array of results (from a rolling horizon simulation) into one result
	 */
	public static Result combine(Result[] results) {
		int nTimeSteps = results.length;
		int nHours = results[0].da.length;
		Result combined = new Result(results[0].initialSoc, results[0].nScenarios, nTimeSteps, nHours);
		int nLoads = combined.nLoads;
		int nScenarios = combined.nScenarios;
		combined.da = results[0].da;
		combined.imb = new double[nTimeSteps][nScenarios];
		combined.cost = new double[nScenarios];
		combined.dsoc = new double[nTimeSteps][nLoads][nScenarios];
		combined.rd = new double[nTimeSteps][nScenarios];
		combined.ru = new double[nTimeSteps][nScenarios];
		int t=0;
		for(Result r: results) {
			for (int i = 0; i < nScenarios; i++) {
				combined.cost[i] += r.cost[i];
				combined.imb[t][i] = r.imb[0][i];
				combined.runtime[i] += r.runtime[i];
			}
			combined.dsoc[t] = r.dsoc[0];
			combined.rd[t] = r.rd[0];
			combined.ru[t] = r.ru[0];
			t++;
		}
		for (int i = 0; i < nScenarios; i++) {
			combined.shortage[i] += results[nTimeSteps - 1].shortage[i];
			combined.overflow[i] += results[nTimeSteps - 1].overflow[i];
		}
		combined.soc = results[nTimeSteps-1].soc;
		return combined;
	}

	/**
	 * Concatenate two result objects on the scenario axis.
	 */
	public void concat(Result other) {
		this.cost = Utils.concatArrays(this.cost, other.cost);
		this.shortage = Utils.concatArrays(this.shortage, other.shortage);
		this.overflow = Utils.concatArrays(this.overflow, other.overflow);
		this.runtime = Utils.concatArrays(this.runtime, other.runtime);
		this.nScenarios += other.nScenarios;
		for (int t = 0; t < nTimeSteps; t++) {
			for (int e = 0; e < nLoads; e++) {
				this.dsoc[t][e] = Utils.concatArrays(this.dsoc[t][e], other.dsoc[t][e]);
			}
			this.rd[t] = Utils.concatArrays(this.rd[t], other.rd[t]);
			this.ru[t] = Utils.concatArrays(this.ru[t], other.ru[t]);
			this.imb[t] = Utils.concatArrays(this.imb[t], other.imb[t]);
		}
		for (int h = 0; h < da.length; h++)
			this.da[h] = Utils.concatArrays(this.da[h], other.da[h]);
		for (int e = 0; e < nLoads; e++) {
			this.soc[e] = Utils.concatArrays(this.soc[e], other.soc[e]);
		}

	}

	private static void boxplotOutput(Config config, Result[] results, PrintStream out) {
		out.print("i\t");
		int n = config.getNumberOfRuns();
		for (int i = 0; i < n; i++) {
			out.print(config.toString(i) + "\t");
		}
		out.println();
		for (int t = 0; t < results[0].nScenarios; t++) {
			out.print(t + "\t");
			for (int i = 0; i < n; i++) {
				out.print(results[i].getTotalCostPerMWh(config.resultShortagePenalty)[t] + "\t");
			}
			out.println();
		}
		out.flush();
	}

	public static void boxplotOutput(String filename, Config config, Result[] results) {
		if (config.verbose >= 1 && config.output.equals("boxplot"))
			boxplotOutput(config, results, System.out);
		boxplotOutputToFile(filename, config, results);
	}
	
	public static void boxplotOutputToFile(String filename, Config config, Result[] results) {
		try {
			File file = new File(filename);
			file.getParentFile().mkdirs();
			PrintStream fileoutput = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)), true);
			boxplotOutput(config, results, fileoutput);
			fileoutput.close();
			fileoutput = null;
		} catch (FileNotFoundException e) {
			System.out.println("Output file " + filename + " could not be created.");
			return;
		}

	}

	/**
	 * Pad a result with zero's to make the indices of two results align
	 * @param prev The previous result with which to align this result
	 * @param resultSoFar The results so far, used to take into account what has already happened
	 */
	public Result padWithZeros(Result prev, Result resultSoFar) {
		Result result = new Result(prev.initialSoc, prev.nScenarios, prev.nTimeSteps, prev.da.length);
		result.shortage = Utils.deepArrayCopy(this.shortage);
		result.overflow = Utils.deepArrayCopy(this.overflow);
		result.soc = Utils.deepArrayCopy(this.soc);
		result.cost = IntStream.range(0, prev.nScenarios).mapToDouble(i -> resultSoFar.cost[i] + this.cost[i]).toArray();
		result.runtime = IntStream.range(0, prev.nScenarios).mapToDouble(i -> resultSoFar.runtime[i] + this.runtime[i]).toArray();
		System.arraycopy(Utils.deepArrayCopy(prev.dsoc), 0, result.dsoc, 0, prev.dsoc.length - this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(prev.imb), 0, result.imb, 0, prev.imb.length - this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(prev.da), 0, result.da, 0, prev.da.length - this.da.length);
		System.arraycopy(Utils.deepArrayCopy(prev.rd), 0, result.rd, 0, prev.rd.length - this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(prev.ru), 0, result.ru, 0, prev.ru.length - this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(this.dsoc), 0, result.dsoc, prev.dsoc.length - this.nTimeSteps, this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(this.imb), 0, result.imb, prev.imb.length - this.nTimeSteps, this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(this.da), 0, result.da, prev.da.length - this.da.length, this.da.length);
		System.arraycopy(Utils.deepArrayCopy(this.rd), 0, result.rd, prev.rd.length - this.nTimeSteps, this.nTimeSteps);
		System.arraycopy(Utils.deepArrayCopy(this.ru), 0, result.ru, prev.ru.length - this.nTimeSteps, this.nTimeSteps);
		return result;
}
	
	
	
}

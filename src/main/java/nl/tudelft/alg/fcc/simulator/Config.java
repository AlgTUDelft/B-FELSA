package nl.tudelft.alg.fcc.simulator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import org.ini4j.Ini;
import org.ini4j.IniPreferences;

import nl.tudelft.alg.fcc.problem.ProblemConfiguration;
import nl.tudelft.alg.fcc.simulator.data.ArimaConfig;
import nl.tudelft.alg.fcc.simulator.data.MarkovConfig;
import nl.tudelft.alg.fcc.utils.Utils;

public class Config {
	static Map<String, Field> varToField;
	static Map<String, Object> defValue;
	final static String[] fileFields = new String[] {
			"outputFolder", "mipLogFile", "mipDebugFile", "evStateFile", "gridFile", "pricedataFolder", "solutionFile" };
	String expFileName;
	Calendar startTime;
	public Calendar startdate;
	List<String> increasingVariable;
	List<Object[]> increasingList;
	public String testVariable;
	Object[] testList;
	
	public int nLoads, nScenarios, maxScenarios, nUClusters, nDClusters, shortagePenalty,
			resultShortagePenalty, firstLoadID, fixedPTUs, ptuLength, relaxedBinaryAfter, randomSeed,
			nTests, startEvaluationScenario, evaluationScenario, nEvaluationScenarios, verbose, fileOutput;
	public String workingDirectory, outputFolder, mipLogFile, mipDebugFile, experimentType, clusterMethod, reservesSettlement, evStateFile, gridFile,
			startdatestring, dateformat, pricedataFolder, model, mipsolver, output,
			generatorType, realizationType, gradient, reservesMarketClearance, solutionFile, modelSetting, markovDownFile, markovUpFile;
	public boolean randomScenarios, V2G, capacityPayment, capacityMarket,
			dayAhead, imbalance, reserves, grid, quantityOnly, perMinute, mipDebug, semiStochastic;
	public double robustness, minBid, desiredProbabilityOfAcceptance,
			mipTimeLimit, mipGap, ptu, batteryDegradation, scenarioFactor;
	public ArimaConfig downConfig, upConfig;
	public MarkovConfig markovDownConfig, markovUpConfig;

	public Config(Preferences config, String filename) throws InvalidConfigurationException, IOException {
		startTime = Calendar.getInstance();
		expFileName = filename;
		for (String key : varToField.keySet())
			setSetting(config, key);
		extraSettingsLogic();

		increasingVariable = new ArrayList<String>();
		increasingList = new ArrayList<Object[]>();
		for(int i=1;;i++) {
			String inc = config.get("increasing" + i, null);
			if(inc == null) break;
			increasingVariable.add(inc);
			increasingList.add(getListFromConfig(config, "list"+i, "start"+i, "end"+i, "step"+i));
		}
		if(testVariable != null) {
			testList = getListFromConfig(config, "test list", "test start", "test end", "test step");
			nTests = Math.max(testList.length, nTests);
		}
	}
		
	Object[] getListFromConfig(Preferences config, String listName, String start, String end, String step) {
		String list = config.get(listName, null);
		if (list != null)
			return list.split(",");
		double low = config.getDouble(start, 1);
		double high = config.getDouble(end, 1);
		double stepsize = config.getDouble(step, 1);
		int n = (int) (stepsize == 0 ? 1 : (high - low) / stepsize + 1);
		return IntStream.range(0, n).mapToDouble(j -> low + j * stepsize).boxed().toArray();
	}
	
	private void updateFromPreferences(Preferences pref) throws InvalidConfigurationException, IOException {
		String[] keys = new String[] {};
		try {
			keys = pref.keys();
		} catch (BackingStoreException e) {}
		for(String key: keys) {
			if(varToField.containsKey(key))
				setSetting(pref, key);
		}
		extraSettingsLogic();
	}

	private void extraSettingsLogic() throws InvalidConfigurationException, IOException {
		if (relaxedBinaryAfter < 0) relaxedBinaryAfter = Integer.MAX_VALUE;
		if (ptuLength > 60 || ptuLength <= 0 || 60 % ptuLength != 0)
			throw new InvalidConfigurationException("PTU length must be less than one hour, at least one minute, and a divisor of 60.");
		ptu = ptuLength / 60.0;
		Utils.setDateFormat(dateformat);
		this.startdate = Utils.stringToCalender(startdatestring);
		String[] split = model.split("\\-");
		model = split[0];
		if (split.length > 1) {
			if (model.equals("IRSC")) nScenarios = Integer.parseInt(split[1]);
			if (model.equals("IRD")) desiredProbabilityOfAcceptance = Double.parseDouble(split[1]);
			if (model.equals("IRD") && split.length > 2) semiStochastic = split[2].equalsIgnoreCase("S");
			modelSetting = String.join("-", Arrays.copyOfRange(split, 1, split.length));
		}
		split = generatorType.split(" ");
		generatorType = split[0];
		if (split.length > 2) {
			gradient = String.join(" ", Arrays.copyOfRange(split, 1, split.length));
		}
		if (generatorType.equalsIgnoreCase("arima") || realizationType.equalsIgnoreCase("arima"))
			loadArimaConfig();
	}

	private void setSetting(Preferences config, String key) {
		Field field = varToField.get(key);
		Object def = defValue.get(key);
		if (field == null) return;
		try {
			if (def instanceof String && ((String) def).startsWith("field/")) {
				String fName = ((String) def).replace("field/", "");
				Field defField = varToField.get(fName);
				def = defField.get(this);
			}
			if (field.getType() == int.class)
				field.setInt(this, (int) Math.floor(config.getDouble(key, ((Number) def).intValue())));
			else if (field.getType() == double.class)
				field.setDouble(this, config.getDouble(key, ((Number) def).doubleValue()));
			else if (field.getType() == String.class)
				field.set(this, config.get(key, (String) def));
			else if (field.getType() == boolean.class)
				field.setBoolean(this, config.getBoolean(key, (Boolean) def));
			//Change relative paths to absolute paths if working directory is given
			if (workingDirectory.length() > 0 && Arrays.asList(fileFields).contains(field.getName())) {
				String path = (String) field.get(this);
				if (path != null && !(new File(path).isAbsolute())) {
					path = Paths.get(workingDirectory, path).toString();
					field.set(this, path);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static Preferences loadPreferencesFromFile(String filename, String node)
			throws IOException {
		Ini ini = new Ini(new File(filename));
		return new IniPreferences(ini).node(node);
	}

	public Config(String filename) throws IOException, InvalidConfigurationException {
		this(loadPreferencesFromFile(filename, "config"), filename);
	}

	public ProblemConfiguration getProblemConfiguration() {
		ProblemConfiguration config = new ProblemConfiguration();
		Utils.copyMatchingFields(this, config);
		return config;
	}
	
	private void updateVariable(String varName, String varValue) throws InvalidConfigurationException, IOException {
		if(varName.equalsIgnoreCase("run")) {
			updateFromPreferences(loadPreferencesFromFile(expFileName, varValue));
		} else {
			setVariable(varName, varValue);
		}
	}
	
	public ProblemConfiguration setProblemConfiguration(int i, int t) throws InvalidConfigurationException, IOException {
		int[] idx = multiIndexToOne(i);
		for (int var = 0; var < idx.length; var++) {
			String varName = increasingVariable.get(var); 
			String varValue = increasingList.get(var)[idx[var]].toString();
			updateVariable(varName, varValue);
		}
		if(testVariable != null) {
			updateVariable(testVariable, testList[t % testList.length].toString());
		}
		String repr = toString(i);
		extraSettingsLogic();
		ProblemConfiguration config = getProblemConfiguration();
		config.setRepr(repr);

		return config;
	}
	
	public ProblemConfiguration setProblemConfiguration(int i) throws InvalidConfigurationException, IOException {
		return setProblemConfiguration(i, 0);
	}
	
	
	private void setVariable(String var, String value) {
		double dValue = 0;
		try {
			dValue = Double.parseDouble(value);
		} catch (NumberFormatException e) {} ;
		int iValue = (int) dValue;
		Field field = varToField.get(var);
		try {
			if (field.getType() == int.class)
				field.setInt(this, iValue);
			else if (field.getType() == double.class)
				field.setDouble(this, dValue);
			else if (field.getType() == String.class)
				field.set(this, value);
			else if (field.getType() == boolean.class)
				field.setBoolean(this, value.equalsIgnoreCase("True"));
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private int[] multiIndexToOne(int i) {
		final int[] lengths = increasingList.stream().mapToInt(a -> a.length).toArray();
		int dims = lengths.length;
		int[] lengthsR = IntStream.range(0, lengths.length).map(j -> lengths[dims - 1 - j]).toArray();
		Arrays.parallelPrefix(lengthsR, (j, k) -> j * k);
		int[] idx = new int[dims];
		for (int j = 1; j <= dims; j++) {
			int n = (j == dims) ? 1 : lengthsR[dims - 1 - j];
			idx[j - 1] = i / n;
			i -= idx[j - 1] * n;
		}
		return idx;
	}

	public int getNumberOfRuns() {
		return increasingList.stream().mapToInt(a -> a.length).reduce(1, (a, b) -> a * b);
	}

	public boolean isOnline() {
		return experimentType.equalsIgnoreCase("Rolling horizon");
	}

	public String toString(int i) {
		int[] idx = multiIndexToOne(i);
		String res = "";
		for (int var = 0; var < idx.length; var++) {
			Object[] list = increasingList.get(var);
			boolean number = Arrays.stream(list).allMatch(o -> o instanceof Number);
			boolean integer = number
					&& Arrays.stream(list).allMatch(o -> ((Number) o).intValue() == ((Number) o).doubleValue());
			if (var != 0) res += "_";
			Object cur = list[idx[var]];
			res += integer ? (int) Double.parseDouble(cur.toString()) : cur.toString();
		}
		if (res.equals("")) return "results";
		return res;
	}

	public String toStringTabbed(int i) {
		return toString(i).replaceAll("_", "\t");
	}

	public String getColumnHeaders() {
		return String.join("\t", increasingVariable);
	}

	public String getOutputFile() {
		return "output.txt";
	}

	public String getBoxplotOutputFile() {
		return "boxplot.txt";
	}

	private void loadArimaConfig() throws IOException {
		Path markovDownPath = Paths.get(pricedataFolder, this.markovDownFile);
		Path markovUpPath = Paths.get(pricedataFolder, this.markovUpFile);
		if (markovDownConfig == null || !markovDownConfig.getPath().equals(markovDownPath.toString()))
			markovDownConfig = new MarkovConfig(markovDownPath);
		if (markovUpConfig == null || !markovUpConfig.getPath().equals(markovUpPath.toString()))
			markovUpConfig = new MarkovConfig(markovUpPath);
		int PTUsPerDay = (int) Math.round(24.0 / this.ptu);
		if (downConfig == null)
			downConfig = new ArimaConfig(loadPreferencesFromFile(this.expFileName, "arima down"), markovDownConfig, PTUsPerDay);
		else {
			downConfig.setMarkovConfig(markovDownConfig);
			downConfig.setPTUsPerDay(PTUsPerDay);
		}
		if (upConfig == null)
			upConfig = new ArimaConfig(loadPreferencesFromFile(this.expFileName, "arima up"), markovUpConfig, PTUsPerDay);
		else {
			upConfig.setMarkovConfig(markovUpConfig);
			upConfig.setPTUsPerDay(PTUsPerDay);
		}
	}

	private static void addSetting(String fieldName, String iniName, Object def) {
		try {
			Field field = Config.class.getField(fieldName);
			varToField.put(iniName, field);
			defValue.put(iniName, def);
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	{
		varToField = new LinkedHashMap<>();
		defValue = new HashMap<>();
		addSetting("workingDirectory", "working directory", "");
		addSetting("outputFolder", "output folder", "output");
		addSetting("mipLogFile", "mip log file", "mip.log");
		addSetting("mipDebugFile", "mip debug file", "debug.lp");
		addSetting("nLoads", "number of loads", 0);
		addSetting("nScenarios", "number of scenarios", 0);
		addSetting("maxScenarios", "maximum scenarios", 0);
		addSetting("nUClusters", "max number up clusters", "field/number of loads");
		addSetting("nDClusters", "max number down clusters", "field/number of loads");
		addSetting("shortagePenalty", "shortage penalty", 1000);
		addSetting("resultShortagePenalty", "result shortage penalty", "field/shortage penalty");
		addSetting("firstLoadID", "first load id", 0);
		addSetting("fixedPTUs", "number of fixed PTUs", 0); //TODO
		addSetting("experimentType", "experiment type", "Rolling horizon");
		addSetting("randomScenarios", "random scenarios", false);
		addSetting("V2G", "V2G", true);
		addSetting("capacityPayment", "capacity payments", true);
		addSetting("capacityMarket", "capacity market", "field/capacity payments");
		addSetting("dayAhead", "day ahead", true);
		addSetting("imbalance", "imbalance", true);
		addSetting("reserves", "reserves", true);
		addSetting("grid", "grid", false);
		addSetting("quantityOnly", "quantity only bids", false);
		addSetting("reservesSettlement", "reserves settlement", "paid as cleared");
		addSetting("perMinute", "per minute evaluation", false);
		addSetting("robustness", "robustness", 1.0);
		addSetting("minBid", "min bid", 0);
		addSetting("desiredProbabilityOfAcceptance", "chi", 0.9);
		addSetting("relaxedBinaryAfter", "relax binary after", -1);
		addSetting("clusterMethod", "cluster method", "none");
		addSetting("evStateFile", "flexible loads file", null);
		addSetting("gridFile", "grid file", null);
		addSetting("startdatestring", "start date", null); //TODO
		addSetting("dateformat", "date format", "yyyy-MM-dd HH:mm:ss");
		addSetting("pricedataFolder", "price data folder", null);
		addSetting("model", "solution model", "IM");
		addSetting("randomSeed", "random seed", 0);
		addSetting("nTests", "number of tests", 1);
		addSetting("startEvaluationScenario", "start evaluation scenario", 0);
		addSetting("evaluationScenario", "evaluation scenario", "field/start evaluation scenario");
		addSetting("nEvaluationScenarios", "number of evaluation scenarios", "field/maximum scenarios");
		addSetting("fileOutput", "file output", 1);
		addSetting("mipDebug", "mip debug", false);
		addSetting("verbose", "verbose", 5);
		addSetting("mipTimeLimit", "mip time limit", Double.POSITIVE_INFINITY);
		addSetting("mipGap", "mip gap", 1e-6);
		addSetting("mipsolver", "mip solver", "gurobi");
		addSetting("output", "output", "average");
		addSetting("generatorType", "generator type", "historic");
		addSetting("realizationType", "realization type", "historic");
		addSetting("gradient", "gradient", "0.75 1");
		addSetting("ptuLength", "ptu length", 15);
		addSetting("reservesMarketClearance", "reserves market clearance", "paid as cleared");
		addSetting("solutionFile", "solution file", null);
		addSetting("batteryDegradation", "battery degradation costs", 42);
		addSetting("semiStochastic", "semi stochastic", false);
		addSetting("testVariable", "test variable", null);
		addSetting("markovDownFile", "markov down file", "markov_ptu_down_probs.txt");
		addSetting("markovUpFile", "markov up file", "markov_ptu_up_probs.txt");
		addSetting("scenarioFactor", "scenario factor", 1);
	}
}

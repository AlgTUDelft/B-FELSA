package nl.tudelft.alg.fcc.gui.settings;

import java.util.HashMap;
import java.util.Map;

public class Setting {
	public final static Map<String, Setting> settingMap = new HashMap<>();
	String iniName;
	String category;
	String GUID;
	String labelGUID;
	String defaultTextGUID;
	String tooltipGUID;

	public Setting(String iniName, String category) {
		this.iniName = iniName;
		this.category = category;
		this.GUID = category + "." + iniName.replace(" ", "_");
		this.labelGUID = this.GUID + "." + "label";
		this.defaultTextGUID = this.GUID + "." + "default";
		this.tooltipGUID = this.GUID + "." + "tooltip";
		settingMap.put(iniName, this);
	}

	public void appendNumberToSetting(int i) {
		replaceName(iniName + i, category + i);
	}

	public void replaceName(String newName, String newCategory) {
		settingMap.remove(iniName);
		settingMap.put(newName, this);
		iniName = newName;
		category = newCategory;
		GUID = category + "." + iniName.replace(" ", "_");
	}

	public void remove() {
		settingMap.remove(iniName);
	}

	public String getIniName() {
		return iniName;
	}

	public String getGUID() {
		return GUID;
	}

	public String getLabelGUID() {
		return labelGUID;
	}

	public String getCategory() {
		return category;
	}

	public String getDefaultTextGUID() {
		return defaultTextGUID;
	}

	public String getToolTipGUID() {
		return tooltipGUID;
	}

	public static Setting[] settingList = new Setting[] {
			//INPUT
			new FolderSetting("working directory", "input"),
			new FolderSetting("price data folder", "input"), new FileSetting("flexible loads file", "input", "ini"),
			new FileSetting("grid file", "input", "csv"),
			new TextSetting("date format", "input"), new TextSetting("start date", "input"),

			//OUTPUT
			new FolderSetting("output folder", "output"),
			new NumberSetting("verbose", "output", 0, 2, 1),
			new ComboSetting("output", "output", new String[] { "average", "boxplot" }),
			new NumberSetting("file output", "output", 0, 2, 1),

			//MODEL
			new ComboSetting("solution model", "model",
					new String[] { "Direct", "Imbalance Charging", "Stochastic (IRS)", "Stochastic Compact (IRSC)",
							"Deterministic (IRD)", "Piecewise Linear", "EFEL", "Lagrangian Relaxation", "Solution From File"},
					new String[] { "D", "IM", "IRS", "IRSC", "IRD", "NM", "EFEL", "LR", "File" }),
			new NumberSetting("shortage penalty", "model", 0, 1000, 10),
			new NumberSetting("robustness", "model", 0, 1, 0.1),
			new BinarySetting("V2G", "model"),
			new BinarySetting("quantity only bids", "model"), new BinarySetting("grid", "model"),

			//MARKET
			new NumberSetting("ptu length", "market", 1, 60, 5),
			new NumberSetting("number of fixed PTUs", "market", 0, 1000, 1),
			new NumberSetting("battery degradation costs", "market", 0, 1000, 1),
			new BinarySetting("day ahead", "market"),
			new BinarySetting("capacity payments", "market"),
			new ComboSetting("generator type", "market", new String[] { "Historic", "Perfect information", "ARIMA" }, new String[] { "historic", "perfect-information", "arima" }),
			new ComboSetting("realization type", "market", new String[] { "Historic", "ARIMA" }, new String[] { "historic", "arima" }),

			//MODEL-SPECIFIC
			new NumberSetting("number of scenarios", "stochastic", 0, 200, 5),
			new ComboSetting("cluster method", "stochastic", new String[] { "none", "per time step", "per flexible load" }),
			new NumberSetting("max number up clusters", "stochastic", 1, 1000, 1),
			new NumberSetting("max number down clusters", "stochastic", 1, 1000, 1),
			new NumberSetting("min bid", "stochastic", 0, 1000, 1),
			new NumberSetting("relax binary after", "stochastic", -1, 1000, 1),

			new NumberSetting("chi", "deterministic", 0, 1, 0.1),

			new FileSetting("solution file", "filesolution", "csv"),

			//EXPERIMENT
			new ComboSetting("experiment type", "experiment", new String[] { "Day ahead", "Rolling Horizon" }),
			new NumberSetting("random seed", "experiment", 0, 10000, 1), new TextSetting("mip gap", "experiment"),
			new NumberSetting("first load id", "experiment", 0, 100, 1),
			new NumberSetting("number of loads", "experiment", 0, 100, 1),
			new NumberSetting("number of tests", "experiment", 0, 100, 1),
			new NumberSetting("result shortage penalty", "experiment", 0, 1000, 10),
			new NumberSetting("maximum scenarios", "experiment", 0, 100, 72),
			new NumberSetting("start evaluation scenario", "experiment", 0, 100, 1),
			new NumberSetting("number of evaluation scenarios", "experiment", 0, 100, 1),
			new BinarySetting("random scenarios", "experiment"),
			new BinarySetting("per minute evaluation", "experiment"),
			new ComboSetting("increasing", "testvariable", null),
			new TextSetting("start", "testvariable"), new TextSetting("end", "testvariable"),
			new TextSetting("step", "testvariable"), new TextSetting("list", "testvariable"),

			//SOLVER
			new ComboSetting("mip solver", "solver", new String[] { "Gurobi", "GLPK" }),
			new FileSetting("mip log file", "solver", "log"),
			new BinarySetting("mip debug", "solver"),
			

	};

}

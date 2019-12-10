package nl.tudelft.alg.fcc.gui.settings;

public class NumberSetting extends Setting {
	double low, high, step;

	public NumberSetting(String iniName, String category, double low, double high, double step) {
		super(iniName, category);
		this.low = low;
		this.high = high;
		this.step = step;
	}

	public double getLow() {
		return low;
	}

	public double getHigh() {
		return high;
	}

	public double getStep() {
		return step;
	}

}

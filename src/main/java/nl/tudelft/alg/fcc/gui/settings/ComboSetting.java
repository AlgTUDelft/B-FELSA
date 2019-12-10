package nl.tudelft.alg.fcc.gui.settings;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ComboSetting extends TextSetting {
	String[] options;
	String[] configOptions;

	public ComboSetting(String iniName, String category, String[] options) {
		this(iniName, category, options, options);
	}

	public ComboSetting(String iniName, String category, String[] options, String[] configOptions) {
		super(iniName, category);
		this.options = options;
		this.configOptions = configOptions;
	}

	public String[] getOptions() {
		return options;
	}

	public String getConfigOption(String neatOption) {
		int ix = Arrays.asList(options).stream().map(s -> s.toLowerCase()).collect(Collectors.toList()).indexOf(neatOption.toLowerCase());
		if (ix < 0 || ix >= configOptions.length) return neatOption;
		return configOptions[ix];
	}

	public String getNeatOption(String configOption) {
		int ix = Arrays.asList(configOptions).stream().map(s -> s.toLowerCase()).collect(Collectors.toList()).indexOf(configOption.toLowerCase());
		if (ix < 0 || ix >= options.length) return configOption;
		return options[ix];
	}

}

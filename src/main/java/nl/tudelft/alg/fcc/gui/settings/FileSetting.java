package nl.tudelft.alg.fcc.gui.settings;

import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public class FileSetting extends TextSetting {
	FileFilter filter;

	public FileSetting(String iniName, String category, String filetype) {
		super(iniName, category);
		switch (filetype) {
			case "csv":
				this.filter = new FileNameExtensionFilter("Comma Seperated Values (csv)", "csv");
				break;
			case "ini":
				this.filter = new FileNameExtensionFilter("Configuration file (ini)", "ini");
				break;
			case "log":
				this.filter = new FileNameExtensionFilter("Log file (log)", "log");
				break;
			case "folder":
				this.filter = null;
		}
	}

	public FileFilter getFilter() {
		return filter;
	}

}

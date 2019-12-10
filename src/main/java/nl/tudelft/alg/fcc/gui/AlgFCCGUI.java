package nl.tudelft.alg.fcc.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import org.ini4j.Profile.Section;

import nl.tudelft.alg.fcc.gui.settings.BinarySetting;
import nl.tudelft.alg.fcc.gui.settings.ComboSetting;
import nl.tudelft.alg.fcc.gui.settings.FileSetting;
import nl.tudelft.alg.fcc.gui.settings.FolderSetting;
import nl.tudelft.alg.fcc.gui.settings.NumberSetting;
import nl.tudelft.alg.fcc.gui.settings.Setting;
import nl.tudelft.alg.fcc.gui.settings.TextSetting;
import nl.tudelft.alg.fcc.main.App;

public class AlgFCCGUI extends JFrame {
	private static final long serialVersionUID = -7293006135949195796L;
	public static final ResourceBundle BUNDLE = ResourceBundle.getBundle("nl.tudelft.alg.fcc.gui.messages"); //$NON-NLS-1$

	private Map<String, JPanel> panelMap;
	private Map<String, JComponent> components;
	private Map<String, String> values;
	private Map<String, Consumer<String>> eventHandlers;
	private JPanel contentPane;
	private JPanel increasingpanel, experimentpanel;
	private JButton addVariableSettingButton;

	private JTabbedPane tabbedPane;
	private int nTestVariables;
	private String configFile = "config.ini";

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					AlgFCCGUI frame = new AlgFCCGUI();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private JPanel createPanel(String panelName, String tabName) {
		JPanel panel = new JPanel();
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[] { 0, 0, 0 };
		gbl_panel.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
		panel.setLayout(gbl_panel);
		if (panelName != null) panelMap.put(panelName, panel);
		if (tabName != null) tabbedPane.addTab(BUNDLE.getString(tabName + ".tabTitle"), null, panel, null); //$NON-NLS-1$
		return panel;
	}

	static void placeComponentInGrid(JPanel panel, JComponent component, int x, int y) {
		GridBagConstraints gbc_component = new GridBagConstraints();
		gbc_component.insets = new Insets(0, 0, 5, 5);
		gbc_component.fill = GridBagConstraints.HORIZONTAL;
		gbc_component.gridx = x;
		gbc_component.gridy = y;
		panel.add(component, gbc_component);
	}

	void createFromSetting(JPanel panel, Setting setting, int y) {
		values.put(setting.getGUID(), BUNDLE.getString(setting.getDefaultTextGUID()));
		JLabel label = createLabelFromSetting(panel, setting, y);
		if (setting instanceof TextSetting) {
			JComponent component = createTextFieldFromSetting(panel, (TextSetting) setting, y);
			if (setting instanceof FileSetting) {
				JButton button = createBrowseButtonFromSetting(panel, (FileSetting) setting, y, (JTextField) component);
			}
			components.put(setting.getGUID(), component);
		} else if (setting instanceof NumberSetting) {
			JSpinner spinner = createNumberFieldFromSetting(panel, (NumberSetting) setting, y);
			components.put(setting.getGUID(), spinner);
		}
		if (BUNDLE.containsKey(setting.getToolTipGUID()))
			label.setToolTipText(BUNDLE.getString(setting.getToolTipGUID()));
	}

	JButton createBrowseButtonFromSetting(JPanel panel, FileSetting setting, int y, JTextField textfield) {
		JButton browseButton = new JButton(BUNDLE.getString("MainWindow.btnBrowse.text"));
		browseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				String wdPath = values.get("input.working_directory").toString();
				Path wd = Paths.get(wdPath);
				JFileChooser chooser = new JFileChooser();
				chooser.setCurrentDirectory(!wdPath.equals("") && Files.exists(wd) ? wd.toFile() : new java.io.File("."));
				chooser.setDialogTitle("Browse the folder to process");
				chooser.setFileSelectionMode(
						setting instanceof FolderSetting ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
				chooser.setAcceptAllFileFilterUsed(false);
				chooser.setFileFilter(setting.getFilter());

				if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
					File selection = chooser.getSelectedFile();
					if (!setting.getGUID().equals("input.working_directory")) {
						if (wdPath.equals(""))
							textfield.setText(selection.toPath().toString().replace("\\", "/"));
						else
							textfield.setText(wd.relativize(selection.toPath()).toString().replace("\\", "/"));
					} else {
						textfield.setText(selection.toString().replace("\\", "/"));
					}
				}
			}
		});
		placeComponentInGrid(panel, browseButton, 2, y);
		return browseButton;
	}

	JSpinner createNumberFieldFromSetting(JPanel panel, NumberSetting setting, int y) {
		JSpinner spinner = new JSpinner();
		spinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				values.put(setting.getGUID(), spinner.getValue().toString());
				if (eventHandlers.containsKey(setting.getGUID()))
					eventHandlers.get(setting.getGUID()).accept(spinner.getValue().toString());
			}
		});
		String deflt = BUNDLE.getString(setting.getDefaultTextGUID());
		spinner.setModel(
				new SpinnerNumberModel(Double.parseDouble(deflt), setting.getLow(), setting.getHigh(), setting.getStep()));

		placeComponentInGrid(panel, spinner, 1, y);
		return spinner;
	}

	JComponent createTextFieldFromSetting(JPanel panel, TextSetting setting, int y) {
		JComponent component;
		if (setting instanceof ComboSetting) {
			JComboBox<String> combobox = new JComboBox<String>(((ComboSetting) setting).getOptions());
			combobox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					Object item = combobox.getSelectedItem();
					if (item == null) return;
					String value = item.toString();
					values.put(setting.getGUID(), ((ComboSetting) setting).getConfigOption(value));
					if (eventHandlers.containsKey(setting.getGUID())) eventHandlers.get(setting.getGUID()).accept(value);
				}
			});
			String value = BUNDLE.getString(setting.getDefaultTextGUID());
			combobox.setSelectedItem(value);
			values.put(setting.getGUID(), ((ComboSetting) setting).getConfigOption(value));
			component = combobox;
		} else {
			JTextField textfield = new JTextField();
			textfield.getDocument().addDocumentListener(new DocumentListener() {
				@Override
				public void removeUpdate(DocumentEvent e) {
					update();
				}

				@Override
				public void insertUpdate(DocumentEvent e) {
					update();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					update();
				}

				public void update() {
					values.put(setting.getGUID(), textfield.getText());
					if (eventHandlers.containsKey(setting.getGUID()))
						eventHandlers.get(setting.getGUID()).accept(textfield.getText());
				}
			});
			String value = BUNDLE.getString(setting.getDefaultTextGUID());
			textfield.setText(value);
			values.put(setting.getGUID(), value);
			textfield.setColumns(10);
			component = textfield;
		}
		placeComponentInGrid(panel, component, 1, y);
		return component;
	}

	void createCheckboxFromSetting(JPanel binaryPanel, BinarySetting s) {
		JCheckBox chckbx = new JCheckBox(BUNDLE.getString(s.getLabelGUID()));
		chckbx.setSelected(BUNDLE.getString(s.getDefaultTextGUID()).equals("true"));
		chckbx.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				String value = e.getStateChange() == ItemEvent.SELECTED ? "true" : "false";
				values.put(s.getGUID(), value);
				if (eventHandlers.containsKey(s.getGUID()))
					eventHandlers.get(s.getGUID()).accept(value);
			}
		});
		binaryPanel.add(chckbx);
		values.put(s.getGUID(), BUNDLE.getString(s.getDefaultTextGUID()));
		components.put(s.getGUID(), chckbx);
	}

	static JLabel createLabelFromSetting(JPanel panel, Setting setting, int y) {
		return createLabel(panel, BUNDLE.getString(setting.getLabelGUID()), y);
	}

	static JLabel createLabel(JPanel panel, String text, int y) {
		JLabel label = new JLabel(text);
		placeComponentInGrid(panel, label, 0, y);
		return label;
	}

	private void updateModelPanel(String value) {
		String[] panelNames = { "empty", "stochastic", "deterministic", "filesolution" };
		for (String n : panelNames) {
			panelMap.get(n).setVisible(false);
		}
		JPanel panel = panelMap.get("empty");
		switch (value) {
			case "EFEL":
			case "Lagrangian Relaxation":
			case "Stochastic (IRS)":
			case "Stochastic Compact (IRSC)":
				panel = panelMap.get("stochastic");
				break;
			case "Deterministic (IRD)":
				panel = panelMap.get("deterministic");
				break;
			case "Solution From File":
				panel = panelMap.get("filesolution");
				break;
			default:
				break;
		}
		panel.setVisible(true);
	}

	JComponent getComponent(String GUID) {
		return components.get(GUID);
	}

	void removeComponent(String GUID) {
		components.remove(GUID);
	}

	void removePanel(String name) {
		panelMap.remove(name);
	}

	void addPanel(JPanel panel, String name) {
		panelMap.put(name, panel);
	}

	/**
	 * Create the frame.
	 */
	public AlgFCCGUI() {
		super("Flexible Loads Toolbox - Launcher");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 750, 508);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		
		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		contentPane.add(tabbedPane);
		panelMap = new HashMap<String, JPanel>();
		
		values = new HashMap<String, String>();
		components = new HashMap<String, JComponent>();
		eventHandlers = new HashMap<String, Consumer<String>>();

		JPanel experimentTabPanel = new JPanel();
		JPanel inputpanel = createPanel("input", "input");
		JPanel outputpanel = createPanel("output", "output");
		JPanel modelpanel = createPanel("model", "model");
		JPanel marketpanel = createPanel("market", "market");
		JPanel emptypanel = createPanel("empty", null);
		JPanel stochasticpanel = createPanel("stochastic", null);
		JPanel deterministicpanel = createPanel("deterministic", null);
		JPanel filesolutionpanel = createPanel("filesolution", null);
		experimentpanel = createPanel("experiment", "experiment");
		increasingpanel = new JPanel();
		tabbedPane.addTab(BUNDLE.getString("experiment.tabTitle"), null, experimentTabPanel, null); //$NON-NLS-1$
		JPanel solverpanel = createPanel("solver", "solver");

		eventHandlers.put("model.solution_model", this::updateModelPanel);

		JPanel modeloptionspanel = new JPanel();
		modeloptionspanel.setBorder(new TitledBorder(null, BUNDLE.getString("MainWindow.borderModelTitle"), TitledBorder.LEADING, TitledBorder.TOP, null, null));
		GridBagConstraints gbc_modeloptionspanel = new GridBagConstraints();
		gbc_modeloptionspanel.gridwidth = 2;
		gbc_modeloptionspanel.fill = GridBagConstraints.BOTH;
		gbc_modeloptionspanel.gridx = 0;
		modeloptionspanel.setLayout(new CardLayout(0, 0));
		
		modeloptionspanel.add(emptypanel);
		JLabel lblNoModelSpecific = new JLabel(BUNDLE.getString("MainWindow.lblNoModelSpecific.text")); //$NON-NLS-1$
		lblNoModelSpecific.setHorizontalAlignment(SwingConstants.CENTER);
		lblNoModelSpecific.setFont(new Font("Tahoma", Font.ITALIC, 13));
		emptypanel.add(lblNoModelSpecific);
		
		modeloptionspanel.add(stochasticpanel);
		modeloptionspanel.add(deterministicpanel);
		modeloptionspanel.add(filesolutionpanel);


		experimentTabPanel.setLayout(new BoxLayout(experimentTabPanel, BoxLayout.Y_AXIS));
		experimentTabPanel.add(experimentpanel);
		experimentTabPanel.add(new JSeparator());
		JScrollPane scrollIncreasingPanel = new JScrollPane(increasingpanel);
		experimentTabPanel.add(scrollIncreasingPanel);
		
		JPanel bottompanel = new JPanel();
		contentPane.add(bottompanel, BorderLayout.SOUTH);
		bottompanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		
		JButton btnBrowse = new JButton(BUNDLE.getString("MainWindow.btnBrowse.text"));
		btnBrowse.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String wdPath = values.get("input.working_directory").toString();
				Path wd = Paths.get(wdPath);
				JFileChooser chooser = new JFileChooser();
				chooser.setCurrentDirectory(!wdPath.equals("") && Files.exists(wd) ? wd.toFile() : new File("."));
				chooser.setDialogTitle("Browse the folder to process");
				chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				chooser.setAcceptAllFileFilterUsed(false);
				chooser.setFileFilter(new FileNameExtensionFilter("Configuration file (ini)", "ini"));

				if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
					File selection = chooser.getSelectedFile();
					if (selection.exists()) {
						configFile = selection.getAbsolutePath();
						Ini ini;
						try {
							ini = new Ini(selection);
							updateFromIni(new IniPreferences(ini).node("config"));
						} catch (IOException e1) {
							e1.printStackTrace();
						}

					}
						
				}

			}
		});
		bottompanel.add(btnBrowse);

		JButton btnApply = new JButton(BUNDLE.getString("MainWindow.btnApply.text"));
		btnApply.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				writeToIni();
			}
		});
		bottompanel.add(btnApply);
		
		JButton btnApplyAndRun = new JButton(BUNDLE.getString("MainWindow.btnApplyAndRun.text"));
		btnApplyAndRun.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				writeToIni();
				App.runSimulator(new File(configFile).getAbsolutePath());
				if(Double.parseDouble(values.get("output.file_output")) == 0) return;
				try {
					Path p = Files.list(new File(values.get("output.output_folder")).toPath())
							.filter(f -> Files.isDirectory(f))
							.max(Comparator.comparingLong(f -> f.toFile().lastModified())).orElse(null);
					if (p == null) return;
					new ProcessBuilder("python.exe", Paths.get("pyplot", "plotgui.py").toString(), p.toString()).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		bottompanel.add(btnApplyAndRun);

		List<BinarySetting> binarySettings = new ArrayList<BinarySetting>();
		for (Setting s : Setting.settingList) {
			if (s instanceof BinarySetting) {
				binarySettings.add((BinarySetting) s);
				continue;
			}
			JPanel _panel = panelMap.get(s.getCategory());
			if (_panel == null) continue;
			int y = getNumberOfRows(_panel);
			createFromSetting(_panel, s, y);
		}
		for (Entry<String, JPanel> e : panelMap.entrySet()) {
			JPanel _panel = e.getValue();
			String panelname = e.getKey();
			int y = getNumberOfRows(_panel);
			JPanel binaryPanel = new JPanel();
			GridBagConstraints gbc_binaryPanel = new GridBagConstraints();
			gbc_binaryPanel.insets = new Insets(0, 0, 5, 5);
			//gbc_binaryPanel.gridwidth = 1;
			gbc_binaryPanel.fill = GridBagConstraints.BOTH;
			gbc_binaryPanel.gridx = 1;
			gbc_binaryPanel.gridy = y;
			_panel.add(binaryPanel, gbc_binaryPanel);
			binaryPanel.setLayout(new GridLayout(0, 2, 0, 0));
			for(BinarySetting s: binarySettings) {
				if (s.getCategory().equals(panelname)) {
					createCheckboxFromSetting(binaryPanel, s);
				}
			}
		}
		modelpanel.doLayout();
		gbc_modeloptionspanel.gridy = ((GridBagLayout) modelpanel.getLayout()).getLayoutDimensions()[1].length;
		modelpanel.add(modeloptionspanel, gbc_modeloptionspanel);
		for (JPanel _panel : panelMap.values()) {
			GridBagLayout layout = (GridBagLayout) _panel.getLayout();
			int h = getNumberOfRows(_panel) + 2;
			double[] we = new double[h];
			int[] wi = new int[h];
			we[h - 1] = Double.MIN_VALUE;
			layout.rowWeights = we;
			layout.rowHeights = wi;
		}

		nTestVariables = 0;
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[] { 0 };
		gbl_panel.columnWeights = new double[] { 1.0 };
		increasingpanel.setLayout(gbl_panel);
		addVariableSettingButton = new JButton("Add variable setting");
		placeComponentInGrid(increasingpanel, addVariableSettingButton, 0, 0);
		addVariableSettingButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				addTestVariable();
			}
		});
		
		App.logger.addHandler(new Handler() {
			@Override
			public void publish(LogRecord record) {
				JOptionPane.showMessageDialog(AlgFCCGUI.this,
					    record.getMessage(),
					    "Error in running the simulator",
					    JOptionPane.ERROR_MESSAGE);
			}
			@Override
			public void flush() {}
			@Override
			public void close() throws SecurityException {}
		});
	}

	private TestVariablePanel addTestVariable() {
		increasingpanel.remove(addVariableSettingButton);
		TestVariablePanel panel = new TestVariablePanel(AlgFCCGUI.this, ++nTestVariables);
		placeComponentInGrid(increasingpanel, panel, 0, nTestVariables - 1);
		placeComponentInGrid(increasingpanel, addVariableSettingButton, 0, nTestVariables);
		experimentpanel.revalidate();
		experimentpanel.repaint();
		return panel;
	}

	void removeTestVariable(TestVariablePanel testVariablePanel, int i) {
		if (testVariablePanel == null)
			testVariablePanel = (TestVariablePanel) panelMap.get("testvariable" + i);
		removePanel("testvariable" + i);
		increasingpanel.remove(testVariablePanel);
		experimentpanel.revalidate();
		experimentpanel.repaint();
		for(int j=i+1; j<=nTestVariables; j++) {
			TestVariablePanel panel = (TestVariablePanel) panelMap.get("testvariable" + j);
			panel.updateIndex(j - 1);
			panelMap.remove("testvariable" + j);
			panelMap.put("testvariable" + (j - 1), panel);
		}
		nTestVariables--;
	}

	void updateGUID(String oldGUID, String newGUID) {
		JComponent component = getComponent(oldGUID);
		removeComponent(oldGUID);
		components.put(newGUID, component);
		String value = values.get(oldGUID);
		values.remove(oldGUID);
		values.put(newGUID, value);

	}

	private int getNumberOfRows(JPanel panel) {
		assert panel.getLayout() instanceof GridBagLayout;
		panel.doLayout();
		GridBagLayout layout = (GridBagLayout) panel.getLayout();
		return layout.getLayoutDimensions()[1].length;
	}

	private String getValue(Setting s) {
		String value = values.get(s.getGUID());
		if (value != null) return value;
		return "not initialized";
	}

	public void writeToIni() {
		Ini ini = new Ini();
		ini.getConfig().setEscape(false);
		Section config = ini.add("config");
		for (Setting s : Setting.settingList) {
			if (s.getCategory().startsWith("testvariable")) continue;
			config.add(s.getIniName(), getValue(s));
		}
		for (int i = 1; i <= nTestVariables; i++) {
			for (Setting s : Setting.settingList) {
				if (!s.getCategory().startsWith("testvariable")) continue;
				Setting _s = Setting.settingMap.get(s.getIniName() + i);
				if (getValue(_s).length() == 0) continue;
				config.add(_s.getIniName(), getValue(_s));
			}
		}
		writeToIni(ini);
	}

	public void writeToIni(Ini ini) {
		try {
			ini.store(new File(configFile));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void updateFromIni(Preferences config) {
		String[] keys = new String[] {};
		try {
			keys = config.keys();
		} catch (BackingStoreException e) {
			e.printStackTrace();
		}
		while (nTestVariables > 0)
			this.removeTestVariable(null, 1);
		for (String key : keys) {
			if (key.startsWith("increasing")) {
				//int ix = Integer.parseInt(key.replace("increasing", ""));
				TestVariablePanel panel = addTestVariable();
				String var = config.get(key, null);
				Setting s = Setting.settingMap.get(var);
				panel.categoryBox.setSelectedItem(s.getCategory());
			}
		}
		for (String key : keys) {
			if (!Setting.settingMap.containsKey(key)) continue;
			Setting s = Setting.settingMap.get(key);
			JComponent c = components.get(s.getGUID());
			if (c instanceof JTextField) {
				String v = config.get(key, null);
				if (v != null) ((JTextField) c).setText(v);
			} else if (c instanceof JComboBox<?>) {
				String v = config.get(key, null);
				v = ((ComboSetting) s).getNeatOption(v);
				if (v != null) ((JComboBox<?>) c).setSelectedItem(v);
			} else if (c instanceof JSpinner) {
				double v = config.getDouble(key, 0);
				((JSpinner) c).setValue(v);
			} else if (c instanceof JCheckBox) {
				boolean v = config.getBoolean(key, false);
				((JCheckBox) c).setSelected(v);

			}
		}
	}

}

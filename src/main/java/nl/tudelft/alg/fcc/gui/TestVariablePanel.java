package nl.tudelft.alg.fcc.gui;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import nl.tudelft.alg.fcc.gui.settings.ComboSetting;
import nl.tudelft.alg.fcc.gui.settings.NumberSetting;
import nl.tudelft.alg.fcc.gui.settings.Setting;
import nl.tudelft.alg.fcc.gui.settings.TextSetting;

public class TestVariablePanel extends JPanel {
	private static final long serialVersionUID = -22736919296922476L;
	AlgFCCGUI parent;
	JComboBox<String> categoryBox;
	JComboBox<String> variableBox;
	JSpinner startSpinner;
	JSpinner endSpinner;
	JSpinner stepSpinner;
	Setting[] settings;
	Setting variable, start, end, step;
	int index;

	public TestVariablePanel(AlgFCCGUI parent, int i) {
		this.parent = parent;
		this.index = i;
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[] { 0, 0, 0 };
		gbl_panel.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
		setLayout(gbl_panel);

		createSettings(i);
		parent.addPanel(this, "testvariable" + i);
		
		int y = 0;
		AlgFCCGUI.createLabel(this, AlgFCCGUI.BUNDLE.getString("testvariable.category.label"), y);
		categoryBox = new JComboBox<String>(new String[] { "model", "market", "stochastic", "deterministic", "experiment" });
		AlgFCCGUI.placeComponentInGrid(this, categoryBox, 1, y);
		for (Setting s : settings)
			parent.createFromSetting(this, s, ++y);
		variableBox = (JComboBox<String>) parent.getComponent(variable.getGUID());
		startSpinner = (JSpinner) parent.getComponent(start.getGUID());
		endSpinner = (JSpinner) parent.getComponent(end.getGUID());
		stepSpinner = (JSpinner) parent.getComponent(step.getGUID());
		
		variableBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Object item = variableBox.getSelectedItem();
				if (item == null) return;
				String value = item.toString();
				setStartEndStepList(value);
			}
		});
		categoryBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String value = categoryBox.getSelectedItem().toString();
				setVariableOptions(value);
			}
		});
		categoryBox.setSelectedItem(AlgFCCGUI.BUNDLE.getString("testvariable.category.default"));

		JButton deleteButton = new JButton("Remove variable setting");
		AlgFCCGUI.placeComponentInGrid(this, deleteButton, 1, ++y);
		deleteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (Setting setting : settings) {
					setting.remove();
					TestVariablePanel.this.parent.removeComponent(setting.getGUID());
				}
				TestVariablePanel.this.parent.removeTestVariable(TestVariablePanel.this, index);
			}
		});
	}

	private void createSettings(int i) {
		variable = new ComboSetting("increasing", "testvariable",
				Arrays.stream(Setting.settingList).map(s -> s.getIniName()).toArray(String[]::new));
		start = new NumberSetting("start", "testvariable", 0, 100, 1);
		end = new NumberSetting("end", "testvariable", 0, 100, 1);
		step = new NumberSetting("step", "testvariable", 0, 100, 1);
		TextSetting list = new TextSetting("list", "testvariable");
		settings = new Setting[] { variable, start, end, step, list };
		for (Setting s : settings)
			s.appendNumberToSetting(i);
	}

	private void setVariableOptions(String category) {
		Setting[] settings = Arrays.stream(Setting.settingList).filter(s -> s.getCategory().equals(category)).toArray(Setting[]::new);
		variableBox.removeAllItems();
		Arrays.stream(settings).forEach(s -> variableBox.addItem(s.getIniName()));
	}

	private void setStartEndStepList(String label) {
		Setting setting = Arrays.stream(Setting.settingList).filter(s -> s.getIniName().equals(label)).findFirst().get();
		boolean number = setting instanceof NumberSetting;
		for (JSpinner spinner : new JSpinner[] { startSpinner, endSpinner, stepSpinner })
			spinner.setEnabled(number);
		if (number) {
			NumberSetting nSetting = (NumberSetting) setting;
			startSpinner.setModel(new SpinnerNumberModel(nSetting.getLow(), nSetting.getLow(), nSetting.getHigh(), nSetting.getStep()));
			endSpinner.setModel(new SpinnerNumberModel(nSetting.getHigh(), nSetting.getLow(), nSetting.getHigh(), nSetting.getStep()));
			stepSpinner.setModel(new SpinnerNumberModel(nSetting.getStep(), 0, nSetting.getHigh(), nSetting.getStep()));
		}
		
	}

	public void updateIndex(int newIndex) {
		String oldCategory = "testvariable"+index;
		String newCategory = "testvariable"+newIndex;
		String newIndexS = Integer.toString(newIndex);
		String oldIndexS = Integer.toString(index);
		for (Setting setting : settings) {
			String oldIniName = setting.getIniName();
			String oldGUID = setting.getGUID();
			int ind = oldIniName.lastIndexOf(oldIndexS);
			if(ind < 0) continue;
			String newIniName = new StringBuilder(oldIniName).replace(ind, ind+oldIndexS.length(), newIndexS).toString();
			setting.replaceName(newIniName, newCategory);
			String newGUID = setting.getGUID();
			parent.updateGUID(oldGUID, newGUID);
		}
		index = newIndex;
	}
}

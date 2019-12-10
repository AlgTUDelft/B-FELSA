package nl.tudelft.alg.fcc.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;

import nl.tudelft.alg.fcc.model.DAPrice;
import nl.tudelft.alg.fcc.model.Line;
import nl.tudelft.alg.fcc.model.PerMinuteData;
import nl.tudelft.alg.fcc.model.PriceScenarioData;



public class ProblemImporter {
	
	public static DAPrice importDAPrices(String file) throws FileNotFoundException {
		String[][] priceData = CSVReader.readCsvFile(file);
		DAPrice daPrice = new DAPrice();
		for (int i = 1; i < priceData.length; i += 1) {
			String date = priceData[i][0];
			String time = priceData[i][1];
			double price = Double.parseDouble(priceData[i][2]);
			daPrice.addDatePricePair(date + " " + time, price);
		}
		return daPrice;
	}
	
	public static PriceScenarioData importPriceScenarioData(DAPrice da, String dataFolder, int ptuLength, boolean capacityPrices)
			throws FileNotFoundException {
		String spreaddownfile = Paths.get(dataFolder, "price_down.csv").toString();
		String spreadupfile = Paths.get(dataFolder, "price_up.csv").toString();
		String spreadcapdownfile = Paths.get(dataFolder, "cprice_down.csv").toString();
		String spreadcapupfile = Paths.get(dataFolder, "cprice_up.csv").toString();
		String ptudownfile = Paths.get(dataFolder, "ptu_down.csv").toString();
		String ptuupfile = Paths.get(dataFolder, "ptu_up.csv").toString();
		String erupfile = Paths.get(dataFolder, "ER_up.csv").toString();
		String erdownfile = Paths.get(dataFolder, "ER_down.csv").toString();
		String apupfile = Paths.get(dataFolder, "AP_up.csv").toString();
		String apdownfile = Paths.get(dataFolder, "AP_down.csv").toString();
		String[][] rawpricedata_down = CSVReader.readCsvFile(spreaddownfile);
		String[][] rawpricedata_up = CSVReader.readCsvFile(spreadupfile);
		String[][] rawcappricedata_down = capacityPrices ? CSVReader.readCsvFile(spreadcapdownfile) : null;
		String[][] rawcappricedata_up = capacityPrices ? CSVReader.readCsvFile(spreadcapupfile) : null;
		String[][] rawptudata_down = CSVReader.readCsvFile(ptudownfile);
		String[][] rawptudata_up = CSVReader.readCsvFile(ptuupfile);
		String[][] rawer_up = (new File(erupfile).exists()) ? CSVReader.readCsvFile(erupfile) : null;
		String[][] rawer_down = (new File(erupfile).exists()) ? CSVReader.readCsvFile(erdownfile) : null;
		String[][] rawap_up = (new File(erupfile).exists()) ? CSVReader.readCsvFile(apupfile) : null;
		String[][] rawap_down = (new File(erupfile).exists()) ? CSVReader.readCsvFile(apdownfile) : null;
		Utils.setDateFormat("yyyy-MM-dd HH:mm:ss");
		Calendar startDate = Utils.stringToCalender(rawpricedata_down[1][0]);
		Calendar secondDate = Utils.stringToCalender(rawpricedata_down[2][0]);
		Calendar endDate = Utils.stringToCalender(rawpricedata_down[rawpricedata_down.length - 1][0]);
		int mDiff = (int) (ChronoUnit.MINUTES.between(startDate.toInstant(), secondDate.toInstant()));
		int nTimeSteps = (int) ((ChronoUnit.MINUTES.between(startDate.toInstant(), endDate.toInstant()) + mDiff) / ptuLength);
		int nScenarios = rawpricedata_down[0].length - 1;
		PriceScenarioData data = new PriceScenarioData(nTimeSteps, nScenarios);
		int tx = 0;
		Calendar nextPTU = startDate;
		for (int t = 1; t < rawpricedata_down.length; t++) {
			String date = rawpricedata_down[t][0];
			Calendar thisPTU = Utils.stringToCalender(date);
			thisPTU.add(Calendar.MINUTE, mDiff);
			while (nextPTU.compareTo(thisPTU) < 0) {
				data.addDateIndex(nextPTU, tx);
				Calendar cal = data.getDateByIndex(tx);
				double daprice = da.getPrice(cal);
				data.setDAPrice(tx, daprice);
				for (int i = 0; i < nScenarios; i++) {
					double downprice = Double.parseDouble(rawpricedata_down[t][i + 1]);
					double upprice = Double.parseDouble(rawpricedata_up[t][i + 1]);
					data.setDownPrice(tx, i, downprice);
					data.setUpPrice(tx, i, upprice);
					if (capacityPrices) {
						double capdownprice = Double.parseDouble(rawcappricedata_down[t][i + 1]);
						double capupprice = Double.parseDouble(rawcappricedata_up[t][i + 1]);
						data.setCapDownPrice(tx, i, capdownprice);
						data.setCapUpPrice(tx, i, capupprice);
					}
					double prop_down = Double.parseDouble(rawptudata_down[t][i + 1]);
					double prop_up = Double.parseDouble(rawptudata_up[t][i + 1]);
					data.setProportionDownUsed(tx, i, prop_down);
					data.setProportionUpUsed(tx, i, prop_up);
					if (prop_down > prop_up)
						data.setImbalancePrice(tx, i, downprice);
					else
						data.setImbalancePrice(tx, i, upprice);

					if (rawer_up != null) data.setERUp(tx, Line.newLines(rawer_up[t]));
					if (rawer_down != null) data.setERDown(tx, Line.newLines(rawer_down[t]));
					if (rawap_up != null) data.setAPUp(tx, Line.newLines(rawap_up[t]));
					if (rawap_down != null) data.setAPDown(tx, Line.newLines(rawap_down[t]));

				}
				tx++;
				nextPTU.add(Calendar.MINUTE, ptuLength);
			}
		}
		for (int i = 0; i < nScenarios; i++) {
			data.setScenarioProbability(i, 1.0 / nScenarios);
		}
		data.calcExpected();
		return data;
	}
	
	public static PerMinuteData importPerMinuteImbalance(String dataFolder, int ptuLength) throws FileNotFoundException {
		String minuteDataFile = Paths.get(dataFolder, "minute.csv").toString();
		String[][] rawMinuteData = CSVReader.readCsvFile(minuteDataFile);
		int nTimeSteps = rawMinuteData.length-1;
		Utils.setDateFormat("MM/dd/yyyy HH:mm");
		PerMinuteData data = new PerMinuteData(nTimeSteps, ptuLength);
		for(int t=1; t<nTimeSteps; t++) {
			String date = rawMinuteData[t][0] + " " + rawMinuteData[t][2];
			data.addDateIndex(date, t-1);
			double upreg = Integer.parseInt(rawMinuteData[t][5]);
			double downreg = Integer.parseInt(rawMinuteData[t][6]);
			double midprice = Double.parseDouble(rawMinuteData[t][11]);
			double upprice, downprice;
			if(rawMinuteData[t][10].isEmpty()) upprice = - Double.MAX_VALUE;
			else upprice = Double.parseDouble(rawMinuteData[t][10]);
			if(rawMinuteData[t][12].isEmpty()) downprice = Double.MAX_VALUE;
			else downprice = Double.parseDouble(rawMinuteData[t][12]);
			data.setUpRegulatingVolume(t-1, upreg);
			data.setDownRegulatingVolume(t-1, downreg);
			data.setUpRegulatingPrice(t-1, upprice);
			data.setDownRegulatingPrice(t-1, downprice);
			data.setMidPrice(t-1, midprice);
			if (t % ptuLength == 0)
				data.setDerivedPrices(t);
		}
		return data;
	}
	
}

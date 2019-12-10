package nl.tudelft.alg.fcc.model;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nl.tudelft.alg.fcc.utils.Utils;

public class DAPrice {
	Map<Calendar, Double> prices;
		
	public DAPrice() {
		prices = new HashMap<Calendar, Double>();
	}
	
	public void addDatePricePair(String d, double p) {
		addDatePricePair(Utils.stringToCalender(d), p);
	}
	
	public void addDatePricePair(Calendar d, double p) {
		prices.put(d, p);
	}
		
	public double getPrice(Calendar cal) {
		cal = (Calendar) cal.clone();
		cal.set(Calendar.MINUTE, 0);
		return prices.get(cal);
	}
	
	public double getPrice(String d) {
		return getPrice(Utils.stringToCalender(d));
	}
	
	public int getNTimeSteps() {
		return prices.size();
	}
	
	public Set<Calendar> getDates() {
		return prices.keySet();
	}
		
}

package nl.tudelft.alg.fcc.model;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import nl.tudelft.alg.fcc.utils.Utils;

public abstract class PriceData {
	Map<Calendar, Integer> datemap;
	Calendar[] dates;
	Map<String, Object> extraData;
	
	public PriceData(int nTimeSteps) {
		datemap = new HashMap<Calendar, Integer>();
		dates = new Calendar[nTimeSteps];
		extraData = new HashMap<String, Object>();
	}
	
	public void addDateIndex(String date, int index) {
		Calendar cal = Utils.stringToCalender(date);
		addDateIndex(cal, index);
	}
	
	public void addDateIndex(Calendar date, int index) {
		datemap.put(date,  index);
		dates[index] = date;
	}
	
	public Calendar getDateByIndex(int index) {
		return dates[index];
	}
	
	public void addExtraData(String key, Object data) {
		extraData.put(key, data);
	}
	
	public Object getExtraData(String key) {
		return extraData.get(key);
	}
}

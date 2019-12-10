package nl.tudelft.alg.fcc.model;

import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.problem.FlexibleLoadProblem;
import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.utils.Utils;

public class Market {
	double ptu; //PTU size (in percentage of an hour) 
	PriceScenarioData pricedata;
	PerMinuteData perMinuteData;
	FlexibleLoadProblem problem;
	
	public Market(Config config, PriceScenarioData pricedata, PerMinuteData perMinuteData) {
		Utils.copyMatchingFields(config, this);
		this.pricedata = pricedata;
		this.perMinuteData = perMinuteData;
	}
	
	public Market(Config config, Market old) {
		this(config, old.pricedata.clone(), old.perMinuteData);
	}

	public Market(Market market, PriceScenarioData priceData) {
		Utils.copyMatchingFields(market, this);
		this.perMinuteData = market.perMinuteData;
		this.problem = market.problem;
		this.pricedata = priceData;
	}

	public void setProblem(FlexibleLoadProblem problem) {
		this.problem = problem;
	}
	
	public void setPriceData(PriceScenarioData data) {
		pricedata = data;
	}

	public PriceScenarioData getPricedata() {
		return pricedata;
	}

	public PerMinuteData getPerMinuteData() {
		return perMinuteData;
	}

	public int getNScenarios() {
		return pricedata.getNScenarios();
	}
	
	public int getNTimeSteps() {
		return problem.getNTimeSteps();
	}
	
	public int getStartT() {
		return problem.getStartT();
	}
	
	public int getEndT() {
		return getStartT() + getNTimeSteps();
	}
	
	public boolean hasCapacityPayments() {
		return problem.getConfig().hasCapacityPayments();
	}
	
	public int getFixedPTUs() {
		return problem.getConfig().getFixedPTUs();
	}
	public boolean considerImbalance() {
		return problem.getConfig().considerImbalance();
	}
	public boolean hasDayAhead() {
		return problem.getConfig().hasDayAhead();
	}
	public boolean isCapacityMarket() {
		return problem.getConfig().isCapacityMarket();
	}
	public boolean hasReserves() {
		return problem.getConfig().hasReserves();
	}
	public double getMinBid() {
		return problem.getConfig().getMinBid();
	}
	
	public void ignoreReserves() {
		problem.getConfig().ignoreReserves();
	}
	
	public void ignoreDayAhead() {
		problem.getConfig().ignoreDayAhead();
	}
	
	public void fixDayAhead() {
		problem.getConfig().fixDayAhead();
	}
	
	public boolean isDayAheadFixed() {
		return problem.getConfig().isDayAheadFixed();
	}
	
	public String getReservesMarketClearance() {
		return problem.getConfig().getReservesMarketClearance();
	}

	public double getDownReserveProportion(int t, int i) {
		return pricedata.getProportionDownUsed(getStartT() + t, i);
	}
	
	public double getExpectedDownReserveProportion( int t) {
		return pricedata.getExpectedProportionDownUsed(getStartT() + t);
	}

	public double getUpReserveProportion(int t, int i) {
		return pricedata.getProportionUpUsed(getStartT() + t, i);
		//TODO this value is set to zero to ignore upward reserves in the model
	}
	
	public double getExpectedUpReserveProportion(int t) {
		return pricedata.getExpectedProportionUpUsed(getStartT() + t);
	}
	
	public double getDownRegulatingPrice(int t, int i) {
		return pricedata.getDownPrice(getStartT() + t, i);
	}
	
	public double getExpectedDownRegulatingPrice(int t) {
		return pricedata.getExpectedDownPrice(getStartT() + t);
	}
	
	public double getUpRegulatingPrice(int t, int i) {
		return pricedata.getUpPrice(getStartT() + t, i);
	}
	
	public double getExpectedUpRegulatingPrice(int t) {
		return pricedata.getExpectedUpPrice(getStartT() + t);
	}
	
	public double getExpectedDownRegulatingPriceBelow(int t, double p) {
		double result = 0;
		double totalProb = 0;
		for(int i=0; i<getNScenarios(); i++) {
			if(getDownRegulatingPrice(t, i) <= p) {
				result += getScenarioProbability(i) * getDownRegulatingPrice(t, i);
				totalProb += getScenarioProbability(i);
			}
		}
		if(totalProb == 0) return p;
		return result / totalProb;
	}
	
	public double getExpectedUpRegulatingPriceAbove(int t, double p) {
		double result = 0;
		double totalProb = 0;
		for(int i=0; i<getNScenarios(); i++) {
			if(getUpRegulatingPrice(t, i) >= p) {
				result += getScenarioProbability(i) * getUpRegulatingPrice(t, i);
				totalProb += getScenarioProbability(i);
			}
		}
		if(totalProb == 0) return p;
		return result / totalProb;
	}
	
	public double getExpectedDownCapacityPaymentAbove(int t, double p) {
		double result = 0;
		double totalProb = 0;
		for(int i=0; i<getNScenarios(); i++) {
			if(getDownCapacityPayment(t, i) >= p) {
				result += getScenarioProbability(i) * getDownCapacityPayment(t, i);
				totalProb += getScenarioProbability(i);
			}
		}
		if(totalProb == 0) return p;
		return result / totalProb;
	}
	
	public double getExpectedUpCapacityPaymentAbove(int t, double p) {
		double result = 0;
		double totalProb = 0;
		for(int i=0; i<getNScenarios(); i++) {
			if(getUpCapacityPayment(t, i) >= p) {
				result += getScenarioProbability(i) * getUpCapacityPayment(t, i);
				totalProb += getScenarioProbability(i);
			}
		}
		if(totalProb == 0) return p;
		return result / totalProb;
	}
	
	public double getExpectedImbalancePriceAboveDown(int t, double p) {
		double result = 0;
		double totalProb = 0;
		for(int i=0; i<getNScenarios(); i++) {
			if(getDownCapacityPayment(t, i) >= p) {
				result += getScenarioProbability(i) * getImbalancePrice(t, i);
				totalProb += getScenarioProbability(i);
			}
		}
		if(totalProb == 0) return p;
		return result / totalProb;
	}
	
	public double getExpectedImbalancePriceAboveUp(int t, double p) {
		double result = 0;
		double totalProb = 0;
		for(int i=0; i<getNScenarios(); i++) {
			if(getUpCapacityPayment(t, i) >= p) {
				result += getScenarioProbability(i) * getImbalancePrice(t, i);
				totalProb += getScenarioProbability(i);
			}
		}
		if(totalProb == 0) return p;
		return result / totalProb;
	}
	
	public double getExpectedImbalancePrice(int t) {
		return pricedata.getExpectedImbalancePrice(getStartT() + t);
	}
	
	public double getImbalancePrice(int t, int i) {
		return pricedata.getImbalancePrice(getStartT() + t, i);
	}
	
	public double getDownCapacityPayment(int t, int i) {
		if(!hasCapacityPayments()) return 0.0;
		return pricedata.getCapDownPrice(getStartT() + t, i); 
	}

	public double getExpectedDownCapacityPayment(int t) {
		if(!hasCapacityPayments()) return 0.0;
		return pricedata.getExpectedCapDownPrice(getStartT() + t);
	}
	
	public double getUpCapacityPayment(int t, int i) {
		if(!hasCapacityPayments()) return 0.0;
		return pricedata.getCapUpPrice(getStartT() + t, i); 
	}
	
	public double getExpectedUpCapacityPayment(int t) {
		if(!hasCapacityPayments()) return 0.0;
		return pricedata.getExpectedCapUpPrice(getStartT() + t);
	}
	
	public double getPTU() {
		return ptu; // percentage of an hour
	}

	public void setPTU(double ptu) {
		this.ptu = ptu;
	}
	
	public double getDAPrice(int t) {
		return pricedata.getDAPrice(t + getStartT());
	}
	
	public double getDAPriceByHour(int h) {
		return getDAPrice(HtoPTU(h));
	}

	public int PTUtoH(int t) {
		return (int) (Math.floor((t + getStartT()) * getPTU()) - Math.floor(getStartT() * getPTU()));
	}
	
	public int HtoPTU(int h) {
		return (int) Math.floor(h / getPTU());
	}
	
	public int HtoFirstPTU(int h) {
		return (int) Math.floor(h / getPTU()) - (getStartT() % ((int) Math.floor((1 / getPTU()))));
	}
	
	public int getNumberOfHours() {
		return PTUtoH(getNTimeSteps()-1)+1;
	}
	
	public double getScenarioProbability(int i) {
		return pricedata.getScenarioProbability(i);
	}
	
	//Ordered from  high to low
	public int[] getScenariosOrderedByDownRegulatingPrice(int t) {
		return pricedata.getScenariosOrderedByDownPrice(getStartT() + t);
	}
	
	//Ordered from  high to low
	public int[] getScenariosOrderedByUpRegulatingPrice(int t) {
		return pricedata.getScenariosOrderedByUpPrice(getStartT() + t);
	}
	
	
	//Ordered from  high to low
	public int[] getScenariosOrderedByDownCapacityPayment(int t) {
		return pricedata.getScenariosOrderedByDownCap(getStartT() + t);
	}
	
	//Ordered from  high to low
	public int[] getScenariosOrderedByUpCapacityPayment(int t) {
		return pricedata.getScenariosOrderedByUpCap(getStartT() + t);
	}

	public double getSuperiorDownRegulatingPrice(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getDownRegulatingPrice(t,i)).max().orElse(0.0) + 1e-3;
	}
	
	public double getSuperiorUpRegulatingPrice(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getUpRegulatingPrice(t,i)).max().orElse(0.0) + 1e-3;
	}
	
	public double getInferiorDownRegulatingPrice(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getDownRegulatingPrice(t,i)).min().orElse(0.0) - 1e-3;
	}
	
	public double getInferiorUpRegulatingPrice(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getUpRegulatingPrice(t,i)).min().orElse(0.0) - 1e-3;
	}
	

	public double getSuperiorDownCapacityPayment(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getDownCapacityPayment(t,i)).max().orElse(0.0) + 1e-3;
	}
	
	public double getSuperiorUpCapacityPayment(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getUpCapacityPayment(t,i)).max().orElse(0.0) + 1e-3;
	}
	
	public double getInferiorDownCapacityPayment(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getDownCapacityPayment(t,i)).min().orElse(0.0) - 1e-3;
	}
	
	public double getInferiorUpCapacityPayment(int t) {
		return IntStream.range(0, getNScenarios()).mapToDouble(i -> getUpCapacityPayment(t,i)).min().orElse(0.0) - 1e-3;
	}
	
	public Line[] getAPdown(int t) {
		return pricedata.APdown[t+getStartT()];
	}
	public Line[] getAPup(int t) {
		return pricedata.APup[t+getStartT()];
	}
	public Line[] getERdown(int t) {
		return pricedata.ERdown[t+getStartT()];
	}
	public Line[] getERup(int t) {
		return pricedata.ERup[t+getStartT()];
	}
	
	public int getAPpieces() {
		return pricedata.getAPpieces();
	}
	
	public int getERpieces() {
		return pricedata.getERpieces();
	}

	public int getNPTUs() {
		return getNTimeSteps();
	}

	public int getResTimesteps() {
		return getNTimeSteps();
	}

	public int getDATimesteps() {
		return getNumberOfHours();
	}

	public double getImbDownObjPrice(int t) {
		if(hasCapacityPayments())
			return getExpectedImbalancePrice(t);
		return getExpectedDownRegulatingPrice(t);
	}

	public double getImbUpObjPrice(int t) {
		if(hasCapacityPayments())
			return getExpectedImbalancePrice(t);
		return getExpectedUpRegulatingPrice(t);
	}

	public double getExpectedUpCapacityPaymentByBid(int t, double upPrice) {
		if(hasCapacityPayments())
			return getExpectedUpCapacityPaymentAbove(t, upPrice);
		return 0;
	}

	public double getExpectedDownCapacityPaymentByBid(int t, double downPrice) {
		if(hasCapacityPayments())
			return getExpectedDownCapacityPaymentAbove(t, downPrice);
		return 0;
	}

	public double getImbUpObjPriceByBid(int t, double upPrice) {
		if(hasCapacityPayments()) {
			return getExpectedImbalancePriceAboveUp(t, upPrice);
		}
		return getExpectedUpRegulatingPriceAbove(t, upPrice);
	}

	public double getImbDownObjPriceByBid(int t, double downPrice) {
		if(hasCapacityPayments()) {
			return getExpectedImbalancePriceAboveDown(t, downPrice);
		}
		return getExpectedDownRegulatingPriceBelow(t, downPrice);
	}

}

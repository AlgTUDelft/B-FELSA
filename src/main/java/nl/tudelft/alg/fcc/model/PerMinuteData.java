package nl.tudelft.alg.fcc.model;

public class PerMinuteData extends PriceData {
	double[] downprice, upprice, upreg, downreg, midprice, imbprice, mindownprice, maxupprice;
	final int ptuLength;
	
	public PerMinuteData(int nTimeSteps, int ptuLength) {
		super(nTimeSteps);
		downprice = new double[nTimeSteps];
		upprice = new double[nTimeSteps];
		upreg = new double[nTimeSteps];
		downreg = new double[nTimeSteps];
		midprice = new double[nTimeSteps];
		imbprice = new double[nTimeSteps / ptuLength];
		mindownprice = new double[nTimeSteps / ptuLength];
		maxupprice = new double[nTimeSteps / ptuLength];
		this.ptuLength = ptuLength;
	}
	
	public void setDownRegulatingPrice(int t, double p) {
		downprice[t] = p;
	}
	
	public void setUpRegulatingPrice(int t, double p) {
		upprice[t] = p;
	}
	
	public void setDownRegulatingVolume(int t, double v) {
		downreg[t] = v;
	}
	
	public void setUpRegulatingVolume(int t, double v) {
		downreg[t] = v;
	}
	
	public void setMidPrice(int t, double p) {
		midprice[t] = p;
	}
	
	public void setDerivedPrices(int t) {
		int T = t / ptuLength - 1;
		int state = 0;
		double maxUp = - Double.MAX_VALUE;
		double minDown = Double.MAX_VALUE;
		for (int i = t - ptuLength; i < t; i++) {
			if(getDownRegulatingPrice(i) < 10e6) {
				if(state == 1) state =  2;
				if(state == 0) state = -1;
				if(getDownRegulatingPrice(i) < minDown) minDown = getDownRegulatingPrice(i);
			}
			if(getUpRegulatingPrice(i) > -10e6) {
					if(state == -1) state = 2;
					if(state == 0) state  = 1;
					if(getUpRegulatingPrice(i) > maxUp) maxUp = getUpRegulatingPrice(i);
			}
		}
		double p;
		double midprice = getMidPrice(t - ptuLength);
		if(state == 0) p = midprice;
		else if(state == -1) p = minDown;
		else if(state == 1) p = maxUp;
		else p = Math.max(maxUp, midprice);//assuming only charging
		imbprice[T] = p;
		mindownprice[T] = minDown;
		maxupprice[T] = maxUp;
	}
	
	public double getDownRegulatingPrice(int t) {
		return downprice[t];
	}
	
	public double getUpRegulatingPrice(int t) {
		return upprice[t];
	}
	
	public double getDownRegulatingVolume(int t) {
		return downreg[t];
	}
	
	public double getUpRegulatingVolume(int t) {
		return upreg[t];
	}
	
	public double getMidPrice(int t) {
		return midprice[t];
	}
	
	public double getImbalancePrice(int T) {
		return imbprice[T];
	}
	
	public double getMaxUpRegulatingPrice(int T) {
		return maxupprice[T];
	}
	
	public double getMinDownRegulatingPrice(int T) {
		return mindownprice[T];
	}

	public int getNTimeSteps() {
		return midprice.length;
	}

	public int getNPTUs() {
		return imbprice.length;
	}

	public int getPTULength() {
		return ptuLength;
	}

}

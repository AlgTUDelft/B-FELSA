package nl.tudelft.alg.fcc.simulator.data;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.simulator.InvalidConfigurationException;
import nl.tudelft.alg.fcc.utils.Utils;

public class HistoricGradientDataGenerator extends DataGenerator {
	Random random;
	
	public HistoricGradientDataGenerator(Random random) {
		this.random = random;
	}
	
	@Override
	public PriceScenarioData generate(Config config, int offset, Data real, Data distr, int nScenarios) throws InvalidConfigurationException {
		PriceScenarioData dData = distr.getMarket().getPricedata();
		PriceScenarioData rData = real.getMarket().getPricedata();
		int nRealScenarios = rData.getNScenarios();
		PriceScenarioData nData = dData.clone();
		int startT = real.getLoads().getFirstT() + offset;
		int endT = real.getLoads().getLastT();
		String[] gradient = config.gradient.split(" ");
		double param1 = Double.parseDouble(gradient[0]);
		double param2 = Double.parseDouble(gradient[1]);
		boolean param3 = (gradient.length > 2 && gradient[2].equals("inv")); // prices in the future are more certain. Real time prices are uncertain
		boolean param4 = (gradient.length > 2 && gradient[2].equals("unc")); // prices real time are uncertain, and this grows in the future
		double[] weights = IntStream.range(0, endT - startT)
				.mapToDouble(i -> param1 / Math.pow(i + 1, param2))
				.toArray();

		for (int t = startT; t < endT; t++) {
			final int _t = t;
			double rw = (param4 ? -1 : 0) + (param3 ? -1 : 1) * weights[t - startT];
			double std = Math.sqrt(IntStream.range(0, nScenarios).mapToDouble(i ->
			Math.pow(dData.getExpectedImbalancePrice(_t) - dData.getImbalancePrice(_t, i), 2)).sum() / nScenarios);
			double mu = dData.getExpectedImbalancePrice(_t);
			double[] probs = IntStream.range(0, nScenarios).mapToDouble(i ->
			getProbability(mu, std, dData.getImbalancePrice(_t, i), 
					rData.getImbalancePrice(_t, i % nRealScenarios))).toArray();
			double[] da_probs = IntStream.range(0, nScenarios).mapToDouble(i -> getProbability(mu, std,
					dData.getImbalancePrice(_t, i), dData.getDAPrice(_t))).toArray();
			probs = Arrays.stream(probs).map(a -> Math.pow(a, 1.0/Math.abs(rw))).toArray();
			da_probs = Arrays.stream(da_probs).map(a -> Math.pow(a, 1.0/Math.abs(rw))).toArray();
			//double prob = probs[i] * Math.abs(rw);
			//double da_prob = da_probs[i] * Math.abs(rw);
			
			double[] neg_probs = Arrays.stream(probs).map(d -> 1 - d).toArray();
			double[] neg_da_probs = Arrays.stream(da_probs).map(d -> 1 - d).toArray();
			for (int i = 0; i < nScenarios; i++) {
				int scenario = i;
				double noise = random.nextGaussian() * std * (1-Math.abs(rw));
				if(rw < 0 && random.nextDouble() < da_probs[i])
					scenario = Utils.weightedChoice(random, neg_da_probs);
				else if (rw >= 0 && random.nextDouble() < probs[i])
					scenario = Utils.weightedChoice(random, neg_probs);
				else noise = 0;

				nData.setImbalancePrice(t, i, dData.getImbalancePrice(t, scenario) + noise);
				nData.setDownPrice(t, i, dData.getDownPrice(t, scenario) + noise);
				nData.setUpPrice(t, i, dData.getUpPrice(t, scenario) + noise);
				nData.setProportionDownUsed(t, i, dData.getProportionDownUsed(t, scenario));
				nData.setProportionUpUsed(t, i, dData.getProportionUpUsed(t, scenario));
				nData.setCapDownPrice(t, i, dData.getCapDownPrice(t, scenario));
				nData.setCapUpPrice(t, i, dData.getCapUpPrice(t, scenario));
			}
		}
		nData.calcExpected();
		nData.reset();
		return nData;
	}

	private static double getProbability(double mu, double std, double sc, double real) {
		if (std == 0) return 0;
		double zs_r = (real - mu) / std;
		double zs_s = (sc - mu) / std;
		double div = zs_r;
	   if(zs_r == 0 && zs_s == 0) return 0;
	   if(zs_r == 0) div = zs_s;
		//return Math.abs(Math.tanh(Math.abs(zs_r - zs_s) / div));
	   return Math.tanh(Math.abs(zs_r - zs_s));
	}

	

	public static PriceScenarioData selectFromScenariosOld(Config config, int offset, Data real, Data distr) {
		PriceScenarioData dData = distr.getMarket().getPricedata();
		PriceScenarioData rData = real.getMarket().getPricedata();
		int nScenarios = dData.getNScenarios();
		int nRealScenarios = rData.getNScenarios();
		PriceScenarioData nData = dData.clone();
		int startT = real.getLoads().getFirstT() + offset;
		int endT = real.getLoads().getLastT();
		String[] gradient = config.gradient.split(" ");
		double param1 = Double.parseDouble(gradient[0]);
		double param2 = Double.parseDouble(gradient[1]);
		boolean param3 = (gradient.length > 2 && gradient[2].equals("inv")); // prices in the future are more certain. Real time prices are uncertain
		boolean param4 = (gradient.length > 2 && gradient[2].equals("unc")); // prices real time are uncertain, and this grows in the future
		double[] weights = IntStream.range(0, endT - startT)
				.mapToDouble(i -> param1 / Math.pow(i + 1, param2))
				.toArray();

		for (int t = startT; t < endT; t++) {
			for (int i = 0; i < nScenarios; i++) {
				double rw = (param4 ? -1 : 0) + (param3 ? -1 : 1) * weights[t - startT];
				double nw = 1.0 - rw;
				//First update the Variance
				nData.setImbalancePrice(t, i,
						dData.getExpectedImbalancePrice(t) * rw + dData.getImbalancePrice(t, i) * nw);
				nData.setDownPrice(t, i, dData.getExpectedDownPrice(t) * rw + dData.getDownPrice(t, i) * nw);
				nData.setUpPrice(t, i, dData.getExpectedUpPrice(t) * rw + dData.getUpPrice(t, i) * nw);
				nData.setProportionDownUsed(t, i,
						dData.getExpectedProportionDownUsed(t) * rw + dData.getProportionDownUsed(t, i) * nw);
				nData.setProportionUpUsed(t, i,
						dData.getExpectedProportionUpUsed(t) * rw + dData.getProportionUpUsed(t, i) * nw);
				nData.setCapDownPrice(t, i,
						dData.getExpectedCapDownPrice(t) * rw + dData.getCapDownPrice(t, i) * nw);
				nData.setCapUpPrice(t, i, rData.getExpectedCapUpPrice(t) * rw + dData.getCapUpPrice(t, i) * nw);

				//Then translate in the direction of the real, or in the direction of DA
				double down = (rw < 0 ? dData.getDAPrice(t) : rData.getDownPrice(t, i % nRealScenarios));
				double up = (rw < 0 ? dData.getDAPrice(t) : rData.getUpPrice(t, i % nRealScenarios));
				double imb = (rw < 0 ? dData.getDAPrice(t) : rData.getImbalancePrice(t, i % nRealScenarios));
				double ptu_down = (rw < 0 ? 0.5 : rData.getProportionDownUsed(t, i % nRealScenarios));
				double ptu_up = (rw < 0 ? 0.5 : rData.getProportionUpUsed(t, i % nRealScenarios));
				if (rw < 0) rw = -rw;
				nData.setImbalancePrice(t, i,
						nData.getImbalancePrice(t, i) + rw * (imb - nData.getExpectedImbalancePrice(t)));
				nData.setDownPrice(t, i, nData.getDownPrice(t, i) + rw * (down - nData.getExpectedDownPrice(t)));
				nData.setUpPrice(t, i, nData.getUpPrice(t, i) + rw * (up - nData.getExpectedUpPrice(t)));
				nData.setProportionDownUsed(t, i, nData.getProportionDownUsed(t, i) + rw * (ptu_down - nData.getExpectedProportionDownUsed(t)));
				nData.setProportionUpUsed(t, i, nData.getProportionUpUsed(t, i) + rw * (ptu_up - nData.getExpectedProportionUpUsed(t)));

				//TODO
				//nData.setCapDownPrice(t, i,
				//		dData.getExpectedCapDownPrice(t) * rw + dData.getCapDownPrice(t, i) * nw);
				//nData.setCapUpPrice(t, i, rData.getExpectedCapUpPrice(t) * rw + dData.getCapUpPrice(t, i) * nw);

			}
		}
		nData.calcExpected();
		nData.reset();
		return nData;
	}



}

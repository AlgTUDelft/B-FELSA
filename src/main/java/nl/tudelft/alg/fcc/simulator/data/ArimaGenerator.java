package nl.tudelft.alg.fcc.simulator.data;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.matrix.store.SparseStore;

import nl.tudelft.alg.fcc.model.PriceScenarioData;
import nl.tudelft.alg.fcc.simulator.Config;
import nl.tudelft.alg.fcc.simulator.InvalidConfigurationException;
import nl.tudelft.alg.fcc.utils.Utils;

public class ArimaGenerator extends DataGenerator {
	Random random;
		
	public ArimaGenerator(Random random) {
		super();
		this.random = random;
	}
	
	@Override
	public PriceScenarioData generate(Config config, int offset, Data real, Data distr, int nResultScenarios) throws InvalidConfigurationException {
		PriceScenarioData dData = distr.getMarket().getPricedata();
		PriceScenarioData rData = real.getMarket().getNScenarios() <=  nResultScenarios 
				? real.getMarket().getPricedata() 
				: real.getMarket().getPricedata().getRandomSubset(nResultScenarios, random);
		int nRealScenarios = rData.getNScenarios();
		int startT = real.getLoads().getFirstT() + Math.max(0, offset);
		int baseT = Math.max(0, startT - (int)((24.0 / config.ptu) * 14.0));
		int endT = real.getLoads().getLastT();
		PriceScenarioData nData = new PriceScenarioData(dData.getNTimeSteps(), nResultScenarios);
		//fill in the other values 
		for (int t = startT; t < endT; t++) {
			nData.setDAPrice(t, rData.getDAPrice(t));
		}
		for (int i = 0; i < nResultScenarios; i++) {
			nData.setScenarioProbability(i, 1.0 / nResultScenarios);
		}		
		
		// Fill the new data with the realized data up to 'now'
		double[][] downprice = new double[endT-baseT][nResultScenarios];
		double[][] upprice = new double[endT-baseT][nResultScenarios];
		double[][] downptu = new double[endT-baseT][nResultScenarios];
		double[][] upptu = new double[endT-baseT][nResultScenarios];
		
		double[] maxdown = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> IntStream.range(baseT, rData.getNTimeSteps()).mapToDouble(t -> rData.getDownPrice(t, i)).max().getAsDouble()).toArray();
		double[] minup = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> IntStream.range(baseT, rData.getNTimeSteps()).mapToDouble(t -> rData.getUpPrice(t, i)).min().getAsDouble()).toArray();
		
		final double probr = 1.0;
		final double probd = 1.0 - probr;
	
		calculcateLambdas(rData, maxdown, minup);
		double[] lambda1down = (double[]) rData.getExtraData("ld1");
		double[] lambda2down = (double[]) rData.getExtraData("ld2");
		double[] lambda1up = (double[]) rData.getExtraData("lu1");
		double[] lambda2up = (double[]) rData.getExtraData("lu2");

		for (int t=baseT; t<dData.getNTimeSteps(); t++) { 
			for (int i = 0; i < nResultScenarios; i++) {
				nData.setDownPrice(t, i, probd * dData.getExpectedDownPrice(t) + probr * rData.getDownPrice(t, i % nRealScenarios));
				nData.setUpPrice(t, i, probd * dData.getExpectedUpPrice(t) + probr * rData.getUpPrice(t, i % nRealScenarios));
				nData.setImbalancePrice(t, i, probd * dData.getExpectedImbalancePrice(t) + probr * rData.getImbalancePrice(t, i % nRealScenarios));
				nData.setProportionDownUsed(t, i, probd * dData.getExpectedProportionDownUsed(t) + probr * rData.getProportionDownUsed(t, i % nRealScenarios));
				nData.setProportionUpUsed(t, i, probd * dData.getExpectedProportionUpUsed(t) + probr * rData.getProportionUpUsed(t, i % nRealScenarios));
				if(t<endT) {
   				downprice[t-baseT][i] = transform(nData.getDownPrice(t, i), maxdown[i % nRealScenarios], lambda1down[i % nRealScenarios], lambda2down[i % nRealScenarios]);
   				upprice[t-baseT][i] = transform(nData.getUpPrice(t, i), minup[i % nRealScenarios], lambda1up[i % nRealScenarios], lambda2up[i % nRealScenarios]);
   				downptu[t-baseT][i] = nData.getProportionDownUsed(t, i % nData.getNScenarios());
   				upptu[t-baseT][i] = nData.getProportionUpUsed(t, i % nData.getNScenarios());
				}
			}
		}
		
		double downlimit = transform(-250, Utils.max(maxdown), lambda1down[0], lambda2down[0]);
		double uplimit = transform(250, Utils.min(minup), lambda1up[0], lambda2up[0]);
		forecastArima(config.downConfig, downprice, config.scenarioFactor, startT-baseT, downlimit, nRealScenarios);
		forecastArima(config.upConfig, upprice, config.scenarioFactor, startT-baseT, uplimit, nRealScenarios);
		MarkovPTUGenerator.generate(config.downConfig.markovConfig, downptu, startT, startT-baseT, rData.getNTimeSteps(), 
				config.downConfig.PTUsPerDay, random, config.scenarioFactor);
		MarkovPTUGenerator.generate(config.upConfig.markovConfig, upptu, startT, startT-baseT, rData.getNTimeSteps(),
				config.upConfig.PTUsPerDay, random, config.scenarioFactor);
		
		for (int i = 0; i < nResultScenarios; i++) {
			nData.setScenarioProbability(i, 1.0/nResultScenarios);
			for (int t = startT; t < endT; t++) {
				double dp = detransform(downprice[t-baseT][i], -1, maxdown[i % nRealScenarios], lambda1down[i % nRealScenarios], lambda2down[i % nRealScenarios]);
				double up = detransform(upprice[t-baseT][i], 1, minup[i % nRealScenarios], lambda1up[i % nRealScenarios], lambda2up[i % nRealScenarios]);
				if(dp < -1000) dp = -1000;
				if(up > 1000) up = 1000;
				nData.setDownPrice(t, i, dp);
				nData.setUpPrice(t, i, up);
				nData.setProportionDownUsed(t, i, downptu[t-baseT][i]);
				nData.setProportionUpUsed(t, i, upptu[t-baseT][i]);
				if (nData.getProportionDownUsed(t, i) < nData.getProportionUpUsed(t, i))
					nData.setImbalancePrice(t, i, up);
				else
					nData.setImbalancePrice(t, i, dp);
			}
		}		
		nData.calcExpected(startT, endT);
		return nData;
	}
	
	private void calculcateLambdas(PriceScenarioData rData, double[] maxdown, double[] minup) {
		// Use all data to estimate lambda parameters
		if(rData.getExtraData("ld1") == null || rData.getExtraData("ld2") == null) {
   		// Find the distance of every price to the maximum down price in that scenario
   		double[][] __alldown = IntStream.range(0, rData.getNScenarios()).mapToObj(i -> IntStream.range(0, rData.getNTimeSteps()).mapToDouble(t -> Math.abs(maxdown[i] - rData.getDownPrice(t, i)) + 1e-5).toArray()).toArray(double[][]::new);
   		// Find the left bound mu-0.75*std
   		double[] leftbounddown = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> Utils.avg(__alldown[i]) - 0.75 * Utils.std(__alldown[i])).toArray();		
   		// All values lower than the leftbound are set to the leftbound
   		double[][] _alldown = IntStream.range(0, rData.getNScenarios()).mapToObj(i -> IntStream.range(0, rData.getNTimeSteps()).mapToDouble(t -> Math.max(__alldown[i][t], leftbounddown[i])).toArray()).toArray(double[][]::new);
   		// Use the findLambda procedures to find the BoxCox parameters
   		double[] lambda2down = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> findLambda2(_alldown[i])).toArray();
   		double[][] alldown = IntStream.range(0, rData.getNScenarios()).mapToObj(i -> IntStream.range(0, rData.getNTimeSteps()).mapToDouble(t -> _alldown[i][t] + lambda2down[i]).toArray()).toArray(double[][]::new);
   		double[] lambda1down = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> findLambda1(alldown[i])).toArray();
   		rData.addExtraData("ld1", lambda1down);
   		rData.addExtraData("ld2", lambda2down);
		}
		if(rData.getExtraData("lu1") == null || rData.getExtraData("lu2") == null) {
			// Find the distance of every price to the minimum up price in that scenario
			double[][] __allup = IntStream.range(0, rData.getNScenarios()).mapToObj(i -> IntStream.range(0, rData.getNTimeSteps()).mapToDouble(t -> Math.abs(minup[i] - rData.getUpPrice(t, i)) + 1e-5).toArray()).toArray(double[][]::new);
			// Find the left bound mu-0.75*std
			double[] leftboundup = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> Utils.avg(__allup[i]) - 0.75 * Utils.std(__allup[i])).toArray();
			// All values lower than the leftbound are set to the leftbound
			double[][] _allup = IntStream.range(0, rData.getNScenarios()).mapToObj(i -> IntStream.range(0, rData.getNTimeSteps()).mapToDouble(t -> Math.max(__allup[i][t], leftboundup[i])).toArray()).toArray(double[][]::new);
			// Use the findLambda procedures to find the BoxCox parameters
			double[] lambda2up = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> findLambda2(_allup[i])).toArray();
   		double[][] allup = IntStream.range(0, rData.getNScenarios()).mapToObj(i -> IntStream.range(0, rData.getNTimeSteps()).mapToDouble(t -> _allup[i][t] + lambda2up[i]).toArray()).toArray(double[][]::new);
   		double[] lambda1up = IntStream.range(0, rData.getNScenarios()).mapToDouble(i -> findLambda1(allup[i])).toArray();
   		rData.addExtraData("lu1", lambda1up);
   		rData.addExtraData("lu2", lambda2up);
		}
	}
	
	/**
	 * Find the optimal BoxCox parameter for values
	 * See Box, G. E. P. and Cox, D. R. (1964). An analysis of transformations, Journal of the Royal Statistical Society, Series B, 26, 211-252
	 * @param values the series to find the Box-Cox parameter for
	 * @return the Box-Cox parameter
	 */
	private double findLambda1(double[] values) {
		BrentOptimizer solver = new BrentOptimizer(1e-10, 1e-14);
      return solver.optimize(new MaxEval(100),
            new BoxCoxLLF(values),
            GoalType.MINIMIZE,
            new SearchInterval(-5, 5)).getPoint();
	}
	
	private double findLambda2(double[] values) {
		return 0;
	}
	
	/**
	 * Transform the data by 
	 * 1) calculating the distance from the value to the maximum value
	 * 2) performing the Box-Cox transformation
	 * @param value the original value
	 * @param max the maximum value in the original series
	 * @param lambda1 the first Box-Cox parameter
	 * @param lambda2 the second Box-Cox parameter
	 * @return the transformed value
	 */
	private double transform(double value, double max, double lambda1, double lambda2) {
		value = Math.abs(max-value) + 1e-5;
   	//Do the BoxCox Transform
   	assert value > 0;
   	if(Math.abs(lambda1) < 1e-4)
   		value = Math.log(value + lambda2);
   	else 
   		value =  (Math.pow(value + lambda2, lambda1) - 1.0) / lambda1;					
   	return value;
   }

	/**
	 * Detransform the data by 
	 * 1) performing the reversed Box-Cox transformation
	 * 2) Adding the value to the maximum value of the original series
	 * @param value the original value
	 * @param sign sign is used to indicate if the value should be added or subtracted from the maximum value
	 * @param max the maximum value in the original series
	 * @param lambda1 the first Box-Cox parameter
	 * @param lambda2 the second Box-Cox parameter
	 * @return the detransformed value
	 */
	private double detransform(double value, int sign, double max, double lambda1, double lambda2) {
		double rValue = value;
		//Do the inverse BoxCox Transform
		if(Math.abs(lambda1) < 1e-4)
			rValue = Math.exp(rValue) - lambda2;
		else {
			double bound = 1.0/(-lambda1);
			if(lambda1 > 0) rValue = Math.max(bound * (1+1e-9), rValue);
			else rValue = Math.min(bound * (1-1e-9), rValue);
			rValue = Math.pow(rValue * lambda1 + 1, 1.0/lambda1) - lambda2;
		}
		if (Double.isNaN(rValue)) {
			System.out.println("");
			System.out.println("-- error --");
			System.out.println("value: "+ value);
			System.out.println("lambda: " +lambda1);
			System.out.println("*: " +((value) * lambda1));
			System.out.println("res: " +Math.pow(Math.max(0, value) * lambda1 + 1, 1.0/lambda1));
			System.out.println("");
		}
		assert !Double.isNaN(rValue);
		return max + sign*rValue;
	}

	/**
	 * Find the difference series
	 * @param data the original series data
	 * @param startT the time step to begin at
	 * @param S the distance between the two points (normally 1, but for seasonal differencing set to the season length)
	 * @param knownSteps the amount of steps at the beginning of the series which are known and should not be differenced
	 * @return the difference series
	 */
	private double[][] difference(double[][] data, int startT, int S, int knownSteps) {
		for (int t = startT - 1; t >= knownSteps; t--) {
			for (int i = 0; i < data[t].length; i++) {
				data[t][i] = data[t][i] - data[t - S][i];
			}
		}
		return data;
	}

	/**
	 * Reverse the differencing and find the original series
	 * @param data the differenced series
	 * @param endT the last time step of the resulting series
	 * @param S the distance between the two points (normally 1, but for seasonal differencing set to the season length)
	 * @param knownSteps the amount of steps at the beginning of the series which are known and are not differenced
	 * @return the original series which is not differenced
	 */
	private double[][] inverseDifference(double[][] data, int endT, int S, int knownSteps) {
		for (int t = knownSteps; t < endT; t++) {
			for (int i = 0; i < data[t].length; i++) {
				data[t][i] = data[t][i] + data[t - S][i];
			}
		}
		return data;
	}
	
	/**
	 * Remove the seasonal component by use of external regressors
	 * @param data the original data
	 * @param startT the beginning of the time series to be generated
	 * @param c the ARIMA configuration
	 */
	private void removeSeasonalXComponent(double[][] data, int startT, ArimaConfig c) {
		if(c.xreg.length == 0) return;
		for(int t=0; t<startT; t++) {
			for (int i = 0; i < data[t].length; i++) {
					data[t][i] -= c.xreg[(t / (c.PTUsPerDay / c.xreg.length)) % c.xreg.length];
			}
		}
	}
	
	/**
	 * Add the seasonal component by use of external regressors
	 * @param data the original data
	 * @param c the ARIMA configuration
	 */
	private void addSeasonalXComponent(double[][] data, ArimaConfig c) {
		//Add seasonal component by use of xreg
		if(c.xreg.length == 0) return;
		for(int t=0; t<data.length; t++) {
			for (int i = 0; i < data[t].length; i++) {
					data[t][i] += c.xreg[(t / (c.PTUsPerDay / c.xreg.length)) % c.xreg.length];
			}
		}
	}

	/**
	 * Create a scenario by forecasting according to an ARIMA configuration
	 * @param config the ARIMA configuration
	 * @param data the data to be transformed
	 * @param scenarioFactor the ratio of scenarios to be generated in comparison to the number of scenarios to return
	 * @param startT the beginning of the resulting time series
	 * @param limit the extreme values to use as cut-off
	 * @param nRealScenarios the number of scenarios in the original data
	 * @throws InvalidConfigurationException
	 */
	protected void forecastArima(ArimaConfig config, double[][] data, double scenarioFactor, int startT, 
			double limit, final int nRealScenarios) throws InvalidConfigurationException {
		final int nTimesteps = data.length;
		final int S = config.S;
		final int nResultScenarios = data[0].length;
		double[][] values = data;
		double[][] error = new double[nTimesteps][nResultScenarios];
		config = config.SARIMAtoARIMA();
		//Calculate the errors for all scenarios to build a scenario tree
		final int errorstart = config.getErrorStart();
		if (errorstart >= startT || errorstart >= nTimesteps) throw new InvalidConfigurationException("Not enough known data for ARIMA model configuration. startT = "
				+ startT + ", nTimesteps = " + nTimesteps + ", required known timesteps before startT: " + errorstart);
		double[][] oldValues = new double[nTimesteps-startT][nResultScenarios];
		
		//Remove extreme left values (because right peeks are more important)
		double mean = Utils.avg(values);
		double std = Utils.std(values);
		double oldstd = std;
		do {
   		double leftLimit = mean - 3*std;
   		for (int t = 0; t < values.length; t++) {
   			for (int i = 0; i < values[t].length; i++) {
   				values[t][i] = Math.max(leftLimit, values[t][i]);
   			}
   		}
   		oldstd = std;
   		mean = Utils.avg(values);
   		std = Utils.std(values);
		} while(oldstd/std > 5);

		//Normalize
		for (int t = 0; t < values.length; t++) {
			for (int i = 0; i < values[t].length; i++) {
				values[t][i] = (values[t][i] - mean) / std;
			}
		}
		limit = (limit - mean) / std;
		
		removeSeasonalXComponent(values, startT, config);
		
		//Differencing
		for (int d = 0; d < config.D; d++) {
			values = difference(values, startT, S, S * (d + 1));
		}
		for (int d = 0; d < config.d; d++) {
			values = difference(values, startT, 1, S * config.D + d + 1);
		}
		
		backForecastErrors(values, error, startT, nRealScenarios, config);
		
		
		//forecast the future based on the ARMA model
		System.arraycopy(values, startT, oldValues, 0, nTimesteps-startT);
		oldValues = Utils.deepArrayCopy(oldValues);
		scenarioFactor = nResultScenarios > 1 ? scenarioFactor : 1;
		PriorityQueue<QueueEntry> scenarioQueue = new PriorityQueue<QueueEntry>(nResultScenarios);
		for (int _i = 0; _i < nResultScenarios * scenarioFactor; _i++) {
			final int i = (_i < nResultScenarios ? _i : scenarioQueue.poll().value);
			for (int t = startT; t < nTimesteps; t++) {
				error[t][i] = random.nextGaussian() * config.std;
				double v = error[t][i];
				for (int a = 1; a <= config.p; a++)
					v += config.a[a - 1] * values[t - a][i];
				for (int a = 1; a <= config.P; a++) {
					v += config.A[a - 1] * values[t - a * S][i];
					for (int b = 1; b <= config.p; b++)
						v -= config.A[a - 1] * config.a[b - 1] * values[t - a * S - b][i];
				}

				for (int a = 1; a <= config.q; a++)
					v += config.t[a - 1] * error[t - a][i];
				for (int a = 1; a <= config.Q; a++) {
					v += config.T[a - 1] * error[t - a * S][i];
					for (int b = 1; b <= config.q; b++)
						v += config.T[a - 1] * config.t[b - 1] * error[t - a * S - b][i];
				}

				values[t][i] = Math.max(-limit, Math.min(limit, v));
				if(Double.isNaN(v) ) {
					final int ix = i;
					System.out.println("error: " +Arrays.toString(Arrays.stream(error).mapToDouble(e -> e[ix]).toArray()));
					System.out.println("value: " +Arrays.toString(Arrays.stream(values).mapToDouble(e -> e[ix]).toArray()));
					System.out.println("t: " + t);
				}
				assert !Double.isNaN(v);
			}
			if(scenarioFactor > 1) {
				scenarioQueue.add(new QueueEntry(getError(random, values, oldValues, startT, i, config.std), i));
			}
		}		
		// inverse differencing
		for (int d = config.d - 1; d >= 0; d--) {
			values = inverseDifference(values, nTimesteps, 1, S * config.D + d + 1);
		}
		for (int d = config.D - 1; d >= 0; d--) {
			values = inverseDifference(values, nTimesteps, S, S * (d + 1));
		}
		
		addSeasonalXComponent(values, config);
		
		//De-normalize
		for (int t = 0; t < nTimesteps; t++) {
			for (int i = 0; i < nResultScenarios; i++) {
				values[t][i] = (values[t][i] * std) + mean;
			}
		}
	}

	/**
	 * Calculate the error for a generated scenario in the data set. The error is calculated by summing
	 * 1) the weighted MAE for every data point in the series
	 * 2) the error of the weighted summed series
	 * The weight of a data point at time step t is 1.0-t/N and is 0 if t >= N
	 * @param random the random number seed
	 * @param values the generated values
	 * @param oldValues the original values which are used to compare the generated scenario with 
	 * @param startT the start time step for calculating the error
	 * @param scenario the scenario to calculate the error for
	 * @param std the standard deviation that should be used to generate random data if the generated data series is shorter than N (N=24)
	 * @return the error measure
	 */
	protected static double getError(Random random, double[][] values, double[][] oldValues, 
			final int startT, final int scenario, final double std) {
		final int N = 24;
		int n = Math.min(N, values.length - startT);
		double[] weights = IntStream.range(0, N).mapToDouble(t -> 1.0 - t/N).toArray();
		double sum = Arrays.stream(weights).sum();
		for(int i = 0; i<N; i++)
			weights[i] /= sum;
		double[] vs = new double[N];
		for(int i=0; i<n; i++) vs[i] = values[startT + i][scenario] - oldValues[i][scenario];
		for(int i=n; i<N; i++) vs[i] = random.nextGaussian() * std - random.nextGaussian() * std;
		double abserror = IntStream.range(0, N).mapToDouble(t -> weights[t] * Math.abs(vs[t])).sum();
		double sumerror = Math.abs(IntStream.range(0, N).mapToDouble(t -> weights[t] * vs[t]).sum());
		return abserror + sumerror;
	}
	
	/**
	 * Use a KalmanFilter to back-forecast the error terms using an ARIMA model
	 * @param value the data series
	 * @param error the error series to write the result to
	 * @param begin the time step to begin at
	 * @param nRealScenarios the number of scenarios in the original data set
	 * @param c the ARIMA configuration
	 */
	private void backForecastErrors(double[][] value, double[][] error, int begin, int nRealScenarios, ArimaConfig c) {
		// https://stats.stackexchange.com/questions/202903/start-up-values-for-the-kalman-filter
		// https://faculty.washington.edu/ezivot/econ584/notes/statespacemodels.pdf
		int errorstart = begin;
		int startT = Math.max(0, begin-errorstart);
		final int nResultScenarios = value[0].length;
		ArimaConfig.KalmanState ks = c.getKalmanState();
		int nTimesteps = begin-startT;
		for(int s=0; s<nRealScenarios; s++) {
			MatrixStore<Double>[] a_upd = IntStream.range(0, nTimesteps+1).mapToObj(i -> PrimitiveDenseStore.FACTORY.makeZero(ks.stateLength, 1)).toArray(PrimitiveDenseStore[]::new);
			MatrixStore<Double> p_upd = PrimitiveDenseStore.FACTORY.makeZero(ks.stateLength, ks.stateLength);
			for(int t=0; t<nTimesteps;t++) {
   			MatrixStore<Double> a_pred = ks.Tmat.multiply(a_upd[t]);
   			MatrixStore<Double> p_pred = ks.Tmat.multiply(p_upd).multiply(ks.TmatT).add(ks.Vmat);
   			error[t+startT][s] = value[t+startT][s] - ks.Zmat.multiply(a_pred).get(0, 0);
   			SparseStore<Double> M = SparseStore.PRIMITIVE.make(ks.stateLength, 1);
   			p_pred.multiply(ks.ZmatT, M);
   			double f = ks.Zmat.multiply(M).get(0, 0) + ks.h;
   			a_upd[t+1] = a_pred.add(M.multiply(error[t+startT][s] / f));
   			p_upd = p_pred.subtract(M.multiply(ks.Zmat).multiply(p_pred).multiply(1.0/f));
   		}
		}
		
		for(int s=nRealScenarios; s<nResultScenarios; s++) {
			for(int t=startT; t<begin;t++) {
				error[t][s] = error[t][s%nRealScenarios];
			}
		}
	}
	
	/**
	 * The Box-Cox Log-likelihood function
	 */
	private class BoxCoxLLF extends UnivariateObjectiveFunction {

		public BoxCoxLLF(double[] data) {
			// From https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.boxcox_llf.html
			super(new UnivariateFunction(){
				double[] logdata;
				double logsum;
				int n;
				boolean init = false;
            @Override
				public double value(double lambda){
            	if(!init) { 
            		logdata = Arrays.stream(data).map(d->Math.log(d)).toArray();
            		logsum = Utils.sum(logdata);
            		n = data.length;
            		init = true;
            	}
      		   //Compute the variance of the transformed data.
            	double variance;
      		    if(lambda == 0) variance = Utils.var(logdata);
      		    else variance = Utils.var(Arrays.stream(data).map(d->Math.pow(d, lambda) / lambda).toArray());
      		   return -((lambda - 1.0) * logsum - n/2.0 * Math.log(variance));
            }
        });
		}
		
	}
	
	public class QueueEntry implements Comparable<QueueEntry> {
	    private double key;
	    private int value;

	    public QueueEntry(double key, int value) {
	        this.key = key;
	        this.value = value;
	    }

	    @Override
	    public int compareTo(QueueEntry other) {
	        return Double.compare(other.key, key);
	    }
	    
	    @Override
	   public String toString() {
	   	return Integer.toString(value) + ": " + Double.toString(key);
	   }
	}

}

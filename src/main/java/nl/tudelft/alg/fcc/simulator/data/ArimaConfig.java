package nl.tudelft.alg.fcc.simulator.data;

import java.util.Arrays;
import java.util.prefs.Preferences;

import org.ojalgo.matrix.store.SparseStore;

public class ArimaConfig {
	final int p, d, q, P, D, Q, S;
	final double[] a, t, A, T;
	final double mu, std;
	final public double[] xreg;
	MarkovConfig markovConfig;
	int PTUsPerDay;
	private ArimaConfig withoutSeason;
	private KalmanState kalmanState;

	/**
	 * Construct a new ARIMA configuration
	 * @param mu the mean of the original series
	 * @param std the standard deviation of the remaining error series
	 * @param a the auto regressive parameters
	 * @param t the moving average parameters
	 * @param A the seasonal auto regressive parameters
	 * @param T the seasonal moving average parameters
	 * @param d the levels of differencing
	 * @param D the levels of seasonal differencing
	 * @param S the length of a season
	 * @param xreg the external regressive variables
	 * @param markovConfig a markov configuration for the reserve usage series
	 * @param PTUsperDay the number of PTUs per day
	 */
	private ArimaConfig(double mu, double std, double[] a, double[] t, double[] A, double[] T, int d, int D, int S, 
			double[] xreg, MarkovConfig markovConfig, int PTUsperDay) {
		this.p = a.length;
		this.d = d;
		this.q = t.length;
		this.a = a;
		this.t = t;
		this.mu = mu;
		this.std = std;
		this.P = A.length;
		this.D = D;
		this.Q = T.length;
		this.A = A;
		this.T = T;
		this.S = S;
		this.xreg = xreg;
		assert a.length == p;
		assert t.length == q;
		assert A.length == P;
		this.withoutSeason = null;
		this.kalmanState = null;
		this.markovConfig = markovConfig;
		this.PTUsPerDay = PTUsperDay;
	}

	public ArimaConfig(Preferences pref, MarkovConfig markovConfig, int PTUsPerDay) {
		this(
				pref.getDouble("mu", 0.0),
				pref.getDouble("std", 1.0),
				getDoubleArrayFromString(pref.get("p", "")),
				getDoubleArrayFromString(pref.get("q", "")),
				getDoubleArrayFromString(pref.get("P", "")),
				getDoubleArrayFromString(pref.get("Q", "")),
				pref.getInt("d", 0),
				pref.getInt("D", 0),
				pref.getInt("S", 0),
				getDoubleArrayFromString(pref.get("xreg", "")),
				markovConfig,
				PTUsPerDay);
	}

	/**
	 * Return an array from a string
	 * @param data a string of data with comma (and optionally extra spaces) delimited data
	 * @return a double array generated from the string
	 */
	private static double[] getDoubleArrayFromString(String data) {
		if (data == null) return new double[] {};
		String[] split = data.split("\\s*,\\s*");
		double[] result = new double[split.length];
		int j = 0;
		for (int i = 0; i < split.length; i++) {
			try {
				result[j] = Double.parseDouble(split[i]);
				j++;
			} catch (NumberFormatException e) {}
		}
		return Arrays.copyOfRange(result, 0, j);
	}
	
	/**
	 * @return the minimal number of data points at the beginning of the series required to refer back to by the ARIMA equation
	 */
	public int getErrorStart() {
		return D * S + d + Math.max(P * S + p, Q * S + q);
	}
	
	/**
	 * Change an SARIMA model into an ARIMA model
	 * @return a new ARIMA configuration with the seasonal component translated into a non-seasonal component
	 */
	public ArimaConfig SARIMAtoARIMA() {
		if(P == 0 && Q == 0) return this;
		if(withoutSeason != null) return withoutSeason;
		double[] a, t;
		if(P > 0) {
			a = new double[P*S+p];
			for(int i=0; i<=P; i++) {
				double sa = i == 0 ? -1 : A[i-1];
				if(i>0) a[S*(i-1)] = sa;				
				for(int j=0; j<p; j++)
					a[i*S+j] = this.a[j] * -sa;
			}
		} else a = this.a;
		if(Q > 0) {
			t = new double[Q*S+q];
			for(int i=0; i<=Q; i++) {
				double st = i == 0 ? 1 : T[i-1];
				if(i>0) t[S*(i-1)] = st;				
				for(int j=0; j<q; j++)
					t[i*S+j] = this.t[j] * st;
			}
		} else { t = this.t;}
		withoutSeason = new ArimaConfig(
			mu, std, a, t, 
			new double[] {}, new double[] {},
			d, D, S, xreg, 
			markovConfig, PTUsPerDay);
		return withoutSeason;
	}
	
	public KalmanState getKalmanState() {
		if(kalmanState != null) return kalmanState;
		kalmanState = new KalmanState(this);
		return kalmanState;
	}
	
	/**
	 * Kalman Filter based on an ARIMA configuration
	 */
	class KalmanState {
		final int stateLength;
		final double Q, h;
		final SparseStore<Double> Tmat, TmatT, Zmat, ZmatT, Rmat, RmatT, Vmat, Tmat2;
		
		public KalmanState(ArimaConfig c) {
			// https://stats.stackexchange.com/questions/202903/start-up-values-for-the-kalman-filter
			// https://faculty.washington.edu/ezivot/econ584/notes/statespacemodels.pdf
			stateLength = Math.max(c.p, c.q+1);

			//Setup matrices
			Tmat = SparseStore.PRIMITIVE.make(stateLength, stateLength);
			TmatT = SparseStore.PRIMITIVE.make(stateLength, stateLength);
			for(int i=0; i<c.p; i++) {
				if(c.a[i] == 0) continue;
				Tmat.set(i, 0, c.a[i]);
				TmatT.set(0, i, c.a[i]);
			}
			for(int i=0; i<stateLength-1; i++) {
				Tmat.set(i, i+1, 1.);
				TmatT.set(i+1, i, 1.);
			}
			Tmat2 = SparseStore.PRIMITIVE.make(stateLength, stateLength);
			Tmat.multiply(Tmat).supplyTo(Tmat2);
			Zmat = SparseStore.PRIMITIVE.make(1, stateLength);
			ZmatT = SparseStore.PRIMITIVE.make(stateLength, 1);
			Zmat.set(0, 0, 1);
			ZmatT.set(0, 0, 1);
			for(int i=0; i<c.q; i++) {
				if(c.t[i] == 0) continue;
				Zmat.set(0, i+1, c.t[i]);
				ZmatT.set(i+1, 0, c.t[i]);
			}
			Rmat = SparseStore.PRIMITIVE.make(stateLength, 1);
			RmatT = SparseStore.PRIMITIVE.make(1, stateLength);
			Rmat.set(0, 0, 1);
			RmatT.set(0, 0, 1);
			for(int i=0; i<c.q; i++) {
				if(c.t[i] == 0) continue; 
				Rmat.set(i+1, 0, c.t[i]);
				RmatT.set(0, i+1, c.t[i]);
			}
			Q = 1;
			Vmat = SparseStore.PRIMITIVE.make(stateLength, stateLength);
			Vmat.accept(Rmat.multiply(Q).multiply(RmatT));
			h = 0;
		}
		
	}

	public void setMarkovConfig(MarkovConfig markovConfig) {
		this.markovConfig = markovConfig;
	}

	public void setPTUsPerDay(int PTUsPerDay) {
		this.PTUsPerDay = PTUsPerDay;
	}
}

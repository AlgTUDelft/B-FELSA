package nl.tudelft.alg.fcc.solution.efel;

import java.util.Arrays;
import java.util.stream.IntStream;

import nl.tudelft.alg.fcc.model.EVState;
import nl.tudelft.alg.fcc.model.FlexibleLoad;
import nl.tudelft.alg.fcc.model.Loads;

public class ClusteredLoads extends Loads {
	int maxEVPerCluster;

	public ClusteredLoads(Loads loads, Cluster[] cluster) {
		super(generateEQEVs(loads, cluster), null);
		this.maxEVPerCluster = Arrays.stream(cluster).mapToInt(c -> c.getEVs()).max().orElse(0);
	}
	
	private static FlexibleLoad[] generateEQEVs(Loads loads, Cluster[] cluster) {
		EQEV[] eqevs = new EQEV[cluster.length];
		for(int e=0; e<cluster.length; e++) {
			FlexibleLoad[] loadArray = loads.getLoads();
			int[] selected = cluster[e].getEVidarray(); 
			EVState[] filtered = IntStream.range(0, loadArray.length)
				.filter(i -> IntStream.of(selected).anyMatch(j -> i == j))
				.mapToObj(i -> loadArray[i])
				.filter(o -> o instanceof EVState).toArray(EVState[]::new);
			eqevs[e] = new EQEV(filtered, cluster[e].getChargingSpeed());
			}
		return eqevs;
	}

	public int getMaxEVPerCluster() {
		return this.maxEVPerCluster;
	}
	
	public double getBatteryCapacity(int c, int t) {
		return ((EQEV) getLoads()[c]).getCapacity(t + problem.getStartT());
	}
	
}

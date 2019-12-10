package nl.tudelft.alg.fcc.simulator.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class MarkovConfig {
	int[][][][] probabilities;
	String path;
	
	public MarkovConfig(Path path) throws IOException {
		probabilities = importProbabilities(path);
		this.path = path.toString();
	}
	
	/**
	 * @param path the path to the file describing the matrix with the transition probabilities
	 * @return the matrix with transition probabilities with indices: period of the year, time of the day, current reserve usage, next reserve usage
	 * @throws IOException
	 */
	private static int[][][][] importProbabilities(Path path) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(path.toFile()));
		int nPeriods = Integer.parseInt(br.readLine());
		int nTimesteps = Integer.parseInt(br.readLine());
		int resolution = Integer.parseInt(br.readLine());
		int[][][][] result = new int[nPeriods][nTimesteps][resolution+1][resolution+1];
		for(int p=0; p<nPeriods; p++) {
   		for(int t=0; t<nTimesteps; t++) {
   			for(int i=0; i<=resolution; i++) {
   				String line = br.readLine();
   				String[] split = line.split(",");
   				for(int j=0; j<split.length; j++)
   					result[p][t][i][j] = Integer.parseInt(split[j]);
   			}
   			br.readLine();
   		}
   		br.readLine();
		}
		br.close();
		return result;
	}

	public String getPath() {
		return path;
	}
}

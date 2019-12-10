package nl.tudelft.alg.fcc.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class CSVReader {
	private BufferedReader br;
	
	public CSVReader(String csvFile) throws FileNotFoundException {
		br = new BufferedReader(new FileReader(csvFile));
	}
	
	//	public String[] readLine() {
	//		String line = null;
	//		try {
	//			line = br.readLine();
	//		} catch (IOException e) {
	//			e.printStackTrace();
	//		}
	//		if(line == null)
	//				return null;
	//        return line.split(",", -1);
	//	}
	
	public String[][] readFile() {
		//ArrayList<String[]> lines = new ArrayList<String[]>();
		//String[] line = null;
		//while((line = readLine()) != null)
		//	lines.add(line);
		//return lines.toArray(new String[1][]);
		return br.lines().map(l -> l.split(",", -1)).toArray(String[][]::new);
	}
	
	public void close() {
		if (br == null) return;
		try {
			br.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String[][] readCsvFile(String csvFile) throws FileNotFoundException {
		CSVReader reader = new CSVReader(csvFile);
		String[][] result = reader.readFile();
		reader.close();
		return result;
	}

}

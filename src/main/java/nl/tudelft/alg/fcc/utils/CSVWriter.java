package nl.tudelft.alg.fcc.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CSVWriter {
private BufferedWriter bw;
	
	public CSVWriter(String csvFile) throws IOException {
		bw = new BufferedWriter(new FileWriter(csvFile));
	}
	
	private Object[] toArray(Object data) {
		if(data.getClass().isArray()) {
			Object[] ar = new Object[Array.getLength(data)];
			for(int i=0; i<ar.length; i++)
				ar[i] = Array.get(data, i);
			return ar;
		}
		return new Object[] {data};
	}
	
	private void writeArray(Object data, String[] dimensions, int orgDim, boolean header) throws IOException {
		Object[] objs = toArray(data);
		if(dimensions.length > 1) {
			if(dimensions.length == 2 && header) {
				Object[] d = IntStream.range(-(orgDim-1), Array.getLength(objs[0])).mapToObj(i -> Integer.toString(i)).toArray();
				for(int i=0; i<orgDim-1; i++) d[i] = "";
				if(orgDim >= 2)
					d[orgDim-2] = dimensions[1];
				writeArray(d, new String[] {dimensions[1]}, orgDim, false);
			}
			int i=0;
			for(Object o: objs) {
				Object[] out = toArray(o);
				String[] dim2 = Arrays.copyOfRange(dimensions, 1, dimensions.length);
				if(dimensions.length == 2) {
					out = new Object[Array.getLength(o)+1];
					System.arraycopy(toArray(o), 0, out, 1, Array.getLength(o));
					out[0] = dimensions[0] +" "+ i;
				} else if (dimensions.length > 2) {
					dim2[0] = dimensions[0] + " " + i + "," + dim2[0];
				}
				writeArray(out, dim2, orgDim, header);
				header = false;
				i++;
			}
			return;
		}
		String line = String.join(",", Arrays.stream(objs).map(o -> o.toString()).collect(Collectors.toList()));
		bw.write(line.toString());
		bw.newLine();
	}
	
	public void close() {
		if (bw == null) return;
		try {
			bw.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void writeCsvFile(String csvFile, Object data, String[] dimensions) throws IOException {
		if(data == null) return;
		CSVWriter writer = new CSVWriter(csvFile);
		writer.writeArray(data, dimensions, dimensions.length, true);
		writer.close();
	}
}

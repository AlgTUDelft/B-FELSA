package nl.tudelft.alg.fcc.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

/*	Code based on https://stackoverflow.com/questions/8708342/redirect-console-output-to-string-in-java
 	by Manasjyoti Sharma, Bilesh Ganguly */

public class ConsoleOutputCapturer {
	private static PrintStream fileoutput;
	private static PrintStream previous;
	private static boolean capturing;

	public static void redirectToFile(String filename) {
		if (capturing) { return; }
		previous = System.out;
		try {
			File file = new File(filename);
			file.getParentFile().mkdirs();
			fileoutput = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)), true);
		} catch (FileNotFoundException e) {
			System.out.println("Output file " + filename + " could not be created.");
			return;
		}
		capturing = true;
		OutputStream outputStreamCombiner = new OutputStreamCombiner(Arrays.asList(previous, fileoutput));
		PrintStream custom = new PrintStream(outputStreamCombiner);

		System.setOut(custom);
	}

	public static void stop() {
		if (!capturing) { return; }

		System.setOut(previous);
		fileoutput.close();

		fileoutput = null;
		previous = null;
		capturing = false;
	}

	private static class OutputStreamCombiner extends OutputStream {
		private List<OutputStream> outputStreams;

		public OutputStreamCombiner(List<OutputStream> outputStreams) {
			this.outputStreams = outputStreams;
		}

		@Override
		public void write(int b) throws IOException {
			for (OutputStream os : outputStreams) {
				os.write(b);
			}
		}

		@Override
		public void flush() throws IOException {
			for (OutputStream os : outputStreams) {
				os.flush();
			}
		}

		@Override
		public void close() throws IOException {
			for (OutputStream os : outputStreams) {
				os.close();
			}
		}
	}
}
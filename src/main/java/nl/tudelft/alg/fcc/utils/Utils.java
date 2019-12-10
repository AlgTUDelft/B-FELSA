package nl.tudelft.alg.fcc.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.stream.IntStream;

public class Utils {
	private static DateFormat dateformat = new SimpleDateFormat("d-M-yyyy hh:mm");
	public static Random random;
	
	public static Calendar stringToCalender(String d) {
		try {
			Date date = dateformat.parse(d);
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			return c;
		} catch (ParseException e) {
			System.out.println("Exception :" + e);
		}
		return null;
	}
	
	public static void setDateFormat(String format) {
		dateformat = new SimpleDateFormat(format);
	}
	
	public static DateFormat getDateFormat() {
		return dateformat;
	}
	
	/**
     * Code from method java.util.Collections.shuffle();
	 * @param random the random generator to use (set to null to use new generator)
     */
    public static void shuffle(int[] array, Random random) {
        int count = array.length;
        for (int i = count; i > 1; i--) {
            swap(array, i - 1, random.nextInt(i));
        }
    }
    
    /**
     * Code from method java.util.Collections.shuffle();
	 * @param random the random generator to use (set to null to use new generator)
     */
    public static void shuffle(int[] array) {
       Random random; 
   	 if (Utils.random == null) random = new Random();
       else  random = Utils.random;
       shuffle(array, random);
    }

    private static void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }
    
    public static double sum(double... values) {
    	return Arrays.stream(values).sum();
    }
    
    public static int sum(int[] values) {
   	 return Arrays.stream(values).sum();
 	}
    
    public static double sum(double[][] values) {
     	return Arrays.stream(values).mapToDouble(a -> sum(a)).sum();
     }
    
    public static double min(double... values) {
    	return Arrays.stream(values).min().orElse(Double.NaN);
    }
    
    public static double min(double[][] values) {
   	 return Arrays.stream(values).mapToDouble(a -> min(a)).min().orElse(0);
     }
    
    public static double max(double... values) {
    	return Arrays.stream(values).max().orElse(Double.NaN);
    }
    
    public static double max(double[][] values) {
   	 return Arrays.stream(values).mapToDouble(a -> max(a)).max().orElse(0);
     }
    
    public static double avg(double... values) {
    	return Arrays.stream(values).average().orElse(Double.NaN);
    }
    
    public static double avg(double[][] values) {
      	return Arrays.stream(values).mapToDouble(a -> avg(a)).average().orElse(0);
    }
    
    public static double var(double... values) {
   	 double mean = avg(values);
   	 return Arrays.stream(values).map(a -> Math.pow(a - mean,2)).sum() / values.length;
    }
    
    public static double std(double... values) {
   	 return Math.sqrt(var(values));
    }
    
    public static double std(double[][] values) {
   	 return std(Arrays.stream(values).flatMapToDouble(Arrays::stream).toArray());
    }
    
    public static int[] argsort(double[] values) {
   	 return IntStream.range(0, values.length)
             .boxed().sorted((i, j) -> Double.compare(values[j], values[i]))
             .mapToInt(e -> e).toArray();
    }
    
    public static double[] columnSum(double[][] values) {
    	double[] result = new double[values[0].length];
    	for(int c=0; c<values[0].length; c++) {
    		final int _c = c;
    		result[c] = IntStream.range(0, values.length).mapToDouble(r -> values[r][_c]).sum();
    	}
    	return result;
    }
    
    public static double[] concatArrays(double[] a1, double[] a2) {
    	double[] out = new double[a1.length+a2.length];
    	System.arraycopy(a1, 0, out, 0, a1.length);
    	System.arraycopy(a2, 0, out, a1.length, a2.length);
    	return out;
    }
    
	public static <T> T[] concatArrays(T[] a1, T[] a2)
	{
		Class<?> type = a1.getClass().getComponentType();
		@SuppressWarnings("unchecked")
		T[] out = (T[]) Array.newInstance(type, a1.length + a2.length);
		System.arraycopy(a1, 0, out, 0, a1.length);
		System.arraycopy(a2, 0, out, a1.length, a2.length);
		return out;
	}

    public static void initRand(int seed) {
    	if(seed != 0)
			random = new Random(seed);
		else
			random = new Random();
    }
    
    public static int weightedChoice(Random random, double[] weights) {
   	 double tw = Utils.sum(weights);
   	 double c = random.nextDouble() * tw;
   	 double o = 0;
   	 for (int i = 0; i < weights.length; i++) {
   		 o += weights[i];
   		 if (o >= c) return i;
   	 }
   	 assert false;
   	 return 0;
    }
    
    public static int weightedChoice(Random random, int[] weights) {
   	 int tw = Utils.sum(weights);
   	 int c = random.nextInt(tw);
   	 int o = 0;
   	 for (int i = 0; i < weights.length; i++) {
   		 o += weights[i];
   		 if (o >= c) return i;
   	 }
   	 assert false;
   	 return 0;
    }
    
    /**
     * Source: https://gist.github.com/scpurcell/9f8c80b6af0f6e3314fb
     * Use reflection to shallow copy simple type fields with matching names from one object to another
     * @param fromObj the object to copy from
     * @param toObj the object to copy to
     * 
     */
    public static void copyMatchingFields( Object fromObj, Object toObj ) {
        if ( fromObj == null || toObj == null )
            throw new NullPointerException("Source and destination objects must be non-null");
        
		Class<? extends Object> fromClass = fromObj.getClass();
		Class<? extends Object> toClass = toObj.getClass();

        Field[] fields = fromClass.getDeclaredFields();
        for ( Field f : fields ) {
            try {
                Field t = toClass.getDeclaredField( f.getName() );

                if ( t.getType() == f.getType() ) {
                    // extend this if to copy more immutable types if interested
                    if ( t.getType() == String.class || t.getType() == double.class
                            || t.getType() == int.class || t.getType() == Integer.class
                            || t.getType() == char.class || t.getType() == Character.class
                            || t.getType() == boolean.class) {
                        f.setAccessible(true);
                        t.setAccessible(true);
                        t.set( toObj, f.get(fromObj) );
                    } else if ( t.getType() == Date.class  ) {
                  	  f.setAccessible(true);
                       t.setAccessible(true);
                  	  // dates are not immutable, so clone non-null dates into the destination object
                       Date d = (Date)f.get(fromObj);
                       t.set( toObj, d != null ? d.clone() : null );
                    } else if ( t.getType() == Calendar.class  ) {
                        // dates are not immutable, so clone non-null dates into the destination object
                  	  f.setAccessible(true);
                       t.setAccessible(true);
                  	  Calendar d = (Calendar) f.get(fromObj);
                       t.set( toObj, d != null ? d.clone() : null );
                    } 
                }
            } catch (NoSuchFieldException ex) {
                // skip it
            } catch (IllegalAccessException ex) {
            	ex.printStackTrace();
                System.out.println(String.format("Unable to copy field: %s", f.getName()));
            }
        }
    }

	/**
	 * Returns a copy of the specified array object, deeply copying multidimensional arrays. If the specified object is
	 * null, the return value is null. Note: if the array object has an element type which is a reference type that is not
	 * an array type, the elements themselves are not deep copied. This method only copies array objects.
	 *
	 * source: https://stackoverflow.com/questions/1564832/how-do-i-do-a-deep-copy-of-a-2d-array-in-java
	 *
	 * @param array
	 *           the array object to deep copy
	 * @param <T>
	 *           the type of the array to deep copy
	 * @return a copy of the specified array object, deeply copying multidimensional arrays, or null if the object is null.
	 *         does not clone contained objects
	 */
	public static <T> T deepArrayCopy(T array) {
		if (array == null)
			return null;

		Class<?> arrayType = array.getClass();
		if (!arrayType.isArray())
			return array;

		int length = Array.getLength(array);
		Class<?> componentType = arrayType.getComponentType();

		@SuppressWarnings("unchecked")
		T copy = (T) Array.newInstance(componentType, length);

		if (componentType.isArray()) {
			for (int i = 0; i < length; ++i)
				Array.set(copy, i, deepArrayCopy(Array.get(array, i)));
		} else {
			System.arraycopy(array, 0, copy, 0, length);
		}

		return copy;
	}
	
}

package de.uni_koeln.spinfo.classification.jasc.dbIO;

public class ProgressBar {

	public static void updateProgress(double progressPercentage) {
	    final int width = 30; // progress bar width in chars
	    
	    System.out.print("\r[");
	    int i = 0;
	    for (; i <= (int)(progressPercentage*width); i++) {
	      System.out.print(".");
	    }
	    for (; i < width; i++) {
	      System.out.print(" ");
	    }
	    System.out.print("]");
	    System.out.println("\n           " + progressPercentage + " %");
	  }

	  public static void main(String[] args) {
	    try {
	      for (double progressPercentage = 0.0; progressPercentage < 1.0; progressPercentage += 0.01) {
	        updateProgress(progressPercentage);
	        Thread.sleep(20);
	      }
	    } catch (InterruptedException e) {}
	  }
	
}

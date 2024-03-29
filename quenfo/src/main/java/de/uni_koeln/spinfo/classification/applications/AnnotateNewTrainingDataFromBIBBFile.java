package de.uni_koeln.spinfo.classification.applications;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_koeln.spinfo.classification.core.data.ClassifyUnit;
import de.uni_koeln.spinfo.classification.core.data.ExperimentConfiguration;
import de.uni_koeln.spinfo.classification.zoneAnalysis.helpers.SingleToMultiClassConverter;
import de.uni_koeln.spinfo.classification.zoneAnalysis.preprocessing.DumpDataBaseTrainingDataGenerator;
import de.uni_koeln.spinfo.classification.zoneAnalysis.preprocessing.TrainingDataGenerator;
import de.uni_koeln.spinfo.classification.zoneAnalysis.workflow.ZoneJobs;
import de.uni_koeln.spinfo.information_extraction.workflow.IEJobs;

public class AnnotateNewTrainingDataFromBIBBFile {
	
	private static String inBIBBClassifiedParagraphs = "classification/data/inBIBBClassifiedParagraphs2016.xls";
	private static String newTrainingDataFile = "classification/data/newTrainingData2016.csv";
	private static ZoneJobs jobs;
	
	
	
	public static void main(String[] args) throws IOException {
		Map<Integer, List<Integer>> translations = new HashMap<Integer, List<Integer>>();
		List<Integer> categories = new ArrayList<Integer>();
		categories.add(1);
		categories.add(2);
		translations.put(5, categories);
		categories = new ArrayList<Integer>();
		categories.add(2);
		categories.add(3);
		translations.put(6, categories);
		SingleToMultiClassConverter stmc = new SingleToMultiClassConverter(6, 4, translations);
		jobs = new ZoneJobs(stmc);
		
	//	DumpDataBaseTrainingDataGenerator ddtdg = new DumpDataBaseTrainingDataGenerator(jobs.getStmc(),new File(inBIBBClassifiedParagraphs), new File(newTrainingDataFile));
		//ddtdg.readInBIBBClassifiedParagraphsFromFile();
		//ddtdg.annotate();		
		TrainingDataGenerator tdg = new TrainingDataGenerator(new File("classification/data/newTrainingData2016.csv"), 4, 6, translations);
		List<ClassifyUnit> cus = tdg.getTrainingData();
		IEJobs jobs = new IEJobs();
		cus = jobs.treatEncoding(cus);
		tdg.writeTrainingDataFile(new File("classification/data/newTrainingData2016.csv"), cus);
		
	}
	


}

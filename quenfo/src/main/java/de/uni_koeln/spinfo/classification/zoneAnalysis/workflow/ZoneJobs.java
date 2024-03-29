package de.uni_koeln.spinfo.classification.zoneAnalysis.workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

import opennlp.tools.ml.model.AbstractModel;
import de.uni_koeln.spinfo.classification.core.classifier.model.Model;
import de.uni_koeln.spinfo.classification.core.data.ClassifyUnit;
import de.uni_koeln.spinfo.classification.core.data.ExperimentConfiguration;
import de.uni_koeln.spinfo.classification.core.data.FeatureUnitConfiguration;
import de.uni_koeln.spinfo.classification.core.featureEngineering.FeatureUnitTokenizer;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureReduction.MutualInformationFilter;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureReduction.Normalizer;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureReduction.Stemmer;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureReduction.StopwordFilter;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureSelection.LetterNGrammGenerator;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureSelection.SuffixTreeFeatureGenerator;
import de.uni_koeln.spinfo.classification.core.featureEngineering.featureWeighting.AbstractFeatureQuantifier;
import de.uni_koeln.spinfo.classification.core.helpers.ClassifyUnitFilter;
import de.uni_koeln.spinfo.classification.core.helpers.EncodingProblemTreatment;
import de.uni_koeln.spinfo.classification.core.helpers.crossvalidation.CrossvalidationGroupBuilder;
import de.uni_koeln.spinfo.classification.core.helpers.crossvalidation.TrainingTestSets;
import de.uni_koeln.spinfo.classification.jasc.data.JASCClassifyUnit;
import de.uni_koeln.spinfo.classification.jasc.dbIO.DbConnector;
import de.uni_koeln.spinfo.classification.jasc.preprocessing.ClassifyUnitSplitter;
import de.uni_koeln.spinfo.classification.jasc.preprocessing.JASCReader;
import de.uni_koeln.spinfo.classification.zoneAnalysis.classifier.ZoneAbstractClassifier;
import de.uni_koeln.spinfo.classification.zoneAnalysis.classifier.svm.SVMClassifier;
import de.uni_koeln.spinfo.classification.zoneAnalysis.data.ExperimentResult;
import de.uni_koeln.spinfo.classification.zoneAnalysis.data.ZoneClassifyUnit;
import de.uni_koeln.spinfo.classification.zoneAnalysis.evaluation.EvaluationValue;
import de.uni_koeln.spinfo.classification.zoneAnalysis.evaluation.ResultComparator;
import de.uni_koeln.spinfo.classification.zoneAnalysis.evaluation.ZoneEvaluator;
import de.uni_koeln.spinfo.classification.zoneAnalysis.helpers.SingleToMultiClassConverter;
import de.uni_koeln.spinfo.classification.zoneAnalysis.preprocessing.TrainingDataGenerator;
import de.uni_koeln.spinfo.information_extraction.data.CompetenceUnit;

public class ZoneJobs {

	public ZoneJobs() throws IOException {
		System.out.println("ZoneJobs: Achtung - keine Translations gesetzt");
		sw_filter = new StopwordFilter(new File("classification/data/stopwords.txt"));
		normalizer = new Normalizer();
		stemmer = new Stemmer();
		tokenizer = new FeatureUnitTokenizer();
		suffixTreeBuilder = new SuffixTreeFeatureGenerator();
	}

	public ZoneJobs(SingleToMultiClassConverter stmc) throws IOException {
		if (stmc == null) {
			System.out.println("ZoneJobs: Achtung - keine Translations gesetzt");
		}
		this.stmc = stmc;
		sw_filter = new StopwordFilter(new File("classification/data/stopwords.txt"));
		normalizer = new Normalizer();
		stemmer = new Stemmer();
		tokenizer = new FeatureUnitTokenizer();
		suffixTreeBuilder = new SuffixTreeFeatureGenerator();

	}

	protected SuffixTreeFeatureGenerator suffixTreeBuilder;
	protected StopwordFilter sw_filter;
	protected Normalizer normalizer;
	protected Stemmer stemmer;
	protected FeatureUnitTokenizer tokenizer;
	private SingleToMultiClassConverter stmc;
	MutualInformationFilter mi_filter = new MutualInformationFilter();

	/**
	 * A method to read job advertisements from the specified file and split
	 * them into paragraphs
	 * 
	 * @param dataFile
	 *            A file containing job advertisements
	 * @return A list of paragraphs from the job ads contained in the data file
	 * @throws IOException
	 */
	public List<JASCClassifyUnit> getParagraphsFromJobAdFile(File dataFile) throws IOException {

		/* Read the source file */
		JASCReader reader = new JASCReader();
		SortedMap<Integer, String> texts = reader.getJobAds(dataFile);
		// System.out.println(texts);

		List<String> textsAsString = new ArrayList<String>(texts.values());
		Collections.reverse(textsAsString);

		/* Split to atomic classification units (here: paragraphs) */
		List<JASCClassifyUnit> paragraphs = new ArrayList<JASCClassifyUnit>();

		Set<Integer> textIDs = texts.keySet();
		for (Integer textID : textIDs) {
			List<String> splitAtEmptyLine = ClassifyUnitSplitter.splitIntoParagraphs(texts.get(textID));
			for (String content : splitAtEmptyLine) {
				JASCClassifyUnit unitToClassify = new JASCClassifyUnit(content.replaceAll("\\/", "\\_"), textID);
				paragraphs.add(unitToClassify);
			}
		}
		return paragraphs;
	}

	/**
	 * A method to get pre-categorized paragraphs from the specified file
	 * 
	 * @param trainingDataFile
	 *            file with pre-categorized paragraphs
	 * @return A list of pre-categorized paragraphs
	 * @throws IOException
	 */
	public List<ClassifyUnit> getCategorizedParagraphsFromFile(File trainingDataFile)
			throws IOException {
		TrainingDataGenerator tdg = new TrainingDataGenerator(trainingDataFile, stmc.getNumberOfCategories(),
				stmc.getNumberOfClasses(), stmc.getTranslations());
		List<ClassifyUnit> paragraphs = tdg.getTrainingData();
		return paragraphs;
	}

	/**
	 * A method to get pre-categorized paragraphs from the specified file
	 * 
	 * @param trainingDataFile
	 *            file with pre-categorized paragraphs
	 * @return A list of pre-categorized paragraphs
	 * @throws IOException
	 */
	public List<ClassifyUnit> getCategorizedASCIIParagraphsFromFile(File trainingDataFile)
			throws IOException {
		TrainingDataGenerator tdg = new TrainingDataGenerator(trainingDataFile, stmc.getNumberOfCategories(),
				stmc.getNumberOfClasses(), stmc.getTranslations());
		List<ClassifyUnit> paragraphs = tdg.getTrainingData();
		for (ClassifyUnit classifyUnit : paragraphs) {
			String content = classifyUnit.getContent();
			classifyUnit.setContent(EncodingProblemTreatment.normalizeEncoding(content));
		}
		return paragraphs;
	}

	public List<ClassifyUnit> getCategorizedParagraphsFromDB(Connection inputConnection, boolean executeAtBIBB)
			throws ClassNotFoundException, SQLException {
		List<ClassifyUnit> toReturn = new ArrayList<ClassifyUnit>();
		Statement stmt = inputConnection.createStatement();
		String sql = "SELECT txtID, ClassONE, ClassTWO, ClassTHREE, ClassFOUR  FROM Classes_Correctable WHERE UseForTraining = 1";
		ResultSet result = stmt.executeQuery(sql);
		ClassifyUnit cu;
		while (result.next()) {
			int txtID = result.getInt(1);
			if (executeAtBIBB) {
				sql = "SELECT STELLENBESCHREIBUNG FROM ClassifiedParaTexts WHERE ID = " + txtID + ";";
			} else {
				sql = "SELECT Text FROM ClassifiedParaTexts WHERE ID = " + txtID + ";";
			}
			Statement stmt2 = inputConnection.createStatement();
			ResultSet paraText = stmt2.executeQuery(sql);
			String content = paraText.getString(1);
			int parentID = -1; // wird nicht benötigt, oder ?!
			boolean[] classIDs = new boolean[stmc.getNumberOfCategories()];
			for (int i = 0; i < stmc.getNumberOfCategories(); i++) {
				classIDs[i] = parseIntToBool(result.getInt(2 + i));
			}
			int actualClassID = getActualClassID(classIDs);
			ZoneClassifyUnit.setNumberOfCategories(stmc.getNumberOfCategories(), stmc.getNumberOfClasses(),
					stmc.getTranslations());
			cu = new JASCClassifyUnit(content, parentID);
			((ZoneClassifyUnit) cu).setActualClassID(actualClassID);
			toReturn.add(cu);
		}
		return toReturn;
	}

	public List<ClassifyUnit> getCategorizedASCIIParagraphsFromDB(Connection inputConnection, boolean executeAtBIBB)
			throws ClassNotFoundException, SQLException {
		List<ClassifyUnit> toReturn = new ArrayList<ClassifyUnit>();
		Statement stmt = inputConnection.createStatement();
		String sql = "SELECT txtID, ClassONE, ClassTWO, ClassTHREE, ClassFOUR  FROM Classes_Correctable WHERE UseForTraining = 1";
		ResultSet result = stmt.executeQuery(sql);
		ClassifyUnit cu;
		while (result.next()) {
			int txtID = result.getInt(1);
			if (executeAtBIBB) {
				sql = "SELECT STELLENBESCHREIBUNG FROM ClassifiedParaTexts WHERE ID = " + txtID + ";";
			} else {
				sql = "SELECT Text FROM ClassifiedParaTexts WHERE ID = " + txtID + ";";
			}
			Statement stmt2 = inputConnection.createStatement();
			ResultSet paraText = stmt2.executeQuery(sql);
			String content = paraText.getString(1);
			int parentID = -1; // wird nicht benötigt, oder ?!
			boolean[] classIDs = new boolean[stmc.getNumberOfCategories()];
			for (int i = 0; i < stmc.getNumberOfCategories(); i++) {
				classIDs[i] = parseIntToBool(result.getInt(2 + i));
			}
			int actualClassID = getActualClassID(classIDs);
			ZoneClassifyUnit.setNumberOfCategories(stmc.getNumberOfCategories(), stmc.getNumberOfClasses(),
					stmc.getTranslations());
			cu = new JASCClassifyUnit(EncodingProblemTreatment.normalizeEncoding(content), parentID);
			((ZoneClassifyUnit) cu).setActualClassID(actualClassID);
			toReturn.add(cu);
		}
		return toReturn;
	}

	private int getActualClassID(boolean[] classIDs) {
		List<Integer> classIdsAsList = new ArrayList<Integer>();
		int classNum = 0;
		int classId = 0;
		for (int i = 0; i < classIDs.length; i++) {
			if (classIDs[i]) {
				classIdsAsList.add(i + 1);
				classId = i + 1;
				classNum++;
			}
		}
		if (classNum > 1) {
			for (Integer i : stmc.getTranslations().keySet()) {
				if (stmc.getTranslations().get(i).containsAll(classIdsAsList)) {
					return i;
				}
			}
			System.out.println("ZoneJobs - getParasFromDB:  Achtung - Translationfehler! ");
			return -1;
		}

		return classId;

	}

	private boolean parseIntToBool(int toParse) {
		if (toParse == 0) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Initializes the feature units (as tokens) of each classify unit.
	 * 
	 * @param paragraphs
	 *            Classify units to initialize
	 * @return Classify units with initialized feature units (tokens)
	 */
	public List<ClassifyUnit> initializeClassifyUnits(List<ClassifyUnit> paragraphs) {
		List<ClassifyUnit> toProcess = new ArrayList<ClassifyUnit>();
		for (ClassifyUnit paragraph : paragraphs) {
			// System.out.println(((ZoneClassifyUnit)
			// paragraph).getActualClassID());
			ZoneClassifyUnit newParagraph = new ZoneClassifyUnit(paragraph.getContent(), paragraph.getID());
			newParagraph.setClassIDs(((ZoneClassifyUnit) paragraph).getClassIDs());
			newParagraph.setActualClassID(((ZoneClassifyUnit) paragraph).getActualClassID());
			newParagraph.setFeatureUnits(tokenizer.tokenize(newParagraph.getContent()));
			toProcess.add(newParagraph);
		}
		return toProcess;
	}

	/**
	 * Sets the feature units of each classify unit by following the
	 * instructions within the specified feature unit configuration.
	 * 
	 * @param paragraphs
	 *            Classify units with initialized features
	 * @param fuc
	 *            feature unit configuration
	 * @return Classify units with features
	 * @throws IOException
	 */
	public List<ClassifyUnit> setFeatures(List<ClassifyUnit> paragraphs, FeatureUnitConfiguration fuc,
			boolean trainingPhase) throws IOException {

		for (ClassifyUnit cu : paragraphs) {
			// if(fuc.isTreatEncoding()){
			// List<String> normalizeEncoding =
			// EncodingProblemTreatment.normalizeEncoding(cu.getFeatureUnits());
			// cu.setFeatureUnits(normalizeEncoding);
			// }
			if (fuc.isNormalize()) {
				normalizer.normalize(cu.getFeatureUnits());
			}
			if (fuc.isFilterStopwords()) {
				// System.out.println("Stopwords filtered");
				List<String> filtered = sw_filter.filterStopwords(cu.getFeatureUnits());
				cu.setFeatureUnits(filtered);
			}
			if (fuc.isStem()) {
				// System.out.println("Stemmed");
				List<String> stems = stemmer.getStems(cu.getFeatureUnits());
				cu.setFeatureUnits(stems);
			}
			int[] ngrams2 = fuc.getNgrams();
			if (ngrams2 != null) {
				// System.out.println(" GramsCont: " + continuusNgrams);
				// useNGrams(cu, nGramLength, continuusNgrams);
				List<String> ngrams = new ArrayList<String>();
				for (int i : ngrams2) {
					ngrams.addAll(LetterNGrammGenerator.getNGramms(cu.getFeatureUnits(), i, fuc.isContinuusNGrams()));
				}
				cu.setFeatureUnits(ngrams);
			}

		}
		if (fuc.getMiScore() != 0) {

			if (trainingPhase) {
				mi_filter.initialize(fuc, paragraphs);
			}
			mi_filter.filter(paragraphs, fuc.getMiScore());
		}
		if (fuc.isSuffixTree()) {
			paragraphs = suffixTreeBuilder.getSuffixTreeFreatures(paragraphs);
		}
		List<ClassifyUnit> filtered = ClassifyUnitFilter.filterByFUs(paragraphs, 1);
		return filtered;
	}

	/**
	 * Initializes the feature vectors of the classify units.
	 * 
	 * @param paragraphs
	 *            Classify units with features
	 * @param fq
	 *            The feature quantifier that should be used.
	 * @param featureUnitOrder
	 *            Pre-determined oder of pre-determined features. If null, a new
	 *            fuo will be generated using the specified festures.
	 * @return Classify units with feature vectors
	 */
	public List<ClassifyUnit> setFeatureVectors(List<ClassifyUnit> paragraphs, AbstractFeatureQuantifier fq,
			List<String> featureUnitOrder) {
		if (fq != null) {
			// initialisieren mit trainingdata...
			fq.setFeatureValues(paragraphs, featureUnitOrder);
		}
		return paragraphs;
	}

	/**
	 * @param cus
	 *            the classify units
	 * @param expConfig
	 *            the experiment configuration
	 * @return a model for the specified experiment configuration
	 * @throws IOException
	 */
	public Model getModelForClassifier(List<ClassifyUnit> cus, ExperimentConfiguration expConfig) throws IOException {
		File modelFile = expConfig.getModelFile();
		// modelFile.createNewFile();
		Model model;
		if (expConfig.getClassifier() instanceof SVMClassifier) {
			SVMClassifier svmC = (SVMClassifier) expConfig.getClassifier();
			svmC.buildModel(expConfig, cus);
			return null;
		}

		if (!modelFile.exists()) {
			// build model...
			System.out.println("build model...");
			model = expConfig.getClassifier().buildModel(cus, expConfig.getFeatureConfiguration(),
					expConfig.getFeatureQuantifier(), expConfig.getDataFile());
			// store model
			// exportModel(expConfig.getModelFile(), model);
			return model;
		} else {
			System.out.println("read model..");
			// read model...
			FileInputStream fis = new FileInputStream(modelFile);
			ObjectInputStream in = new ObjectInputStream(fis);
			try {
				Object o = in.readObject();
				model = (Model) o;
				in.close();
				return model;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				in.close();
				return null;
			}
		}
	}

	/**
	 * @param cus
	 *            the classify units
	 * @param expConfig
	 *            the experiment configuration
	 * @return a model for the specified experiment configuration
	 * @throws IOException
	 */
	public Model getNewModelForClassifier(List<ClassifyUnit> cus, ExperimentConfiguration expConfig)
			throws IOException {
		Model model;
		if (expConfig.getClassifier() instanceof SVMClassifier) {
			SVMClassifier svmC = (SVMClassifier) expConfig.getClassifier();
			svmC.buildModel(expConfig, cus);
			return null;
		}
		// build model...
		System.out.println("build model...");
		model = expConfig.getClassifier().buildModel(cus, expConfig.getFeatureConfiguration(),
				expConfig.getFeatureQuantifier(), expConfig.getDataFile());
		// store model
		// exportModel(expConfig.getModelFile(), model);
		return model;

	}

	public void exportModel(File modelFile, Model model) throws IOException {
		if (modelFile.exists()) {
			return;
		} else {
			modelFile.createNewFile();
			FileOutputStream fos = new FileOutputStream(modelFile);
			ObjectOutputStream out = new ObjectOutputStream(fos);
			out.writeObject(model);
			out.flush();
			out.close();
		}
	}

	/**
	 * Trains and tests the specified classifier by building 10 crossvalidation
	 * groups over the specified manually annotated classify units.
	 * 
	 * @param paragraphs
	 *            Classify units
	 * @param classifier
	 *            Classifier to test
	 * @return A map of all classify units as key and the guessed categories as
	 *         values
	 * @throws IOException
	 */

	public Map<ClassifyUnit, boolean[]> crossvalidate(List<ClassifyUnit> paragraphs, ExperimentConfiguration expConfig)
			throws IOException {

		// build crossvalidationgroups...
		int numberOfCrossValidGroups = 10;
		CrossvalidationGroupBuilder<ClassifyUnit> cvgb = new CrossvalidationGroupBuilder<ClassifyUnit>(paragraphs,
				numberOfCrossValidGroups);
		Iterator<TrainingTestSets<ClassifyUnit>> iterator = cvgb.iterator();

		// classify..
		Map<ClassifyUnit, boolean[]> classified = new HashMap<ClassifyUnit, boolean[]>();

		while (iterator.hasNext()) {
			TrainingTestSets<ClassifyUnit> testSets = iterator.next();

			List<ClassifyUnit> trainingSet = testSets.getTrainingSet();
			List<ClassifyUnit> testSet = testSets.getTestSet();
			Model model = getModelForClassifier(trainingSet, expConfig);
			classified.putAll(classify(testSet, expConfig, model));
		}

		return classified;
	}

	/**
	 * Classifies the specified classify units with the specified classifier,
	 * based on the specified model
	 * 
	 * @param paragraphs
	 *            Units to classify
	 * @param classifier
	 *            Classifier to use
	 * @param model
	 *            Model to train the classifier
	 * @return A map of all classify units as key and the guessed categories as
	 *         values
	 */

	public Map<ClassifyUnit, boolean[]> classify(List<ClassifyUnit> paragraphs, ExperimentConfiguration expConfig,
			Model model) {
		ZoneAbstractClassifier classifier = (ZoneAbstractClassifier) expConfig.getClassifier();
		if (classifier instanceof SVMClassifier) {
			try {
				Map<ClassifyUnit, boolean[]> classified = ((SVMClassifier) classifier).predict(paragraphs, expConfig);
				// TODO....
				return classified;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Map<ClassifyUnit, boolean[]> classified = new HashMap<ClassifyUnit, boolean[]>();
		for (ClassifyUnit cu : paragraphs) {
			boolean[] classes = ((ZoneAbstractClassifier) classifier).classify((ZoneClassifyUnit) cu, model);
			classified.put(cu, classes);
		}
		return classified;
	}

	/**
	 * Merges the results of the second map into the first map. For every
	 * classify unit each class will be matched that matches in the first OR in
	 * the second map.
	 * 
	 * @param all
	 *            A map containg all classify units
	 * @param other
	 *            A map that should not contain more or other classify units
	 *            than the
	 * @return A map of all classify units as key and the guessed categories as
	 *         values
	 */
	public Map<ClassifyUnit, boolean[]> mergeResults(Map<ClassifyUnit, boolean[]> all,
			Map<ClassifyUnit, boolean[]> other) {
		Map<ClassifyUnit, boolean[]> toReturn = new HashMap<ClassifyUnit, boolean[]>();
		Set<ClassifyUnit> allUnits = all.keySet();
		for (ClassifyUnit classifyUnit : allUnits) {

			boolean[] otherCats = other.get(classifyUnit);
			boolean[] allCats = all.get(classifyUnit);
			boolean[] toReturnCats = new boolean[allCats.length];

			for (int i = 0; i < allCats.length; i++) {
				if (allCats[i]) {
					toReturnCats[i] = true;
				}
				if (otherCats != null && otherCats[i]) {

					toReturnCats[i] = true;
				}
			}
			toReturn.put(classifyUnit, toReturnCats);
			// for (int i = 0; i < toReturnCats.length; i++) {
			// if(toReturnCats[i] != allCats[i]){
			// System.out.println(classifyUnit.getContent());
			// System.out.println(((ZoneClassifyUnit)
			// classifyUnit).getActualClassID());
			// System.out.println("difference");
			// System.out.println("all");
			// for (int j = 0; j < toReturnCats.length; j++) {
			// System.out.print(allCats[j]+" ");
			// }
			// System.out.println();
			// System.out.println("pre");
			// for (int j = 0; j < toReturnCats.length; j++) {
			// System.out.print(otherCats[j]+" ");
			// }
			// System.out.println();
			// System.out.println("merged");
			// for (int j = 0; j < toReturnCats.length; j++) {
			// System.out.print(toReturnCats[j]+" ");
			// }
			// System.out.println();
			// }
			// }
		}
		return toReturn;
	}

	/**
	 * Translates the boolean arrays of the specified map (key and value) into
	 * possibly-multiple-true-values-boolean arrays (Short: It translates
	 * single-value-6-classes to multiple-value-4-classes)
	 * 
	 * @param untranslated
	 *            A map with boolean arrays each containing no more than one
	 *            true value (6 single-value classes)
	 * @return A map with boolean arrays each possibly containing more than one
	 *         true value (4 multiple-value classes)
	 */
	public Map<ClassifyUnit, boolean[]> translateClasses(Map<ClassifyUnit, boolean[]> untranslated) {
		Map<ClassifyUnit, boolean[]> translated = new HashMap<ClassifyUnit, boolean[]>();
		Set<ClassifyUnit> keySet = untranslated.keySet();
		for (ClassifyUnit classifyUnit : keySet) {
			boolean[] classIDs = ((ZoneClassifyUnit) classifyUnit).getClassIDs();
			int singleClassID = -1;
			for (int i = 0; i < classIDs.length; i++) {
				if (classIDs[i]) {
					singleClassID = i + 1;
				}
			}
			boolean[] multiClasses = stmc.getMultiClasses(singleClassID);
			((ZoneClassifyUnit) classifyUnit).setClassIDs(multiClasses);
			boolean[] newClassIDs = untranslated.get(classifyUnit);
			singleClassID = -1;
			for (int i = 0; i < newClassIDs.length; i++) {
				if (newClassIDs[i]) {
					singleClassID = i + 1;
				}
			}
			boolean[] newMultiClasses = stmc.getMultiClasses(singleClassID);
			translated.put(classifyUnit, newMultiClasses);
		}
		return translated;
	}

	public ExperimentResult evaluate(Map<ClassifyUnit, boolean[]> classified, List<Integer> categories,
			ExperimentConfiguration config) {
		ZoneEvaluator evaluator = new ZoneEvaluator();
		evaluator.evaluate(classified, categories);
		ExperimentResult er = new ExperimentResult();
		er.setExperimentConfiguration(config.toString());
		er.setCategoryEvaluations(evaluator.getCategoryEvaluations());
		er.setAccuracy(evaluator.getOverallAccuracy());
		er.setF1Measure(evaluator.getOverallF1Score());
		er.setPrecision(evaluator.getOverallPrecision());
		er.setRecall(evaluator.getOverallRecall());
		er.setTN(evaluator.getOverallTNs());
		er.setTP(evaluator.getOverallTPs());
		er.setFN(evaluator.getOverallFNs());
		er.setFP(evaluator.getOverallFPs());
		if (stmc != null) {
			er.setNumberOfClasses(stmc.getNumberOfCategories());
		} else {
			er.setNumberOfClasses(classified.values().iterator().next().length);
		}
		return er;
	}

	public File persistExperimentResult(ExperimentResult er, File outputFolder) throws IOException {
		if (!outputFolder.exists()) {
			outputFolder.mkdirs();
		}
		String objectFileName = null;
		StringBuffer sb = new StringBuffer();
		sb.append(outputFolder.getAbsolutePath() + "\\");
		sb.append("singleResult");
		sb.append(er.getExperimentConfiguration().replaceAll("[:\\-]", "_"));
		objectFileName = sb.toString();
		File file = new File(objectFileName + ".bin");
		if (!file.exists()) {
			file.createNewFile();
		}

		FileOutputStream fos = new FileOutputStream(file);
		ObjectOutputStream out = new ObjectOutputStream(fos);

		out.writeObject(er);
		out.flush();
		out.close();
		System.out.println("**************************************___WRITTEN: " + objectFileName
				+ ".bin___***************************************************");
		return file;
	}

	public File persistExperimentResults(List<ExperimentResult> ers, File outputFolder) throws IOException {
		if (!outputFolder.exists()) {
			outputFolder.mkdirs();
		}
		StringBuffer sb1 = new StringBuffer();
		for (ExperimentResult experimentResult : ers) {
			sb1.append(experimentResult.getExperimentConfiguration() + experimentResult.getAccuracy()
					+ experimentResult.getID());
		}
		int hash = sb1.toString().hashCode();
		String objectFileName = null;
		StringBuffer sb = new StringBuffer();
		sb.append(outputFolder.getAbsolutePath() + "\\");
		sb.append(ers.size() + "_Results_" + hash);
		// String expconf = ers.get(0).getExperimentConfiguration();
		// String withoutFUConf = expconf.substring(expconf.indexOf('&')+1);
		// sb.append(withoutFUConf);
		// sb.append("_"+ers.size()+"_experiments");
		objectFileName = sb.toString();
		File file = new File(objectFileName + ".bin");
		if (!file.exists()) {
			file.createNewFile();
		}

		FileOutputStream fos = new FileOutputStream(file);
		ObjectOutputStream out = new ObjectOutputStream(fos);
		for (ExperimentResult experimentResult : ers) {
			out.writeObject(experimentResult);
		}
		out.flush();
		out.close();
		System.out.println("**************************************___WRITTEN___: " + objectFileName
				+ ".bin___***************************************************");
		return file;
	}

	public void rankResults(File toRank, File output) throws ClassNotFoundException, IOException {
		if (!output.exists()) {
			output.mkdirs();
		}
		ResultComparator rc = new ResultComparator();
		rc.addResults(toRank);
		int numberOfClasses = rc.getAllResults().iterator().next().getNumberOfClasses();
		for (EvaluationValue v : EvaluationValue.values()) {
			for (int classID = 0; classID <= numberOfClasses; classID++) {
				rc.rankAll(v, classID, output.getAbsolutePath());
			}
		}
	}

	public SingleToMultiClassConverter getStmc() {
		return stmc;
	}

}

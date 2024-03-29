package de.uni_koeln.spinfo.classification.zoneAnalysis.data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.uni_koeln.spinfo.classification.core.data.ClassifyUnit;
import de.uni_koeln.spinfo.classification.zoneAnalysis.helpers.SingleToMultiClassConverter;

public class ZoneClassifyUnit extends ClassifyUnit{
	
	protected int actualClassID;
	boolean[] classIDs;
	private static int NUMBEROFSINGLECLASSES;
	private static int NUMBEROFMULTICLASSES;
	private static SingleToMultiClassConverter CONVERTER;	
	

	
	public ZoneClassifyUnit(String content, UUID id){
		super(content,id);
		//this.classIDs = new boolean[8];
		this.actualClassID = -1;
	}
	
	public ZoneClassifyUnit(String content){
		super(content, UUID.randomUUID());
		//this.classIDs = new boolean[8];
		this.actualClassID = -1;
	}
	

	
	
	public static void setNumberOfCategories(int categoriesNo){
		setNumberOfCategories(categoriesNo, categoriesNo, null);
	}
	

	public static void setNumberOfCategories(int categoriesNo, int classesNo, Map<Integer, List<Integer>> translations){
		NUMBEROFMULTICLASSES = categoriesNo;
		NUMBEROFSINGLECLASSES = classesNo;
		CONVERTER = new SingleToMultiClassConverter(NUMBEROFSINGLECLASSES, NUMBEROFMULTICLASSES, translations);
	}
	
	
	
	public boolean[] getClassIDs() {
		return classIDs;
	}	

	public void setClassIDs(boolean[] classIDs) {
		this.classIDs = classIDs;
	}
	
	public int getActualClassID() {
		return actualClassID;
	}

	public void setActualClassID(int classID){
		this.actualClassID = classID;
		if(CONVERTER != null){
			classIDs = CONVERTER.getMultiClasses(classID);
		}
		else {
			classIDs = new boolean[NUMBEROFSINGLECLASSES];
			if(classID >=0){
				classIDs[classID-1] = true;
			}
		}	
	}
	

}

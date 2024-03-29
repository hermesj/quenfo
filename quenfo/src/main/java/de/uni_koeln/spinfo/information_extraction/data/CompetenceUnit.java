package de.uni_koeln.spinfo.information_extraction.data;

import is2.data.SentenceData09;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompetenceUnit {

	private String sentence;
	private List<Competence> competences;
	private SentenceData09 sentenceData = new SentenceData09();
	private DependencyTree tree;
	private UUID classifyUnitID;
	private int jobAdID;
	private int secondJobAdID;

	public int getSecondJobAdID() {
		return secondJobAdID;
	}

	public void setSecondJobAdID(int secondJobAdID) {
		this.secondJobAdID = secondJobAdID;
	}

	public UUID getClassifyUnitID() {
		return classifyUnitID;
	}

	public void setClassifyUnitID(UUID classifyUnitID) {
		this.classifyUnitID = classifyUnitID;
	}

	public int getJobAdID() {
		return jobAdID;
	}

	public void setJobAdID(int jobAdID) {
		this.jobAdID = jobAdID;
	}

	public CompetenceUnit(String sentence) {
		this.sentence = sentence;
		competences = new ArrayList<Competence>();
	}

	public CompetenceUnit() {

	}

	public void setDependencyTree(DependencyTree tree) {
		this.tree = tree;
	}

	public DependencyTree getDependencyTree() {
		return tree;
	}

	public String[] getTokens() {
		return sentenceData.forms;
	}

	public String[] getLemmata() {
		return sentenceData.plemmas;
	}

	public String[] getMorphTags() {
		return sentenceData.pfeats;
	}

	public String[] getPosTags() {
		return sentenceData.ppos;
	}

	public void setSentenceData(SentenceData09 sentenceData) {
		this.sentenceData = sentenceData;
	}

	public SentenceData09 getSentenceData() {
		return sentenceData;
	}

	public void setCompetences(List<Competence> competences) {
		if (this.competences == null) {
			this.competences = competences;
		} else {
			this.competences.addAll(competences);
		}
	}

	public void setCompetence(Competence comp) {
		if (competences == null) {
			competences = new ArrayList<Competence>();
		}
		competences.add(comp);
	}

	public List<Competence> getCompetences() {
		return competences;
	}

	public void add(Competence c) {
		competences.add(c);
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public String getSentence() {
		return sentence;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(sentence + "\n");
		if (competences != null) {
			for (Competence comp : competences) {
				sb.append(comp.toString() + "\n");
			}
		}

		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		CompetenceUnit cu = (CompetenceUnit) o;
		return (this.getJobAdID() + this.getSecondJobAdID()+this.getClassifyUnitID().toString()+this.getSentence()).equals(cu.getJobAdID()+cu.getSecondJobAdID()+cu.getClassifyUnitID().toString()+cu.getSentence());
	}

}

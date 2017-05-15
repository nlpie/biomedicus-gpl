/*
 * Copyright (C) 2016 Regents of the University of Minnesota
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.umn.biomedicus.internal.docclass;

import edu.umn.biomedicus.framework.store.TextView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.*;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text processing used for the symptom severity annotator, as written for the 2016 i2b2 NLP Shared Task
 * Currently works on raw document text; could be modified to work on richer data (i.e., biomedicus's NLP results)
 *
 * @author Greg Finley
 */
class SeverityWekaProcessor implements Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeverityWekaProcessor.class);

    // Build this incrementally with each added document
    private Instances trainingTextInstances;

    // de-weight instances in the 'absent' and 'mild' classes to deal with sparsity issues
    private final double[] byClassWeights = {1, .3, 3, 3};

    private final List<String> classValues;

    private final boolean sortWordsByDescendingFreq;
    private final int minTermCount;
    private final Set<String> stopWords;
    private Map<String, Integer> dictionary;

    // Empty Instances objects used to maintain consistent format between individual Instance objects
    private Instances textTemplate;
    private Instances vectorTemplate;
    private final Attribute classAttribute;

    // Created specifically for the i2b2-format XML files
    private final Pattern fileTextPattern = Pattern.compile("\\|(.*)\\[report_end\\]", Pattern.DOTALL);
    private final Pattern scorePattern = Pattern.compile("score=\"(\\w+)\"");
    private final Pattern annotatedBy = Pattern.compile("annotated_by=\"(.)\"");

    /**
     * Initialize this processor
     * @param stopWords an optional set of words to exclude from the vector space
     * @param minTermCount minimum number of occurrences to use a term in the vector space (2 is a good value)
     * @param sortWordsByDescendingFreq whether to sort words by global frequency (this helps attribute selection)
     */
    SeverityWekaProcessor(@Nullable Set<String> stopWords, int minTermCount, boolean sortWordsByDescendingFreq) {
        this.stopWords = stopWords == null ? new HashSet<>() : stopWords;
        this.sortWordsByDescendingFreq = sortWordsByDescendingFreq;
        this.minTermCount = minTermCount;

        ArrayList<Attribute> textInstanceAttributes = new ArrayList<>();
        classValues = Arrays.asList("ABSENT", "MILD", "MODERATE", "SEVERE", SeverityClassifierModel.UNK);
        classAttribute = new Attribute("_class", classValues);
        textInstanceAttributes.add(classAttribute);
        textInstanceAttributes.add(new Attribute("text", (List<String>) null));

        textTemplate = new Instances("textTemplate", textInstanceAttributes, 0);
        textTemplate.setClassIndex(0);

        trainingTextInstances = new Instances(textTemplate);
    }

    /**
     * Once all documents have been passed, vectorize the text and return the real-valued feature vectors
     * @return Instances containing all training data
     */
    Instances getTrainingData() {
        buildDictionary(trainingTextInstances);
        return vectorizeInstances(trainingTextInstances);
    }

    /**
     * Add a document for training. Will extract this doc's text but will not train on it until getTrainingData called
     * @param document a document
     */
    void addTrainingDocument(TextView document) {
        Instance trainingInstance = getTextInstance(document.getText());
        if (trainingInstance != null) {
            trainingTextInstances.add(trainingInstance);
        }
    }

    /**
     * Convert a document into a vector instance. buildDictionary() needs to have been run.
     * @param document a document
     * @return an Instance with real-valued data
     */
    Instance getTestData(TextView document) {
        Instance textInstance = getTextInstance(document.getText());
        Instance vectorInstance = vectorizeInstance(textInstance);
        vectorInstance.setDataset(vectorTemplate);
        return vectorInstance;
    }

    /**
     * Process the text and class of this document and put it into an Instance
     * @param docText raw text from the file (assumed XML format)
     * @return an Instance with two attributes: class, and doctext
     */
    @Nullable
    private Instance getTextInstance(String docText) {
        String fileText;
        String docClass;
        double weight = 1;
        Matcher matcher = fileTextPattern.matcher(docText);
        if(matcher.find()) {
            fileText = matcher.group(1);
        } else {
            fileText = docText;
        }
        fileText = processText(fileText);
        matcher = scorePattern.matcher(docText);
        if(matcher.find() && classValues.contains(docClass = matcher.group(1))) {
            weight *= byClassWeights[classValues.indexOf(docClass)];
        } else {
            docClass = SeverityClassifierModel.UNK;
            if(dictionary == null) {
                LOGGER.warn("Added document with unknown class during training; will ignore!");
                return null;
            }
        }
        // add the annotator as a word (this helps classification a little)
        matcher = annotatedBy.matcher(docText);
        if(matcher.find()) {
            fileText += " thisNoteAnnotatedBy" + matcher.group(1);
            if (matcher.group(1).equals("1")) {
                weight /= 2;
            }
        }

        Instance inst = new DenseInstance(2);
        inst.setDataset(textTemplate);
        inst.setValue(0, docClass);
        inst.attribute(1).addStringValue(fileText);
        inst.setValue(1, fileText);
        inst.setWeight(weight);
        return inst;
    }

    /**
     * Prepare text for vectorization (lowercasing, fixing bad line breaks, rough tokenization)
     * @param origText entire text of a document
     * @return the processed text
     */
    private String processText(String origText) {
        String text = fixTableRows(origText);
        text = text.toLowerCase();
        String[] words = text.split("\\W+");
        if(words.length > 0) {
            StringBuilder builder = new StringBuilder();
            builder.append(words[0]);
            for (int i = 1; i < words.length; i++) {
                builder.append(" ");
                builder.append(words[i]);
            }
            addBigrams(words, builder);
            return builder.toString();
        } else {
            LOGGER.warn("Empty document");
            return "";
        }
    }
    /**
     * Given a list of words and a StringBuilder, continue to build bigrams/trigrams/etc. onto the builder
     * @param words array of words in their natural order
     * @param builder an active StringBuilder
     */
    private void addBigrams(String[] words, StringBuilder builder) {
        for(int i=1; i<words.length; i++) {
            builder.append(" ");
            builder.append(words[i-1]);
            builder.append("_");
            builder.append(words[i]);
        }
    }

    /**
     * Fixes a problem common in the i2b2 text: sometimes new table lines start without any whitespace
     * @param origText text with table problems
     * @return text with line breaks inserted
     */
    private String fixTableRows(String origText) {
        String pattern = "(:.*)\n+(.*[^A-Z\\-\\( \\s/])([A-Z].*:)";
        String repl = "$1 $2\n$3";
        // have to run this a few times to be sure we get it all (adjacent ones won't both be matched)
        String fixed = origText.replaceAll(pattern, repl);
        fixed = fixed.replaceAll(pattern, repl);
        fixed = fixed.replaceAll(pattern, repl);
        return fixed;
    }

    /**
     * Builds a dictionary from known text and set the attributes for vector instances
     * In current implementation, this is done all at once, not incrementally, since total word counts must be known
     * This function must be called before vectorizing any text instances
     * @param textInstances Instances containing text (whitespace-delimited words)
     */
    private void buildDictionary(Instances textInstances) {
        Map<String, Integer> globalCounts = new LinkedHashMap<>();
        for (Instance inst : textInstances) {
            String processed = inst.stringValue(1);
            String[] words = processed.split("\\s+");
            for (String uni : words) {
                if (!stopWords.contains(uni)) {
                    if (!globalCounts.containsKey(uni)) {
                        globalCounts.put(uni, 0);
                    }
                    globalCounts.put(uni, globalCounts.get(uni) + 1);
                }
            }
        }
        List<String> sortedWords = new ArrayList<>();
        sortedWords.addAll(globalCounts.keySet());
        if (sortWordsByDescendingFreq) {
            sortedWords.sort((word1, word2) -> {
                int cmp = Integer.compare(globalCounts.get(word2), globalCounts.get(word1));
                return cmp == 0 ? word2.compareTo(word1) : cmp;
            });
        }
        dictionary = new HashMap<>();
        ArrayList<Attribute> vectorInstanceAttributes = new ArrayList<>();
        vectorInstanceAttributes.add(classAttribute);
        for (String word : sortedWords) {
            if (globalCounts.get(word) >= minTermCount) {
                dictionary.put(word, dictionary.size());
                vectorInstanceAttributes.add(new Attribute(word));
            }
        }
        vectorTemplate = new Instances("vectorTemplate", vectorInstanceAttributes, 0);
        vectorTemplate.setClassIndex(0);
    }

    /**
     * Vectorize a bunch of text instances and put them into a single Instances object, probably to train a classifier
     * @param textInstances Instances that have a class and text attribute
     * @return Instances that have a class and many real-valued attributes
     */
    private Instances vectorizeInstances(Instances textInstances) {
        List<Instance> listInstance = new ArrayList<>();
        for(Instance textInstance : textInstances) {
            listInstance.add(vectorizeInstance(textInstance));
        }
        Instances vectorized = new Instances(vectorTemplate, textInstances.numInstances());
        for(Instance inst : listInstance) vectorized.add(inst);
        return vectorized;
    }

    /**
     * Vectorize a text instance, probably for a classifier to evaluate
     * @param textInstance Instance that has a class and text attribute
     * @return Instance that has a class and many real-valued attributes
     */
    private Instance vectorizeInstance(Instance textInstance) {
        // Put the class and word counts for this doc into an array, then build an Instance from that
        // counts[0] is the doc class, not actually a word count
        double[] counts = new double[dictionary.size() + 1];
        counts[0] = textInstance.classValue();
        String processed = textInstance.stringValue(1);
        String[] words = processed.split("\\s+");
        for (String uni : words) {
            if (!stopWords.contains(uni) && dictionary.containsKey(uni)) {
                counts[dictionary.get(uni) + 1]++;
            }
        }
        Instance vec = new SparseInstance(1, counts);
        vec.setWeight(textInstance.weight());
        return vec;
    }

}

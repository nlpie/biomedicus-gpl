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

import com.google.inject.Inject;
import edu.umn.biomedicus.annotations.ProcessorScoped;
import edu.umn.biomedicus.annotations.ProcessorSetting;
import edu.umn.biomedicus.application.PostProcessor;
import edu.umn.biomedicus.application.TextView;
import edu.umn.biomedicus.exc.BiomedicusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Train a Weka model to classify documents according to symptom severity
 * Created for the 2016 i2b2 NLP Shared Task
 *
 * @author Greg Finley
 */
@ProcessorScoped
public class SeverityClassifierTrainer implements PostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeverityClassifierTrainer.class);

    private final Path outPath;
    private final SeverityWekaProcessor wekaProcessor;
    private final int attributesToKeep;

    /**
     * Initialize this trainer. If the stopwords file is not present or can't be read from, trainer will still work
     * @param outPath the path to write the model to
     * @param stopWordsPath path to a stopwords file
     */
    @Inject
    public SeverityClassifierTrainer(@ProcessorSetting("docclass.severity.output.path") Path outPath,
                                     @ProcessorSetting("docclass.stopwords.path") @Nullable Path stopWordsPath,
                                     @ProcessorSetting("docclass.severity.attributesToKeep") Integer attributesToKeep,
                                     @ProcessorSetting("docclass.severity.minWordCount") Integer minWordCount) {
        Set<String> stopWords = null;
        if(stopWordsPath != null) {
            try {
                stopWords = Files.lines(stopWordsPath).collect(Collectors.toSet());
            } catch (IOException e) {
                LOGGER.warn("Could not load stopwords file; will not exclude stopwords");
            }
        }
        this.outPath = outPath;
        this.attributesToKeep = attributesToKeep;
        wekaProcessor = new SeverityWekaProcessor(stopWords, minWordCount, true);
    }

    /**
     * Add the document to the collection, which will be trained all at once at the end
     * @param document a document
     */
    public void processDocument(TextView document) {
        wekaProcessor.addTrainingDocument(document);
    }

    @Override
    public void afterProcessing() throws BiomedicusException {
        Instances trainSet = wekaProcessor.getTrainingData();
        Classifier classifier = new SMO();
        AttributeSelection sel = new AttributeSelection();
        ASEvaluation infogain = new InfoGainAttributeEval();
        Ranker ranker = new Ranker();
        Remove remove = new Remove();

        ranker.setNumToSelect(attributesToKeep);
        sel.setEvaluator(infogain);
        sel.setSearch(ranker);

        try {
            sel.SelectAttributes(trainSet);
            int[] selected = sel.selectedAttributes();
            remove.setInvertSelection(true);
            remove.setAttributeIndicesArray(selected);
            remove.setInputFormat(trainSet);
            trainSet = Filter.useFilter(trainSet, remove);
            classifier.buildClassifier(trainSet);
        } catch (Exception e) {
            throw new BiomedicusException();
        }

        SeverityClassifierModel model = new SeverityClassifierModel(classifier, remove, wekaProcessor);

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outPath.toFile()));
            oos.writeObject(model);
            oos.close();
        } catch(IOException e) {
            throw new BiomedicusException();
        }
    }
}

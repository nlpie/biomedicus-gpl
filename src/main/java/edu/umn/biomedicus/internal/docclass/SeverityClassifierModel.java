/*
 * Copyright (C) 2018 Regents of the University of Minnesota
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
import com.google.inject.ProvidedBy;
import edu.umn.biomedicus.annotations.ProcessorScoped;
import edu.umn.biomedicus.annotations.ProcessorSetting;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DataLoader;
import edu.umn.nlpengine.Document;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.filters.Filter;

/**
 * Classify documents based on symptom severity
 * Uses a Weka classifier with attribute selection
 *
 * @author Greg Finley
 */
@ProvidedBy(SeverityClassifierModel.Loader.class)
public class SeverityClassifierModel implements Serializable {

  // For unknown classes (test data or poorly formatted training data)
  static final String UNK = "unknown";
  private final Classifier classifier;
  private final Filter attSel;
  private final SeverityWekaProcessor severityWekaProcessor;

  private final Map<Double, String> severityMap;

  /**
   * Initialize this model
   * All training happens in the trainer; just store what we need to keep for classification
   *
   * @param classifier a Weka Classifier object
   * @param attSel an attribute selection object
   * @param severityWekaProcessor a processor to convert Document objects into Weka Instance
   * objects
   */
  SeverityClassifierModel(
      Classifier classifier,
      Filter attSel,
      SeverityWekaProcessor severityWekaProcessor
  ) {
    severityMap = new HashMap<>();
    severityMap.put(0., "ABSENT");
    severityMap.put(1., "MILD");
    severityMap.put(2., "MODERATE");
    severityMap.put(3., "SEVERE");
    severityMap.put(4., UNK);
    this.classifier = classifier;
    this.attSel = attSel;
    this.severityWekaProcessor = severityWekaProcessor;
  }

  /**
   * Perform attribute selection and then classification using the stored Weka objects
   * Where classes are tied, err on the side of higher class
   *
   * @param document the text
   * @return a string (from the predefined classes) representing this text's symptom severity
   */
  public String predict(Document document) {
    Instance inst = severityWekaProcessor.getTestData(document);
    double result;
    try {
      if (attSel.input(inst)) {
        inst = attSel.output();
        double[] dist = classifier.distributionForInstance(inst);
        result = -1;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < dist.length; i++) {
          if (dist[i] >= max) {
            max = dist[i];
            result = i;
          }
        }
      } else {
        throw new RuntimeException();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return severityMap.get(result);
  }

  public String getMetadataKey() {
    return "Severity";
  }

  /**
   * Load a serialized model
   */
  @ProcessorScoped
  static class Loader extends DataLoader<SeverityClassifierModel> {

    private final Path modelPath;

    @Inject
    public Loader(@ProcessorSetting("docclass.severity.model.path") Path modelPath) {
      this.modelPath = modelPath;
    }

    @Override
    protected SeverityClassifierModel loadModel() throws BiomedicusException {
      try {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelPath.toFile()));
        return (SeverityClassifierModel) ois.readObject();
      } catch (Exception e) {
        throw new BiomedicusException();
      }
    }
  }

}

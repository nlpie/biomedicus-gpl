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
import edu.umn.biomedicus.common.TextIdentifiers;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DocumentProcessor;
import edu.umn.nlpengine.Document;
import edu.umn.nlpengine.LabeledText;

public class SeverityClassifier implements DocumentProcessor {

  private final SeverityClassifierModel severityClassifierModel;

  @Inject
  public SeverityClassifier(SeverityClassifierModel severityClassifierModel) {
    this.severityClassifierModel = severityClassifierModel;
  }

  @Override
  public void process(Document document) throws BiomedicusException {
    LabeledText system = TextIdentifiers.getSystemLabeledText(document);
    String prediction = severityClassifierModel.predict(system);
    document.getMetadata().put("Severity", prediction);
  }
}

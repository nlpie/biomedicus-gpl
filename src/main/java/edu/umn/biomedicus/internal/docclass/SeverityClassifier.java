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
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DocumentProcessor;
import edu.umn.biomedicus.framework.store.Document;
import edu.umn.biomedicus.framework.store.TextView;

public class SeverityClassifier implements DocumentProcessor {

  private final SeverityClassifierModel severityClassifierModel;
  private final Document document;
  private final TextView textView;

  @Inject
  public SeverityClassifier(SeverityClassifierModel severityClassifierModel, Document document,
      TextView textView) {
    this.severityClassifierModel = severityClassifierModel;
    this.document = document;
    this.textView = textView;
  }

  @Override
  public void process() throws BiomedicusException {
    String prediction = severityClassifierModel.predict(textView);
    document.putMetadata("Severity", prediction);
  }
}

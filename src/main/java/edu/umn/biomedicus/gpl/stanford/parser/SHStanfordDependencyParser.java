/*
 * Copyright (C) 2017 Regents of the University of Minnesota
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

package edu.umn.biomedicus.gpl.stanford.parser;

import edu.umn.biomedicus.annotations.ProcessorSetting;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DocumentProcessor;
import edu.umn.biomedicus.framework.store.Document;
import edu.umn.biomedicus.framework.store.TextView;
import edu.umn.biomedicus.parsing.DependencyParse;
import edu.umn.biomedicus.sh.SocialHistoryCandidate;
import edu.umn.biomedicus.tagging.PosTag;
import edu.umn.biomedicus.tokenization.ParseToken;
import edu.umn.nlpengine.LabelIndex;
import edu.umn.nlpengine.Labeler;
import javax.annotation.Nonnull;
import javax.inject.Inject;

public class SHStanfordDependencyParser implements DocumentProcessor {

  private final StanfordDependencyParserModel model;

  private final String viewName;

  @Inject
  public SHStanfordDependencyParser(
      StanfordDependencyParserModel model,
      @ProcessorSetting("viewName") String viewName
  ) {
    this.model = model;
    this.viewName = viewName;
  }

  @Override
  public void process(@Nonnull Document document) throws BiomedicusException {
    TextView view = document.getTextView(viewName)
        .orElseThrow(() -> new BiomedicusException("View not found: " + viewName));

    LabelIndex<SocialHistoryCandidate> candidates = view.getLabelIndex(
        SocialHistoryCandidate.class
    );

    LabelIndex<ParseToken> tokens = view.getLabelIndex(ParseToken.class);
    LabelIndex<PosTag> posTags = view.getLabelIndex(PosTag.class);
    Labeler<DependencyParse> labeler = view.getLabeler(DependencyParse.class);

    for (SocialHistoryCandidate candidate : candidates) {
      String parse = model.parseSentence(
          tokens.insideSpan(candidate).asList(),
          posTags.insideSpan(candidate).asList()
      );
      labeler.add(new DependencyParse(candidate, parse));
    }
  }
}

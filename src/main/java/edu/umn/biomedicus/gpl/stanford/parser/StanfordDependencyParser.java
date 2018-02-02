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

package edu.umn.biomedicus.gpl.stanford.parser;

import edu.umn.biomedicus.common.TextIdentifiers;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DocumentProcessor;
import edu.umn.nlpengine.Document;
import edu.umn.nlpengine.LabeledText;
import edu.umn.biomedicus.parsing.DependencyParse;
import edu.umn.biomedicus.sentences.Sentence;
import edu.umn.biomedicus.tagging.PosTag;
import edu.umn.biomedicus.tokenization.ParseToken;
import edu.umn.nlpengine.LabelIndex;
import edu.umn.nlpengine.Labeler;
import javax.annotation.Nonnull;
import javax.inject.Inject;

public class StanfordDependencyParser implements DocumentProcessor {

  private final StanfordDependencyParserModel model;

  @Inject
  StanfordDependencyParser(StanfordDependencyParserModel model) {
    this.model = model;
  }

  @Override
  public void process(@Nonnull Document document) throws BiomedicusException {
    LabeledText view = TextIdentifiers.getSystemLabeledText(document);

    LabelIndex<Sentence> sentences = view.labelIndex(Sentence.class);
    LabelIndex<ParseToken> tokens = view.labelIndex(ParseToken.class);
    LabelIndex<PosTag> posTags = view.labelIndex(PosTag.class);
    Labeler<DependencyParse> labeler = view.labeler(DependencyParse.class);

    for (Sentence sentence : sentences) {
      String parse = model.parseSentence(
          tokens.insideSpan(sentence).asList(),
          posTags.insideSpan(sentence).asList()
      );
      labeler.add(new DependencyParse(sentence, parse));
    }
  }
}

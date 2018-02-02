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

import edu.umn.biomedicus.annotations.ProcessorSetting;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DocumentProcessor;
import edu.umn.nlpengine.Document;
import edu.umn.nlpengine.LabeledText;
import edu.umn.biomedicus.parsing.ConstituencyParse;
import edu.umn.biomedicus.sentences.Sentence;
import edu.umn.biomedicus.tagging.PosTag;
import edu.umn.biomedicus.tokenization.ParseToken;
import edu.umn.nlpengine.LabelIndex;
import edu.umn.nlpengine.Labeler;
import javax.inject.Inject;

public class StanfordConstituencyParser implements DocumentProcessor {

  private final StanfordConstituencyParserModel stanfordConstituencyParserModel;
  private final String viewName;

  @Inject
  public StanfordConstituencyParser(
      StanfordConstituencyParserModel stanfordConstituencyParserModel,
      @ProcessorSetting("viewName") String viewName
  ) {
    this.stanfordConstituencyParserModel = stanfordConstituencyParserModel;
    this.viewName = viewName;
  }

  @Override
  public void process(Document document) throws BiomedicusException {
    LabeledText view = document.getLabeledTexts().get(viewName);

    if (view == null) {
      throw new BiomedicusException("View not found: " + viewName);
    }

    LabelIndex<Sentence> sentences = view.labelIndex(Sentence.class);
    LabelIndex<ParseToken> parseTokenLabelIndex = view.labelIndex(ParseToken.class);
    LabelIndex<PosTag> partOfSpeechLabelIndex = view.labelIndex(PosTag.class);
    Labeler<ConstituencyParse> labeler = view.labeler(ConstituencyParse.class);

    for (Sentence sentence : sentences) {
      stanfordConstituencyParserModel.parseSentence(sentence, parseTokenLabelIndex,
          partOfSpeechLabelIndex, labeler);
    }
  }
}

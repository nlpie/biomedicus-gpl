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

import edu.umn.biomedicus.parsing.ConstituencyParse;
import edu.umn.biomedicus.sentences.Sentence;
import edu.umn.biomedicus.tagging.PosTag;
import edu.umn.biomedicus.tokenization.ParseToken;
import edu.umn.nlpengine.Document;
import edu.umn.nlpengine.DocumentTask;
import edu.umn.nlpengine.LabelIndex;
import edu.umn.nlpengine.Labeler;
import javax.annotation.Nonnull;
import javax.inject.Inject;

public class StanfordConstituencyParser implements DocumentTask {

  private final StanfordConstituencyParserModel stanfordConstituencyParserModel;

  @Inject
  public StanfordConstituencyParser(
      StanfordConstituencyParserModel stanfordConstituencyParserModel
  ) {
    this.stanfordConstituencyParserModel = stanfordConstituencyParserModel;
  }

  @Override
  public void run(@Nonnull Document document) {
    LabelIndex<Sentence> sentences = document.labelIndex(Sentence.class);
    LabelIndex<ParseToken> parseTokenLabelIndex = document.labelIndex(ParseToken.class);
    LabelIndex<PosTag> partOfSpeechLabelIndex = document.labelIndex(PosTag.class);
    Labeler<ConstituencyParse> labeler = document.labeler(ConstituencyParse.class);

    for (Sentence sentence : sentences) {
      stanfordConstituencyParserModel.parseSentence(sentence, parseTokenLabelIndex,
          partOfSpeechLabelIndex, labeler);
    }
  }
}

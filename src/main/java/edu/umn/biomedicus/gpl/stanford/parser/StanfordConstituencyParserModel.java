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

import com.google.inject.ProvidedBy;
import com.google.inject.Singleton;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser;
import edu.stanford.nlp.trees.Tree;
import edu.umn.biomedicus.annotations.Setting;
import edu.umn.biomedicus.common.types.syntax.PartOfSpeech;
import edu.umn.biomedicus.common.types.syntax.PartsOfSpeech;
import edu.umn.biomedicus.exc.BiomedicusException;
import edu.umn.biomedicus.framework.DataLoader;
import edu.umn.biomedicus.parsing.ConstituencyParse;
import edu.umn.biomedicus.tagging.PosTag;
import edu.umn.biomedicus.tokenization.ParseToken;
import edu.umn.nlpengine.TextRange;
import edu.umn.nlpengine.LabelIndex;
import edu.umn.nlpengine.Labeler;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

@Singleton
@ProvidedBy(StanfordConstituencyParserModel.Loader.class)
public class StanfordConstituencyParserModel {

  private final ShiftReduceParser shiftReduceParser;

  private StanfordConstituencyParserModel(ShiftReduceParser shiftReduceParser) {
    this.shiftReduceParser = shiftReduceParser;
  }

  public void parseSentence(
      TextRange sentenceLabel,
      LabelIndex<ParseToken> parseTokenLabelIndex,
      LabelIndex<PosTag> partOfSpeechLabelIndex,
      Labeler<ConstituencyParse> constituencyParseLabeler
  ) {
    List<TaggedWord> taggedWordList = new ArrayList<>();
    for (ParseToken parseTokenLabel : parseTokenLabelIndex.insideSpan(sentenceLabel)) {
      String word = parseTokenLabel.getText();
      PartOfSpeech partOfSpeech = partOfSpeechLabelIndex.firstAtLocation(parseTokenLabel)
          .getPartOfSpeech();

      TaggedWord taggedWord = new TaggedWord(word, PartsOfSpeech.tagForPartOfSpeech(partOfSpeech));
      taggedWordList.add(taggedWord);
    }
    Tree tree = shiftReduceParser.apply(taggedWordList);
    StringWriter stringWriter = new StringWriter();
    tree.pennPrint(new PrintWriter(stringWriter));
    String pennPrint = stringWriter.toString();
    ConstituencyParse constituencyParse = new ConstituencyParse(sentenceLabel, pennPrint);
    constituencyParseLabeler.add(constituencyParse);
  }

  @Singleton
  public static class Loader extends DataLoader<StanfordConstituencyParserModel> {

    private final Path path;

    @Inject
    public Loader(@Setting("stanford.srParser.path") Path path) {
      this.path = path;
    }

    @Override
    protected StanfordConstituencyParserModel loadModel() {
      ShiftReduceParser shiftReduceParser = ShiftReduceParser.loadModel(path.toString());
      return new StanfordConstituencyParserModel(shiftReduceParser);
    }
  }
}

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
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.umn.biomedicus.annotations.Setting;
import edu.umn.biomedicus.common.types.syntax.PartsOfSpeech;
import edu.umn.biomedicus.framework.DataLoader;
import edu.umn.biomedicus.gpl.stanford.parser.StanfordDependencyParserModel.Loader;
import edu.umn.biomedicus.tagging.PosTag;
import edu.umn.biomedicus.tokenization.ParseToken;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@ProvidedBy(Loader.class)
public class StanfordDependencyParserModel {

  private final DependencyParser parser;

  private StanfordDependencyParserModel(DependencyParser parser) {
    this.parser = parser;
  }

  public String parseSentence(
      List<ParseToken> tokens,
      List<PosTag> posTags
  ) {
    GrammaticalStructure structure = parseToGrammaticalStructure(tokens, posTags);
    return structure.typedDependencies().toString();
  }

  public GrammaticalStructure parseToGrammaticalStructure(
      List<ParseToken> tokens,
      List<PosTag> posTags
  ) {
    int size = tokens.size();
    List<TaggedWord> taggedWordList = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      ParseToken parseToken = tokens.get(i);
      PosTag posTag = posTags.get(i);
      TaggedWord taggedWord = new TaggedWord(parseToken.getText(),
          PartsOfSpeech.tagForPartOfSpeech(posTag.getPartOfSpeech()));
      taggedWordList.add(taggedWord);
    }
    return parser.predict(taggedWordList);
  }

  @Singleton
  public static class Loader extends DataLoader<StanfordDependencyParserModel> {
    private final Path path;

    @Inject
    public Loader(@Setting("stanford.depParserPath") Path path) {
      this.path = path;
    }

    @Override
    protected StanfordDependencyParserModel loadModel() {
      DependencyParser parser = DependencyParser.loadFromModelFile(path.toString());
      return new StanfordDependencyParserModel(parser);
    }
  }
}

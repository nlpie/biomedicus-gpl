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

package edu.umn.biomedicus.gpl.stanford.parser;

import com.google.inject.ProvidedBy;
import com.google.inject.Singleton;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser;
import edu.stanford.nlp.trees.Tree;
import edu.umn.biomedicus.annotations.Setting;
import edu.umn.biomedicus.application.DataLoader;
import edu.umn.biomedicus.common.labels.Label;
import edu.umn.biomedicus.common.labels.LabelIndex;
import edu.umn.biomedicus.common.labels.Labeler;
import edu.umn.biomedicus.common.types.syntax.PartOfSpeech;
import edu.umn.biomedicus.common.types.syntax.PartsOfSpeech;
import edu.umn.biomedicus.common.types.text.ConstituencyParse;
import edu.umn.biomedicus.common.types.text.ParseToken;
import edu.umn.biomedicus.exc.BiomedicusException;

import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
@ProvidedBy(StanfordConstituencyParserModel.Loader.class)
public class StanfordConstituencyParserModel {
    private final ShiftReduceParser shiftReduceParser;

    private StanfordConstituencyParserModel(ShiftReduceParser shiftReduceParser) {
        this.shiftReduceParser = shiftReduceParser;
    }

    void parseSentence(Label<?> sentenceLabel,
                       LabelIndex<ParseToken> parseTokenLabelIndex,
                       LabelIndex<PartOfSpeech> partOfSpeechLabelIndex,
                       Labeler<ConstituencyParse> constituencyParseLabeler) throws BiomedicusException {
        List<TaggedWord> taggedWordList = new ArrayList<>();
        for (Label<ParseToken> parseTokenLabel : parseTokenLabelIndex.insideSpan(sentenceLabel)) {
            String word = parseTokenLabel.value().text();
            PartOfSpeech partOfSpeech = partOfSpeechLabelIndex.withTextLocation(parseTokenLabel)
                    .orElseThrow(() -> new BiomedicusException("parse token did not have part of speech."))
                    .value();

            TaggedWord taggedWord = new TaggedWord(word, PartsOfSpeech.tagForPartOfSpeech(partOfSpeech));
            taggedWordList.add(taggedWord);
        }
        Tree tree = shiftReduceParser.apply(taggedWordList);
        StringWriter stringWriter = new StringWriter();
        tree.pennPrint(new PrintWriter(stringWriter));
        String pennPrint = stringWriter.toString();
        ConstituencyParse constituencyParse = new ConstituencyParse(pennPrint);
        constituencyParseLabeler.value(constituencyParse).label(sentenceLabel);
    }

    @Singleton
    public static class Loader extends DataLoader<StanfordConstituencyParserModel> {
        private final Path path;

        @Inject
        public Loader(@Setting("stanford.srParser.path") Path path) {
            this.path = path;
        }

        @Override
        protected StanfordConstituencyParserModel loadModel() throws BiomedicusException {
            ShiftReduceParser shiftReduceParser = ShiftReduceParser.loadModel(path.toString());
            return new StanfordConstituencyParserModel(shiftReduceParser);
        }
    }
}

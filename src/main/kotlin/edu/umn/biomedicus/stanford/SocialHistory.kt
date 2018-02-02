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

package edu.umn.biomedicus.stanford

import edu.umn.biomedicus.annotations.ProcessorSetting
import edu.umn.biomedicus.exc.BiomedicusException
import edu.umn.biomedicus.framework.DocumentProcessor
import edu.umn.biomedicus.gpl.stanford.parser.StanfordConstituencyParserModel
import edu.umn.biomedicus.gpl.stanford.parser.StanfordDependencyParserModel
import edu.umn.biomedicus.parsing.ConstituencyParse
import edu.umn.biomedicus.parsing.DependencyParse
import edu.umn.biomedicus.sentences.Sentence
import edu.umn.biomedicus.sh.NicotineCandidate
import edu.umn.biomedicus.tagging.PosTag
import edu.umn.biomedicus.tokenization.ParseToken
import edu.umn.nlpengine.Document
import javax.inject.Inject

class SHParser @Inject constructor(
        private val stanfordConstituencyParserModel: StanfordConstituencyParserModel,
        private val stanfordDependencyParserModel: StanfordDependencyParserModel,
        @ProcessorSetting("viewName") val viewName: String
) : DocumentProcessor {
    override fun process(document: Document) {
        val view = document.labeledTexts[viewName] ?: throw BiomedicusException("View not found: " + viewName)

        val sentences = view.labelIndex(Sentence::class)

        val parseTokens = view.labelIndex(ParseToken::class)
        val posTags = view.labelIndex(PosTag::class)

        val smokingCandidates = view.labelIndex(NicotineCandidate::class)

        val dependencyParseLabeler = view.labeler(DependencyParse::class)
        val constituencyParseLabeler = view.labeler(ConstituencyParse::class)

        for (sentence in sentences) {
            if (smokingCandidates.containsSpan(sentence)) {
                stanfordConstituencyParserModel.parseSentence(sentence, parseTokens, posTags,
                        constituencyParseLabeler)
                dependencyParseLabeler.add(DependencyParse(
                        sentence,
                        stanfordDependencyParserModel.parseSentence(
                                parseTokens.insideSpan(sentence).asList(),
                                posTags.insideSpan(sentence).asList()
                        )
                ))
            }
        }
    }
}

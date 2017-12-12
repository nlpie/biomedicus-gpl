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

package edu.umn.biomedicus.stanford

import edu.umn.biomedicus.annotations.ProcessorSetting
import edu.umn.biomedicus.exc.BiomedicusException
import edu.umn.biomedicus.framework.DocumentProcessor
import edu.umn.biomedicus.framework.store.Document
import edu.umn.biomedicus.getLabelIndex
import edu.umn.biomedicus.getLabeler
import edu.umn.biomedicus.gpl.stanford.parser.StanfordConstituencyParserModel
import edu.umn.biomedicus.gpl.stanford.parser.StanfordDependencyParserModel
import edu.umn.biomedicus.parsing.ConstituencyParse
import edu.umn.biomedicus.parsing.DependencyParse
import edu.umn.biomedicus.sentences.Sentence
import edu.umn.biomedicus.sh.SmokingCandidate
import edu.umn.biomedicus.tagging.PosTag
import edu.umn.biomedicus.tokenization.ParseToken
import javax.inject.Inject

class SHParser @Inject constructor(
        private val stanfordConstituencyParserModel: StanfordConstituencyParserModel,
        private val stanfordDependencyParserModel: StanfordDependencyParserModel,
        @ProcessorSetting("viewName") val viewName: String
) : DocumentProcessor {
    override fun process(document: Document) {
        val view = document.getTextView(viewName)
                .orElseThrow { BiomedicusException("View not found: " + viewName) }

        val sentences = view.getLabelIndex(Sentence::class)

        val parseTokens = view.getLabelIndex(ParseToken::class)
        val posTags = view.getLabelIndex(PosTag::class)

        val smokingCandidates = view.getLabelIndex(SmokingCandidate::class)

        val dependencyParseLabeler = view.getLabeler(DependencyParse::class)
        val constituencyParseLabeler = view.getLabeler(ConstituencyParse::class)

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
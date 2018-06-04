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

import edu.umn.biomedicus.gpl.stanford.parser.StanfordDependencyParserModel
import edu.umn.biomedicus.parsing.Dependency
import edu.umn.biomedicus.parsing.DependencyParse
import edu.umn.biomedicus.sentences
import edu.umn.biomedicus.sh.AlcoholCandidate
import edu.umn.biomedicus.sh.DrugCandidate
import edu.umn.biomedicus.sh.NicotineCandidate
import edu.umn.biomedicus.tagging.PosTag
import edu.umn.biomedicus.tokens
import edu.umn.nlpengine.Document
import edu.umn.nlpengine.DocumentOperation
import edu.umn.nlpengine.labelIndex
import edu.umn.nlpengine.labeler
import javax.inject.Inject

class SHParser @Inject constructor(
        private val stanfordDependencyParserModel: StanfordDependencyParserModel
) : DocumentOperation {
    override fun process(document: Document) {
        val sentences = document.sentences()

        val parseTokens = document.tokens()
        val posTags = document.labelIndex<PosTag>()

        val alcoholCandidates = document.labelIndex<AlcoholCandidate>()
        val drugCandidates = document.labelIndex<DrugCandidate>()
        val nicotineCandidates = document.labelIndex<NicotineCandidate>()

        val dependencyLabeler = document.labeler<Dependency>()
        val dependencyParseLabeler = document.labeler<DependencyParse>()

        sentences
                .filter {
                    alcoholCandidates.containsSpan(it) || drugCandidates.containsSpan(it)
                            || nicotineCandidates.containsSpan(it)
                }
                .forEach {
                    val sentenceTokens = parseTokens.inside(it).asList()
                    val sentenceTags = posTags.inside(it).asList()

                    val grammaticalStructure = stanfordDependencyParserModel
                            .parseToGrammaticalStructure(sentenceTokens, sentenceTags)

                    labelDependencyParse(
                            grammaticalStructure = grammaticalStructure,
                            sentence = it,
                            tokens = sentenceTokens,
                            dependencyLabeler = dependencyLabeler,
                            rootLabeler = dependencyParseLabeler
                    )
                }
    }
}

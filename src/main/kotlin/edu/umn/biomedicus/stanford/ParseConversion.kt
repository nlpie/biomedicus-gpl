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

import edu.stanford.nlp.trees.GrammaticalStructure
import edu.stanford.nlp.trees.TypedDependency
import edu.umn.biomedicus.parsing.Dependency
import edu.umn.biomedicus.parsing.DependencyParse
import edu.umn.biomedicus.parsing.getUniversalDependencyRelation
import edu.umn.biomedicus.sentences.Sentence
import edu.umn.biomedicus.tokenization.ParseToken
import edu.umn.nlpengine.Labeler
import java.util.*

/**
 * Labels the dependencies from CoreNLP's [GrammaticalStructure].
 */
fun labelDependencyParse(
        grammaticalStructure: GrammaticalStructure,
        sentence: Sentence,
        tokens: List<ParseToken>,
        dependencyLabeler: Labeler<Dependency>,
        rootLabeler: Labeler<DependencyParse>
) {

    val dependencies = mutableListOf<Pair<Int, Dependency>>()

    val arrayDeque = ArrayDeque<TypedDependency>()
    arrayDeque.addAll(grammaticalStructure.typedDependencies())
    while (arrayDeque.isNotEmpty()) {
        val typedDependency = arrayDeque.removeFirst()
        val govIndex = typedDependency.gov().index() - 1
        val depIndex = typedDependency.dep().index() - 1
        if (depIndex == -1) {
            continue
        }
        govIndex.takeIf { it != -1 }
        val head = if (govIndex != -1) {
            val gov = dependencies.find { it.first == govIndex }
            if (gov == null) {
                arrayDeque.addLast(typedDependency)
                continue
            } else gov.second
        } else null

        val relation = typedDependency.reln()
        val dep = tokens[depIndex]
        if (relation.shortName == "root") {
            rootLabeler.add(DependencyParse(sentence, dep))
        }
        Dependency(
                dep = dep,
                relation = getUniversalDependencyRelation(relation.shortName.substringBefore(':')),
                head = head
        ).let {
            dependencyLabeler.add(it)
            dependencies.add(Pair(depIndex, it))
        }
    }
}

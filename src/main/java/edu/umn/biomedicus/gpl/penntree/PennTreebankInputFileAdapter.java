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

package edu.umn.biomedicus.gpl.penntree;

import com.google.inject.Inject;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.PennTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.umn.biomedicus.common.types.syntax.PartOfSpeech;
import edu.umn.biomedicus.common.types.syntax.PartsOfSpeech;
import edu.umn.biomedicus.common.types.text.ImmutableParseToken;
import edu.umn.biomedicus.common.types.text.Sentence;
import edu.umn.biomedicus.framework.store.Document;
import edu.umn.biomedicus.framework.store.Label;
import edu.umn.biomedicus.framework.store.Span;
import edu.umn.biomedicus.framework.store.TextView;
import edu.umn.biomedicus.uima.adapter.UimaAdapters;
import edu.umn.biomedicus.uima.files.InputFileAdapter;
import edu.umn.biomedicus.uima.labels.LabelAdapters;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;

/**
 * Adapts Penn treebank format files to CAS files.
 *
 * @author Ben Knoll
 * @since 1.3.0
 */
public final class PennTreebankInputFileAdapter implements InputFileAdapter {

  /**
   * The penn tree reader factory.
   */
  private final PennTreeReaderFactory pennTreeReaderFactory = new PennTreeReaderFactory();

  private final LabelAdapters labelAdapters;

  /**
   * The view name to load into.
   */
  private String viewName;

  @Inject
  public PennTreebankInputFileAdapter(LabelAdapters labelAdapters) {
    this.labelAdapters = labelAdapters;
  }

  @Override
  public void adaptFile(CAS cas, Path path) throws CollectionException {
    StringBuilder text = new StringBuilder();
    ArrayList<SentenceBuilder> sentences = new ArrayList<>();
    try (Reader reader = Files.newBufferedReader(path)) {
      TreeReader treeReader = pennTreeReaderFactory.newTreeReader(reader);
      Tree tree;
      while ((tree = treeReader.readTree()) != null) {
        int sentenceStart = text.length();

        ArrayList<TaggedWord> taggedWords = tree.taggedYield();
        SentenceBuilder sentenceBuilder = new SentenceBuilder();
        sentenceBuilder.tokenBuilders = new ArrayList<>(taggedWords.size());
        for (TaggedWord taggedWord : taggedWords) {
          String tag = taggedWord.tag();
          String word = taggedWord.word();
          text.append(" ");

          /**
           * This -NONE- tag occurs in some documents when there is a assumed phrase.
           */
          if (!"-NONE-".equals(tag)) {
            int tokenStart = text.length();
            if ("-LRB-".equals(word)) {
              text.append('(');
            } else if ("-RRB-".equals(word)) {
              text.append(')');
            } else if ("-LCB-".equals(word)) {
              text.append('{');
            } else if ("-RCB-".equals(word)) {
              text.append('}');
            } else if ("-LSB-".equals(word)) {
              text.append('[');
            } else if ("-RSB-".equals(word)) {
              text.append(']');
            } else if ("``".equals(word)) {
              text.append("\"");
            } else if ("''".equals(word)) {
              text.append("\"");
            } else {
              text.append(word);
            }
            int tokenEnd = text.length();

            PartOfSpeech partOfSpeech;
            if ("-LRB-".equals(tag)) {
              partOfSpeech = PartOfSpeech.LEFT_PAREN;
            } else if ("-RRB-".equals(tag)) {
              partOfSpeech = PartOfSpeech.RIGHT_PAREN;
            } else {
              if (tag.contains("|")) {
                String[] tags = tag.split("\\|");
                Random random = new Random();
                int randomIndex = random.nextInt(tags.length);
                partOfSpeech = PartsOfSpeech.forTag(tags[randomIndex]);
              } else {
                partOfSpeech = PartsOfSpeech.forTag(tag);
              }

            }

            TokenBuilder tokenBuilder = new TokenBuilder();
            tokenBuilder.tokenSpan = new Span(tokenStart, tokenEnd);
            tokenBuilder.partOfSpeech = partOfSpeech;
            if (partOfSpeech == null) {
              throw new AssertionError("part of speech should not be null");
            }
            sentenceBuilder.tokenBuilders.add(tokenBuilder);
          }
        }

        int sentenceEnd = text.length();

        sentenceBuilder.sentenceSpan = new Span(sentenceStart, sentenceEnd);
        sentences.add(sentenceBuilder);
      }
    } catch (IOException e) {
      throw new CollectionException(e);
    }

    Document document = UimaAdapters.createDocument(cas, labelAdapters,
        path.getFileName().toString());
    TextView textView = document.newTextView()
        .withText(text.toString())
        .withName(viewName)
        .build();

    for (SentenceBuilder sentence : sentences) {
      Span sentenceSpan = sentence.sentenceSpan;

      textView.label(Label.create(sentenceSpan, Sentence.create()));

      for (TokenBuilder tokenBuilder : sentence.tokenBuilders) {
        Span tokenSpan = tokenBuilder.tokenSpan;

        textView.label(Label.create(tokenSpan,
            ImmutableParseToken.builder()
                .text(tokenSpan.getCovered(text).toString())
                .hasSpaceAfter(true)
                .build()
        ));

        PartOfSpeech partOfSpeech = tokenBuilder.partOfSpeech;
        if (partOfSpeech != null) {
          String pos = partOfSpeech.toString();
          textView.label(Label.create(tokenSpan, PartsOfSpeech.forTag(pos)));
        }
      }
    }

  }

  @Override
  public void setTargetView(String viewName) {
    this.viewName = viewName;
  }

  /**
   * Used to build sentences.
   */
  private static class SentenceBuilder {

    /**
     * The begin and end of the sentence.
     */
    private Span sentenceSpan;

    /**
     * The tokens of the sentence.
     */
    private List<TokenBuilder> tokenBuilders;
  }

  /**
   * Used to build tokens
   */
  private static class TokenBuilder {

    /**
     * The begin and end of the token
     */
    private Span tokenSpan;

    /**
     * The part of speech.
     */
    private PartOfSpeech partOfSpeech;
  }
}

package ru.eltex.producthub.search.service;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.ru.RussianLightStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/** Анализаторы поиска: слова без морфологии (для префиксов и опечаток) и с морфологией русского и английского. */
final class HubAnalyzers {

    private static final NormalizeCharMap YO = yo();

    private HubAnalyzers() {
    }

    private static NormalizeCharMap yo() {
        NormalizeCharMap.Builder b = new NormalizeCharMap.Builder();
        b.add("ё", "е");
        b.add("Ё", "е");
        return b.build();
    }

    /** Только слова в нижнем регистре, "ё" как "е". */
    static final Analyzer RAW = new Analyzer() {
        @Override
        protected Reader initReader(String fieldName, Reader reader) {
            return new MappingCharFilter(YO, reader);
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new StandardTokenizer();
            return new TokenStreamComponents(source, new LowerCaseFilter(source));
        }
    };

    /** Слова с основой: русская и английская морфология в одной цепочке (каждая трогает только свои буквы). */
    static final Analyzer STEM = new Analyzer() {
        @Override
        protected Reader initReader(String fieldName, Reader reader) {
            return new MappingCharFilter(YO, reader);
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new StandardTokenizer();
            TokenStream stream = new LowerCaseFilter(source);
            stream = new RussianLightStemFilter(stream);
            stream = new PorterStemFilter(stream);
            return new TokenStreamComponents(source, stream);
        }
    };
}

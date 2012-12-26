/*
 * Copyright  2012  Ali Ok (aliokATapacheDOTorg)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trnltk.morphology.lexicon;

import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.trnltk.morphology.model.ImmutableRoot;
import org.trnltk.morphology.model.Lexeme;
import org.trnltk.morphology.model.LexemeAttribute;
import org.trnltk.morphology.model.SyntacticCategory;
import org.trnltk.morphology.phonetics.*;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class ImmutableRootGenerator {
    private static final ImmutableSet<LexemeAttribute> modifiersToWatch = Sets.immutableEnumSet(LexemeAttribute.Doubling,
            LexemeAttribute.LastVowelDrop,
            LexemeAttribute.ProgressiveVowelDrop,
            LexemeAttribute.InverseHarmony,
            LexemeAttribute.Voicing,
            LexemeAttribute.VoicingOpt,
            LexemeAttribute.RootChange);

    private static final ImmutableMap<Pair<String, SyntacticCategory>, String> rootChanges = new ImmutableMap.Builder<Pair<String, SyntacticCategory>, String>()
            .put(Pair.of("ben", SyntacticCategory.PRONOUN), "ban")
            .put(Pair.of("sen", SyntacticCategory.PRONOUN), "san")
            .put(Pair.of("demek", SyntacticCategory.VERB), "di")
            .put(Pair.of("yemek", SyntacticCategory.VERB), "yi")
            .put(Pair.of("hepsi", SyntacticCategory.PRONOUN), "hep")
            .put(Pair.of("ora", SyntacticCategory.PRONOUN), "or")
            .put(Pair.of("bura", SyntacticCategory.PRONOUN), "bur")
            .put(Pair.of("şura", SyntacticCategory.PRONOUN), "şur")
            .put(Pair.of("nere", SyntacticCategory.PRONOUN), "ner")
            .put(Pair.of("içeri", (SyntacticCategory) null), "içer") // applicable to all forms of the word
            .put(Pair.of("dışarı", (SyntacticCategory) null), "dışar") // applicable to all forms of the word
            .put(Pair.of("birbiri", SyntacticCategory.PRONOUN), "birbir")
            .build();

    private PhoneticsAnalyzer phoneticsAnalyzer = new PhoneticsAnalyzer();

    public Collection<ImmutableRoot> generateAll(final Set<Lexeme> lexemes) {
        HashSet<ImmutableRoot> all = new HashSet<ImmutableRoot>();
        for (Lexeme lexeme : lexemes) {
            all.addAll(this.generate(lexeme));
        }
        return all;
    }

    public HashSet<ImmutableRoot> generate(final Lexeme lexeme) {
        if (CollectionUtils.containsAny(lexeme.getAttributes(), modifiersToWatch)) {
            return this.generateModifiedRootNodes(lexeme);
        } else {
            Set<PhoneticAttribute> phoneticAttributes = phoneticsAnalyzer.calculatePhoneticAttributes(lexeme.getLemmaRoot(), lexeme.getAttributes());
            final ImmutableRoot root = new ImmutableRoot(lexeme.getLemmaRoot(), lexeme, Sets.immutableEnumSet(phoneticAttributes), null);
            return Sets.newHashSet(root);
        }
    }

    private HashSet<ImmutableRoot> generateModifiedRootNodes(final Lexeme lexeme) {
        final ImmutableSet<LexemeAttribute> lexemeAttributes = lexeme.getAttributes();
        if (lexemeAttributes.contains(LexemeAttribute.RootChange))
            return this.handleSpecialRoots(lexeme);

        final String lemmaRoot = lexeme.getLemmaRoot();
        String modifiedRootStr = lexeme.getLemmaRoot();

        final EnumSet<PhoneticAttribute> originalPhoneticAttrs = phoneticsAnalyzer.calculatePhoneticAttributes(lexeme.getLemmaRoot(), null);
        final EnumSet<PhoneticAttribute> modifiedPhoneticAttrs = phoneticsAnalyzer.calculatePhoneticAttributes(lexeme.getLemmaRoot(), null);

        final EnumSet<PhoneticExpectation> originalPhoneticExpectations = EnumSet.noneOf(PhoneticExpectation.class);
        final EnumSet<PhoneticExpectation> modifiedPhoneticExpectations = EnumSet.noneOf(PhoneticExpectation.class);

        if (CollectionUtils.containsAny(lexemeAttributes, Sets.immutableEnumSet(LexemeAttribute.Voicing, LexemeAttribute.VoicingOpt))) {
            final TurkishLetter lastLetter = TurkishAlphabet.getLetterForChar(modifiedRootStr.charAt(modifiedRootStr.length() - 1));
            final TurkishLetter voicedLastLetter = lemmaRoot.endsWith("nk") ? TurkishAlphabet.L_g : TurkishAlphabet.voice(lastLetter);
            Validate.notNull(voicedLastLetter);
            modifiedRootStr = modifiedRootStr.substring(0, modifiedRootStr.length() - 1) + voicedLastLetter.getCharValue();

            modifiedPhoneticAttrs.remove(PhoneticAttribute.LastLetterVoicelessStop);

            if (voicedLastLetter.isContinuant()) {
                modifiedPhoneticAttrs.remove(PhoneticAttribute.LastLetterNotContinuant);
                modifiedPhoneticAttrs.add(PhoneticAttribute.LastLetterContinuant);
            } else {
                modifiedPhoneticAttrs.remove(PhoneticAttribute.LastLetterContinuant);
                modifiedPhoneticAttrs.add(PhoneticAttribute.LastLetterNotContinuant);
            }

            if (!lexemeAttributes.contains(LexemeAttribute.VoicingOpt)) {
                originalPhoneticExpectations.add(PhoneticExpectation.ConsonantStart);
            }

            modifiedPhoneticExpectations.add(PhoneticExpectation.VowelStart);
        }

        if (lexemeAttributes.contains(LexemeAttribute.Doubling)) {
            modifiedRootStr += modifiedRootStr.charAt(modifiedRootStr.length() - 1);
            originalPhoneticExpectations.add(PhoneticExpectation.ConsonantStart);
            modifiedPhoneticExpectations.add(PhoneticExpectation.VowelStart);
        }

        if (lexemeAttributes.contains(LexemeAttribute.LastVowelDrop)) {
            modifiedRootStr = modifiedRootStr.substring(0, modifiedRootStr.length() - 2) + modifiedRootStr.charAt(modifiedRootStr.length() - 1);
            if (!SyntacticCategory.VERB.equals(lexeme.getSyntacticCategory()))
                originalPhoneticExpectations.add(PhoneticExpectation.ConsonantStart);

            modifiedPhoneticExpectations.add(PhoneticExpectation.VowelStart);
        }

        if (lexemeAttributes.contains(LexemeAttribute.InverseHarmony)) {
            originalPhoneticAttrs.add(PhoneticAttribute.LastVowelFrontal);
            originalPhoneticAttrs.remove(PhoneticAttribute.LastVowelBack);
            modifiedPhoneticAttrs.add(PhoneticAttribute.LastVowelFrontal);
            modifiedPhoneticAttrs.remove(PhoneticAttribute.LastVowelBack);
        }

        if (lexemeAttributes.contains(LexemeAttribute.ProgressiveVowelDrop)) {
            modifiedRootStr = modifiedRootStr.substring(0, modifiedRootStr.length() - 1);
            if (this.hasVowel(modifiedRootStr)) {
                modifiedPhoneticAttrs.clear();
                modifiedPhoneticAttrs.addAll(phoneticsAnalyzer.calculatePhoneticAttributes(modifiedRootStr, null));
            }
            modifiedPhoneticExpectations.add(PhoneticExpectation.VowelStart);
        }

        ImmutableRoot originalRoot = new ImmutableRoot(lexeme.getLemmaRoot(), lexeme, Sets.immutableEnumSet(originalPhoneticAttrs), Sets.immutableEnumSet(originalPhoneticExpectations));
        ImmutableRoot modifiedRoot = new ImmutableRoot(modifiedRootStr, lexeme, Sets.immutableEnumSet(modifiedPhoneticAttrs), Sets.immutableEnumSet(modifiedPhoneticExpectations));

        if (originalRoot.equals(modifiedRoot))
            return Sets.newHashSet(originalRoot);
        else
            return Sets.newHashSet(originalRoot, modifiedRoot);
    }

    private HashSet<ImmutableRoot> handleSpecialRoots(final Lexeme originalLexeme) {
        String changedRootStr = rootChanges.get(Pair.of(originalLexeme.getLemma(), originalLexeme.getSyntacticCategory()));
        if (StringUtils.isBlank(changedRootStr))
            changedRootStr = rootChanges.get(Pair.of(originalLexeme.getLemma(), (SyntacticCategory) null));

        Validate.notNull(changedRootStr, "Unhandled root change : " + originalLexeme);

        final ImmutableSet<LexemeAttribute> attributes = originalLexeme.getAttributes();
        final EnumSet<LexemeAttribute> newAttributes = EnumSet.copyOf(attributes);
        newAttributes.remove(LexemeAttribute.RootChange);
        final Lexeme modifiedLexeme = new Lexeme(originalLexeme.getLemma(), originalLexeme.getLemmaRoot(), originalLexeme.getSyntacticCategory(),
                originalLexeme.getSecondarySyntacticCategory(), Sets.immutableEnumSet(newAttributes));


        final String unchangedRootStr = originalLexeme.getLemmaRoot();

        final ImmutableRoot rootUnchanged = new ImmutableRoot(unchangedRootStr, modifiedLexeme,
                Sets.immutableEnumSet(phoneticsAnalyzer.calculatePhoneticAttributes(unchangedRootStr, modifiedLexeme.getAttributes())), null);

        final ImmutableRoot rootChanged = new ImmutableRoot(changedRootStr, modifiedLexeme,
                Sets.immutableEnumSet(phoneticsAnalyzer.calculatePhoneticAttributes(changedRootStr, modifiedLexeme.getAttributes())), null);

        return Sets.newHashSet(rootUnchanged, rootChanged);

    }

    private boolean hasVowel(String str) {
        return Iterables.any(Lists.newArrayList(ArrayUtils.toObject(str.toCharArray())), new Predicate<Character>() {
            @Override
            public boolean apply(Character input) {
                return TurkishAlphabet.getLetterForChar(input).isVowel();
            }
        });
    }
}
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

package org.trnltk.morphology.contextless.parser;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;
import org.trnltk.common.specification.Specification;
import org.trnltk.common.specification.Specifications;
import org.trnltk.morphology.model.*;
import org.trnltk.morphology.morphotactics.SuffixGraph;
import org.trnltk.morphology.morphotactics.SuffixGraphState;

import java.util.LinkedList;
import java.util.List;

import static org.trnltk.morphology.morphotactics.suffixformspecifications.SuffixFormSpecifications.rootHasProgressiveVowelDrop;
import static org.trnltk.morphology.morphotactics.suffixformspecifications.SuffixFormSpecifications.rootHasSyntacticCategory;

public class RequiredTransitionApplier {

    // not good to use other classes' loggers
    // however, it is good to have less things to turn on and off during debugging
    private Logger logger = ContextlessMorphologicParser.logger;

    private final SuffixGraph suffixGraph;
    private final SuffixApplier suffixApplier;

    private final LinkedList<RequiredTransitionRule> requiredTransitionRules = new LinkedList<RequiredTransitionRule>();

    public RequiredTransitionApplier(final SuffixGraph suffixGraph, final SuffixApplier suffixApplier) {
        this.suffixGraph = suffixGraph;
        this.suffixApplier = suffixApplier;

        this.createRules();
    }

    private void createRules() {
        // English translation to following code fragment:
        // if root has syn cat VERB and root has progressive vowel drop
        // and its state is VERB_ROOT
        // add positive suffix, and then progressive suffix

        final RequiredTransitionRule progressiveVowelDropRule = new RequiredTransitionRuleBuilder(this.suffixGraph)
                .condition(
                        Specifications.and(
                                rootHasSyntacticCategory(SyntacticCategory.VERB),
                                rootHasProgressiveVowelDrop()
                        )
                )
                .sourceState("VERB_ROOT")
                .step("Pos", "", "VERB_WITH_POLARITY")
                .step("Prog", "Iyor", "VERB_WITH_TENSE")
                .build();

        // more rule can be added

        this.requiredTransitionRules.add(progressiveVowelDropRule);
    }

    List<MorphemeContainer> applyRequiredTransitionsToMorphemeContainers(final List<MorphemeContainer> morphemeContainers, final TurkishSequence input) {
        final List<MorphemeContainer> newMorphemeContainers = new LinkedList<MorphemeContainer>();
        for (MorphemeContainer morphemeContainer : morphemeContainers) {
            MorphemeContainer newMorhpemeContainer = morphemeContainer;

            for (RequiredTransitionRule requiredTransitionRule : this.requiredTransitionRules) {
                if (!requiredTransitionRule.getCondition().isSatisfiedBy(newMorhpemeContainer))
                    continue;
                if (!requiredTransitionRule.getSourceState().equals(newMorhpemeContainer.getLastState()))
                    continue;

                for (RequiredTransitionRuleStep requiredTransitionRuleStep : requiredTransitionRule.getRequiredTransitionRuleSteps()) {
                    newMorhpemeContainer = applyRequiredTransitionRuleStepToMorphemeContainer(newMorhpemeContainer, requiredTransitionRuleStep, input);
                    if (newMorhpemeContainer == null)
                        break;
                }

                if (newMorhpemeContainer == null)
                    break;
            }

            if (newMorhpemeContainer != null)
                newMorphemeContainers.add(newMorhpemeContainer);
        }
        return newMorphemeContainers;
    }

    private MorphemeContainer applyRequiredTransitionRuleStepToMorphemeContainer(MorphemeContainer morphemeContainer, RequiredTransitionRuleStep requiredTransitionRuleStep, TurkishSequence input) {
        final SuffixForm suffixForm = requiredTransitionRuleStep.getSuffixForm();
        final Suffix suffix = suffixForm.getSuffix();
        if (!this.suffixApplier.transitionAllowedForSuffix(morphemeContainer, suffix))
            throw new IllegalStateException(String.format("There is a matching required transition rule, but suffix \"%s\" cannot be applied to %s", suffix, morphemeContainer));

        morphemeContainer = this.suffixApplier.trySuffixForm(morphemeContainer, suffixForm, requiredTransitionRuleStep.getTargetState(), input);
        if (morphemeContainer == null) {
            if (logger.isDebugEnabled())
                logger.debug(String.format("There is a matching required transition rule, but suffix form \"%s\" cannot be applied to %s", suffixForm, morphemeContainer));
            return null;
        } else {
            return morphemeContainer;
        }
    }

    private static class RequiredTransitionRuleBuilder {
        private final SuffixGraph suffixGraph;

        private Specification<MorphemeContainer> condition;
        private SuffixGraphState sourceState;
        private LinkedList<RequiredTransitionRuleStep> steps = new LinkedList<RequiredTransitionRuleStep>();

        private RequiredTransitionRuleBuilder(final SuffixGraph suffixGraph) {
            this.suffixGraph = suffixGraph;
        }

        public RequiredTransitionRuleBuilder condition(Specification<MorphemeContainer> condition) {
            this.condition = condition;
            return this;
        }

        public RequiredTransitionRuleBuilder sourceState(String sourceStateName) {
            this.sourceState = this.suffixGraph.getSuffixGraphState(sourceStateName);
            Validate.notNull(this.sourceState);
            return this;
        }

        public RequiredTransitionRuleBuilder step(String suffixName, String suffixFormStr, String targetStateName) {
            final SuffixForm suffixForm = this.suffixGraph.getSuffixForm(suffixName, suffixFormStr);
            final SuffixGraphState targetState = this.suffixGraph.getSuffixGraphState(targetStateName);

            Validate.notNull(suffixForm);
            Validate.notNull(targetState);

            this.steps.add(new RequiredTransitionRuleStep(suffixForm, targetState));
            return this;
        }

        public RequiredTransitionRule build() {
            Validate.notNull(this.sourceState);
            Validate.notNull(this.condition);
            Validate.notEmpty(this.steps);

            return new RequiredTransitionRule(condition, sourceState, steps);
        }
    }

    private static class RequiredTransitionRule {
        private Specification<MorphemeContainer> condition;
        private SuffixGraphState sourceState;
        private List<RequiredTransitionRuleStep> requiredTransitionRuleSteps;

        private RequiredTransitionRule(Specification<MorphemeContainer> condition, SuffixGraphState sourceState,
                                       List<RequiredTransitionRuleStep> requiredTransitionRuleSteps) {
            Validate.notEmpty(requiredTransitionRuleSteps);
            this.condition = condition;
            this.sourceState = sourceState;
            this.requiredTransitionRuleSteps = requiredTransitionRuleSteps;
        }

        public SuffixGraphState getSourceState() {
            return sourceState;
        }

        public Specification<MorphemeContainer> getCondition() {
            return condition;
        }

        public List<RequiredTransitionRuleStep> getRequiredTransitionRuleSteps() {
            return requiredTransitionRuleSteps;
        }
    }

    private static class RequiredTransitionRuleStep {
        private final SuffixForm suffixForm;
        private final SuffixGraphState targetState;

        private RequiredTransitionRuleStep(SuffixForm suffixForm, SuffixGraphState targetState) {
            this.suffixForm = suffixForm;
            this.targetState = targetState;
        }

        public SuffixForm getSuffixForm() {
            return suffixForm;

        }

        public SuffixGraphState getTargetState() {
            return targetState;
        }
    }
}
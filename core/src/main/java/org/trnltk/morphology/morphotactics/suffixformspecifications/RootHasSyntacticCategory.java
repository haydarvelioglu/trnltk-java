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

package org.trnltk.morphology.morphotactics.suffixformspecifications;

import org.apache.commons.lang3.Validate;
import org.trnltk.common.specification.AbstractSpecification;
import org.trnltk.morphology.model.MorphemeContainer;
import org.trnltk.morphology.model.SyntacticCategory;

public class RootHasSyntacticCategory extends AbstractSpecification<MorphemeContainer> {
    private final SyntacticCategory syntacticCategory;

    public RootHasSyntacticCategory(SyntacticCategory syntacticCategory) {
        this.syntacticCategory = syntacticCategory;
    }

    @Override
    public String describe() {
        return String.format("root_has_syntactic_category(%s)", syntacticCategory);
    }

    @Override
    public boolean isSatisfiedBy(MorphemeContainer morphemeContainer) {
        Validate.notNull(morphemeContainer);

        return this.syntacticCategory.equals(morphemeContainer.getRoot().getLexeme().getSyntacticCategory());
    }
}
/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NoEmptyCollectionWithRawType extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `Collections#emptyList()`, `emptyMap()`, and `emptySet()`";
    }

    @Override
    public String getDescription() {
        return "Replaces `Collections#EMPTY_..` with methods that return generic types.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1596");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.util.Collections", false), new JavaVisitor<ExecutionContext>() {
            final Map<String, String> updateFields = new HashMap<>();

            {
                updateFields.put("EMPTY_LIST", "emptyList");
                updateFields.put("EMPTY_MAP", "emptyMap");
                updateFields.put("EMPTY_SET", "emptySet");
            }

            @Override
            public J visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Identifier name = anImport.getQualid().getName();
                if (anImport.isStatic() && name.getSimpleName().startsWith("EMPTY_") &&
                    TypeUtils.isOfClassType(anImport.getQualid().getTarget().getType(), "java.util.Collections")) {
                    return anImport.withQualid(anImport.getQualid().withName(name.withSimpleName(updateFields.get(name.getSimpleName()))));
                }
                return super.visitImport(anImport, ctx);
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.Identifier name = fieldAccess.getName();
                JavaType.Variable varType = name.getFieldType();
                if (varType != null && TypeUtils.isOfClassType(varType.getOwner(), "java.util.Collections") &&
                    varType.getName().startsWith("EMPTY_")) {
                    return JavaTemplate.builder("#{any(java.util.Collections)}." + updateFields.get(varType.getName()) + "()")
                            .contextSensitive() // context sensitive due to generics
                            .imports("java.util.Collections")
                            .build()
                            .apply(getCursor(), fieldAccess.getCoordinates().replace(), fieldAccess.getTarget());
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);
                JavaType.Variable varType = id.getFieldType();
                if (varType != null && TypeUtils.isOfClassType(varType.getOwner(), "java.util.Collections") &&
                    varType.getName().startsWith("EMPTY_")) {

                    return JavaTemplate.builder(updateFields.get(varType.getName()) + "()")
                            .contextSensitive() // context sensitive due to generics
                            .staticImports("java.util.Collections." + updateFields.get(varType.getName()))
                            .build()
                            .apply(getCursor(), identifier.getCoordinates().replace());

                }
                return id;
            }
        });
    }
}

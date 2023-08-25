package com.yourorg;

import lombok.EqualsAndHashCode;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.*;

@EqualsAndHashCode(callSuper = true)
public class FluentSyntaxConversion extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert to fluent syntax";
    }

    @Override
    public String getDescription() {
        return "Converts setters inside of a class to fluent syntax and then combines call sites with chained setter calls together.";
    }

    private final static MethodMatcher methodMatcher = new MethodMatcher("set*(String)");

    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate addStatementsTemplate = JavaTemplate.builder("return this;")
                    .contextSensitive()
                    .build();

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {

                   J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                    // Check if already method has return statements
                    if (isSetterMethod(m) && isReturnStatementAdded(m)) {
                       return m;
                    }
                    // Update the method return type from void to FullyQualified Class Name
                    if (isSetterMethod(m) && !isReturnStatementAdded(m)) {
                        m = m.withReturnTypeExpression(TypeTree.build(getTopLevelClassType(getCursor())
                                .getFullyQualifiedName()));
                        // Add the return statement to the "set*(String)" method body
                        assert m.getBody() != null;
                        m = maybeAutoFormat(m, addStatementsTemplate.apply(updateCursor(m),
                                m.getBody().getCoordinates().lastStatement()), ctx);
                    }

                    if(m.isConstructor() && (m.getBody() != null && m.getBody().getStatements().size() == 3)) {
                        J.MethodInvocation methodInvocation = null;
                        Expression select = null;
                        for (Statement s : m.getBody().getStatements()) {

                            if (s instanceof J.MethodInvocation) {
                                if (select == null) {
                                    methodInvocation = ((J.MethodInvocation) s);
                                    select = methodInvocation;
                                    doAfterVisit(new DeleteStatement<>(s));
                                } else {
                                    methodInvocation = ((J.MethodInvocation) s);
                                    assert ((J.MethodInvocation) s).getMethodType() != null;
                                    methodInvocation = methodInvocation.withSelect(select);
                                    select = methodInvocation;
                                    doAfterVisit(new DeleteStatement<>(s));
                                }

                            }

                            }
                        String template  = methodInvocation!=null?methodInvocation.toString()+";":"";
                        JavaTemplate addStatementsTemplate = JavaTemplate.builder(template)
                                .contextSensitive()
                                .build();

                        assert methodInvocation != null;
                        m = addStatementsTemplate.apply(getCursor(), m.getBody().getCoordinates().lastStatement());
                    }

                    return m;
            }
      };
    }
    private static JavaType.FullyQualified getTopLevelClassType(Cursor cursor) {
        if (cursor.getParent()!= null && cursor.getParent().getValue() instanceof J.ClassDeclaration) {
            JavaType.FullyQualified type = ((J.ClassDeclaration) (cursor.getParent().getValue())).getType();
            assert type != null;

            return (Objects.requireNonNull(type.getOwningClass()!=null ? type.getOwningClass():type));
        }
        return  getTopLevelClassType(cursor.getParent());
    }

    private static boolean isSetterMethod(J.MethodDeclaration methodDeclaration){
        assert  methodDeclaration.getMethodType() != null;
        return  methodDeclaration.getMethodType().getName().startsWith(methodMatcher.getTargetTypePattern().toString());
    }

    private static boolean isReturnStatementAdded(J.MethodDeclaration methodDeclaration){
        assert  methodDeclaration.getBody() != null;
        return methodDeclaration.getBody().getStatements().size() > 1;
    }
}

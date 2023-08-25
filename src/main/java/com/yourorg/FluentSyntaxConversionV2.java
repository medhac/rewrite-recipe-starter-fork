package com.yourorg;

import lombok.EqualsAndHashCode;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.*;

@EqualsAndHashCode(callSuper = true)
public class FluentSyntaxConversionV2 extends Recipe {

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
                    .doBeforeParseTemplate(System.out::println)
                    .contextSensitive()
                    .build();
            private static final String argumentType = "(#{any(java.lang.String)})";

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {

                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                // Check if already method has return statements
                if (isSetterMethod(m) && isReturnStatementAdded(m)) {
                    return m;
                }
                // Update the method return type from void to FullyQualified Class Name
                if (isSetterMethod(m) && !isReturnStatementAdded(m)) {
                    TypeTree returnExpr = TypeTree.build(getTopLevelClassType(getCursor()).getFullyQualifiedName());
                    m = m.withReturnTypeExpression(returnExpr.withPrefix(Space.format(" ")));
                    assert m.getMethodType() != null;

                    // Add the return statement to the "set*(String)" method body
                    assert m.getBody() != null;
                    m = maybeAutoFormat(m, addStatementsTemplate.apply(updateCursor(m),
                            m.getBody().getCoordinates().lastStatement()), ctx);
                }

                //TODO  This condition needed to be changed
                if(m.isConstructor() && (m.getBody() != null && m.getBody().getStatements().size()==3)) {
                    J.MethodInvocation methodInvocation = null;
                    String methodType = "";
                    String template = "";
                    List<Expression> argumentList = new ArrayList<>();
                    for (Statement s : m.getBody().getStatements()) {
                        if (s instanceof J.MethodInvocation) {
                            if (StringUtils.isBlank(methodType)) {
                                methodInvocation = ((J.MethodInvocation) s);
                                assert methodInvocation.getMethodType() != null;
                                methodType = "#{any("+((J.MethodInvocation) s).getMethodType().getDeclaringType()+")}";
                                argumentList.add(methodInvocation.getSelect());
                                argumentList.addAll(methodInvocation.getArguments());
                                template=methodType+"."+methodInvocation.getSimpleName()+argumentType;
                                doAfterVisit(new DeleteStatement<>(s));
                            } else {
                                methodInvocation = ((J.MethodInvocation) s);
                                assert ((J.MethodInvocation) s).getMethodType() != null;
                                argumentList.addAll(methodInvocation.getArguments());
                                template=template+"."+methodInvocation.getSimpleName()+argumentType;
                                doAfterVisit(new DeleteStatement<>(s));
                            }
                        }
                    }
                    JavaTemplate addStatementsTemplate = JavaTemplate.builder(template+";")
                            .doBeforeParseTemplate(System.out::println)
                            .build();

                    //TODO  This argument list should ne dynamic
                    m = addStatementsTemplate.apply(getCursor(), m.getBody().getCoordinates().lastStatement(),
                            argumentList.get(0),argumentList.get(1),argumentList.get(2));
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

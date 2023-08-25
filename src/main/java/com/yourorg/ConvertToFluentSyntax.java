package com.yourorg;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.Objects;

@Value
@EqualsAndHashCode(callSuper = true)
public class ConvertToFluentSyntax extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert to fluent syntax";
    }

    @Override
    public String getDescription() {
        return "Converts setters inside of a class to fluent syntax and then combines call sites with chained setter calls together.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

       return new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher methodMatcher = new MethodMatcher("set*(String)");
            private final JavaTemplate addStatementsTemplate = JavaTemplate.builder("return this;")
                    .contextSensitive()
                    .build();
           private String methodStatement = "";


           @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext executionContext) {

                  JavaType.FullyQualified topLevelClassName;

                  // Check whether methods are already updated, if updated then return
                  if ((methodDeclaration.getMethodType() != null) &&
                        (methodDeclaration.getMethodType().getName().startsWith(methodMatcher.getTargetTypePattern().toString()))
                        && (methodDeclaration.getBody() != null)
                        && (methodDeclaration.getBody().getStatements().size() > 1)) {
                    return methodDeclaration;
                  }

                  // Find methods with help of matchers
                if(methodDeclaration.getMethodType()!= null &&
                         methodDeclaration.getMethodType().getName().startsWith(methodMatcher.getTargetTypePattern().toString())) {

                      // Update the method return type from void to FullyQualified Class Name
                      topLevelClassName = getTopLevelClassType(getCursor());
                      methodDeclaration = methodDeclaration.withReturnTypeExpression(
                              TypeTree.build(topLevelClassName.getFullyQualifiedName()));


                      // Safe to assert since we just added a body to the method
                      assert methodDeclaration.getBody() != null;

                      // Prepare the parameter for the chain method call from the constructor
                      assert methodDeclaration.getMethodType() != null;
                      String parameters = (methodDeclaration.getMethodType().getName().substring(3)).toLowerCase();
                      methodStatement = methodStatement + "." + methodDeclaration.getMethodType().getName() + "(\"" + parameters + "\")";

                      // Add the return statement to the "set*(String)" method body
                      methodDeclaration = maybeAutoFormat(
                              methodDeclaration, addStatementsTemplate.apply(updateCursor(methodDeclaration),
                                      methodDeclaration.getBody().getCoordinates().lastStatement()),
                              executionContext
                      );
                  }

                  // Check if Constructor
                  if ( methodDeclaration.isConstructor()
                        && (methodDeclaration.getBody() != null)
                        && (methodDeclaration.getBody().getStatements().size() > 2)) {

                      List<Statement> statements = methodDeclaration.getBody().getStatements();
                      for (int i = 0; i < statements.size(); i++) {
                          Statement s = statements.get(i);
                          if (i > 0) {
                              doAfterVisit(new DeleteStatement<>(s));
                          }
                      }

                      methodStatement = !StringUtils.isBlank(methodStatement) ? methodStatement.substring(1):"";
                      topLevelClassName = getTopLevelClassType(getCursor());
                      methodStatement = topLevelClassName.getFullyQualifiedName().toLowerCase() +"."+ methodStatement;
                      JavaTemplate addStatementsTemplate = JavaTemplate.builder(methodStatement + ";")
                              .contextSensitive()
                              .build();
                      methodDeclaration = addStatementsTemplate.apply(updateCursor(methodDeclaration),
                              methodDeclaration.getBody().getCoordinates().lastStatement());
                  }
                 return methodDeclaration;
            }
           private JavaType.FullyQualified getTopLevelClassType(Cursor cursor) {
               if (cursor.getParent()!= null && cursor.getParent().getValue() instanceof J.ClassDeclaration) {
                   JavaType.FullyQualified type = ((J.ClassDeclaration) (cursor.getParent().getValue())).getType();
                   assert type != null;

                   return (Objects.requireNonNull(type.getOwningClass()!=null ? type.getOwningClass():type));
               }
               return  getTopLevelClassType(cursor.getParent());
           }
       };
    }
}
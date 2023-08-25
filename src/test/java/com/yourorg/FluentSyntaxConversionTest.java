package com.yourorg;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.openrewrite.java.Assertions.java;

class FluentSyntaxConversionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FluentSyntaxConversion());
    }

    @Test
    void replaceWithFluentSyntaxWithTwoMethods() {
            rewriteRun(
            //language=java
            java("""
            class A {
            private String foo;
            private String bar;
               
            public void setFoo(String foo) {
               this.foo = foo;
            }
            public void setBar(String bar) {
               this.bar = bar;
            }
            private static class B {
                A a;
                private B() {
                    this.a = new A();
                    a.setFoo("foo");
                    a.setBar("bar");
                }
            }
            }
            """,
            """
            class A {
            private String foo;
            private String bar;
            
                public A setFoo(String foo) {
                    this.foo = foo;
                    return this;
                }
                
                public A setBar(String bar) {
                    this.bar = bar;
                    return this;
                }
            private static class B {
                A a;
                private B() {
                    this.a = new A();
                    a.setFoo("foo").setBar("bar");
                }
            }
            }
            """
            )
        );
    }
    @Test
    void replaceWithFluentSyntaxWithOne() {
        rewriteRun(
//language=java
                java("""
class A {
private String foo;

public void setFoo(String foo) {
   this.foo = foo;
}
private static class B {
  A a;
  private B() {
     this.a = new A();
     a.setFoo("foo");
  }
}
}
""",
                        """
class A {
private String foo;

    public A setFoo(String foo) {
        this.foo = foo;
        return this;
    }
private static class B {
  A a;
  private B() {
     this.a = new A();
     a.setFoo("foo");
  }
}
}
"""
                )
        );
    }
}
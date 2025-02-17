/**
 * Copyright 2022 SPeCS.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

package pt.up.fe.comp.cp1;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;

public class GrammarTest {


    // TODO: Set name of imports grammar rule
    private static final String IMPORT = "importDecl";
    // TODO: Set name of main method grammar rule
    private static final String MAIN_METHOD = "mainMethodDecl";
    private static final String INSTANCE_METHOD = "methodDecl";
    private static final String STATEMENT = "stmt";
    private static final String EXPRESSION = "expr";

    @Test
    public void testImportSingle() {
        TestUtils.parseVerbose("import bar;", IMPORT);
    }

    @Test
    public void testImportMulti() {
        TestUtils.parseVerbose("import bar.foo.a;", IMPORT);
    }

    @Test
    public void testClass() {
        TestUtils.parseVerbose("class Foo extends Bar {}");
    }

    @Test
    public void testVarDecls() {
        TestUtils.parseVerbose("class Foo {int a; int[] b; int c; boolean d; Bar e;}");
    }

   /*@Test
    public void testVarDeclString() {
        TestUtils.parseVerbose("String aString;", "VarDecl");
    }*/

    @Test
    public void testMainMethodEmpty() {
        TestUtils.parseVerbose("static void main(String[] args) {}", MAIN_METHOD);
    }

    @Test
    public void testInstanceMethodEmpty() {
        TestUtils.parseVerbose("int foo(int anInt, int[] anArray, boolean aBool, String aString) {return a;}",
                INSTANCE_METHOD);
    }

    @Test
    public void testInstanceMethodVarargs() {
        TestUtils.parseVerbose("int foo(int... ints) {return 0;}",
                INSTANCE_METHOD);
    }

    @Test
    public void testStmtScope() {
        TestUtils.parseVerbose("{a; b; c;}", STATEMENT);
    }

    @Test
    public void testStmtEmptyScope() {
        TestUtils.parseVerbose("{}", STATEMENT);
    }

    @Test
    public void testStmtIfElse() {
        TestUtils.parseVerbose("if(a){ifStmt1;ifStmt2;}else{elseStmt1;elseStmt2;}", STATEMENT);
    }

    @Test
    public void testStmtIfElseWithoutBrackets() {
        TestUtils.parseVerbose("if(a)ifStmt;else elseStmt;", STATEMENT);
    }

    @Test
    public void testStmtWhile() {
        TestUtils.parseVerbose("while(a){whileStmt1;whileStmt2;}", STATEMENT);
    }

    @Test
    public void testStmtWhileWithoutBrackets() {
        TestUtils.parseVerbose("while(a)whileStmt1;", STATEMENT);
    }

    @Test
    public void testStmtAssign() {
        TestUtils.parseVerbose("a=b;", STATEMENT);
    }

    @Test
    public void testStmtArrayAssign() {
        TestUtils.parseVerbose("anArray[a]=b;", STATEMENT);
    }

    @Test
    public void testExprTrue() {
        TestUtils.parseVerbose("true", EXPRESSION);
    }

    @Test
    public void testExprFalse() {
        TestUtils.parseVerbose("false", EXPRESSION);
    }

    @Test
    public void testExprThis() {
        TestUtils.parseVerbose("this", EXPRESSION);
    }

    @Test
    public void testExprId() {
        TestUtils.parseVerbose("a", EXPRESSION);
    }

    @Test
    public void testExprIntLiteral() {
        TestUtils.parseVerbose("10", EXPRESSION);
    }

    @Test
    public void testExprParen() {
        TestUtils.parseVerbose("(10)", EXPRESSION);
    }

    @Test
    public void testExprMemberCall() {
        TestUtils.parseVerbose("foo.bar(10, a, true)", EXPRESSION);
    }

    @Test
    public void testExprMemberCallChain() {
        TestUtils.parseVerbose("callee.level1().level2(false, 10).level3(true)", EXPRESSION);
    }

    @Test
    public void testExprLength() {
        TestUtils.parseVerbose("a.length", EXPRESSION);
    }

    @Test
    public void testExprLengthChain() {
        TestUtils.parseVerbose("a.length.length", EXPRESSION);
    }

    @Test
    public void testArrayAccess() {
        TestUtils.parseVerbose("a[10]", EXPRESSION);
    }

    @Test
    public void testArrayAccessChain() {
        TestUtils.parseVerbose("a[10][20]", EXPRESSION);
    }

    @Test
    public void testParenArrayChain() {
        TestUtils.parseVerbose("(a)[10]", EXPRESSION);
    }

    @Test
    public void testCallArrayAccessLengthChain() {
        TestUtils.parseVerbose("callee.foo()[10].length", EXPRESSION);
    }

    @Test
    public void testExprNot() {
        TestUtils.parseVerbose("!true", EXPRESSION);
    }

    @Test
    public void testExprNewArray() {
        TestUtils.parseVerbose("new int[!a]", EXPRESSION);
    }

    @Test
    public void testExprNewClass() {
        TestUtils.parseVerbose("new Foo()", EXPRESSION);
    }

    @Test
    public void testExprMult() {
        TestUtils.parseVerbose("2 * 3", EXPRESSION);
    }

    @Test
    public void testExprDiv() {
        TestUtils.parseVerbose("2 / 3", EXPRESSION);
    }

    @Test
    public void testExprMultChain() {
        TestUtils.parseVerbose("1 * 2 / 3 * 4", EXPRESSION);
    }

    @Test
    public void testExprAdd() {
        TestUtils.parseVerbose("2 + 3", EXPRESSION);
    }

    @Test
    public void testExprSub() {
        TestUtils.parseVerbose("2 - 3", EXPRESSION);
    }

    @Test
    public void testExprAddChain() {
        TestUtils.parseVerbose("1 + 2 - 3 + 4", EXPRESSION);
    }

    @Test
    public void testExprRelational() {
        TestUtils.parseVerbose("1 < 2", EXPRESSION);
    }

    @Test
    public void testExprRelationalChain() {
        TestUtils.parseVerbose("1 < 2 < 3 < 4", EXPRESSION);
    }

    @Test
    public void testExprLogical() {
        TestUtils.parseVerbose("1 && 2", EXPRESSION);
    }

    @Test
    public void testExprLogicalChain() {
        TestUtils.parseVerbose("1 && 2 && 3 && 4", EXPRESSION);
    }

    @Test
    public void testExprChain() {
        TestUtils.parseVerbose("1 && 2 < 3 + 4 - 5 * 6 / 7", EXPRESSION);
    }

    @Test
    public void testExprArrayInit() {
        TestUtils.parseVerbose("[10, 20, 30]", EXPRESSION);
    }

    /**
     * Test to ensure that a class with multiple fields and methods is parsed correctly.
     */
    @Test
    public void testMethodsAndFields() {
        TestUtils.parseVerbose(
                "class MethodsAndFields{" +
                        "int field1;" +
                        "boolean field2;" +
                        "MethodsAndFields field3;" +
                        "public int getField1(){" +
                        "   return field1;" +
                        "}" +
                        "public boolean getField2(){" +
                        "   return field2;" +
                        "}" +
                        "public MethodsAndFields getField3(){" +
                        "   return field3;" +
                        "}" +
                        "public int[] all(int a, boolean b, MethodsAndFields maf){" +
                        "   int[] c;" +
                        "   return c;" +
                        "}" +
                        "public static void main(String[] args){" +
                        "}" +
                        "}"
        );
    }

    @Test
    public void testIntPlusObject() {
        TestUtils.parseVerbose(
                "import A;" +
                        "class IntPlusObject {" +
                        "   public static void main(String[] args) {" +
                        "   }" +
                        "   public int foo() {" +
                        "       A a;" +
                        "       a = new A();" +
                        "       return 10 + a;" +
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testBoolTimesInt() {
        TestUtils.parseVerbose(
                "import BoolTimesInt;" +
                        "class TestBoolTimesInt {" +
                        "   public static void main(String[] args) {" +
                        "   }" +
                        "   public int foo() {" +
                        "       int a;" +
                        "       boolean b;" +
                        "       a = 10;" +
                        "       b = true;" +
                        "       return b * a;" +
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testArrayPlusInt() {
        TestUtils.parseVerbose(
                "import ArrayPlusInt;" +
                        "class TestArrayPlusInt {" +
                        "   public static void main(String[] args) {" +
                        "   }" +
                        "   public int foo() {" +
                        "       int[] a;" +
                        "       int b;" +
                        "       a = new int[2];" +
                        "       b = 10;" +
                        "       return a + b;" + // This line is conceptually for the test, assuming handling for array plus int
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testArrayAccessOnInt() {
        TestUtils.parseVerbose(
                "import ArrayAccessOnInt;" +
                        "class TestArrayAccessOnInt {" +
                        "   public static void main(String[] args) {" +
                        "   }" +
                        "   public int foo() {" +
                        "       int a;" +
                        "       a = 0;" +
                        "       return a[10];" + // Conceptually incorrect in Java, assuming interpretation by TestUtils.parseVerbose
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testAssignIntToBool() {
        TestUtils.parseVerbose(
                "import AssignIntToBool;" +
                        "class TestAssignIntToBool {" +
                        "   public static void main(String[] args) {" +
                        "   }" +
                        "   public boolean foo() {" +
                        "       boolean a;" +
                        "       a = 10;" +
                        "       return a;" +
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testArrayInitialization() {
        TestUtils.parseVerbose(
                "class ArrayInit {" +
                        "   public int[] foo() {" +
                        "       int[] a;" +
                        "       a = [10, 20, 30];" +
                        "       return a;" +
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testMethodCallWithArguments() {
        TestUtils.parseVerbose(
                "import A;" +
                        "class AssumeArguments {" +
                        "   public static void main(String[] args) {" +
                        "   }" +
                        "   public int bar() {" +
                        "       A a;" +
                        "       boolean b;" +
                        "       a = new A();" +
                        "       b = true;" +
                        "       return a.foo(b);" +
                        "   }" +
                        "}"
        );
    }

    @Test
    public void testVarargsMethod() {
        TestUtils.parseVerbose(
                "class Varargs {\n" +
                        "   public int foo() {\n" +
                        "       int a;\n" +
                        "       a = varargs(10, 20, 30);\n" +
                        "       return a;\n" +
                        "   }\n" +
                        "   public int varargs(int... a) {\n" +
                        "       return a[0];\n" +
                        "   }\n" +
                        "}"
        );
    }

    @Test
    public void testCompileArithmeticMethod() {
        TestUtils.parseVerbose(
                "class CompileArithmetic {\n" +
                        "   public static void main(String[] args) {\n" +
                        "   }\n" +
                        "   public int foo() {\n" +
                        "       int a;\n" +
                        "       int b;\n" +
                        "       a = 1;\n" +
                        "       b = 2;\n" +
                        "       return a + b;\n" +
                        "   }\n" +
                        "}"
        );
    }

    @Test
    public void testCompileBasicMethods() {
        TestUtils.parseVerbose(
                "import io;\n" +
                        "import Quicksort;\n" +
                        "class CompileBasic extends Quicksort {\n" +
                        "   int intField;\n" +
                        "   boolean boolField;\n" +
                        "   public int method1() {\n" +
                        "       int intLocal1;\n" +
                        "       boolean boolLocal1;\n" +
                        "       return 0;\n" +
                        "   }\n" +
                        "   public boolean method2(int intParam1, boolean boolParam1) {\n" +
                        "       return boolParam1;\n" +
                        "   }\n" +
                        "   public static void main(String[] args) {\n" +
                        "   }\n" +
                        "}"
        );
    }

    @Test
    public void testCompileMethodInvocation() {
        TestUtils.parseVerbose(
                "import io;\n" +
                        "class CompileMethodInvocation {\n" +
                        "   public static void main(String[] args) {\n" +
                        "   }\n" +
                        "   public int foo() {\n" +
                        "       int a;\n" +
                        "       a = 1;\n" +
                        "       io.println(a);\n" +
                        "       return 0;\n" +
                        "   }\n" +
                        "}"
        );
    }

    @Test
    public void testHelloWorldMethodInvocation() {
        TestUtils.parseVerbose(
                """
                        import ioPlus;
                        class HelloWorld {
                           public static void main(String[] args) {
                               ioPlus.printHelloWorld();
                           }
                        }"""
        );
    }

    @Test
    public void testIntInIfCondition() {
        TestUtils.parseVerbose(
            """
                    class IntInIfCondition {
                            public static void main(String[] args) {
                            }
                            public int foo() {
                                if (1 + 2) {
                                } else {
                                }
                                return 0;
                            }
                        }
                  """
        );
    }

    @Test
    public void testIncompatibleArguments() {
        TestUtils.parseVerbose(
            """
                    class IncompatibleArguments {
                        public static void main(String[] args) {
                        }
                        public int foo(int a) {
                            return a + 1;
                        }
                        public int bar() {
                            IncompatibleArguments a;
                            boolean b;
                            a = new IncompatibleArguments();
                            b = true;
                            return a.foo(b);
                        }
                    }
                  """
        );
    }

    @Test
    public void testAppSimple() {
        TestUtils.parseVerbose(
            """
                    import io;
                    class Simple {
                        public int add(int a, int b){
                            int c;
                            c = a + this.constInstr();
                            return c;
                        }
                        public static void main(String[] args){
                            int a;
                            int b;
                            int c;
                            Simple s;
                            a = 20;
                            b = 10;
                            s = new Simple();
                            c = s.add(a,b);
                            io.println(c);
                        }
                        public int constInstr(){
                            int c;
                            c = 0;
                            c = 4;
                            c = 8;
                            c = 14;
                            c = 250;
                            c = 400;
                            c = 1000;
                            c = 100474650;
                    		c = 10;
                            return c;
                        }
                    }
                 """
        );
    }

    @Test
    public void testArrayIndexNotInt() {
        TestUtils.parseVerbose(
                """
                    class ArrayIndexNotInt {
                            public static void main(String[] args) {
                            }
                            public int foo() {
                                int[] a;
                                boolean b;
                                a = new int[2];
                                b = true;
                                return a[b];
                            }
                        }
                    """
        );
    }

    @Test
    public void testVarArgsInFieldInvalid() {
        TestUtils.parseVerbose(
                """
                    class VarargsFieldInvalid {
                        int... numbers;
                        public static void main(String[] args) {
                        }
                    }
                    """
        );
    }

    @Test
    public void testArrayInWhileCondition() {
        TestUtils.parseVerbose(
                """
                        class ArrayInWhileCondition {
                                public static void main(String[] args) {
                                }
                                public int foo() {
                                    int[] a;
                                    a = new int[2];
                                    while (a) {
                                    }
                                    return 0;
                                }
                            }
                    """
        );
    }

    @Test
    public void testSymbolTable() {
        TestUtils.parseVerbose(
                """
                        import io;
                            import foo.Bar;
                            import comp.Table;
                            class SymbolTable extends Table {
                                int intField;
                                boolean boolField;
                                public int method1() {
                                    int intLocal1;
                                    boolean boolLocal1;
                                    Bar barLocal1;
                                    return 0;
                                }
                                public Bar method2(int intParam1, boolean boolParam1, Bar barParam1) {
                                    return barParam1;
                                }
                                public static void main(String[] args) {
                                }
                            }
                    """
        );
    }

    @Test
    public void testMemberAccessWrong() {
        TestUtils.parseVerbose(
                """
                        class MemberAccessWrong {
                                    public int foo() {
                                        int[] arr;
                                        arr = [1, 2, 3];
                                        return arr.size;
                                    }
                                    public static void main(String[] args) {
                                    }
                                }
                    """
        );
    }
}
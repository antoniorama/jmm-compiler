package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(PARENTHESES_EXPRESSION, this::visitParenthesesExpr);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(LOGICAL_EXPRESSION, this::visitLogicalExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_VALUE, this::visitBoolean);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(NEW_CLASS_INSTANCE, this::visitNewClassInstance);
        addVisit(THIS, this::visitThis);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        var name = node.get("value");

        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var boolType = new Type(TypeUtils.getBooleanTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = (Objects.equals(node.get("value"), "true") ? "1" : "0") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParenthesesExpr(JmmNode node, Void unused) {
        return visit(node.getJmmChild(0));
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitLogicalExpr(JmmNode node, Void unused) {
        return visitBinExpr(node, unused);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);

        if (type == null) {
            return new OllirExprResult(id);
        }
        String ollirType = OptUtils.toOllirType(type);

        String computation = "";
        boolean isField = true;

        // Get the current method
        JmmNode parent = node.getParent();
        while (!parent.getKind().equals("MethodDecl") && !parent.getKind().equals("MainMethodDecl")) {
            parent = parent.getParent();
        }
        String methodName = parent.get("name");

        for (var local : table.getLocalVariables(methodName)) {
            if (local.getName().equals(node.get("name"))) {
                isField = false;
                break;
            }
        }

        // Check if it's a param
        if (isField) {

            for (var param : table.getParameters(methodName)) {
                if (param.getName().equals(node.get("name"))) {
                    isField = false;
                    break;
                }
            }

        }
        if (isField) {
            var temp = OptUtils.getTemp();
            computation = temp + ollirType + SPACE + ASSIGN + ollirType + " getfield(this, " + id + ollirType + ")" + ollirType + END_STMT;
            id = temp;
        }

        String code = id + ollirType;
        return new OllirExprResult(code, computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        List<String> argsCode = new ArrayList<>();

        // Visit the owner of the method (e.g., an instance of a class, or the class itself for static methods)
        OllirExprResult ownerExpr = visit(node.getJmmChild(0));
        computation.append(ownerExpr.getComputation());

        // Handle each argument of the method
        for (int i = 1; i < node.getNumChildren(); i++) {
            OllirExprResult argExpr = visit(node.getJmmChild(i));
            computation.append(argExpr.getComputation());
            argsCode.add(argExpr.getCode());
        }

        boolean isImported = true;
        boolean isAssign = false;
        boolean isStatic = false;

        // Get the method name and return type
        String methodName = node.get("methodName");
        Type returnType = table.getReturnType(methodName);

        JmmNode parent = node.getParent();
        JmmNode grandParent = parent.getParent();

        if (parent.getKind().equals("AssignStmt") || grandParent.getKind().equals("AssignStmt"))  {
            isAssign = true;
        }

        JmmNode child = node.getJmmChild(0);
        var childType = TypeUtils.getExprType(child, table);

        if (childType == null) {
            isStatic = true;
        }

        if (child.getKind().equals("This") || (childType != null && Objects.equals(childType.getName(), table.getClassName()))) {
            isImported = false;
        }

        // imported method
        if (isImported && isAssign && isStatic) {

            returnType = TypeUtils.getExprType(parent.getJmmChild(0), table);
        }
        else if (isImported && !isStatic) {
            returnType = childType;
        }
        else if (returnType == null && node.getAncestor(CLASS_DECL).get().hasAttribute("extendedClass")) {
            returnType = new Type(table.getClassName(),  false);
        }
        else if (returnType == null) {
            returnType = new Type("void", false);
        }

        String returnTypeString = OptUtils.toOllirType(returnType);

        // Construct the method call
        String argsList = String.join(", ", argsCode);
        String tempVar = OptUtils.getTemp() + returnTypeString;
        String methodCallCode =  (isStatic ? "invokestatic(" : "invokevirtual(") + ownerExpr.getCode() + ", \"" + methodName + "\""
                + (argsCode.isEmpty() ? "" : ", " + argsList)
                + ")" + returnTypeString;

        // Store the result of the method call in a temporary variable
        if (isAssign) {
            computation.append(tempVar).append(SPACE).append(ASSIGN).append(returnTypeString).append(SPACE);
        }
        computation.append(methodCallCode).append(END_STMT);

        return new OllirExprResult(tempVar, computation.toString());
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        return new OllirExprResult("this." + table.getClassName(), "");
    }

    private OllirExprResult visitNewClassInstance(JmmNode node, Void unused) {
        String className = node.get("name");
        String tempVar = OptUtils.getTemp() + "." + className;
        String initializationCode = tempVar + " " + ASSIGN + "." + className + " " + "new(" + className + ")." + className
                + END_STMT + "invokespecial(" + tempVar + ", \"<init>\").V" + END_STMT;
        return new OllirExprResult(tempVar, initializationCode);
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }
}

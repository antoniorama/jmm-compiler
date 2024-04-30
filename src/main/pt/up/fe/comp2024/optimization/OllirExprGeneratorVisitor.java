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
        addVisit(LOGICAL_EXPRESSION, this::visitBinExpr);
        addVisit(RELATIONAL_EXPRESSION, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_VALUE, this::visitBoolean);
        addVisit(NOT_EXPRESSION, this::visitNotExpr);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(PROPERTY_ACCESS, this::visitPropertyAccess);
        addVisit(NEW_CLASS_INSTANCE, this::visitNewClassInstance);
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(THIS, this::visitThis);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);

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

    private OllirExprResult visitNotExpr(JmmNode node, Void unused) {
        var child = visit(node.getChild(0));
        Type childType = TypeUtils.getExprType(node, table);
        StringBuilder computation = new StringBuilder();
        computation.append(child.getComputation());
        String code = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getExprType(node, table));
        computation.append(code).append(SPACE).append(ASSIGN).append(OptUtils.toOllirType(childType)).append(SPACE).append("!.bool ").append(child.getCode()).append(END_STMT);
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitParenthesesExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation()).append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE).append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE).append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
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

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        List<String> argsCode = new ArrayList<>();

        // Visit the owner of the method (e.g., an instance of a class, or the class itself for static methods)
        OllirExprResult ownerExpr = visit(node.getChild(0));
        computation.append(ownerExpr.getComputation());

        // Handle each argument of the method
        for (int i = 1; i < node.getNumChildren(); i++) {
            OllirExprResult argExpr = visit(node.getChild(i));
            computation.append(argExpr.getComputation());
            argsCode.add(argExpr.getCode());
        }

        boolean isAssign = false;
        boolean isStatic = false;
        boolean isInsideMethodCall = false;
        JmmNode parent = node.getParent();
        JmmNode child = node.getChild(0);
        Type childType = TypeUtils.getExprType(child, table);

        while(!parent.getKind().equals("MethodDecl") && !parent.getKind().equals("MainMethodDecl")) {
            if (parent.getKind().equals("AssignStmt")) {
                isAssign = true;
            }
            else if (parent.getKind().equals("MethodCall")) {
                isInsideMethodCall = true;
            }
            parent = parent.getParent();
        }

        if (childType == null) {
            isStatic = true;
        }

        // Get the method name and return type
        String methodName = node.get("methodName");
        Type returnType = TypeUtils.getExprType(node, table);
        
        String returnTypeString = OptUtils.toOllirType(returnType);

        // Construct the method call
        String argsList = String.join(", ", argsCode);
        String tempVar = OptUtils.getTemp() + returnTypeString;
        String methodCallComputation =  (isStatic ? "invokestatic(" : "invokevirtual(") + ownerExpr.getCode() + ", \"" + methodName + "\""
                + (argsCode.isEmpty() ? "" : ", " + argsList)
                + ")" + returnTypeString;

        // Store the result of the method call in a temporary variable
        if (isAssign || isInsideMethodCall) {
            computation.append(tempVar).append(SPACE).append(ASSIGN).append(returnTypeString).append(SPACE);
        }
        computation.append(methodCallComputation).append(END_STMT);

        return new OllirExprResult(tempVar, computation.toString());
    }

    private OllirExprResult visitPropertyAccess(JmmNode node, Void unused) {
        if (!node.get("name").equals("length")) return OllirExprResult.EMPTY; // Only length property is supported
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        String intType = OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        String tempVar = OptUtils.getTemp() + intType;
        JmmNode array = node.getChild(0);
        String arrayType = OptUtils.toOllirType(TypeUtils.getExprType(array, table));

        code.append(tempVar);
        computation.append(tempVar).append(ASSIGN).append(intType).append(" arraylength(").append(array.get("name")).append(arrayType).append(")").append(intType).append(END_STMT);

        return new OllirExprResult(code.toString(), computation.toString());

    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        // Visit the size of the array
        OllirExprResult sizeExpr = visit(node.getChild(1));
        computation.append(sizeExpr.getComputation());

        // Get the type of the array
        Type arrayType = TypeUtils.getExprType(node, table);
        String arrayTypeString = OptUtils.toOllirType(arrayType);

        // Construct the array creation
        String arrayCreationCode = "new(array" + ", " + sizeExpr.getCode() + ")" + arrayTypeString;

        // Store the result of the array creation in a temporary variable
        code.append(arrayCreationCode);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        JmmNode array = node.getChild(0);
        OllirExprResult index = visit(node.getChild(1));
        String arrayType = OptUtils.toOllirType(TypeUtils.getExprType(array, table));
        String intType = OptUtils.toOllirType(new Type(TypeUtils.getIntTypeName(), false));
        String tempVar = OptUtils.getTemp() + intType;

        computation.append(index.getComputation());

        boolean isInsideMethodCall = false;
        JmmNode parent = node.getParent();
        while (!parent.getKind().equals("MethodDecl") && !parent.getKind().equals("MainMethodDecl")) {
            if (parent.getKind().equals("MethodCall")) {
                isInsideMethodCall = true;
                break;
            }
            parent = parent.getParent();
        }

        if (isInsideMethodCall) {
            computation.append(tempVar).append(SPACE).append(ASSIGN).append(intType).append(SPACE).append(array.get("name")).append("[").append(index.getCode()).append("]").append(intType).append(END_STMT);
        }
        code.append(isInsideMethodCall ? tempVar : array.get("name") + "[" + index.getCode() + "]" + arrayType);

        return new OllirExprResult(code.toString(), computation.toString());
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

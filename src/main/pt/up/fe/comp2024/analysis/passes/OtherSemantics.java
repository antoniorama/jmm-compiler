package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

public class OtherSemantics extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::verifyTypeCompatibility);
        addVisit(Kind.ARRAY_INIT, this::visitArrayInit);
        addVisit(Kind.ASSIGN_STMT, this::verifyTypeCompatibility);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        // if there is a varArgs parameter that is not the last one -> error!
        List<Symbol> methodParameters = table.getParameters(method.get("name"));
        for (int i = 0; i < methodParameters.size() - 1; i++) {
            if (methodParameters.get(i).getType().hasAttribute("isVarArgs")) {
                var message = "Varargs parameter is not the last one!";
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(method), NodeUtils.getColumn(method), message, null));
            }
        }

        currentMethod = method.get("name");
        return null;
    }

    // Verifies type compatibility for BinaryOps and Assigns
    private Void verifyTypeCompatibility(JmmNode node, SymbolTable table) {
        String operator = "ASSIGN"; // we will consider "ASSIGN" to be an operator.
        // this way we don't have to do two separate functions for handling BinaryExpr and AssignStmt
        // and we can use the same logic for TypeCompatibility

        if (node.getKind().equals("BinaryExpr"))
            operator = node.get("op");

        Type leftType;
        if (node.getChild(0).getKind().equals("VarRefExpr")) {
            leftType = getVarType(node.getChild(0).get("name"), table, node);
        } else {
            leftType = TypeUtils.getExprType(node.getChild(0), table);
        }

        Type rightType;
        if (node.getChild(1).getKind().equals("VarRefExpr")) {
            rightType = getVarType(node.getChild(1).get("name"), table, node);
        } else {
            rightType = TypeUtils.getExprType(node.getChild(1), table);
        }

        // debug
        // System.out.println("LEFT TYPE:");
        // System.out.println(leftType.getName());
        // System.out.println("RIGHT TYPE:");
        // System.out.println(rightType.getName());

        // Verify the compatibility of the types and handle the error report
        if (!areTypesCompatible(operator, leftType, rightType, table)) {
            // not sure if Kinds print for Strings?
            var message = String.format("Incompatible types for operator '" + operator + "': '" + leftType + "' and '" + rightType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table) {
        JmmNode arrayVar = arrayAccess.getChild(0);
        JmmNode arrayIndex = arrayAccess.getChild(1);

        // Error if index is not an integer
        if (!arrayIndex.getKind().equals(TypeUtils.getIntTypeName()) && !arrayIndex.getKind().equals("IntegerLiteral")) {
            var message = String.format("Trying to access array with index that is not an Integer");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }

        Type arrayVarType;
        if (arrayVar.getKind().equals("VarRefExpr")) {
            arrayVarType = getVarType(arrayAccess.getChild(0).get("name"), table, arrayAccess);
        } else {
            arrayVarType = TypeUtils.getExprType(arrayAccess.getChild(0), table);
        }

        // Error if variable isn't an array
        if (!arrayVarType.isArray()) {
            var message = String.format("Variable " + arrayVarType.getName() + " is not an array.");
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayAccess), NodeUtils.getColumn(arrayAccess), message, null));
        }

        return null;
    }

    private Void visitArrayInit(JmmNode arrayInit, SymbolTable table) {
        JmmNode expressionList = arrayInit.getJmmChild(0);
        List<JmmNode> elements = expressionList.getChildren();

        // if array has length 0, then it's valid
        if (elements.isEmpty()) {
            return null;
        }

        Type firstElementType = TypeUtils.getExprType(elements.get(0), table);

        for (int i = 1; i < elements.size(); i++) {
            Type currentElementType = TypeUtils.getExprType(elements.get(i), table);

            if (!firstElementType.equals(currentElementType)) {
                var message = String.format("Array initialization contains elements of different types: %s and %s.", firstElementType.getName(), currentElementType.getName());
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(arrayInit), NodeUtils.getColumn(arrayInit), message, null));
                break; // Reporting one error for type inconsistency is enough
            }
        }

        return null;
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {
        List<String> classMethods = table.getMethods();
        Type calledType = getVarType(methodCall.getJmmChild(0).get("name"), table, methodCall);

        // if there is an extends, we can just assume that the method is in there
        if (table.getSuper() != null) {
            return null;
        }

        if (calledType.getName().equals(table.getClassName()) && !classMethods.contains(methodCall.get("methodName"))) {
            var message = String.format("Method %s doesn't exist in class %s", methodCall.get("methodName"), table.getClassName());
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall), message, null));
        }

        return null;
    }

    private Type getVarType(String varName, SymbolTable table, JmmNode node) {

        List<Symbol> symbolTableOfLocalVars = table.getLocalVariables(this.currentMethod);
        List<Symbol> symbolTableOfParameters = table.getParameters(this.currentMethod);

        for (Symbol symbol : symbolTableOfLocalVars) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        for (Symbol symbol : symbolTableOfParameters) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        var message = String.format("Variable " + varName + " not found.");
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        System.out.println("herehreree");
        return null;
    }

    private boolean areTypesCompatible(String operator, Type leftType, Type rightType, SymbolTable table) {
        String intTypeName = TypeUtils.getIntTypeName();

        System.out.println(operator);

        return switch (operator) {
            case "+" -> leftType == rightType;
            case "*" -> leftType == rightType;
            case "ASSIGN" -> isAssignValid(leftType, rightType, table);
            default -> false;
        };
    }

    private boolean isAssignValid(Type leftType, Type rightType, SymbolTable table) {
        // TODO - probably isn't handling all possible cases

        // Check for array compatibility first. If one is an array and the other isn't, they can't be assigned regardless of their base type.
        if (leftType.isArray() != rightType.isArray()) {
            return false; // Directly return false if one is an array and the other isn't.
        }

        // If both types have the same name and array status, they are compatible.
        // This check is sufficient for both primitive types and class types, including arrays, since we already checked array status compatibility.
        if (leftType.getName().equals(rightType.getName())) {
            return true;
        }

        // ObjectAssignmentPassExtends
        if (rightType.getName().equals(table.getClassName()) && leftType.getName().equals(table.getSuper())) {
            return true;
        }

        // ObjectAssignmentPassImports
        List<String> importedList = table.getImports();
        if (importedList.contains(rightType.getName()) && importedList.contains(leftType.getName())) {
            return true;
        }

        return false;
    }

}

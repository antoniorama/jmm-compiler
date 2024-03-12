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

import javax.annotation.processing.SupportedSourceVersion;
import java.util.List;

public class TypeCompatibility extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::verifyTypeCompatibility);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }
    private Void verifyTypeCompatibility(JmmNode node, SymbolTable table) {
        String operator = node.get("op");

        Type leftType = TypeUtils.getExprType(node.getChild(0), table);
        Type rightType = TypeUtils.getExprType(node.getChild(1), table);

        // debug
        System.out.println("here");
        System.out.println(leftType.getName());
        System.out.println(rightType.getName());

        // Verify the compatibility of the types and handle the error report
        if (!areTypesCompatible(operator, leftType, rightType)) {
            // not sure if Kinds print for Strings?
            var message = String.format("Incompatible types for operator '" + operator + "': '" + leftType + "' and '" + rightType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        return null;
    }

    /*
    private Type getTypeFromNode(JmmNode node, SymbolTable table) {
        Type type = null;

        switch (node.getKind()) {
            case "BinaryExpr":
                Type leftType = getTypeFromNode(node.getChild(0), table);
                Type rightType = getTypeFromNode(node.getChild(1), table);
                String operator = node.get("op");
                type = determineResultingType(operator, leftType, rightType, node);
                break;
            case "IntegerLiteral":
                type = new Type();
                break;
            case "VarRefExpr":
                String varName = node.get("name");
                type = table.getVarType(varName, currentMethod);
                break;
            // Add cases for other node types that can be encountered.

            default: // only for debug, to be deleted
                System.out.println("getTypeFromNode error");
        }

        // only for debug, to be deleted
        if (type == null) {
            System.out.println("getTypeFromNode null type");
        }

        return type;
    }
    */

    private Type getVarType(String varName, SymbolTable table, JmmNode node) {

        List<Symbol> symbolTableOfLocalVars = table.getLocalVariables(this.currentMethod);
        List<Symbol> symbolTableOfFieldVariables = table.getFields();

        for (Symbol symbol : symbolTableOfLocalVars) {
            if (symbol.getName().equals(varName)) {
                // found variable
                return symbol.getType();
            }
        }

        // TODO -> Code to get field variables

        var message = String.format("Variable " + varName + " not found.");
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        return null;
    }

    private Type determineResultingType(String operator, Type leftType, Type rightType, JmmNode node) {

        if (areTypesCompatible(operator, leftType, rightType)) {
            return leftType;
        } else {
            var message = String.format("Incompatible types for operator '" + operator + "': '" + leftType + "' and '" + rightType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        return null;
    }

    private boolean areTypesCompatible(String operator, Type leftType, Type rightType) {
        String intTypeName = TypeUtils.getIntTypeName();

        switch (operator) {
            case "+":
                return (leftType.getName().equals(intTypeName) && rightType.getName().equals(intTypeName));
            default:
                return false;
        }
    }

    private Void visitVarRefExpr(JmmNode jmmNode, SymbolTable symbolTable) {
        return null;
    }
}

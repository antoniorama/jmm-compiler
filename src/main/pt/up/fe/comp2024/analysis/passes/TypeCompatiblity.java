package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.HashMap;

public class TypeCompatiblity extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::verifyTypeCompatibility);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }
    private Void verifyTypeCompatibility(JmmNode node, SymbolTable table) {
        String operator = node.get("op");

        Kind leftType = getTypeFromNode(node.getChild(0), table);
        Kind rightType = getTypeFromNode(node.getChild(1), table);

        // Verify the compatibility of the types and handle the error report
        if (!areTypesCompatible(operator, leftType, rightType)) {
            // not sure if Kinds print for Strings?
            var message = String.format("Incompatible types for operator '" + operator + "': '" + leftType + "' and '" + rightType);
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), message, null));
        }

        return null;
    }

    private Kind getTypeFromNode(JmmNode node, SymbolTable table) {
        Kind type = null;

        switch (node.getKind()) {
            case "BinaryExpr":
                Kind leftType = getTypeFromNode(node.getChild(0), table);
                Kind rightType = getTypeFromNode(node.getChild(1), table);
                String operator = node.get("op");
                type = determineResultingType(operator, leftType, rightType);
                break;
            case "IntegerLiteral":
                type = Kind.INTEGER_TYPE;
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

    public Kind getVarType(String varName, String currentMethod) {
        // First, check if the variable is a local variable in the current method scope.
        Kind type = getLocalVariableType(varName, currentMethod);
        if (type != null) {
            return type;
        }

        // If not found, check if it's a field (class-level variable).
        type = getFieldVariableType(varName);
        if (type != null) {
            return type;
        }

        // If the variable is not found in either scope, it's an error.
        // TODO
        return null;
    }

    private Kind getLocalVariableType(String varName, String currentMethod) {
        // Here, you would access the data structure that contains the method's local variables.
        // It might look something like this, depending on your symbol table structure:
        HashMap<Object, Object> symbolTableOfLocalVars;
        if (currentMethod != null && symbolTableOfLocalVars.containsKey(currentMethod)) {
            Map<String, Kind> localVars = symbolTableOfLocalVars.get(currentMethod);
            return localVars.get(varName); // This will return null if the variable is not found.
        }
        return null;
    }

    private Kind getFieldVariableType(String varName) {
        // Here, you would access the data structure that contains the class's field variables.
        // It might look something like this:
        return symbolTableOfFields.get(varName); // This will return null if the field is not found.
    }


    private Kind determineResultingType(String operator, Kind leftType, Kind rightType) {
        // This method would have the same logic as before to determine the resulting type based on operator and operand types.
        // Placeholder implementation:
        if (areTypesCompatible(operator, leftType, rightType)) {
            // Assuming binary operations with integers result in integers, and similarly for other types
            return leftType; // or some logic to determine the resulting type based on the operator
        } else {
            throw new SemanticException("Incompatible types for operator '" + operator + "': " + leftType + " and " + rightType);
        }
    }

    private Void visitVarRefExpr(JmmNode jmmNode, SymbolTable symbolTable) {
        return null;
    }
}
